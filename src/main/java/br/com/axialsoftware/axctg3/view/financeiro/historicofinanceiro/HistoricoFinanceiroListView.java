package br.com.axialsoftware.axctg3.view.financeiro.historicofinanceiro;

import br.com.axialsoftware.axctg3.entity.financeiro.HistoricoFinanceiro;
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

import java.util.function.Function;

@Route(value = "historico-financeiroes", layout = MainView.class)
@ViewController(id = "HistoricoFinanceiro.list")
@ViewDescriptor(path = "historico-financeiro-list-view.xml")
@LookupComponent("historicoFinanceiroesDataGrid")
@DialogMode(width = "64em")
public class HistoricoFinanceiroListView extends StandardListView<HistoricoFinanceiro> {
    @ViewComponent
    private CollectionLoader<HistoricoFinanceiro> historicoFinanceiroesDl;
    @Autowired
    private UtilGeralService utilGeralService;
    @ViewComponent
    private HorizontalLayout buttonsPanel;
    @Autowired
    private UiComponents uiComponents;

    @Subscribe
    public void onBeforeShow(final BeforeShowEvent event) {
        historicoFinanceiroesDl.setParameter("codEmpresa", utilGeralService.getCodEmpresa());
        historicoFinanceiroesDl.load();

        Dialog dialog = UiComponentUtils.findDialog(this);
        buttonsPanel.setVisible(dialog == null);
    }

    @Supply(to = "historicoFinanceiroesDataGrid.emissao", subject = "renderer")
    private Renderer<HistoricoFinanceiro> historicoFinanceiroesDataGridEmissaoRenderer() {
        return checkboxRenderer(HistoricoFinanceiro::getEmissao);
    }

    @Supply(to = "historicoFinanceiroesDataGrid.baixa", subject = "renderer")
    private Renderer<HistoricoFinanceiro> historicoFinanceiroesDataGridBaixaRenderer() {
        return checkboxRenderer(HistoricoFinanceiro::getBaixa);
    }

    @Supply(to = "historicoFinanceiroesDataGrid.parcial", subject = "renderer")
    private Renderer<HistoricoFinanceiro> historicoFinanceiroesDataGridParcialRenderer() {
        return checkboxRenderer(HistoricoFinanceiro::getParcial);
    }

    @Supply(to = "historicoFinanceiroesDataGrid.juros", subject = "renderer")
    private Renderer<HistoricoFinanceiro> historicoFinanceiroesDataGridJurosRenderer() {
        return checkboxRenderer(HistoricoFinanceiro::getJuros);
    }

    @Supply(to = "historicoFinanceiroesDataGrid.desconto", subject = "renderer")
    private Renderer<HistoricoFinanceiro> historicoFinanceiroesDataGridDescontoRenderer() {
        return checkboxRenderer(HistoricoFinanceiro::getDesconto);
    }

    private Renderer<HistoricoFinanceiro> checkboxRenderer(
            Function<HistoricoFinanceiro, Boolean> valueGetter) {
        return new ComponentRenderer<>(historicoFinanceiro -> {
            JmixCheckbox checkbox = uiComponents.create(JmixCheckbox.class);
            checkbox.setValue(Boolean.TRUE.equals(valueGetter.apply(historicoFinanceiro)));
            checkbox.setReadOnly(true);
            checkbox.addClassName("grid-value-checkbox");
            return checkbox;
        });
    }
}
