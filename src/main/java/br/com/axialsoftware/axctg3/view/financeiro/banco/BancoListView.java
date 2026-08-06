package br.com.axialsoftware.axctg3.view.financeiro.banco;

import br.com.axialsoftware.axctg3.entity.financeiro.Banco;

import br.com.axialsoftware.axctg3.service.financeiro.BancoUpdateService;
import br.com.axialsoftware.axctg3.service.UtilGeralService;
import br.com.axialsoftware.axctg3.view.main.MainView;

import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.component.UiComponentUtils;
import io.jmix.flowui.model.CollectionLoader;
import io.jmix.flowui.view.*;


import org.springframework.beans.factory.annotation.Autowired;


import java.util.Collection;


@Route(value = "bancoes", layout = MainView.class)
@ViewController(id = "Banco.list")
@ViewDescriptor(path = "banco-list-view.xml")
@LookupComponent("bancoesDataGrid")
@DialogMode(width = "64em")
public class BancoListView extends StandardListView<Banco> {
    @Autowired
    private BancoUpdateService updateService;
    @ViewComponent
    private CollectionLoader<Banco> bancoesDl;
    @Autowired
    private UtilGeralService utilGeralService;
    @ViewComponent
    private HorizontalLayout buttonsPanel;

    @Install(to = "bancoesDataGrid.removeAction", subject = "delegate")
    private void bancoesDataGridRemoveDelegate(final Collection<Banco> collection) {
        collection.forEach(updateService::remove);
    }

    @Subscribe
    public void onBeforeShow(final BeforeShowEvent event) {
        bancoesDl.setParameter("codEmpresa", utilGeralService.getCodEmpresa());
        bancoesDl.load();

        Dialog dialog = UiComponentUtils.findDialog(this);
        buttonsPanel.setVisible(dialog == null);
    }
}