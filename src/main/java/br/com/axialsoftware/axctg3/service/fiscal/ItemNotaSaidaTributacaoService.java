package br.com.axialsoftware.axctg3.service.fiscal;

import br.com.axialsoftware.axctg3.entity.fiscal.ItemNotaSaida;
import br.com.axialsoftware.axctg3.entity.fiscal.NaturezaOperacao;
import br.com.axialsoftware.axctg3.entity.fiscal.Produto;
import br.com.axialsoftware.axctg3.entity.tabelas.ClassTrib;
import br.com.axialsoftware.axctg3.entity.tabelas.Cst;
import io.jmix.core.DataManager;
import io.jmix.core.EntityStates;
import io.jmix.core.FetchPlan;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

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

    private final DataManager dataManager;
    private final EntityStates entityStates;

    public ItemNotaSaidaTributacaoService(DataManager dataManager, EntityStates entityStates) {
        this.dataManager = dataManager;
        this.entityStates = entityStates;
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
        TributacaoIcms tributacao = resolverTributacaoIcms(natureza, produto);
        BigDecimal aliqIcms = tributacao == null ? BigDecimal.ZERO : tributacao.aliqIcms();
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
    private boolean semIcmsProprio(String cst) {
        if (cst == null || cst.isBlank()) {
            return true;
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
