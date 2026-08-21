package br.com.axialsoftware.axctg3.view.financeiro.baixadiversopagar;

import br.com.axialsoftware.axctg3.bean.MenuBean;
import br.com.axialsoftware.axctg3.entity.cadastros.ConfigRel;
import br.com.axialsoftware.axctg3.entity.financeiro.Banco;
import br.com.axialsoftware.axctg3.entity.financeiro.DiversoPagar;
import br.com.axialsoftware.axctg3.entity.financeiro.HistoricoFinanceiro;
import br.com.axialsoftware.axctg3.entity.financeiro.ItemDiversoPagar;
import br.com.axialsoftware.axctg3.service.UtilGeralService;
import br.com.axialsoftware.axctg3.service.financeiro.ItemDiversoPagarService;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import static io.jmix.flowui.app.inputdialog.InputParameter.entityParameter;
import static io.jmix.flowui.app.inputdialog.InputParameter.localDateParameter;

@Route(value = "baixaDiversoPagars", layout = MainView.class)
@ViewController(id = "BaixaDiversoPagar.list")
@ViewDescriptor(path = "baixa-diverso-pagar-list-view.xml")
@LookupComponent("diversoPagarsDataGrid")
@DialogMode(width = "64em")
public class BaixaDiversoPagarListView extends StandardListView<DiversoPagar> {

    @ViewComponent
    private CollectionLoader<DiversoPagar> diversoPagarsDl;
    @Autowired
    private UtilGeralService utilGeralService;
    @ViewComponent
    private DataGrid<DiversoPagar> diversoPagarsDataGrid;
    @Autowired
    private UiComponents uiComponents;
    @Autowired
    private Dialogs dialogs;
    @Autowired
    private ViewNavigators viewNavigators;
    @Autowired
    private DataManager dataManager;
    @ViewComponent
    private CollectionContainer<DiversoPagar> diversoPagarsDc;
    @Autowired
    private MenuBean menuBean;
    @Autowired
    private ItemDiversoPagarService itemDiversoPagarService;
    @ViewComponent
    private HorizontalLayout buttonsPanel;

    // Cache do intervalo em uso, pra getPageTitle() não precisar reconsultar o
    // ConfigRel (prepararConfigRel() não é memoizado) a cada chamada do Vaadin.
    private LocalDate dataVencimentoInicial = LocalDate.now();
    private LocalDate dataVencimentoFinal = LocalDate.now();

    @Subscribe
    public void onBeforeShow(final BeforeShowEvent event) {
        ConfigRel configRel = utilGeralService.prepararConfigRel();
        dataVencimentoInicial = Optional.ofNullable(configRel.getDataVencimentoDiversoInicial()).orElse(LocalDate.now());
        dataVencimentoFinal = Optional.ofNullable(configRel.getDataVencimentoDiversoFinal()).orElse(LocalDate.now());
        diversoPagarsDl.setParameter("dataVencimentoInicial", dataVencimentoInicial);
        diversoPagarsDl.setParameter("dataVencimentoFinal", dataVencimentoFinal);
        diversoPagarsDl.setParameter("codEmpresa", utilGeralService.getCodEmpresa());
        diversoPagarsDl.load();

        Dialog dialog = UiComponentUtils.findDialog(this);
        buttonsPanel.setVisible(dialog == null);
    }

    @Override
    public String getPageTitle() {
        return super.getPageTitle() + utilGeralService.formatIntervaloTitulo("vencimento", dataVencimentoInicial, dataVencimentoFinal);
    }

    @Supply(to = "diversoPagarsDataGrid.aberto", subject = "renderer")
    private Renderer<DiversoPagar> diversoPagarsDataGridAbertoRenderer() {
        return checkboxRenderer(DiversoPagar::getAberto);
    }

    @Supply(to = "diversoPagarsDataGrid.contabilizadoEmissao", subject = "renderer")
    private Renderer<DiversoPagar> diversoPagarsDataGridContabilizadoEmissaoRenderer() {
        return checkboxRenderer(DiversoPagar::getContabilizadoEmissao);
    }

    @Supply(to = "diversoPagarsDataGrid.contabilizadoBaixa", subject = "renderer")
    private Renderer<DiversoPagar> diversoPagarsDataGridContabilizadoBaixaRenderer() {
        return checkboxRenderer(DiversoPagar::getContabilizadoBaixa);
    }

    private Renderer<DiversoPagar> checkboxRenderer(Function<DiversoPagar, Boolean> valueGetter) {
        return new ComponentRenderer<>(diversoPagar -> {
            JmixCheckbox checkbox = uiComponents.create(JmixCheckbox.class);
            checkbox.setValue(Boolean.TRUE.equals(valueGetter.apply(diversoPagar)));
            checkbox.setReadOnly(true);
            checkbox.addClassName("grid-value-checkbox");
            return checkbox;
        });
    }

