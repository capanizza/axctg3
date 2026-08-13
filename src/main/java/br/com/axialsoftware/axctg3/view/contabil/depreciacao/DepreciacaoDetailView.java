package br.com.axialsoftware.axctg3.view.contabil.depreciacao;

import br.com.axialsoftware.axctg3.entity.contabil.Depreciacao;
import br.com.axialsoftware.axctg3.view.main.MainView;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.view.EditedEntityContainer;
import io.jmix.flowui.view.StandardDetailView;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewDescriptor;

@Route(value = "depreciacaos/:id", layout = MainView.class)
@ViewController(id = "Depreciacao.detail")
@ViewDescriptor(path = "depreciacao-detail-view.xml")
@EditedEntityContainer("depreciacaoDc")
public class DepreciacaoDetailView extends StandardDetailView<Depreciacao> {
}
