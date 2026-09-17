package br.com.axialsoftware.axctg3.listener.fiscal;

import br.com.axialsoftware.axctg3.entity.fiscal.ItemPedidoVenda;
import br.com.axialsoftware.axctg3.service.UtilGeralService;
import io.jmix.core.event.EntitySavingEvent;
import io.jmix.data.Sequence;
import io.jmix.data.Sequences;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Numeração do item, mesmo padrão de {@code ItemNotaSaidaEventListener} — sem a
 * resolução de tributação/cálculo de ICMS que existe lá, ainda não modelados pro
 * pedido de venda.
 */
@Component
public class ItemPedidoVendaEventListener {

    private final Sequences sequences;
    private final UtilGeralService utilGeralService;

    public ItemPedidoVendaEventListener(Sequences sequences, UtilGeralService utilGeralService) {
        this.sequences = sequences;
        this.utilGeralService = utilGeralService;
    }

    @EventListener
    public void onItemPedidoVendaSaving(final EntitySavingEvent<ItemPedidoVenda> event) {
        ItemPedidoVenda item = event.getEntity();
        if (event.isNewEntity() && item.getItem() == null) {
            Integer codEmpresa = utilGeralService.getCodEmpresa();
            long numero = sequences.createNextValue(Sequence.withName("item_pedido_venda_seq_" + codEmpresa));
            item.setItem(Math.toIntExact(numero));
        }
    }
}
