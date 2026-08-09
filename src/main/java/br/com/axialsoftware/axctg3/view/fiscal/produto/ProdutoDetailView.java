package br.com.axialsoftware.axctg3.view.fiscal.produto;

import br.com.axialsoftware.axctg3.entity.fiscal.Produto;
import br.com.axialsoftware.axctg3.view.main.MainView;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.view.*;

@Route(value = "produtos/:id", layout = MainView.class)
@ViewController(id = "Produto.detail")
@ViewDescriptor(path = "produto-detail-view.xml")
@EditedEntityContainer("produtoDc")
public class ProdutoDetailView extends StandardDetailView<Produto> {
}
