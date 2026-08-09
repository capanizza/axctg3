package br.com.axialsoftware.axctg3.view.fiscal.produto;

import br.com.axialsoftware.axctg3.entity.fiscal.Produto;
import br.com.axialsoftware.axctg3.service.UtilGeralService;
import br.com.axialsoftware.axctg3.view.main.MainView;

import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.data.renderer.Renderer;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.UiComponents;
import io.jmix.flowui.component.UiComponentUtils;
import io.jmix.flowui.component.checkbox.JmixCheckbox;
import io.jmix.flowui.model.CollectionLoader;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;

@Route(value = "produtos", layout = MainView.class)
@ViewController(id = "Produto.list")
@ViewDescriptor(path = "produto-list-view.xml")
@LookupComponent("produtosDataGrid")
@DialogMode(width = "64em")
public class ProdutoListView extends StandardListView<Produto> {

    @ViewComponent
    private CollectionLoader<Produto> produtosDl;
    @Autowired
    private UtilGeralService utilGeralService;
    @ViewComponent
    private HorizontalLayout buttonsPanel;
    @Autowired
    private UiComponents uiComponents;

    @Subscribe
    public void onBeforeShow(final BeforeShowEvent event) {
        produtosDl.setParameter("codEmpresa", utilGeralService.getCodEmpresa());
        produtosDl.load();

        Dialog dialog = UiComponentUtils.findDialog(this);
        buttonsPanel.setVisible(dialog == null);
    }

    @Supply(to = "produtosDataGrid.inativo", subject = "renderer")
    private Renderer<Produto> produtosDataGridInativoRenderer() {
        return new ComponentRenderer<>(produto -> {
            JmixCheckbox checkbox = uiComponents.create(JmixCheckbox.class);
            checkbox.setValue(Boolean.TRUE.equals(produto.getInativo()));
            checkbox.setReadOnly(true);
            checkbox.addClassName("grid-value-checkbox");
            return checkbox;
        });
    }
}
