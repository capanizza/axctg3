package br.com.axialsoftware.axctg3.view.financeiro.titulopagar;

import br.com.axialsoftware.axctg3.entity.financeiro.ItemPagar;
import br.com.axialsoftware.axctg3.entity.financeiro.TituloPagar;
import br.com.axialsoftware.axctg3.view.main.MainView;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.data.renderer.Renderer;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.UiComponents;
import io.jmix.flowui.component.checkbox.JmixCheckbox;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;

@Route(value = "tituloPagars/:id", layout = MainView.class)
@ViewController(id = "TituloPagar.detail")
@ViewDescriptor(path = "titulo-pagar-detail-view.xml")
@EditedEntityContainer("tituloPagarDc")
public class TituloPagarDetailView extends StandardDetailView<TituloPagar> {

    @Autowired
    private UiComponents uiComponents;

    @Supply(to = "itensDataGrid.contabilizado", subject = "renderer")
    private Renderer<ItemPagar> itensDataGridContabilizadoRenderer() {
        return new ComponentRenderer<>(itemPagar -> {
            JmixCheckbox checkbox = uiComponents.create(JmixCheckbox.class);
            checkbox.setValue(Boolean.TRUE.equals(itemPagar.getContabilizado()));
            checkbox.setReadOnly(true);
            checkbox.addClassName("grid-value-checkbox");
            return checkbox;
        });
    }

}
