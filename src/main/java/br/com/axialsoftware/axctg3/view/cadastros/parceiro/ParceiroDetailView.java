package br.com.axialsoftware.axctg3.view.cadastros.parceiro;

import br.com.axialsoftware.axctg3.entity.cadastros.Parceiro;
import br.com.axialsoftware.axctg3.view.main.MainView;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.view.*;

@Route(value = "parceiroes/:id", layout = MainView.class)
@ViewController(id = "Parceiro.detail")
@ViewDescriptor(path = "parceiro-detail-view.xml")
@EditedEntityContainer("parceiroDc")
public class ParceiroDetailView extends StandardDetailView<Parceiro> {
}
