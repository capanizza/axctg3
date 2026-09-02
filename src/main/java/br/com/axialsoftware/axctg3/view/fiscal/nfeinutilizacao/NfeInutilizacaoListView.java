package br.com.axialsoftware.axctg3.view.fiscal.nfeinutilizacao;

import br.com.axialsoftware.axctg3.entity.fiscal.NfeInutilizacao;
import br.com.axialsoftware.axctg3.service.UtilGeralService;
import br.com.axialsoftware.axctg3.service.fiscal.NfeInutilizacaoComprovanteService;
import br.com.axialsoftware.axctg3.view.main.MainView;

import com.vaadin.flow.router.Route;
import io.jmix.flowui.Dialogs;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.kit.action.ActionPerformedEvent;
import io.jmix.flowui.model.CollectionLoader;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Histórico de {@link NfeInutilizacao} — pedidos de inutilização de numeração de NF-e já
 * enviados (via {@code NfeListView.pedirFaixaEInutilizar}), homologados ou não. Sem ação de
 * criar: a faixa inutilizada só existe depois de um pedido de verdade contra a SEFAZ, nunca
 * digitada aqui (mesmo raciocínio de {@code Nfe.list}, que também não tem botão "novo").
 * "Imprimir" gera o comprovante em PDF (ver {@link NfeInutilizacaoComprovanteService}) — sem
 * leiaute oficial da SEFAZ pra esse documento, ao contrário do DANFE.
 */
@Route(value = "nfe-inutilizacoes", layout = MainView.class)
@ViewController(id = "NfeInutilizacao.list")
@ViewDescriptor(path = "nfe-inutilizacao-list-view.xml")
@LookupComponent("nfeInutilizacoesDataGrid")
@DialogMode(width = "64em")
public class NfeInutilizacaoListView extends StandardListView<NfeInutilizacao> {

    @ViewComponent
    private CollectionLoader<NfeInutilizacao> nfeInutilizacoesDl;
    @ViewComponent
    private DataGrid<NfeInutilizacao> nfeInutilizacoesDataGrid;
    @ViewComponent
    private MessageBundle messageBundle;
    @Autowired
    private UtilGeralService utilGeralService;
    @Autowired
    private NfeInutilizacaoComprovanteService nfeInutilizacaoComprovanteService;
    @Autowired
    private Dialogs dialogs;

    @Subscribe
    public void onBeforeShow(final BeforeShowEvent event) {
        nfeInutilizacoesDl.setParameter("codEmpresa", utilGeralService.getCodEmpresa());
        nfeInutilizacoesDl.load();
    }

    @Subscribe("nfeInutilizacoesDataGrid.imprimirAction")
    public void onNfeInutilizacoesDataGridImprimirAction(final ActionPerformedEvent event) {
        NfeInutilizacao selecionada = nfeInutilizacoesDataGrid.getSingleSelectedItem();
        if (selecionada == null) {
            dialogs.createMessageDialog()
                    .withHeader(messageBundle.getMessage("nfeInutilizacaoListView.imprimirAction.text"))
                    .withText(messageBundle.getMessage("nfeInutilizacaoListView.imprimir.naoSelecionado"))
                    .open();
            return;
        }
        nfeInutilizacaoComprovanteService.imprimir(selecionada.getId());
    }
}
