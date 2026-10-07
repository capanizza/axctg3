package br.com.axialsoftware.axctg3.administracao;

import br.com.axialsoftware.axctg3.entity.User;
import br.com.axialsoftware.axctg3.entity.cadastros.Empresa;
import br.com.axialsoftware.axctg3.entity.cadastros.Grupo;
import br.com.axialsoftware.axctg3.security.FullAccessRole;
import br.com.axialsoftware.axctg3.security.OperadorContabilRole;
import br.com.axialsoftware.axctg3.service.cadastros.GrupoService;
import br.com.axialsoftware.axctg3.test_support.AuthenticatedAsAdmin;
import io.jmix.core.DataManager;
import io.jmix.core.SaveContext;
import io.jmix.core.security.SystemAuthenticator;
import io.jmix.security.role.assignment.RoleAssignmentRoleType;
import io.jmix.securitydata.entity.RoleAssignmentEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Grupo (cliente da Axial) acima da empresa: cria dois grupos pelo GrupoService e confere,
 * logado como o administrador de um deles, que ele só enxerga o próprio grupo, não
 * consegue se dar system-full-access e não aponta usuário para empresa do outro grupo.
 */
@SpringBootTest
@ActiveProfiles("test")
@ExtendWith(AuthenticatedAsAdmin.class)
class GrupoIsolamentoTest {

    // Código e usernames variam por execução: o HSQLDB de teste é persistente e não tem
    // índice único parcial (soft delete), então um valor fixo colidiria na rodada seguinte.
    private static final int BASE = 700_000 + (int) (System.currentTimeMillis() % 100_000) * 2;

    @Autowired
    DataManager dataManager;
    @Autowired
    GrupoService grupoService;
    @Autowired
    SystemAuthenticator systemAuthenticator;

    private Grupo grupoA;
    private Grupo grupoB;
    private String adminA;
    private String adminB;

    @BeforeEach
    void setUp() {
        adminA = "admin_grupo_" + BASE;
        adminB = "admin_grupo_" + (BASE + 1);
        grupoA = criarGrupo(BASE, adminA);
        grupoB = criarGrupo(BASE + 1, adminB);
    }

    @AfterEach
    void tearDown() {
        List<String> usernames = dataManager.load(User.class)
                .query("select u from User u where u.codGrupo in :grupos")
                .parameter("grupos", List.of(grupoA.getCodigo(), grupoB.getCodigo()))
                .list().stream().map(User::getUsername).toList();
        SaveContext remover = new SaveContext();
        dataManager.load(RoleAssignmentEntity.class)
                .query("select r from sec_RoleAssignmentEntity r where r.username in :usernames")
                .parameter("usernames", usernames)
                .list().forEach(remover::removing);
        dataManager.load(User.class)
                .query("select u from User u where u.username in :usernames")
                .parameter("usernames", usernames)
                .list().forEach(remover::removing);
        dataManager.load(Empresa.class)
                .query("select e from Empresa e where e.codGrupo in :grupos")
                .parameter("grupos", List.of(grupoA.getCodigo(), grupoB.getCodigo()))
                .list().forEach(remover::removing);
        dataManager.load(Grupo.class)
                .query("select g from Grupo g where g.codigo in :grupos")
                .parameter("grupos", List.of(grupoA.getCodigo(), grupoB.getCodigo()))
                .list().forEach(remover::removing);
        dataManager.save(remover);
    }

    @Test
    void criarGrupoGravaPrimeiraEmpresaEAdministrador() {
        User admin = carregarUsuario(adminA);
        assertThat(admin.getCodGrupo()).isEqualTo(grupoA.getCodigo());
        assertThat(admin.getPasswordChangeRequired()).isTrue();

        Empresa empresa = dataManager.load(Empresa.class)
                .query("select e from Empresa e where e.codigo = :codigo")
                .parameter("codigo", admin.getCodEmpresa())
                .one();
        assertThat(empresa.getCodGrupo()).isEqualTo(grupoA.getCodigo());
        assertThat(empresa.getCodigo()).isPositive();

        List<String> papeis = dataManager.load(RoleAssignmentEntity.class)
                .query("select r from sec_RoleAssignmentEntity r where r.username = :username")
                .parameter("username", adminA)
                .list().stream().map(RoleAssignmentEntity::getRoleCode).toList();
        assertThat(papeis).contains("admin-grupo", "ui-minimal").doesNotContain(FullAccessRole.CODE);
    }

