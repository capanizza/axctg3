package br.com.axialsoftware.axctg3.view.cadastros.empresa;

import br.com.axialsoftware.axctg3.entity.cadastros.Empresa;

import br.com.axialsoftware.axctg3.view.main.MainView;

import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.data.renderer.Renderer;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.UiComponents;
import io.jmix.flowui.component.checkbox.JmixCheckbox;
import io.jmix.flowui.view.*;


import org.springframework.beans.factory.annotation.Autowired;


@Route(value = "empresas", layout = MainView.class)
@ViewController(id = "Empresa.list")
@ViewDescriptor(path = "empresa-list-view.xml")
@LookupComponent("empresasDataGrid")
@DialogMode(width = "64em")
public class EmpresaListView extends StandardListView<Empresa> {
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

}
