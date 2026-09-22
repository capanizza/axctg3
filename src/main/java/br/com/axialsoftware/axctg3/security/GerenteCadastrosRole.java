package br.com.axialsoftware.axctg3.security;

import br.com.axialsoftware.axctg3.entity.cadastros.CentroCusto;
import br.com.axialsoftware.axctg3.entity.cadastros.CondicaoPagamento;
import br.com.axialsoftware.axctg3.entity.cadastros.Mensagem;
import br.com.axialsoftware.axctg3.entity.cadastros.Parceiro;
import br.com.axialsoftware.axctg3.entity.cadastros.Transportadora;
import br.com.axialsoftware.axctg3.entity.cadastros.Vendedor;
import io.jmix.security.model.EntityPolicyAction;
import io.jmix.security.role.annotation.EntityPolicy;
import io.jmix.security.role.annotation.ResourceRole;

/**
 * Tudo do {@link OperadorCadastrosRole} + excluir nas telas do módulo.
 */
@ResourceRole(name = "Gerente Cadastros", code = GerenteCadastrosRole.CODE)
public interface GerenteCadastrosRole extends OperadorCadastrosRole {

    String CODE = "gerente-cadastros";

    @EntityPolicy(entityClass = CentroCusto.class, actions = EntityPolicyAction.DELETE)
    @EntityPolicy(entityClass = Parceiro.class, actions = EntityPolicyAction.DELETE)
    @EntityPolicy(entityClass = CondicaoPagamento.class, actions = EntityPolicyAction.DELETE)
    @EntityPolicy(entityClass = Vendedor.class, actions = EntityPolicyAction.DELETE)
    @EntityPolicy(entityClass = Transportadora.class, actions = EntityPolicyAction.DELETE)
    @EntityPolicy(entityClass = Mensagem.class, actions = EntityPolicyAction.DELETE)
    void excluirEntidadesModuloCadastros();
}
