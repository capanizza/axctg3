package br.com.axialsoftware.axctg3.service.fiscal;

import br.com.axialsoftware.axctg3.entity.cadastros.Empresa;
import br.com.axialsoftware.axctg3.entity.enums.AmbienteNfe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Comunicação com os webservices da SEFAZ-SP (só SP por enquanto — ver
 * docs/EMISSAO-NFE.md; endpoints conferidos em
 * https://portal.fazenda.sp.gov.br/servicos/nfe/Paginas/URL-WEBSERVICES.aspx em
 * 2026-08-17, não são tabela de banco por serem dado que praticamente nunca muda):
 * {@code NFeStatusServico4} (checagem simples, sem NFe nenhuma — bom teste isolado de
 * certificado/mTLS antes de emitir de verdade) e {@code NFeAutorizacao4}/
 * {@code NFeRetAutorizacao4} (transmissão da NFe assinada).
 *
 * <p>Os webservices "...4" (leiaute 4.00) não usam mais o SOAP Header {@code nfeCabecMsg}
 * (obsoleto, ignorado desde a versão 4.0) — só {@code soap12:Body}/{@code nfeDadosMsg}.
 * A conexão HTTPS autentica com o mesmo certificado da empresa (mTLS) — a SEFAZ exige o
 * certificado do emitente como client cert, não só a assinatura do XML.
 */
@Service
public class NfeWebserviceClient {

    private static final Logger log = LoggerFactory.getLogger(NfeWebserviceClient.class);

    private final NfeXmlSigner signer;

    public NfeWebserviceClient(NfeXmlSigner signer) {
        this.signer = signer;
    }

    private static final Map<AmbienteNfe, String> URL_AUTORIZACAO = Map.of(
            AmbienteNfe.PRODUCAO, "https://nfe.fazenda.sp.gov.br/ws/nfeautorizacao4.asmx",
            AmbienteNfe.HOMOLOGACAO, "https://homologacao.nfe.fazenda.sp.gov.br/ws/nfeautorizacao4.asmx");

    private static final Map<AmbienteNfe, String> URL_RET_AUTORIZACAO = Map.of(
            AmbienteNfe.PRODUCAO, "https://nfe.fazenda.sp.gov.br/ws/nferetautorizacao4.asmx",
            AmbienteNfe.HOMOLOGACAO, "https://homologacao.nfe.fazenda.sp.gov.br/ws/nferetautorizacao4.asmx");

    private static final Map<AmbienteNfe, String> URL_STATUS_SERVICO = Map.of(
            AmbienteNfe.PRODUCAO, "https://nfe.fazenda.sp.gov.br/ws/nfestatusservico4.asmx",
            AmbienteNfe.HOMOLOGACAO, "https://homologacao.nfe.fazenda.sp.gov.br/ws/nfestatusservico4.asmx");

    public record Resposta(Integer cStat, String xMotivo, String nRec, String xmlProtNFe) {
        public boolean autorizada() {
            return cStat != null && cStat == 100;
        }

        public boolean loteRecebido() {
            return cStat != null && cStat == 103;
        }
    }

    /** Envia a NFe assinada (processamento síncrono, indSinc=1 — evita precisar consultar recibo). */
    public Resposta autorizar(byte[] xmlAssinado, Empresa empresa) throws Exception {
        String corpo = "<enviNFe xmlns=\"http://www.portalfiscal.inf.br/nfe\" versao=\"4.00\">"
                + "<idLote>1</idLote><indSinc>1</indSinc>"
                + new String(xmlAssinado, StandardCharsets.UTF_8)
                + "</enviNFe>";
        String respostaBody = enviar(URL_AUTORIZACAO.get(empresa.getAmbienteNfe()),
                "http://www.portalfiscal.inf.br/nfe/wsdl/NFeAutorizacao4", corpo, empresa);
        return interpretarResposta(respostaBody);
    }

