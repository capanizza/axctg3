package br.com.axialsoftware.axctg3.view.financeiro.baixatitulopagar;

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

/**
 * Reusa a entidade {@link TituloPagar}, com view id próprio
 * ({@code BaixaTituloPagar.detail}) — só a baixa manual (item 2+), cabeçalho
 * somente-leitura. Ver {@code TituloPagar.detail} para a tela de emissão.
 */
@Route(value = "baixaTituloPagars/:id", layout = MainView.class)
@ViewController(id = "BaixaTituloPagar.detail")
@ViewDescriptor(path = "baixa-titulo-pagar-detail-view.xml")
@EditedEntityContainer("tituloPagarDc")
@DialogMode(width = "1200px", height = "800px")
public class BaixaTituloPagarDetailView extends StandardDetailView<TituloPagar> {

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
