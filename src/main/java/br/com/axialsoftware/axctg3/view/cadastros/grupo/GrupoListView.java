package br.com.axialsoftware.axctg3.view.cadastros.grupo;

import br.com.axialsoftware.axctg3.entity.cadastros.Grupo;
import br.com.axialsoftware.axctg3.view.main.MainView;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.view.*;

/**
 * Grupos (clientes da Axial). Só a Axial chega aqui (menu Administração, FullAccess).
 * Sem excluir: usuários e empresas guardam o código do grupo.
 */
@Route(value = "grupos", layout = MainView.class)
@ViewController(id = "Grupo.list")
@ViewDescriptor(path = "grupo-list-view.xml")
@LookupComponent("gruposDataGrid")
@DialogMode(width = "64em")
public class GrupoListView extends StandardListView<Grupo> {
}
