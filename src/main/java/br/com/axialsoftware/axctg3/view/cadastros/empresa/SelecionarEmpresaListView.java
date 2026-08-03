package br.com.axialsoftware.axctg3.view.cadastros.empresa;

import br.com.axialsoftware.axctg3.entity.User;
import br.com.axialsoftware.axctg3.entity.cadastros.ConfigRel;
import br.com.axialsoftware.axctg3.entity.cadastros.Empresa;

import br.com.axialsoftware.axctg3.service.UtilGeralService;
import br.com.axialsoftware.axctg3.view.main.MainView;

import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.data.renderer.Renderer;
import com.vaadin.flow.router.Route;
import io.jmix.core.DataManager;
import io.jmix.core.SaveContext;
import io.jmix.core.security.CurrentAuthentication;
import io.jmix.flowui.Dialogs;
import io.jmix.flowui.UiComponents;
import io.jmix.flowui.action.DialogAction;
import io.jmix.flowui.component.checkbox.JmixCheckbox;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.component.sidepanellayout.SidePanelLayout;
import io.jmix.flowui.component.textfield.JmixIntegerField;
import io.jmix.flowui.kit.action.ActionPerformedEvent;
import io.jmix.flowui.kit.component.button.JmixButton;
import io.jmix.flowui.model.CollectionContainer;
import io.jmix.flowui.model.CollectionLoader;
import io.jmix.flowui.view.*;


import org.springframework.beans.factory.annotation.Autowired;


import java.time.LocalDate;
import java.util.List;
import java.util.Map;


@Route(value = "selecionarempresas", layout = MainView.class)
@ViewController(id = "SelecionarEmpresa.list")
@ViewDescriptor(path = "selecionar-empresa-list-view.xml")
@LookupComponent("empresasDataGrid")
@DialogMode(width = "64em")
public class SelecionarEmpresaListView extends StandardListView<Empresa> {
    @ViewComponent
    private MessageBundle messageBundle;
    @ViewComponent
    private DataGrid<Empresa> empresasDataGrid;
    @Autowired
    private Dialogs dialogs;
    @ViewComponent
    private CollectionContainer<Empresa> empresasDc;
    @Autowired
    private DataManager dataManager;
    @Autowired
    private CurrentAuthentication currentAuthentication;
    @ViewComponent
    private JmixIntegerField anoContabilField;
    @ViewComponent
    private JmixIntegerField mesContabilField;
    @Autowired
    private UiComponents uiComponents;
    @ViewComponent
    private CollectionLoader<Empresa> empresasDl;
    @ViewComponent
    private SidePanelLayout sidePanelLayout;
    @Autowired
    private UtilGeralService utilGeralService;

    @Supply(to = "empresasDataGrid.selecionada", subject = "renderer")
    private Renderer<Empresa> empresasDataGridSelecionadaRenderer() {
        return new ComponentRenderer<>(empresa -> {
            JmixCheckbox checkbox = uiComponents.create(JmixCheckbox.class);
            checkbox.setValue(Boolean.TRUE.equals(empresa.getSelecionada()));
            // checkbox.setReadOnly(true);
            checkbox.addValueChangeListener(e -> {
                if (e.isFromClient()) {
                    // Reverte para o valor original
                    checkbox.setValue(Boolean.TRUE.equals(empresa.getSelecionada()));
                }
            });
            return checkbox;
        });
    }

