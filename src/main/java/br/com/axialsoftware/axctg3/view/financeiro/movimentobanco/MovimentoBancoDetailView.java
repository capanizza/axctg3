package br.com.axialsoftware.axctg3.view.financeiro.movimentobanco;

import br.com.axialsoftware.axctg3.entity.contabil.ContaContabil;
import br.com.axialsoftware.axctg3.entity.financeiro.Banco;
import br.com.axialsoftware.axctg3.entity.financeiro.HistoricoFinanceiro;
import br.com.axialsoftware.axctg3.entity.financeiro.MovimentoBanco;
import br.com.axialsoftware.axctg3.service.UtilGeralService;
import br.com.axialsoftware.axctg3.view.main.MainView;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.model.CollectionLoader;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;

@Route(value = "movimentoBancoes/:id", layout = MainView.class)
@ViewController(id = "MovimentoBanco.detail")
@ViewDescriptor(path = "movimento-banco-detail-view.xml")
@EditedEntityContainer("movimentoBancoDc")
public class MovimentoBancoDetailView extends StandardDetailView<MovimentoBanco> {
    @Autowired
    private UtilGeralService utilGeralService;
    @ViewComponent
    private CollectionLoader<Banco> bancosDl;
    @ViewComponent
    private CollectionLoader<HistoricoFinanceiro> historicosFinanceirosDl;
    @ViewComponent
    private CollectionLoader<ContaContabil> contasContabeisDl;

    /**
     * Carrega manualmente os itemsContainer de banco/historicoFinanceiro/contaContabil
     * (entityComboBox, troca de entityPicker) — dataLoadCoordinator auto="true" NÃO
     * carrega sozinho um loader com parâmetro "solto"; precisa chamar .load() na mão
     * (ver memória entitycombobox-toggle-nao-abre). codEmpresa/ano da sessão, mesmo
     * critério de ContaContabilListView quando aberta como lookup
     * (apenasAnaliticas=true).
     */
    @Subscribe
    public void onBeforeShow(final BeforeShowEvent event) {
        Integer codEmpresa = utilGeralService.getCodEmpresa();
        bancosDl.setParameter("codEmpresa", codEmpresa);
        bancosDl.load();

        historicosFinanceirosDl.setParameter("codEmpresa", codEmpresa);
        historicosFinanceirosDl.load();

        contasContabeisDl.setParameter("codEmpresa", codEmpresa);
        contasContabeisDl.setParameter("ano", utilGeralService.getAnoContabil());
        contasContabeisDl.load();
    }
}
