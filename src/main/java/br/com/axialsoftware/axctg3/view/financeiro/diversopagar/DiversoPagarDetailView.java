package br.com.axialsoftware.axctg3.view.financeiro.diversopagar;

import br.com.axialsoftware.axctg3.entity.cadastros.Parceiro;
import br.com.axialsoftware.axctg3.entity.contabil.ContaContabil;
import br.com.axialsoftware.axctg3.entity.financeiro.DiversoPagar;
import br.com.axialsoftware.axctg3.entity.financeiro.ItemDiversoPagar;
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

@Route(value = "diversoPagars/:id", layout = MainView.class)
@ViewController(id = "DiversoPagar.detail")
@ViewDescriptor(path = "diverso-pagar-detail-view.xml")
@EditedEntityContainer("diversoPagarDc")
public class DiversoPagarDetailView extends StandardDetailView<DiversoPagar> {

    @ViewComponent
    private CollectionLoader<Parceiro> parceirosDl;
    @Autowired
    private UtilGeralService utilGeralService;
    @Autowired
    private UiComponents uiComponents;
    @ViewComponent
    private CollectionLoader<ContaContabil> contaContabilsDl;

    @Subscribe
    public void onBeforeShow(final BeforeShowEvent event) {
        parceirosDl.setParameter("codEmpresa", utilGeralService.getCodEmpresa());
        parceirosDl.load();

        contaContabilsDl.setParameter("codEmpresa", utilGeralService.getCodEmpresa());
        contaContabilsDl.setParameter("anoContabil", utilGeralService.getAnoContabil());
        contaContabilsDl.load();
    }

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
