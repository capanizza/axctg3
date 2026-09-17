package br.com.axialsoftware.axctg3.listener.fiscal;

import br.com.axialsoftware.axctg3.entity.fiscal.ItemPedidoVenda;
import br.com.axialsoftware.axctg3.entity.fiscal.PedidoVenda;
import br.com.axialsoftware.axctg3.service.UtilGeralService;
import io.jmix.core.DataManager;
import io.jmix.core.Id;
import io.jmix.core.event.EntityChangedEvent;
import io.jmix.core.event.EntitySavingEvent;
import io.jmix.data.Sequence;
import io.jmix.data.Sequences;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Numeração via {@code Sequence} simples, mesmo padrão de
 * {@code NotaSaidaEventListener}. Depois que o pedido é criado, renumera os itens em
 * sequência (1, 2, 3...); cada {@link ItemPedidoVenda} chega com um número provisório
 * da própria sequência de itens ({@code ItemPedidoVendaEventListener}).
 */
@Component
public class PedidoVendaEventListener {

    private final UtilGeralService utilGeralService;
    private final DataManager dataManager;
    private final Sequences sequences;

    public PedidoVendaEventListener(UtilGeralService utilGeralService, DataManager dataManager, Sequences sequences) {
        this.utilGeralService = utilGeralService;
        this.dataManager = dataManager;
        this.sequences = sequences;
    }

    @EventListener
    public void onPedidoVendaSaving(final EntitySavingEvent<PedidoVenda> event) {
        PedidoVenda pedidoVenda = event.getEntity();
        if (event.isNewEntity()) {
            if (pedidoVenda.getCodEmpresa() == null) {
                pedidoVenda.setCodEmpresa(utilGeralService.getCodEmpresa());
            }
            if (pedidoVenda.getNumero() == null) {
                long numero = sequences.createNextValue(Sequence.withName("pedido_venda_seq_" + pedidoVenda.getCodEmpresa()));
                pedidoVenda.setNumero(Math.toIntExact(numero));
            }
        }
    }

    @EventListener
    public void onPedidoVendaChangedBeforeCommit(final EntityChangedEvent<PedidoVenda> event) {
        if (event.getType() == EntityChangedEvent.Type.CREATED) {
            Id<PedidoVenda> id = event.getEntityId();
            PedidoVenda pedidoVenda = dataManager.load(id).one();
            List<ItemPedidoVenda> itens = pedidoVenda.getItens();
            int numItem = 1;
            for (ItemPedidoVenda item : itens) {
                item.setItem(numItem++);
                dataManager.save(item);
            }
        }
    }
}
