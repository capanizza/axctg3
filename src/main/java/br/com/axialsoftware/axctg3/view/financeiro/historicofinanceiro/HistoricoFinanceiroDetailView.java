package br.com.axialsoftware.axctg3.view.financeiro.historicofinanceiro;

import br.com.axialsoftware.axctg3.entity.contabil.ContaContabil;
import br.com.axialsoftware.axctg3.entity.financeiro.HistoricoFinanceiro;
import br.com.axialsoftware.axctg3.service.UtilGeralService;
import br.com.axialsoftware.axctg3.view.main.MainView;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.model.CollectionLoader;
import io.jmix.flowui.model.InstanceLoader;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;

@Route(value = "historico-financeiroes/:id", layout = MainView.class)
@ViewController(id = "HistoricoFinanceiro.detail")
@ViewDescriptor(path = "historico-financeiro-detail-view.xml")
@EditedEntityContainer("historicoFinanceiroDc")
public class HistoricoFinanceiroDetailView extends StandardDetailView<HistoricoFinanceiro> {
    @ViewComponent
    private InstanceLoader<HistoricoFinanceiro> historicoFinanceiroDl;
    @Autowired
    private UtilGeralService utilGeralService;
    @ViewComponent
    private CollectionLoader<ContaContabil> contaContabilsDl;

    @Subscribe
    public void onBeforeShow(final BeforeShowEvent event) {
        contaContabilsDl.setParameter("codEmpresa", utilGeralService.getCodEmpresa());
        contaContabilsDl.setParameter("anoContabil", utilGeralService.getAnoContabil());
        contaContabilsDl.load();

        historicoFinanceiroDl.setParameter("codEmpresa", utilGeralService.getCodEmpresa());
        historicoFinanceiroDl.load();
    }
}
