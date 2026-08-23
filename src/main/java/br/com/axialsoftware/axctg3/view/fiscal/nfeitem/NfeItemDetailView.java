package br.com.axialsoftware.axctg3.view.fiscal.nfeitem;

import br.com.axialsoftware.axctg3.entity.fiscal.NfeItem;
import br.com.axialsoftware.axctg3.view.main.MainView;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.view.*;

@Route(value = "nfe-itens/:id", layout = MainView.class)
@ViewController(id = "NfeItem.detail")
@ViewDescriptor(path = "nfe-item-detail-view.xml")
@EditedEntityContainer("nfeItemDc")
@DialogMode(width = "80%")
public class NfeItemDetailView extends StandardDetailView<NfeItem> {
}