    /**
     * Consulta se o serviço da SEFAZ está em operação (cStat=107 é o "tudo certo") — não
     * exige NFe nenhuma montada/assinada, só o certificado configurado. Útil como teste
     * isolado de mTLS/certificado antes de tentar emitir de verdade (ver docs/EMISSAO-NFE.md).
     */
    public Resposta consultarStatusServico(Empresa empresa) throws Exception {
        if (empresa.getMunicipio() == null) {
            throw new IllegalStateException("Empresa sem município cadastrado — necessário pra cUF");
        }
        Integer cUf = UfIbge.codigo(empresa.getMunicipio().getUf());
        String corpo = "<consStatServ xmlns=\"http://www.portalfiscal.inf.br/nfe\" versao=\"4.00\">"
                + "<tpAmb>" + empresa.getAmbienteNfe().getId() + "</tpAmb>"
                + "<cUF>" + cUf + "</cUF>"
                + "<xServ>STATUS</xServ>"
                + "</consStatServ>";
        String respostaBody = enviar(URL_STATUS_SERVICO.get(empresa.getAmbienteNfe()),
                "http://www.portalfiscal.inf.br/nfe/wsdl/NFeStatusServico4", corpo, empresa);
        return interpretarResposta(respostaBody);
    }

    /** Consulta o recibo de um lote que voltou cStat=103 (processamento assíncrono). */
    public Resposta consultarRecibo(String nRec, Empresa empresa) throws Exception {
        String corpo = "<consReciNFe xmlns=\"http://www.portalfiscal.inf.br/nfe\" versao=\"4.00\">"
                + "<tpAmb>" + empresa.getAmbienteNfe().getId() + "</tpAmb>"
                + "<nRec>" + nRec + "</nRec>"
                + "</consReciNFe>";
        String respostaBody = enviar(URL_RET_AUTORIZACAO.get(empresa.getAmbienteNfe()),
                "http://www.portalfiscal.inf.br/nfe/wsdl/NFeRetAutorizacao4", corpo, empresa);
        return interpretarResposta(respostaBody);
    }

    private String enviar(String url, String namespaceOperacao, String corpo, Empresa empresa) throws Exception {
        String envelope = "<?xml version=\"1.0\" encoding=\"utf-8\"?>"
                + "<soap12:Envelope xmlns:soap12=\"http://www.w3.org/2003/05/soap-envelope\">"
                + "<soap12:Body>"
                + "<nfeDadosMsg xmlns=\"" + namespaceOperacao + "\">" + corpo + "</nfeDadosMsg>"
                + "</soap12:Body>"
                + "</soap12:Envelope>";

        // .version(HTTP_1_1): os webservices da SEFAZ (ASP.NET/.asmx) só falam HTTP/1.1 —
        // sem isso o HttpClient tenta negociar HTTP/2 por padrão e o servidor derruba a
        // conexão (RST_STREAM "Use HTTP/1.1 for request"), confirmado no teste de 2026-08-17.
        HttpClient client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .sslContext(sslContext(empresa))
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/soap+xml; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(envelope, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        // Nível info de propósito nessa fase experimental (docs/EMISSAO-NFE.md) — não tem
        // outro jeito de ver o XML de verdade que foi pra SEFAZ numa rejeição, já que só o
        // caminho de sucesso persiste alguma coisa (NfeEmissaoService.salvarEmitida).
        log.info("SEFAZ [{}] envio -> {}", url, envelope);
        log.info("SEFAZ [{}] resposta <- {}", url, response.body());
        return response.body();
    }

    /**
     * mTLS: a SEFAZ exige o certificado do emitente como client cert na conexão HTTPS, não
     * só a assinatura do XML — mesmo keystore de {@link NfeXmlSigner}. Confiança no
     * servidor: truststore padrão da JVM **mesclado** com a cadeia ICP-Brasil embarcada em
     * {@code nfe/icp-brasil-chain.pem} (confirmado em 2026-08-17 contra
     * homologacao.nfe.fazenda.sp.gov.br e nfe.fazenda.sp.gov.br via {@code openssl
     * s_client}: intermediária "AC SOLUTI SSL EV G4" + raiz "Autoridade Certificadora Raiz
     * Brasileira v10", baixada de acraiz.icpbrasil.gov.br) — essa AC normalmente não está
     * no truststore padrão da JVM, e foi exatamente o erro visto no primeiro teste
     * ("PKIX path building failed... unable to find valid certification path"). Embarcado
     * como recurso do projeto (não modifica o cacerts do JDK do sistema — não precisa de
     * admin, é reproduzível em qualquer máquina).
     */
    private SSLContext sslContext(Empresa empresa) throws Exception {
        KeyStore keyStore = signer.carregarKeyStore(empresa);
        char[] senha = empresa.getCertificadoSenha().toCharArray();

        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, senha);

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(kmf.getKeyManagers(), new TrustManager[]{trustManagerMesclado()}, null);
        return sslContext;
    }

