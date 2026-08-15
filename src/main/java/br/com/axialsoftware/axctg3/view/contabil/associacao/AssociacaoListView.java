package br.com.axialsoftware.axctg3.view.contabil.associacao;

import br.com.axialsoftware.axctg3.entity.contabil.ContaContabil;
import br.com.axialsoftware.axctg3.entity.tabelas.ContaReferencial;

import br.com.axialsoftware.axctg3.service.FormatacaoService;
import br.com.axialsoftware.axctg3.service.UtilGeralService;
import br.com.axialsoftware.axctg3.service.contabil.ContaContabilService;
import br.com.axialsoftware.axctg3.view.main.MainView;

import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.data.renderer.Renderer;
import com.vaadin.flow.data.renderer.TextRenderer;
import com.vaadin.flow.router.Route;
import io.jmix.core.DataManager;
import io.jmix.flowui.Dialogs;
import io.jmix.flowui.app.inputdialog.DialogActions;
import io.jmix.flowui.app.inputdialog.DialogOutcome;
import io.jmix.flowui.component.UiComponentUtils;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.component.sidepanellayout.SidePanelLayout;
import io.jmix.flowui.component.textfield.TypedTextField;
import io.jmix.flowui.kit.action.ActionPerformedEvent;
import io.jmix.flowui.kit.component.button.JmixButton;
import io.jmix.flowui.model.CollectionLoader;
import io.jmix.flowui.model.InstanceContainer;
import io.jmix.flowui.view.*;

import org.springframework.beans.factory.annotation.Autowired;

import static io.jmix.flowui.app.inputdialog.InputParameter.entityParameter;
import static io.jmix.flowui.app.inputdialog.InputParameter.stringParameter;

@Route(value = "associacao-contas-referenciais", layout = MainView.class)
@ViewController(id = "Associacao.list")
@ViewDescriptor(path = "associacao-list-view.xml")
@LookupComponent("contaContabilsDataGrid")
@DialogMode(width = "64em")
public class AssociacaoListView extends StandardListView<ContaContabil> {

    @ViewComponent
    private MessageBundle messageBundle;
    @Autowired
    private UtilGeralService utilGeralService;
    @Autowired
    private FormatacaoService formatacaoService;
    @ViewComponent
    private CollectionLoader<ContaContabil> contaContabilsDl;
    @ViewComponent
    private InstanceContainer<ContaContabil> editedContaContabilDc;
    @ViewComponent
    private HorizontalLayout buttonsPanel;
    @ViewComponent
    private SidePanelLayout sidePanelLayout;
    @ViewComponent
    private DataGrid<ContaContabil> contaContabilsDataGrid;
    @ViewComponent
    private TypedTextField<Object> codigoField;
    @ViewComponent
    private TypedTextField<Object> nomeField;
    @Autowired
    private Dialogs dialogs;
    @Autowired
    private DataManager dataManager;
    @Autowired
    private ContaContabilService contaContabilService;

    @Subscribe
    public void onBeforeShow(final BeforeShowEvent event) {
        Integer codEmpresa = utilGeralService.getCodEmpresa();
        if (codEmpresa != null) {
            contaContabilsDl.setParameter("codEmpresa", codEmpresa);
            contaContabilsDl.setParameter("ano", utilGeralService.getAnoContabil());
            contaContabilsDl.load();
        }

        Dialog dialog = UiComponentUtils.findDialog(this);
        buttonsPanel.setVisible(dialog == null);
    }

    private boolean empresaSelecionada() {
        if (utilGeralService.getCodEmpresa() != null) {
            return true;
        }
        dialogs.createMessageDialog()
                .withHeader(messageBundle.getMessage("associacaoListView.empresaNaoSelecionada.header"))
                .withText(messageBundle.getMessage("associacaoListView.empresaNaoSelecionada.text"))
                .open();
        return false;
    }

    @Supply(to = "contaContabilsDataGrid.codigo", subject = "renderer")
    private Renderer<ContaContabil> contaContabilsDataGridCodigoRenderer() {
        return new TextRenderer<>(contaContabil ->
                formatacaoService.formatacaoMascContabil(contaContabil.getCodigo()));
    }

