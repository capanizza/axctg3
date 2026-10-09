package br.com.axialsoftware.axctg3.security;

import br.com.axialsoftware.axctg3.entity.cadastros.ConfigRel;
import br.com.axialsoftware.axctg3.entity.fiscal.ItemNotaSaida;
import br.com.axialsoftware.axctg3.entity.fiscal.ItemPedidoVenda;
import br.com.axialsoftware.axctg3.entity.fiscal.NaturezaOperacao;
import br.com.axialsoftware.axctg3.entity.fiscal.Nfe;
import br.com.axialsoftware.axctg3.entity.fiscal.NfeCartaCorrecao;
import br.com.axialsoftware.axctg3.entity.fiscal.NfeDi;
import br.com.axialsoftware.axctg3.entity.fiscal.NfeDiAdicao;
import br.com.axialsoftware.axctg3.entity.fiscal.NfeDuplicata;
import br.com.axialsoftware.axctg3.entity.fiscal.NfeItem;
import br.com.axialsoftware.axctg3.entity.fiscal.NfePagamento;
import br.com.axialsoftware.axctg3.entity.fiscal.NfeVolume;
import br.com.axialsoftware.axctg3.entity.fiscal.NotaSaida;
import br.com.axialsoftware.axctg3.entity.fiscal.PedidoVenda;
import br.com.axialsoftware.axctg3.entity.fiscal.Produto;
import br.com.axialsoftware.axctg3.entity.fiscal.SaldoProduto;
import io.jmix.security.model.EntityAttributePolicyAction;
import io.jmix.security.model.EntityPolicyAction;
import io.jmix.security.role.annotation.EntityAttributePolicy;
import io.jmix.security.role.annotation.EntityPolicy;
import io.jmix.security.role.annotation.ResourceRole;

/**
 * Tudo do {@link OperadorFiscalRole} + excluir nas telas do módulo que realmente têm
 * botão de excluir. GrupoProduto e NfeInutilizacao ficam de fora de propósito — nenhum
 * dos dois tem ação de remover em tela nenhuma (ver comentários no operador), então
 * DELETE aqui não abriria nada, só sobraria como permissão morta.
 */
@ResourceRole(name = "Gerente Fiscal", code = GerenteFiscalRole.CODE)
public interface GerenteFiscalRole extends OperadorFiscalRole {

    String CODE = "gerente-fiscal";

    @EntityPolicy(entityClass = NaturezaOperacao.class, actions = EntityPolicyAction.DELETE)
    @EntityPolicy(entityClass = Produto.class, actions = EntityPolicyAction.DELETE)
    @EntityPolicy(entityClass = SaldoProduto.class, actions = EntityPolicyAction.DELETE)
    @EntityPolicy(entityClass = PedidoVenda.class, actions = EntityPolicyAction.DELETE)
    @EntityPolicy(entityClass = ItemPedidoVenda.class, actions = EntityPolicyAction.DELETE)
    @EntityPolicy(entityClass = NotaSaida.class, actions = EntityPolicyAction.DELETE)
    @EntityPolicy(entityClass = ItemNotaSaida.class, actions = EntityPolicyAction.DELETE)
    @EntityPolicy(entityClass = ConfigRel.class, actions = EntityPolicyAction.DELETE)
    void excluirEntidadesModuloFiscal();

    // Nfe.list tem removeAction de verdade; os filhos por composição têm
    // @OnDelete(CASCADE) no Nfe.java (e DI/adição, netos via NfeItem) — sem DELETE neles
    // também, excluir uma Nfe quebra no meio do cascade.
    @EntityPolicy(entityClass = Nfe.class, actions = EntityPolicyAction.DELETE)
    @EntityPolicy(entityClass = NfeItem.class, actions = EntityPolicyAction.DELETE)
    @EntityPolicy(entityClass = NfeDuplicata.class, actions = EntityPolicyAction.DELETE)
    @EntityPolicy(entityClass = NfePagamento.class, actions = EntityPolicyAction.DELETE)
    @EntityPolicy(entityClass = NfeVolume.class, actions = EntityPolicyAction.DELETE)
    @EntityPolicy(entityClass = NfeCartaCorrecao.class, actions = EntityPolicyAction.DELETE)
    @EntityPolicy(entityClass = NfeDi.class, actions = EntityPolicyAction.DELETE)
    @EntityPolicy(entityClass = NfeDiAdicao.class, actions = EntityPolicyAction.DELETE)
    void excluirNfeECascata();

    // NFe digitada (desde 2026-10-09): o rascunho abre editável em Nfe.detail só pra quem pode
    // excluir NFe — este papel e o admin (NfeDetailView.podeDigitar). O operador tem VIEW nos
    // atributos; aqui sobe pra MODIFY, senão os campos do rascunho ficariam travados também
    // pro gerente. Nota emitida/importada continua só leitura pela própria tela.
    @EntityAttributePolicy(entityClass = Nfe.class, attributes = "*", action = EntityAttributePolicyAction.MODIFY)
    @EntityAttributePolicy(entityClass = NfeItem.class, attributes = "*", action = EntityAttributePolicyAction.MODIFY)
    @EntityAttributePolicy(entityClass = NfeDi.class, attributes = "*", action = EntityAttributePolicyAction.MODIFY)
    @EntityAttributePolicy(entityClass = NfeDiAdicao.class, attributes = "*", action = EntityAttributePolicyAction.MODIFY)
    @EntityAttributePolicy(entityClass = NfeVolume.class, attributes = "*", action = EntityAttributePolicyAction.MODIFY)
    @EntityAttributePolicy(entityClass = NfeDuplicata.class, attributes = "*", action = EntityAttributePolicyAction.MODIFY)
    @EntityAttributePolicy(entityClass = NfePagamento.class, attributes = "*", action = EntityAttributePolicyAction.MODIFY)
    void digitarNfe();
}
