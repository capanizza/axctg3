package br.com.axialsoftware.axctg3.security;

import br.com.axialsoftware.axctg3.entity.User;
import br.com.axialsoftware.axctg3.entity.cadastros.ConfigRel;
import br.com.axialsoftware.axctg3.entity.cadastros.CondicaoPagamento;
import br.com.axialsoftware.axctg3.entity.cadastros.Empresa;
import br.com.axialsoftware.axctg3.entity.cadastros.Mensagem;
import br.com.axialsoftware.axctg3.entity.cadastros.Parceiro;
import br.com.axialsoftware.axctg3.entity.cadastros.Transportadora;
import br.com.axialsoftware.axctg3.entity.cadastros.Vendedor;
import br.com.axialsoftware.axctg3.entity.contabil.ContaContabil;
import br.com.axialsoftware.axctg3.entity.financeiro.Banco;
import br.com.axialsoftware.axctg3.entity.financeiro.ItemReceber;
import br.com.axialsoftware.axctg3.entity.financeiro.TituloReceber;
import br.com.axialsoftware.axctg3.entity.fiscal.GrupoProduto;
import br.com.axialsoftware.axctg3.entity.fiscal.ItemNotaSaida;
import br.com.axialsoftware.axctg3.entity.fiscal.ItemPedidoVenda;
import br.com.axialsoftware.axctg3.entity.fiscal.NaturezaOperacao;
import br.com.axialsoftware.axctg3.entity.fiscal.Nfe;
import br.com.axialsoftware.axctg3.entity.fiscal.NfeCartaCorrecao;
import br.com.axialsoftware.axctg3.entity.fiscal.NfeDuplicata;
import br.com.axialsoftware.axctg3.entity.fiscal.NfeInutilizacao;
import br.com.axialsoftware.axctg3.entity.fiscal.NfeItem;
import br.com.axialsoftware.axctg3.entity.fiscal.NfePagamento;
import br.com.axialsoftware.axctg3.entity.fiscal.NfeVolume;
import br.com.axialsoftware.axctg3.entity.fiscal.NotaSaida;
import br.com.axialsoftware.axctg3.entity.fiscal.PedidoVenda;
import br.com.axialsoftware.axctg3.entity.fiscal.Produto;
import br.com.axialsoftware.axctg3.entity.fiscal.SaldoProduto;
import br.com.axialsoftware.axctg3.entity.tabelas.ClassTrib;
import br.com.axialsoftware.axctg3.entity.tabelas.ClassificacaoFiscal;
import br.com.axialsoftware.axctg3.entity.tabelas.Cst;
import io.jmix.security.model.EntityAttributePolicyAction;
import io.jmix.security.model.EntityPolicyAction;
import io.jmix.security.role.annotation.EntityAttributePolicy;
import io.jmix.security.role.annotation.EntityPolicy;
import io.jmix.security.role.annotation.ResourceRole;
import io.jmix.securityflowui.role.annotation.MenuPolicy;
import io.jmix.securityflowui.role.annotation.ViewPolicy;

/**
 * Piloto do modelo função+módulo (ver memória do projeto). Operador fiscal: CRUD
 * normal das telas do módulo (sem excluir onde existe botão de excluir — algumas
 * telas nunca tiveram esse botão, ver comentários pontuais abaixo). Botões de negócio
 * sem view própria (emitir NFe, cancelar, CCe, inutilizar, alternar ambiente
 * homologação/produção) ainda sem checagem de role — mesma pendência conhecida do
 * {@link OperadorContabilRole}/{@link OperadorFinanceiroRole}; "alternar ambiente" em
 * particular parece um bom candidato a virar gerente-only numa 2ª passada, mas não
 * decidi isso sozinho — fica registrado aqui pra discutir.
 */
@ResourceRole(name = "Operador Fiscal", code = OperadorFiscalRole.CODE)
public interface OperadorFiscalRole {

    String CODE = "operador-fiscal";

    @EntityAttributePolicy(entityClass = NaturezaOperacao.class, attributes = "*", action = EntityAttributePolicyAction.MODIFY)
    @EntityPolicy(entityClass = NaturezaOperacao.class, actions = {EntityPolicyAction.READ, EntityPolicyAction.CREATE, EntityPolicyAction.UPDATE})
    void naturezaOperacaoEntity();