    private X509TrustManager trustManagerMesclado() throws Exception {
        TrustManagerFactory tmfPadrao = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmfPadrao.init((KeyStore) null);

        KeyStore icpBrasil = KeyStore.getInstance(KeyStore.getDefaultType());
        icpBrasil.load(null, null);
        try (InputStream in = getClass().getResourceAsStream("/br/com/axialsoftware/axctg3/nfe/icp-brasil-chain.pem")) {
            if (in == null) {
                throw new IllegalStateException("nfe/icp-brasil-chain.pem não encontrado no classpath");
            }
            CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
            Collection<? extends java.security.cert.Certificate> certificados = certificateFactory.generateCertificates(in);
            int i = 0;
            for (java.security.cert.Certificate certificado : certificados) {
                icpBrasil.setCertificateEntry("icp-brasil-" + i, certificado);
                i++;
            }
        }
        TrustManagerFactory tmfIcpBrasil = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmfIcpBrasil.init(icpBrasil);

        List<X509TrustManager> delegados = new ArrayList<>();
        for (TrustManager tm : tmfPadrao.getTrustManagers()) {
            if (tm instanceof X509TrustManager x509) {
                delegados.add(x509);
            }
        }
        for (TrustManager tm : tmfIcpBrasil.getTrustManagers()) {
            if (tm instanceof X509TrustManager x509) {
                delegados.add(x509);
            }
        }
        return new CompositeX509TrustManager(delegados);
    }

    private Resposta interpretarResposta(String xmlResposta) throws ParserConfigurationException, SAXException, IOException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(new ByteArrayInputStream(xmlResposta.getBytes(StandardCharsets.UTF_8)));

        Element infProt = primeiroOuNull(doc, "infProt");
        Integer cStat = null;
        String xMotivo = null;
        String xmlProtNFe = null;
        if (infProt != null) {
            cStat = inteiro(texto(infProt, "cStat"));
            xMotivo = texto(infProt, "xMotivo");
            xmlProtNFe = serializar(primeiroOuNull(doc, "protNFe"));
        } else {
            Element retorno = primeiroOuNull(doc, "retEnviNFe");
            if (retorno == null) {
                retorno = primeiroOuNull(doc, "retConsReciNFe");
            }
            if (retorno == null) {
                retorno = primeiroOuNull(doc, "retConsStatServ");
            }
            if (retorno != null) {
                cStat = inteiro(texto(retorno, "cStat"));
                xMotivo = texto(retorno, "xMotivo");
            }
        }
        String nRec = texto(primeiroOuNull(doc, "infRec"), "nRec");
        return new Resposta(cStat, xMotivo, nRec, xmlProtNFe);
    }

    private Element primeiroOuNull(Document doc, String tag) {
        NodeList lista = doc.getElementsByTagName(tag);
        return lista.getLength() > 0 ? (Element) lista.item(0) : null;
    }

    private String texto(Element pai, String tag) {
        if (pai == null) {
            return null;
        }
        NodeList lista = pai.getElementsByTagName(tag);
        return lista.getLength() > 0 ? lista.item(0).getTextContent() : null;
    }

    private Integer inteiro(String texto) {
        return texto == null || texto.isBlank() ? null : Integer.valueOf(texto.trim());
    }

    private String serializar(Element el) {
        if (el == null) {
            return null;
        }
        try {
            javax.xml.transform.Transformer transformer = javax.xml.transform.TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(javax.xml.transform.OutputKeys.OMIT_XML_DECLARATION, "yes");
            java.io.StringWriter writer = new java.io.StringWriter();
            transformer.transform(new javax.xml.transform.dom.DOMSource(el), new javax.xml.transform.stream.StreamResult(writer));
            return writer.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
