package br.com.axialsoftware.axctg3.view.financeiro.movimentobanco;

import br.com.axialsoftware.axctg3.bean.MenuBean;
import br.com.axialsoftware.axctg3.entity.cadastros.ConfigRel;
import br.com.axialsoftware.axctg3.entity.financeiro.Banco;
import br.com.axialsoftware.axctg3.entity.financeiro.MovimentoBanco;
import br.com.axialsoftware.axctg3.service.UtilGeralService;
import br.com.axialsoftware.axctg3.service.financeiro.MovimentoBancoService;
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

import static io.jmix.flowui.app.inputdialog.InputParameter.entityParameter;
import static io.jmix.flowui.app.inputdialog.InputParameter.localDateParameter;

@Route(value = "movimentoBancoes", layout = MainView.class)
@ViewController(id = "MovimentoBanco.list")
@ViewDescriptor(path = "movimento-banco-list-view.xml")
@LookupComponent("movimentoBancoesDataGrid")
@DialogMode(width = "64em")
public class MovimentoBancoListView extends StandardListView<MovimentoBanco> {

    @ViewComponent
    private CollectionLoader<MovimentoBanco> movimentoBancoesDl;
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
    @ViewComponent
    private DataGrid<MovimentoBanco> movimentoBancoesDataGrid;
    @Autowired
    private MovimentoBancoService movimentoBancoService;
    @ViewComponent
    private HorizontalLayout buttonsPanel;

    @Subscribe
    public void onBeforeShow(final BeforeShowEvent event) {
        ConfigRel configRel = utilGeralService.prepararConfigRel();
        Integer banco = configRel.getBancoMovimentoBanco();
        String listarTodos = banco == null ? "sim" : "nao";
        LocalDate dataInicial = Optional.ofNullable(configRel.getDataMovimentoBancoInicial()).orElse(LocalDate.now());
        LocalDate dataFinal = Optional.ofNullable(configRel.getDataMovimentoBancoFinal()).orElse(LocalDate.now());
        movimentoBancoesDl.setParameter("dataInicial", dataInicial);
        movimentoBancoesDl.setParameter("dataFinal", dataFinal);
        movimentoBancoesDl.setParameter("banco", banco);
        movimentoBancoesDl.setParameter("codEmpresa", utilGeralService.getCodEmpresa());
        movimentoBancoesDl.setParameter("listarTodos", listarTodos);
        movimentoBancoesDl.setParameter("tipoSaldo", false);
        movimentoBancoesDl.load();

        Dialog dialog = UiComponentUtils.findDialog(this);
        buttonsPanel.setVisible(dialog == null);
    }

    @Supply(to = "movimentoBancoesDataGrid.entrada", subject = "renderer")
    private Renderer<MovimentoBanco> movimentoBancoesDataGridEntradaRenderer() {
        return checkboxRenderer(MovimentoBanco::getEntrada);
    }

    @Supply(to = "movimentoBancoesDataGrid.lancado", subject = "renderer")
    private Renderer<MovimentoBanco> movimentoBancoesDataGridLancadoRenderer() {
        return checkboxRenderer(MovimentoBanco::getLancado);
    }

    private Renderer<MovimentoBanco> checkboxRenderer(Function<MovimentoBanco, Boolean> valueGetter) {
        return new ComponentRenderer<>(movimentoBanco -> {
            JmixCheckbox checkbox = uiComponents.create(JmixCheckbox.class);
            checkbox.setValue(Boolean.TRUE.equals(valueGetter.apply(movimentoBanco)));
            checkbox.setReadOnly(true);
            checkbox.addClassName("grid-value-checkbox");
            return checkbox;
        });
    }

