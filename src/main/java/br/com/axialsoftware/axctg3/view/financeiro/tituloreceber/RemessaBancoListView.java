package br.com.axialsoftware.axctg3.view.financeiro.tituloreceber;

import br.com.axialsoftware.axctg3.entity.financeiro.RemessaBanco;
import br.com.axialsoftware.axctg3.service.UtilGeralService;
import br.com.axialsoftware.axctg3.view.main.MainView;

import com.vaadin.flow.router.Route;
import io.jmix.flowui.model.CollectionLoader;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Histórico de {@link RemessaBanco} — remessas bancárias já geradas (via
 * {@code TituloReceberListView.gerarRemessaAction}). Sem ação de criar/remover: uma
 * remessa só existe depois de gerada de verdade, mesmo raciocínio de
 * {@code NfeInutilizacao.list}.
 */
@Route(value = "remessa-bancos", layout = MainView.class)
@ViewController(id = "RemessaBanco.list")
@ViewDescriptor(path = "remessa-banco-list-view.xml")
@LookupComponent("remessaBancosDataGrid")
@DialogMode(width = "56em")
public class RemessaBancoListView extends StandardListView<RemessaBanco> {

    @ViewComponent
    private CollectionLoader<RemessaBanco> remessaBancosDl;
    @Autowired
    private UtilGeralService utilGeralService;

    @Subscribe
    public void onBeforeShow(final BeforeShowEvent event) {
        remessaBancosDl.setParameter("codEmpresa", utilGeralService.getCodEmpresa());
        remessaBancosDl.load();
    }
}
