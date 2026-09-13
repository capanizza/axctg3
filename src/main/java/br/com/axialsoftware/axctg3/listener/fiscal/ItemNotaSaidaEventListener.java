package br.com.axialsoftware.axctg3.listener.fiscal;

import br.com.axialsoftware.axctg3.entity.fiscal.ItemNotaSaida;
import br.com.axialsoftware.axctg3.entity.fiscal.NaturezaOperacao;
import br.com.axialsoftware.axctg3.entity.fiscal.NotaSaida;
import br.com.axialsoftware.axctg3.service.UtilGeralService;
import io.jmix.core.event.EntitySavingEvent;
import io.jmix.data.Sequence;
import io.jmix.data.Sequences;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * A resolução de {@code cClassTrib} (regra de precedência
 * {@code NaturezaOperacao.classTrib}/{@code Produto.classTrib}, ver
 * [[reforma-tributaria-cclasstrib-precedencia]]/docs/REFORMA-TRIBUTARIA-IBS-CBS.md)
 * morava aqui antes de 2026-09-13 — {@code ItemNotaSaida} não guarda mais
 * {@code codClassTrib} (decisão de tornar o item minimalista, ver Javadoc de {@link
 * ItemNotaSaida}), então essa regra foi pra {@code NfeXmlBuilder}, calculada na hora da
 * emissão a partir dos mesmos dois cadastros.
 */
@Component
public class ItemNotaSaidaEventListener {

    private final Sequences sequences;
    private final UtilGeralService utilGeralService;

    public ItemNotaSaidaEventListener(Sequences sequences, UtilGeralService utilGeralService) {
        this.sequences = sequences;
        this.utilGeralService = utilGeralService;
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
            NaturezaOperacao natureza = notaSaida == null ? null : notaSaida.getNatureza();
            if (itemNotaSaida.getCfop() == null && natureza != null) {
                itemNotaSaida.setCfop(natureza.getCfop());
            }
        }
    }
}
