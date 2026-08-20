package br.com.axialsoftware.axctg3.view.financeiro.titulopagar;

import br.com.axialsoftware.axctg3.bean.MenuBean;
import br.com.axialsoftware.axctg3.entity.cadastros.ConfigRel;
import br.com.axialsoftware.axctg3.entity.financeiro.TituloPagar;
import br.com.axialsoftware.axctg3.service.UtilGeralService;
import br.com.axialsoftware.axctg3.service.financeiro.TituloPagarService;
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
import io.jmix.flowui.action.DialogAction;
import io.jmix.flowui.app.inputdialog.DialogActions;
import io.jmix.flowui.app.inputdialog.DialogOutcome;
import io.jmix.flowui.backgroundtask.BackgroundTask;
import io.jmix.flowui.backgroundtask.TaskLifeCycle;
import io.jmix.flowui.component.UiComponentUtils;
import io.jmix.flowui.component.checkbox.JmixCheckbox;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.kit.action.ActionPerformedEvent;
import io.jmix.flowui.model.CollectionLoader;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import static io.jmix.flowui.app.inputdialog.InputParameter.localDateParameter;

@Route(value = "tituloPagars", layout = MainView.class)
@ViewController(id = "TituloPagar.list")
@ViewDescriptor(path = "titulo-pagar-list-view.xml")
@LookupComponent("tituloPagarsDataGrid")
@DialogMode(width = "64em")
public class TituloPagarListView extends StandardListView<TituloPagar> {

    @ViewComponent
    private CollectionLoader<TituloPagar> tituloPagarsDl;
    @Autowired
    private UtilGeralService utilGeralService;
    @Autowired
    private UiComponents uiComponents;
    @Autowired
    private Dialogs dialogs;
    @Autowired
    private DataManager dataManager;
    @Autowired
    private MenuBean menuBean;
    @Autowired
    private TituloPagarService tituloPagarService;
    @ViewComponent
    private DataGrid<TituloPagar> tituloPagarsDataGrid;
    @ViewComponent
    private HorizontalLayout buttonsPanel;

    @Subscribe
    public void onBeforeShow(final BeforeShowEvent event) {
        ConfigRel configRel = utilGeralService.prepararConfigRel();
        LocalDate dataInicial = Optional.ofNullable(configRel.getDataEmissaoPagarInicial()).orElse(LocalDate.now());
        LocalDate dataFinal = Optional.ofNullable(configRel.getDataEmissaoPagarFinal()).orElse(LocalDate.now());
        tituloPagarsDl.setParameter("dataEmissaoInicial", dataInicial);
        tituloPagarsDl.setParameter("dataEmissaoFinal", dataFinal);
        tituloPagarsDl.setParameter("codEmpresa", utilGeralService.getCodEmpresa());
        tituloPagarsDl.load();

        Dialog dialog = UiComponentUtils.findDialog(this);
        buttonsPanel.setVisible(dialog == null);
    }

    @Override
    public String getPageTitle() {
        ConfigRel configRel = utilGeralService.prepararConfigRel();
        LocalDate dataInicial = Optional.ofNullable(configRel.getDataEmissaoPagarInicial()).orElse(LocalDate.now());
        LocalDate dataFinal = Optional.ofNullable(configRel.getDataEmissaoPagarFinal()).orElse(LocalDate.now());
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        return super.getPageTitle() + " emissão: " + dataInicial.format(formatter) + " a " + dataFinal.format(formatter);
    }

    @Supply(to = "tituloPagarsDataGrid.aberto", subject = "renderer")
    private Renderer<TituloPagar> tituloPagarsDataGridAbertoRenderer() {
        return checkboxRenderer(TituloPagar::getAberto);
    }

