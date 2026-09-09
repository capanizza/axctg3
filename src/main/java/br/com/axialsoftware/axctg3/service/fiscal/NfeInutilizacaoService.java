package br.com.axialsoftware.axctg3.service.fiscal;

import br.com.axialsoftware.axctg3.entity.cadastros.Empresa;
import br.com.axialsoftware.axctg3.entity.fiscal.NfeInutilizacao;
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
import java.time.format.DateTimeFormatter;

/**
 * Pedido de Inutilização de Numeração de NF-e — leiaute próprio {@code inutNFe} v4.00,
 * <b>não</b> um {@code tpEvento} (diferente de {@link NfeCancelamentoService}): webservice
 * dedicado {@code NFeInutilizacao4}, sem envelope de lote, resposta síncrona. Usado quando
 * uma faixa de números de uma série nunca vai ser emitida (nota pulada, falha de sequência
 * etc.) — ao contrário do cancelamento, não existe {@code Nfe} correspondente à faixa, por
 * isso o resultado é gravado numa entidade própria ({@link NfeInutilizacao}) em vez de
 * atualizar uma linha existente.
 *
 * <p>Mesma orquestração de {@link NfeCancelamentoService} (montar XML → assinar →
 * transmitir → gravar) e mesmo motivo pra montar o XML aqui mesmo, sem builder dedicado: o
 * corpo de {@code infInut} é pequeno.
 */
@Service
public class NfeInutilizacaoService {

    private static final String NS_NFE = "http://www.portalfiscal.inf.br/nfe";
    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
    // Modelo do documento fiscal — 55 (NF-e), mesmo default de Nfe.mod. NFC-e (65) fica de
    // fora, mesmo escopo já assumido pelo resto da emissão própria (docs/EMISSAO-NFE.md).
    private static final int MODELO_NFE = 55;
    private static final int JUSTIFICATIVA_TAMANHO_MINIMO = 15;
    // Mesmo raciocínio de NfeCancelamentoService.JUSTIFICATIVA_PADRAO: cobre o campo em
    // branco com um texto válido pro schema (xJust exige mínimo 15 caracteres) sem
    // transformar "não justificou" num erro bloqueante.
    private static final String JUSTIFICATIVA_PADRAO = "Erro de sequência na numeração.";

    private final DataManager dataManager;
    private final NfeXmlSigner signer;
    private final NfeWebserviceClient client;

    public NfeInutilizacaoService(DataManager dataManager, NfeXmlSigner signer, NfeWebserviceClient client) {
        this.dataManager = dataManager;
        this.signer = signer;
        this.client = client;
    }

    public record ResultadoInutilizacao(boolean sucesso, Integer cStat, String motivo) {
    }

    public ResultadoInutilizacao inutilizar(Integer codEmpresa, Integer ano, Integer serie,
                                             Integer numeroInicial, Integer numeroFinal,
                                             String justificativaInformada) {
        if (numeroInicial == null || numeroFinal == null || numeroInicial < 1 || numeroFinal < 1) {
            return new ResultadoInutilizacao(false, null, "Informe os números inicial e final da faixa");
        }
        if (numeroInicial > numeroFinal) {
            return new ResultadoInutilizacao(false, null, "O número inicial não pode ser maior que o final");
        }
        String justificativa = justificativaInformada == null || justificativaInformada.isBlank()
                ? JUSTIFICATIVA_PADRAO : justificativaInformada.trim();
        if (justificativa.length() < JUSTIFICATIVA_TAMANHO_MINIMO) {
            return new ResultadoInutilizacao(false, null,
                    "Justificativa precisa ter pelo menos " + JUSTIFICATIVA_TAMANHO_MINIMO + " caracteres");
        }

        Empresa empresa = buscarEmpresa(codEmpresa);
        if (empresa == null) {
            return new ResultadoInutilizacao(false, null, "Empresa não encontrada");
        }
        if (empresa.getCrt() == null || empresa.getAmbienteNfe() == null
                || empresa.getCertificadoArquivo() == null || empresa.getCertificadoSenha() == null) {
            return new ResultadoInutilizacao(false, null,
                    "Empresa sem certificado digital ou ambiente configurado — ver aba \"Emissão NFe\" do cadastro");
        }
        if (empresa.getMunicipio() == null) {
            return new ResultadoInutilizacao(false, null, "Empresa sem município cadastrado — necessário pra cUF");
        }

        try {
            Document inutAssinado = signer.assinarInfInut(
                    construirInutNFe(empresa, ano, serie, numeroInicial, numeroFinal, justificativa), empresa);
            String xmlInutAssinado = serializar(inutAssinado.getDocumentElement());
            NfeWebserviceClient.RespostaInutilizacao resposta = client.enviarInutilizacao(
                    xmlInutAssinado.getBytes(StandardCharsets.UTF_8), empresa);

            NfeInutilizacao registro = dataManager.create(NfeInutilizacao.class);
            registro.setCodEmpresa(codEmpresa);
            registro.setAno(ano);
            registro.setModelo(MODELO_NFE);
            registro.setSerie(serie);
            registro.setNumeroInicial(numeroInicial);
            registro.setNumeroFinal(numeroFinal);
            registro.setJustificativa(justificativa);
            registro.setRetCStat(resposta.cStat());
            registro.setRetXMotivo(resposta.xMotivo());
            registro.setRetNProt(resposta.nProt());
            registro.setDhRecbto(parseDhRecbto(resposta.dhRecbto()));
            registro.setXmlRetorno(resposta.xmlRetInutNFe());
            dataManager.save(registro);

            return new ResultadoInutilizacao(resposta.homologada(), resposta.cStat(), resposta.xMotivo());
        } catch (Exception e) {
            return new ResultadoInutilizacao(false, null, "Erro ao inutilizar: " + e.getMessage());
        }
    }

