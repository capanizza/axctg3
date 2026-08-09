package br.com.axialsoftware.axctg3.view.contabil.periodocontabil;

import br.com.axialsoftware.axctg3.entity.User;
import br.com.axialsoftware.axctg3.entity.cadastros.ConfigRel;
import br.com.axialsoftware.axctg3.entity.cadastros.Empresa;

import br.com.axialsoftware.axctg3.service.UtilGeralService;
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
import java.util.Map;

@Route(value = "periodo-contabeis", layout = MainView.class)
@ViewController(id = "PeriodoContabil.list")
@ViewDescriptor(path = "periodo-contabil-list-view.xml")
@LookupComponent("empresasDataGrid")
@DialogMode(width = "64em")
public class PeriodoContabilListView extends StandardListView<Empresa> {

    @ViewComponent
    private MessageBundle messageBundle;
    @ViewComponent
    private DataGrid<Empresa> empresasDataGrid;
    @ViewComponent
    private SidePanelLayout sidePanelLayout;
    @ViewComponent
    private JmixIntegerField anoContabilField;
    @ViewComponent
    private JmixIntegerField mesContabilField;
    @Autowired
    private Dialogs dialogs;
    @Autowired
    private DataManager dataManager;
    @Autowired
    private CurrentAuthentication currentAuthentication;
    @Autowired
    private UiComponents uiComponents;
    @Autowired
    private UtilGeralService utilGeralService;

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
                    .withHeader(messageBundle.getMessage("periodoContabilListView.periodo.header"))
                    .withText(messageBundle.getMessage("periodoContabilListView.apenasSelecionada.text"))
                    .open();
            return;
        }
        User user = (User) currentAuthentication.getUser();
        User userSalvo = dataManager.load(User.class).id(user.getId()).one();
        LocalDate hoje = LocalDate.now();
        anoContabilField.setValue(userSalvo.getAnoContabil() != null
                ? userSalvo.getAnoContabil() : hoje.getYear());
        mesContabilField.setValue(userSalvo.getMesContabil() != null
                ? userSalvo.getMesContabil() : hoje.getMonthValue());
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
        Integer anoContabil = anoContabilField.getValue();
        Integer mesContabil = mesContabilField.getValue();
        if (anoContabil == null || mesContabil == null || mesContabil < 1 || mesContabil > 12) {
            dialogs.createMessageDialog()
                    .withHeader(messageBundle.getMessage("periodoContabilListView.periodo.header"))
                    .withText(messageBundle.getMessage("periodoContabilListView.periodoInvalido.text"))
                    .open();
            return;
        }
        User user = (User) currentAuthentication.getUser();
        User usuario = dataManager.load(User.class).id(user.getId()).one();
        user.setAnoContabil(anoContabil); // para atualizar a tela
        user.setMesContabil(mesContabil);
        usuario.setAnoContabil(anoContabil);
        usuario.setMesContabil(mesContabil);
        dataManager.saveWithoutReload(usuario);
        ConfigRel configRel = utilGeralService.prepararConfigRel();
        Map<String, LocalDate> mapa = utilGeralService.prepararDatas(usuario.getAnoContabil(), usuario.getMesContabil());
        configRel.setDataLancamentoInicial(mapa.get("dataInicial"));
        configRel.setDataLancamentoFinal(mapa.get("dataFinal"));
        dataManager.saveWithoutReload(configRel);
        sidePanelLayout.closeSidePanel();
        UI.getCurrent().getPage().reload();
    }
}
