package br.com.axialsoftware.axctg3.service.fiscal;

import br.com.axialsoftware.axctg3.entity.cadastros.Empresa;
import br.com.axialsoftware.axctg3.entity.cadastros.Parceiro;
import br.com.axialsoftware.axctg3.entity.enums.CodRegimeTributario;
import br.com.axialsoftware.axctg3.entity.fiscal.ItemNotaSaida;
import br.com.axialsoftware.axctg3.entity.fiscal.NaturezaOperacao;
import br.com.axialsoftware.axctg3.entity.fiscal.NotaSaida;
import br.com.axialsoftware.axctg3.entity.fiscal.PedidoVenda;
import br.com.axialsoftware.axctg3.entity.fiscal.Produto;
import br.com.axialsoftware.axctg3.entity.tabelas.ClassTrib;
import br.com.axialsoftware.axctg3.entity.tabelas.Cst;
import br.com.axialsoftware.axctg3.service.UtilGeralService;
import io.jmix.core.DataManager;
import io.jmix.core.EntityStates;
import io.jmix.core.FetchPlan;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Set;

/**
 * Resolução de CST/cClassTrib e cálculo de ICMS de um {@link ItemNotaSaida} — extraído de
 * {@code ItemNotaSaidaEventListener} em 2026-09-14 pra ser reaproveitado também pela tela
 * (preview ao vivo enquanto o usuário digita quantidade/valorUnitário/produto, antes de
 * salvar — ver {@code ItemNotaSaidaDetailView}). O listener continua sendo quem GRAVA o
 * resultado (é o único lugar com autoridade sobre o valor persistido); esta classe só
 * calcula.
 */
@Service
public class ItemNotaSaidaTributacaoService {

    // CST-IBS/CBS "tributação integral", os 3 primeiros dígitos de um cClassTrib de 6
    // dígitos (ex.: 000000..000499) — ver docs/REFORMA-TRIBUTARIA-IBS-CBS.md. Natureza
    // com esse CST é "rasa": não fixa um tratamento tributário próprio, então quem decide
    // é o produto do item.
    private static final int CST_TRIBUTACAO_INTEGRAL = 0;

    // CST de ICMS "tributação integral" (Cst.codigo, não o CST-IBS/CBS acima — catálogos
    // diferentes, mesmo nome). Mesma ideia de "rasa": natureza com esse CST (ou sem Cst
    // nenhum) não fixa tratamento próprio, quem decide é o Cst do produto.
    private static final String CST_ICMS_TRIBUTACAO_INTEGRAL = "00";

    // CFOPs com CSOSN fixo, como na procedure CALCULACSOSN do legado (banco da GB).
    private static final Set<Integer> CFOP_CSOSN_900 = Set.of(5206, 6206, 5922, 1949);
    private static final Set<Integer> CFOP_CSOSN_400 = Set.of(5902, 5925, 1901, 6902, 6925, 2901);
    // Industrialização por encomenda: só o 1º item da nota leva o imposto, os demais saem 400.
    private static final Set<Integer> CFOP_INDUSTRIALIZACAO = Set.of(5124, 5125);
    // Devolução simbólica do material do cliente — fora do total da nota. Mesma lista do
    // f_nfe.pas do AxFat (5902/5925/1901, regra escrita para GB/Premiere/Goldenplast mas
    // desligada lá por um "TipoRetorno := false" fixo), mais as interestaduais; 1901 é
    // entrada e não aparece numa nota de saída.
    private static final Set<Integer> CFOP_FORA_DO_TOTAL = Set.of(5902, 6902, 5925, 6925);

    private final DataManager dataManager;
    private final EntityStates entityStates;
    private final UtilGeralService utilGeralService;

    public ItemNotaSaidaTributacaoService(DataManager dataManager, EntityStates entityStates,
                                          UtilGeralService utilGeralService) {
        this.dataManager = dataManager;
        this.entityStates = entityStates;
        this.utilGeralService = utilGeralService;
    }

