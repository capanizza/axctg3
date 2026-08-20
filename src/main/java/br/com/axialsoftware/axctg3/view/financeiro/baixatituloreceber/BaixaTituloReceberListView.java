package br.com.axialsoftware.axctg3.view.financeiro.baixatituloreceber;

import br.com.axialsoftware.axctg3.bean.MenuBean;
import br.com.axialsoftware.axctg3.entity.cadastros.ConfigRel;
import br.com.axialsoftware.axctg3.entity.financeiro.HistoricoFinanceiro;
import br.com.axialsoftware.axctg3.entity.financeiro.ItemReceber;
import br.com.axialsoftware.axctg3.entity.financeiro.TituloReceber;
import br.com.axialsoftware.axctg3.service.UtilGeralService;
import br.com.axialsoftware.axctg3.service.financeiro.ItemReceberService;
import br.com.axialsoftware.axctg3.view.main.MainView;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.data.renderer.Renderer;
import com.vaadin.flow.router.Route;
import io.jmix.core.DataManager;
import io.jmix.core.SaveContext;
import io.jmix.flowui.Dialogs;
import io.jmix.flowui.UiComponents;
import io.jmix.flowui.ViewNavigators;
import io.jmix.flowui.action.DialogAction;
import io.jmix.flowui.app.inputdialog.DialogActions;
import io.jmix.flowui.app.inputdialog.DialogOutcome;
import io.jmix.flowui.backgroundtask.BackgroundTask;
import io.jmix.flowui.backgroundtask.TaskLifeCycle;
import io.jmix.flowui.component.UiComponentUtils;
import io.jmix.flowui.component.checkbox.JmixCheckbox;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.kit.action.ActionPerformedEvent;
import io.jmix.flowui.model.CollectionContainer;
import io.jmix.flowui.model.CollectionLoader;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import static io.jmix.flowui.app.inputdialog.InputParameter.localDateParameter;

@Route(value = "baixaTituloRecebers", layout = MainView.class)
@ViewController(id = "BaixaTituloReceber.list")
@ViewDescriptor(path = "baixa-titulo-receber-list-view.xml")
@LookupComponent("tituloRecebersDataGrid")
@DialogMode(width = "64em")
public class BaixaTituloReceberListView extends StandardListView<TituloReceber> {

    @ViewComponent
    private CollectionLoader<TituloReceber> tituloRecebersDl;
    @ViewComponent
    private CollectionContainer<TituloReceber> tituloRecebersDc;
    @Autowired
    private UtilGeralService utilGeralService;
    @Autowired
    private Dialogs dialogs;
    @Autowired
    private DataManager dataManager;
    @ViewComponent
    private DataGrid<TituloReceber> tituloRecebersDataGrid;
    @Autowired
    private ViewNavigators viewNavigators;
    @Autowired
    private UiComponents uiComponents;
    @Autowired
    private ItemReceberService itemReceberService;
    @Autowired
    private MenuBean menuBean;
    @ViewComponent
    private HorizontalLayout buttonsPanel;

    // Cache do intervalo em uso, pra getPageTitle() não precisar reconsultar o
    // ConfigRel (prepararConfigRel() não é memoizado) a cada chamada do Vaadin.
    private LocalDate dataVencimentoInicial = LocalDate.now();
    private LocalDate dataVencimentoFinal = LocalDate.now();

    @Subscribe
    public void onBeforeShow(final BeforeShowEvent event) {
        ConfigRel configRel = utilGeralService.prepararConfigRel();
        dataVencimentoInicial = Optional.ofNullable(configRel.getDataVencimentoReceberInicial()).orElse(LocalDate.now());
        dataVencimentoFinal = Optional.ofNullable(configRel.getDataVencimentoReceberFinal()).orElse(LocalDate.now());
        tituloRecebersDl.setParameter("dataVencimentoInicial", dataVencimentoInicial);
        tituloRecebersDl.setParameter("dataVencimentoFinal", dataVencimentoFinal);
        tituloRecebersDl.setParameter("codEmpresa", utilGeralService.getCodEmpresa());
        tituloRecebersDl.load();

        Dialog dialog = UiComponentUtils.findDialog(this);
        buttonsPanel.setVisible(dialog == null);
    }

