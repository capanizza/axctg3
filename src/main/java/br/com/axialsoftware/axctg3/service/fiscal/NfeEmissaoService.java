package br.com.axialsoftware.axctg3.service.fiscal;

import br.com.axialsoftware.axctg3.entity.cadastros.Empresa;
import br.com.axialsoftware.axctg3.entity.fiscal.Nfe;
import br.com.axialsoftware.axctg3.entity.fiscal.NotaSaida;
import io.jmix.core.DataManager;
import io.jmix.core.FetchPlan;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Orquestra a emissão própria de NFe (docs/EMISSAO-NFE.md): monta o XML
 * ({@link NfeXmlBuilder}), assina ({@link NfeXmlSigner}), transmite pra SEFAZ
 * ({@link NfeWebserviceClient}) e, se autorizada, salva o resultado reaproveitando
 * {@link NfeImportService#salvarEmitida(byte[])} — a NFe emitida vira uma linha em
 * {@code Nfe} exatamente como uma importada, e {@code NotaSaida.chave} é atualizado
 * separadamente (mesmo motivo de não haver FK entre as duas entidades).
 */
@Service
public class NfeEmissaoService {

    private final DataManager dataManager;
    private final NfeXmlBuilder xmlBuilder;
    private final NfeXmlSigner signer;
    private final NfeWebserviceClient client;
    private final NfeImportService importService;

    public NfeEmissaoService(DataManager dataManager, NfeXmlBuilder xmlBuilder, NfeXmlSigner signer,
                              NfeWebserviceClient client, NfeImportService importService) {
        this.dataManager = dataManager;
        this.xmlBuilder = xmlBuilder;
        this.signer = signer;
        this.client = client;
        this.importService = importService;
    }

    public record ResultadoEmissao(boolean sucesso, String chave, String protocolo, String motivo) {
    }

    public ResultadoEmissao emitir(UUID notaSaidaId) {
        NotaSaida notaSaida = carregarComFetchPlan(notaSaidaId);
        if (notaSaida.getChave() != null && !notaSaida.getChave().isBlank()) {
            return new ResultadoEmissao(false, null, null, "Nota já emitida (chave " + notaSaida.getChave() + ")");
        }

        Empresa empresa = dataManager.load(Empresa.class)
                .query("select e from Empresa e where e.codigo = :codigo")
                .parameter("codigo", notaSaida.getCodEmpresa())
                .optional()
                .orElse(null);
        if (empresa == null) {
            return new ResultadoEmissao(false, null, null, "Empresa não encontrada");
        }

        NfeXmlBuilder.Resultado construido;
        try {
            construido = xmlBuilder.construir(notaSaida);
        } catch (Exception e) {
            return new ResultadoEmissao(false, null, null, "Erro ao montar XML: " + e.getMessage());
        }

        Document assinado;
        try {
            assinado = signer.assinar(construido.documento(), construido.chave(), empresa);
        } catch (Exception e) {
            return new ResultadoEmissao(false, construido.chave(), null, "Erro ao assinar: " + e.getMessage());
        }
        String xmlNfeAssinada = serializar(assinado.getDocumentElement());

        NfeWebserviceClient.Resposta resposta;
        try {
            resposta = client.autorizar(xmlNfeAssinada.getBytes(StandardCharsets.UTF_8), empresa);
            int tentativa = 0;
            while (resposta.loteRecebido() && resposta.nRec() != null && tentativa < 3) {
                Thread.sleep(3000);
                resposta = client.consultarRecibo(resposta.nRec(), empresa);
                tentativa++;
            }
        } catch (Exception e) {
            return new ResultadoEmissao(false, construido.chave(), null,
                    "Erro de comunicação com a SEFAZ: " + e.getMessage());
        }

        if (!resposta.autorizada()) {
            return new ResultadoEmissao(false, construido.chave(), null,
                    "Rejeitada (cStat=" + resposta.cStat() + "): " + resposta.xMotivo());
        }

        String nfeProcXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<nfeProc versao=\"4.00\" xmlns=\"http://www.portalfiscal.inf.br/nfe\">"
                + xmlNfeAssinada
                + resposta.xmlProtNFe()
                + "</nfeProc>";
        Nfe nfeSalva = importService.salvarEmitida(nfeProcXml.getBytes(StandardCharsets.UTF_8));
        nfeSalva.setXmlEnvio(xmlNfeAssinada);
        nfeSalva.setXmlRetorno(resposta.xmlProtNFe());
        dataManager.save(nfeSalva);

        notaSaida.setChave(construido.chave());
        dataManager.save(notaSaida);

        return new ResultadoEmissao(true, construido.chave(), nfeSalva.getProtNProt(), null);
    }

    private NotaSaida carregarComFetchPlan(UUID id) {
        return dataManager.load(NotaSaida.class)
                .id(id)
                .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE)
                        .add("parceiro", fpParceiro -> fpParceiro.addFetchPlan(FetchPlan.BASE)
                                .add("municipio", FetchPlan.BASE)
                                .add("tipoLogradouro", FetchPlan.BASE))
                        .add("natureza", fpNatureza -> fpNatureza.addFetchPlan(FetchPlan.BASE)
                                .add("classTrib", FetchPlan.BASE))
                        .add("itens", fpItens -> fpItens.addFetchPlan(FetchPlan.BASE)
                                .add("produto", fpProduto -> fpProduto.addFetchPlan(FetchPlan.BASE)
                                        .add("classificacaoFiscal", FetchPlan.BASE)
                                        .add("classTrib", FetchPlan.BASE))))
                .one();
    }

    private String serializar(org.w3c.dom.Element el) {
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
