package br.com.axialsoftware.axctg3.view.fiscal.nfepagamento;

import br.com.axialsoftware.axctg3.entity.fiscal.NfePagamento;
import br.com.axialsoftware.axctg3.view.main.MainView;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.view.*;

@Route(value = "nfe-pagamentos/:id", layout = MainView.class)
@ViewController(id = "NfePagamento.detail")
@ViewDescriptor(path = "nfe-pagamento-detail-view.xml")
@EditedEntityContainer("nfePagamentoDc")
@DialogMode(width = "50%")
public class NfePagamentoDetailView extends StandardDetailView<NfePagamento> {
}
