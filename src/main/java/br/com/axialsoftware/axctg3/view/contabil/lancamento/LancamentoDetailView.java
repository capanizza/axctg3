package br.com.axialsoftware.axctg3.view.contabil.lancamento;

import br.com.axialsoftware.axctg3.entity.contabil.Lancamento;
import br.com.axialsoftware.axctg3.view.main.MainView;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.view.EditedEntityContainer;
import io.jmix.flowui.view.StandardDetailView;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewDescriptor;


@Route(value = "lancamentoes/:id", layout = MainView.class)
@ViewController(id = "Lancamento.detail")
@ViewDescriptor(path = "lancamento-detail-view.xml")
@EditedEntityContainer("lancamentoDc")
public class LancamentoDetailView extends StandardDetailView<Lancamento> {
}