package br.com.axialsoftware.axctg3.service.fiscal;

import br.com.axialsoftware.axctg3.entity.cadastros.Empresa;
import br.com.axialsoftware.axctg3.entity.enums.AmbienteNfe;
import br.com.axialsoftware.axctg3.entity.fiscal.Nfe;
import io.jmix.core.DataManager;
import io.jmix.core.SaveContext;
import io.jmix.data.Sequence;
import io.jmix.data.Sequences;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Transmissão da NFe digitada: numera, gera a chave, escreve o XML com
 * {@link NfeXmlSerializer}, assina e envia — e grava o resultado no próprio registro do
 * rascunho, que passa a ser uma NFe como qualquer outra (DANFE, cancelamento, CC-e e
 * exportação já trabalham a partir da {@link Nfe}).
 *
 * <p>Mesma proteção contra resposta perdida do {@link NfeEmissaoService}: a chave e o XML
 * assinado são gravados ({@code chaveTentativa}/{@code xmlEnvio}) ANTES do envio. Se a
 * resposta se perder, a próxima tentativa consulta a SEFAZ primeiro: autorizada, completa o
 * registro com o XML guardado; não localizada (217/218), reenvia com a mesma chave.
 *
 * <p>O número sai da mesma sequência da NotaSaida ({@code nota_saida_seq_<codEmpresa>}, ver
 * {@code NotaSaidaEventListener}) — as duas emitem na mesma série, e números próprios
 * colidiriam. É tirado na primeira transmissão, não ao criar o rascunho, pra rascunho
 * abandonado não deixar buraco na numeração.
 */
@Service
public class NfeDigitadaEmissaoService {

    private static final String HOMOLOGACAO_X_NOME = "NF-E EMITIDA EM AMBIENTE DE HOMOLOGACAO - SEM VALOR FISCAL";

    private final DataManager dataManager;
    private final NfeDigitadaService nfeDigitadaService;
    private final NfeXmlSerializer serializer;
    private final NfeXmlSigner signer;
    private final NfeWebserviceClient client;
    private final NfeChaveService chaveService;
    private final NfeXmlParser parser;
    private final Sequences sequences;

    public NfeDigitadaEmissaoService(DataManager dataManager, NfeDigitadaService nfeDigitadaService,
                                     NfeXmlSerializer serializer, NfeXmlSigner signer, NfeWebserviceClient client,
                                     NfeChaveService chaveService, NfeXmlParser parser, Sequences sequences) {
        this.dataManager = dataManager;
        this.nfeDigitadaService = nfeDigitadaService;
        this.serializer = serializer;
        this.signer = signer;
        this.client = client;
        this.chaveService = chaveService;
        this.parser = parser;
        this.sequences = sequences;
    }

    public record Resultado(boolean sucesso, String chave, String protocolo, String motivo) {
    }

