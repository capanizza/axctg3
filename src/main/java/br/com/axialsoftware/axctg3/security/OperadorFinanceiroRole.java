package br.com.axialsoftware.axctg3.security;

import br.com.axialsoftware.axctg3.entity.cadastros.ConfigRel;
import br.com.axialsoftware.axctg3.entity.cadastros.Parceiro;
import br.com.axialsoftware.axctg3.entity.contabil.ContaContabil;
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
import io.jmix.security.model.EntityAttributePolicyAction;
import io.jmix.security.model.EntityPolicyAction;
import io.jmix.security.role.annotation.EntityAttributePolicy;
import io.jmix.security.role.annotation.EntityPolicy;
import io.jmix.security.role.annotation.ResourceRole;
import io.jmix.securityflowui.role.annotation.MenuPolicy;
import io.jmix.securityflowui.role.annotation.ViewPolicy;

/**
 * Piloto do modelo função+módulo (ver memória do projeto). Operador financeiro: CRUD
 * normal das telas do módulo (sem excluir). RemessaBanco/RetornoBanco entram com
 * CREATE+UPDATE porque "gerar remessa"/"ler retorno" continuam sem checagem de role
 * própria (mesma pendência conhecida do {@link OperadorContabilRole}) — sem essas
 * ações, os botões gerarRemessaAction/lerRetornoAction quebrariam com
 * AccessDeniedException pro operador mesmo estando visíveis pra ele.
 */
@ResourceRole(name = "Operador Financeiro", code = OperadorFinanceiroRole.CODE)
public interface OperadorFinanceiroRole {

    String CODE = "operador-financeiro";

    @EntityAttributePolicy(entityClass = Banco.class, attributes = "*", action = EntityAttributePolicyAction.MODIFY)
    @EntityPolicy(entityClass = Banco.class, actions = {EntityPolicyAction.READ, EntityPolicyAction.CREATE, EntityPolicyAction.UPDATE})
    void bancoEntity();

    @EntityAttributePolicy(entityClass = HistoricoFinanceiro.class, attributes = "*", action = EntityAttributePolicyAction.MODIFY)
    @EntityPolicy(entityClass = HistoricoFinanceiro.class, actions = {EntityPolicyAction.READ, EntityPolicyAction.CREATE, EntityPolicyAction.UPDATE})
    void historicoFinanceiroEntity();

    @EntityAttributePolicy(entityClass = DiversoPagar.class, attributes = "*", action = EntityAttributePolicyAction.MODIFY)
    @EntityPolicy(entityClass = DiversoPagar.class, actions = {EntityPolicyAction.READ, EntityPolicyAction.CREATE, EntityPolicyAction.UPDATE})
    @EntityAttributePolicy(entityClass = ItemDiversoPagar.class, attributes = "*", action = EntityAttributePolicyAction.MODIFY)
    @EntityPolicy(entityClass = ItemDiversoPagar.class, actions = {EntityPolicyAction.READ, EntityPolicyAction.CREATE, EntityPolicyAction.UPDATE})
    void diversoPagarEntity();

    @EntityAttributePolicy(entityClass = TituloPagar.class, attributes = "*", action = EntityAttributePolicyAction.MODIFY)
    @EntityPolicy(entityClass = TituloPagar.class, actions = {EntityPolicyAction.READ, EntityPolicyAction.CREATE, EntityPolicyAction.UPDATE})
    @EntityAttributePolicy(entityClass = ItemPagar.class, attributes = "*", action = EntityAttributePolicyAction.MODIFY)
    @EntityPolicy(entityClass = ItemPagar.class, actions = {EntityPolicyAction.READ, EntityPolicyAction.CREATE, EntityPolicyAction.UPDATE})
    void tituloPagarEntity();

    @EntityAttributePolicy(entityClass = TituloReceber.class, attributes = "*", action = EntityAttributePolicyAction.MODIFY)
    @EntityPolicy(entityClass = TituloReceber.class, actions = {EntityPolicyAction.READ, EntityPolicyAction.CREATE, EntityPolicyAction.UPDATE})
    @EntityAttributePolicy(entityClass = ItemReceber.class, attributes = "*", action = EntityAttributePolicyAction.MODIFY)
    @EntityPolicy(entityClass = ItemReceber.class, actions = {EntityPolicyAction.READ, EntityPolicyAction.CREATE, EntityPolicyAction.UPDATE})
    void tituloReceberEntity();

