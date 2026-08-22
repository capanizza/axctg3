package br.com.axialsoftware.axctg3.view.cadastros.transportadora;

import br.com.axialsoftware.axctg3.entity.cadastros.Transportadora;
import br.com.axialsoftware.axctg3.service.UtilGeralService;
import br.com.axialsoftware.axctg3.view.main.MainView;

import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.component.UiComponentUtils;
import io.jmix.flowui.model.CollectionLoader;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;

@Route(value = "transportadoras", layout = MainView.class)
@ViewController(id = "Transportadora.list")
@ViewDescriptor(path = "transportadora-list-view.xml")
@LookupComponent("transportadorasDataGrid")
@DialogMode(width = "64em")
public class TransportadoraListView extends StandardListView<Transportadora> {
    @ViewComponent
    private CollectionLoader<Transportadora> transportadorasDl;
    @Autowired
    private UtilGeralService utilGeralService;
    @ViewComponent
    private HorizontalLayout buttonsPanel;

    @Subscribe
    public void onBeforeShow(final BeforeShowEvent event) {
        transportadorasDl.setParameter("codEmpresa", utilGeralService.getCodEmpresa());
        transportadorasDl.load();

        Dialog dialog = UiComponentUtils.findDialog(this);
        buttonsPanel.setVisible(dialog == null);
    }
}
