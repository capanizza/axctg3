package br.com.axialsoftware.axctg3.view.importacao.implote;

import br.com.axialsoftware.axctg3.entity.enums.TipoLote;
import br.com.axialsoftware.axctg3.entity.importacao.ImpLote;
import br.com.axialsoftware.axctg3.service.importacao.ImportacaoBancosService;
import br.com.axialsoftware.axctg3.service.importacao.ImportacaoCentrosCustoService;
import br.com.axialsoftware.axctg3.service.importacao.ImportacaoDiversosPagarService;
import br.com.axialsoftware.axctg3.service.importacao.ImportacaoHistoricosFinanceirosService;
import br.com.axialsoftware.axctg3.service.importacao.ImportacaoHistoricosService;
import br.com.axialsoftware.axctg3.service.importacao.ImportacaoLancamentosService;
import br.com.axialsoftware.axctg3.service.importacao.ImportacaoParceirosService;
import br.com.axialsoftware.axctg3.service.importacao.ImportacaoPlanoContasService;
import br.com.axialsoftware.axctg3.service.importacao.ImportacaoTitulosPagarService;
import br.com.axialsoftware.axctg3.service.importacao.ImportacaoTitulosReceberService;
import br.com.axialsoftware.axctg3.view.main.MainView;

import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.Dialogs;
import io.jmix.flowui.action.DialogAction;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.component.textarea.JmixTextArea;
import io.jmix.flowui.kit.action.ActionPerformedEvent;
import io.jmix.flowui.kit.component.button.JmixButton;
import io.jmix.flowui.model.CollectionContainer;
import io.jmix.flowui.model.CollectionLoader;
import io.jmix.flowui.model.InstanceContainer;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Lotes exportados do sistema legado pelo projeto Axial ({@code exportacao2}): plano de contas
 * com saldos, lançamentos, históricos contábeis, bancos, históricos financeiros, parceiros,
 * centros de custo, títulos a receber e a pagar e diversos a pagar. Não há criar
 * nem editar: o lote nasce no exportador. Daqui só se confere (relatório sem gravar nada) e
 * importa (grava, se não houver erro estrutural). O relatório fica no próprio lote e aparece
 * no quadro abaixo da grade.
 * <p>
 * A importação grava na empresa do lote, não na selecionada na sessão: quem importa é a Axial,
 * para empresas de outros grupos.
 */
@Route(value = "imp-lotes", layout = MainView.class)
@ViewController(id = "ImpLote.list")
@ViewDescriptor(path = "imp-lote-list-view.xml")
@DialogMode(width = "64em")
public class ImpLoteListView extends StandardListView<ImpLote> {

    @ViewComponent
    private CollectionLoader<ImpLote> impLotesDl;
    @ViewComponent
    private CollectionContainer<ImpLote> impLotesDc;
    @ViewComponent
    private DataGrid<ImpLote> impLotesDataGrid;
    @ViewComponent
    private JmixTextArea mensagemField;
    @ViewComponent
    private MessageBundle messageBundle;
    @Autowired
    private Dialogs dialogs;
    @Autowired
    private ImportacaoPlanoContasService importacaoPlanoContasService;
    @Autowired
    private ImportacaoLancamentosService importacaoLancamentosService;
    @Autowired
    private ImportacaoHistoricosService importacaoHistoricosService;
    @Autowired
    private ImportacaoBancosService importacaoBancosService;
    @Autowired
    private ImportacaoHistoricosFinanceirosService importacaoHistoricosFinanceirosService;
    @Autowired
    private ImportacaoParceirosService importacaoParceirosService;
    @Autowired
    private ImportacaoCentrosCustoService importacaoCentrosCustoService;
    @Autowired
    private ImportacaoTitulosReceberService importacaoTitulosReceberService;
    @Autowired
    private ImportacaoTitulosPagarService importacaoTitulosPagarService;
    @Autowired
    private ImportacaoDiversosPagarService importacaoDiversosPagarService;

    @Subscribe(id = "impLotesDc", target = Target.DATA_CONTAINER)
    public void onImpLotesDcItemChange(final InstanceContainer.ItemChangeEvent<ImpLote> event) {
        ImpLote lote = event.getItem();
        mensagemField.setValue(lote == null || lote.getMensagem() == null ? "" : lote.getMensagem());
    }