    /**
     * CST/CSOSN sugerido pra um item novo. Regime normal: a precedência natureza/produto de
     * {@link #resolverCstIcms}. Simples Nacional: a mesma regra da procedure
     * {@code CALCULACSOSN} do legado (lida no banco da GB em 2026-10-27), com o CSOSN da
     * natureza/produto no papel do {@code CONDICAOICM}:
     * <ul>
     * <li>CFOP 5206/6206/5922/1949 → 900; 5902/6902/5925/6925/1901/2901 → 400;</li>
     * <li>5124/5125 fora do 1º item da nota → 400;</li>
     * <li>destinatário não contribuinte (sem IE) → 102;</li>
     * <li>natureza 101 (ou tributada sem CSOSN) com alíquota → 101; sem alíquota → 102;</li>
     * <li>qualquer outro CSOSN da natureza/produto vale como está.</li>
     * </ul>
     * Sem ICMS-ST (201/202), que a emissão ainda não monta. É só sugestão: o operador
     * troca o CST na tela do item.
     */
    public String resolverCstItem(NaturezaOperacao naturezaIn, Produto produtoIn, NotaSaida notaSaida,
                                  Integer cfop, boolean primeiroItem) {
        return resolverCstItem(naturezaIn, produtoIn, codEmpresa(notaSaida == null ? null : notaSaida.getCodEmpresa()),
                notaSaida == null ? null : parceiroDaNota(notaSaida), cfop, primeiroItem);
    }

    /** Mesma regra pro item do pedido de venda (que vira item da nota na conversão). */
    public String resolverCstItem(NaturezaOperacao naturezaIn, Produto produtoIn, PedidoVenda pedido,
                                  Integer cfop, boolean primeiroItem) {
        return resolverCstItem(naturezaIn, produtoIn, codEmpresa(pedido == null ? null : pedido.getCodEmpresa()),
                pedido == null ? null : pedido.getParceiro(), cfop, primeiroItem);
    }

    private String resolverCstItem(NaturezaOperacao naturezaIn, Produto produtoIn, Integer codEmpresa,
                                   Parceiro parceiro, Integer cfop, boolean primeiroItem) {
        NaturezaOperacao natureza = comTributacaoFetched(naturezaIn);
        String base = resolverCstIcmsImpl(natureza, comTributacaoFetched(produtoIn));
        if (!simplesNacional(codEmpresa)) {
            return base;
        }
        if (cfop != null && CFOP_CSOSN_900.contains(cfop)) {
            return "900";
        }
        if (cfop != null && CFOP_CSOSN_400.contains(cfop)) {
            return "400";
        }
        if (cfop != null && CFOP_INDUSTRIALIZACAO.contains(cfop) && !primeiroItem) {
            return "400";
        }
        if (!contribuinte(parceiro)) {
            return "102";
        }
        boolean comAliquota = natureza != null && natureza.getAliqIcms() != null
                && natureza.getAliqIcms().signum() != 0;
        if (base == null || "101".equals(base) || CST_ICMS_TRIBUTACAO_INTEGRAL.equals(base)) {
            return comAliquota ? "101" : "102";
        }
        if (base.length() == 2) {
            // CST de regime normal cadastrado numa empresa do Simples: isenta/não
            // tributada/suspensão viram 400, o resto 102 (o CONDICAOICM 3 do legado)
            return switch (base) {
                case "40", "41", "50" -> "400";
                default -> "102";
            };
        }
        return base;
    }

    /** Item que sai com indTot=0 por padrão: devolução simbólica do material do cliente
     * junto da industrialização por encomenda — CFOP de retorno num item de nota cuja
     * natureza tem outro CFOP (como no f_nfe.pas; uma nota só de retorno continua somando,
     * senão o vNF zeraria). Só na empresa do Simples (pedido do usuário pra GB,
     * 2026-10-27). Editável no item. */
    public boolean foraDoTotalPorPadrao(NotaSaida notaSaida, Integer cfop) {
        return notaSaida != null
                && foraDoTotalPorPadrao(notaSaida.getNatureza(), codEmpresa(notaSaida.getCodEmpresa()), cfop);
    }

    public boolean foraDoTotalPorPadrao(PedidoVenda pedido, Integer cfop) {
        return pedido != null
                && foraDoTotalPorPadrao(pedido.getNatureza(), codEmpresa(pedido.getCodEmpresa()), cfop);
    }

    private boolean foraDoTotalPorPadrao(NaturezaOperacao natureza, Integer codEmpresa, Integer cfop) {
        if (cfop == null || !CFOP_FORA_DO_TOTAL.contains(cfop)) {
            return false;
        }
        if (natureza != null && cfop.equals(natureza.getCfop())) {
            return false;
        }
        return simplesNacional(codEmpresa);
    }

