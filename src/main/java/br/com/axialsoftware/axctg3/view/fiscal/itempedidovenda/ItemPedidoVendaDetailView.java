package br.com.axialsoftware.axctg3.view.fiscal.itempedidovenda;

import br.com.axialsoftware.axctg3.entity.fiscal.ItemPedidoVenda;
import br.com.axialsoftware.axctg3.entity.fiscal.Produto;
import br.com.axialsoftware.axctg3.service.UtilGeralService;
import br.com.axialsoftware.axctg3.view.main.MainView;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.model.CollectionLoader;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;

@Route(value = "item-pedido-vendas/:id", layout = MainView.class)
@ViewController(id = "ItemPedidoVenda.detail")
@ViewDescriptor(path = "item-pedido-venda-detail-view.xml")
@EditedEntityContainer("itemPedidoVendaDc")
public class ItemPedidoVendaDetailView extends StandardDetailView<ItemPedidoVenda> {

    @Autowired
    private UtilGeralService utilGeralService;
    @ViewComponent
    private CollectionLoader<Produto> produtosDl;

    @Subscribe
    public void onBeforeShow(final BeforeShowEvent event) {
        produtosDl.setParameter("codEmpresa", utilGeralService.getCodEmpresa());
        produtosDl.load();
    }
}
