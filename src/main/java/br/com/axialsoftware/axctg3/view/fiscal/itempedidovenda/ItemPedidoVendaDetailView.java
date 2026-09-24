package br.com.axialsoftware.axctg3.view.fiscal.itempedidovenda;

import br.com.axialsoftware.axctg3.entity.fiscal.ItemPedidoVenda;
import br.com.axialsoftware.axctg3.entity.fiscal.NaturezaOperacao;
import br.com.axialsoftware.axctg3.entity.fiscal.PedidoVenda;
import br.com.axialsoftware.axctg3.entity.fiscal.Produto;
import br.com.axialsoftware.axctg3.service.UtilGeralService;
import br.com.axialsoftware.axctg3.service.fiscal.ItemNotaSaidaTributacaoService;
import br.com.axialsoftware.axctg3.view.main.MainView;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.model.CollectionLoader;
import io.jmix.flowui.model.InstanceContainer;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;

@Route(value = "item-pedido-vendas/:id", layout = MainView.class)
@ViewController(id = "ItemPedidoVenda.detail")
@ViewDescriptor(path = "item-pedido-venda-detail-view.xml")
@EditedEntityContainer("itemPedidoVendaDc")
public class ItemPedidoVendaDetailView extends StandardDetailView<ItemPedidoVenda> {

    @Autowired
    private UtilGeralService utilGeralService;
    @Autowired
    private ItemNotaSaidaTributacaoService tributacaoService;
    @ViewComponent
    private CollectionLoader<Produto> produtosDl;

    @Subscribe
    public void onBeforeShow(final BeforeShowEvent event) {
        produtosDl.setParameter("codEmpresa", utilGeralService.getCodEmpresa());
        produtosDl.load();
    }

    /**
     * CST/cClassTrib pré-preenchidos, mesma precedência do item da nota (cabeçalho →
     * natureza → produto, ver ItemNotaSaidaTributacaoService) — na abertura com o que o
     * cabeçalho/natureza decidem sozinhos, e de novo quando o produto é escolhido. Só
     * preenche campo em branco; o pedido continua sem cálculo de ICMS.
     */
    @Subscribe
    public void onInitEntity(final InitEntityEvent<ItemPedidoVenda> event) {
        resolverTributacao(event.getEntity());
    }

    @Subscribe(id = "itemPedidoVendaDc", target = Target.DATA_CONTAINER)
    public void onItemPedidoVendaDcItemPropertyChange(
            final InstanceContainer.ItemPropertyChangeEvent<ItemPedidoVenda> event) {
        if ("produto".equals(event.getProperty())) {
            resolverTributacao(event.getItem());
        }
    }

    private void resolverTributacao(ItemPedidoVenda item) {
        PedidoVenda pedido = item.getPedidoVenda();
        NaturezaOperacao natureza = pedido == null ? null : pedido.getNatureza();
        Produto produto = item.getProduto();
        if (item.getCst() == null) {
            String cst = tributacaoService.resolverCstIcms(natureza, produto);
            if (cst != null) {
                item.setCst(cst);
            }
        }
        if (item.getCodClassTrib() == null) {
            Integer codClassTrib = tributacaoService.resolverCodClassTrib(
                    pedido == null ? null : pedido.getClassTrib(), natureza, produto);
            if (codClassTrib != null) {
                item.setCodClassTrib(codClassTrib);
            }
        }
    }
}