    /** 1º item da nota, pra regra do 5124/5125 — conta os itens já gravados, fora o próprio. */
    public boolean primeiroItem(NotaSaida notaSaida, ItemNotaSaida item) {
        if (notaSaida == null || entityStates.isNew(notaSaida)) {
            return true;
        }
        Long outros = dataManager.loadValue(
                        "select count(i) from ItemNotaSaida i where i.notaSaida.id = :nota and i.id <> :item",
                        Long.class)
                .parameter("nota", notaSaida.getId())
                .parameter("item", item.getId())
                .one();
        return outros == 0;
    }

    // nota/pedido novo ainda sem codEmpresa: o listener grava o da sessão no save
    private Integer codEmpresa(Integer codEmpresaDoDocumento) {
        return codEmpresaDoDocumento != null ? codEmpresaDoDocumento : utilGeralService.getCodEmpresa();
    }

    private boolean simplesNacional(Integer codEmpresa) {
        if (codEmpresa == null) {
            return false;
        }
        CodRegimeTributario crt = dataManager.load(Empresa.class)
                .query("select e from Empresa e where e.codigo = :codigo")
                .parameter("codigo", codEmpresa)
                .optional()
                .map(Empresa::getCrt)
                .orElse(null);
        return crt == CodRegimeTributario.SIMPLES_NACIONAL
                || crt == CodRegimeTributario.SIMPLES_NACIONAL_EXCESSO_SUBLIMITE;
    }

    private Parceiro parceiroDaNota(NotaSaida notaSaida) {
        if (entityStates.isLoaded(notaSaida, "parceiro")) {
            return notaSaida.getParceiro();
        }
        return dataManager.load(Parceiro.class)
                .query("select n.parceiro from NotaSaida n where n.id = :id")
                .parameter("id", notaSaida.getId())
                .optional()
                .orElse(null);
    }

    // Mesmo critério do indIEDest em NfeXmlBuilder: sem IE = não contribuinte. Documento
    // sem destinatário ainda conta como contribuinte (não força o 102 antes da hora).
    private boolean contribuinte(Parceiro parceiro) {
        if (parceiro == null) {
            return true;
        }
        if (!entityStates.isLoaded(parceiro, "inscricao")) {
            parceiro = dataManager.load(Parceiro.class).id(parceiro.getId()).one();
        }
        String inscricao = parceiro.getInscricao();
        return inscricao != null && !inscricao.isBlank() && !"ISENTO".equalsIgnoreCase(inscricao.trim());
    }

    /** CST resolvido (Natureza/Produto, ver {@link #resolverCstIcms}) + alíquota — a
     * alíquota vem SEMPRE de {@code NaturezaOperacao.aliqIcms}, decidido com o usuário
     * 2026-09-14 (correção de uma versão anterior, que fazia a alíquota seguir o mesmo
     * lado que decidiu o CST — errado: quem manda na alíquota é sempre a natureza,
     * independente de qual dos dois decidiu o CST). */
    public record TributacaoIcms(String cst, BigDecimal aliqIcms) {
    }

    public TributacaoIcms resolverTributacaoIcms(NaturezaOperacao naturezaIn, Produto produtoIn) {
        NaturezaOperacao natureza = comTributacaoFetched(naturezaIn);
        Produto produto = comTributacaoFetched(produtoIn);
        String cst = resolverCstIcmsImpl(natureza, produto);
        if (cst == null) {
            return null;
        }
        BigDecimal aliqIcms = natureza == null ? BigDecimal.ZERO : natureza.getAliqIcms();
        return new TributacaoIcms(cst, aliqIcms);
    }

    /**
     * Precedência do CST de ICMS — mesma regra de {@link #resolverCodClassTrib}, catálogo
     * diferente (ver Javadoc de {@link Cst}): a natureza decide quando fixa um CST próprio
     * (qualquer um diferente de "00"); quando a natureza é "rasa" (Cst "00" ou sem Cst),
     * quem decide é o Cst do produto. Sem nenhum dos dois, retorna {@code null} — o
     * default final ("40"/"102" conforme o regime) é aplicado só na emissão, em
     * {@code NfeXmlBuilder.resolverCstIcms}, pra não gravar um valor no item que a UI não
     * mostrou ao usuário.
     */
    public String resolverCstIcms(NaturezaOperacao naturezaIn, Produto produtoIn) {
        return resolverCstIcmsImpl(comTributacaoFetched(naturezaIn), comTributacaoFetched(produtoIn));
    }

