package br.com.axialsoftware.axctg3.view.tabelas.cst;

import br.com.axialsoftware.axctg3.entity.tabelas.Cst;
import br.com.axialsoftware.axctg3.view.main.MainView;

import com.vaadin.flow.router.Route;
import io.jmix.flowui.view.LookupComponent;
import io.jmix.flowui.view.StandardListView;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewDescriptor;

@Route(value = "csts", layout = MainView.class)
@ViewController(id = "Cst.list")
@ViewDescriptor(path = "cst-list-view.xml")
@LookupComponent("cstsDataGrid")
public class CstListView extends StandardListView<Cst> {
}