    @EntityAttributePolicy(entityClass = MovimentoBanco.class, attributes = "*", action = EntityAttributePolicyAction.MODIFY)
    @EntityPolicy(entityClass = MovimentoBanco.class, actions = {EntityPolicyAction.READ, EntityPolicyAction.CREATE, EntityPolicyAction.UPDATE})
    @EntityAttributePolicy(entityClass = ItemMovimentoBanco.class, attributes = "*", action = EntityAttributePolicyAction.MODIFY)
    @EntityPolicy(entityClass = ItemMovimentoBanco.class, actions = {EntityPolicyAction.READ, EntityPolicyAction.CREATE, EntityPolicyAction.UPDATE})
    void movimentoBancoEntity();

    @EntityAttributePolicy(entityClass = RemessaBanco.class, attributes = "*", action = EntityAttributePolicyAction.MODIFY)
    @EntityPolicy(entityClass = RemessaBanco.class, actions = {EntityPolicyAction.READ, EntityPolicyAction.CREATE, EntityPolicyAction.UPDATE})
    @EntityAttributePolicy(entityClass = RetornoBanco.class, attributes = "*", action = EntityAttributePolicyAction.MODIFY)
    @EntityPolicy(entityClass = RetornoBanco.class, actions = {EntityPolicyAction.READ, EntityPolicyAction.CREATE, EntityPolicyAction.UPDATE})
    void remessaRetornoEntity();

    // ConfigRel guarda os últimos filtros de relatório por usuário (ver
    // UtilGeralService.prepararConfigRel) — precisa mesmo com os botões de relatório
    // ainda liberados sem checagem de role.
    @EntityAttributePolicy(entityClass = ConfigRel.class, attributes = "*", action = EntityAttributePolicyAction.MODIFY)
    @EntityPolicy(entityClass = ConfigRel.class, actions = {EntityPolicyAction.READ, EntityPolicyAction.CREATE, EntityPolicyAction.UPDATE})
    void configRelEntity();

    // Referenciadas via entityComboBox (lookup), mas não geridas por este role.
    @EntityAttributePolicy(entityClass = Parceiro.class, attributes = "*", action = EntityAttributePolicyAction.VIEW)
    @EntityPolicy(entityClass = Parceiro.class, actions = EntityPolicyAction.READ)
    @EntityAttributePolicy(entityClass = ContaContabil.class, attributes = "*", action = EntityAttributePolicyAction.VIEW)
    @EntityPolicy(entityClass = ContaContabil.class, actions = EntityPolicyAction.READ)
    void referenciasLookupEntities();

    @ViewPolicy(viewIds = {
            "Banco.list",
            "Banco.detail",
            "HistoricoFinanceiro.list",
            "HistoricoFinanceiro.detail",
            "DiversoPagar.list",
            "DiversoPagar.detail",
            "BaixaDiversoPagar.list",
            "BaixaDiversoPagar.detail",
            "ItemDiversoPagar.detail",
            "TituloPagar.list",
            "TituloPagar.detail",
            "BaixaTituloPagar.list",
            "BaixaTituloPagar.detail",
            "ItemPagar.detail",
            "TituloReceber.list",
            "TituloReceber.detail",
            "BaixaTituloReceber.list",
            "BaixaTituloReceber.detail",
            "ItemReceber.detail",
            "MovimentoBanco.list",
            "MovimentoBanco.detail",
            "RemessaBanco.list",
            "RetornoBanco.list",
            "Nfcom.import",
            "RetornoBanco.import",
    })
    @MenuPolicy(menuIds = {
            "Banco.list",
            "HistoricoFinanceiro.list",
            "DiversoPagar.list",
            "BaixaDiversoPagar.list",
            "TituloReceber.list",
            "BaixaTituloReceber.list",
            "TituloPagar.list",
            "BaixaTituloPagar.list",
            "MovimentoBanco.list",
    })
    void moduloFinanceiroScreens();

    // Tela de outro módulo aberta só via lookup do combo de conta contábil — sem item
    // de menu, o operador financeiro não navega direto pra ela.
    @ViewPolicy(viewIds = {"ContaContabil.list", "Parceiro.list"})
    void referenciasLookupScreens();
}
