package br.com.axialsoftware.axctg3.view.financeiro.baixatitulopagar;

import br.com.axialsoftware.axctg3.bean.MenuBean;
import br.com.axialsoftware.axctg3.entity.cadastros.ConfigRel;
import br.com.axialsoftware.axctg3.entity.financeiro.Banco;
import br.com.axialsoftware.axctg3.entity.financeiro.HistoricoFinanceiro;
import br.com.axialsoftware.axctg3.entity.financeiro.ItemPagar;
import br.com.axialsoftware.axctg3.entity.financeiro.TituloPagar;
import br.com.axialsoftware.axctg3.service.UtilGeralService;
import br.com.axialsoftware.axctg3.service.financeiro.ItemPagarService;
import br.com.axialsoftware.axctg3.view.main.MainView;
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

@Route(value = "baixaTituloPagars", layout = MainView.class)
@ViewController(id = "BaixaTituloPagar.list")
@ViewDescriptor(path = "baixa-titulo-pagar-list-view.xml")
@LookupComponent("tituloPagarsDataGrid")
@DialogMode(width = "64em")
public class BaixaTituloPagarListView extends StandardListView<TituloPagar> {

    @ViewComponent
    private CollectionLoader<TituloPagar> tituloPagarsDl;
    @Autowired
    private UtilGeralService utilGeralService;
    @ViewComponent
    private DataGrid<TituloPagar> tituloPagarsDataGrid;
    @Autowired
    private UiComponents uiComponents;
    @Autowired
    private Dialogs dialogs;
    @Autowired
    private ViewNavigators viewNavigators;
    @Autowired
    private DataManager dataManager;
    @ViewComponent
    private CollectionContainer<TituloPagar> tituloPagarsDc;
    @Autowired
    private MenuBean menuBean;
    @Autowired
    private ItemPagarService itemPagarService;
    @ViewComponent
    private HorizontalLayout buttonsPanel;

    @Subscribe
    public void onBeforeShow(final BeforeShowEvent event) {
        ConfigRel configRel = utilGeralService.prepararConfigRel();
        LocalDate dataInicial = Optional.ofNullable(configRel.getDataVencimentoPagarInicial()).orElse(LocalDate.now());
        LocalDate dataFinal = Optional.ofNullable(configRel.getDataVencimentoPagarFinal()).orElse(LocalDate.now());
        tituloPagarsDl.setParameter("dataVencimentoInicial", dataInicial);
        tituloPagarsDl.setParameter("dataVencimentoFinal", dataFinal);
        tituloPagarsDl.setParameter("codEmpresa", utilGeralService.getCodEmpresa());
        tituloPagarsDl.load();

        Dialog dialog = UiComponentUtils.findDialog(this);
        buttonsPanel.setVisible(dialog == null);
    }

    @Supply(to = "tituloPagarsDataGrid.aberto", subject = "renderer")
    private Renderer<TituloPagar> tituloPagarsDataGridAbertoRenderer() {
        return checkboxRenderer(TituloPagar::getAberto);
    }

    @Supply(to = "tituloPagarsDataGrid.contabilizadoEmissao", subject = "renderer")
    private Renderer<TituloPagar> tituloPagarsDataGridContabilizadoEmissaoRenderer() {
        return checkboxRenderer(TituloPagar::getContabilizadoEmissao);
    }

    @Supply(to = "tituloPagarsDataGrid.contabilizadoBaixa", subject = "renderer")
    private Renderer<TituloPagar> tituloPagarsDataGridContabilizadoBaixaRenderer() {
        return checkboxRenderer(TituloPagar::getContabilizadoBaixa);
    }

    private Renderer<TituloPagar> checkboxRenderer(Function<TituloPagar, Boolean> valueGetter) {
        return new ComponentRenderer<>(tituloPagar -> {
            JmixCheckbox checkbox = uiComponents.create(JmixCheckbox.class);
            checkbox.setValue(Boolean.TRUE.equals(valueGetter.apply(tituloPagar)));
            checkbox.setReadOnly(true);
            checkbox.addClassName("grid-value-checkbox");
            return checkbox;
        });
    }

