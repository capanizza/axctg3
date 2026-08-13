package br.com.axialsoftware.axctg3.view.contabil.bem;

import br.com.axialsoftware.axctg3.entity.contabil.Bem;
import br.com.axialsoftware.axctg3.service.UtilGeralService;
import br.com.axialsoftware.axctg3.view.main.MainView;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.model.CollectionLoader;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;

@Route(value = "bens", layout = MainView.class)
@ViewController(id = "Bem.list")
@ViewDescriptor(path = "bem-list-view.xml")
@LookupComponent("bemsDataGrid")
@DialogMode(width = "64em")
public class BemListView extends StandardListView<Bem> {

    @ViewComponent
    private CollectionLoader<Bem> bemsDl;
    @Autowired
    private UtilGeralService utilGeralService;

    @Subscribe
    public void onBeforeShow(final BeforeShowEvent event) {
        bemsDl.setParameter("codEmpresa", utilGeralService.getCodEmpresa());
        bemsDl.load();
    }
}
