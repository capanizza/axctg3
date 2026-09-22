package br.com.axialsoftware.axctg3.security;

import br.com.axialsoftware.axctg3.entity.cadastros.ConfigRel;
import br.com.axialsoftware.axctg3.entity.contabil.Bem;
import br.com.axialsoftware.axctg3.entity.contabil.ContaContabil;
import br.com.axialsoftware.axctg3.entity.contabil.Depreciacao;
import br.com.axialsoftware.axctg3.entity.contabil.HistoricoContabil;
import br.com.axialsoftware.axctg3.entity.contabil.Lancamento;
import br.com.axialsoftware.axctg3.entity.contabil.SaldoConta;
import io.jmix.security.model.EntityPolicyAction;
import io.jmix.security.role.annotation.EntityPolicy;
import io.jmix.security.role.annotation.ResourceRole;
import io.jmix.securityflowui.role.annotation.MenuPolicy;
import io.jmix.securityflowui.role.annotation.ViewPolicy;

/**
 * Tudo do {@link OperadorContabilRole} + excluir nas telas do módulo + as telas
 * supervisórias sem equivalente pro operador (encerramento de exercício, geração do
 * SPED ECD). O modelo de roles é aditivo/sem-deny (ver skill jmix-create-resource-role):
 * herdar via extends soma as policies do operador; as anotações abaixo só ACRESCENTAM
 * o que falta, não redeclaram o que já veio herdado.
 */
@ResourceRole(name = "Gerente Contábil", code = GerenteContabilRole.CODE)
public interface GerenteContabilRole extends OperadorContabilRole {

    String CODE = "gerente-contabil";

    @EntityPolicy(entityClass = Lancamento.class, actions = EntityPolicyAction.DELETE)
    @EntityPolicy(entityClass = ContaContabil.class, actions = EntityPolicyAction.DELETE)
    @EntityPolicy(entityClass = SaldoConta.class, actions = EntityPolicyAction.DELETE)
    @EntityPolicy(entityClass = Bem.class, actions = EntityPolicyAction.DELETE)
    @EntityPolicy(entityClass = Depreciacao.class, actions = EntityPolicyAction.DELETE)
    @EntityPolicy(entityClass = HistoricoContabil.class, actions = EntityPolicyAction.DELETE)
    @EntityPolicy(entityClass = ConfigRel.class, actions = EntityPolicyAction.DELETE)
    void excluirEntidadesModuloContabil();

    @ViewPolicy(viewIds = {
            "Associacao.list",
            "ContaReferencial.list",
    })
    @MenuPolicy(menuIds = {
            "Associacao.list",
            "ContaReferencial.list",
            "gerarSpedEcd",
            "lancarEncerramento",
            "criarProximoExercicio",
            "transferirSaldosProximoExercicio",
    })
    void telasSupervisorias();
}