    @Supply(to = "tituloPagarsDataGrid.contabilizadoEmissao", subject = "renderer")
    private Renderer<TituloPagar> tituloPagarsDataGridContabilizadoEmissaoRenderer() {
        return checkboxRenderer(TituloPagar::getContabilizadoEmissao);
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
                .withHeader("Títulos a pagar")
                .withParameters(
                        localDateParameter("dataEmissaoInicial")
                                .withLabel("Data emissão inicial")
                                .withDefaultValue(configRel.getDataEmissaoPagarInicial()),
                        localDateParameter("dataEmissaoFinal")
                                .withLabel("Data emissão final")
                                .withDefaultValue(configRel.getDataEmissaoPagarFinal())
                )
                .withActions(DialogActions.OK_CANCEL)
                .withCloseListener(closeEvent -> {
                    if (closeEvent.closedWith(DialogOutcome.OK)) {
                        SaveContext saveContext = new SaveContext();
                        configRel.setDataEmissaoPagarInicial(closeEvent.getValue("dataEmissaoInicial"));
                        configRel.setDataEmissaoPagarFinal(closeEvent.getValue("dataEmissaoFinal"));
                        saveContext.saving(configRel);
                        dataManager.save(saveContext);
                        tituloPagarsDl.setParameter("dataEmissaoInicial", configRel.getDataEmissaoPagarInicial());
                        tituloPagarsDl.setParameter("dataEmissaoFinal", configRel.getDataEmissaoPagarFinal());
                        tituloPagarsDl.setParameter("codEmpresa", utilGeralService.getCodEmpresa());
                        tituloPagarsDl.load();
                        // getPageTitle() não é reconsultado automaticamente pelo Vaadin fora
                        // de navegação — atualiza o título da aba in-place, sem navigate()
                        // (que reinstancia a view e perderia seleção/filtro/ordenação da grid).
                        UI.getCurrent().getPage().setTitle(getPageTitle());
                    }
                })
                .open();
    }

    @Subscribe("tituloPagarsDataGrid.lancamentoAction")
    public void onTituloPagarsDataGridLancamentoAction(final ActionPerformedEvent event) {
        Set<TituloPagar> selecionados = tituloPagarsDataGrid.getSelectedItems();
        List<TituloPagar> titulosPagar = new ArrayList<>(selecionados);
        titulosPagar.removeIf(TituloPagar::getContabilizadoEmissao);
        titulosPagar.removeIf(tituloPagar -> tituloPagar.getContaContabil() == null);
        if (titulosPagar.isEmpty()) {
            dialogs.createMessageDialog()
                    .withHeader("Lançamentos contábeis")
                    .withText("Nenhum título para ser lançado")
                    .open();
            return;
        }

        titulosPagar.sort(Comparator.comparing(TituloPagar::getNumero));

        String mensagem = "Confirma geração do(s) lançamento(s) do(s) " + titulosPagar.size() + " título(s) selecionado(s)?";
        dialogs.createOptionDialog()
                .withHeader("Lançamentos contábeis")
                .withText(mensagem)
                .withActions(
                        new DialogAction(DialogAction.Type.YES)
                                .withHandler(e -> dialogs.createBackgroundTaskDialog(new LancamentoEmissaoTask(20, this, titulosPagar))
                                        .withHeader("Lançamento de entradas a pagar")
                                        .withText("Gerando lançamentos...")
                                        .withTotal(titulosPagar.size())
                                        .withCancelAllowed(true)
                                        .open()),
                        new DialogAction(DialogAction.Type.NO)
                )
                .open();
    }

    protected class LancamentoEmissaoTask extends BackgroundTask<Integer, Void> {
        private final List<TituloPagar> titulosPagar;

        public LancamentoEmissaoTask(long timeoutSeconds, View<?> view, List<TituloPagar> titulosPagar) {
            super(timeoutSeconds, view);
            this.titulosPagar = titulosPagar;
        }

        @Override
        public Void run(TaskLifeCycle<Integer> taskLifeCycle) throws Exception {
            int i = 1;
            for (TituloPagar tituloPagar : titulosPagar) {
                tituloPagarService.lancamentosEmissao(tituloPagar);
                taskLifeCycle.publish(i++);
            }
            return null;
        }

        @Override
        public void done(Void result) {
            super.done(result);
            tituloPagarsDl.load();
            dialogs.createMessageDialog()
                    .withHeader("Lançamentos contábeis")
                    .withText(titulosPagar.size() + " título(s) lançado(s)")
                    .open();
        }
    }

    @Subscribe("relatoriosSwitcher.emissaoItem.emissaoAction")
    public void onRelatoriosSwitcherEmissaoAction(final ActionPerformedEvent event) {
        menuBean.listarEntradaTitulosPagar();
    }

    @Subscribe("relatoriosSwitcher.vencimentoItem.vencimentoAction")
    public void onRelatoriosSwitcherVencimentoAction(final ActionPerformedEvent event) {
        menuBean.tituloPagarVencimento();
    }

}
