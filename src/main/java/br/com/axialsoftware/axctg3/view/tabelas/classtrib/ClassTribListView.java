package br.com.axialsoftware.axctg3.view.tabelas.classtrib;

import br.com.axialsoftware.axctg3.entity.tabelas.ClassTrib;
import br.com.axialsoftware.axctg3.service.tabelas.ClassTribImportService;
import br.com.axialsoftware.axctg3.view.main.MainView;

import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.data.renderer.Renderer;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.Notifications;
import io.jmix.flowui.UiComponents;
import io.jmix.flowui.component.UiComponentUtils;
import io.jmix.flowui.component.checkbox.JmixCheckbox;
import io.jmix.flowui.component.upload.FileUploadField;
import io.jmix.flowui.kit.component.button.JmixButton;
import io.jmix.flowui.model.CollectionLoader;
import io.jmix.flowui.view.*;

import org.springframework.beans.factory.annotation.Autowired;

@Route(value = "class-tribs", layout = MainView.class)
@ViewController(id = "ClassTrib.list")
@ViewDescriptor(path = "class-trib-list-view.xml")
@LookupComponent("classTribsDataGrid")
@DialogMode(width = "64em")
public class ClassTribListView extends StandardListView<ClassTrib> {

    @ViewComponent
    private MessageBundle messageBundle;
    @ViewComponent
    private CollectionLoader<ClassTrib> classTribsDl;
    @Autowired
    private Notifications notifications;
    @Autowired
    private UiComponents uiComponents;
    @Autowired
    private ClassTribImportService classTribImportService;
    @ViewComponent
    private HorizontalLayout buttonsPanel;
    @ViewComponent
    private FileUploadField importField;

    @Subscribe
    public void onBeforeShow(final BeforeShowEvent event) {
        Dialog dialog = UiComponentUtils.findDialog(this);
        buttonsPanel.setVisible(dialog == null);
    }

    @Supply(to = "classTribsDataGrid.aplicavelNfe", subject = "renderer")
    private Renderer<ClassTrib> classTribsDataGridAplicavelNfeRenderer() {
        return new ComponentRenderer<>(classTrib -> {
            JmixCheckbox checkbox = uiComponents.create(JmixCheckbox.class);
            checkbox.setValue(Boolean.TRUE.equals(classTrib.getAplicavelNfe()));
            checkbox.setReadOnly(true);
            checkbox.addClassName("grid-value-checkbox");
            return checkbox;
        });
    }

    @Subscribe(id = "importButton", subject = "clickListener")
    public void onImportButtonClick(final ClickEvent<JmixButton> event) {
        byte[] conteudo = importField.getValue();
        if (conteudo == null || conteudo.length == 0) {
            notifications.create(messageBundle.getMessage("classTribListView.selecioneArquivo"))
                    .withType(Notifications.Type.WARNING)
                    .show();
            return;
        }

        ClassTribImportService.ImportResult resultado;
        try {
            resultado = classTribImportService.importar(conteudo);
        } catch (IllegalArgumentException e) {
            notifications.create(e.getMessage())
                    .withType(Notifications.Type.ERROR)
                    .show();
            return;
        }
        classTribsDl.load();
        importField.clear();

        notifications.create(messageBundle.formatMessage("classTribListView.importResultado",
                        resultado.criados(), resultado.atualizados(), resultado.ignorados()))
                .withType(Notifications.Type.SUCCESS)
                .show();
    }
}
