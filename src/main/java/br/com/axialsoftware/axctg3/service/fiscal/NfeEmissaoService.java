package br.com.axialsoftware.axctg3.service.fiscal;

import br.com.axialsoftware.axctg3.entity.cadastros.Empresa;
import br.com.axialsoftware.axctg3.entity.enums.FinNfe;
import br.com.axialsoftware.axctg3.entity.fiscal.ItemNotaSaida;
import br.com.axialsoftware.axctg3.entity.fiscal.Nfe;
import br.com.axialsoftware.axctg3.entity.fiscal.NotaSaida;
import br.com.axialsoftware.axctg3.entity.fiscal.Produto;
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
import java.math.BigDecimal;
import java.math.RoundingMode;
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

        // NFe Complementar sem item nenhum lançado manualmente — gera o "pseudo item" a
        // partir do cabeçalho (valorMercadoria/baseIcms/valorIcms/baseIpi/valorIpi), mesmo
        // padrão do legado AxFat (F_Complemento.pas). Sempre na hora de emitir, nunca num
        // listener de salvamento — assim nunca fica desatualizado se o operador editar o
        // cabeçalho de novo depois de um primeiro save (ver gerarItemComplementar).
        if (notaSaida.getFinNfe() == FinNfe.COMPLEMENTAR && notaSaida.getItens().isEmpty()) {
            String erroItem = gerarItemComplementar(notaSaida, empresa);
            if (erroItem != null) {
                return new ResultadoEmissao(false, null, null, erroItem);
            }
            notaSaida = carregarComFetchPlan(notaSaida.getId());
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
     * Gera o único item de uma NFe Complementar (finNFe=2) que o operador não lançou
     * manualmente — mesmo padrão do legado AxFat (`F_Complemento.pas`): o operador só
     * edita o cabeçalho da nota ("Valores calculados"), o item é montado sozinho aqui.
     *
     * <p>Diferente do legado (onde o XML pegava {@code vProd}/{@code vICMS} direto do
     * cabeçalho, independente da quantidade do item), {@link NfeXmlBuilder} usa {@code
     * item.getSubTotal()} (quantidade×valorUnitario) pra {@code vProd} — então o item
     * precisa nascer matematicamente equivalente ao cabeçalho: {@code quantidade=1}/
     * {@code valorUnitario=valorMercadoria} quando há diferença de preço, {@code 0}/{@code
     * 0} quando é só imposto (senão {@code ICMSTot/vProd}, que {@link NfeXmlBuilder} ainda
     * lê direto de {@code NotaSaida.valorMercadoria}, ficaria inconsistente com o item —
     * mesma família de bug já corrigida pra frete/IBS-UF, confirmada de novo em
     * homologação 2026-09-03 pra este caso específico antes desta correção existir).
     *
     * <p>ICMS, ICMS-ST e IPI são espelhados — CST sai {@code "10"} (com {@code baseSt}/
     * {@code valorSt} do cabeçalho) quando a complementar tem ST, senão {@code "00"} (ver
     * bloco abaixo). {@code construirIcms} só lê {@code baseSt}/{@code valorSt} do item nos
     * ramos CST 10/60 — os únicos dois casos cobertos aqui.
     */
    private String gerarItemComplementar(NotaSaida notaSaida, Empresa empresa) {
        if (empresa.getProdutoNfeComplementar() == null) {
            return "Nota complementar sem itens — configure um Produto padrão em Empresa "
                    + "(aba \"Emissão NFe\") pra gerar o item automaticamente, ou lance o item manualmente.";
        }
        if (notaSaida.getNatureza() == null || notaSaida.getNatureza().getCfop() == null) {
            return "Natureza de operação sem CFOP configurado — necessário pro item automático da complementar.";
        }

        // Recarrega o produto com fetch plan explícito incluindo classTrib — o placeholder
        // vindo de Empresa está "detached" e sem esse atributo buscado (ItemNotaSaidaEventListener.
        // resolverCodClassTrib lê produto.getClassTrib() ao salvar o item, e um objeto
        // detached não consegue mais buscar lazy; confirmado por IllegalStateException
        // "Cannot get unfetched attribute [classTrib]" rodando os testes).
        Produto produtoPlaceholder = dataManager.load(Produto.class)
                .id(empresa.getProdutoNfeComplementar().getId())
                .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE).add("classTrib", FetchPlan.BASE))
                .one();

        ItemNotaSaida item = dataManager.create(ItemNotaSaida.class);
        item.setNotaSaida(notaSaida);
        item.setItem(1);
        item.setProduto(produtoPlaceholder);
        item.setCfop(notaSaida.getNatureza().getCfop());

        // qCom sempre 1 — mesmo quando a complementar é só de imposto (valorMercadoria
        // zero) — nunca 0. Achado 2026-09-06 investigando o cStat=225 (ver
        // [[axctg3-nfe-complementar-cstat225]]): nenhuma das 183 notas reais de referência
        // ([[nfe-xmls-reais-teste]] + as 7 complementares) tem qCom=0 — mesmo com vUnCom/
        // vProd zerados, qCom nunca é zero. O tipo aceita "0.0000" no padrão regex (já
        // conferido contra o XSD), mas na prática a SEFAZ rejeita quantidade zero como
        // "Falha no Schema XML" (provável minExclusive no tipo, não visível só na regex).
        // item.setQuantidade(null) — que antes só disparava quando valorMercadoria != 0 —
        // fazia NfeXmlBuilder.dec(null,4) cair em "0.0000" nesse caso.
        BigDecimal valorMercadoria = nvl(notaSaida.getValorMercadoria());
        item.setQuantidade(BigDecimal.ONE);
        item.setValorUnitario(valorMercadoria);

        item.setBaseIcms(nvl(notaSaida.getBaseIcms()));
        item.setValorIcms(nvl(notaSaida.getValorIcms()));
        item.setAliqIcms(aliquotaEfetiva(item.getValorIcms(), item.getBaseIcms()));

        // CST 10 (ICMS com ST) quando a complementar carrega baseSt/valorSt no cabeçalho —
        // mesmos campos já editáveis em NotaSaidaComplementarDetailView, mesma mecânica do
        // ICMS "normal" acima. Achado 2026-09-06 (ver [[axctg3-nfe-complementar-cstat225]]):
        // antes disso o CST saía sempre "00", mesmo pra uma nota original CST 10 — estrutural
        // pro schema (CST 00 é uma variante válida por si só), mas não reflete a operação
        // quando o valor complementado é de ICMS-ST. NfeXmlBuilder.construirIcms's CST "10"
        // deriva pMVAST de baseSt/baseIcms — por isso baseIcms tem que estar preenchido
        // também nesse caso (não dá pra complementar só o ST sem base de ICMS).
        BigDecimal baseSt = nvl(notaSaida.getBaseSt());
        BigDecimal valorSt = nvl(notaSaida.getValorSt());
        if (baseSt.compareTo(BigDecimal.ZERO) != 0 || valorSt.compareTo(BigDecimal.ZERO) != 0) {
            item.setBaseSt(baseSt);
            item.setValorSt(valorSt);
            item.setCst("10");
        } else {
            item.setCst("00");
        }

        item.setBaseIpi(nvl(notaSaida.getBaseIpi()));
        item.setValorIpi(nvl(notaSaida.getValorIpi()));
        item.setAliqIpi(aliquotaEfetiva(item.getValorIpi(), item.getBaseIpi()));

        // CST/cClassTrib do IBS/CBS: em princípio os MESMOS da nota original (o
        // NotaSaida.classTrib já é copiado dela ao criar a complementar —
        // NotaSaidaListView.criarComplementar, `nova.setClassTrib(original.getClassTrib())`)
        // — MAS só faz sentido herdar isso quando o pseudo item carrega valor de mercadoria
        // de verdade (complemento de preço). Quando é complemento SÓ de imposto
        // (valorMercadoria=0, este bloco if), o item não representa a mesma operação pro
        // IBS/CBS — não tem base nenhuma pra tributar — e cravar um classTrib "Padrão"
        // (tributação integral) com o grupo gIBSCBS inteiro zerado foi rejeitado pela SEFAZ
        // como "Falha no Schema XML" (achado 2026-09-06, ver
        // [[axctg3-nfe-complementar-cstat225]] — confirmado que a nota original usada no
        // teste também caía no mesmo classTrib genérico "000001", então herdar dela não
        // ajudaria aqui). 410029 "Operações acobertadas somente pelo ICMS" (CST 410, tipo
        // "Sem alíquota") é o código que bate com esse caso — grupo gIBSCBS.
        if (valorMercadoria.compareTo(BigDecimal.ZERO) == 0) {
            item.setCodClassTrib(410029);
        } else if (notaSaida.getClassTrib() != null) {
            item.setCodClassTrib(notaSaida.getClassTrib().getCodigo());
        }

        dataManager.save(item);
        return null;
    }

    private static BigDecimal nvl(BigDecimal valor) {
        return valor == null ? BigDecimal.ZERO : valor;
    }

    /** {@code valor×100/base}, ou zero se a base for zero (evita divisão por zero). */
    private static BigDecimal aliquotaEfetiva(BigDecimal valor, BigDecimal base) {
        if (base == null || base.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return valor.multiply(new BigDecimal(100)).divide(base, 2, RoundingMode.HALF_UP);
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
