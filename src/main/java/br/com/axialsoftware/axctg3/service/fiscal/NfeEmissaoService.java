package br.com.axialsoftware.axctg3.service.fiscal;

import br.com.axialsoftware.axctg3.entity.cadastros.Empresa;
import br.com.axialsoftware.axctg3.entity.enums.FinNfe;
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
 *
 * <p>{@code NotaSaida.chaveTentativa} é gravada assim que a chave é calculada, antes de
 * assinar/transmitir — sobrevive a erro de comunicação/timeout com a SEFAZ (cenário em que
 * a nota pode ter sido autorizada de verdade sem a resposta ter chegado). Quando existe uma
 * tentativa pendente, {@link #emitir} NÃO reenvia direto — {@link #resolverTentativaPendente}
 * consulta a SEFAZ primeiro ({@code NFeConsultaProtocolo4}) com essa mesma chave: se autorizada,
 * só completa o registro local (o {@code Nfe} normalmente já foi gravado, só o
 * {@code NotaSaida.chave} que não confirmou); se "não localizada", segue pra
 * {@link #transmitir}, que reaproveita a mesma chave em vez de sortear outra
 * ({@link NfeXmlBuilder#resolverCNf}) — reenviar a MESMA chave de uma tentativa que tinha
 * sido autorizada é rejeitado pela SEFAZ como duplicidade (cStat=539), em vez de autorizar
 * duas NFe pro mesmo número. Enquanto pendente, essa chave também serve de reserva pra
 * {@code NotaSaidaListView.onNotaSaidasDataGridConsultarNfeAction} (consulta manual, sem
 * tentar reemitir).
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

        String erroFinalidade = validarFinalidade(notaSaida);
        if (erroFinalidade != null) {
            return new ResultadoEmissao(false, null, null, erroFinalidade);
        }

        Empresa empresa = dataManager.load(Empresa.class)
                .query("select e from Empresa e where e.codigo = :codigo")
                .parameter("codigo", notaSaida.getCodEmpresa())
                .optional()
                .orElse(null);
        if (empresa == null) {
            return new ResultadoEmissao(false, null, null, "Empresa não encontrada");
        }

        // Já existe uma tentativa pendente (resposta anterior perdida/timeout) — não
        // reenvia às cegas: consulta primeiro (mesma chave, ver resolverTentativaPendente).
        // Reenviar direto bateria em cStat=539 "Duplicidade de NF-e" se a tentativa anterior
        // TINHA sido autorizada (confirmado em teste real 2026-09-02) — a consulta resolve
        // isso antes de decidir se completa o registro local ou tenta de novo.
        if (notaSaida.getChaveTentativa() != null && !notaSaida.getChaveTentativa().isBlank()) {
            return resolverTentativaPendente(notaSaida, empresa);
        }

        return transmitir(notaSaida, empresa);
    }

    /**
     * NFe Complementar (finNFe=2) precisa de {@code chaveNotaOriginal} (44 dígitos) pra
     * montar o grupo {@code NFref} — sem isso a SEFAZ aceitaria como NFe normal disfarçada,
     * sem vínculo com a nota original (ver {@code NfeXmlBuilder.construirIde}). O caminho
     * inverso (finalidade normal com chave preenchida) também é barrado — mesmo raciocínio
     * de outras checagens de consistência do projeto: mais barato travar aqui do que deixar
     * um dado inconsistente virar XML.
     */
    private String validarFinalidade(NotaSaida notaSaida) {
        FinNfe finNfe = notaSaida.getFinNfe() != null ? notaSaida.getFinNfe() : FinNfe.NORMAL;
        String chaveNotaOriginal = notaSaida.getChaveNotaOriginal();
        boolean temChaveOriginal = chaveNotaOriginal != null && !chaveNotaOriginal.isBlank();

        if (finNfe == FinNfe.NORMAL) {
            if (temChaveOriginal) {
                return "Chave de nota original preenchida numa NFe normal — mude a finalidade "
                        + "pra complementar ou limpe o campo";
            }
            return null;
        }

        if (finNfe == FinNfe.COMPLEMENTAR) {
            if (!temChaveOriginal || !chaveNotaOriginal.trim().matches("\\d{44}")) {
                return "NFe complementar precisa referenciar a chave da NFe original (44 dígitos)";
            }
            return null;
        }

        // AJUSTE/DEVOLUCAO existem no enum só por completude do código oficial — não são
        // emitidos por esta versão (ver Javadoc de FinNfe).
        return "Finalidade \"" + finNfe + "\" ainda não é emitida por este sistema";
    }

    /**
     * Resolve uma {@code chaveTentativa} pendente via {@code NFeConsultaProtocolo4} antes de
     * decidir o que fazer — nunca reenvia sem saber o que a SEFAZ diz sobre essa chave
     * específica.
     */
    private ResultadoEmissao resolverTentativaPendente(NotaSaida notaSaida, Empresa empresa) {
        String chaveTentativa = notaSaida.getChaveTentativa();
        NfeWebserviceClient.RespostaConsulta consulta;
        try {
            consulta = client.consultarProtocolo(chaveTentativa, empresa);
        } catch (Exception e) {
            return new ResultadoEmissao(false, chaveTentativa, null,
                    "Tentativa anterior pendente (chave " + chaveTentativa + ") — não foi possível confirmar "
                            + "com a SEFAZ: " + e.getMessage() + ". Tente de novo em instantes, ou use \"Consultar NFe\".");
        }

        if (consulta.cStat() != null && consulta.cStat() == 100) {
            return completarComProtocoloConfirmado(notaSaida, chaveTentativa, consulta.nProt());
        }

        if (consulta.cStat() != null && (consulta.cStat() == 217 || consulta.cStat() == 218)) {
            // Não localizada — a SEFAZ nunca recebeu essa tentativa, reenviar é seguro.
            // transmitir() reaproveita a MESMA chaveTentativa (NfeXmlBuilder.resolverCNf),
            // não sorteia uma nova.
            return transmitir(notaSaida, empresa);
        }

        // Qualquer outro cStat (denegada etc.) — não decide sozinho, devolve a situação pro
        // usuário resolver antes de tentar de novo.
        return new ResultadoEmissao(false, chaveTentativa, null,
                "Tentativa anterior pendente (chave " + chaveTentativa + ") — SEFAZ retornou cStat="
                        + consulta.cStat() + ": " + consulta.xMotivo() + ". Resolva antes de tentar de novo.");
    }

    /**
     * Completa {@code NotaSaida.chave}/zera {@code chaveTentativa} pra uma chave já
     * confirmada autorizada (cStat=100) via {@code NFeConsultaProtocolo4} — usado tanto por
     * {@link #resolverTentativaPendente} quanto por {@link #completarSeAutorizada} (chamado
     * direto pela UI, ver {@code NotaSaidaListView.consultarEExibir}, quando "Consultar NFe"
     * já mostrou o resultado autorizado e a nota ainda não tinha isso salvo). O {@code Nfe}
     * já pode ter sido gravado antes da resposta se perder da primeira vez (caso confirmado
     * em teste real: {@link NfeImportService#salvarEmitida} já tinha rodado; só o segundo
     * {@code save()} de {@code NotaSaida.chave} falhou depois). Sem o XML completo (a
     * consulta só devolve {@code protNFe}, não os itens/detalhes da nota), não dá pra
     * reconstruir um {@code Nfe} que ainda não existe — só completa o que já existe.
     */
    private ResultadoEmissao completarComProtocoloConfirmado(NotaSaida notaSaida, String chave, String nProt) {
        boolean nfeJaGravada = dataManager.load(Nfe.class)
                .query("select e from Nfe e where e.chave = :chave")
                .parameter("chave", chave)
                .optional()
                .isPresent();
        if (!nfeJaGravada) {
            return new ResultadoEmissao(false, chave, nProt,
                    "NFe autorizada na SEFAZ (protocolo " + nProt + "), mas o sistema não tem o "
                            + "XML completo pra registrar a nota — contate o suporte.");
        }
        notaSaida.setChave(chave);
        notaSaida.setChaveTentativa(null);
        dataManager.save(notaSaida);
        return new ResultadoEmissao(true, chave, nProt, null);
    }

    /**
     * Completa o registro local a partir de uma {@link NfeWebserviceClient.RespostaConsulta}
     * que a UI já obteve (evita perguntar duas vezes pra SEFAZ) — usado por "Consultar NFe"
     * (só {@code NotaSaidaListView}, {@code Nfe} sempre já tem chave confirmada) quando o
     * resultado veio autorizado e {@code NotaSaida.chave} ainda está vazia.
     */
    public ResultadoEmissao completarSeAutorizada(UUID notaSaidaId, NfeWebserviceClient.RespostaConsulta consulta) {
        if (consulta.cStat() == null || consulta.cStat() != 100) {
            return new ResultadoEmissao(false, null, consulta.nProt(),
                    "Chave não está autorizada (cStat=" + consulta.cStat() + ": " + consulta.xMotivo() + ")");
        }
        NotaSaida notaSaida = carregarComFetchPlan(notaSaidaId);
        if (notaSaida.getChave() != null && !notaSaida.getChave().isBlank()) {
            return new ResultadoEmissao(true, notaSaida.getChave(), consulta.nProt(), null);
        }
        String chave = notaSaida.getChaveTentativa();
        if (chave == null || chave.isBlank()) {
            return new ResultadoEmissao(false, null, null, "Nota sem chave calculada pra confirmar");
        }
        return completarComProtocoloConfirmado(notaSaida, chave, consulta.nProt());
    }

    /** Monta o XML, assina e transmite pra SEFAZ — usado tanto numa primeira tentativa quanto numa reemissão segura. */
    private ResultadoEmissao transmitir(NotaSaida notaSaida, Empresa empresa) {
        NfeXmlBuilder.Resultado construido;
        try {
            construido = xmlBuilder.construir(notaSaida);
        } catch (Exception e) {
            return new ResultadoEmissao(false, null, null, "Erro ao montar XML: " + e.getMessage());
        }

        // Gravada ANTES de assinar/transmitir — sobrevive a qualquer falha depois daqui
        // (erro de comunicação, timeout, resposta perdida). Serve de reserva pra "Consultar
        // NFe" (NotaSaidaListView) enquanto a SEFAZ não confirma, e é reaproveitada pelo
        // NfeXmlBuilder numa reemissão (resolverCNf) em vez de gerar uma chave nova — evita
        // duas NFe autorizadas pro mesmo número se essa tentativa na verdade tinha sido
        // autorizada e só a resposta se perdeu.
        notaSaida.setChaveTentativa(construido.chave());
        // save() devolve a entidade mesclada com a VERSION nova — reatribuir é obrigatório
        // aqui: o segundo save() mais adiante usa esse mesmo notaSaida, e salvar de novo com
        // a VERSION antiga (do objeto carregado no início do método) dispara "objeto
        // alterado por outro" (OptimisticLockException) mesmo sem nenhuma edição
        // concorrente de verdade — confirmado em teste real 2026-09-02.
        notaSaida = dataManager.save(notaSaida);

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
        notaSaida.setChaveTentativa(null);
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
