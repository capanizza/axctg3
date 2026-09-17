package br.com.axialsoftware.axctg3.view.fiscal.pedidovenda;

import br.com.axialsoftware.axctg3.entity.fiscal.PedidoVenda;
import br.com.axialsoftware.axctg3.service.UtilGeralService;
import br.com.axialsoftware.axctg3.view.main.MainView;

import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.data.renderer.Renderer;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.UiComponents;
import io.jmix.flowui.component.checkbox.JmixCheckbox;
import io.jmix.flowui.model.CollectionLoader;
import io.jmix.flowui.view.*;

import org.springframework.beans.factory.annotation.Autowired;

@Route(value = "pedido-vendas", layout = MainView.class)
@ViewController(id = "PedidoVenda.list")
@ViewDescriptor(path = "pedido-venda-list-view.xml")
@LookupComponent("pedidoVendasDataGrid")
@DialogMode(width = "64em")
public class PedidoVendaListView extends StandardListView<PedidoVenda> {

    @ViewComponent
    private CollectionLoader<PedidoVenda> pedidoVendasDl;
    @Autowired
    private UtilGeralService utilGeralService;
    @Autowired
    private UiComponents uiComponents;

    @Subscribe
    public void onBeforeShow(final BeforeShowEvent event) {
        pedidoVendasDl.setParameter("codEmpresa", utilGeralService.getCodEmpresa());
        pedidoVendasDl.load();
    }

    @Supply(to = "pedidoVendasDataGrid.cancelado", subject = "renderer")
    private Renderer<PedidoVenda> pedidoVendasDataGridCanceladoRenderer() {
        return new ComponentRenderer<>(pedidoVenda -> {
            JmixCheckbox checkbox = uiComponents.create(JmixCheckbox.class);
            checkbox.setValue(Boolean.TRUE.equals(pedidoVenda.getCancelado()));
            checkbox.setReadOnly(true);
            checkbox.addClassName("grid-value-checkbox");
            return checkbox;
        });
    }
}