    @Subscribe("movimentoBancoesDataGrid.delimitarAction")
    public void onMovimentoBancoesDataGridDelimitarAction(final ActionPerformedEvent event) {
        ConfigRel configRel = utilGeralService.prepararConfigRel();
        Integer codigoAtual = configRel.getBancoMovimentoBanco();
        Banco bancoAtual = codigoAtual == null ? null : dataManager.load(Banco.class)
                .query("select e from Banco e where e.codigo = :codigo and e.codEmpresa = :codEmpresa")
                .parameter("codigo", codigoAtual)
                .parameter("codEmpresa", utilGeralService.getCodEmpresa())
                .optional()
                .orElse(null);
        dialogs.createInputDialog(UiComponentUtils.getCurrentView())
                .withHeader("Movimento bancário")
                .withParameters(
                        localDateParameter("dataInicial")
                                .withLabel("Data inicial")
                                .withDefaultValue(configRel.getDataMovimentoBancoInicial()),
                        localDateParameter("dataFinal")
                                .withLabel("Data final")
                                .withDefaultValue(configRel.getDataMovimentoBancoFinal()),
                        entityParameter("banco", Banco.class)
                                .withLabel("Banco (em branco = todos)")
                                .withDefaultValue(bancoAtual)
                )
                .withActions(DialogActions.OK_CANCEL)
                .withCloseListener(closeEvent -> {
                    if (closeEvent.closedWith(DialogOutcome.OK)) {
                        SaveContext saveContext = new SaveContext();
                        configRel.setDataMovimentoBancoInicial(closeEvent.getValue("dataInicial"));
                        configRel.setDataMovimentoBancoFinal(closeEvent.getValue("dataFinal"));
                        Banco banco = closeEvent.getValue("banco");
                        configRel.setBancoMovimentoBanco(banco == null ? null : banco.getCodigo());
                        saveContext.saving(configRel);
                        dataManager.save(saveContext);
                        movimentoBancoesDl.setParameter("dataInicial", configRel.getDataMovimentoBancoInicial());
                        movimentoBancoesDl.setParameter("dataFinal", configRel.getDataMovimentoBancoFinal());
                        movimentoBancoesDl.setParameter("banco", configRel.getBancoMovimentoBanco());
                        movimentoBancoesDl.setParameter("listarTodos", banco == null ? "sim" : "nao");
                        movimentoBancoesDl.setParameter("codEmpresa", utilGeralService.getCodEmpresa());
                        movimentoBancoesDl.load();
                    }
                })
                .open();
    }

    @Subscribe("listagemSwitcher.listagemItem.listagemAction")
    public void onListagemSwitcherListagemAction(final ActionPerformedEvent event) {
        menuBean.listarMovimentoBanco(1);
    }

    @Subscribe("listagemSwitcher.financeiroItem.financeiroAction")
    public void onListagemSwitcherFinanceiroAction(final ActionPerformedEvent event) {
        menuBean.listarMovimentoBanco(2);
    }

    @Subscribe("movimentoBancoesDataGrid.lancamentoAction")
    public void onMovimentoBancoesDataGridLancamentoAction(final ActionPerformedEvent event) {
        Set<MovimentoBanco> selecionados = movimentoBancoesDataGrid.getSelectedItems();
        List<MovimentoBanco> movimentosBanco = new ArrayList<>(selecionados);
        movimentosBanco.removeIf(movimentoBanco -> Boolean.TRUE.equals(movimentoBanco.getLancado()));
        if (movimentosBanco.isEmpty()) {
            dialogs.createMessageDialog()
                    .withHeader("Lançamentos contábeis")
                    .withText("Nenhum movimento para ser lançado")
                    .open();
            return;
        }

        movimentosBanco.sort(Comparator.comparing(MovimentoBanco::getLancamento));

        String mensagem = "Confirma geração do(s) lançamento(s) do(s) " + movimentosBanco.size() + " movimento(s) selecionado(s)?";
        dialogs.createOptionDialog()
                .withHeader("Lançamentos contábeis")
                .withText(mensagem)
                .withActions(
                        new DialogAction(DialogAction.Type.YES)
                                .withHandler(e -> dialogs.createBackgroundTaskDialog(new LancamentoTask(20, this, movimentosBanco))
                                        .withHeader("Lançamento de movimentos de banco")
                                        .withText("Gerando lançamentos...")
                                        .withTotal(movimentosBanco.size())
                                        .withCancelAllowed(true)
                                        .open()),
                        new DialogAction(DialogAction.Type.NO)
                )
                .open();
    }

    protected class LancamentoTask extends BackgroundTask<Integer, Void> {
        private final List<MovimentoBanco> movimentosBanco;

        public LancamentoTask(long timeoutSeconds, View<?> view, List<MovimentoBanco> movimentosBanco) {
            super(timeoutSeconds, view);
            this.movimentosBanco = movimentosBanco;
        }

        @Override
        public Void run(TaskLifeCycle<Integer> taskLifeCycle) throws Exception {
            int i = 1;
            for (MovimentoBanco movimentoBanco : movimentosBanco) {
                movimentoBancoService.lancamentos(movimentoBanco);
                taskLifeCycle.publish(i++);
            }
            return null;
        }

        @Override
        public void done(Void result) {
            super.done(result);
            movimentoBancoesDl.load();
            dialogs.createMessageDialog()
                    .withHeader("Movimentos de banco")
                    .withText(movimentosBanco.size() + " movimento(s) lançado(s)")
                    .open();
        }
    }

}
