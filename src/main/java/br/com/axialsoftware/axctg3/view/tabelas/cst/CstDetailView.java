package br.com.axialsoftware.axctg3.view.tabelas.cst;

import br.com.axialsoftware.axctg3.entity.tabelas.Cst;
import br.com.axialsoftware.axctg3.view.main.MainView;

import com.vaadin.flow.router.Route;
import io.jmix.flowui.view.EditedEntityContainer;
import io.jmix.flowui.view.StandardDetailView;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewDescriptor;

@Route(value = "csts/:id", layout = MainView.class)
@ViewController(id = "Cst.detail")
@ViewDescriptor(path = "cst-detail-view.xml")
@EditedEntityContainer("cstDc")
public class CstDetailView extends StandardDetailView<Cst> {
}
