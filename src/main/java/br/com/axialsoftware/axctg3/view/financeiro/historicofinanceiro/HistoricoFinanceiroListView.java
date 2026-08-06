package br.com.axialsoftware.axctg3.view.financeiro.historicofinanceiro;

import br.com.axialsoftware.axctg3.entity.financeiro.HistoricoFinanceiro;
import br.com.axialsoftware.axctg3.service.UtilGeralService;
import br.com.axialsoftware.axctg3.view.main.MainView;

import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.component.UiComponentUtils;
import io.jmix.flowui.model.CollectionLoader;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;

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

    @Subscribe
    public void onBeforeShow(final BeforeShowEvent event) {
        historicoFinanceiroesDl.setParameter("codEmpresa", utilGeralService.getCodEmpresa());
        historicoFinanceiroesDl.load();

        Dialog dialog = UiComponentUtils.findDialog(this);
        buttonsPanel.setVisible(dialog == null);
    }
}
