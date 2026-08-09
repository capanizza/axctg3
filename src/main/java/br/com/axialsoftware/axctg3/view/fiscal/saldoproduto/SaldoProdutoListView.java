package br.com.axialsoftware.axctg3.view.fiscal.saldoproduto;

import br.com.axialsoftware.axctg3.entity.fiscal.SaldoProduto;
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
import io.jmix.flowui.kit.action.ActionPerformedEvent;
import io.jmix.flowui.kit.component.button.JmixButton;
import io.jmix.flowui.model.CollectionLoader;
import io.jmix.flowui.model.InstanceContainer;
import io.jmix.flowui.view.*;

import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;

@Route(value = "saldo-produtos", layout = MainView.class)
@ViewController(id = "SaldoProduto.list")
@ViewDescriptor(path = "saldo-produto-list-view.xml")
@LookupComponent("saldoProdutosDataGrid")
@DialogMode(width = "64em")
public class SaldoProdutoListView extends StandardListView<SaldoProduto> {

    @ViewComponent
    private MessageBundle messageBundle;
    @Autowired
    private UtilGeralService utilGeralService;
    @ViewComponent
    private CollectionLoader<SaldoProduto> saldoProdutosDl;
    @ViewComponent
    private InstanceContainer<SaldoProduto> saldoProdutoDc;
    @ViewComponent
    private HorizontalLayout buttonsPanel;
    @ViewComponent
    private SidePanelLayout sidePanelLayout;
    @ViewComponent
    private DataGrid<SaldoProduto> saldoProdutosDataGrid;
    @Autowired
    private Dialogs dialogs;
    @Autowired
    private DataManager dataManager;

    @Subscribe
    public void onBeforeShow(final BeforeShowEvent event) {
        Integer codEmpresa = utilGeralService.getCodEmpresa();
        if (codEmpresa != null) {
            saldoProdutosDl.setParameter("codEmpresa", codEmpresa);
            saldoProdutosDl.load();
        }

        Dialog dialog = UiComponentUtils.findDialog(this);
        buttonsPanel.setVisible(dialog == null);
    }

    private boolean empresaSelecionada() {
        if (utilGeralService.getCodEmpresa() != null) {
            return true;
        }
        dialogs.createMessageDialog()
                .withHeader(messageBundle.getMessage("saldoProdutoListView.empresaNaoSelecionada.header"))
                .withText(messageBundle.getMessage("saldoProdutoListView.empresaNaoSelecionada.text"))
                .open();
        return false;
    }

    @Subscribe("saldoProdutosDataGrid.createAction")
    public void onSaldoProdutosDataGridCreateAction(final ActionPerformedEvent event) {
        // abrir o sidePanelLayout, criar uma instância de SaldoProduto e salvar
        // quando for pressionado o botão saveAndClose
        if (!empresaSelecionada()) {
            return;
        }
        SaldoProduto saldoProduto = dataManager.create(SaldoProduto.class);
        saldoProduto.setData(LocalDate.now());
        saldoProdutoDc.setItem(saldoProduto);
        sidePanelLayout.openSidePanel();
    }

    @Subscribe("saldoProdutosDataGrid.editAction")
    public void onSaldoProdutosDataGridEditAction(final ActionPerformedEvent event) {
        // abrir o sidePanelLayout, ler o SaldoProduto atual, alterar e salvar
        // quando for pressionado o botão saveAndClose
        SaldoProduto selected = saldoProdutosDataGrid.getSingleSelectedItem();
        if (selected == null) {
            dialogs.createMessageDialog()
                    .withHeader(messageBundle.getMessage("saldoProdutoListView.naoSelecionado.header"))
                    .withText(messageBundle.getMessage("saldoProdutoListView.naoSelecionado.text"))
                    .open();
            return;
        }
        // "selected" já vem com o fetchPlan de saldoProdutosDl (produto com
        // _instance_name), então usamos direto — um reload perderia essa
        // profundidade sem um fetchPlan explícito
        saldoProdutoDc.setItem(selected);
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
        // salvar o SaldoProduto atual e fechar o sidePanelLayout
        SaldoProduto saldoProduto = saldoProdutoDc.getItemOrNull();
        if (saldoProduto == null) {
            sidePanelLayout.closeSidePanel();
            return;
        }
        dataManager.saveWithoutReload(saldoProduto);
        saldoProdutosDl.load();
        sidePanelLayout.closeSidePanel();
    }
}
