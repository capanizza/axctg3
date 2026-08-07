package br.com.axialsoftware.axctg3.view.financeiro.tituloreceber;

import br.com.axialsoftware.axctg3.entity.financeiro.ItemReceber;
import br.com.axialsoftware.axctg3.entity.financeiro.TituloReceber;
import br.com.axialsoftware.axctg3.service.financeiro.TituloReceberService;
import br.com.axialsoftware.axctg3.view.main.MainView;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.data.renderer.Renderer;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.UiComponents;
import io.jmix.flowui.component.checkbox.JmixCheckbox;
import io.jmix.flowui.component.textfield.TypedTextField;
import io.jmix.flowui.model.InstanceContainer;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;

@Route(value = "tituloRecebers/:id", layout = MainView.class)
@ViewController(id = "TituloReceber.detail")
@ViewDescriptor(path = "titulo-receber-detail-view.xml")
@EditedEntityContainer("tituloReceberDc")
public class TituloReceberDetailView extends StandardDetailView<TituloReceber> {

    @ViewComponent
    private InstanceContainer<TituloReceber> tituloReceberDc;
    @Autowired
    private TituloReceberService tituloReceberService;
    @ViewComponent
    private TypedTextField<BigDecimal> valorField;
    @Autowired
    private UiComponents uiComponents;

    @Subscribe
    public void onBeforeShow(final BeforeShowEvent event) {
        TituloReceber tituloReceber = tituloReceberDc.getItem();
        valorField.setReadOnly(tituloReceberService.valorRecebidoTitulo(tituloReceber).compareTo(BigDecimal.ZERO) != 0);
    }

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
