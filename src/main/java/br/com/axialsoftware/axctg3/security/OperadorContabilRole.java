package br.com.axialsoftware.axctg3.security;

import br.com.axialsoftware.axctg3.entity.User;
import br.com.axialsoftware.axctg3.entity.cadastros.CentroCusto;
import br.com.axialsoftware.axctg3.entity.cadastros.ConfigRel;
import br.com.axialsoftware.axctg3.entity.cadastros.Empresa;
import br.com.axialsoftware.axctg3.entity.cadastros.Parceiro;
import br.com.axialsoftware.axctg3.entity.contabil.Bem;
import br.com.axialsoftware.axctg3.entity.contabil.ContaContabil;
import br.com.axialsoftware.axctg3.entity.contabil.Depreciacao;
import br.com.axialsoftware.axctg3.entity.contabil.HistoricoContabil;
import br.com.axialsoftware.axctg3.entity.contabil.Lancamento;
import br.com.axialsoftware.axctg3.entity.contabil.SaldoConta;
import br.com.axialsoftware.axctg3.entity.tabelas.ContaReferencial;
import io.jmix.security.model.EntityAttributePolicyAction;
import io.jmix.security.model.EntityPolicyAction;
import io.jmix.security.role.annotation.EntityAttributePolicy;
import io.jmix.security.role.annotation.EntityPolicy;
import io.jmix.security.role.annotation.ResourceRole;
import io.jmix.securityflowui.role.annotation.MenuPolicy;
import io.jmix.securityflowui.role.annotation.ViewPolicy;

/**
 * Piloto do modelo função+módulo (ver memória do projeto sobre a decisão). Operador
 * contábil: CRUD normal das telas do módulo (sem excluir) e sem as telas supervisórias
 * de encerramento/SPED ECD, reservadas ao {@link GerenteContabilRole}. Botões de
 * negócio sem view própria (relatórios, cálculo de depreciação) ainda não são
 * restringíveis por policy — ficam liberados pra quem abre a tela, mesmo pro operador;
 * é pendência conhecida, não esquecimento.
 */
@ResourceRole(name = "Operador Contábil", code = OperadorContabilRole.CODE)
public interface OperadorContabilRole {

    String CODE = "operador-contabil";

    @EntityAttributePolicy(entityClass = Lancamento.class, attributes = "*", action = EntityAttributePolicyAction.MODIFY)
    @EntityPolicy(entityClass = Lancamento.class, actions = {EntityPolicyAction.READ, EntityPolicyAction.CREATE, EntityPolicyAction.UPDATE})
    void lancamentoEntity();

    @EntityAttributePolicy(entityClass = ContaContabil.class, attributes = "*", action = EntityAttributePolicyAction.MODIFY)
    @EntityPolicy(entityClass = ContaContabil.class, actions = {EntityPolicyAction.READ, EntityPolicyAction.CREATE, EntityPolicyAction.UPDATE})
    void contaContabilEntity();

    // Linhas de saldo mensal: nunca editadas em tela, mas gravadas via DataManager
    // sob a sessão do operador quando LancamentoEventListener.atualizarSaldos roda
    // durante o save de um Lancamento — sem UPDATE aqui, lançar entrada quebra.
    @EntityAttributePolicy(entityClass = SaldoConta.class, attributes = "*", action = EntityAttributePolicyAction.MODIFY)
    @EntityPolicy(entityClass = SaldoConta.class, actions = {EntityPolicyAction.READ, EntityPolicyAction.CREATE, EntityPolicyAction.UPDATE})
    void saldoContaEntity();

    @EntityAttributePolicy(entityClass = Bem.class, attributes = "*", action = EntityAttributePolicyAction.MODIFY)
    @EntityPolicy(entityClass = Bem.class, actions = {EntityPolicyAction.READ, EntityPolicyAction.CREATE, EntityPolicyAction.UPDATE})
    void bemEntity();

