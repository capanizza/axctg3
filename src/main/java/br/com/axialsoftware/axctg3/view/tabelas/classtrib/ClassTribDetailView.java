package br.com.axialsoftware.axctg3.view.tabelas.classtrib;

import br.com.axialsoftware.axctg3.entity.tabelas.ClassTrib;
import br.com.axialsoftware.axctg3.view.main.MainView;

import com.vaadin.flow.router.Route;
import io.jmix.flowui.view.EditedEntityContainer;
import io.jmix.flowui.view.StandardDetailView;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewDescriptor;

@Route(value = "class-tribs/:id", layout = MainView.class)
@ViewController(id = "ClassTrib.detail")
@ViewDescriptor(path = "class-trib-detail-view.xml")
@EditedEntityContainer("classTribDc")
public class ClassTribDetailView extends StandardDetailView<ClassTrib> {
}
