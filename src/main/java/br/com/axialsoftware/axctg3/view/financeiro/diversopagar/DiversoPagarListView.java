package br.com.axialsoftware.axctg3.view.financeiro.diversopagar;

import br.com.axialsoftware.axctg3.bean.MenuBean;
import br.com.axialsoftware.axctg3.entity.cadastros.ConfigRel;
import br.com.axialsoftware.axctg3.entity.financeiro.DiversoPagar;
import br.com.axialsoftware.axctg3.service.UtilGeralService;
import br.com.axialsoftware.axctg3.service.financeiro.DiversoPagarService;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import static io.jmix.flowui.app.inputdialog.InputParameter.localDateParameter;

@Route(value = "diversoPagars", layout = MainView.class)
@ViewController(id = "DiversoPagar.list")
@ViewDescriptor(path = "diverso-pagar-list-view.xml")
@LookupComponent("diversoPagarsDataGrid")
@DialogMode(width = "64em")
public class DiversoPagarListView extends StandardListView<DiversoPagar> {

    @ViewComponent
    private CollectionLoader<DiversoPagar> diversoPagarsDl;
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
    private DiversoPagarService diversoPagarService;
    @ViewComponent
    private DataGrid<DiversoPagar> diversoPagarsDataGrid;
    @ViewComponent
    private HorizontalLayout buttonsPanel;

    // Cache do intervalo em uso, pra getPageTitle() não precisar reconsultar o
    // ConfigRel (prepararConfigRel() não é memoizado) a cada chamada do Vaadin.
    private LocalDate dataEmissaoInicial = LocalDate.now();
    private LocalDate dataEmissaoFinal = LocalDate.now();

    @Subscribe
    public void onBeforeShow(final BeforeShowEvent event) {
        ConfigRel configRel = utilGeralService.prepararConfigRel();
        dataEmissaoInicial = Optional.ofNullable(configRel.getDataEmissaoDiversoInicial()).orElse(LocalDate.now());
        dataEmissaoFinal = Optional.ofNullable(configRel.getDataEmissaoDiversoFinal()).orElse(LocalDate.now());
        diversoPagarsDl.setParameter("dataEmissaoInicial", dataEmissaoInicial);
        diversoPagarsDl.setParameter("dataEmissaoFinal", dataEmissaoFinal);
        diversoPagarsDl.setParameter("codEmpresa", utilGeralService.getCodEmpresa());
        diversoPagarsDl.load();

        Dialog dialog = UiComponentUtils.findDialog(this);
        buttonsPanel.setVisible(dialog == null);
    }

    @Override
    public String getPageTitle() {
        return super.getPageTitle() + utilGeralService.formatIntervaloTitulo("emissão", dataEmissaoInicial, dataEmissaoFinal);
    }

    @Supply(to = "diversoPagarsDataGrid.aberto", subject = "renderer")
    private Renderer<DiversoPagar> diversoPagarsDataGridAbertoRenderer() {
        return checkboxRenderer(DiversoPagar::getAberto);
    }