    private OffsetDateTime parseDhRecbto(String dhRecbto) {
        if (dhRecbto == null || dhRecbto.isBlank()) {
            return null;
        }
        return OffsetDateTime.parse(dhRecbto, DATA_HORA);
    }

    private Empresa buscarEmpresa(Integer codEmpresa) {
        return dataManager.load(Empresa.class)
                .query("select e from Empresa e where e.codigo = :codigo")
                .parameter("codigo", codEmpresa)
                .optional()
                .orElse(null);
    }

    /**
     * Monta {@code <inutNFe><infInut>...} (leiaute do Pedido de Inutilização, versão
     * 4.00). Mesmos cuidados de {@link NfeXmlBuilder#construir}: {@code createElementNS}
     * (não {@code setAttribute("xmlns", ...)}) pro namespace ficar disponível de verdade
     * pra canonicalização da assinatura.
     */
    private Document construirInutNFe(Empresa empresa, Integer ano, Integer serie,
                                       Integer numeroInicial, Integer numeroFinal, String justificativa) {
        Document doc = novoDocumento();
        Integer cUf = UfIbge.codigo(empresa.getMunicipio().getUf());
        String cnpj = somenteDigitos(empresa.getCnpj());
        String ano2Digitos = zeroPad(ano % 100, 2);

        // Id = "ID" + cUF(2) + ano(2) + CNPJ(14) + mod(2) + serie(3) + nNFIni(9) + nNFFin(9)
        // — todos os campos numéricos com zero à esquerda até a largura fixa (padrão
        // ID[0-9]{41} do schema leiauteInutNFe_v4.00.xsd)
        String id = "ID" + zeroPad(cUf, 2) + ano2Digitos + cnpj + zeroPad(MODELO_NFE, 2)
                + zeroPad(serie, 3) + zeroPad(numeroInicial, 9) + zeroPad(numeroFinal, 9);

        Element inutNFe = doc.createElementNS(NS_NFE, "inutNFe");
        inutNFe.setAttribute("versao", "4.00");
        doc.appendChild(inutNFe);

        Element infInut = doc.createElementNS(NS_NFE, "infInut");
        infInut.setAttribute("Id", id);
        text(doc, infInut, "tpAmb", empresa.getAmbienteNfe().getId());
        text(doc, infInut, "xServ", "INUTILIZAR");
        text(doc, infInut, "cUF", cUf);
        text(doc, infInut, "ano", ano2Digitos);
        text(doc, infInut, "CNPJ", cnpj);
        text(doc, infInut, "mod", MODELO_NFE);
        text(doc, infInut, "serie", serie);
        text(doc, infInut, "nNFIni", numeroInicial);
        text(doc, infInut, "nNFFin", numeroFinal);
        text(doc, infInut, "xJust", justificativa);

        inutNFe.appendChild(infInut);
        return doc;
    }

    private String zeroPad(int valor, int largura) {
        return String.format("%0" + largura + "d", valor);
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
