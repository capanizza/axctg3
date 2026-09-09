package br.com.axialsoftware.axctg3.view.financeiro.tituloreceber;

import br.com.axialsoftware.axctg3.entity.financeiro.RetornoBanco;
import br.com.axialsoftware.axctg3.service.UtilGeralService;
import br.com.axialsoftware.axctg3.view.main.MainView;

import com.vaadin.flow.router.Route;
import io.jmix.flowui.model.CollectionLoader;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Histórico de {@link RetornoBanco} — retornos bancários já processados (via
 * {@code TituloReceberListView.lerRetornoButton}). Sem ação de criar/remover, mesmo
 * raciocínio de {@code NfeInutilizacao.list}.
 */
@Route(value = "retorno-bancos", layout = MainView.class)
@ViewController(id = "RetornoBanco.list")
@ViewDescriptor(path = "retorno-banco-list-view.xml")
@LookupComponent("retornoBancosDataGrid")
@DialogMode(width = "64em")
public class RetornoBancoListView extends StandardListView<RetornoBanco> {

    @ViewComponent
    private CollectionLoader<RetornoBanco> retornoBancosDl;
    @Autowired
    private UtilGeralService utilGeralService;

    @Subscribe
    public void onBeforeShow(final BeforeShowEvent event) {
        retornoBancosDl.setParameter("codEmpresa", utilGeralService.getCodEmpresa());
        retornoBancosDl.load();
    }
}
