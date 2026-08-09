package br.com.axialsoftware.axctg3.view.fiscal.notasaida;

import br.com.axialsoftware.axctg3.entity.fiscal.NotaSaida;
import br.com.axialsoftware.axctg3.view.main.MainView;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.view.*;

@Route(value = "nota-saidas/:id", layout = MainView.class)
@ViewController(id = "NotaSaida.detail")
@ViewDescriptor(path = "nota-saida-detail-view.xml")
@EditedEntityContainer("notaSaidaDc")
public class NotaSaidaDetailView extends StandardDetailView<NotaSaida> {
}
