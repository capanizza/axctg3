package br.com.axialsoftware.axctg3.view.cadastros.empresa;

import br.com.axialsoftware.axctg3.entity.cadastros.Empresa;
import br.com.axialsoftware.axctg3.view.main.MainView;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.view.*;

@Route(value = "empresas/:id", layout = MainView.class)
@ViewController(id = "Empresa.detail")
@ViewDescriptor(path = "empresa-detail-view.xml")
@EditedEntityContainer("empresaDc")
public class EmpresaDetailView extends StandardDetailView<Empresa> {
}
