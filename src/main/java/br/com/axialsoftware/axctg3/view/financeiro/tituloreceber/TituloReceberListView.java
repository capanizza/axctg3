package br.com.axialsoftware.axctg3.view.financeiro.tituloreceber;

import br.com.axialsoftware.axctg3.bean.MenuBean;
import br.com.axialsoftware.axctg3.entity.cadastros.ConfigRel;
import br.com.axialsoftware.axctg3.entity.financeiro.Banco;
import br.com.axialsoftware.axctg3.entity.financeiro.RemessaBanco;
import br.com.axialsoftware.axctg3.entity.financeiro.RetornoBanco;
import br.com.axialsoftware.axctg3.entity.financeiro.TituloReceber;
import br.com.axialsoftware.axctg3.service.UtilGeralService;
import br.com.axialsoftware.axctg3.service.financeiro.RemessaBancoService;
import br.com.axialsoftware.axctg3.service.financeiro.RetornoBancoService;
import br.com.axialsoftware.axctg3.view.main.MainView;
import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.data.renderer.Renderer;
import com.vaadin.flow.router.Route;
import io.jmix.core.DataManager;
import io.jmix.core.SaveContext;
import io.jmix.flowui.Dialogs;
import io.jmix.flowui.DialogWindows;
import io.jmix.flowui.UiComponents;
import io.jmix.flowui.action.DialogAction;
import io.jmix.flowui.app.inputdialog.DialogActions;
import io.jmix.flowui.app.inputdialog.DialogOutcome;
import io.jmix.flowui.component.UiComponentUtils;
import io.jmix.flowui.component.checkbox.JmixCheckbox;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.kit.action.ActionPerformedEvent;
import io.jmix.flowui.kit.component.button.JmixButton;
import io.jmix.flowui.model.CollectionLoader;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import static io.jmix.flowui.app.inputdialog.InputParameter.localDateParameter;

/**
 * Só a listagem/edição de emissão. Não tem botão "Lançamentos" — o lançamento contábil
 * de emissão de {@link TituloReceber} depende de campos fiscais de NotaSaida que ainda
 * não existem (ver Javadoc da entidade); a baixa é lançada em BaixaTituloReceber.list.
 */
@Route(value = "tituloRecebers", layout = MainView.class)
@ViewController(id = "TituloReceber.list")
@ViewDescriptor(path = "titulo-receber-list-view.xml")
@LookupComponent("tituloRecebersDataGrid")
@DialogMode(width = "64em")
public class TituloReceberListView extends StandardListView<TituloReceber> {

    @ViewComponent
    private CollectionLoader<TituloReceber> tituloRecebersDl;
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
    private DialogWindows dialogWindows;
    @Autowired
    private RemessaBancoService remessaBancoService;
    @Autowired
    private RetornoBancoService retornoBancoService;
    @ViewComponent
    private HorizontalLayout buttonsPanel;
    @ViewComponent
    private DataGrid<TituloReceber> tituloRecebersDataGrid;

    // Cache do intervalo em uso, pra getPageTitle() não precisar reconsultar o
    // ConfigRel (prepararConfigRel() não é memoizado) a cada chamada do Vaadin.
    private LocalDate dataEmissaoInicial = LocalDate.now();
    private LocalDate dataEmissaoFinal = LocalDate.now();

    @Subscribe
    public void onBeforeShow(final BeforeShowEvent event) {
        ConfigRel configRel = utilGeralService.prepararConfigRel();
        dataEmissaoInicial = Optional.ofNullable(configRel.getDataEmissaoReceberInicial()).orElse(LocalDate.now());
        dataEmissaoFinal = Optional.ofNullable(configRel.getDataEmissaoReceberFinal()).orElse(LocalDate.now());
        tituloRecebersDl.setParameter("dataEmissaoInicial", dataEmissaoInicial);
        tituloRecebersDl.setParameter("dataEmissaoFinal", dataEmissaoFinal);
        tituloRecebersDl.setParameter("codEmpresa", utilGeralService.getCodEmpresa());
        tituloRecebersDl.load();

        Dialog dialog = UiComponentUtils.findDialog(this);
        buttonsPanel.setVisible(dialog == null);
    }

    @Override
    public String getPageTitle() {
        return super.getPageTitle() + utilGeralService.formatIntervaloTitulo("emissão", dataEmissaoInicial, dataEmissaoFinal);
    }

    @Supply(to = "tituloRecebersDataGrid.aberto", subject = "renderer")
    private Renderer<TituloReceber> tituloRecebersDataGridAbertoRenderer() {
        return checkboxRenderer(TituloReceber::getAberto);
    }

