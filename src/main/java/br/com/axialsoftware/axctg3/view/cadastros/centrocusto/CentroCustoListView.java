package br.com.axialsoftware.axctg3.view.cadastros.centrocusto;

import br.com.axialsoftware.axctg3.entity.cadastros.CentroCusto;

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
import io.jmix.flowui.component.textfield.TypedTextField;
import io.jmix.flowui.kit.action.ActionPerformedEvent;
import io.jmix.flowui.kit.component.button.JmixButton;
import io.jmix.flowui.model.CollectionContainer;
import io.jmix.flowui.model.CollectionLoader;
import io.jmix.flowui.view.*;

import org.springframework.beans.factory.annotation.Autowired;

@Route(value = "centro-custoes", layout = MainView.class)
@ViewController(id = "CentroCusto.list")
@ViewDescriptor(path = "centro-custo-list-view.xml")
@LookupComponent("centroCustoesDataGrid")
@DialogMode(width = "64em")
public class CentroCustoListView extends StandardListView<CentroCusto> {
    @ViewComponent
    private MessageBundle messageBundle;
    @Autowired
    private UtilGeralService utilGeralService;
    @ViewComponent
    private CollectionLoader<CentroCusto> centroCustoesDl;
    @ViewComponent
    private SidePanelLayout sidePanelLayout;
    @ViewComponent
    private DataGrid<CentroCusto> centroCustoesDataGrid;
    @ViewComponent
    private CollectionContainer<CentroCusto> centroCustoesDc;
    @Autowired
    private Dialogs dialogs;
    @ViewComponent
    private TypedTextField<Object> codigoField;
    @ViewComponent
    private TypedTextField<Object> nomeField;
    @Autowired
    private DataManager dataManager;

    private CentroCusto editedCentroCusto;
    @ViewComponent
    private HorizontalLayout buttonsPanel;

    @Subscribe
    public void onBeforeShow(final BeforeShowEvent event) {
        Integer codEmpresa = utilGeralService.getCodEmpresa();
        if (codEmpresa != null) {
            centroCustoesDl.setParameter("codEmpresa", codEmpresa);
            centroCustoesDl.load();
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
                .withHeader(messageBundle.getMessage("centroCustoListView.empresaNaoSelecionada.header"))
                .withText(messageBundle.getMessage("centroCustoListView.empresaNaoSelecionada.text"))
                .open();
        return false;
    }


    @Subscribe("centroCustoesDataGrid.createAction")
    public void onCentroCustoesDataGridCreateAction(final ActionPerformedEvent event) {
        // abrir o sidePanelLayout, criar uma instância do CentroCusto e salvar
        // salvar quando for oressionado o botao saveAndClose
        if (!empresaSelecionada()) {
            return;
        }
        editedCentroCusto = dataManager.create(CentroCusto.class);
        editedCentroCusto.setCodEmpresa(utilGeralService.getCodEmpresa());
        codigoField.setTypedValue(null);
        nomeField.setTypedValue(null);
        sidePanelLayout.openSidePanel();
        codigoField.focus();
    }

    @Subscribe("centroCustoesDataGrid.editAction")
    public void onCentroCustoesDataGridEditAction(final ActionPerformedEvent event) {
        // abrir o sidePanelLayout, ler o CentrodeCusto atual, alterar e salvar
        // salvar quando for oressionado o botao saveAndClose
        CentroCusto selected = centroCustoesDataGrid.getSingleSelectedItem();
        if (selected == null) {
            dialogs.createMessageDialog()
                    .withHeader(messageBundle.getMessage("centroCustoListView.naoSelecionado.header"))
                    .withText(messageBundle.getMessage("centroCustoListView.naoSelecionado.text"))
                    .open();
            return;
        }
        editedCentroCusto = dataManager.load(CentroCusto.class)
                .id(selected.getId())
                .one();
        codigoField.setTypedValue(editedCentroCusto.getCodigo());
        nomeField.setTypedValue(editedCentroCusto.getNome());
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
        // salvar o CentroCusto atual e fechar o sidePanelLayout}
        if (editedCentroCusto == null) {
            sidePanelLayout.closeSidePanel();
            return;
        }
        editedCentroCusto.setCodigo(codigoField.getValue());
        editedCentroCusto.setNome(nomeField.getValue());
        dataManager.saveWithoutReload(editedCentroCusto);
        centroCustoesDl.load();
        sidePanelLayout.closeSidePanel();
    }
}