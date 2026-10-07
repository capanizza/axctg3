package br.com.axialsoftware.axctg3.administracao;

import br.com.axialsoftware.axctg3.Axctg3Application;
import br.com.axialsoftware.axctg3.entity.User;
import br.com.axialsoftware.axctg3.entity.cadastros.Empresa;
import br.com.axialsoftware.axctg3.entity.cadastros.Grupo;
import br.com.axialsoftware.axctg3.test_support.AdminUiTestAuthenticator;
import br.com.axialsoftware.axctg3.view.cadastros.empresa.EmpresaDetailView;
import br.com.axialsoftware.axctg3.view.cadastros.empresa.EmpresaListView;
import br.com.axialsoftware.axctg3.view.cadastros.empresa.SelecionarEmpresaListView;
import br.com.axialsoftware.axctg3.view.cadastros.grupo.GrupoDetailView;
import br.com.axialsoftware.axctg3.view.cadastros.grupo.GrupoListView;
import br.com.axialsoftware.axctg3.view.contabil.periodocontabil.PeriodoContabilListView;
import br.com.axialsoftware.axctg3.view.fiscal.periodofiscal.PeriodoFiscalListView;
import br.com.axialsoftware.axctg3.view.user.UserDetailView;
import br.com.axialsoftware.axctg3.view.user.UserListView;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.textfield.PasswordField;
import io.jmix.core.DataManager;
import io.jmix.core.SaveContext;
import io.jmix.flowui.ViewNavigators;
import io.jmix.flowui.component.textfield.JmixIntegerField;
import io.jmix.flowui.component.textfield.TypedTextField;
import io.jmix.flowui.kit.component.button.JmixButton;
import io.jmix.flowui.testassist.FlowuiTestAssistConfiguration;
import io.jmix.flowui.testassist.UiTest;
import io.jmix.flowui.testassist.UiTestUtils;
import io.jmix.securitydata.entity.RoleAssignmentEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Telas do grupo e as que mudaram com ele (coluna "Selecionada" sem propriedade, combo de
 * grupo) abrem sem erro; e o cadastro de um grupo novo pela tela grava junto a primeira
 * empresa e o administrador.
 */
@UiTest(authenticator = AdminUiTestAuthenticator.class)
@SpringBootTest(classes = {Axctg3Application.class, FlowuiTestAssistConfiguration.class})
@ActiveProfiles("test")
class GrupoUiTest {

    private static final int COD_GRUPO = 600_000 + (int) (System.currentTimeMillis() % 100_000);
    private static final String ADMIN = "admin_ui_" + COD_GRUPO;

    @Autowired
    DataManager dataManager;
    @Autowired
    ViewNavigators viewNavigators;

    @AfterEach
    void tearDown() {
        SaveContext remover = new SaveContext();
        dataManager.load(RoleAssignmentEntity.class)
                .query("select r from sec_RoleAssignmentEntity r where r.username = :username")
                .parameter("username", ADMIN)
                .list().forEach(remover::removing);
        dataManager.load(User.class)
                .query("select u from User u where u.username = :username")
                .parameter("username", ADMIN)
                .list().forEach(remover::removing);
        dataManager.load(Empresa.class)
                .query("select e from Empresa e where e.codGrupo = :grupo")
                .parameter("grupo", COD_GRUPO)
                .list().forEach(remover::removing);
        dataManager.load(Grupo.class)
                .query("select g from Grupo g where g.codigo = :grupo")
                .parameter("grupo", COD_GRUPO)
                .list().forEach(remover::removing);
        dataManager.save(remover);
    }

