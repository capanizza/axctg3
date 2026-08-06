package br.com.axialsoftware.axctg3.view.cadastros.parceiro;

import br.com.axialsoftware.axctg3.entity.cadastros.Parceiro;
import br.com.axialsoftware.axctg3.service.UtilGeralService;
import br.com.axialsoftware.axctg3.view.main.MainView;

import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.component.UiComponentUtils;
import io.jmix.flowui.model.CollectionLoader;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;

@Route(value = "parceiroes", layout = MainView.class)
@ViewController(id = "Parceiro.list")
@ViewDescriptor(path = "parceiro-list-view.xml")
@LookupComponent("parceiroesDataGrid")
@DialogMode(width = "64em")
public class ParceiroListView extends StandardListView<Parceiro> {
    @ViewComponent
    private CollectionLoader<Parceiro> parceiroesDl;
    @Autowired
    private UtilGeralService utilGeralService;
    @ViewComponent
    private HorizontalLayout buttonsPanel;

    @Subscribe
    public void onBeforeShow(final BeforeShowEvent event) {
        parceiroesDl.setParameter("codEmpresa", utilGeralService.getCodEmpresa());
        parceiroesDl.load();

        Dialog dialog = UiComponentUtils.findDialog(this);
        buttonsPanel.setVisible(dialog == null);
    }
}