    private String resolverCstIcmsImpl(NaturezaOperacao natureza, Produto produto) {
        Cst cstNatureza = natureza == null ? null : natureza.getCst();
        boolean rasa = cstNatureza == null || CST_ICMS_TRIBUTACAO_INTEGRAL.equals(cstNatureza.getCodigo());
        if (!rasa) {
            return cstNatureza.getCodigo();
        }
        Cst cstProduto = produto == null ? null : produto.getCst();
        return cstProduto == null ? null : cstProduto.getCodigo();
    }

    /**
     * Regra de precedência do cClassTrib decidida em docs/REFORMA-TRIBUTARIA-IBS-CBS.md
     * (fora do escopo aqui: devolução, que deve espelhar a operação original — ainda não
     * modelada). {@code NaturezaOperacao.classTrib}/{@code Produto.classTrib} são
     * referências reais pra {@link ClassTrib} (cadastro); o valor gravado aqui em
     * {@code ItemNotaSaida.codClassTrib} é só o código numérico resolvido — snapshot
     * congelado, não uma referência viva (mesmo motivo de {@code NfeItem.codClassTrib}
     * ser String, não FK).
     */
    public Integer resolverCodClassTrib(NaturezaOperacao naturezaIn, Produto produtoIn) {
        return resolverCodClassTrib(null, naturezaIn, produtoIn);
    }

    /**
     * Mesma precedência, com o cClassTrib do cabeçalho da nota/pedido na frente
     * (pedido do usuário 2026-09-24): quando o cabeçalho traz uma classificação que não é
     * a de tributação integral (ex.: 410999 numa doação), ela vale pra todos os itens,
     * acima da natureza e do produto. Cabeçalho vazio ou "000xxx" cai na regra de sempre.
     */
    public Integer resolverCodClassTrib(ClassTrib classTribCabecalhoIn, NaturezaOperacao naturezaIn,
                                        Produto produtoIn) {
        ClassTrib classTribCabecalho = comCstCarregado(classTribCabecalhoIn);
        if (classTribCabecalho != null && classTribCabecalho.getCst() != null
                && classTribCabecalho.getCst() != CST_TRIBUTACAO_INTEGRAL) {
            return classTribCabecalho.getCodigo();
        }
        NaturezaOperacao natureza = comTributacaoFetched(naturezaIn);
        Produto produto = comTributacaoFetched(produtoIn);
        ClassTrib classTribNatureza = natureza == null ? null : natureza.getClassTrib();
        // Natureza sem ClassTrib ainda (cadastro pendente de migração) é tratada como
        // "rasa", mesma consequência prática de CST 000: nada de especial foi fixado na
        // natureza, então o produto decide.
        int cst = classTribNatureza == null ? CST_TRIBUTACAO_INTEGRAL : classTribNatureza.getCst();
        if (cst == CST_TRIBUTACAO_INTEGRAL) {
            ClassTrib classTribProduto = produto == null ? null : produto.getClassTrib();
            return classTribProduto == null ? null : classTribProduto.getCodigo();
        }
        return classTribNatureza.getCodigo();
    }

    /**
     * Base/alíquota/valor do ICMS do item, calculados a partir da precedência acima e do
     * subtotal do item (quantidade×valorUnitario) — grava direto no {@code item} passado
     * (chamador decide se persiste ou só usa como preview de tela). Sem redução de base
     * nem ICMS-ST nesta versão (fora do escopo "nota simples" de 2026-09-14).
     */
    public void aplicarCalculoIcms(ItemNotaSaida item, NaturezaOperacao natureza, Produto produto) {
        // Alíquota sempre da natureza, mesmo com o CST digitado no item (antes vinha zero
        // quando nem natureza nem produto tinham CST — o CST agora é editável na tela).
        NaturezaOperacao naturezaFetched = comTributacaoFetched(natureza);
        BigDecimal aliqIcms = naturezaFetched == null || naturezaFetched.getAliqIcms() == null
                ? BigDecimal.ZERO : naturezaFetched.getAliqIcms();
        // Isenta/não tributada/suspensão/ST retido: sem ICMS próprio, mesmo que a
        // natureza tenha alíquota (bug real 2026-09-24: doação com CST 41 gravava ICMS de
        // 7% da natureza). O CST vale o do item, que o usuário pode ter trocado à mão.
        // 51 (diferimento) continua calculando — o XML usa o valor em vICMSOp.
        if (semIcmsProprio(item.getCst())) {
            aliqIcms = BigDecimal.ZERO;
        }
        BigDecimal baseIcms = item.getSubTotal() == null ? BigDecimal.ZERO : item.getSubTotal();
        BigDecimal valorIcms = baseIcms.multiply(aliqIcms)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        item.setAliqIcms(aliqIcms);
        item.setBaseIcms(baseIcms);
        item.setValorIcms(valorIcms);
    }

