package br.com.axialsoftware.axctg3.view.fiscal.nfe;

import br.com.axialsoftware.axctg3.entity.fiscal.Nfe;
import br.com.axialsoftware.axctg3.view.main.MainView;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.view.*;

@Route(value = "nfes/:id", layout = MainView.class)
@ViewController(id = "Nfe.detail")
@ViewDescriptor(path = "nfe-detail-view.xml")
@EditedEntityContainer("nfeDc")
public class NfeDetailView extends StandardDetailView<Nfe> {
}
