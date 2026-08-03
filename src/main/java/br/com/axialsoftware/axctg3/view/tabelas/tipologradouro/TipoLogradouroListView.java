package br.com.axialsoftware.axctg3.view.tabelas.tipologradouro;

import br.com.axialsoftware.axctg3.entity.tabelas.TipoLogradouro;

import br.com.axialsoftware.axctg3.view.main.MainView;

import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.router.Route;
import io.jmix.core.DataManager;
import io.jmix.flowui.Dialogs;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.component.sidepanellayout.SidePanelLayout;
import io.jmix.flowui.component.textfield.TypedTextField;
import io.jmix.flowui.kit.action.ActionPerformedEvent;
import io.jmix.flowui.kit.component.button.JmixButton;
import io.jmix.flowui.model.CollectionContainer;
import io.jmix.flowui.model.CollectionLoader;
import io.jmix.flowui.view.*;


import org.springframework.beans.factory.annotation.Autowired;


@Route(value = "tipo-logradouroes", layout = MainView.class)
@ViewController(id = "TipoLogradouro.list")
@ViewDescriptor(path = "tipo-logradouro-list-view.xml")
@LookupComponent("tipoLogradouroesDataGrid")
@DialogMode(width = "64em")
public class TipoLogradouroListView extends StandardListView<TipoLogradouro> {
    @ViewComponent
    private MessageBundle messageBundle;
    @ViewComponent
    private CollectionLoader<TipoLogradouro> tipoLogradouroesDl;
    @ViewComponent
    private SidePanelLayout sidePanelLayout;
    @ViewComponent
    private DataGrid<TipoLogradouro> tipoLogradouroesDataGrid;
    @ViewComponent
    private CollectionContainer<TipoLogradouro> tipoLogradouroesDc;

    @Autowired
    private DataManager dataManager;
    @ViewComponent
    private TypedTextField<Object> codigoField;
    @ViewComponent
    private TypedTextField<Object> descricaoField;
    @Autowired
    private Dialogs dialogs;

    private TipoLogradouro editedTipoLogradouro;

    @Subscribe("tipoLogradouroesDataGrid.createAction")
    public void onTipoLogradouroesDataGridCreateAction(final ActionPerformedEvent event) {
        editedTipoLogradouro = dataManager.create(TipoLogradouro.class);
        codigoField.setTypedValue(null);
        descricaoField.setTypedValue(null);
        sidePanelLayout.openSidePanel();
        codigoField.focus();
    }

    @Subscribe("tipoLogradouroesDataGrid.editAction")
    public void onTipoLogradouroesDataGridEditAction(final ActionPerformedEvent event) {
        TipoLogradouro selected = tipoLogradouroesDataGrid.getSingleSelectedItem();
        if (selected == null) {
            dialogs.createMessageDialog()
                    .withHeader(messageBundle.getMessage("tipoLogradouroListView.naoSelecionado.header"))
                    .withText(messageBundle.getMessage("tipoLogradouroListView.naoSelecionado.text"))
                    .open();
            return;
        }
        editedTipoLogradouro = dataManager.load(TipoLogradouro.class)
                .id(selected.getId())
                .one();
        codigoField.setTypedValue(editedTipoLogradouro.getCodigo());
        descricaoField.setTypedValue(editedTipoLogradouro.getDescricao());
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
        if (editedTipoLogradouro == null) {
            sidePanelLayout.closeSidePanel();
            return;
        }
        editedTipoLogradouro.setCodigo(codigoField.getValue());
        editedTipoLogradouro.setDescricao(descricaoField.getValue());
        dataManager.saveWithoutReload(editedTipoLogradouro);
        tipoLogradouroesDl.load();
        sidePanelLayout.closeSidePanel();
    }

}