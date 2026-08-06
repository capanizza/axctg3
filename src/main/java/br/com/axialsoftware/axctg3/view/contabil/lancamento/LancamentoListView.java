package br.com.axialsoftware.axctg3.view.contabil.lancamento;

import br.com.axialsoftware.axctg3.bean.MenuBean;
import br.com.axialsoftware.axctg3.entity.cadastros.ConfigRel;
import br.com.axialsoftware.axctg3.entity.contabil.Lancamento;

import br.com.axialsoftware.axctg3.entity.contabil.LancamentoTmp;
import br.com.axialsoftware.axctg3.service.UtilGeralService;
import br.com.axialsoftware.axctg3.service.contabil.LancamentoService;
import br.com.axialsoftware.axctg3.view.main.MainView;

import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.router.Route;
import io.jmix.core.DataManager;
import io.jmix.core.SaveContext;
import io.jmix.flowui.Dialogs;
import io.jmix.flowui.ViewNavigators;
import io.jmix.flowui.action.DialogAction;
import io.jmix.flowui.app.inputdialog.DialogActions;
import io.jmix.flowui.app.inputdialog.DialogOutcome;
import io.jmix.flowui.backgroundtask.BackgroundTask;
import io.jmix.flowui.backgroundtask.TaskLifeCycle;
import io.jmix.flowui.component.UiComponentUtils;
import io.jmix.flowui.kit.action.ActionPerformedEvent;
import io.jmix.flowui.model.CollectionLoader;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static io.jmix.flowui.app.inputdialog.InputParameter.localDateParameter;


@Route(value = "lancamentoes", layout = MainView.class)
@ViewController(id = "Lancamento.list")
@ViewDescriptor(path = "lancamento-list-view.xml")
@LookupComponent("lancamentoesDataGrid")
@DialogMode(width = "64em")
public class LancamentoListView extends StandardListView<Lancamento> {

    /**
     * Folga para a importação inteira. A tentativa anterior usava 20 <em>segundos</em> e o
     * watchdog derrubava a tarefa no meio de qualquer volume real.
     */
    private static final long TIMEOUT_IMPORTACAO_MINUTOS = 30;

    static Boolean isNew = false;
    @Autowired
    private UtilGeralService utilGeralService;
    @ViewComponent
    private CollectionLoader<Lancamento> lancamentoesDl;
    @ViewComponent
    private HorizontalLayout buttonsPanel;
    @Autowired
    private Dialogs dialogs;
    @Autowired
    private DataManager dataManager;
    @Autowired
    private ViewNavigators viewNavigators;
    @Autowired
    private LancamentoService lancamentoService;
    @Autowired
    private MenuBean menuBean;

    @Subscribe
    public void onBeforeShow(final BeforeShowEvent event) {
        ConfigRel configRel = utilGeralService.prepararConfigRel();
        Integer codEmpresa = utilGeralService.getCodEmpresa();
        Optional<LocalDate> optData = Optional.ofNullable(configRel.getDataLancamentoInicial());
        LocalDate dataLancamentoInicial = optData.orElse(LocalDate.now());
        optData = Optional.ofNullable(configRel.getDataLancamentoFinal());
        LocalDate dataLancamentoFinal = optData.orElse(LocalDate.now());
        lancamentoesDl.setParameter("dataLancamentoInicial", dataLancamentoInicial);
        lancamentoesDl.setParameter("dataLancamentoFinal", dataLancamentoFinal);
        lancamentoesDl.setParameter("codEmpresa", codEmpresa);
        lancamentoesDl.load();

        Dialog dialog = UiComponentUtils.findDialog(this);
        buttonsPanel.setVisible(dialog == null);
    }

    @Override
    public String getPageTitle() {
        ConfigRel configRel = utilGeralService.prepararConfigRel();
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");
        SimpleDateFormat sdf2 = new SimpleDateFormat("MMM/yyyy");
        String title = super.getPageTitle();
        String apelidoEmpresa =  utilGeralService.getApelidoEmpresa();
        if (isNew) {
            isNew = false;
        } else {
            Optional<LocalDate> optData = Optional.ofNullable(configRel.getDataLancamentoInicial());
            LocalDate dataLancamentoInicial = optData.orElse(LocalDate.now());
            optData = Optional.ofNullable(configRel.getDataLancamentoFinal());
            LocalDate dataLancamentoFinal = optData.orElse(LocalDate.now());
            int ano = utilGeralService.getAnoContabil();
            int mes = utilGeralService.getMesContabil();
            LocalDate dt = LocalDate.of(ano, mes, 1);
            String title2 = sdf.format(utilGeralService.localDateToSqlDate(dataLancamentoInicial)) + " a " +
                    sdf.format(utilGeralService.localDateToSqlDate(dataLancamentoFinal));
            String title3 = "processando " + sdf2.format(utilGeralService.localDateToSqlDate(dt));
            title = title + " "  + title2 + " "  + title3;
        }
        return title;
    }