    @Supply(to = "tituloRecebersDataGrid.contabilizadoEmissao", subject = "renderer")
    private Renderer<TituloReceber> tituloRecebersDataGridContabilizadoEmissaoRenderer() {
        return checkboxRenderer(TituloReceber::getContabilizadoEmissao);
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
                .withHeader("Títulos a receber")
                .withParameters(
                        localDateParameter("dataEmissaoInicial")
                                .withLabel("Data emissão inicial")
                                .withDefaultValue(configRel.getDataEmissaoReceberInicial()),
                        localDateParameter("dataEmissaoFinal")
                                .withLabel("Data emissão final")
                                .withDefaultValue(configRel.getDataEmissaoReceberFinal())
                )
                .withActions(DialogActions.OK_CANCEL)
                .withCloseListener(closeEvent -> {
                    if (closeEvent.closedWith(DialogOutcome.OK)) {
                        SaveContext saveContext = new SaveContext();
                        configRel.setDataEmissaoReceberInicial(closeEvent.getValue("dataEmissaoInicial"));
                        configRel.setDataEmissaoReceberFinal(closeEvent.getValue("dataEmissaoFinal"));
                        saveContext.saving(configRel);
                        dataManager.save(saveContext);
                        dataEmissaoInicial = Optional.ofNullable(configRel.getDataEmissaoReceberInicial()).orElse(LocalDate.now());
                        dataEmissaoFinal = Optional.ofNullable(configRel.getDataEmissaoReceberFinal()).orElse(LocalDate.now());
                        tituloRecebersDl.setParameter("dataEmissaoInicial", dataEmissaoInicial);
                        tituloRecebersDl.setParameter("dataEmissaoFinal", dataEmissaoFinal);
                        tituloRecebersDl.setParameter("codEmpresa", utilGeralService.getCodEmpresa());
                        tituloRecebersDl.load();
                        // getPageTitle() não é reconsultado automaticamente pelo Vaadin fora
                        // de navegação — atualiza o título da aba in-place, sem navigate()
                        // (que reinstancia a view e perderia seleção/filtro/ordenação da grid).
                        UI.getCurrent().getPage().setTitle(getPageTitle());
                    }
                })
                .open();
    }

    @Subscribe("relatoriosSwitcher.emissaoItem.emissaoAction")
    public void onRelatoriosSwitcherEmissaoAction(final ActionPerformedEvent event) {
        menuBean.listarEntradaTitulosReceber();
    }

    @Subscribe("relatoriosSwitcher.vencimentoItem.vencimentoAction")
    public void onRelatoriosSwitcherVencimentoAction(final ActionPerformedEvent event) {
        menuBean.tituloReceberVencimento();
    }

    @Subscribe("tituloRecebersDataGrid.gerarRemessaAction")
    public void onTituloRecebersDataGridGerarRemessaAction(final ActionPerformedEvent event) {
        Set<TituloReceber> selecionados = tituloRecebersDataGrid.getSelectedItems();
        List<TituloReceber> titulos = new ArrayList<>(selecionados);
        if (titulos.isEmpty()) {
            dialogs.createMessageDialog()
                    .withHeader("Remessa bancária")
                    .withText("Nenhum título selecionado")
                    .open();
            return;
        }

        dialogs.createOptionDialog()
                .withHeader("Remessa bancária")
                .withText("Confirma a geração da remessa com " + titulos.size() + " título(s) selecionado(s)?")
                .withActions(
                        new DialogAction(DialogAction.Type.YES)
                                .withHandler(e -> {
                                    try {
                                        RemessaBanco remessaBanco = remessaBancoService.gerarRemessa(titulos);
                                        tituloRecebersDl.load();
                                        dialogs.createMessageDialog()
                                                .withHeader("Remessa bancária")
                                                .withText("Remessa nº " + remessaBanco.getNumRemessa() + " gerada com "
                                                        + remessaBanco.getQuantidadeTitulos() + " título(s)")
                                                .open();
                                    } catch (IllegalArgumentException ex) {
                                        dialogs.createMessageDialog()
                                                .withHeader("Remessa bancária")
                                                .withText(ex.getMessage())
                                                .open();
                                    }
                                }),
                        new DialogAction(DialogAction.Type.NO)
                )
                .open();
    }

    @Subscribe(id = "lerRetornoButton", subject = "clickListener")
    public void onLerRetornoButtonClick(final ClickEvent<JmixButton> event) {
        dialogWindows.view(this, RetornoBancoImportView.class)
                .withAfterCloseListener(closeEvent -> {
                    if (!closeEvent.closedWith(StandardOutcome.SAVE)) {
                        return;
                    }
                    RetornoBancoImportView view = closeEvent.getView();
                    Banco banco = view.getBanco();
                    byte[] arquivo = view.getArquivo();
                    String nomeArquivo = view.getNomeArquivo();
                    try {
                        RetornoBanco retornoBanco = retornoBancoService.processarRetorno(banco, arquivo, nomeArquivo);
                        tituloRecebersDl.load();
                        dialogs.createMessageDialog()
                                .withHeader("Retorno bancário")
                                .withText(retornoBanco.getQuantidadeConfirmados() + " confirmado(s), "
                                        + retornoBanco.getQuantidadeBaixados() + " baixado(s), "
                                        + retornoBanco.getQuantidadeRejeitados() + " rejeitado(s), "
                                        + retornoBanco.getQuantidadeNaoEncontrados() + " não encontrado(s)")
                                .open();
                    } catch (IllegalArgumentException ex) {
                        dialogs.createMessageDialog()
                                .withHeader("Retorno bancário")
                                .withText(ex.getMessage())
                                .open();
                    }
                })
                .open();
    }

    @Subscribe("tituloRecebersDataGrid.verRemessasAction")
    public void onTituloRecebersDataGridVerRemessasAction(final ActionPerformedEvent event) {
        dialogWindows.view(this, RemessaBancoListView.class).open();
    }

    @Subscribe("tituloRecebersDataGrid.verRetornosAction")
    public void onTituloRecebersDataGridVerRetornosAction(final ActionPerformedEvent event) {
        dialogWindows.view(this, RetornoBancoListView.class).open();
    }

}