    @Subscribe("impLotesDataGrid.conferirAction")
    public void onImpLotesDataGridConferirAction(final ActionPerformedEvent event) {
        ImpLote lote = loteSelecionado();
        if (lote == null) {
            return;
        }
        ImportacaoPlanoContasService.Relatorio relatorio = switch (lote.getTipo()) {
            case LANCAMENTOS -> importacaoLancamentosService.conferir(lote.getId());
            case HISTORICOS -> importacaoHistoricosService.conferir(lote.getId());
            case BANCOS -> importacaoBancosService.conferir(lote.getId());
            case HISTORICOS_FINANCEIROS -> importacaoHistoricosFinanceirosService.conferir(lote.getId());
            case PARCEIROS -> importacaoParceirosService.conferir(lote.getId());
            case CENTROS_CUSTO -> importacaoCentrosCustoService.conferir(lote.getId());
            case TITULOS_RECEBER -> importacaoTitulosReceberService.conferir(lote.getId());
            case TITULOS_PAGAR -> importacaoTitulosPagarService.conferir(lote.getId());
            case DIVERSOS_PAGAR -> importacaoDiversosPagarService.conferir(lote.getId());
            case PLANO_CONTAS -> importacaoPlanoContasService.conferir(lote.getId());
        };
        recarregar(lote);
        dialogs.createMessageDialog()
                .withHeader(messageBundle.getMessage("impLoteListView.conferirAction.text"))
                .withText(messageBundle.formatMessage("impLoteListView.resumo",
                        relatorio.getErros().size(), relatorio.getDivergencias().size(), relatorio.getAvisos().size()))
                .open();
    }

    @Subscribe("impLotesDataGrid.importarAction")
    public void onImpLotesDataGridImportarAction(final ActionPerformedEvent event) {
        ImpLote lote = loteSelecionado();
        if (lote == null) {
            return;
        }
        String confirmacao = switch (lote.getTipo()) {
            case LANCAMENTOS -> messageBundle.formatMessage("impLoteListView.importarLancamentos.confirmar",
                    lote.getPeriodo(), lote.getCodEmpresa());
            case HISTORICOS -> messageBundle.formatMessage("impLoteListView.importarHistoricos.confirmar",
                    lote.getCodEmpresa());
            case BANCOS -> messageBundle.formatMessage("impLoteListView.importarBancos.confirmar",
                    lote.getCodEmpresa());
            case HISTORICOS_FINANCEIROS -> messageBundle.formatMessage(
                    "impLoteListView.importarHistoricosFinanceiros.confirmar", lote.getCodEmpresa());
            case PARCEIROS -> messageBundle.formatMessage("impLoteListView.importarParceiros.confirmar",
                    lote.getCodEmpresa());
            case CENTROS_CUSTO -> messageBundle.formatMessage("impLoteListView.importarCentrosCusto.confirmar",
                    lote.getCodEmpresa());
            case TITULOS_RECEBER -> messageBundle.formatMessage("impLoteListView.importarTitulosReceber.confirmar",
                    lote.getPeriodo(), lote.getCodEmpresa());
            case TITULOS_PAGAR -> messageBundle.formatMessage("impLoteListView.importarTitulosPagar.confirmar",
                    lote.getPeriodo(), lote.getCodEmpresa());
            case DIVERSOS_PAGAR -> messageBundle.formatMessage("impLoteListView.importarDiversosPagar.confirmar",
                    lote.getPeriodo(), lote.getCodEmpresa());
            case PLANO_CONTAS -> messageBundle.formatMessage("impLoteListView.importar.confirmar",
                    lote.getAno(), lote.getCodEmpresa());
        };
        dialogs.createOptionDialog()
                .withHeader(messageBundle.getMessage("impLoteListView.importarAction.text"))
                .withText(confirmacao)
                .withActions(
                        new DialogAction(DialogAction.Type.YES).withHandler(e -> importar(lote)),
                        new DialogAction(DialogAction.Type.NO))
                .open();
    }

