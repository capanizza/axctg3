package br.com.axialsoftware.axctg3.view.sequencia;

import br.com.axialsoftware.axctg3.entity.Sequencia;

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
import io.jmix.flowui.model.CollectionLoader;
import io.jmix.flowui.view.*;

import org.springframework.beans.factory.annotation.Autowired;

@Route(value = "sequencias", layout = MainView.class)
@ViewController(id = "Sequencia.list")
@ViewDescriptor(path = "sequencia-list-view.xml")
@LookupComponent("sequenciasDataGrid")
@DialogMode(width = "64em")
public class SequenciaListView extends StandardListView<Sequencia> {
    @ViewComponent
    private MessageBundle messageBundle;
    @Autowired
    private UtilGeralService utilGeralService;
    @ViewComponent
    private CollectionLoader<Sequencia> sequenciasDl;
    @ViewComponent
    private SidePanelLayout sidePanelLayout;
    @ViewComponent
    private DataGrid<Sequencia> sequenciasDataGrid;
    @Autowired
    private Dialogs dialogs;
    @ViewComponent
    private TypedTextField<Object> codigoField;
    @ViewComponent
    private JmixIntegerField valorField;
    @Autowired
    private DataManager dataManager;

    private Sequencia editedSequencia;
    @ViewComponent
    private HorizontalLayout buttonsPanel;

    @Subscribe
    public void onBeforeShow(final BeforeShowEvent event) {
        Integer codEmpresa = utilGeralService.getCodEmpresa();
        if (codEmpresa != null) {
            sequenciasDl.setParameter("codEmpresa", codEmpresa);
            sequenciasDl.load();
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
                .withHeader(messageBundle.getMessage("sequenciaListView.empresaNaoSelecionada.header"))
                .withText(messageBundle.getMessage("sequenciaListView.empresaNaoSelecionada.text"))
                .open();
        return false;
    }

    @Subscribe("sequenciasDataGrid.createAction")
    public void onSequenciasDataGridCreateAction(final ActionPerformedEvent event) {
        if (!empresaSelecionada()) {
            return;
        }
        editedSequencia = dataManager.create(Sequencia.class);
        editedSequencia.setCodEmpresa(utilGeralService.getCodEmpresa());
        codigoField.setTypedValue(null);
        valorField.setValue(null);
        sidePanelLayout.openSidePanel();
        codigoField.focus();
    }

    @Subscribe("sequenciasDataGrid.editAction")
    public void onSequenciasDataGridEditAction(final ActionPerformedEvent event) {
        Sequencia selected = sequenciasDataGrid.getSingleSelectedItem();
        if (selected == null) {
            dialogs.createMessageDialog()
                    .withHeader(messageBundle.getMessage("sequenciaListView.naoSelecionado.header"))
                    .withText(messageBundle.getMessage("sequenciaListView.naoSelecionado.text"))
                    .open();
            return;
        }
        editedSequencia = dataManager.load(Sequencia.class)
                .id(selected.getId())
                .one();
        codigoField.setTypedValue(editedSequencia.getCodigo());
        valorField.setValue(editedSequencia.getValor());
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
        if (editedSequencia == null) {
            sidePanelLayout.closeSidePanel();
            return;
        }
        editedSequencia.setCodigo(codigoField.getValue());
        editedSequencia.setValor(valorField.getValue());
        dataManager.saveWithoutReload(editedSequencia);
        sequenciasDl.load();
        sidePanelLayout.closeSidePanel();
    }
}
