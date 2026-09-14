package br.com.axialsoftware.axctg3.listener.fiscal;

import br.com.axialsoftware.axctg3.entity.fiscal.ItemNotaSaida;
import br.com.axialsoftware.axctg3.entity.fiscal.NaturezaOperacao;
import br.com.axialsoftware.axctg3.entity.fiscal.NotaSaida;
import br.com.axialsoftware.axctg3.entity.fiscal.Produto;
import br.com.axialsoftware.axctg3.entity.tabelas.ClassTrib;
import br.com.axialsoftware.axctg3.entity.tabelas.Cst;
import br.com.axialsoftware.axctg3.service.UtilGeralService;
import io.jmix.core.DataManager;
import io.jmix.core.EntityStates;
import io.jmix.core.FetchPlan;
import io.jmix.core.event.EntitySavingEvent;
import io.jmix.data.Sequence;
import io.jmix.data.Sequences;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class ItemNotaSaidaEventListener {

    // CST-IBS/CBS "tributação integral", os 3 primeiros dígitos de um cClassTrib de 6
    // dígitos (ex.: 000000..000499) — ver docs/REFORMA-TRIBUTARIA-IBS-CBS.md. Natureza
    // com esse CST é "rasa": não fixa um tratamento tributário próprio, então quem decide
    // é o produto do item.
    private static final int CST_TRIBUTACAO_INTEGRAL = 0;

    // CST de ICMS "tributação integral" (Cst.codigo, não o CST-IBS/CBS acima — catálogos
    // diferentes, mesmo nome). Mesma ideia de "rasa": natureza com esse CST (ou sem Cst
    // nenhum) não fixa tratamento próprio, quem decide é o Cst do produto.
    private static final String CST_ICMS_TRIBUTACAO_INTEGRAL = "00";

    private final Sequences sequences;
    private final UtilGeralService utilGeralService;
    private final DataManager dataManager;
    private final EntityStates entityStates;

    public ItemNotaSaidaEventListener(Sequences sequences, UtilGeralService utilGeralService,
                                       DataManager dataManager, EntityStates entityStates) {
        this.sequences = sequences;
        this.utilGeralService = utilGeralService;
        this.dataManager = dataManager;
        this.entityStates = entityStates;
    }

    @EventListener
    public void onItemNotaSaidaSaving(final EntitySavingEvent<ItemNotaSaida> event) {
        if (event.isNewEntity()) {
            ItemNotaSaida itemNotaSaida = event.getEntity();
            if (itemNotaSaida.getItem() == null) {
                Integer codEmpresa = utilGeralService.getCodEmpresa();
                long item = sequences.createNextValue(Sequence.withName("item_nota_saida_seq_" + codEmpresa));
                itemNotaSaida.setItem(Math.toIntExact(item));
            }
            NotaSaida notaSaida = itemNotaSaida.getNotaSaida();
            NaturezaOperacao naturezaRef = notaSaida == null ? null : notaSaida.getNatureza();
            if (itemNotaSaida.getCfop() == null && naturezaRef != null) {
                itemNotaSaida.setCfop(naturezaRef.getCfop());
            }
            // Recarrega natureza/produto com classTrib+cst garantidamente fetched: as
            // referências que chegam aqui (via notaSaida.getNatureza()/item.getProduto())
            // normalmente vêm de um entityPicker de tela, carregado só com
            // "_instance_name" — ler um ManyToOne LAZY não fetched dentro de
            // EntitySavingEvent não faz lazy-load, estoura
            // ValidationException.instantiatingValueholderWithNullSession porque a
            // referência está desanexada da sessão nesse ponto do save (confirmado ao vivo
            // 2026-09-14, com Produto.cst preenchido). Só recarrega quando falta mesmo —
            // a maioria dos saves já vem com o suficiente.
            NaturezaOperacao natureza = carregarNaturezaComTributacao(naturezaRef);
            Produto produto = carregarProdutoComTributacao(itemNotaSaida.getProduto());
            if (itemNotaSaida.getCodClassTrib() == null) {
                itemNotaSaida.setCodClassTrib(resolverCodClassTrib(natureza, produto));
            }
            if (itemNotaSaida.getCst() == null) {
                String cst = resolverCstIcms(natureza, produto);
                if (cst != null) {
                    itemNotaSaida.setCst(cst);
                }
            }
        }
    }

    private NaturezaOperacao carregarNaturezaComTributacao(NaturezaOperacao natureza) {
        if (natureza == null) {
            return null;
        }
        if (entityStates.isLoaded(natureza, "classTrib") && entityStates.isLoaded(natureza, "cst")) {
            return natureza;
        }
        return dataManager.load(NaturezaOperacao.class)
                .id(natureza.getId())
                .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE)
                        .add("classTrib", FetchPlan.BASE)
                        .add("cst", FetchPlan.BASE))
                .optional()
                .orElse(natureza);
    }

    private Produto carregarProdutoComTributacao(Produto produto) {
        if (produto == null) {
            return null;
        }
        if (entityStates.isLoaded(produto, "classTrib") && entityStates.isLoaded(produto, "cst")) {
            return produto;
        }
        return dataManager.load(Produto.class)
                .id(produto.getId())
                .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE)
                        .add("classTrib", FetchPlan.BASE)
                        .add("cst", FetchPlan.BASE))
                .optional()
                .orElse(produto);
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
    private String resolverCstIcms(NaturezaOperacao natureza, Produto produto) {
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
    private Integer resolverCodClassTrib(NaturezaOperacao natureza, Produto produto) {
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
}