    @Subscribe("empresasDataGrid.selecionarAction")
    public void onEmpresasDataGridSelecionarAction(final ActionPerformedEvent event) {
        Empresa empresa = empresasDataGrid.getSingleSelectedItem();
        if (empresa == null) {
            dialogs.createMessageDialog()
                    .withHeader(messageBundle.getMessage("selecionarEmpresaListView.selecionarEmpresa.header"))
                    .withText(messageBundle.getMessage("selecionarEmpresaListView.nenhumaSelecionada.text"))
                    .open();
            return;
        }
        List<Empresa> empresas =  empresasDc.getItems();
        int codEmpresa = empresa.getCodigo();

        String mensagem = messageBundle.formatMessage("selecionarEmpresaListView.confirmar.text",
                empresa.getApelido());
        dialogs.createOptionDialog()
                .withHeader(messageBundle.getMessage("selecionarEmpresaListView.selecionarEmpresa.header"))
                .withText(mensagem)
                .withActions(
                        new DialogAction(DialogAction.Type.YES)
                                .withHandler(e -> {
                                    SaveContext saveContext = new SaveContext();
                                    for (Empresa empr : empresas) {
                                        empr.setSelecionada(empr.getCodigo() == codEmpresa);
                                        saveContext.saving(empr);
                                    }
                                    dataManager.save(saveContext);
                                    User user = (User) currentAuthentication.getUser();
                                    User userSalvo = dataManager.load(User.class).id(user.getId()).one();
                                    user.setCodEmpresa(codEmpresa);  // para atualizar a tela
                                    userSalvo.setCodEmpresa(codEmpresa);
                                    dataManager.saveWithoutReload(userSalvo);
                                    dialogs.createMessageDialog()
                                            .withHeader(messageBundle.getMessage("selecionarEmpresaListView.selecionarEmpresa.header"))
                                            .withText(messageBundle.formatMessage("selecionarEmpresaListView.selecionada.text",
                                                    empresa.getApelido()))
                                            .open();
                                    UI.getCurrent().getPage().reload();
                                }),
                        new DialogAction(DialogAction.Type.NO)
                                .withHandler(e -> {
                                })
                )
                .open();
    }

    @Subscribe("empresasDataGrid.periodoAction")
    public void onEmpresasDataGridPeriodoAction(final ActionPerformedEvent event) {
        Empresa empresa = empresasDataGrid.getSingleSelectedItem();
        if (empresa == null) {
            return;
        }
        if (!Boolean.TRUE.equals(empresa.getSelecionada())) {
            dialogs.createMessageDialog()
                    .withHeader(messageBundle.getMessage("selecionarEmpresaListView.periodo.header"))
                    .withText(messageBundle.getMessage("selecionarEmpresaListView.apenasSelecionada.text"))
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

    @Subscribe(id = "closeBtn", subject = "clickListener")
    public void onDetailActionsClick(final ClickEvent<HorizontalLayout> event) {
        sidePanelLayout.closeSidePanel();
    }

    @Subscribe(id = "saveAndCloseBtn", subject = "clickListener")
    public void onSaveAndCloseBtnClick(final ClickEvent<JmixButton> event) {
        Integer anoContabil = anoContabilField.getValue();
        Integer mesContabil = mesContabilField.getValue();
        if (anoContabil == null || mesContabil == null || mesContabil < 1 || mesContabil > 12) {
            dialogs.createMessageDialog()
                    .withHeader(messageBundle.getMessage("selecionarEmpresaListView.periodo.header"))
                    .withText(messageBundle.getMessage("selecionarEmpresaListView.periodoInvalido.text"))
                    .open();
            return;
        }
        User user = (User) currentAuthentication.getUser();
        User usuario = (User) dataManager.load(User.class).id(user.getId()).one();
        user.setAnoContabil(anoContabil); // para atualizar tela
        user.setMesContabil(mesContabil);
        usuario.setAnoContabil(anoContabil);
        usuario.setMesContabil(mesContabil);
        dataManager.saveWithoutReload(usuario);
        ConfigRel configRel = utilGeralService.prepararConfigRel();
        Map<String, LocalDate> mapa = utilGeralService.prepararDatas(usuario.getAnoContabil(), usuario.getMesContabil());
        LocalDate dataInicial = mapa.get("dataInicial");
        LocalDate dataFinal = mapa.get("dataFinal");
        configRel.setDataLancamentoInicial(dataInicial);
        configRel.setDataLancamentoFinal(dataFinal);
        dataManager.saveWithoutReload(configRel);
        sidePanelLayout.closeSidePanel();
        UI.getCurrent().getPage().reload();
    }

}