    @Supply(to = "diversoPagarsDataGrid.contabilizadoEmissao", subject = "renderer")
    private Renderer<DiversoPagar> diversoPagarsDataGridContabilizadoEmissaoRenderer() {
        return checkboxRenderer(DiversoPagar::getContabilizadoEmissao);
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
                .withHeader("Diversos a pagar")
                .withParameters(
                        localDateParameter("dataEmissaoInicial")
                                .withLabel("Data emissão inicial")
                                .withDefaultValue(configRel.getDataEmissaoDiversoInicial()),
                        localDateParameter("dataEmissaoFinal")
                                .withLabel("Data emissão final")
                                .withDefaultValue(configRel.getDataEmissaoDiversoFinal())
                )
                .withActions(DialogActions.OK_CANCEL)
                .withCloseListener(closeEvent -> {
                    if (closeEvent.closedWith(DialogOutcome.OK)) {
                        SaveContext saveContext = new SaveContext();
                        configRel.setDataEmissaoDiversoInicial(closeEvent.getValue("dataEmissaoInicial"));
                        configRel.setDataEmissaoDiversoFinal(closeEvent.getValue("dataEmissaoFinal"));
                        saveContext.saving(configRel);
                        dataManager.save(saveContext);
                        dataEmissaoInicial = Optional.ofNullable(configRel.getDataEmissaoDiversoInicial()).orElse(LocalDate.now());
                        dataEmissaoFinal = Optional.ofNullable(configRel.getDataEmissaoDiversoFinal()).orElse(LocalDate.now());
                        diversoPagarsDl.setParameter("dataEmissaoInicial", dataEmissaoInicial);
                        diversoPagarsDl.setParameter("dataEmissaoFinal", dataEmissaoFinal);
                        diversoPagarsDl.setParameter("codEmpresa", utilGeralService.getCodEmpresa());
                        diversoPagarsDl.load();
                        // getPageTitle() não é reconsultado automaticamente pelo Vaadin fora
                        // de navegação — atualiza o título da aba in-place, sem navigate()
                        // (que reinstancia a view e perderia seleção/filtro/ordenação da grid).
                        UI.getCurrent().getPage().setTitle(getPageTitle());
                    }
                })
                .open();
    }

    @Subscribe("diversoPagarsDataGrid.listagemAction")
    public void onDiversoPagarsDataGridListagemAction(final ActionPerformedEvent event) {
        menuBean.listarEntradaDiversosPagar();
    }

    @Subscribe("diversoPagarsDataGrid.lancamentoAction")
    public void onDiversoPagarsDataGridLancamentoAction(final ActionPerformedEvent event) {
        Set<DiversoPagar> selecionados = diversoPagarsDataGrid.getSelectedItems();
        List<DiversoPagar> diversosPagar = new ArrayList<>(selecionados);
        diversosPagar.removeIf(DiversoPagar::getContabilizadoEmissao);
        diversosPagar.removeIf(diversoPagar -> diversoPagar.getContaContabil() == null);
        if (diversosPagar.isEmpty()) {
            dialogs.createMessageDialog()
                    .withHeader("Lançamentos contábeis")
                    .withText("Nenhum diverso para ser lançado")
                    .open();
            return;
        }

        diversosPagar.sort(Comparator.comparing(DiversoPagar::getNumero));

        String mensagem = "Confirma geração do(s) lançamento(s) do(s) " + diversosPagar.size() + " diverso(s) selecionado(s)?";
        dialogs.createOptionDialog()
                .withHeader("Lançamentos contábeis")
                .withText(mensagem)
                .withActions(
                        new DialogAction(DialogAction.Type.YES)
                                .withHandler(e -> dialogs.createBackgroundTaskDialog(new LancamentoEmissaoTask(20, this, diversosPagar))
                                        .withHeader("Lançamento de entradas de diversos a pagar")
                                        .withText("Gerando lançamentos...")
                                        .withTotal(diversosPagar.size())
                                        .withCancelAllowed(true)
                                        .open()),
                        new DialogAction(DialogAction.Type.NO)
                )
                .open();
    }

    protected class LancamentoEmissaoTask extends BackgroundTask<Integer, Void> {
        private final List<DiversoPagar> diversosPagar;

        public LancamentoEmissaoTask(long timeoutSeconds, View<?> view, List<DiversoPagar> diversosPagar) {
            super(timeoutSeconds, view);
            this.diversosPagar = diversosPagar;
        }

        @Override
        public Void run(TaskLifeCycle<Integer> taskLifeCycle) throws Exception {
            int i = 1;
            for (DiversoPagar diversoPagar : diversosPagar) {
                diversoPagarService.lancamentosEmissao(diversoPagar);
                taskLifeCycle.publish(i++);
            }
            return null;
        }

        @Override
        public void done(Void result) {
            super.done(result);
            diversoPagarsDl.load();
            dialogs.createMessageDialog()
                    .withHeader("Lançamentos contábeis")
                    .withText(diversosPagar.size() + " diverso(s) lançado(s)")
                    .open();
        }
    }

}