    private void importar(ImpLote lote) {
        ImportacaoPlanoContasService.Relatorio relatorio = switch (lote.getTipo()) {
            case LANCAMENTOS -> importacaoLancamentosService.importar(lote.getId());
            case HISTORICOS -> importacaoHistoricosService.importar(lote.getId());
            case BANCOS -> importacaoBancosService.importar(lote.getId());
            case HISTORICOS_FINANCEIROS -> importacaoHistoricosFinanceirosService.importar(lote.getId());
            case PARCEIROS -> importacaoParceirosService.importar(lote.getId());
            case CENTROS_CUSTO -> importacaoCentrosCustoService.importar(lote.getId());
            case TITULOS_RECEBER -> importacaoTitulosReceberService.importar(lote.getId());
            case TITULOS_PAGAR -> importacaoTitulosPagarService.importar(lote.getId());
            case DIVERSOS_PAGAR -> importacaoDiversosPagarService.importar(lote.getId());
            case PLANO_CONTAS -> importacaoPlanoContasService.importar(lote.getId());
        };
        recarregar(lote);
        String texto;
        if (!relatorio.getErros().isEmpty()) {
            texto = messageBundle.formatMessage("impLoteListView.importar.erro", relatorio.getErros().size());
        } else if (lote.getTipo() == TipoLote.HISTORICOS) {
            texto = messageBundle.formatMessage("impLoteListView.importarHistoricos.ok",
                    relatorio.getImportadas(), relatorio.getAvisos().size());
        } else if (lote.getTipo() == TipoLote.BANCOS) {
            texto = messageBundle.formatMessage("impLoteListView.importarBancos.ok",
                    relatorio.getImportadas(), relatorio.getAvisos().size());
        } else if (lote.getTipo() == TipoLote.HISTORICOS_FINANCEIROS) {
            texto = messageBundle.formatMessage("impLoteListView.importarHistoricosFinanceiros.ok",
                    relatorio.getImportadas(), relatorio.getAvisos().size());
        } else if (lote.getTipo() == TipoLote.PARCEIROS) {
            texto = messageBundle.formatMessage("impLoteListView.importarParceiros.ok",
                    relatorio.getImportadas(), relatorio.getAvisos().size());
        } else if (lote.getTipo() == TipoLote.CENTROS_CUSTO) {
            texto = messageBundle.formatMessage("impLoteListView.importarCentrosCusto.ok",
                    relatorio.getImportadas(), relatorio.getAvisos().size());
        } else if (lote.getTipo() == TipoLote.TITULOS_RECEBER) {
            texto = messageBundle.formatMessage("impLoteListView.importarTitulosReceber.ok",
                    relatorio.getImportadas(), relatorio.getDivergencias().size(), relatorio.getAvisos().size());
        } else if (lote.getTipo() == TipoLote.TITULOS_PAGAR) {
            texto = messageBundle.formatMessage("impLoteListView.importarTitulosPagar.ok",
                    relatorio.getImportadas(), relatorio.getDivergencias().size(), relatorio.getAvisos().size());
        } else if (lote.getTipo() == TipoLote.DIVERSOS_PAGAR) {
            texto = messageBundle.formatMessage("impLoteListView.importarDiversosPagar.ok",
                    relatorio.getImportadas(), relatorio.getDivergencias().size(), relatorio.getAvisos().size());
        } else {
            texto = messageBundle.formatMessage(lote.getTipo() == TipoLote.LANCAMENTOS
                            ? "impLoteListView.importarLancamentos.ok" : "impLoteListView.importar.ok",
                    relatorio.getImportadas(), relatorio.getDivergencias().size(), relatorio.getAvisos().size());
        }
        dialogs.createMessageDialog()
                .withHeader(messageBundle.getMessage("impLoteListView.importarAction.text"))
                .withText(texto)
                .open();
    }

    @Subscribe(id = "atualizarButton", subject = "clickListener")
    public void onAtualizarButtonClick(final ClickEvent<JmixButton> event) {
        impLotesDl.load();
    }

    private ImpLote loteSelecionado() {
        ImpLote lote = impLotesDataGrid.getSingleSelectedItem();
        if (lote == null) {
            dialogs.createMessageDialog()
                    .withHeader(messageBundle.getMessage("impLoteListView.title"))
                    .withText(messageBundle.getMessage("impLoteListView.naoSelecionado"))
                    .open();
            return null;
        }
        if (lote.getTipo() == null) {
            dialogs.createMessageDialog()
                    .withHeader(messageBundle.getMessage("impLoteListView.title"))
                    .withText(messageBundle.getMessage("impLoteListView.tipoNaoSuportado"))
                    .open();
            return null;
        }
        return lote;
    }

    /** Recarrega a grade e reposiciona no lote, para o quadro mostrar o relatório novo. */
    private void recarregar(ImpLote lote) {
        impLotesDl.load();
        ImpLote recarregado = impLotesDc.getItemOrNull(lote.getId());
        if (recarregado != null) {
            impLotesDataGrid.select(recarregado);
        }
    }
}
