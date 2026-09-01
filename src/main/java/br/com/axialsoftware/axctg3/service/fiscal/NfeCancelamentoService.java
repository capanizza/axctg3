package br.com.axialsoftware.axctg3.service.fiscal;

import br.com.axialsoftware.axctg3.entity.cadastros.Empresa;
import br.com.axialsoftware.axctg3.entity.fiscal.Nfe;
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
 * Cancelamento de NFe — evento {@code tpEvento} 110111, o único evento implementado por
 * ora (carta de correção e inutilização de numeração ficam pra depois, mesmo escopo
 * anunciado em docs/EMISSAO-NFE.md). Espelha a orquestração de {@link NfeEmissaoService}
 * (montar XML → assinar → transmitir → gravar), mas o XML do evento é bem menor que o da
 * NFe inteira e por isso é montado aqui mesmo, sem um {@code NfeXmlBuilder}-equivalente
 * dedicado.
 *
 * <p>Só cancela uma NFe emitida por este próprio sistema ou importada com protocolo de
 * autorização gravado ({@link Nfe#getProtNProt()}) — sem isso não tem como montar o
 * {@code detEvento/nProt} exigido pelo schema.
 */
@Service
public class NfeCancelamentoService {

    private static final String NS_NFE = "http://www.portalfiscal.inf.br/nfe";
    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
    private static final int TP_EVENTO_CANCELAMENTO = 110111;
    // Primeira tentativa de cancelamento de uma NFe — não há retentativa com sequência
    // maior nesta versão (só faz sentido incrementar quando já existe um evento do mesmo
    // tipo REGISTRADO pra essa chave, e uma NFe já cancelada é barrada antes de chegar
    // aqui pelo check de protCStat==100 em cancelar()).
    private static final String N_SEQ_EVENTO = "01";
    private static final int JUSTIFICATIVA_TAMANHO_MINIMO = 15;

    private final DataManager dataManager;
    private final NfeXmlSigner signer;
    private final NfeWebserviceClient client;

    public NfeCancelamentoService(DataManager dataManager, NfeXmlSigner signer, NfeWebserviceClient client) {
        this.dataManager = dataManager;
        this.signer = signer;
        this.client = client;
    }

    public record ResultadoCancelamento(boolean sucesso, Integer cStat, String motivo) {
    }

    /** Carrega a {@link Nfe} pelo id e cancela. Usado por {@code NfeListView}. */
    public ResultadoCancelamento cancelar(UUID nfeId, String justificativa) {
        Nfe nfe = dataManager.load(Nfe.class).id(nfeId).one();
        return cancelar(nfe, justificativa);
    }

    /**
     * Carrega a {@link Nfe} pela chave de acesso e cancela. Usado por
     * {@code NotaSaidaListView}, que só tem a chave gravada em {@code NotaSaida.chave}.
     */
    public ResultadoCancelamento cancelarPorChave(String chave, String justificativa) {
        Nfe nfe = dataManager.load(Nfe.class)
                .query("select e from Nfe e where e.chave = :chave")
                .parameter("chave", chave)
                .optional()
                .orElse(null);
        if (nfe == null) {
            return new ResultadoCancelamento(false, null, "Não foi encontrada uma NFe correspondente a esta chave de acesso");
        }
        return cancelar(nfe, justificativa);
    }

    private ResultadoCancelamento cancelar(Nfe nfe, String justificativa) {
        if (justificativa == null || justificativa.trim().length() < JUSTIFICATIVA_TAMANHO_MINIMO) {
            return new ResultadoCancelamento(false, null,
                    "Justificativa precisa ter pelo menos " + JUSTIFICATIVA_TAMANHO_MINIMO + " caracteres");
        }
        if (nfe.getProtCStat() == null || nfe.getProtCStat() != 100) {
            return new ResultadoCancelamento(false, nfe.getProtCStat(),
                    "Só é possível cancelar uma NFe autorizada — esta está com cStat="
                            + (nfe.getProtCStat() == null ? "vazio" : nfe.getProtCStat()));
        }
        if (nfe.getProtNProt() == null || nfe.getProtNProt().isBlank()) {
            return new ResultadoCancelamento(false, null,
                    "NFe sem protocolo de autorização gravado — não é possível montar o evento de cancelamento");
        }

        Empresa empresa = buscarEmpresa(nfe.getCodEmpresa());
        if (empresa == null) {
            return new ResultadoCancelamento(false, null, "Empresa não encontrada");
        }
        if (empresa.getCrt() == null || empresa.getAmbienteNfe() == null
                || empresa.getCertificadoArquivo() == null || empresa.getCertificadoSenha() == null) {
            return new ResultadoCancelamento(false, null,
                    "Empresa sem certificado digital ou ambiente configurado — ver aba \"Emissão NFe\" do cadastro");
        }
        if (empresa.getMunicipio() == null) {
            return new ResultadoCancelamento(false, null, "Empresa sem município cadastrado — necessário pra cOrgao do evento");
        }

        try {
            Document eventoAssinado = signer.assinarEvento(construirEventoCancelamento(nfe, empresa, justificativa), empresa);
            String xmlEventoAssinado = serializar(eventoAssinado.getDocumentElement());
            NfeWebserviceClient.RespostaEvento resposta = client.enviarEvento(
                    xmlEventoAssinado.getBytes(StandardCharsets.UTF_8), empresa);

            if (resposta.registrado()) {
                nfe.setCancCStat(resposta.cStat());
                nfe.setCancXMotivo(resposta.xMotivo());
                nfe.setCancNProt(resposta.nProt());
                nfe.setCancDhRegEvento(OffsetDateTime.now(ZoneOffset.of("-03:00")));
                nfe.setCancXJust(justificativa);
                nfe.setCancXmlRetorno(resposta.xmlRetEvento());
                // Código canônico de "NFe cancelada" — mesmo valor já documentado no
                // Javadoc de Nfe.protCStat ("100=autorizada, 101/151=cancelada..."). O
                // protocolo de AUTORIZAÇÃO original (protNProt/protDhRecbto) fica intocado;
                // o protocolo do EVENTO vai só em cancNProt.
                nfe.setProtCStat(101);
                nfe.setProtXMotivo("Cancelamento de NF-e homologado");
                dataManager.save(nfe);
                return new ResultadoCancelamento(true, resposta.cStat(), resposta.xMotivo());
            }
            return new ResultadoCancelamento(false, resposta.cStat(), resposta.xMotivo());
        } catch (Exception e) {
            return new ResultadoCancelamento(false, null, "Erro ao cancelar: " + e.getMessage());
        }
    }

    private Empresa buscarEmpresa(Integer codEmpresa) {
        return dataManager.load(Empresa.class)
                .query("select e from Empresa e where e.codigo = :codigo")
                .parameter("codigo", codEmpresa)
                .optional()
                .orElse(null);
    }

    /**
     * Monta {@code <evento><infEvento>...} (leiaute do evento, versão 1.00) — bem menor
     * que o XML da NFe inteira, por isso não passa por um builder dedicado. Mesmos
     * cuidados de {@link NfeXmlBuilder#construir}: {@code createElementNS} (não
     * {@code setAttribute("xmlns", ...)}) pro namespace ficar disponível de verdade pra
     * canonicalização da assinatura.
     */
    private Document construirEventoCancelamento(Nfe nfe, Empresa empresa, String justificativa) {
        Document doc = novoDocumento();
        Integer cOrgao = UfIbge.codigo(empresa.getMunicipio().getUf());
        OffsetDateTime dhEvento = OffsetDateTime.now(ZoneOffset.of("-03:00")).truncatedTo(ChronoUnit.SECONDS);
        String id = "ID" + TP_EVENTO_CANCELAMENTO + nfe.getChave() + N_SEQ_EVENTO;

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
        text(doc, infEvento, "tpEvento", TP_EVENTO_CANCELAMENTO);
        text(doc, infEvento, "nSeqEvento", Integer.valueOf(N_SEQ_EVENTO));
        text(doc, infEvento, "verEvento", "1.00");

        Element detEvento = doc.createElementNS(NS_NFE, "detEvento");
        detEvento.setAttribute("versao", "1.00");
        text(doc, detEvento, "descEvento", "Cancelamento");
        text(doc, detEvento, "nProt", nfe.getProtNProt());
        text(doc, detEvento, "xJust", justificativa.trim());
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
