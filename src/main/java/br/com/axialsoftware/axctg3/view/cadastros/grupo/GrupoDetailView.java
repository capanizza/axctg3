package br.com.axialsoftware.axctg3.view.cadastros.grupo;

import br.com.axialsoftware.axctg3.entity.cadastros.Grupo;
import br.com.axialsoftware.axctg3.service.cadastros.GrupoService;
import br.com.axialsoftware.axctg3.view.main.MainView;
import com.google.common.base.Strings;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.router.Route;
import io.jmix.core.DataManager;
import io.jmix.core.EntityStates;
import io.jmix.core.SaveContext;
import io.jmix.flowui.component.textfield.JmixIntegerField;
import io.jmix.flowui.component.textfield.TypedTextField;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Objects;
import java.util.Set;

/**
 * Cadastro de grupo. Num grupo novo pede também a primeira empresa (código vem da
 * Sequence) e o administrador do grupo — gravados junto com o grupo, num único save.
 */
@Route(value = "grupos/:id", layout = MainView.class)
@ViewController(id = "Grupo.detail")
@ViewDescriptor(path = "grupo-detail-view.xml")
@EditedEntityContainer("grupoDc")
public class GrupoDetailView extends StandardDetailView<Grupo> {

    @ViewComponent
    private JmixIntegerField codigoField;
    @ViewComponent
    private VerticalLayout novoGrupoBox;
    @ViewComponent
    private TypedTextField<String> empresaNomeField;
    @ViewComponent
    private TypedTextField<String> empresaApelidoField;
    @ViewComponent
    private TypedTextField<String> empresaCnpjField;
    @ViewComponent
    private TypedTextField<String> adminUsernameField;
    @ViewComponent
    private PasswordField adminSenhaField;
    @ViewComponent
    private PasswordField adminConfirmarSenhaField;
    @ViewComponent
    private MessageBundle messageBundle;
    @Autowired
    private GrupoService grupoService;
    @Autowired
    private EntityStates entityStates;
    @Autowired
    private DataManager dataManager;

    @Subscribe
    public void onInitEntity(final InitEntityEvent<Grupo> event) {
        codigoField.setReadOnly(false);
        novoGrupoBox.setVisible(true);
    }

    @Subscribe
    public void onValidation(final ValidationEvent event) {
        if (!entityStates.isNew(getEditedEntity())) {
            return;
        }
        Integer codigo = getEditedEntity().getCodigo();
        if (codigo != null && grupoService.codigoGrupoEmUso(codigo)) {
            event.getErrors().add(messageBundle.formatMessage("grupoDetailView.codigoEmUso", codigo));
        }
        if (Strings.isNullOrEmpty(empresaApelidoField.getValue())
                || Strings.isNullOrEmpty(empresaNomeField.getValue())
                || Strings.isNullOrEmpty(adminUsernameField.getValue())
                || Strings.isNullOrEmpty(adminSenhaField.getValue())) {
            event.getErrors().add(messageBundle.getMessage("grupoDetailView.camposObrigatorios"));
        }
        if (!Objects.equals(adminSenhaField.getValue(), adminConfirmarSenhaField.getValue())) {
            event.getErrors().add(messageBundle.getMessage("grupoDetailView.senhasDiferentes"));
        }
        String username = adminUsernameField.getValue();
        if (!Strings.isNullOrEmpty(username) && grupoService.usernameEmUso(username)) {
            event.getErrors().add(messageBundle.formatMessage("grupoDetailView.usernameEmUso", username));
        }
    }

    @Install(target = Target.DATA_CONTEXT)
    private Set<Object> saveDelegate(final SaveContext saveContext) {
        if (!entityStates.isNew(getEditedEntity())) {
            return dataManager.save(saveContext);
        }
        return grupoService.criarGrupo(saveContext, getEditedEntity(),
                new GrupoService.PrimeiraEmpresaEAdmin(
                        empresaNomeField.getValue(),
                        empresaApelidoField.getValue(),
                        Strings.emptyToNull(empresaCnpjField.getValue()),
                        adminUsernameField.getValue(),
                        adminSenhaField.getValue()));
    }
}
