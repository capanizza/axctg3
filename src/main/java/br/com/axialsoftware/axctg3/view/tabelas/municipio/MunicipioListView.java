package br.com.axialsoftware.axctg3.view.tabelas.municipio;

import br.com.axialsoftware.axctg3.entity.tabelas.Municipio;

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
import io.jmix.flowui.component.textarea.JmixTextArea;
import io.jmix.flowui.component.textfield.JmixIntegerField;
import io.jmix.flowui.component.textfield.TypedTextField;
import io.jmix.flowui.kit.action.ActionPerformedEvent;
import io.jmix.flowui.kit.component.button.JmixButton;
import io.jmix.flowui.model.CollectionLoader;
import io.jmix.flowui.view.*;


import org.springframework.beans.factory.annotation.Autowired;

@Route(value = "municipios", layout = MainView.class)
@ViewController(id = "Municipio.list")
@ViewDescriptor(path = "municipio-list-view.xml")
@LookupComponent("municipiosDataGrid")
@DialogMode(width = "64em")
public class MunicipioListView extends StandardListView<Municipio> {
    @ViewComponent
    private MessageBundle messageBundle;
    @ViewComponent
    private SidePanelLayout sidePanelLayout;
    @ViewComponent
    private CollectionLoader<Municipio> municipiosDl;
    @ViewComponent
    private DataGrid<Municipio> municipiosDataGrid;
    @Autowired
    private DataManager dataManager;
    @ViewComponent
    private JmixIntegerField codigoField;
    @ViewComponent
    private JmixTextArea nomeField;
    @ViewComponent
    private TypedTextField<Object> ufField;
    @Autowired
    private Dialogs dialogs;

    private Municipio editedMunicipio;
    @ViewComponent
    private HorizontalLayout buttonsPanel;

    @Subscribe
    public void onBeforeShow(final BeforeShowEvent event) {
        Dialog dialog = UiComponentUtils.findDialog(this);
        buttonsPanel.setVisible(dialog == null);
    }

    @Subscribe("municipiosDataGrid.createAction")
    public void onMunicipiosDataGridCreateAction(final ActionPerformedEvent event) {
        editedMunicipio = dataManager.create(Municipio.class);
        codigoField.clear();
        nomeField.setValue(null);
        ufField.setTypedValue(null);
        sidePanelLayout.openSidePanel();
        codigoField.focus();
    }

    @Subscribe("municipiosDataGrid.editAction")
    public void onMunicipioDataGridEdit(final ActionPerformedEvent event) {
        Municipio selected = municipiosDataGrid.getSingleSelectedItem();
        if (selected == null) {
            dialogs.createMessageDialog()
                    .withHeader(messageBundle.getMessage("municipioListView.naoSelecionado.header"))
                    .withText(messageBundle.getMessage("municipioListView.naoSelecionado.text"))
                    .open();
            return;
        }
        editedMunicipio = dataManager.load(Municipio.class)
                .id(selected.getId())
                .one();
        codigoField.setValue(editedMunicipio.getCodigo());
        nomeField.setValue(editedMunicipio.getNome());
        ufField.setTypedValue(editedMunicipio.getUf());
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
        if (editedMunicipio == null) {
            sidePanelLayout.closeSidePanel();
            return;
        }
        editedMunicipio.setCodigo(codigoField.getValue());
        editedMunicipio.setNome(nomeField.getValue());
        editedMunicipio.setUf(ufField.getValue());
        dataManager.saveWithoutReload(editedMunicipio);
        municipiosDl.load();
        sidePanelLayout.closeSidePanel();
    }
}