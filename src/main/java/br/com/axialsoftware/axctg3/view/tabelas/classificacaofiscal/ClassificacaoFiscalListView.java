package br.com.axialsoftware.axctg3.view.tabelas.classificacaofiscal;

import br.com.axialsoftware.axctg3.entity.tabelas.ClassificacaoFiscal;
import br.com.axialsoftware.axctg3.service.tabelas.ClassificacaoFiscalImportService;
import br.com.axialsoftware.axctg3.view.main.MainView;

import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.router.Route;
import io.jmix.core.AccessManager;
import io.jmix.core.DataManager;
import io.jmix.core.Metadata;
import io.jmix.core.accesscontext.CrudEntityContext;
import io.jmix.flowui.Dialogs;
import io.jmix.flowui.Notifications;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.component.sidepanellayout.SidePanelLayout;
import io.jmix.flowui.component.textarea.JmixTextArea;
import io.jmix.flowui.component.textfield.JmixIntegerField;
import io.jmix.flowui.component.textfield.TypedTextField;
import io.jmix.flowui.component.upload.FileUploadField;
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
    private JmixTextArea descricaoField;
    @ViewComponent
    private FileUploadField importField;
    @Autowired
    private Notifications notifications;
    @Autowired
    private ClassificacaoFiscalImportService classificacaoFiscalImportService;
    @Autowired
    private AccessManager accessManager;
    @Autowired
    private Metadata metadata;
    @ViewComponent
    private JmixButton importButton;

    private ClassificacaoFiscal editedClassificacaoFiscal;

    // A tabela é global (todos os grupos): importar é só para quem pode criar e apagar —
    // na prática a Axial. Os papéis de operador/gerente só leem.
    @Subscribe
    public void onBeforeShow(final BeforeShowEvent event) {
        CrudEntityContext contexto = new CrudEntityContext(metadata.getClass(ClassificacaoFiscal.class));
        accessManager.applyRegisteredConstraints(contexto);
        boolean podeImportar = contexto.isCreatePermitted() && contexto.isDeletePermitted();
        importField.setVisible(podeImportar);
        importButton.setVisible(podeImportar);
    }

    @Subscribe("classificacaoFiscalsDataGrid.createAction")
    public void onClassificacaoFiscalsDataGridCreateAction(final ActionPerformedEvent event) {
        editedClassificacaoFiscal = dataManager.create(ClassificacaoFiscal.class);
        codigoField.setValue(null);
        codNcmField.setTypedValue(null);
        descricaoField.setValue("");
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
        descricaoField.setValue(editedClassificacaoFiscal.getDescricao() == null
                ? "" : editedClassificacaoFiscal.getDescricao());
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
        String descricao = descricaoField.getValue();
        editedClassificacaoFiscal.setDescricao(descricao == null || descricao.isBlank() ? null : descricao);
        dataManager.saveWithoutReload(editedClassificacaoFiscal);
        classificacaoFiscalsDl.load();
        sidePanelLayout.closeSidePanel();
    }

    @Subscribe(id = "importButton", subject = "clickListener")
    public void onImportButtonClick(final ClickEvent<JmixButton> event) {
        byte[] conteudo = importField.getValue();
        if (conteudo == null || conteudo.length == 0) {
            notifications.create(messageBundle.getMessage("classificacaoFiscalListView.selecioneArquivo"))
                    .withType(Notifications.Type.WARNING)
                    .show();
            return;
        }

        ClassificacaoFiscalImportService.ImportResult resultado;
        try {
            resultado = classificacaoFiscalImportService.importar(conteudo);
        } catch (IllegalArgumentException e) {
            notifications.create(e.getMessage())
                    .withType(Notifications.Type.ERROR)
                    .show();
            return;
        }
        classificacaoFiscalsDl.load();
        importField.clear();

        String texto = messageBundle.formatMessage("classificacaoFiscalListView.importResultado",
                resultado.vigencia(), resultado.criados(), resultado.atualizados(), resultado.removidos());
        if (resultado.mantidosEmUso().isEmpty()) {
            notifications.create(texto)
                    .withType(Notifications.Type.SUCCESS)
                    .show();
        } else {
            dialogs.createMessageDialog()
                    .withHeader(messageBundle.getMessage("classificacaoFiscalListView.importMantidos.header"))
                    .withText(texto + " " + messageBundle.formatMessage(
                            "classificacaoFiscalListView.importMantidos.text",
                            String.join(", ", resultado.mantidosEmUso())))
                    .open();
        }
    }
}