    @Subscribe("tituloPagarsDataGrid.delimitarAction")
    public void onTituloPagarsDataGridDelimitarAction(final ActionPerformedEvent event) {
        ConfigRel configRel = utilGeralService.prepararConfigRel();
        dialogs.createInputDialog(UiComponentUtils.getCurrentView())
                .withHeader("Baixa de títulos a pagar")
                .withParameters(
                        localDateParameter("dataVencimentoInicial")
                                .withLabel("Data vencimento inicial")
                                .withDefaultValue(configRel.getDataVencimentoPagarInicial()),
                        localDateParameter("dataVencimentoFinal")
                                .withLabel("Data vencimento final")
                                .withDefaultValue(configRel.getDataVencimentoPagarFinal())
                )
                .withActions(DialogActions.OK_CANCEL)
                .withCloseListener(closeEvent -> {
                    if (closeEvent.closedWith(DialogOutcome.OK)) {
                        SaveContext saveContext = new SaveContext();
                        configRel.setDataVencimentoPagarInicial(closeEvent.getValue("dataVencimentoInicial"));
                        configRel.setDataVencimentoPagarFinal(closeEvent.getValue("dataVencimentoFinal"));
                        saveContext.saving(configRel);
                        dataManager.save(saveContext);
                        tituloPagarsDl.setParameter("dataVencimentoInicial", configRel.getDataVencimentoPagarInicial());
                        tituloPagarsDl.setParameter("dataVencimentoFinal", configRel.getDataVencimentoPagarFinal());
                        tituloPagarsDl.setParameter("codEmpresa", utilGeralService.getCodEmpresa());
                        tituloPagarsDl.load();
                    }
                })
                .open();
    }

