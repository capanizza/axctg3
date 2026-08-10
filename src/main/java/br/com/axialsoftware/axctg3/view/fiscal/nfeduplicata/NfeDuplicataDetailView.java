package br.com.axialsoftware.axctg3.view.fiscal.nfeduplicata;

import br.com.axialsoftware.axctg3.entity.fiscal.NfeDuplicata;
import br.com.axialsoftware.axctg3.view.main.MainView;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.view.*;

@Route(value = "nfe-duplicatas/:id", layout = MainView.class)
@ViewController(id = "NfeDuplicata.detail")
@ViewDescriptor(path = "nfe-duplicata-detail-view.xml")
@EditedEntityContainer("nfeDuplicataDc")
@DialogMode(width = "40%")
public class NfeDuplicataDetailView extends StandardDetailView<NfeDuplicata> {
}