    // Grids de depreciacaos no Bem.detail são só leitura hoje (sem botão criar/editar),
    // mas DepreciacaoService grava via DataManager sob a sessão do operador quando o
    // botão "calcularAction" roda — sem CREATE/UPDATE aqui, o cálculo quebra.
    @EntityAttributePolicy(entityClass = Depreciacao.class, attributes = "*", action = EntityAttributePolicyAction.MODIFY)
    @EntityPolicy(entityClass = Depreciacao.class, actions = {EntityPolicyAction.READ, EntityPolicyAction.CREATE, EntityPolicyAction.UPDATE})
    void depreciacaoEntity();

    @EntityAttributePolicy(entityClass = HistoricoContabil.class, attributes = "*", action = EntityAttributePolicyAction.MODIFY)
    @EntityPolicy(entityClass = HistoricoContabil.class, actions = {EntityPolicyAction.READ, EntityPolicyAction.CREATE, EntityPolicyAction.UPDATE})
    void historicoContabilEntity();

    // ConfigRel guarda os últimos filtros de relatório por usuário (ver
    // UtilGeralService.prepararConfigRel) — precisa mesmo com os botões de relatório
    // ainda liberados sem checagem de role.
    @EntityAttributePolicy(entityClass = ConfigRel.class, attributes = "*", action = EntityAttributePolicyAction.MODIFY)
    @EntityPolicy(entityClass = ConfigRel.class, actions = {EntityPolicyAction.READ, EntityPolicyAction.CREATE, EntityPolicyAction.UPDATE})
    void configRelEntity();

    // PeriodoContabil.list grava ano/mês contábil no próprio User autenticado
    // (PeriodoContabilListView) — autoatendimento, não expõe outros campos do User.
    @EntityAttributePolicy(entityClass = User.class, attributes = {"anoContabil", "mesContabil"}, action = EntityAttributePolicyAction.MODIFY)
    @EntityPolicy(entityClass = User.class, actions = {EntityPolicyAction.READ, EntityPolicyAction.UPDATE})
    void userPeriodoEntity();

    // Somente leitura, pra listar as empresas em PeriodoContabil.list.
    @EntityAttributePolicy(entityClass = Empresa.class, attributes = "*", action = EntityAttributePolicyAction.VIEW)
    @EntityPolicy(entityClass = Empresa.class, actions = EntityPolicyAction.READ)
    void empresaEntity();

    // Referenciadas via entityComboBox (lookup), mas não geridas por este role.
    @EntityAttributePolicy(entityClass = CentroCusto.class, attributes = "*", action = EntityAttributePolicyAction.VIEW)
    @EntityPolicy(entityClass = CentroCusto.class, actions = EntityPolicyAction.READ)
    @EntityAttributePolicy(entityClass = Parceiro.class, attributes = "*", action = EntityAttributePolicyAction.VIEW)
    @EntityPolicy(entityClass = Parceiro.class, actions = EntityPolicyAction.READ)
    @EntityAttributePolicy(entityClass = ContaReferencial.class, attributes = "*", action = EntityAttributePolicyAction.VIEW)
    @EntityPolicy(entityClass = ContaReferencial.class, actions = EntityPolicyAction.READ)
    void referenciasLookupEntities();

    @ViewPolicy(viewIds = {
            "Lancamento.list",
            "Lancamento.detail",
            "ContaContabil.list",
            "ContaContabil.detail",
            "Bem.list",
            "Bem.detail",
            "Depreciacao.detail",
            "HistoricoContabil.list",
            "HistoricoContabil.detail",
            "PeriodoContabil.list",
    })
    @MenuPolicy(menuIds = {
            "Lancamento.list",
            "ContaContabil.list",
            "Bem.list",
            "HistoricoContabil.list",
            "PeriodoContabil.list",
    })
    void moduloContabilScreens();

    // Telas de outro módulo abertas só via lookup (entity_lookup dos combos acima) —
    // sem item de menu, o operador não navega direto pra elas.
    @ViewPolicy(viewIds = {
            "CentroCusto.list",
            "Parceiro.list",
            "ContaReferencial.list",
    })
    void referenciasLookupScreens();
}
