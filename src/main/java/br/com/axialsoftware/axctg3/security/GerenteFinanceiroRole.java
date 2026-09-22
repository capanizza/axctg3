package br.com.axialsoftware.axctg3.security;

import br.com.axialsoftware.axctg3.entity.cadastros.ConfigRel;
import br.com.axialsoftware.axctg3.entity.financeiro.Banco;
import br.com.axialsoftware.axctg3.entity.financeiro.DiversoPagar;
import br.com.axialsoftware.axctg3.entity.financeiro.HistoricoFinanceiro;
import br.com.axialsoftware.axctg3.entity.financeiro.ItemDiversoPagar;
import br.com.axialsoftware.axctg3.entity.financeiro.ItemMovimentoBanco;
import br.com.axialsoftware.axctg3.entity.financeiro.ItemPagar;
import br.com.axialsoftware.axctg3.entity.financeiro.ItemReceber;
import br.com.axialsoftware.axctg3.entity.financeiro.MovimentoBanco;
import br.com.axialsoftware.axctg3.entity.financeiro.RemessaBanco;
import br.com.axialsoftware.axctg3.entity.financeiro.RetornoBanco;
import br.com.axialsoftware.axctg3.entity.financeiro.TituloPagar;
import br.com.axialsoftware.axctg3.entity.financeiro.TituloReceber;
import io.jmix.security.model.EntityPolicyAction;
import io.jmix.security.role.annotation.EntityPolicy;
import io.jmix.security.role.annotation.ResourceRole;

/**
 * Tudo do {@link OperadorFinanceiroRole} + excluir nas telas do módulo. Sem tela
 * supervisória própria hoje no financeiro (ao contrário do contábil, que tem
 * encerramento/SPED ECD) — a diferenciação real do gerente aqui é só o excluir; emitir
 * boleto/gerar remessa ficam pra uma 2ª passada junto com a checagem de botões (ver
 * {@link OperadorFinanceiroRole}).
 */
@ResourceRole(name = "Gerente Financeiro", code = GerenteFinanceiroRole.CODE)
public interface GerenteFinanceiroRole extends OperadorFinanceiroRole {

    String CODE = "gerente-financeiro";

    @EntityPolicy(entityClass = Banco.class, actions = EntityPolicyAction.DELETE)
    @EntityPolicy(entityClass = HistoricoFinanceiro.class, actions = EntityPolicyAction.DELETE)
    @EntityPolicy(entityClass = DiversoPagar.class, actions = EntityPolicyAction.DELETE)
    @EntityPolicy(entityClass = ItemDiversoPagar.class, actions = EntityPolicyAction.DELETE)
    @EntityPolicy(entityClass = TituloPagar.class, actions = EntityPolicyAction.DELETE)
    @EntityPolicy(entityClass = ItemPagar.class, actions = EntityPolicyAction.DELETE)
    @EntityPolicy(entityClass = TituloReceber.class, actions = EntityPolicyAction.DELETE)
    @EntityPolicy(entityClass = ItemReceber.class, actions = EntityPolicyAction.DELETE)
    @EntityPolicy(entityClass = MovimentoBanco.class, actions = EntityPolicyAction.DELETE)
    @EntityPolicy(entityClass = ItemMovimentoBanco.class, actions = EntityPolicyAction.DELETE)
    @EntityPolicy(entityClass = RemessaBanco.class, actions = EntityPolicyAction.DELETE)
    @EntityPolicy(entityClass = RetornoBanco.class, actions = EntityPolicyAction.DELETE)
    @EntityPolicy(entityClass = ConfigRel.class, actions = EntityPolicyAction.DELETE)
    void excluirEntidadesModuloFinanceiro();
}
