package br.com.axialsoftware.axctg3.view.financeiro.baixadiversopagar;

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

/**
 * Reusa a entidade {@link DiversoPagar}, com view id próprio
 * ({@code BaixaDiversoPagar.detail}) — só a baixa manual (item 2+), cabeçalho
 * somente-leitura. Ver {@code DiversoPagar.detail} para a tela de emissão.
 */
@Route(value = "baixaDiversoPagars/:id", layout = MainView.class)
@ViewController(id = "BaixaDiversoPagar.detail")
@ViewDescriptor(path = "baixa-diverso-pagar-detail-view.xml")
@EditedEntityContainer("diversoPagarDc")
@DialogMode(width = "1200px", height = "800px")
public class BaixaDiversoPagarDetailView extends StandardDetailView<DiversoPagar> {

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
