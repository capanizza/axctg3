package br.com.axialsoftware.axctg3.view.financeiro.baixatituloreceber;

import br.com.axialsoftware.axctg3.entity.financeiro.ItemReceber;
import br.com.axialsoftware.axctg3.entity.financeiro.TituloReceber;
import br.com.axialsoftware.axctg3.view.main.MainView;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.data.renderer.Renderer;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.UiComponents;
import io.jmix.flowui.component.checkbox.JmixCheckbox;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Reusa a entidade {@link TituloReceber}, com view id próprio
 * ({@code BaixaTituloReceber.detail}) — só a baixa manual (item 2+), cabeçalho
 * somente-leitura. Ver {@code TituloReceber.detail} para a tela de emissão.
 */
@Route(value = "baixaTituloRecebers/:id", layout = MainView.class)
@ViewController(id = "BaixaTituloReceber.detail")
@ViewDescriptor(path = "baixa-titulo-receber-detail-view.xml")
@EditedEntityContainer("tituloReceberDc")
@DialogMode(width = "1000px", height = "800px")
public class BaixaTituloReceberDetailView extends StandardDetailView<TituloReceber> {

    @Autowired
    private UiComponents uiComponents;

    @Supply(to = "itensDataGrid.contabilizado", subject = "renderer")
    private Renderer<ItemReceber> itensDataGridContabilizadoRenderer() {
        return new ComponentRenderer<>(itemReceber -> {
            JmixCheckbox checkbox = uiComponents.create(JmixCheckbox.class);
            checkbox.setValue(Boolean.TRUE.equals(itemReceber.getContabilizado()));
            checkbox.setReadOnly(true);
            checkbox.addClassName("grid-value-checkbox");
            return checkbox;
        });
    }

}
