package br.com.axialsoftware.axctg3.view.fiscal.itemnotasaida;

import br.com.axialsoftware.axctg3.entity.fiscal.ItemNotaSaida;
import br.com.axialsoftware.axctg3.view.main.MainView;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.view.*;

@Route(value = "item-nota-saidas/:id", layout = MainView.class)
@ViewController(id = "ItemNotaSaida.detail")
@ViewDescriptor(path = "item-nota-saida-detail-view.xml")
@EditedEntityContainer("itemNotaSaidaDc")
@DialogMode(width = "80%")
public class ItemNotaSaidaDetailView extends StandardDetailView<ItemNotaSaida> {
}
