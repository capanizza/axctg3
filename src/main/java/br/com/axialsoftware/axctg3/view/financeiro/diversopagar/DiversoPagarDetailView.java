package br.com.axialsoftware.axctg3.view.financeiro.diversopagar;

import br.com.axialsoftware.axctg3.entity.financeiro.DiversoPagar;
import br.com.axialsoftware.axctg3.entity.financeiro.ItemDiversoPagar;
import br.com.axialsoftware.axctg3.view.main.MainView;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.data.renderer.Renderer;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.UiComponents;
import io.jmix.flowui.component.checkbox.JmixCheckbox;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;

@Route(value = "diversoPagars/:id", layout = MainView.class)
@ViewController(id = "DiversoPagar.detail")
@ViewDescriptor(path = "diverso-pagar-detail-view.xml")
@EditedEntityContainer("diversoPagarDc")
public class DiversoPagarDetailView extends StandardDetailView<DiversoPagar> {

    @Autowired
    private UiComponents uiComponents;

    @Supply(to = "itensDataGrid.contabilizado", subject = "renderer")
    private Renderer<ItemDiversoPagar> itensDataGridContabilizadoRenderer() {
        return new ComponentRenderer<>(itemDiversoPagar -> {
            JmixCheckbox checkbox = uiComponents.create(JmixCheckbox.class);
            checkbox.setValue(Boolean.TRUE.equals(itemDiversoPagar.getContabilizado()));
            checkbox.setReadOnly(true);
            checkbox.addClassName("grid-value-checkbox");
            return checkbox;
        });
    }

}
