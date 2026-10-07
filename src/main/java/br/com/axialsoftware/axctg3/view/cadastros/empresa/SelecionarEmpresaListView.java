package br.com.axialsoftware.axctg3.view.cadastros.empresa;

import br.com.axialsoftware.axctg3.entity.User;
import br.com.axialsoftware.axctg3.entity.cadastros.Empresa;
import br.com.axialsoftware.axctg3.service.UtilGeralService;

import br.com.axialsoftware.axctg3.view.main.MainView;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.data.renderer.Renderer;
import com.vaadin.flow.router.Route;
import io.jmix.core.DataManager;
import io.jmix.core.security.CurrentAuthentication;
import io.jmix.flowui.Dialogs;
import io.jmix.flowui.UiComponents;
import io.jmix.flowui.action.DialogAction;
import io.jmix.flowui.component.checkbox.JmixCheckbox;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.kit.action.ActionPerformedEvent;
import io.jmix.flowui.view.*;


import org.springframework.beans.factory.annotation.Autowired;


import java.util.Objects;


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
            checkbox.setValue(empresaSelecionada(empresa));
            checkbox.setReadOnly(true);
            checkbox.addClassName("grid-value-checkbox");
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
        int codEmpresa = empresa.getCodigo();

        String mensagem = messageBundle.formatMessage("selecionarEmpresaListView.confirmar.text",
                empresa.getApelido());
        dialogs.createOptionDialog()
                .withHeader(messageBundle.getMessage("selecionarEmpresaListView.selecionarEmpresa.header"))
                .withText(mensagem)
                .withActions(
                        new DialogAction(DialogAction.Type.YES)
                                .withHandler(e -> {
                                    // Grava antes de mexer no principal: o UserEventListener
                                    // recusa empresa de outro grupo, e aí a sessão não muda.
                                    User user = (User) currentAuthentication.getUser();
                                    User userSalvo = dataManager.load(User.class).id(user.getId()).one();
                                    userSalvo.setCodEmpresa(codEmpresa);
                                    dataManager.saveWithoutReload(userSalvo);
                                    user.setCodEmpresa(codEmpresa);  // para atualizar a tela
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

    // Empresa corrente do usuário logado (User.codEmpresa) — cada usuário tem a sua.
    private boolean empresaSelecionada(Empresa empresa) {
        return Objects.equals(empresa.getCodigo(), utilGeralService.getCodEmpresa());
    }
}