    @Test
    void administradorDoGrupoSoEnxergaOProprioGrupo() {
        systemAuthenticator.withUser(adminA, () -> {
            List<Empresa> empresas = dataManager.load(Empresa.class).all().list();
            assertThat(empresas).isNotEmpty()
                    .allSatisfy(e -> assertThat(e.getCodGrupo()).isEqualTo(grupoA.getCodigo()));

            List<User> usuarios = dataManager.load(User.class).all().list();
            assertThat(usuarios).extracting(User::getUsername).containsExactly(adminA);

            // AdminGrupoRole não dá acesso à entidade Grupo — cadastro de grupos é da Axial
            assertThat(dataManager.load(Grupo.class).all().list()).isEmpty();
            return null;
        });
    }

    @Test
    void administradorDoGrupoNaoSeDaFullAccess() {
        systemAuthenticator.withUser(adminA, () -> {
            assertThatThrownBy(() -> dataManager.save(atribuicao(adminA, FullAccessRole.CODE)))
                    .isInstanceOf(RuntimeException.class);
            // papel operacional ao usuário do próprio grupo passa
            dataManager.save(atribuicao(adminA, OperadorContabilRole.CODE));
            // e nada a usuário de outro grupo
            assertThatThrownBy(() -> dataManager.save(atribuicao(adminB, OperadorContabilRole.CODE)))
                    .isInstanceOf(RuntimeException.class);
            return null;
        });
    }

    @Test
    void empresaCriadaPeloAdministradorFicaNoGrupoDele() {
        Empresa salva = systemAuthenticator.withUser(adminA, () -> {
            Empresa empresa = dataManager.create(Empresa.class);
            empresa.setNome("Empresa nova do grupo A");
            empresa.setApelido("NOVA A");
            empresa.setCodGrupo(grupoB.getCodigo()); // tentativa de gravar no grupo B
            return dataManager.save(empresa);
        });
        assertThat(salva.getCodGrupo()).isEqualTo(grupoA.getCodigo());
        assertThat(salva.getCodigo()).isPositive();
    }

    @Test
    void usuarioNaoApontaParaEmpresaDeOutroGrupo() {
        Integer empresaDoGrupoB = carregarUsuario(adminB).getCodEmpresa();
        User usuarioA = carregarUsuario(adminA);
        usuarioA.setCodEmpresa(empresaDoGrupoB);
        assertThatThrownBy(() -> dataManager.save(usuarioA))
                .hasStackTraceContaining("não pertence ao grupo");
    }

    private Grupo criarGrupo(int codigo, String usernameAdmin) {
        Grupo grupo = dataManager.create(Grupo.class);
        grupo.setCodigo(codigo);
        grupo.setNome("Grupo teste " + codigo);
        grupoService.criarGrupo(new SaveContext(), grupo,
                new GrupoService.PrimeiraEmpresaEAdmin("Empresa do grupo " + codigo, "G" + codigo,
                        null, usernameAdmin, "senha123"));
        return grupo;
    }

    private User carregarUsuario(String username) {
        return dataManager.load(User.class)
                .query("select u from User u where u.username = :username")
                .parameter("username", username)
                .one();
    }

    private RoleAssignmentEntity atribuicao(String username, String papel) {
        RoleAssignmentEntity atribuicao = dataManager.create(RoleAssignmentEntity.class);
        atribuicao.setUsername(username);
        atribuicao.setRoleCode(papel);
        atribuicao.setRoleType(RoleAssignmentRoleType.RESOURCE);
        return atribuicao;
    }
}