    @Subscribe("lancamentoesDataGrid.delimitarAction")
    public void onLancamentoesDataGridDelimitarAction(final ActionPerformedEvent event) {
        ConfigRel configRel = utilGeralService.prepararConfigRel();
        Optional<LocalDate> optData = Optional.ofNullable(configRel.getDataLancamentoInicial());
        LocalDate dataLancamentoInicial = optData.orElse(LocalDate.now());
        optData = Optional.ofNullable(configRel.getDataLancamentoFinal());
        LocalDate dataLancamentoFinal = optData.orElse(LocalDate.now());
        dialogs.createInputDialog(UiComponentUtils.getCurrentView())
                .withHeader("Lançamentos contábeis")
                .withParameters(
                        localDateParameter("dataLancamentoInicial")
                                .withLabel("Data lançamento inicial")
                                .withDefaultValue(dataLancamentoInicial),
                        localDateParameter("dataLancamentoFinal")
                                .withLabel("Data lançamento final")
                                .withDefaultValue(dataLancamentoFinal)
                )
                .withActions(DialogActions.OK_CANCEL)
                .withCloseListener(closeEvent -> {
                    if (closeEvent.closedWith(DialogOutcome.OK)) {
                        SaveContext saveContext = new SaveContext();
                        configRel.setDataLancamentoInicial(closeEvent.getValue("dataLancamentoInicial"));
                        configRel.setDataLancamentoFinal(closeEvent.getValue("dataLancamentoFinal"));
                        saveContext.saving(configRel);
                        dataManager.save(saveContext);
                        lancamentoesDl.setParameter("dataLancamentoInicial", configRel.getDataLancamentoInicial());
                        lancamentoesDl.setParameter("dataLancamentoFinal", configRel.getDataLancamentoFinal());
                        lancamentoesDl.setParameter("codEmpresa", utilGeralService.getCodEmpresa());
                        lancamentoesDl.load();
                        getPageTitle();
                        viewNavigators.listView(this, Lancamento.class)
                                .withViewClass(LancamentoListView.class)
                                .navigate();
                    }
                })
                .open();
    }

    @Subscribe("lancamentoesDataGrid.importarAction")
    public void onLancamentoesDataGridImportarAction(final ActionPerformedEvent event) {
        if (!lancamentoService.periodoDelimitadoDentroDoMesContabil()) {
            dialogs.createMessageDialog()
                    .withHeader("Importação de lançamentos")
                    .withText("Intervalo de datas fora do mês contábil")
                    .open();
            return;
        }

        List<LancamentoTmp> pendentes = lancamentoService.lancamentosTmpPendentes();
        if (pendentes.isEmpty()) {
            dialogs.createMessageDialog()
                    .withHeader("Importação de lançamentos")
                    .withText("Nenhum lançamento a ser importado no período delimitado")
                    .open();
            return;
        }

        dialogs.createOptionDialog()
                .withHeader("Importar lançamentos contábeis")
                .withText("Confirma importação de " + pendentes.size() +
                        " lançamentos do sistema antigo?")
                .withActions(
                        new DialogAction(DialogAction.Type.YES)
                                .withHandler(e -> dialogs
                                        .createBackgroundTaskDialog(new ImportarLancamentosTask(pendentes))
                                        .withHeader("Importação de lançamentos")
                                        .withText("Importando lançamentos do sistema antigo...")
                                        .withTotal(pendentes.size())
                                        .withShowProgressInPercentage(true)
                                        .withCancelAllowed(true)
                                        .open()),
                        new DialogAction(DialogAction.Type.NO)
                )
                .open();
    }

    /**
     * Importa um {@code LancamentoTmp} por iteração. O save de cada registro é o que dispara o
     * rateio de saldos no {@code LancamentoEventListener} — a parte lenta —, então publicar o
     * progresso a cada item faz a barra andar junto com o trabalho real, em vez de encher
     * primeiro e congelar depois.
     * <p>
     * O {@code BackgroundWorker} do Jmix copia o {@code Authentication} da sessão para a thread
     * do worker, por isso {@code UtilGeralService} continua respondendo empresa e período aqui
     * dentro. Em contrapartida, nada de UI dentro de {@link #run}: diálogos e recarga do grid só
     * em {@link #done} e {@link #canceled}, que o framework executa com acesso à UI.
     */
    protected class ImportarLancamentosTask extends BackgroundTask<Integer, Integer> {

        private final List<LancamentoTmp> pendentes;
        private final AtomicInteger importados = new AtomicInteger();

        protected ImportarLancamentosTask(List<LancamentoTmp> pendentes) {
            super(TIMEOUT_IMPORTACAO_MINUTOS, TimeUnit.MINUTES, LancamentoListView.this);
            this.pendentes = pendentes;
        }

        @Override
        public Integer run(TaskLifeCycle<Integer> taskLifeCycle) throws Exception {
            int ultimoNumero = 0;
            for (LancamentoTmp lancamentoTmp : pendentes) {
                if (taskLifeCycle.isCancelled() || taskLifeCycle.isInterrupted()) {
                    break;
                }
                lancamentoService.gravarLancamento(lancamentoTmp);
                ultimoNumero = Math.max(ultimoNumero, lancamentoTmp.getNumero());
                taskLifeCycle.publish(importados.incrementAndGet());
            }
            // também no cancelamento: os números já gravados foram consumidos de fato
            if (ultimoNumero > 0) {
                lancamentoService.ajustarSequenciaLancamento(ultimoNumero);
            }
            return importados.get();
        }

        @Override
        public void done(Integer total) {
            lancamentoesDl.load();
            dialogs.createMessageDialog()
                    .withHeader("Importação de lançamentos")
                    .withText(total + " lançamentos importados")
                    .open();
        }

        @Override
        public void canceled() {
            lancamentoesDl.load();
            dialogs.createMessageDialog()
                    .withHeader("Importação de lançamentos")
                    .withText("Importação interrompida com " + importados.get() +
                            " lançamentos gravados. Repetir a importação retoma de onde parou.")
                    .open();
        }
    }


    @Subscribe("listagemSwitcher.listagemItem.listagemAction")
    public void onListagemSwitcherListagemItemListagemAction(final ActionPerformedEvent event) {
        menuBean.listarLancamentos();
    }

    @Subscribe("listagemSwitcher.razaoItem.razaoAction")
    public void onListagemSwitcherRazaoItemRazaoAction(final ActionPerformedEvent event) {
        menuBean.listarRazao();
    }
}