    @Subscribe("diversoPagarsDataGrid.delimitarAction")
    public void onDiversoPagarsDataGridDelimitarAction(final ActionPerformedEvent event) {
        ConfigRel configRel = utilGeralService.prepararConfigRel();
        dialogs.createInputDialog(UiComponentUtils.getCurrentView())
                .withHeader("Baixa de diversos a pagar")
                .withParameters(
                        localDateParameter("dataVencimentoInicial")
                                .withLabel("Data vencimento inicial")
                                .withDefaultValue(configRel.getDataVencimentoDiversoInicial()),
                        localDateParameter("dataVencimentoFinal")
                                .withLabel("Data vencimento final")
                                .withDefaultValue(configRel.getDataVencimentoDiversoFinal())
                )
                .withActions(DialogActions.OK_CANCEL)
                .withCloseListener(closeEvent -> {
                    if (closeEvent.closedWith(DialogOutcome.OK)) {
                        SaveContext saveContext = new SaveContext();
                        configRel.setDataVencimentoDiversoInicial(closeEvent.getValue("dataVencimentoInicial"));
                        configRel.setDataVencimentoDiversoFinal(closeEvent.getValue("dataVencimentoFinal"));
                        saveContext.saving(configRel);
                        dataManager.save(saveContext);
                        dataVencimentoInicial = Optional.ofNullable(configRel.getDataVencimentoDiversoInicial()).orElse(LocalDate.now());
                        dataVencimentoFinal = Optional.ofNullable(configRel.getDataVencimentoDiversoFinal()).orElse(LocalDate.now());
                        diversoPagarsDl.setParameter("dataVencimentoInicial", dataVencimentoInicial);
                        diversoPagarsDl.setParameter("dataVencimentoFinal", dataVencimentoFinal);
                        diversoPagarsDl.setParameter("codEmpresa", utilGeralService.getCodEmpresa());
                        diversoPagarsDl.load();
                        // getPageTitle() não é reconsultado automaticamente pelo Vaadin fora
                        // de navegação — atualiza o título da aba in-place, sem navigate()
                        // (que reinstancia a view e perderia seleção múltipla/filtro/ordenação
                        // da grid, usados pelas actions de baixa em lote logo abaixo).
                        UI.getCurrent().getPage().setTitle(getPageTitle());
                    }
                })
                .open();
    }

    @Subscribe("diversoPagarsDataGrid.baixaAutomaticaAction")
    public void onDiversoPagarsDataGridBaixaAutomaticaAction(final ActionPerformedEvent event) {
        Set<DiversoPagar> selecionados = diversoPagarsDataGrid.getSelectedItems();
        List<DiversoPagar> diversos = new ArrayList<>(selecionados);
        diversos.removeIf(diversoPagar -> !Boolean.TRUE.equals(diversoPagar.getAberto()));
        diversos.sort(Comparator.comparing(DiversoPagar::getOrdemBaixa));
        if (diversos.isEmpty()) {
            dialogs.createMessageDialog()
                    .withHeader("Baixa de diversos a pagar")
                    .withText("Nenhum documento selecionado")
                    .open();
            return;
        }

        ConfigRel configRel = utilGeralService.prepararConfigRel();
        LocalDate dataBaixa = configRel.getDataBaixaPagar();
        Banco banco = configRel.getBanco();
        dialogs.createInputDialog(UiComponentUtils.getCurrentView())
                .withHeader("Baixa de diversos a pagar")
                .withParameters(
                        localDateParameter("dataBaixa")
                                .withLabel("Data baixa títulos")
                                .withDefaultValue(dataBaixa),
                        entityParameter("banco", Banco.class)
                                .withLabel("Banco")
                                .withDefaultValue(banco)
                                .withRequired(true)
                )
                .withActions(DialogActions.OK_CANCEL)
                .withCloseListener(closeEvent -> {
                    if (closeEvent.closedWith(DialogOutcome.OK)) {
                        LocalDate dataBaixaEscolhida = closeEvent.getValue("dataBaixa");
                        Banco bancoEscolhido = closeEvent.getValue("banco");
                        dialogs.createOptionDialog()
                                .withHeader("Baixa de diversos a pagar")
                                .withText("Confirma a baixa do(s) " + diversos.size() + " documento(s) selecionado(s)?")
                                .withActions(
                                        new DialogAction(DialogAction.Type.YES)
                                                .withHandler(e -> {
                                                    HistoricoFinanceiro historicoFinanceiro = utilGeralService.getEmpresa().getHistFinBaixaPagar();
                                                    configRel.setBanco(bancoEscolhido);
                                                    configRel.setDataBaixaPagar(dataBaixaEscolhida);
                                                    dataManager.save(configRel);
                                                    for (DiversoPagar diversoPagar : diversos) {
                                                        ItemDiversoPagar itemDiversoPagar = dataManager.create(ItemDiversoPagar.class);
                                                        itemDiversoPagar.setDiversoPagar(diversoPagar);
                                                        itemDiversoPagar.setItem(2);
                                                        itemDiversoPagar.setData(dataBaixaEscolhida);
                                                        itemDiversoPagar.setHistoricoFinanceiro(historicoFinanceiro);
                                                        itemDiversoPagar.setBanco(bancoEscolhido);
                                                        itemDiversoPagar.setValor(diversoPagar.getValor().subtract(diversoPagar.getValorBaixado()));
                                                        itemDiversoPagar.setJuros(BigDecimal.ZERO);
                                                        itemDiversoPagar.setDesconto(BigDecimal.ZERO);
                                                        itemDiversoPagar.setContabilizado(false);
                                                        dataManager.save(itemDiversoPagar);
                                                    }
                                                    diversoPagarsDl.load();
                                                    dialogs.createMessageDialog()
                                                            .withHeader("Baixa de diversos a pagar")
                                                            .withText(diversos.size() + " documento(s) baixado(s)")
                                                            .open();
                                                }),
                                        new DialogAction(DialogAction.Type.NO)
                                )
                                .open();
                    }
                })
                .open();
    }