    @Subscribe("contaContabilsDataGrid.associarAction")
    public void onContaContabilsDataGridAssociarAction(final ActionPerformedEvent event) {
        // abrir o sidePanelLayout, associar a contaReferencial da ContaContabil selecionada
        // e salvar quando for pressionado o botão saveAndClose
        if (!empresaSelecionada()) {
            return;
        }
        ContaContabil selected = contaContabilsDataGrid.getSingleSelectedItem();
        if (selected == null) {
            dialogs.createMessageDialog()
                    .withHeader(messageBundle.getMessage("associacaoListView.naoSelecionado.header"))
                    .withText(messageBundle.getMessage("associacaoListView.naoSelecionado.text"))
                    .open();
            return;
        }
        // "selected" já vem com contaReferencial do fetchPlan de contaContabilsDl
        editedContaContabilDc.setItem(selected);
        codigoField.setTypedValue(formatacaoService.formatacaoMascContabil(selected.getCodigo()));
        nomeField.setTypedValue(selected.getNome());
        sidePanelLayout.openSidePanel();
    }

    @Subscribe(id = "closeButton", subject = "clickListener")
    public void onCloseButtonClick(final ClickEvent<JmixButton> event) {
        sidePanelLayout.closeSidePanel();
    }

    @Subscribe(id = "closeBtn", subject = "clickListener")
    public void onCloseBtnClick(final ClickEvent<JmixButton> event) {
        sidePanelLayout.closeSidePanel();
    }

    @Subscribe(id = "saveAndCloseBtn", subject = "clickListener")
    public void onSaveAndCloseBtnClick(final ClickEvent<JmixButton> event) {
        // salvar a contaReferencial associada à ContaContabil atual e fechar o sidePanelLayout
        ContaContabil contaContabil = editedContaContabilDc.getItemOrNull();
        if (contaContabil == null) {
            sidePanelLayout.closeSidePanel();
            return;
        }
        dataManager.saveWithoutReload(contaContabil);
        contaContabilsDl.load();
        sidePanelLayout.closeSidePanel();
    }

    @Subscribe(id = "associarGrupoButton", subject = "clickListener")
    public void onAssociarGrupoButtonClick(final ClickEvent<JmixButton> event) {
        // associa uma contaReferencial a todas as contas contábeis analíticas cujo código
        // esteja entre codigoInicial e codigoFinal (inclusive)
        if (!empresaSelecionada()) {
            return;
        }
        dialogs.createInputDialog(this)
                .withHeader(messageBundle.getMessage("associacaoListView.grupoDialog.header"))
                .withParameters(
                        stringParameter("codigoInicial")
                                .withLabel(messageBundle.getMessage("associacaoListView.grupoDialog.codigoInicial"))
                                .withRequired(true),
                        stringParameter("codigoFinal")
                                .withLabel(messageBundle.getMessage("associacaoListView.grupoDialog.codigoFinal"))
                                .withRequired(true),
                        entityParameter("contaReferencial", ContaReferencial.class)
                                .withLabel(messageBundle.getMessage("associacaoListView.grupoDialog.contaReferencial"))
                                .withRequired(true)
                )
                .withActions(DialogActions.OK_CANCEL)
                .withCloseListener(closeEvent -> {
                    if (!closeEvent.closedWith(DialogOutcome.OK)) {
                        return;
                    }
                    String codigoInicial = closeEvent.getValue("codigoInicial");
                    String codigoFinal = closeEvent.getValue("codigoFinal");
                    ContaReferencial contaReferencial = closeEvent.getValue("contaReferencial");
                    int atualizadas = contaContabilService.associarContaReferencialEmGrupo(
                            codigoInicial, codigoFinal, contaReferencial);
                    contaContabilsDl.load();
                    dialogs.createMessageDialog()
                            .withHeader(messageBundle.getMessage("associacaoListView.grupoDialog.header"))
                            .withText(atualizadas == 0
                                    ? messageBundle.getMessage("associacaoListView.grupoDialog.nenhumaConta")
                                    : messageBundle.formatMessage("associacaoListView.grupoDialog.resultado", atualizadas))
                            .open();
                })
                .open();
    }
}
