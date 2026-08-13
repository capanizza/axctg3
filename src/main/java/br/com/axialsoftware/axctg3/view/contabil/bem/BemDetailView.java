package br.com.axialsoftware.axctg3.view.contabil.bem;

import br.com.axialsoftware.axctg3.entity.contabil.Bem;
import br.com.axialsoftware.axctg3.view.main.MainView;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.view.EditedEntityContainer;
import io.jmix.flowui.view.StandardDetailView;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewDescriptor;

@Route(value = "bems/:id", layout = MainView.class)
@ViewController(id = "Bem.detail")
@ViewDescriptor(path = "bem-detail-view.xml")
@EditedEntityContainer("bemDc")
public class BemDetailView extends StandardDetailView<Bem> {
}