    @Subscribe("diversoPagarsDataGrid.baixaManualAction")
    public void onDiversoPagarsDataGridBaixaManualAction(final ActionPerformedEvent event) {
        DiversoPagar diversoPagar = diversoPagarsDc.getItem();
        viewNavigators.detailView(this, DiversoPagar.class)
                .editEntity(diversoPagar)
                .withViewClass(BaixaDiversoPagarDetailView.class)
                .navigate();
    }

    @Subscribe("diversoPagarsDataGrid.lancamentoAction")
    public void onDiversoPagarsDataGridLancamentoAction(final ActionPerformedEvent event) {
        Set<DiversoPagar> selecionados = diversoPagarsDataGrid.getSelectedItems();
        List<DiversoPagar> diversosPagar = new ArrayList<>(selecionados);
        diversosPagar.removeIf(DiversoPagar::getContabilizadoBaixa);
        diversosPagar.removeIf(diversoPagar -> Boolean.TRUE.equals(diversoPagar.getAberto()));
        if (diversosPagar.isEmpty()) {
            dialogs.createMessageDialog()
                    .withHeader("Lançamentos contábeis")
                    .withText("Nenhum diverso para ser lançado")
                    .open();
            return;
        }

        diversosPagar.sort(Comparator.comparing(DiversoPagar::getNumero));

        dialogs.createOptionDialog()
                .withHeader("Lançamentos contábeis")
                .withText("Confirma geração dos lançamentos dos diversos selecionados?")
                .withActions(
                        new DialogAction(DialogAction.Type.YES)
                                .withHandler(e -> dialogs.createBackgroundTaskDialog(new LancamentoBaixaTask(20, this, diversosPagar))
                                        .withHeader("Lançamento de baixa de diversos a pagar")
                                        .withText("Gerando lançamentos...")
                                        .withTotal(diversosPagar.size())
                                        .withCancelAllowed(true)
                                        .open()),
                        new DialogAction(DialogAction.Type.NO)
                )
                .open();
    }

    protected class LancamentoBaixaTask extends BackgroundTask<Integer, Void> {
        private final List<DiversoPagar> diversosPagar;

        public LancamentoBaixaTask(long timeoutSeconds, View<?> view, List<DiversoPagar> diversosPagar) {
            super(timeoutSeconds, view);
            this.diversosPagar = diversosPagar;
        }

        @Override
        public Void run(TaskLifeCycle<Integer> taskLifeCycle) throws Exception {
            int i = 1;
            for (DiversoPagar diversoPagar : diversosPagar) {
                itemDiversoPagarService.lancamentosBaixa(diversoPagar);
                taskLifeCycle.publish(i++);
            }
            return null;
        }

        @Override
        public void done(Void result) {
            super.done(result);
            diversoPagarsDl.load();
            dialogs.createMessageDialog()
                    .withHeader("Baixa de diversos a pagar")
                    .withText(diversosPagar.size() + " diverso(s) lançado(s)")
                    .open();
        }
    }

    @Subscribe("diversoPagarsDataGrid.listagemAction")
    public void onDiversoPagarsDataGridListagemAction(final ActionPerformedEvent event) {
        menuBean.listarBaixaDiversosPagar();
    }

}
