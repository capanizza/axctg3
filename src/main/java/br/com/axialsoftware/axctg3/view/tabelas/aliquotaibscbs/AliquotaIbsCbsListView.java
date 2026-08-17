package br.com.axialsoftware.axctg3.view.tabelas.aliquotaibscbs;

import br.com.axialsoftware.axctg3.entity.tabelas.AliquotaIbsCbs;
import br.com.axialsoftware.axctg3.view.main.MainView;

import com.vaadin.flow.router.Route;
import io.jmix.flowui.view.LookupComponent;
import io.jmix.flowui.view.DialogMode;
import io.jmix.flowui.view.StandardListView;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewDescriptor;

@Route(value = "aliquota-ibs-cbses", layout = MainView.class)
@ViewController(id = "AliquotaIbsCbs.list")
@ViewDescriptor(path = "aliquota-ibs-cbs-list-view.xml")
@LookupComponent("aliquotaIbsCbsesDataGrid")
@DialogMode(width = "48em")
public class AliquotaIbsCbsListView extends StandardListView<AliquotaIbsCbs> {
}
