package br.com.axialsoftware.axctg3.view.cadastros.condicaopagamento;

import br.com.axialsoftware.axctg3.entity.cadastros.CondicaoPagamento;
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

@Route(value = "condicao-pagamentoes", layout = MainView.class)
@ViewController(id = "CondicaoPagamento.list")
@ViewDescriptor(path = "condicao-pagamento-list-view.xml")
@LookupComponent("condicaoPagamentoesDataGrid")
@DialogMode(width = "64em")
public class CondicaoPagamentoListView extends StandardListView<CondicaoPagamento> {

    @ViewComponent
    private MessageBundle messageBundle;
    @Autowired
    private UtilGeralService utilGeralService;
    @ViewComponent
    private CollectionLoader<CondicaoPagamento> condicaoPagamentoesDl;
    @ViewComponent
    private HorizontalLayout buttonsPanel;
    @ViewComponent
    private SidePanelLayout sidePanelLayout;
    @ViewComponent
    private DataGrid<CondicaoPagamento> condicaoPagamentoesDataGrid;
    @Autowired
    private Dialogs dialogs;
    @Autowired
    private DataManager dataManager;
    @ViewComponent
    private JmixIntegerField codigoField;
    @ViewComponent
    private TypedTextField<Object> nomeField;
    @ViewComponent
    private JmixIntegerField parcelasField;
    @ViewComponent
    private JmixIntegerField primeiraField;
    @ViewComponent
    private JmixIntegerField diferencaField;

    private CondicaoPagamento editedCondicaoPagamento;

    @Subscribe
    public void onBeforeShow(final BeforeShowEvent event) {
        Integer codEmpresa = utilGeralService.getCodEmpresa();
        if (codEmpresa != null) {
            condicaoPagamentoesDl.setParameter("codEmpresa", codEmpresa);
            condicaoPagamentoesDl.load();
        }

        Dialog dialog = UiComponentUtils.findDialog(this);
        buttonsPanel.setVisible(dialog == null);
    }

    private boolean empresaSelecionada() {
        if (utilGeralService.getCodEmpresa() != null) {
            return true;
        }
        dialogs.createMessageDialog()
                .withHeader(messageBundle.getMessage("condicaoPagamentoListView.empresaNaoSelecionada.header"))
                .withText(messageBundle.getMessage("condicaoPagamentoListView.empresaNaoSelecionada.text"))
                .open();
        return false;
    }

    @Subscribe("condicaoPagamentoesDataGrid.createAction")
    public void onCondicaoPagamentoesDataGridCreateAction(final ActionPerformedEvent event) {
        // abrir o sidePanelLayout, criar uma instância de CondicaoPagamento e salvar
        // quando for pressionado o botão saveAndClose
        if (!empresaSelecionada()) {
            return;
        }
        editedCondicaoPagamento = dataManager.create(CondicaoPagamento.class);
        editedCondicaoPagamento.setCodEmpresa(utilGeralService.getCodEmpresa());
        codigoField.setValue(null);
        nomeField.setTypedValue(null);
        parcelasField.setValue(null);
        primeiraField.setValue(null);
        diferencaField.setValue(null);
        sidePanelLayout.openSidePanel();
        codigoField.focus();
    }

    @Subscribe("condicaoPagamentoesDataGrid.editAction")
    public void onCondicaoPagamentoesDataGridEditAction(final ActionPerformedEvent event) {
        // abrir o sidePanelLayout, ler a CondicaoPagamento atual, alterar e salvar
        // quando for pressionado o botão saveAndClose
        CondicaoPagamento selected = condicaoPagamentoesDataGrid.getSingleSelectedItem();
        if (selected == null) {
            dialogs.createMessageDialog()
                    .withHeader(messageBundle.getMessage("condicaoPagamentoListView.naoSelecionado.header"))
                    .withText(messageBundle.getMessage("condicaoPagamentoListView.naoSelecionado.text"))
                    .open();
            return;
        }
        editedCondicaoPagamento = dataManager.load(CondicaoPagamento.class)
                .id(selected.getId())
                .one();
        codigoField.setValue(editedCondicaoPagamento.getCodigo());
        nomeField.setTypedValue(editedCondicaoPagamento.getNome());
        parcelasField.setValue(editedCondicaoPagamento.getParcelas());
        primeiraField.setValue(editedCondicaoPagamento.getPrimeira());
        diferencaField.setValue(editedCondicaoPagamento.getDiferenca());
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
        // salvar a CondicaoPagamento atual e fechar o sidePanelLayout
        if (editedCondicaoPagamento == null) {
            sidePanelLayout.closeSidePanel();
            return;
        }
        editedCondicaoPagamento.setCodigo(codigoField.getValue());
        editedCondicaoPagamento.setNome((String) nomeField.getValue());
        editedCondicaoPagamento.setParcelas(parcelasField.getValue());
        editedCondicaoPagamento.setPrimeira(primeiraField.getValue());
        editedCondicaoPagamento.setDiferenca(diferencaField.getValue());
        dataManager.saveWithoutReload(editedCondicaoPagamento);
        condicaoPagamentoesDl.load();
        sidePanelLayout.closeSidePanel();
    }
}