    // Sem CST o item sai como "40" na emissão (NfeXmlBuilder.resolverCstIcms) — mesmo
    // tratamento aqui, pra o valor gravado bater com o XML.
    /**
     * cClassTrib cadastrado na natureza — usado pra pré-preencher o cClassTrib do
     * cabeçalho da nota/pedido quando o operador escolhe a natureza (a natureza que vem
     * do combo da tela não traz a referência carregada).
     */
    public ClassTrib classTribDaNatureza(NaturezaOperacao naturezaIn) {
        NaturezaOperacao natureza = comTributacaoFetched(naturezaIn);
        return natureza == null ? null : natureza.getClassTrib();
    }

    private ClassTrib comCstCarregado(ClassTrib classTrib) {
        if (classTrib == null || entityStates.isLoaded(classTrib, "cst")) {
            return classTrib;
        }
        return dataManager.load(ClassTrib.class).id(classTrib.getId()).optional().orElse(classTrib);
    }

    // CSOSN (Simples, 3 dígitos): só o 101 leva valor — aí aliqIcms/valorIcms do item são o
    // pCredSN/vCredICMSSN (crédito do art. 23 da LC 123), com a alíquota vinda da natureza,
    // como no legado da GB (2026-10-27). Os demais CSOSN não têm ICMS próprio no XML.
    private boolean semIcmsProprio(String cst) {
        if (cst == null || cst.isBlank()) {
            return true;
        }
        if (cst.trim().length() == 3) {
            return !"101".equals(cst.trim());
        }
        return switch (cst.trim()) {
            case "40", "41", "50", "60" -> true;
            default -> false;
        };
    }

    /**
     * Garante {@code cst}/{@code classTrib}/{@code aliqIcms} carregados antes de ler — as
     * referências que chegam aqui costumam vir de um {@code entityPicker} de tela ou de um
     * {@code notaSaidaDl} com fetch plan restrito (ex.: {@code fetchPlan="_instance_name"}
     * em {@code nota-saida-detail-view.xml}, que NÃO inclui esses atributos). Bug real
     * achado 2026-09-15: {@code ItemNotaSaidaDetailView}'s preview ao vivo lia {@code
     * natureza.getAliqIcms()} direto, sem essa garantia — funcionava na inclusão (a
     * natureza vinha recém-escolhida pelo entityPicker, com fetch plan mais rico) e
     * quebrava na alteração de uma nota já salva (natureza recarregada só com
     * {@code _instance_name} por {@code notaSaidaDl}), com {@code IllegalStateException:
     * Cannot get unfetched attribute [aliqIcms] from detached object}. Centralizado aqui
     * (em vez de duplicado em cada chamador) pra qualquer uso futuro de {@code
     * NaturezaOperacao}/{@code Produto} por este serviço ficar protegido automaticamente —
     * {@code ItemNotaSaidaEventListener} já fazia essa mesma garantia por conta própria
     * antes de chamar este serviço; manter os dois não causa reload duplicado (o segundo
     * {@code isLoaded} já bate certo).
     */
    private NaturezaOperacao comTributacaoFetched(NaturezaOperacao natureza) {
        if (natureza == null) {
            return null;
        }
        if (entityStates.isLoaded(natureza, "cst")
                && entityStates.isLoaded(natureza, "classTrib")
                && entityStates.isLoaded(natureza, "aliqIcms")) {
            return natureza;
        }
        return dataManager.load(NaturezaOperacao.class)
                .id(natureza.getId())
                .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE)
                        .add("cst", FetchPlan.BASE)
                        .add("classTrib", FetchPlan.BASE))
                .optional()
                .orElse(natureza);
    }

    private Produto comTributacaoFetched(Produto produto) {
        if (produto == null) {
            return null;
        }
        if (entityStates.isLoaded(produto, "cst") && entityStates.isLoaded(produto, "classTrib")) {
            return produto;
        }
        return dataManager.load(Produto.class)
                .id(produto.getId())
                .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE)
                        .add("cst", FetchPlan.BASE)
                        .add("classTrib", FetchPlan.BASE))
                .optional()
                .orElse(produto);
    }
}
