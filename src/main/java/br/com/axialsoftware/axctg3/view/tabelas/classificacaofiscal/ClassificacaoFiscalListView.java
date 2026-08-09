package br.com.axialsoftware.axctg3.view.tabelas.classificacaofiscal;

import br.com.axialsoftware.axctg3.entity.tabelas.ClassificacaoFiscal;
import br.com.axialsoftware.axctg3.view.main.MainView;

import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.router.Route;
import io.jmix.core.DataManager;
import io.jmix.flowui.Dialogs;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.component.sidepanellayout.SidePanelLayout;
import io.jmix.flowui.component.textfield.JmixIntegerField;
import io.jmix.flowui.component.textfield.TypedTextField;
import io.jmix.flowui.kit.action.ActionPerformedEvent;
import io.jmix.flowui.kit.component.button.JmixButton;
import io.jmix.flowui.model.CollectionLoader;
import io.jmix.flowui.view.*;

import org.springframework.beans.factory.annotation.Autowired;

@Route(value = "classificacao-fiscals", layout = MainView.class)
@ViewController(id = "ClassificacaoFiscal.list")
@ViewDescriptor(path = "classificacao-fiscal-list-view.xml")
@LookupComponent("classificacaoFiscalsDataGrid")
@DialogMode(width = "64em")
public class ClassificacaoFiscalListView extends StandardListView<ClassificacaoFiscal> {

    @ViewComponent
    private MessageBundle messageBundle;
    @ViewComponent
    private CollectionLoader<ClassificacaoFiscal> classificacaoFiscalsDl;
    @ViewComponent
    private SidePanelLayout sidePanelLayout;
    @ViewComponent
    private DataGrid<ClassificacaoFiscal> classificacaoFiscalsDataGrid;
    @Autowired
    private DataManager dataManager;
    @Autowired
    private Dialogs dialogs;
    @ViewComponent
    private JmixIntegerField codigoField;
    @ViewComponent
    private TypedTextField<Object> codNcmField;
    @ViewComponent
    private TypedTextField<Object> descricaoField;

    private ClassificacaoFiscal editedClassificacaoFiscal;

    @Subscribe("classificacaoFiscalsDataGrid.createAction")
    public void onClassificacaoFiscalsDataGridCreateAction(final ActionPerformedEvent event) {
        editedClassificacaoFiscal = dataManager.create(ClassificacaoFiscal.class);
        codigoField.setValue(null);
        codNcmField.setTypedValue(null);
        descricaoField.setTypedValue(null);
        sidePanelLayout.openSidePanel();
        codigoField.focus();
    }

    @Subscribe("classificacaoFiscalsDataGrid.editAction")
    public void onClassificacaoFiscalsDataGridEditAction(final ActionPerformedEvent event) {
        ClassificacaoFiscal selected = classificacaoFiscalsDataGrid.getSingleSelectedItem();
        if (selected == null) {
            dialogs.createMessageDialog()
                    .withHeader(messageBundle.getMessage("classificacaoFiscalListView.naoSelecionado.header"))
                    .withText(messageBundle.getMessage("classificacaoFiscalListView.naoSelecionado.text"))
                    .open();
            return;
        }
        editedClassificacaoFiscal = dataManager.load(ClassificacaoFiscal.class)
                .id(selected.getId())
                .one();
        codigoField.setValue(editedClassificacaoFiscal.getCodigo());
        codNcmField.setTypedValue(editedClassificacaoFiscal.getCodNcm());
        descricaoField.setTypedValue(editedClassificacaoFiscal.getDescricao());
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
        if (editedClassificacaoFiscal == null) {
            sidePanelLayout.closeSidePanel();
            return;
        }
        editedClassificacaoFiscal.setCodigo(codigoField.getValue());
        editedClassificacaoFiscal.setCodNcm((String) codNcmField.getValue());
        editedClassificacaoFiscal.setDescricao((String) descricaoField.getValue());
        dataManager.saveWithoutReload(editedClassificacaoFiscal);
        classificacaoFiscalsDl.load();
        sidePanelLayout.closeSidePanel();
    }
}
