package br.com.axialsoftware.axctg3.view.financeiro.movimentobanco;

import br.com.axialsoftware.axctg3.entity.financeiro.MovimentoBanco;
import br.com.axialsoftware.axctg3.view.main.MainView;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.view.*;

@Route(value = "movimentoBancoes/:id", layout = MainView.class)
@ViewController(id = "MovimentoBanco.detail")
@ViewDescriptor(path = "movimento-banco-detail-view.xml")
@EditedEntityContainer("movimentoBancoDc")
public class MovimentoBancoDetailView extends StandardDetailView<MovimentoBanco> {
}
