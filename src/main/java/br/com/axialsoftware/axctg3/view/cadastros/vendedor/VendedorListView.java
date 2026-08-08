package br.com.axialsoftware.axctg3.view.cadastros.vendedor;

import br.com.axialsoftware.axctg3.entity.cadastros.Vendedor;
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
import io.jmix.flowui.component.textfield.JmixBigDecimalField;
import io.jmix.flowui.component.textfield.JmixIntegerField;
import io.jmix.flowui.component.textfield.TypedTextField;
import io.jmix.flowui.kit.action.ActionPerformedEvent;
import io.jmix.flowui.kit.component.button.JmixButton;
import io.jmix.flowui.model.CollectionLoader;
import io.jmix.flowui.view.*;

import org.springframework.beans.factory.annotation.Autowired;

@Route(value = "vendedores", layout = MainView.class)
@ViewController(id = "Vendedor.list")
@ViewDescriptor(path = "vendedor-list-view.xml")
@LookupComponent("vendedoresDataGrid")
@DialogMode(width = "64em")
public class VendedorListView extends StandardListView<Vendedor> {

    @ViewComponent
    private MessageBundle messageBundle;
    @Autowired
    private UtilGeralService utilGeralService;
    @ViewComponent
    private CollectionLoader<Vendedor> vendedoresDl;
    @ViewComponent
    private HorizontalLayout buttonsPanel;
    @ViewComponent
    private SidePanelLayout sidePanelLayout;
    @ViewComponent
    private DataGrid<Vendedor> vendedoresDataGrid;
    @Autowired
    private Dialogs dialogs;
    @Autowired
    private DataManager dataManager;
    @ViewComponent
    private JmixIntegerField codigoField;
    @ViewComponent
    private TypedTextField<Object> nomeField;
    @ViewComponent
    private TypedTextField<Object> cnpjField;
    @ViewComponent
    private JmixBigDecimalField comissaoField;

    private Vendedor editedVendedor;

    @Subscribe
    public void onBeforeShow(final BeforeShowEvent event) {
        Integer codEmpresa = utilGeralService.getCodEmpresa();
        if (codEmpresa != null) {
            vendedoresDl.setParameter("codEmpresa", codEmpresa);
            vendedoresDl.load();
        }

        Dialog dialog = UiComponentUtils.findDialog(this);
        buttonsPanel.setVisible(dialog == null);
    }

    private boolean empresaSelecionada() {
        if (utilGeralService.getCodEmpresa() != null) {
            return true;
        }
        dialogs.createMessageDialog()
                .withHeader(messageBundle.getMessage("vendedorListView.empresaNaoSelecionada.header"))
                .withText(messageBundle.getMessage("vendedorListView.empresaNaoSelecionada.text"))
                .open();
        return false;
    }

    @Subscribe("vendedoresDataGrid.createAction")
    public void onVendedoresDataGridCreateAction(final ActionPerformedEvent event) {
        // abrir o sidePanelLayout, criar uma instância de Vendedor e salvar
        // quando for pressionado o botão saveAndClose
        if (!empresaSelecionada()) {
            return;
        }
        editedVendedor = dataManager.create(Vendedor.class);
        editedVendedor.setCodEmpresa(utilGeralService.getCodEmpresa());
        codigoField.setValue(null);
        nomeField.setTypedValue(null);
        cnpjField.setTypedValue(null);
        comissaoField.setValue(null);
        sidePanelLayout.openSidePanel();
        codigoField.focus();
    }

    @Subscribe("vendedoresDataGrid.editAction")
    public void onVendedoresDataGridEditAction(final ActionPerformedEvent event) {
        // abrir o sidePanelLayout, ler o Vendedor atual, alterar e salvar
        // quando for pressionado o botão saveAndClose
        Vendedor selected = vendedoresDataGrid.getSingleSelectedItem();
        if (selected == null) {
            dialogs.createMessageDialog()
                    .withHeader(messageBundle.getMessage("vendedorListView.naoSelecionado.header"))
                    .withText(messageBundle.getMessage("vendedorListView.naoSelecionado.text"))
                    .open();
            return;
        }
        editedVendedor = dataManager.load(Vendedor.class)
                .id(selected.getId())
                .one();
        codigoField.setValue(editedVendedor.getCodigo());
        nomeField.setTypedValue(editedVendedor.getNome());
        cnpjField.setTypedValue(editedVendedor.getCnpj());
        comissaoField.setValue(editedVendedor.getComissao());
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
        // salvar o Vendedor atual e fechar o sidePanelLayout
        if (editedVendedor == null) {
            sidePanelLayout.closeSidePanel();
            return;
        }
        editedVendedor.setCodigo(codigoField.getValue());
        editedVendedor.setNome((String) nomeField.getValue());
        editedVendedor.setCnpj((String) cnpjField.getValue());
        editedVendedor.setComissao(comissaoField.getValue());
        dataManager.saveWithoutReload(editedVendedor);
        vendedoresDl.load();
        sidePanelLayout.closeSidePanel();
    }
}
