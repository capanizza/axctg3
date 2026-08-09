package br.com.axialsoftware.axctg3.view.fiscal.grupoproduto;

import br.com.axialsoftware.axctg3.entity.enums.TipoProduto;
import br.com.axialsoftware.axctg3.entity.fiscal.GrupoProduto;
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
import io.jmix.flowui.component.select.JmixSelect;
import io.jmix.flowui.component.sidepanellayout.SidePanelLayout;
import io.jmix.flowui.component.textfield.JmixIntegerField;
import io.jmix.flowui.component.textfield.TypedTextField;
import io.jmix.flowui.kit.action.ActionPerformedEvent;
import io.jmix.flowui.kit.component.button.JmixButton;
import io.jmix.flowui.model.CollectionLoader;
import io.jmix.flowui.view.*;

import org.springframework.beans.factory.annotation.Autowired;

@Route(value = "grupo-produtos", layout = MainView.class)
@ViewController(id = "GrupoProduto.list")
@ViewDescriptor(path = "grupo-produto-list-view.xml")
@LookupComponent("grupoProdutosDataGrid")
@DialogMode(width = "64em")
public class GrupoProdutoListView extends StandardListView<GrupoProduto> {

    @ViewComponent
    private MessageBundle messageBundle;
    @Autowired
    private UtilGeralService utilGeralService;
    @ViewComponent
    private CollectionLoader<GrupoProduto> grupoProdutosDl;
    @ViewComponent
    private HorizontalLayout buttonsPanel;
    @ViewComponent
    private SidePanelLayout sidePanelLayout;
    @ViewComponent
    private DataGrid<GrupoProduto> grupoProdutosDataGrid;
    @Autowired
    private Dialogs dialogs;
    @Autowired
    private DataManager dataManager;
    @ViewComponent
    private JmixIntegerField codigoField;
    @ViewComponent
    private TypedTextField<Object> nomeField;
    @ViewComponent
    private JmixSelect<TipoProduto> tipoProdutoField;

    private GrupoProduto editedGrupoProduto;

    @Subscribe
    public void onBeforeShow(final BeforeShowEvent event) {
        Integer codEmpresa = utilGeralService.getCodEmpresa();
        if (codEmpresa != null) {
            grupoProdutosDl.setParameter("codEmpresa", codEmpresa);
            grupoProdutosDl.load();
        }

        Dialog dialog = UiComponentUtils.findDialog(this);
        buttonsPanel.setVisible(dialog == null);
    }

    private boolean empresaSelecionada() {
        if (utilGeralService.getCodEmpresa() != null) {
            return true;
        }
        dialogs.createMessageDialog()
                .withHeader(messageBundle.getMessage("grupoProdutoListView.empresaNaoSelecionada.header"))
                .withText(messageBundle.getMessage("grupoProdutoListView.empresaNaoSelecionada.text"))
                .open();
        return false;
    }

    @Subscribe("grupoProdutosDataGrid.createAction")
    public void onGrupoProdutosDataGridCreateAction(final ActionPerformedEvent event) {
        // abrir o sidePanelLayout, criar uma instância de GrupoProduto e salvar
        // quando for pressionado o botão saveAndClose
        if (!empresaSelecionada()) {
            return;
        }
        editedGrupoProduto = dataManager.create(GrupoProduto.class);
        editedGrupoProduto.setCodEmpresa(utilGeralService.getCodEmpresa());
        codigoField.setValue(null);
        nomeField.setTypedValue(null);
        tipoProdutoField.setValue(null);
        sidePanelLayout.openSidePanel();
        codigoField.focus();
    }

    @Subscribe("grupoProdutosDataGrid.editAction")
    public void onGrupoProdutosDataGridEditAction(final ActionPerformedEvent event) {
        // abrir o sidePanelLayout, ler o GrupoProduto atual, alterar e salvar
        // quando for pressionado o botão saveAndClose
        GrupoProduto selected = grupoProdutosDataGrid.getSingleSelectedItem();
        if (selected == null) {
            dialogs.createMessageDialog()
                    .withHeader(messageBundle.getMessage("grupoProdutoListView.naoSelecionado.header"))
                    .withText(messageBundle.getMessage("grupoProdutoListView.naoSelecionado.text"))
                    .open();
            return;
        }
        editedGrupoProduto = dataManager.load(GrupoProduto.class)
                .id(selected.getId())
                .one();
        codigoField.setValue(editedGrupoProduto.getCodigo());
        nomeField.setTypedValue(editedGrupoProduto.getNome());
        tipoProdutoField.setValue(editedGrupoProduto.getTipoProduto());
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
        // salvar o GrupoProduto atual e fechar o sidePanelLayout
        if (editedGrupoProduto == null) {
            sidePanelLayout.closeSidePanel();
            return;
        }
        editedGrupoProduto.setCodigo(codigoField.getValue());
        editedGrupoProduto.setNome((String) nomeField.getValue());
        editedGrupoProduto.setTipoProduto(tipoProdutoField.getValue());
        dataManager.saveWithoutReload(editedGrupoProduto);
        grupoProdutosDl.load();
        sidePanelLayout.closeSidePanel();
    }
}
