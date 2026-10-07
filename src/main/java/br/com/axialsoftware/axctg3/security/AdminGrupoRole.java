package br.com.axialsoftware.axctg3.security;

import br.com.axialsoftware.axctg3.entity.User;
import br.com.axialsoftware.axctg3.entity.cadastros.Empresa;
import br.com.axialsoftware.axctg3.entity.contabil.ContaContabil;
import br.com.axialsoftware.axctg3.entity.contabil.HistoricoContabil;
import br.com.axialsoftware.axctg3.entity.financeiro.HistoricoFinanceiro;
import br.com.axialsoftware.axctg3.entity.fiscal.Produto;
import br.com.axialsoftware.axctg3.entity.tabelas.Municipio;
import br.com.axialsoftware.axctg3.entity.tabelas.TipoLogradouro;
import io.jmix.security.model.EntityAttributePolicyAction;
import io.jmix.security.model.EntityPolicyAction;
import io.jmix.security.model.ResourceRoleModel;
import io.jmix.security.model.RowLevelRoleModel;
import io.jmix.security.role.annotation.EntityAttributePolicy;
import io.jmix.security.role.annotation.EntityPolicy;
import io.jmix.security.role.annotation.ResourceRole;
import io.jmix.securitydata.entity.RoleAssignmentEntity;
import io.jmix.securityflowui.role.annotation.MenuPolicy;
import io.jmix.securityflowui.role.annotation.ViewPolicy;

import java.util.Set;

/**
 * Administrador de um grupo (cliente da Axial): cadastra os usuários e as empresas do
 * próprio grupo e atribui a eles os papéis operacionais. Quais registros ele enxerga é
 * decidido por {@link IsolamentoGrupoRole}; o codGrupo do que ele cria é forçado pelos
 * listeners de User/Empresa. Não exclui empresas — isso fica com a Axial.
 */
@ResourceRole(name = "Administrador do grupo", code = AdminGrupoRole.CODE)
public interface AdminGrupoRole {

    String CODE = "admin-grupo";

    /** Papéis que quem não é da Axial pode atribuir — ver IsolamentoGrupoRole. */
    Set<String> PAPEIS_ATRIBUIVEIS = Set.of(
            CODE,
            UiMinimalRole.CODE,
            OperadorCadastrosRole.CODE, GerenteCadastrosRole.CODE,
            OperadorContabilRole.CODE, GerenteContabilRole.CODE,
            OperadorFinanceiroRole.CODE, GerenteFinanceiroRole.CODE,
            OperadorFiscalRole.CODE, GerenteFiscalRole.CODE);

    @EntityAttributePolicy(entityClass = User.class, attributes = "*", action = EntityAttributePolicyAction.MODIFY)
    @EntityPolicy(entityClass = User.class, actions = EntityPolicyAction.ALL)
    @EntityAttributePolicy(entityClass = RoleAssignmentEntity.class, attributes = "*", action = EntityAttributePolicyAction.MODIFY)
    @EntityPolicy(entityClass = RoleAssignmentEntity.class, actions = EntityPolicyAction.ALL)
    // grids dos lookups de papéis (sem leitura nessas entidades a lista abre em branco);
    // quais papéis aparecem é filtrado por PapeisAtribuiveisCandidatePredicate
    @EntityAttributePolicy(entityClass = ResourceRoleModel.class, attributes = "*", action = EntityAttributePolicyAction.VIEW)
    @EntityPolicy(entityClass = ResourceRoleModel.class, actions = EntityPolicyAction.READ)
    @EntityAttributePolicy(entityClass = RowLevelRoleModel.class, attributes = "*", action = EntityAttributePolicyAction.VIEW)
    @EntityPolicy(entityClass = RowLevelRoleModel.class, actions = EntityPolicyAction.READ)
    void usuarios();

    @EntityAttributePolicy(entityClass = Empresa.class, attributes = "*", action = EntityAttributePolicyAction.MODIFY)
    @EntityPolicy(entityClass = Empresa.class, actions = {EntityPolicyAction.READ, EntityPolicyAction.CREATE, EntityPolicyAction.UPDATE})
    void empresas();

    // Referenciadas pelos combos da tela de empresa.
    @EntityAttributePolicy(entityClass = TipoLogradouro.class, attributes = "*", action = EntityAttributePolicyAction.VIEW)
    @EntityPolicy(entityClass = TipoLogradouro.class, actions = EntityPolicyAction.READ)
    @EntityAttributePolicy(entityClass = Municipio.class, attributes = "*", action = EntityAttributePolicyAction.VIEW)
    @EntityPolicy(entityClass = Municipio.class, actions = EntityPolicyAction.READ)
    @EntityAttributePolicy(entityClass = ContaContabil.class, attributes = "*", action = EntityAttributePolicyAction.VIEW)
    @EntityPolicy(entityClass = ContaContabil.class, actions = EntityPolicyAction.READ)
    @EntityAttributePolicy(entityClass = HistoricoContabil.class, attributes = "*", action = EntityAttributePolicyAction.VIEW)
    @EntityPolicy(entityClass = HistoricoContabil.class, actions = EntityPolicyAction.READ)
    @EntityAttributePolicy(entityClass = HistoricoFinanceiro.class, attributes = "*", action = EntityAttributePolicyAction.VIEW)
    @EntityPolicy(entityClass = HistoricoFinanceiro.class, actions = EntityPolicyAction.READ)
    @EntityAttributePolicy(entityClass = Produto.class, attributes = "*", action = EntityAttributePolicyAction.VIEW)
    @EntityPolicy(entityClass = Produto.class, actions = EntityPolicyAction.READ)
    void referenciasEmpresa();

    @ViewPolicy(viewIds = {
            "User.list", "User.detail",
            "Empresa.list", "Empresa.detail",
            "roleAssignmentView", "sec_ResourceRoleModel.lookup", "sec_RowLevelRoleModel.lookup",
            "changePasswordView", "resetPasswordView",
            // o admin criado junto com o grupo entra com troca de senha obrigatória
            "sec_PasswordChangeRequiredView"})
    @MenuPolicy(menuIds = {"User.list", "Empresa.list"})
    void telas();
}