    // Sem botão de excluir hoje (GrupoProdutoListView é sidePanel, sem ação de
    // remover) — READ+CREATE+UPDATE já é o teto real, não uma escolha de tier.
    @EntityAttributePolicy(entityClass = GrupoProduto.class, attributes = "*", action = EntityAttributePolicyAction.MODIFY)
    @EntityPolicy(entityClass = GrupoProduto.class, actions = {EntityPolicyAction.READ, EntityPolicyAction.CREATE, EntityPolicyAction.UPDATE})
    void grupoProdutoEntity();

    @EntityAttributePolicy(entityClass = Produto.class, attributes = "*", action = EntityAttributePolicyAction.MODIFY)
    @EntityPolicy(entityClass = Produto.class, actions = {EntityPolicyAction.READ, EntityPolicyAction.CREATE, EntityPolicyAction.UPDATE})
    void produtoEntity();

    @EntityAttributePolicy(entityClass = SaldoProduto.class, attributes = "*", action = EntityAttributePolicyAction.MODIFY)
    @EntityPolicy(entityClass = SaldoProduto.class, actions = {EntityPolicyAction.READ, EntityPolicyAction.CREATE, EntityPolicyAction.UPDATE})
    void saldoProdutoEntity();

    @EntityAttributePolicy(entityClass = PedidoVenda.class, attributes = "*", action = EntityAttributePolicyAction.MODIFY)
    @EntityPolicy(entityClass = PedidoVenda.class, actions = {EntityPolicyAction.READ, EntityPolicyAction.CREATE, EntityPolicyAction.UPDATE})
    @EntityAttributePolicy(entityClass = ItemPedidoVenda.class, attributes = "*", action = EntityAttributePolicyAction.MODIFY)
    @EntityPolicy(entityClass = ItemPedidoVenda.class, actions = {EntityPolicyAction.READ, EntityPolicyAction.CREATE, EntityPolicyAction.UPDATE})
    void pedidoVendaEntity();

    @EntityAttributePolicy(entityClass = NotaSaida.class, attributes = "*", action = EntityAttributePolicyAction.MODIFY)
    @EntityPolicy(entityClass = NotaSaida.class, actions = {EntityPolicyAction.READ, EntityPolicyAction.CREATE, EntityPolicyAction.UPDATE})
    @EntityAttributePolicy(entityClass = ItemNotaSaida.class, attributes = "*", action = EntityAttributePolicyAction.MODIFY)
    @EntityPolicy(entityClass = ItemNotaSaida.class, actions = {EntityPolicyAction.READ, EntityPolicyAction.CREATE, EntityPolicyAction.UPDATE})
    void notaSaidaEntity();

    // Nfe e seus filhos (composição, cascade delete) são só visualizados
    // (NfeListView.editAction é list_read, sem list_edit) — quem grava é sempre
    // NfeEmissaoService/NfeCancelamentoService/NfeCartaCorrecaoService por trás dos
    // botões emitir/cancelar/CCe/consultar. VIEW nos atributos porque não existe
    // formulário de criação/edição pra esses campos; CREATE+UPDATE na entidade porque
    // o serviço grava sob a sessão do operador.
    @EntityAttributePolicy(entityClass = Nfe.class, attributes = "*", action = EntityAttributePolicyAction.VIEW)
    @EntityPolicy(entityClass = Nfe.class, actions = {EntityPolicyAction.READ, EntityPolicyAction.CREATE, EntityPolicyAction.UPDATE})
    @EntityAttributePolicy(entityClass = NfeItem.class, attributes = "*", action = EntityAttributePolicyAction.VIEW)
    @EntityPolicy(entityClass = NfeItem.class, actions = {EntityPolicyAction.READ, EntityPolicyAction.CREATE, EntityPolicyAction.UPDATE})
    @EntityAttributePolicy(entityClass = NfeDuplicata.class, attributes = "*", action = EntityAttributePolicyAction.VIEW)
    @EntityPolicy(entityClass = NfeDuplicata.class, actions = {EntityPolicyAction.READ, EntityPolicyAction.CREATE, EntityPolicyAction.UPDATE})
    @EntityAttributePolicy(entityClass = NfePagamento.class, attributes = "*", action = EntityAttributePolicyAction.VIEW)
    @EntityPolicy(entityClass = NfePagamento.class, actions = {EntityPolicyAction.READ, EntityPolicyAction.CREATE, EntityPolicyAction.UPDATE})
    @EntityAttributePolicy(entityClass = NfeVolume.class, attributes = "*", action = EntityAttributePolicyAction.VIEW)
    @EntityPolicy(entityClass = NfeVolume.class, actions = {EntityPolicyAction.READ, EntityPolicyAction.CREATE, EntityPolicyAction.UPDATE})
    void nfeEntity();

