package br.com.axialsoftware.axctg3.service.fiscal;

import br.com.axialsoftware.axctg3.entity.cadastros.Empresa;
import br.com.axialsoftware.axctg3.entity.fiscal.Nfe;
import br.com.axialsoftware.axctg3.entity.fiscal.NfeCartaCorrecao;
import io.jmix.core.DataManager;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Carta de Correção Eletrônica — evento {@code tpEvento} 110110. Espelha
 * {@link NfeCancelamentoService} (montar XML do evento → assinar → transmitir → gravar),
 * reaproveitando {@link NfeXmlSigner#assinarEvento} e {@link NfeWebserviceClient#enviarEvento}
 * sem alteração — os dois já são genéricos pra qualquer {@code tpEvento}.
 *
 * <p>Diferente do cancelamento (evento único por NFe), a mesma NFe pode receber várias CC-e's
 * ao longo do tempo — cada correção grava uma linha nova em {@link NfeCartaCorrecao}
 * (composição de {@link Nfe}), com {@code numeroSequencial} incremental. Cada CC-e é
 * independente: o texto digitado não repete automaticamente as correções anteriores (decisão
 * consciente — mais simples que montar um texto cumulativo, mesmo a SEFAZ recomendando isso
 * como boa prática; não é uma exigência do schema).
 */
@Service
public class NfeCartaCorrecaoService {

    private static final String NS_NFE = "http://www.portalfiscal.inf.br/nfe";
    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
    private static final int TP_EVENTO_CCE = 110110;
    private static final int TEXTO_TAMANHO_MINIMO = 15;
    // Limite real do schema/MOC da SEFAZ — no máximo 20 eventos do mesmo tipo por NFe.
    private static final int MAX_CCE_POR_NFE = 20;
    // Texto de condições de uso — fixo e obrigatório pelo schema da CC-e (grupo detEvento),
    // não é digitado pelo operador. Público porque NfeCartaCorrecaoComprovanteService também
    // usa (mesmo texto tem que aparecer no comprovante impresso).
    public static final String X_COND_USO = "A Carta de Correção é disciplinada pelo § 1º-A "
            + "do art. 7º do Convênio S/N, de 15 de dezembro de 1970 e pode ser utilizada para "
            + "regularização de erro ocorrido na emissão de documento fiscal, desde que o erro "
            + "não esteja relacionado com: I - as variáveis que determinam o valor do imposto "
            + "tais como: base de cálculo, alíquota, diferença de preço, quantidade, valor da "
            + "operação ou da prestação; II - a correção de dados cadastrais que implique "
            + "mudança do remetente ou do destinatário; III - a data de emissão ou de saída.";

    private final DataManager dataManager;
    private final NfeXmlSigner signer;
    private final NfeWebserviceClient client;

    public NfeCartaCorrecaoService(DataManager dataManager, NfeXmlSigner signer, NfeWebserviceClient client) {
        this.dataManager = dataManager;
        this.signer = signer;
        this.client = client;
    }

    public record ResultadoCorrecao(boolean sucesso, Integer cStat, String motivo, Integer numeroSequencial) {
    }

    /** Carrega a {@link Nfe} pelo id e corrige. Usado por {@code NfeListView}. */
    public ResultadoCorrecao corrigir(UUID nfeId, String textoCorrecao) {
        Nfe nfe = dataManager.load(Nfe.class).id(nfeId).one();
        return corrigir(nfe, textoCorrecao);
    }

    /**
     * Carrega a {@link Nfe} pela chave de acesso e corrige. Usado por {@code NotaSaidaListView},
     * que só tem a chave gravada em {@code NotaSaida.chave}.
     */
    public ResultadoCorrecao corrigirPorChave(String chave, String textoCorrecao) {
        Nfe nfe = dataManager.load(Nfe.class)
                .query("select e from Nfe e where e.chave = :chave")
                .parameter("chave", chave)
                .optional()
                .orElse(null);
        if (nfe == null) {
            return new ResultadoCorrecao(false, null, "Não foi encontrada uma NFe correspondente a esta chave de acesso", null);
        }
        return corrigir(nfe, textoCorrecao);
    }

    private ResultadoCorrecao corrigir(Nfe nfe, String textoCorrecaoInformado) {
        // Diferente da justificativa de cancelamento, aqui não há desculpa padrão: uma CC-e
        // sem correção real não faz sentido, então texto em branco é sempre rejeitado.
        String textoCorrecao = textoCorrecaoInformado == null ? "" : textoCorrecaoInformado.trim();
        if (textoCorrecao.length() < TEXTO_TAMANHO_MINIMO) {
            return new ResultadoCorrecao(false, null,
                    "O texto da correção precisa ter pelo menos " + TEXTO_TAMANHO_MINIMO + " caracteres", null);
        }
        if (nfe.getProtCStat() == null || nfe.getProtCStat() != 100) {
            return new ResultadoCorrecao(false, nfe.getProtCStat(),
                    "Só é possível corrigir uma NFe autorizada — esta está com cStat="
                            + (nfe.getProtCStat() == null ? "vazio" : nfe.getProtCStat()), null);
        }

        int proximoSequencial = proximoNumeroSequencial(nfe);
        if (proximoSequencial > MAX_CCE_POR_NFE) {
            return new ResultadoCorrecao(false, null,
                    "Limite de " + MAX_CCE_POR_NFE + " cartas de correção por NFe já atingido", null);
        }

        Empresa empresa = buscarEmpresa(nfe.getCodEmpresa());
        if (empresa == null) {
            return new ResultadoCorrecao(false, null, "Empresa não encontrada", null);
        }
        if (empresa.getCrt() == null || empresa.getAmbienteNfe() == null
                || empresa.getCertificadoArquivo() == null || empresa.getCertificadoSenha() == null) {
            return new ResultadoCorrecao(false, null,
                    "Empresa sem certificado digital ou ambiente configurado — ver aba \"Emissão NFe\" do cadastro", null);
        }
        if (empresa.getMunicipio() == null) {
            return new ResultadoCorrecao(false, null, "Empresa sem município cadastrado — necessário pra cOrgao do evento", null);
        }

        try {
            OffsetDateTime dhEvento = OffsetDateTime.now(ZoneOffset.of("-03:00")).truncatedTo(ChronoUnit.SECONDS);
            Document eventoAssinado = signer.assinarEvento(
                    construirEventoCorrecao(nfe, empresa, textoCorrecao, proximoSequencial, dhEvento), empresa);
            String xmlEventoAssinado = serializar(eventoAssinado.getDocumentElement());
            NfeWebserviceClient.RespostaEvento resposta = client.enviarEvento(
                    xmlEventoAssinado.getBytes(StandardCharsets.UTF_8), empresa);

            if (resposta.registrado()) {
                NfeCartaCorrecao cartaCorrecao = dataManager.create(NfeCartaCorrecao.class);
                cartaCorrecao.setNfe(nfe);
                cartaCorrecao.setNumeroSequencial(proximoSequencial);
                cartaCorrecao.setTextoCorrecao(textoCorrecao);
                cartaCorrecao.setDataHoraEvento(dhEvento);
                cartaCorrecao.setCceCStat(resposta.cStat());
                cartaCorrecao.setCceXMotivo(resposta.xMotivo());
                cartaCorrecao.setCceNProt(resposta.nProt());
                cartaCorrecao.setCceDhRegEvento(OffsetDateTime.now(ZoneOffset.of("-03:00")));
                cartaCorrecao.setCceXmlRetorno(resposta.xmlRetEvento());
                dataManager.save(cartaCorrecao);
                return new ResultadoCorrecao(true, resposta.cStat(), resposta.xMotivo(), proximoSequencial);
            }
            return new ResultadoCorrecao(false, resposta.cStat(), resposta.xMotivo(), null);
        } catch (Exception e) {
            return new ResultadoCorrecao(false, null, "Erro ao corrigir: " + e.getMessage(), null);
        }
    }

    /**
     * Não confia em {@code nfe.getCartasCorrecao()} em memória (pode não estar carregado,
     * dependendo do fetch plan usado pra chegar até aqui) — consulta direto o maior
     * {@code numeroSequencial} já gravado pra essa NFe.
     */
    private int proximoNumeroSequencial(Nfe nfe) {
        Integer maiorSequencial = dataManager.load(NfeCartaCorrecao.class)
                .query("select e from NfeCartaCorrecao e where e.nfe = :nfe order by e.numeroSequencial desc")
                .parameter("nfe", nfe)
                .maxResults(1)
                .optional()
                .map(NfeCartaCorrecao::getNumeroSequencial)
                .orElse(0);
        return maiorSequencial + 1;
    }

    private Empresa buscarEmpresa(Integer codEmpresa) {
        return dataManager.load(Empresa.class)
                .query("select e from Empresa e where e.codigo = :codigo")
                .parameter("codigo", codEmpresa)
                .optional()
                .orElse(null);
    }

    /**
     * Monta {@code <evento><infEvento>...} da CC-e (leiaute do evento, versão 1.00) — mesma
     * técnica de {@code NfeCancelamentoService.construirEventoCancelamento}: {@code
     * createElementNS} (não {@code setAttribute("xmlns", ...)}) pro namespace ficar disponível
     * de verdade pra canonicalização da assinatura.
     */
    private Document construirEventoCorrecao(Nfe nfe, Empresa empresa, String textoCorrecao,
                                              int numeroSequencial, OffsetDateTime dhEvento) {
        Document doc = novoDocumento();
        Integer cOrgao = UfIbge.codigo(empresa.getMunicipio().getUf());
        String nSeqEvento = String.format("%02d", numeroSequencial);
        String id = "ID" + TP_EVENTO_CCE + nfe.getChave() + nSeqEvento;

        Element evento = doc.createElementNS(NS_NFE, "evento");
        evento.setAttribute("versao", "1.00");
        doc.appendChild(evento);

        Element infEvento = doc.createElementNS(NS_NFE, "infEvento");
        infEvento.setAttribute("Id", id);
        text(doc, infEvento, "cOrgao", cOrgao);
        text(doc, infEvento, "tpAmb", empresa.getAmbienteNfe().getId());
        text(doc, infEvento, "CNPJ", somenteDigitos(empresa.getCnpj()));
        text(doc, infEvento, "chNFe", nfe.getChave());
        text(doc, infEvento, "dhEvento", DATA_HORA.format(dhEvento));
        text(doc, infEvento, "tpEvento", TP_EVENTO_CCE);
        text(doc, infEvento, "nSeqEvento", numeroSequencial);
        text(doc, infEvento, "verEvento", "1.00");

        Element detEvento = doc.createElementNS(NS_NFE, "detEvento");
        detEvento.setAttribute("versao", "1.00");
        text(doc, detEvento, "descEvento", "Carta de Correção");
        text(doc, detEvento, "xCorrecao", textoCorrecao);
        text(doc, detEvento, "xCondUso", X_COND_USO);
        infEvento.appendChild(detEvento);

        evento.appendChild(infEvento);
        return doc;
    }

    private Document novoDocumento() {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            return builder.newDocument();
        } catch (ParserConfigurationException e) {
            throw new IllegalStateException(e);
        }
    }

    private void text(Document doc, Element parent, String tag, Object valor) {
        if (valor == null) {
            return;
        }
        Element el = doc.createElementNS(NS_NFE, tag);
        el.setTextContent(String.valueOf(valor));
        parent.appendChild(el);
    }

    private String somenteDigitos(String texto) {
        return texto == null ? "" : texto.replaceAll("\\D", "");
    }

    private String serializar(Element el) {
        try {
            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            StringWriter writer = new StringWriter();
            transformer.transform(new DOMSource(el), new StreamResult(writer));
            return writer.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
