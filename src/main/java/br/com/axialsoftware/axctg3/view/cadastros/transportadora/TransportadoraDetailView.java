package br.com.axialsoftware.axctg3.view.cadastros.transportadora;

import br.com.axialsoftware.axctg3.entity.cadastros.Transportadora;
import br.com.axialsoftware.axctg3.view.main.MainView;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.view.*;

@Route(value = "transportadoras/:id", layout = MainView.class)
@ViewController(id = "Transportadora.detail")
@ViewDescriptor(path = "transportadora-detail-view.xml")
@EditedEntityContainer("transportadoraDc")
public class TransportadoraDetailView extends StandardDetailView<Transportadora> {
}
