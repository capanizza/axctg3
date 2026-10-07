package br.com.axialsoftware.axctg3.view.cadastros.empresa;

import br.com.axialsoftware.axctg3.entity.cadastros.Empresa;
import br.com.axialsoftware.axctg3.service.UtilGeralService;
import br.com.axialsoftware.axctg3.view.cadastros.grupo.GrupoComboSupport;

import br.com.axialsoftware.axctg3.view.main.MainView;

import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.data.renderer.Renderer;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.UiComponents;
import io.jmix.flowui.component.checkbox.JmixCheckbox;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.view.*;


import org.springframework.beans.factory.annotation.Autowired;
import java.util.Objects;


@Route(value = "empresas", layout = MainView.class)
@ViewController(id = "Empresa.list")
@ViewDescriptor(path = "empresa-list-view.xml")
@LookupComponent("empresasDataGrid")
@DialogMode(width = "64em")
public class EmpresaListView extends StandardListView<Empresa> {
    @Autowired
    private UiComponents uiComponents;
    @Autowired
    private UtilGeralService utilGeralService;
    @Autowired
    private GrupoComboSupport grupoComboSupport;
    @ViewComponent
    private DataGrid<Empresa> empresasDataGrid;

    @Subscribe
    public void onInit(final InitEvent event) {
        empresasDataGrid.getColumnByKey("codGrupo").setVisible(grupoComboSupport.usuarioAtualEnxergaTodosGrupos());
    }

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

    // Empresa corrente do usuário logado (User.codEmpresa) — cada usuário tem a sua.
    private boolean empresaSelecionada(Empresa empresa) {
        return Objects.equals(empresa.getCodigo(), utilGeralService.getCodEmpresa());
    }
}
