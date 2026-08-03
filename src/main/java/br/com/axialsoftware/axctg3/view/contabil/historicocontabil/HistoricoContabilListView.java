package br.com.axialsoftware.axctg3.view.contabil.historicocontabil;

import br.com.axialsoftware.axctg3.entity.contabil.HistoricoContabil;

import br.com.axialsoftware.axctg3.service.UtilGeralService;
import br.com.axialsoftware.axctg3.view.main.MainView;

import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.router.Route;
import io.jmix.core.DataManager;
import io.jmix.flowui.Dialogs;
import io.jmix.flowui.component.UiComponentUtils;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.component.sidepanellayout.SidePanelLayout;
import io.jmix.flowui.component.textfield.JmixIntegerField;
import io.jmix.flowui.component.textfield.TypedTextField;
import io.jmix.flowui.kit.action.ActionPerformedEvent;
import io.jmix.flowui.kit.component.button.JmixButton;
import io.jmix.flowui.model.CollectionContainer;
import io.jmix.flowui.model.CollectionLoader;
import io.jmix.flowui.view.*;


import org.springframework.beans.factory.annotation.Autowired;



@Route(value = "historico-contabils", layout = MainView.class)
@ViewController(id = "HistoricoContabil.list")
@ViewDescriptor(path = "historico-contabil-list-view.xml")
@LookupComponent("historicoContabilsDataGrid")
@DialogMode(width = "64em")
public class HistoricoContabilListView extends StandardListView<HistoricoContabil> {
    @ViewComponent
    private MessageBundle messageBundle;
    @ViewComponent
    private CollectionLoader<HistoricoContabil> historicoContabilsDl;
    @ViewComponent
    private SidePanelLayout sidePanelLayout;
    @ViewComponent
    private DataGrid<HistoricoContabil> historicoContabilsDataGrid;
    @ViewComponent
    private CollectionContainer<HistoricoContabil> historicoContabilsDc;
    @Autowired
    private UtilGeralService utilGeralService;
    @Autowired
    private DataManager dataManager;
    @ViewComponent
    private JmixIntegerField codigoField;
    @ViewComponent
    private TypedTextField<Object> descricaoField;
    @Autowired
    private Dialogs dialogs;

    private HistoricoContabil editedHistoricoContabil;
    @ViewComponent
    private HorizontalLayout buttonsPanel;

    @Subscribe
    public void onBeforeShow(final BeforeShowEvent event) {
        Integer codEmpresa = utilGeralService.getCodEmpresa();
        if (codEmpresa != null) {
            historicoContabilsDl.setParameter("codEmpresa", codEmpresa);
            historicoContabilsDl.load();
        }

        Dialog dialog = UiComponentUtils.findDialog(this);
        buttonsPanel.setVisible(dialog == null);
    }

    @Subscribe
    public void onReady(final ReadyEvent event) {
        empresaSelecionada();
    }

    private boolean empresaSelecionada() {
        if (utilGeralService.getCodEmpresa() != null) {
            return true;
        }
        dialogs.createMessageDialog()
                .withHeader(messageBundle.getMessage("historicoContabilListView.empresaNaoSelecionada.header"))
                .withText(messageBundle.getMessage("historicoContabilListView.empresaNaoSelecionada.text"))
                .open();
        return false;
    }


    @Subscribe("historicoContabilsDataGrid.createAction")
    public void onHistoricoContabilsDataGridCreateAction(final ActionPerformedEvent event) {
        if (!empresaSelecionada()) {
            return;
        }
        editedHistoricoContabil = dataManager.create(HistoricoContabil.class);
        editedHistoricoContabil.setCodEmpresa(utilGeralService.getCodEmpresa());
        codigoField.clear();
        descricaoField.setTypedValue(null);
        sidePanelLayout.openSidePanel();
        codigoField.focus();
    }

    @Subscribe("historicoContabilsDataGrid.editAction")
    public void onHistoricoContabilsDataGridEditAction(final ActionPerformedEvent event) {
        HistoricoContabil selected = historicoContabilsDataGrid.getSingleSelectedItem();
        if (selected == null) {
            dialogs.createMessageDialog()
                    .withHeader(messageBundle.getMessage("historicoContabilListView.naoSelecionado.header"))
                    .withText(messageBundle.getMessage("historicoContabilListView.naoSelecionado.text"))
                    .open();
            return;
        }
        editedHistoricoContabil = dataManager.load(HistoricoContabil.class)
                .id(selected.getId())
                .one();
        codigoField.setValue(editedHistoricoContabil.getCodigo());
        descricaoField.setTypedValue(editedHistoricoContabil.getDescricao());
        sidePanelLayout.openSidePanel();
        codigoField.focus();
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
        // salvar o HistoricoContabil atual e fechar o sidePanelLayout}
        if (editedHistoricoContabil == null) {
            sidePanelLayout.closeSidePanel();
            return;
        }
        editedHistoricoContabil.setCodigo(codigoField.getValue());
        editedHistoricoContabil.setDescricao(descricaoField.getValue());
        dataManager.saveWithoutReload(editedHistoricoContabil);
        historicoContabilsDl.load();
        sidePanelLayout.closeSidePanel();
    }
}