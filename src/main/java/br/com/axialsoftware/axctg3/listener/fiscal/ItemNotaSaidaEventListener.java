package br.com.axialsoftware.axctg3.listener.fiscal;

import br.com.axialsoftware.axctg3.entity.fiscal.ItemNotaSaida;
import br.com.axialsoftware.axctg3.entity.fiscal.NaturezaOperacao;
import br.com.axialsoftware.axctg3.entity.fiscal.NotaSaida;
import br.com.axialsoftware.axctg3.entity.fiscal.Produto;
import br.com.axialsoftware.axctg3.entity.tabelas.ClassTrib;
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

    private final Sequences sequences;

    public ItemNotaSaidaEventListener(Sequences sequences) {
        this.sequences = sequences;
    }

    @EventListener
    public void onItemNotaSaidaSaving(final EntitySavingEvent<ItemNotaSaida> event) {
        if (event.isNewEntity()) {
            ItemNotaSaida itemNotaSaida = event.getEntity();
            if (itemNotaSaida.getItem() == null) {
                long item = sequences.createNextValue(Sequence.withName("item_nota_saida_seq"));
                itemNotaSaida.setItem(Math.toIntExact(item));
            }
            NotaSaida notaSaida = itemNotaSaida.getNotaSaida();
            NaturezaOperacao natureza = notaSaida == null ? null : notaSaida.getNatureza();
            if (itemNotaSaida.getCfop() == null && natureza != null) {
                itemNotaSaida.setCfop(natureza.getCfop());
            }
            if (itemNotaSaida.getCodClassTrib() == null) {
                itemNotaSaida.setCodClassTrib(resolverCodClassTrib(natureza, itemNotaSaida));
            }
        }
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
    private Integer resolverCodClassTrib(NaturezaOperacao natureza, ItemNotaSaida itemNotaSaida) {
        ClassTrib classTribNatureza = natureza == null ? null : natureza.getClassTrib();
        // Natureza sem ClassTrib ainda (cadastro pendente de migração) é tratada como
        // "rasa", mesma consequência prática de CST 000: nada de especial foi fixado na
        // natureza, então o produto decide.
        int cst = classTribNatureza == null ? CST_TRIBUTACAO_INTEGRAL : classTribNatureza.getCst();
        if (cst == CST_TRIBUTACAO_INTEGRAL) {
            Produto produto = itemNotaSaida.getProduto();
            ClassTrib classTribProduto = produto == null ? null : produto.getClassTrib();
            return classTribProduto == null ? null : classTribProduto.getCodigo();
        }
        return classTribNatureza.getCodigo();
    }
}
