package br.com.axialsoftware.axctg3.security;

import br.com.axialsoftware.axctg3.entity.User;
import br.com.axialsoftware.axctg3.entity.cadastros.Empresa;
import br.com.axialsoftware.axctg3.entity.cadastros.Grupo;
import io.jmix.core.UnconstrainedDataManager;
import io.jmix.core.security.CurrentAuthentication;
import io.jmix.security.role.assignment.RoleAssignmentRoleType;
import io.jmix.securitydata.entity.RoleAssignmentEntity;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Regras de grupo que não cabem numa condição JPQL — usadas pelo predicado de
 * {@link IsolamentoGrupoRole} e pelos listeners de User/Empresa. As consultas aqui são
 * sem restrição de propósito: precisam enxergar o registro para decidir se ele é do grupo.
 */
@Component
public class GrupoAcesso {

    private final CurrentAuthentication currentAuthentication;
    private final UnconstrainedDataManager unconstrainedDataManager;

    public GrupoAcesso(CurrentAuthentication currentAuthentication,
                       UnconstrainedDataManager unconstrainedDataManager) {
        this.currentAuthentication = currentAuthentication;
        this.unconstrainedDataManager = unconstrainedDataManager;
    }

    /**
     * Grupo do usuário logado, ou {@code null} quando não há um {@link User} autenticado
     * (usuário "system" de rotinas internas).
     */
    @Nullable
    public Integer getCodGrupoUsuarioAtual() {
        if (currentAuthentication.isSet() && currentAuthentication.getUser() instanceof User user) {
            return user.getCodGrupo();
        }
        return null;
    }

    /** Usuário logado é da Axial (grupo 0) ou é uma rotina interna sem usuário. */
    public boolean usuarioAtualEnxergaTodosGrupos() {
        Integer codGrupo = getCodGrupoUsuarioAtual();
        return codGrupo == null || codGrupo == Grupo.CODIGO_AXIAL;
    }

    public boolean podeAlterarAtribuicao(RoleAssignmentEntity atribuicao) {
        if (usuarioAtualEnxergaTodosGrupos()) {
            return true;
        }
        return RoleAssignmentRoleType.RESOURCE.equals(atribuicao.getRoleType())
                && AdminGrupoRole.PAPEIS_ATRIBUIVEIS.contains(atribuicao.getRoleCode())
                && Objects.equals(getCodGrupoDoUsuario(atribuicao.getUsername()), getCodGrupoUsuarioAtual());
    }

    @Nullable
    public Integer getCodGrupoDoUsuario(@Nullable String username) {
        if (username == null) {
            return null;
        }
        return unconstrainedDataManager.load(User.class)
                .query("select u from User u where u.username = :username")
                .parameter("username", username)
                .optional()
                .map(User::getCodGrupo)
                .orElse(null);
    }

    @Nullable
    public Integer getCodGrupoDaEmpresa(Integer codEmpresa) {
        return unconstrainedDataManager.load(Empresa.class)
                .query("select e from Empresa e where e.codigo = :codigo")
                .parameter("codigo", codEmpresa)
                .optional()
                .map(Empresa::getCodGrupo)
                .orElse(null);
    }
}