    @Override
    public String getPageTitle() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        return super.getPageTitle() + " vencimento: " + dataVencimentoInicial.format(formatter) + " a " + dataVencimentoFinal.format(formatter);
    }

    @Supply(to = "tituloRecebersDataGrid.aberto", subject = "renderer")
    private Renderer<TituloReceber> tituloRecebersDataGridAbertoRenderer() {
        return checkboxRenderer(TituloReceber::getAberto);
    }

    @Supply(to = "tituloRecebersDataGrid.contabilizadoEmissao", subject = "renderer")
    private Renderer<TituloReceber> tituloRecebersDataGridContabilizadoEmissaoRenderer() {
        return checkboxRenderer(TituloReceber::getContabilizadoEmissao);
    }

    @Supply(to = "tituloRecebersDataGrid.contabilizadoBaixa", subject = "renderer")
    private Renderer<TituloReceber> tituloRecebersDataGridContabilizadoBaixaRenderer() {
        return checkboxRenderer(TituloReceber::getContabilizadoBaixa);
    }

    private Renderer<TituloReceber> checkboxRenderer(Function<TituloReceber, Boolean> valueGetter) {
        return new ComponentRenderer<>(tituloReceber -> {
            JmixCheckbox checkbox = uiComponents.create(JmixCheckbox.class);
            checkbox.setValue(Boolean.TRUE.equals(valueGetter.apply(tituloReceber)));
            checkbox.setReadOnly(true);
            checkbox.addClassName("grid-value-checkbox");
            return checkbox;
        });
    }

    @Subscribe("tituloRecebersDataGrid.delimitarAction")
    public void onTituloRecebersDataGridDelimitarAction(final ActionPerformedEvent event) {
        ConfigRel configRel = utilGeralService.prepararConfigRel();
        dialogs.createInputDialog(UiComponentUtils.getCurrentView())
                .withHeader("Baixa de títulos a receber")
                .withParameters(
                        localDateParameter("dataVencimentoInicial")
                                .withLabel("Data vencimento inicial")
                                .withDefaultValue(configRel.getDataVencimentoReceberInicial()),
                        localDateParameter("dataVencimentoFinal")
                                .withLabel("Data vencimento final")
                                .withDefaultValue(configRel.getDataVencimentoReceberFinal())
                )
                .withActions(DialogActions.OK_CANCEL)
                .withCloseListener(closeEvent -> {
                    if (closeEvent.closedWith(DialogOutcome.OK)) {
                        SaveContext saveContext = new SaveContext();
                        configRel.setDataVencimentoReceberInicial(closeEvent.getValue("dataVencimentoInicial"));
                        configRel.setDataVencimentoReceberFinal(closeEvent.getValue("dataVencimentoFinal"));
                        saveContext.saving(configRel);
                        dataManager.save(saveContext);
                        dataVencimentoInicial = configRel.getDataVencimentoReceberInicial();
                        dataVencimentoFinal = configRel.getDataVencimentoReceberFinal();
                        tituloRecebersDl.setParameter("dataVencimentoInicial", dataVencimentoInicial);
                        tituloRecebersDl.setParameter("dataVencimentoFinal", dataVencimentoFinal);
                        tituloRecebersDl.setParameter("codEmpresa", utilGeralService.getCodEmpresa());
                        tituloRecebersDl.load();
                        // getPageTitle() não é reconsultado automaticamente pelo Vaadin fora
                        // de navegação — atualiza o título da aba in-place, sem navigate()
                        // (que reinstancia a view e perderia seleção múltipla/filtro/ordenação
                        // da grid, usados pelas actions de baixa em lote logo abaixo).
                        UI.getCurrent().getPage().setTitle(getPageTitle());
                    }
                })
                .open();
    }

    @Subscribe("tituloRecebersDataGrid.baixaAutomaticaAction")
    public void onTituloRecebersDataGridBaixaAutomaticaAction(final ActionPerformedEvent event) {
        Set<TituloReceber> selecionados = tituloRecebersDataGrid.getSelectedItems();
        List<TituloReceber> titulos = new ArrayList<>(selecionados);
        titulos.removeIf(tituloReceber -> !Boolean.TRUE.equals(tituloReceber.getAberto()));
        titulos.sort(Comparator.comparing(TituloReceber::getOrdemBaixa));
        if (titulos.isEmpty()) {
            dialogs.createMessageDialog()
                    .withHeader("Baixa de títulos a receber")
                    .withText("Nenhum título selecionado")
                    .open();
            return;
        }

        ConfigRel configRel = utilGeralService.prepararConfigRel();
        LocalDate dataBaixa = configRel.getDataBaixaReceber();
        dialogs.createInputDialog(UiComponentUtils.getCurrentView())
                .withHeader("Baixa de títulos a receber")
                .withParameters(
                        localDateParameter("dataBaixa")
                                .withLabel("Data baixa títulos")
                                .withDefaultValue(dataBaixa)
                )
                .withActions(DialogActions.OK_CANCEL)
                .withCloseListener(closeEvent -> {
                    if (closeEvent.closedWith(DialogOutcome.OK)) {
                        LocalDate dataBaixaEscolhida = closeEvent.getValue("dataBaixa");
                        dialogs.createOptionDialog()
                                .withHeader("Baixa de títulos a receber")
                                .withText("Confirma a baixa do(s) " + titulos.size() + " título(s) selecionado(s)?")
                                .withActions(
                                        new DialogAction(DialogAction.Type.YES)
                                                .withHandler(e -> {
                                                    HistoricoFinanceiro historicoFinanceiro = utilGeralService.getEmpresa().getHistFinBaixaReceber();
                                                    configRel.setDataBaixaReceber(dataBaixaEscolhida);
                                                    dataManager.save(configRel);
                                                    for (TituloReceber tituloReceber : titulos) {
                                                        ItemReceber itemReceber = dataManager.create(ItemReceber.class);
                                                        itemReceber.setItem(2);
                                                        itemReceber.setTituloReceber(tituloReceber);
                                                        itemReceber.setData(dataBaixaEscolhida);
                                                        itemReceber.setHistoricoFinanceiro(historicoFinanceiro);
                                                        itemReceber.setValor(tituloReceber.getValor().subtract(tituloReceber.getValorBaixado()));
                                                        itemReceber.setJuros(BigDecimal.ZERO);
                                                        itemReceber.setDesconto(BigDecimal.ZERO);
                                                        itemReceber.setContabilizado(false);
                                                        dataManager.save(itemReceber);
                                                    }
                                                    diversosBaixadosMensagem(titulos.size());
                                                    tituloRecebersDl.load();
                                                }),
                                        new DialogAction(DialogAction.Type.NO)
                                )
                                .open();
                    }
                })
                .open();
    }

    private void diversosBaixadosMensagem(int quantidade) {
        dialogs.createMessageDialog()
                .withHeader("Baixa de títulos a receber")
                .withText(quantidade + " título(s) baixado(s)")
                .open();
    }

    @Subscribe("tituloRecebersDataGrid.baixaManualAction")
    public void onTituloRecebersDataGridBaixaManualAction(final ActionPerformedEvent event) {
        TituloReceber tituloReceber = tituloRecebersDc.getItem();
        viewNavigators.detailView(this, TituloReceber.class)
                .editEntity(tituloReceber)
                .withViewClass(BaixaTituloReceberDetailView.class)
                .navigate();
    }

    @Subscribe("tituloRecebersDataGrid.lancamentoAction")
    public void onTituloRecebersDataGridLancamentoAction(final ActionPerformedEvent event) {
        Set<TituloReceber> selecionados = tituloRecebersDataGrid.getSelectedItems();
        List<TituloReceber> titulosReceber = new ArrayList<>(selecionados);
        titulosReceber.removeIf(TituloReceber::getContabilizadoBaixa);
        titulosReceber.removeIf(tituloReceber -> Boolean.TRUE.equals(tituloReceber.getAberto()));
        if (titulosReceber.isEmpty()) {
            dialogs.createMessageDialog()
                    .withHeader("Lançamentos contábeis")
                    .withText("Nenhum título para ser lançado")
                    .open();
            return;
        }

        titulosReceber.sort(Comparator.comparing(TituloReceber::getNumero));

        dialogs.createOptionDialog()
                .withHeader("Lançamentos contábeis")
                .withText("Confirma geração dos lançamentos dos títulos selecionados?")
                .withActions(
                        new DialogAction(DialogAction.Type.YES)
                                .withHandler(e -> dialogs.createBackgroundTaskDialog(new LancamentoBaixaTask(20, this, titulosReceber))
                                        .withHeader("Lançamento de baixa de títulos a receber")
                                        .withText("Gerando lançamentos...")
                                        .withTotal(titulosReceber.size())
                                        .withCancelAllowed(true)
                                        .open()),
                        new DialogAction(DialogAction.Type.NO)
                )
                .open();
    }

    protected class LancamentoBaixaTask extends BackgroundTask<Integer, Void> {
        private final List<TituloReceber> titulosReceber;

        public LancamentoBaixaTask(long timeoutSeconds, View<?> view, List<TituloReceber> titulosReceber) {
            super(timeoutSeconds, view);
            this.titulosReceber = titulosReceber;
        }

        @Override
        public Void run(TaskLifeCycle<Integer> taskLifeCycle) throws Exception {
            int i = 1;
            for (TituloReceber tituloReceber : titulosReceber) {
                itemReceberService.lancamentosBaixa(tituloReceber);
                taskLifeCycle.publish(i++);
            }
            return null;
        }

        @Override
        public void done(Void result) {
            super.done(result);
            tituloRecebersDl.load();
            dialogs.createMessageDialog()
                    .withHeader("Baixa de títulos a receber")
                    .withText(titulosReceber.size() + " título(s) lançado(s)")
                    .open();
        }
    }

    @Subscribe("tituloRecebersDataGrid.listagemAction")
    public void onTituloRecebersDataGridListagemAction(final ActionPerformedEvent event) {
        menuBean.listarBaixaTituloReceber();
    }

}
