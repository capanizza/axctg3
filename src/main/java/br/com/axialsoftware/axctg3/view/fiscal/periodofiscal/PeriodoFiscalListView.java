package br.com.axialsoftware.axctg3.view.fiscal.periodofiscal;

import br.com.axialsoftware.axctg3.entity.User;
import br.com.axialsoftware.axctg3.entity.cadastros.Empresa;

import br.com.axialsoftware.axctg3.view.main.MainView;

import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.data.renderer.Renderer;
import com.vaadin.flow.router.Route;
import io.jmix.core.DataManager;
import io.jmix.core.security.CurrentAuthentication;
import io.jmix.flowui.Dialogs;
import io.jmix.flowui.UiComponents;
import io.jmix.flowui.component.checkbox.JmixCheckbox;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.component.sidepanellayout.SidePanelLayout;
import io.jmix.flowui.component.textfield.JmixIntegerField;
import io.jmix.flowui.kit.action.ActionPerformedEvent;
import io.jmix.flowui.kit.component.button.JmixButton;
import io.jmix.flowui.view.*;

import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;

@Route(value = "periodo-fiscais", layout = MainView.class)
@ViewController(id = "PeriodoFiscal.list")
@ViewDescriptor(path = "periodo-fiscal-list-view.xml")
@LookupComponent("empresasDataGrid")
@DialogMode(width = "64em")
public class PeriodoFiscalListView extends StandardListView<Empresa> {

    @ViewComponent
    private MessageBundle messageBundle;
    @ViewComponent
    private DataGrid<Empresa> empresasDataGrid;
    @ViewComponent
    private SidePanelLayout sidePanelLayout;
    @ViewComponent
    private JmixIntegerField anoFiscalField;
    @ViewComponent
    private JmixIntegerField mesFiscalField;
    @Autowired
    private Dialogs dialogs;
    @Autowired
    private DataManager dataManager;
    @Autowired
    private CurrentAuthentication currentAuthentication;
    @Autowired
    private UiComponents uiComponents;

    @Supply(to = "empresasDataGrid.selecionada", subject = "renderer")
    private Renderer<Empresa> empresasDataGridSelecionadaRenderer() {
        return new ComponentRenderer<>(empresa -> {
            JmixCheckbox checkbox = uiComponents.create(JmixCheckbox.class);
            checkbox.setValue(Boolean.TRUE.equals(empresa.getSelecionada()));
            checkbox.setReadOnly(true);
            checkbox.addClassName("grid-value-checkbox");
            return checkbox;
        });
    }

    @Subscribe("empresasDataGrid.periodoAction")
    public void onEmpresasDataGridPeriodoAction(final ActionPerformedEvent event) {
        Empresa empresa = empresasDataGrid.getSingleSelectedItem();
        if (empresa == null) {
            return;
        }
        if (!Boolean.TRUE.equals(empresa.getSelecionada())) {
            dialogs.createMessageDialog()
                    .withHeader(messageBundle.getMessage("periodoFiscalListView.periodo.header"))
                    .withText(messageBundle.getMessage("periodoFiscalListView.apenasSelecionada.text"))
                    .open();
            return;
        }
        User user = (User) currentAuthentication.getUser();
        User userSalvo = dataManager.load(User.class).id(user.getId()).one();
        LocalDate hoje = LocalDate.now();
        anoFiscalField.setValue(userSalvo.getAnoFiscal() != null
                ? userSalvo.getAnoFiscal() : hoje.getYear());
        mesFiscalField.setValue(userSalvo.getMesFiscal() != null
                ? userSalvo.getMesFiscal() : hoje.getMonthValue());
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
        Integer anoFiscal = anoFiscalField.getValue();
        Integer mesFiscal = mesFiscalField.getValue();
        if (anoFiscal == null || mesFiscal == null || mesFiscal < 1 || mesFiscal > 12) {
            dialogs.createMessageDialog()
                    .withHeader(messageBundle.getMessage("periodoFiscalListView.periodo.header"))
                    .withText(messageBundle.getMessage("periodoFiscalListView.periodoInvalido.text"))
                    .open();
            return;
        }
        User user = (User) currentAuthentication.getUser();
        User usuario = dataManager.load(User.class).id(user.getId()).one();
        user.setAnoFiscal(anoFiscal); // para atualizar a tela
        user.setMesFiscal(mesFiscal);
        usuario.setAnoFiscal(anoFiscal);
        usuario.setMesFiscal(mesFiscal);
        dataManager.saveWithoutReload(usuario);
        sidePanelLayout.closeSidePanel();
        UI.getCurrent().getPage().reload();
    }
}