    public Resultado transmitir(UUID nfeId) {
        Nfe nfe = nfeDigitadaService.carregarCompleta(nfeId);
        if (!Boolean.TRUE.equals(nfe.getDigitada())) {
            return falha("Esta NFe não é digitada — emita pela nota de saída");
        }
        if (nfe.getChave() != null) {
            return falha("NFe já autorizada (chave " + nfe.getChave() + ")");
        }
        Empresa empresa = dataManager.load(Empresa.class)
                .query("select e from Empresa e where e.codigo = :codigo")
                .parameter("codigo", nfe.getCodEmpresa())
                .optional()
                .orElse(null);
        String erroEmpresa = validarEmpresa(empresa);
        if (erroEmpresa != null) {
            return falha(erroEmpresa);
        }

        // Tentativa anterior sem resposta confirmada: pergunta à SEFAZ antes de qualquer coisa
        if (nfe.getChaveTentativa() != null) {
            Resultado pendente = resolverTentativaPendente(nfe, empresa);
            if (pendente != null) {
                return pendente;
            }
        }

        nfeDigitadaService.preencherEmitente(nfe, empresa);
        if (nfe.getCodMunFg() == null) {
            // município do fato gerador: na falta, o do emitente (o mesmo que o rascunho já traz)
            nfe.setCodMunFg(nfe.getEmitCMun());
        }
        if (empresa.getAmbienteNfe() == AmbienteNfe.HOMOLOGACAO) {
            // exigência da própria SEFAZ em homologação (cStat=598), igual ao NfeXmlBuilder
            nfe.setDestXNome(HOMOLOGACAO_X_NOME);
        }
        if (nfe.getSerie() == null) {
            return falha("Série da NFe não configurada na empresa (aba \"Emissão NFe\")");
        }
        List<String> divergencias = nfeDigitadaService.divergenciasDeTotais(nfe);
        if (!divergencias.isEmpty()) {
            return falha("Os totais da nota não batem com a soma dos itens — use \"Recalcular totais\" ou "
                    + "corrija à mão: " + String.join("; ", divergencias));
        }

        if (nfe.getNumeroNf() == null) {
            long numero = sequences.createNextValue(Sequence.withName("nota_saida_seq_" + nfe.getCodEmpresa()));
            nfe.setNumeroNf(Math.toIntExact(numero));
        }
        OffsetDateTime dhEmi = OffsetDateTime.now(ZoneOffset.of("-03:00")).truncatedTo(ChronoUnit.SECONDS);
        nfe.setDhEmi(dhEmi);
        // dhSaiEnt não pode ser anterior a dhEmi (cStat=506)
        if (nfe.getDhSaiEnt() != null && nfe.getDhSaiEnt().isBefore(dhEmi)) {
            nfe.setDhSaiEnt(dhEmi);
        }
        // mesmo cNF da tentativa anterior quando ainda é o mesmo mês (NfeXmlBuilder.resolverCNf)
        Integer cNf = chaveService.gerarCNf();
        if (nfe.getChaveTentativa() != null && nfe.getChaveTentativa().length() == 44) {
            String aammAtual = String.format("%02d%02d", dhEmi.getYear() % 100, dhEmi.getMonthValue());
            if (chaveService.extrairAamm(nfe.getChaveTentativa()).equals(aammAtual)) {
                cNf = chaveService.extrairCNf(nfe.getChaveTentativa());
            }
        }
        Integer tpEmis = nfe.getTpEmis() != null ? nfe.getTpEmis() : 1;
        String chave = chaveService.gerarChave(nfe.getCodUf(), dhEmi.toLocalDate(), nfe.getEmitCnpj(), 55,
                nfe.getSerie(), nfe.getNumeroNf(), tpEmis, cNf);
        nfe.setCodNf(cNf);
        nfe.setCodDv(Integer.parseInt(chave.substring(43)));
        nfe.setMod(55);
        nfe.setTpEmis(tpEmis);

        // o serializador lê a chave da própria Nfe; só vira "chave" de verdade se autorizar
        nfe.setChave(chave);
        String xmlAssinado;
        try {
            Document documento = serializer.serializar(nfe);
            xmlAssinado = serializar(signer.assinar(documento, chave, empresa));
        } catch (Exception e) {
            return falha("Erro ao montar/assinar o XML: " + e.getMessage());
        } finally {
            nfe.setChave(null);
        }

        // gravado ANTES do envio — sobrevive a timeout/resposta perdida
        nfe.setChaveTentativa(chave);
        nfe.setXmlEnvio(xmlAssinado);
        nfe = salvarTudo(nfe);

        NfeWebserviceClient.Resposta resposta;
        try {
            resposta = client.autorizar(xmlAssinado.getBytes(StandardCharsets.UTF_8), empresa);
            int tentativa = 0;
            while (resposta.loteRecebido() && resposta.nRec() != null && tentativa < 3) {
                Thread.sleep(3000);
                resposta = client.consultarRecibo(resposta.nRec(), empresa);
                tentativa++;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Resultado(false, chave, null, "Envio interrompido — use \"Transmitir\" de novo para consultar a SEFAZ");
        } catch (Exception e) {
            return new Resultado(false, chave, null, "Erro de comunicação com a SEFAZ: " + e.getMessage()
                    + ". Use \"Transmitir\" de novo: o sistema consulta a SEFAZ antes de reenviar.");
        }
        if (!resposta.autorizada()) {
            // rejeição: a SEFAZ não registrou a chave, então nada fica pendente — limpa a
            // tentativa pra nota voltar a ser editável (NfeDetailView trava a pendente). O
            // número continua reservado e é reaproveitado na próxima transmissão.
            nfe.setChaveTentativa(null);
            nfe.setXmlEnvio(null);
            salvarTudo(nfe);
            return new Resultado(false, chave, null, "Rejeitada (cStat=" + resposta.cStat() + "): " + resposta.xMotivo());
        }
        return completar(nfe, chave, xmlAssinado, resposta.xmlProtNFe());
    }

    /**
     * {@code null} quando a tentativa anterior não foi registrada pela SEFAZ e a transmissão
     * deve seguir normalmente (com a mesma chave, se ainda for o mesmo mês); senão o
     * resultado final (autorizada agora completada, ou situação que o operador resolve).
     */
    private Resultado resolverTentativaPendente(Nfe nfe, Empresa empresa) {
        String chaveTentativa = nfe.getChaveTentativa();
        NfeWebserviceClient.RespostaConsulta consulta;
        try {
            consulta = client.consultarProtocolo(chaveTentativa, empresa);
        } catch (Exception e) {
            return new Resultado(false, chaveTentativa, null, "Tentativa anterior pendente (chave " + chaveTentativa
                    + ") — não foi possível confirmar com a SEFAZ: " + e.getMessage() + ". Tente de novo em instantes.");
        }
        if (consulta.cStat() != null && consulta.cStat() == 100) {
            if (nfe.getXmlEnvio() == null) {
                return new Resultado(false, chaveTentativa, consulta.nProt(), "NFe autorizada na SEFAZ (protocolo "
                        + consulta.nProt() + "), mas o XML enviado não ficou gravado — contate o suporte.");
            }
            String protNFe = extrairProtNFe(consulta.xmlRetConsSitNFe());
            if (protNFe == null) {
                return new Resultado(false, chaveTentativa, consulta.nProt(), "NFe autorizada na SEFAZ (protocolo "
                        + consulta.nProt() + "), mas a consulta não devolveu o protocolo completo — tente de novo.");
            }
            return completar(nfe, chaveTentativa, nfe.getXmlEnvio(), protNFe);
        }
        if (consulta.cStat() != null && (consulta.cStat() == 217 || consulta.cStat() == 218)) {
            return null;
        }
        return new Resultado(false, chaveTentativa, null, "Tentativa anterior pendente (chave " + chaveTentativa
                + ") — SEFAZ retornou cStat=" + consulta.cStat() + ": " + consulta.xMotivo()
                + ". Resolva antes de tentar de novo.");
    }

    // Autorizada: o rascunho vira a NFe — chave, protocolo e XML oficial (nfeProc) no próprio registro
    private Resultado completar(Nfe nfe, String chave, String xmlAssinado, String xmlProtNFe) {
        String nfeProcXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<nfeProc versao=\"4.00\" xmlns=\"http://www.portalfiscal.inf.br/nfe\">"
                + xmlAssinado
                + xmlProtNFe
                + "</nfeProc>";
        Nfe protocolo = parser.parse(nfeProcXml.getBytes(StandardCharsets.UTF_8));
        nfe.setChave(chave);
        nfe.setChaveTentativa(null);
        nfe.setXmlEnvio(xmlAssinado);
        nfe.setXmlRetorno(nfeProcXml);
        nfe.setProtTpAmb(protocolo.getProtTpAmb());
        nfe.setProtVerAplic(protocolo.getProtVerAplic());
        nfe.setProtDhRecbto(protocolo.getProtDhRecbto());
        nfe.setProtNProt(protocolo.getProtNProt());
        nfe.setProtDigVal(protocolo.getProtDigVal());
        nfe.setProtCStat(protocolo.getProtCStat());
        nfe.setProtXMotivo(protocolo.getProtXMotivo());
        salvarTudo(nfe);
        return new Resultado(true, chave, protocolo.getProtNProt(), null);
    }

    // emitente/totais/vItem/numeração podem ter mudado nos itens também — grava o grafo todo
    private Nfe salvarTudo(Nfe nfe) {
        SaveContext saveContext = new SaveContext().saving(nfe);
        nfe.getItens().forEach(saveContext::saving);
        dataManager.save(saveContext);
        return nfeDigitadaService.carregarCompleta(nfe.getId());
    }

    private String validarEmpresa(Empresa empresa) {
        if (empresa == null) {
            return "Empresa da NFe não encontrada";
        }
        if (empresa.getCrt() == null || empresa.getAmbienteNfe() == null) {
            return "Empresa sem regime tributário (CRT) ou ambiente de NFe configurado — ver aba \"Emissão NFe\" do cadastro";
        }
        if (empresa.getCertificadoArquivo() == null || empresa.getCertificadoSenha() == null) {
            return "Empresa sem certificado digital configurado — ver aba \"Emissão NFe\" do cadastro";
        }
        if (empresa.getMunicipio() == null) {
            return "Empresa sem município cadastrado — necessário pra cUF da NFe";
        }
        if (NfeXml.somenteDigitos(empresa.getCnpj()).length() != 14) {
            return "Empresa sem CNPJ válido cadastrado";
        }
        if (empresa.getInscEst() == null || empresa.getInscEst().isBlank()) {
            return "Empresa sem Inscrição Estadual cadastrada — preencha com o número ou com \"ISENTO\"";
        }
        return null;
    }

    // <protNFe ...>...</protNFe> de dentro do retConsSitNFe
    private static String extrairProtNFe(String xmlRetConsSit) {
        if (xmlRetConsSit == null) {
            return null;
        }
        int inicio = xmlRetConsSit.indexOf("<protNFe");
        int fim = xmlRetConsSit.indexOf("</protNFe>");
        if (inicio < 0 || fim < 0) {
            return null;
        }
        return xmlRetConsSit.substring(inicio, fim + "</protNFe>".length());
    }

    private static Resultado falha(String motivo) {
        return new Resultado(false, null, null, motivo);
    }

    private static String serializar(Document doc) throws Exception {
        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(doc.getDocumentElement()), new StreamResult(writer));
        return writer.toString();
    }
}