    @Subscribe("tituloPagarsDataGrid.baixaAutomaticaAction")
    public void onTituloPagarsDataGridBaixaAutomaticaAction(final ActionPerformedEvent event) {
        Set<TituloPagar> selecionados = tituloPagarsDataGrid.getSelectedItems();
        List<TituloPagar> titulos = new ArrayList<>(selecionados);
        titulos.removeIf(tituloPagar -> !Boolean.TRUE.equals(tituloPagar.getAberto()));
        titulos.sort(Comparator.comparing(TituloPagar::getOrdemBaixa));
        if (titulos.isEmpty()) {
            dialogs.createMessageDialog()
                    .withHeader("Baixa de títulos a pagar")
                    .withText("Nenhum documento selecionado")
                    .open();
            return;
        }

        ConfigRel configRel = utilGeralService.prepararConfigRel();
        LocalDate dataBaixa = configRel.getDataBaixaPagar();
        Banco banco = configRel.getBanco();
        dialogs.createInputDialog(UiComponentUtils.getCurrentView())
                .withHeader("Baixa de títulos a pagar")
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
                                .withHeader("Baixa de títulos a pagar")
                                .withText("Confirma a baixa do(s) " + titulos.size() + " documento(s) selecionado(s)?")
                                .withActions(
                                        new DialogAction(DialogAction.Type.YES)
                                                .withHandler(e -> {
                                                    HistoricoFinanceiro historicoFinanceiro = utilGeralService.getEmpresa().getHistFinBaixaPagar();
                                                    configRel.setBanco(bancoEscolhido);
                                                    configRel.setDataBaixaPagar(dataBaixaEscolhida);
                                                    dataManager.save(configRel);
                                                    for (TituloPagar tituloPagar : titulos) {
                                                        ItemPagar itemPagar = dataManager.create(ItemPagar.class);
                                                        itemPagar.setTituloPagar(tituloPagar);
                                                        itemPagar.setItem(2);
                                                        itemPagar.setData(dataBaixaEscolhida);
                                                        itemPagar.setHistoricoFinanceiro(historicoFinanceiro);
                                                        itemPagar.setBanco(bancoEscolhido);
                                                        itemPagar.setValor(tituloPagar.getValor().subtract(tituloPagar.getValorBaixado()));
                                                        itemPagar.setJuros(BigDecimal.ZERO);
                                                        itemPagar.setDesconto(BigDecimal.ZERO);
                                                        itemPagar.setContabilizado(false);
                                                        dataManager.save(itemPagar);
                                                    }
                                                    tituloPagarsDl.load();
                                                    dialogs.createMessageDialog()
                                                            .withHeader("Baixa de títulos a pagar")
                                                            .withText(titulos.size() + " documento(s) baixado(s)")
                                                            .open();
                                                }),
                                        new DialogAction(DialogAction.Type.NO)
                                )
                                .open();
                    }
                })
                .open();
    }

    @Subscribe("tituloPagarsDataGrid.baixaManualAction")
    public void onTituloPagarsDataGridBaixaManualAction(final ActionPerformedEvent event) {
        TituloPagar tituloPagar = tituloPagarsDc.getItem();
        viewNavigators.detailView(this, TituloPagar.class)
                .editEntity(tituloPagar)
                .withViewClass(BaixaTituloPagarDetailView.class)
                .navigate();
    }

    @Subscribe("tituloPagarsDataGrid.lancamentoAction")
    public void onTituloPagarsDataGridLancamentoAction(final ActionPerformedEvent event) {
        Set<TituloPagar> selecionados = tituloPagarsDataGrid.getSelectedItems();
        List<TituloPagar> titulosPagar = new ArrayList<>(selecionados);
        titulosPagar.removeIf(TituloPagar::getContabilizadoBaixa);
        titulosPagar.removeIf(tituloPagar -> Boolean.TRUE.equals(tituloPagar.getAberto()));
        if (titulosPagar.isEmpty()) {
            dialogs.createMessageDialog()
                    .withHeader("Lançamentos contábeis")
                    .withText("Nenhum título para ser lançado")
                    .open();
            return;
        }

        titulosPagar.sort(Comparator.comparing(TituloPagar::getNumero));

        dialogs.createOptionDialog()
                .withHeader("Lançamentos contábeis")
                .withText("Confirma geração dos lançamentos dos títulos selecionados?")
                .withActions(
                        new DialogAction(DialogAction.Type.YES)
                                .withHandler(e -> dialogs.createBackgroundTaskDialog(new LancamentoBaixaTask(20, this, titulosPagar))
                                        .withHeader("Lançamento de baixa de títulos a pagar")
                                        .withText("Gerando lançamentos...")
                                        .withTotal(titulosPagar.size())
                                        .withCancelAllowed(true)
                                        .open()),
                        new DialogAction(DialogAction.Type.NO)
                )
                .open();
    }

    protected class LancamentoBaixaTask extends BackgroundTask<Integer, Void> {
        private final List<TituloPagar> titulosPagar;

        public LancamentoBaixaTask(long timeoutSeconds, View<?> view, List<TituloPagar> titulosPagar) {
            super(timeoutSeconds, view);
            this.titulosPagar = titulosPagar;
        }

        @Override
        public Void run(TaskLifeCycle<Integer> taskLifeCycle) throws Exception {
            int i = 1;
            for (TituloPagar tituloPagar : titulosPagar) {
                itemPagarService.lancamentosBaixa(tituloPagar);
                taskLifeCycle.publish(i++);
            }
            return null;
        }

        @Override
        public void done(Void result) {
            super.done(result);
            tituloPagarsDl.load();
            dialogs.createMessageDialog()
                    .withHeader("Baixa de títulos a pagar")
                    .withText(titulosPagar.size() + " título(s) lançado(s)")
                    .open();
        }
    }

    @Subscribe("tituloPagarsDataGrid.listagemAction")
    public void onTituloPagarsDataGridListagemAction(final ActionPerformedEvent event) {
        menuBean.listarBaixaTituloPagar();
    }

}
