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

    /**
     * Parâmetros dos itemsContainer de banco/historicoFinanceiro/contaContabil
     * (entityComboBox, troca de entityPicker) — codEmpresa/ano da sessão, mesmo
     * critério de ContaContabilListView quando aberta como lookup
     * (apenasAnaliticas=true).
     */
    @Subscribe(id = "bancosDl", target = Target.DATA_LOADER)
    public void onBancosDlPreLoad(final CollectionLoader.PreLoadEvent<Banco> event) {
        event.getSource().setParameter("codEmpresa", utilGeralService.getCodEmpresa());
    }

    @Subscribe(id = "historicosFinanceirosDl", target = Target.DATA_LOADER)
    public void onHistoricosFinanceirosDlPreLoad(final CollectionLoader.PreLoadEvent<HistoricoFinanceiro> event) {
        event.getSource().setParameter("codEmpresa", utilGeralService.getCodEmpresa());
    }

    @Subscribe(id = "contasContabeisDl", target = Target.DATA_LOADER)
    public void onContasContabeisDlPreLoad(final CollectionLoader.PreLoadEvent<ContaContabil> event) {
        event.getSource().setParameter("codEmpresa", utilGeralService.getCodEmpresa());
        event.getSource().setParameter("ano", utilGeralService.getAnoContabil());
    }
}