    // Cartas de correção: histórico imutável (nfe-carta-correcao-list-view.xml só tem
    // imprimirAction, sem editar/excluir) — CREATE é o teto real pro filho da
    // composição também, não uma escolha de tier.
    @EntityAttributePolicy(entityClass = NfeCartaCorrecao.class, attributes = "*", action = EntityAttributePolicyAction.VIEW)
    @EntityPolicy(entityClass = NfeCartaCorrecao.class, actions = {EntityPolicyAction.READ, EntityPolicyAction.CREATE})
    void nfeCartaCorrecaoEntity();

    // Inutilização de numeração: histórico imutável (sem detail view, sem Remover, ver
    // memória do projeto) — READ+CREATE é o teto real, nunca ganha DELETE nem no
    // gerente.
    @EntityAttributePolicy(entityClass = NfeInutilizacao.class, attributes = "*", action = EntityAttributePolicyAction.VIEW)
    @EntityPolicy(entityClass = NfeInutilizacao.class, actions = {EntityPolicyAction.READ, EntityPolicyAction.CREATE})
    void nfeInutilizacaoEntity();

    // TituloReceberService.gerarTitulosDaEmissao roda sob a sessão de quem emite a
    // NFe: ao reemitir depois que NotaSaida.valor muda, ela EXCLUI (hard delete) os
    // títulos antigos e recria — por isso TituloReceber precisa de DELETE aqui mesmo
    // pro operador, fora do padrão "operador nunca exclui" — sem isso a reemissão
    // quebra com AccessDeniedException. Ver memória titulos-receber-desatualizados-cstat851.
    @EntityAttributePolicy(entityClass = TituloReceber.class, attributes = "*", action = EntityAttributePolicyAction.MODIFY)
    @EntityPolicy(entityClass = TituloReceber.class, actions = EntityPolicyAction.ALL)
    @EntityAttributePolicy(entityClass = ItemReceber.class, attributes = "*", action = EntityAttributePolicyAction.MODIFY)
    @EntityPolicy(entityClass = ItemReceber.class, actions = EntityPolicyAction.ALL)
    void tituloReceberCrossEntity();

    // ConfigRel guarda os últimos filtros de relatório por usuário (ver
    // UtilGeralService.prepararConfigRel) — precisa mesmo com os botões de relatório
    // ainda liberados sem checagem de role.
    @EntityAttributePolicy(entityClass = ConfigRel.class, attributes = "*", action = EntityAttributePolicyAction.MODIFY)
    @EntityPolicy(entityClass = ConfigRel.class, actions = {EntityPolicyAction.READ, EntityPolicyAction.CREATE, EntityPolicyAction.UPDATE})
    void configRelEntity();

    // PeriodoFiscal.list grava ano/mês fiscal, e SelecionarEmpresa.list grava a
    // empresa corrente, ambos no próprio User autenticado — autoatendimento.
    @EntityAttributePolicy(entityClass = User.class, attributes = {"anoFiscal", "mesFiscal", "codEmpresa"}, action = EntityAttributePolicyAction.MODIFY)
    @EntityPolicy(entityClass = User.class, actions = {EntityPolicyAction.READ, EntityPolicyAction.UPDATE})
    void userPeriodoEntity();

    // "selecionada" por SelecionarEmpresa.list; "ambienteNfe" pelo botão "Alternar
    // ambiente" (homologação/produção) do Nfe.list/NotaSaida.list.
    @EntityAttributePolicy(entityClass = Empresa.class, attributes = "*", action = EntityAttributePolicyAction.VIEW)
    @EntityAttributePolicy(entityClass = Empresa.class, attributes = {"selecionada", "ambienteNfe"}, action = EntityAttributePolicyAction.MODIFY)
    @EntityPolicy(entityClass = Empresa.class, actions = {EntityPolicyAction.READ, EntityPolicyAction.UPDATE})
    void empresaEntity();

