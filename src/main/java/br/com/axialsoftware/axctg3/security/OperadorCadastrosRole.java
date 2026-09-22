package br.com.axialsoftware.axctg3.security;

import br.com.axialsoftware.axctg3.entity.User;
import br.com.axialsoftware.axctg3.entity.cadastros.CentroCusto;
import br.com.axialsoftware.axctg3.entity.cadastros.CondicaoPagamento;
import br.com.axialsoftware.axctg3.entity.cadastros.Empresa;
import br.com.axialsoftware.axctg3.entity.cadastros.Mensagem;
import br.com.axialsoftware.axctg3.entity.cadastros.Parceiro;
import br.com.axialsoftware.axctg3.entity.cadastros.Transportadora;
import br.com.axialsoftware.axctg3.entity.cadastros.Vendedor;
import br.com.axialsoftware.axctg3.entity.tabelas.Municipio;
import br.com.axialsoftware.axctg3.entity.tabelas.TipoLogradouro;
import io.jmix.security.model.EntityAttributePolicyAction;
import io.jmix.security.model.EntityPolicyAction;
import io.jmix.security.role.annotation.EntityAttributePolicy;
import io.jmix.security.role.annotation.EntityPolicy;
import io.jmix.security.role.annotation.ResourceRole;
import io.jmix.securityflowui.role.annotation.MenuPolicy;
import io.jmix.securityflowui.role.annotation.ViewPolicy;

/**
 * Piloto do modelo função+módulo (ver memória do projeto). Operador cadastros: CRUD
 * normal das telas do módulo (sem excluir). Módulo mais simples que
 * contábil/financeiro/fiscal — sem composition children, sem side-effect em outra
 * entidade de outro módulo (é sempre o outro módulo que referencia cadastros, nunca o
 * contrário), sem tela supervisória própria.
 */
@ResourceRole(name = "Operador Cadastros", code = OperadorCadastrosRole.CODE)
public interface OperadorCadastrosRole {

    String CODE = "operador-cadastros";

    @EntityAttributePolicy(entityClass = CentroCusto.class, attributes = "*", action = EntityAttributePolicyAction.MODIFY)
    @EntityPolicy(entityClass = CentroCusto.class, actions = {EntityPolicyAction.READ, EntityPolicyAction.CREATE, EntityPolicyAction.UPDATE})
    @EntityAttributePolicy(entityClass = Parceiro.class, attributes = "*", action = EntityAttributePolicyAction.MODIFY)
    @EntityPolicy(entityClass = Parceiro.class, actions = {EntityPolicyAction.READ, EntityPolicyAction.CREATE, EntityPolicyAction.UPDATE})
    @EntityAttributePolicy(entityClass = CondicaoPagamento.class, attributes = "*", action = EntityAttributePolicyAction.MODIFY)
    @EntityPolicy(entityClass = CondicaoPagamento.class, actions = {EntityPolicyAction.READ, EntityPolicyAction.CREATE, EntityPolicyAction.UPDATE})
    @EntityAttributePolicy(entityClass = Vendedor.class, attributes = "*", action = EntityAttributePolicyAction.MODIFY)
    @EntityPolicy(entityClass = Vendedor.class, actions = {EntityPolicyAction.READ, EntityPolicyAction.CREATE, EntityPolicyAction.UPDATE})
    @EntityAttributePolicy(entityClass = Transportadora.class, attributes = "*", action = EntityAttributePolicyAction.MODIFY)
    @EntityPolicy(entityClass = Transportadora.class, actions = {EntityPolicyAction.READ, EntityPolicyAction.CREATE, EntityPolicyAction.UPDATE})
    @EntityAttributePolicy(entityClass = Mensagem.class, attributes = "*", action = EntityAttributePolicyAction.MODIFY)
    @EntityPolicy(entityClass = Mensagem.class, actions = {EntityPolicyAction.READ, EntityPolicyAction.CREATE, EntityPolicyAction.UPDATE})
    void moduloCadastrosEntities();

    // Referenciadas via entityComboBox (endereço do Parceiro), mas não geridas por
    // este role.
    @EntityAttributePolicy(entityClass = TipoLogradouro.class, attributes = "*", action = EntityAttributePolicyAction.VIEW)
    @EntityPolicy(entityClass = TipoLogradouro.class, actions = EntityPolicyAction.READ)
    @EntityAttributePolicy(entityClass = Municipio.class, attributes = "*", action = EntityAttributePolicyAction.VIEW)
    @EntityPolicy(entityClass = Municipio.class, actions = EntityPolicyAction.READ)
    void referenciasLookupEntities();

    // SelecionarEmpresa.list grava a empresa corrente no próprio User autenticado
    // (User.codEmpresa) e marca qual Empresa é "selecionada" — autoatendimento, não
    // expõe outros campos. Mesmo grant repetido nos outros 3 roles de módulo.
    @EntityAttributePolicy(entityClass = User.class, attributes = "codEmpresa", action = EntityAttributePolicyAction.MODIFY)
    @EntityPolicy(entityClass = User.class, actions = {EntityPolicyAction.READ, EntityPolicyAction.UPDATE})
    @EntityAttributePolicy(entityClass = Empresa.class, attributes = "*", action = EntityAttributePolicyAction.VIEW)
    @EntityAttributePolicy(entityClass = Empresa.class, attributes = "selecionada", action = EntityAttributePolicyAction.MODIFY)
    @EntityPolicy(entityClass = Empresa.class, actions = {EntityPolicyAction.READ, EntityPolicyAction.UPDATE})
    void selecionarEmpresaEntities();

    @ViewPolicy(viewIds = {
            "CentroCusto.list",
            "Parceiro.list",
            "Parceiro.detail",
            "CondicaoPagamento.list",
            "Vendedor.list",
            "Transportadora.list",
            "Transportadora.detail",
            "Mensagem.list",
            "SelecionarEmpresa.list",
    })
    @MenuPolicy(menuIds = {
            "CentroCusto.list",
            "Parceiro.list",
            "CondicaoPagamento.list",
            "Vendedor.list",
            "Transportadora.list",
            "Mensagem.list",
            "SelecionarEmpresa.list",
    })
    void moduloCadastrosScreens();

    // Tela de outro módulo aberta só via lookup do combo de endereço — sem item de
    // menu, o operador cadastros não navega direto pra ela.
    @ViewPolicy(viewIds = {"TipoLogradouro.list", "Municipio.list"})
    void referenciasLookupScreens();
}
