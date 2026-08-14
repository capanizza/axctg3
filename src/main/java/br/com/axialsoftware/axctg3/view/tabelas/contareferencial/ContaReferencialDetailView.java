package br.com.axialsoftware.axctg3.view.tabelas.contareferencial;

import br.com.axialsoftware.axctg3.entity.tabelas.ContaReferencial;
import br.com.axialsoftware.axctg3.view.main.MainView;

import com.vaadin.flow.router.Route;
import io.jmix.flowui.view.EditedEntityContainer;
import io.jmix.flowui.view.StandardDetailView;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewDescriptor;

@Route(value = "conta-referencials/:id", layout = MainView.class)
@ViewController(id = "ContaReferencial.detail")
@ViewDescriptor(path = "conta-referencial-detail-view.xml")
@EditedEntityContainer("contaReferencialDc")
public class ContaReferencialDetailView extends StandardDetailView<ContaReferencial> {
}
