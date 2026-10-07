package br.com.axialsoftware.axctg3.security;

import br.com.axialsoftware.axctg3.entity.User;
import br.com.axialsoftware.axctg3.entity.cadastros.Empresa;
import br.com.axialsoftware.axctg3.entity.cadastros.Grupo;
import io.jmix.security.model.RowLevelBiPredicate;
import io.jmix.security.model.RowLevelPolicyAction;
import io.jmix.security.role.annotation.JpqlRowLevelPolicy;
import io.jmix.security.role.annotation.PredicateRowLevelPolicy;
import io.jmix.security.role.annotation.RowLevelRole;
import io.jmix.securitydata.entity.RoleAssignmentEntity;
import org.springframework.context.ApplicationContext;

/**
 * Separa os grupos (clientes da Axial): cada usuário só enxerga as empresas, os usuários
 * e as atribuições de papel do próprio grupo. O grupo 0 (Axial) passa por todas as
 * condições e enxerga tudo.
 * <p>
 * Não é atribuído pela tela: {@link DatabaseUserRepository} acrescenta este papel a todo
 * usuário no login, para que nenhum usuário fique sem ele por esquecimento.
 * <p>
 * Os demais dados (lançamentos, títulos, notas...) já são separados pelo codEmpresa, que
 * é único no banco todo; o que vazava entre clientes era só a escolha da empresa — e
 * {@code UserEventListener} recusa um codEmpresa de empresa de outro grupo.
 */
@RowLevelRole(name = "Isolamento por grupo", code = IsolamentoGrupoRole.CODE)
public interface IsolamentoGrupoRole {

    String CODE = "isolamento-grupo";

    String DO_GRUPO_OU_AXIAL = " = :current_user_codGrupo or :current_user_codGrupo = 0)";

    @JpqlRowLevelPolicy(entityClass = Empresa.class, where = "({E}.codGrupo" + DO_GRUPO_OU_AXIAL)
    void empresa();

    @JpqlRowLevelPolicy(entityClass = User.class, where = "({E}.codGrupo" + DO_GRUPO_OU_AXIAL)
    void user();

    @JpqlRowLevelPolicy(entityClass = Grupo.class, where = "({E}.codigo" + DO_GRUPO_OU_AXIAL)
    void grupo();

    @JpqlRowLevelPolicy(entityClass = RoleAssignmentEntity.class,
            where = "({E}.username in (select u.username from User u where u.codGrupo = :current_user_codGrupo)"
                    + " or :current_user_codGrupo = 0)")
    void atribuicoesPapel();

    /**
     * Quem não é da Axial só atribui/retira papéis de {@link AdminGrupoRole#PAPEIS_ATRIBUIVEIS}
     * e só a usuários do próprio grupo — senão o admin do grupo se daria
     * {@code system-full-access} e enxergaria todos os clientes.
     */
    @PredicateRowLevelPolicy(entityClass = RoleAssignmentEntity.class,
            actions = {RowLevelPolicyAction.CREATE, RowLevelPolicyAction.UPDATE, RowLevelPolicyAction.DELETE})
    default RowLevelBiPredicate<RoleAssignmentEntity, ApplicationContext> alterarAtribuicoesPapel() {
        return (atribuicao, applicationContext) ->
                applicationContext.getBean(GrupoAcesso.class).podeAlterarAtribuicao(atribuicao);
    }
}
