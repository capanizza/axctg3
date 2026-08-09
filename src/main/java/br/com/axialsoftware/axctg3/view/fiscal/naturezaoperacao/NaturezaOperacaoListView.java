package br.com.axialsoftware.axctg3.view.fiscal.naturezaoperacao;

import br.com.axialsoftware.axctg3.entity.fiscal.NaturezaOperacao;
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

@Route(value = "natureza-operacaos", layout = MainView.class)
@ViewController(id = "NaturezaOperacao.list")
@ViewDescriptor(path = "natureza-operacao-list-view.xml")
@LookupComponent("naturezaOperacaosDataGrid")
@DialogMode(width = "64em")
public class NaturezaOperacaoListView extends StandardListView<NaturezaOperacao> {

    @ViewComponent
    private CollectionLoader<NaturezaOperacao> naturezaOperacaosDl;
    @Autowired
    private UtilGeralService utilGeralService;
    @Autowired
    private UiComponents uiComponents;
    @ViewComponent
    private HorizontalLayout buttonsPanel;

    @Subscribe
    public void onBeforeShow(final BeforeShowEvent event) {
        naturezaOperacaosDl.setParameter("codEmpresa", utilGeralService.getCodEmpresa());
        naturezaOperacaosDl.load();

        Dialog dialog = UiComponentUtils.findDialog(this);
        buttonsPanel.setVisible(dialog == null);
    }

    @Supply(to = "naturezaOperacaosDataGrid.venda", subject = "renderer")
    private Renderer<NaturezaOperacao> naturezaOperacaosDataGridVendaRenderer() {
        return new ComponentRenderer<>(naturezaOperacao -> {
            JmixCheckbox checkbox = uiComponents.create(JmixCheckbox.class);
            checkbox.setValue(Boolean.TRUE.equals(naturezaOperacao.getVenda()));
            checkbox.setReadOnly(true);
            checkbox.addClassName("grid-value-checkbox");
            return checkbox;
        });
    }
}
