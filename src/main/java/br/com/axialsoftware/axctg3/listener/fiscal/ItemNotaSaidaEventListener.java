package br.com.axialsoftware.axctg3.listener.fiscal;

import br.com.axialsoftware.axctg3.entity.fiscal.ItemNotaSaida;
import br.com.axialsoftware.axctg3.entity.fiscal.NotaSaida;
import io.jmix.core.event.EntitySavingEvent;
import io.jmix.data.Sequence;
import io.jmix.data.Sequences;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class ItemNotaSaidaEventListener {

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
            if (itemNotaSaida.getCfop() == null) {
                NotaSaida notaSaida = itemNotaSaida.getNotaSaida();
                if (notaSaida != null && notaSaida.getNatureza() != null) {
                    itemNotaSaida.setCfop(notaSaida.getNatureza().getCfop());
                }
            }
        }
    }
}