    // Referenciadas via entityComboBox (lookup), mas não geridas por este role.
    @EntityAttributePolicy(entityClass = Parceiro.class, attributes = "*", action = EntityAttributePolicyAction.VIEW)
    @EntityPolicy(entityClass = Parceiro.class, actions = EntityPolicyAction.READ)
    @EntityAttributePolicy(entityClass = CondicaoPagamento.class, attributes = "*", action = EntityAttributePolicyAction.VIEW)
    @EntityPolicy(entityClass = CondicaoPagamento.class, actions = EntityPolicyAction.READ)
    @EntityAttributePolicy(entityClass = Vendedor.class, attributes = "*", action = EntityAttributePolicyAction.VIEW)
    @EntityPolicy(entityClass = Vendedor.class, actions = EntityPolicyAction.READ)
    @EntityAttributePolicy(entityClass = Transportadora.class, attributes = "*", action = EntityAttributePolicyAction.VIEW)
    @EntityPolicy(entityClass = Transportadora.class, actions = EntityPolicyAction.READ)
    @EntityAttributePolicy(entityClass = Mensagem.class, attributes = "*", action = EntityAttributePolicyAction.VIEW)
    @EntityPolicy(entityClass = Mensagem.class, actions = EntityPolicyAction.READ)
    @EntityAttributePolicy(entityClass = ClassTrib.class, attributes = "*", action = EntityAttributePolicyAction.VIEW)
    @EntityPolicy(entityClass = ClassTrib.class, actions = EntityPolicyAction.READ)
    @EntityAttributePolicy(entityClass = ClassificacaoFiscal.class, attributes = "*", action = EntityAttributePolicyAction.VIEW)
    @EntityPolicy(entityClass = ClassificacaoFiscal.class, actions = EntityPolicyAction.READ)
    @EntityAttributePolicy(entityClass = Cst.class, attributes = "*", action = EntityAttributePolicyAction.VIEW)
    @EntityPolicy(entityClass = Cst.class, actions = EntityPolicyAction.READ)
    @EntityAttributePolicy(entityClass = ContaContabil.class, attributes = "*", action = EntityAttributePolicyAction.VIEW)
    @EntityPolicy(entityClass = ContaContabil.class, actions = EntityPolicyAction.READ)
    @EntityAttributePolicy(entityClass = Banco.class, attributes = "*", action = EntityAttributePolicyAction.VIEW)
    @EntityPolicy(entityClass = Banco.class, actions = EntityPolicyAction.READ)
    void referenciasLookupEntities();

    @ViewPolicy(viewIds = {
            "NaturezaOperacao.list",
            "NaturezaOperacao.detail",
            "GrupoProduto.list",
            "Produto.list",
            "Produto.detail",
            "SaldoProduto.list",
            "PedidoVenda.list",
            "PedidoVenda.detail",
            "ItemPedidoVenda.detail",
            "NotaSaida.list",
            "NotaSaida.detail",
            "NotaSaida.complementar",
            "ItemNotaSaida.detail",
            "Nfe.list",
            "Nfe.detail",
            "Nfe.import",
            "NfeItem.detail",
            "NfeDuplicata.detail",
            "NfePagamento.detail",
            "NfeVolume.detail",
            "NfeCartaCorrecao.list",
            "NfeInutilizacao.list",
            "PeriodoFiscal.list",
            "SelecionarEmpresa.list",
    })
    @MenuPolicy(menuIds = {
            "NaturezaOperacao.list",
            "GrupoProduto.list",
            "Produto.list",
            "SaldoProduto.list",
            "PedidoVenda.list",
            "NotaSaida.list",
            "Nfe.list",
            "PeriodoFiscal.list",
            "SelecionarEmpresa.list",
    })
    void moduloFiscalScreens();

    // Telas de outro módulo abertas só via lookup dos combos acima — sem item de
    // menu, o operador fiscal não navega direto pra elas.
    @ViewPolicy(viewIds = {
            "Parceiro.list",
            "CondicaoPagamento.list",
            "Vendedor.list",
            "Transportadora.list",
            "Mensagem.list",
            "ClassTrib.list",
            "ClassificacaoFiscal.list",
            "Cst.list",
            "ContaContabil.list",
            "Banco.list",
    })
    void referenciasLookupScreens();
}