    @Test
    void telasAlteradasAbrem() {
        viewNavigators.view(UiTestUtils.getCurrentView(), GrupoListView.class).navigate();
        assertThat((Object) UiTestUtils.getCurrentView()).isInstanceOf(GrupoListView.class);
        viewNavigators.view(UiTestUtils.getCurrentView(), EmpresaListView.class).navigate();
        assertThat((Object) UiTestUtils.getCurrentView()).isInstanceOf(EmpresaListView.class);
        viewNavigators.view(UiTestUtils.getCurrentView(), SelecionarEmpresaListView.class).navigate();
        assertThat((Object) UiTestUtils.getCurrentView()).isInstanceOf(SelecionarEmpresaListView.class);
        viewNavigators.view(UiTestUtils.getCurrentView(), PeriodoContabilListView.class).navigate();
        assertThat((Object) UiTestUtils.getCurrentView()).isInstanceOf(PeriodoContabilListView.class);
        viewNavigators.view(UiTestUtils.getCurrentView(), PeriodoFiscalListView.class).navigate();
        assertThat((Object) UiTestUtils.getCurrentView()).isInstanceOf(PeriodoFiscalListView.class);
        viewNavigators.view(UiTestUtils.getCurrentView(), UserListView.class).navigate();
        assertThat((Object) UiTestUtils.getCurrentView()).isInstanceOf(UserListView.class);

        viewNavigators.detailView(UiTestUtils.getCurrentView(), Empresa.class)
                .newEntity().withViewClass(EmpresaDetailView.class).navigate();
        EmpresaDetailView empresaView = UiTestUtils.getCurrentView();
        ComboBox<Integer> grupoEmpresa = UiTestUtils.getComponent(empresaView, "codGrupoField");
        assertThat(grupoEmpresa.isVisible()).isTrue();          // admin é da Axial
        assertThat(grupoEmpresa.getValue()).isEqualTo(Grupo.CODIGO_AXIAL);

        viewNavigators.detailView(UiTestUtils.getCurrentView(), User.class)
                .newEntity().withViewClass(UserDetailView.class).navigate();
        UserDetailView userView = UiTestUtils.getCurrentView();
        ComboBox<Integer> grupoUsuario = UiTestUtils.getComponent(userView, "codGrupoField");
        assertThat(grupoUsuario.isVisible()).isTrue();
    }

    @Test
    void cadastrarGrupoPelaTela() {
        viewNavigators.detailView(UiTestUtils.getCurrentView(), Grupo.class)
                .newEntity().withViewClass(GrupoDetailView.class).navigate();
        GrupoDetailView view = UiTestUtils.getCurrentView();

        JmixIntegerField codigoField = UiTestUtils.getComponent(view, "codigoField");
        codigoField.setValue(COD_GRUPO);
        TypedTextField<String> nomeField = UiTestUtils.getComponent(view, "nomeField");
        nomeField.setValue("Cliente teste UI");
        TypedTextField<String> apelido = UiTestUtils.getComponent(view, "empresaApelidoField");
        apelido.setValue("CLIUI");
        TypedTextField<String> nomeEmpresa = UiTestUtils.getComponent(view, "empresaNomeField");
        nomeEmpresa.setValue("Cliente teste UI Ltda");
        TypedTextField<String> username = UiTestUtils.getComponent(view, "adminUsernameField");
        username.setValue(ADMIN);
        PasswordField senha = UiTestUtils.getComponent(view, "adminSenhaField");
        senha.setValue("senha123");
        PasswordField confirmar = UiTestUtils.getComponent(view, "adminConfirmarSenhaField");
        confirmar.setValue("senha123");

        JmixButton salvar = UiTestUtils.getComponent(view, "saveAndCloseButton");
        salvar.click();

        User admin = dataManager.load(User.class)
                .query("select u from User u where u.username = :username")
                .parameter("username", ADMIN)
                .one();
        assertThat(admin.getCodGrupo()).isEqualTo(COD_GRUPO);
        Empresa empresa = dataManager.load(Empresa.class)
                .query("select e from Empresa e where e.codigo = :codigo")
                .parameter("codigo", admin.getCodEmpresa())
                .one();
        assertThat(empresa.getCodGrupo()).isEqualTo(COD_GRUPO);
        assertThat(empresa.getApelido()).isEqualTo("CLIUI");
    }
}
