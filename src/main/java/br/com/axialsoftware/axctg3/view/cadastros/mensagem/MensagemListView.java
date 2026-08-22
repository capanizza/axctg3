package br.com.axialsoftware.axctg3.view.cadastros.mensagem;

import br.com.axialsoftware.axctg3.entity.cadastros.Mensagem;
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
import io.jmix.flowui.component.textarea.JmixTextArea;
import io.jmix.flowui.component.textfield.JmixIntegerField;
import io.jmix.flowui.kit.action.ActionPerformedEvent;
import io.jmix.flowui.kit.component.button.JmixButton;
import io.jmix.flowui.model.CollectionLoader;
import io.jmix.flowui.view.*;

import org.springframework.beans.factory.annotation.Autowired;

@Route(value = "mensagens", layout = MainView.class)
@ViewController(id = "Mensagem.list")
@ViewDescriptor(path = "mensagem-list-view.xml")
@LookupComponent("mensagensDataGrid")
@DialogMode(width = "64em")
public class MensagemListView extends StandardListView<Mensagem> {

    @ViewComponent
    private MessageBundle messageBundle;
    @Autowired
    private UtilGeralService utilGeralService;
    @ViewComponent
    private CollectionLoader<Mensagem> mensagensDl;
    @ViewComponent
    private HorizontalLayout buttonsPanel;
    @ViewComponent
    private SidePanelLayout sidePanelLayout;
    @ViewComponent
    private DataGrid<Mensagem> mensagensDataGrid;
    @Autowired
    private Dialogs dialogs;
    @Autowired
    private DataManager dataManager;
    @ViewComponent
    private JmixIntegerField codigoField;
    @ViewComponent
    private JmixTextArea textoField;

    private Mensagem editedMensagem;

    @Subscribe
    public void onBeforeShow(final BeforeShowEvent event) {
        Integer codEmpresa = utilGeralService.getCodEmpresa();
        if (codEmpresa != null) {
            mensagensDl.setParameter("codEmpresa", codEmpresa);
            mensagensDl.load();
        }

        Dialog dialog = UiComponentUtils.findDialog(this);
        buttonsPanel.setVisible(dialog == null);
    }

    private boolean empresaSelecionada() {
        if (utilGeralService.getCodEmpresa() != null) {
            return true;
        }
        dialogs.createMessageDialog()
                .withHeader(messageBundle.getMessage("mensagemListView.empresaNaoSelecionada.header"))
                .withText(messageBundle.getMessage("mensagemListView.empresaNaoSelecionada.text"))
                .open();
        return false;
    }

    @Subscribe("mensagensDataGrid.createAction")
    public void onMensagensDataGridCreateAction(final ActionPerformedEvent event) {
        // abrir o sidePanelLayout, criar uma instância de Mensagem e salvar
        // quando for pressionado o botão saveAndClose
        if (!empresaSelecionada()) {
            return;
        }
        editedMensagem = dataManager.create(Mensagem.class);
        editedMensagem.setCodEmpresa(utilGeralService.getCodEmpresa());
        codigoField.setValue(null);
        textoField.setValue("");
        sidePanelLayout.openSidePanel();
        codigoField.focus();
    }

    @Subscribe("mensagensDataGrid.editAction")
    public void onMensagensDataGridEditAction(final ActionPerformedEvent event) {
        // abrir o sidePanelLayout, ler a Mensagem atual, alterar e salvar
        // quando for pressionado o botão saveAndClose
        Mensagem selected = mensagensDataGrid.getSingleSelectedItem();
        if (selected == null) {
            dialogs.createMessageDialog()
                    .withHeader(messageBundle.getMessage("mensagemListView.naoSelecionado.header"))
                    .withText(messageBundle.getMessage("mensagemListView.naoSelecionado.text"))
                    .open();
            return;
        }
        editedMensagem = dataManager.load(Mensagem.class)
                .id(selected.getId())
                .one();
        codigoField.setValue(editedMensagem.getCodigo());
        textoField.setValue(editedMensagem.getTexto() == null ? "" : editedMensagem.getTexto());
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
        // salvar a Mensagem atual e fechar o sidePanelLayout
        if (editedMensagem == null) {
            sidePanelLayout.closeSidePanel();
            return;
        }
        editedMensagem.setCodigo(codigoField.getValue());
        editedMensagem.setTexto(textoField.getValue());
        dataManager.saveWithoutReload(editedMensagem);
        mensagensDl.load();
        sidePanelLayout.closeSidePanel();
    }
}
