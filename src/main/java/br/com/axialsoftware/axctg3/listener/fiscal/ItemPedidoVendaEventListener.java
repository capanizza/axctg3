package br.com.axialsoftware.axctg3.listener.fiscal;

import br.com.axialsoftware.axctg3.entity.fiscal.ItemPedidoVenda;
import br.com.axialsoftware.axctg3.entity.fiscal.NaturezaOperacao;
import br.com.axialsoftware.axctg3.entity.fiscal.PedidoVenda;
import br.com.axialsoftware.axctg3.service.UtilGeralService;
import br.com.axialsoftware.axctg3.service.fiscal.ItemNotaSaidaTributacaoService;
import io.jmix.core.DataManager;
import io.jmix.core.EntityStates;
import io.jmix.core.FetchPlan;
import io.jmix.core.event.EntitySavingEvent;
import io.jmix.data.Sequence;
import io.jmix.data.Sequences;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Numeração do item e CST/cClassTrib na criação, mesmo padrão de
 * {@code ItemNotaSaidaEventListener} (mesma precedência cabeçalho → natureza → produto)
 * — sem o cálculo de ICMS que existe lá, ainda não modelado pro pedido de venda.
 */
@Component
public class ItemPedidoVendaEventListener {

    private final Sequences sequences;
    private final UtilGeralService utilGeralService;
    private final ItemNotaSaidaTributacaoService tributacaoService;
    private final EntityStates entityStates;
    private final DataManager dataManager;

    public ItemPedidoVendaEventListener(Sequences sequences, UtilGeralService utilGeralService,
                                        ItemNotaSaidaTributacaoService tributacaoService,
                                        EntityStates entityStates, DataManager dataManager) {
        this.sequences = sequences;
        this.utilGeralService = utilGeralService;
        this.tributacaoService = tributacaoService;
        this.entityStates = entityStates;
        this.dataManager = dataManager;
    }

    @EventListener
    public void onItemPedidoVendaSaving(final EntitySavingEvent<ItemPedidoVenda> event) {
        ItemPedidoVenda item = event.getEntity();
        if (event.isNewEntity() && item.getItem() == null) {
            Integer codEmpresa = utilGeralService.getCodEmpresa();
            long numero = sequences.createNextValue(Sequence.withName("item_pedido_venda_seq_" + codEmpresa));
            item.setItem(Math.toIntExact(numero));
        }
        if (event.isNewEntity()
                && (item.getCfop() == null || item.getCst() == null || item.getCodClassTrib() == null)) {
            PedidoVenda pedido = pedidoComTributacao(item.getPedidoVenda());
            NaturezaOperacao natureza = pedido == null ? null : pedido.getNatureza();
            if (item.getCfop() == null && natureza != null) {
                item.setCfop(natureza.getCfop());
            }
            if (item.getCst() == null) {
                item.setCst(tributacaoService.resolverCstIcms(natureza, item.getProduto()));
            }
            if (item.getCodClassTrib() == null) {
                item.setCodClassTrib(tributacaoService.resolverCodClassTrib(
                        pedido == null ? null : pedido.getClassTrib(), natureza, item.getProduto()));
            }
        }
    }

    // Pedido vindo com fetch plan sem natureza/classTrib não faz lazy-load dentro do
    // save (mesmo problema de ItemNotaSaidaEventListener.notaSaidaDoItem).
    private PedidoVenda pedidoComTributacao(PedidoVenda pedido) {
        if (pedido == null
                || (entityStates.isLoaded(pedido, "natureza") && entityStates.isLoaded(pedido, "classTrib"))) {
            return pedido;
        }
        return dataManager.load(PedidoVenda.class)
                .id(pedido.getId())
                .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE)
                        .add("natureza", FetchPlan.BASE)
                        .add("classTrib", FetchPlan.BASE))
                .optional()
                .orElse(pedido);
    }
}
