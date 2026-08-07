package br.com.axialsoftware.axctg3.financeiro;

import br.com.axialsoftware.axctg3.entity.User;
import br.com.axialsoftware.axctg3.entity.cadastros.Empresa;
import br.com.axialsoftware.axctg3.entity.cadastros.Parceiro;
import br.com.axialsoftware.axctg3.entity.contabil.ContaContabil;
import br.com.axialsoftware.axctg3.entity.contabil.Lancamento;
import br.com.axialsoftware.axctg3.entity.enums.CodNat;
import br.com.axialsoftware.axctg3.entity.financeiro.Banco;
import br.com.axialsoftware.axctg3.entity.financeiro.DiversoPagar;
import br.com.axialsoftware.axctg3.entity.financeiro.HistoricoFinanceiro;
import br.com.axialsoftware.axctg3.entity.financeiro.ItemDiversoPagar;
import br.com.axialsoftware.axctg3.service.financeiro.DiversoPagarService;
import br.com.axialsoftware.axctg3.service.financeiro.ItemDiversoPagarService;
import br.com.axialsoftware.axctg3.test_support.AuthenticatedAsAdmin;
import io.jmix.core.DataManager;
import io.jmix.core.SaveContext;
import io.jmix.core.security.CurrentAuthentication;
import io.jmix.data.PersistenceHints;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cobre {@code DiversoPagarEventListener} (numeração via Sequence, item 1 de emissão
 * automático, campos calculados) e a postagem contábil de {@code DiversoPagarService}/
 * {@code ItemDiversoPagarService}. Empresa/período sintéticos, mesmo padrão de
 * {@code ImportarLancamentosTest}, para não cruzar com dado nenhum do HSQLDB de teste.
 */
@SpringBootTest
@ExtendWith(AuthenticatedAsAdmin.class)
@ActiveProfiles("test")
public class DiversoPagarTest {

    private static final int COD_EMPRESA = 9101;
    private static final int ANO = 2099;
    private static final int MES = 8;

    @Autowired
    DataManager dataManager;

    @Autowired
    CurrentAuthentication currentAuthentication;

    @Autowired
    DiversoPagarService diversoPagarService;

    @Autowired
    ItemDiversoPagarService itemDiversoPagarService;

    private ContaContabil contaEntrada;
    private ContaContabil contaParceiro;
    private ContaContabil contaBanco;
    private Parceiro parceiro;
    private Banco banco;
    private HistoricoFinanceiro histEmissao;
    private HistoricoFinanceiro histBaixa;
    private Empresa empresa;

    @BeforeEach
    void setUp() {
        User admin = (User) currentAuthentication.getUser();
        admin.setCodEmpresa(COD_EMPRESA);
        admin.setAnoContabil(ANO);
        admin.setMesContabil(MES);

        limparDadosDaEmpresa();

        contaEntrada = criarConta("9101", "Despesa de teste");
        contaParceiro = criarConta("9102", "Fornecedores de teste");
        contaBanco = criarConta("9103", "Banco de teste");

        parceiro = dataManager.create(Parceiro.class);
        parceiro.setCodigo(9101L);
        parceiro.setCodEmpresa(COD_EMPRESA);
        parceiro.setNome("Fornecedor de teste");
        parceiro.setApelido("Fornecedor");
        parceiro.setCnpj("00000000000191");
        parceiro.setFornecedor(true);
        parceiro = dataManager.save(parceiro);

        banco = dataManager.create(Banco.class);
        banco.setCodigo(9101);
        banco.setCodEmpresa(COD_EMPRESA);
        banco.setNome("Banco de teste");
        banco.setContaContabil(contaBanco);
        banco = dataManager.save(banco);

        histEmissao = criarHistoricoFinanceiro(1, "Emissão de teste", false, false, false);
        histBaixa = criarHistoricoFinanceiro(2, "Baixa de teste", true, false, false);

        empresa = dataManager.create(Empresa.class);
        empresa.setCodigo(COD_EMPRESA);
        empresa.setNome("Empresa de teste");
        empresa.setApelido("Teste");
        empresa.setContaParceiroEntrada(contaParceiro);
        empresa = dataManager.save(empresa);
    }

    @Test
    void test_criarDiversoPagarGeraNumeroECriaItemDeEmissao() {
        DiversoPagar diversoPagar = criarDiversoPagar(new BigDecimal("500.00"));

        assertThat(diversoPagar.getNumero()).isNotNull();
        assertThat(diversoPagar.getCodEmpresa()).isEqualTo(COD_EMPRESA);

        List<ItemDiversoPagar> itens = carregarItens(diversoPagar);
        assertThat(itens).hasSize(1);
        ItemDiversoPagar item1 = itens.get(0);
        assertThat(item1.getItem()).isEqualTo(1);
        assertThat(item1.getHistoricoFinanceiro().getId()).isEqualTo(histEmissao.getId());
        assertThat(item1.getValor()).isEqualByComparingTo("500.00");
        assertThat(item1.getContabilizado()).isFalse();
    }

    @Test
    void test_camposCalculadosRefletemBaixaParcial() {
        DiversoPagar diversoPagar = criarDiversoPagar(new BigDecimal("500.00"));

        DiversoPagar semBaixa = recarregar(diversoPagar);
        assertThat(semBaixa.getAberto()).isTrue();
        assertThat(semBaixa.getValorBaixado()).isEqualByComparingTo("0");
        assertThat(semBaixa.getValorAberto()).isEqualByComparingTo("500.00");
        assertThat(semBaixa.getContabilizadoEmissao()).isFalse();
        assertThat(semBaixa.getContabilizadoBaixa()).isFalse();

        criarItemBaixa(diversoPagar, new BigDecimal("200.00"));

        DiversoPagar comBaixaParcial = recarregar(diversoPagar);
        assertThat(comBaixaParcial.getAberto()).isTrue();
        assertThat(comBaixaParcial.getValorBaixado()).isEqualByComparingTo("200.00");
        assertThat(comBaixaParcial.getValorAberto()).isEqualByComparingTo("300.00");

        criarItemBaixa(diversoPagar, new BigDecimal("300.00"));

        DiversoPagar quitado = recarregar(diversoPagar);
        assertThat(quitado.getAberto()).isFalse();
        assertThat(quitado.getValorBaixado()).isEqualByComparingTo("500.00");
    }

    @Test
    void test_lancamentosEmissaoPostaContaEntradaXContaParceiroEMarcaContabilizado() {
        DiversoPagar diversoPagar = criarDiversoPagar(new BigDecimal("500.00"));
        DiversoPagar comItens = carregarComItens(diversoPagar);

        diversoPagarService.lancamentosEmissao(comItens);

        Lancamento lancamento = carregarUnicoLancamento("em diverso");
        assertThat(lancamento.getContaDevedora().getId()).isEqualTo(contaEntrada.getId());
        assertThat(lancamento.getContaCredora().getId()).isEqualTo(contaParceiro.getId());
        assertThat(lancamento.getValor()).isEqualByComparingTo("500.00");

        ItemDiversoPagar item1 = carregarItens(diversoPagar).get(0);
        assertThat(item1.getContabilizado()).isTrue();
    }

    @Test
    void test_lancamentosBaixaPostaContaParceiroXContaBancoEMarcaContabilizado() {
        DiversoPagar diversoPagar = criarDiversoPagar(new BigDecimal("500.00"));
        criarItemBaixa(diversoPagar, new BigDecimal("500.00"));
        DiversoPagar comItens = carregarComItens(diversoPagar);

        itemDiversoPagarService.lancamentosBaixa(comItens);

        Lancamento lancamento = carregarUnicoLancamento("bx diverso");
        assertThat(lancamento.getContaDevedora().getId()).isEqualTo(contaParceiro.getId());
        assertThat(lancamento.getContaCredora().getId()).isEqualTo(contaBanco.getId());
        assertThat(lancamento.getValor()).isEqualByComparingTo("500.00");

        List<ItemDiversoPagar> itens = carregarItens(diversoPagar);
        ItemDiversoPagar itemBaixa = itens.stream().filter(i -> i.getItem() == 2).findFirst().orElseThrow();
        assertThat(itemBaixa.getContabilizado()).isTrue();
    }

    @AfterEach
    void tearDown() {
        limparDadosDaEmpresa();
    }

    private DiversoPagar criarDiversoPagar(BigDecimal valor) {
        DiversoPagar diversoPagar = dataManager.create(DiversoPagar.class);
        diversoPagar.setDataEmissao(LocalDate.of(ANO, MES, 10));
        diversoPagar.setDataVencimento(LocalDate.of(ANO, MES, 20));
        diversoPagar.setParceiro(parceiro);
        diversoPagar.setValor(valor);
        diversoPagar.setContaContabil(contaEntrada);
        return dataManager.save(diversoPagar);
    }

    private void criarItemBaixa(DiversoPagar diversoPagar, BigDecimal valor) {
        ItemDiversoPagar item = dataManager.create(ItemDiversoPagar.class);
        item.setDiversoPagar(dataManager.load(DiversoPagar.class).id(diversoPagar.getId()).one());
        item.setItem(2);
        item.setData(LocalDate.of(ANO, MES, 15));
        item.setHistoricoFinanceiro(histBaixa);
        item.setBanco(banco);
        item.setValor(valor);
        dataManager.save(item);
    }

    private DiversoPagar recarregar(DiversoPagar diversoPagar) {
        return dataManager.load(DiversoPagar.class).id(diversoPagar.getId()).one();
    }

    /**
     * O service só lê {@code diversoPagar.getItens()} em memória — mais simples e menos
     * arriscado que acertar a API de fetch plan programático do zero do que carregar com
     * um fetch plan avulso incluindo "itens".
     */
    private DiversoPagar carregarComItens(DiversoPagar diversoPagar) {
        DiversoPagar carregado = dataManager.load(DiversoPagar.class).id(diversoPagar.getId()).one();
        carregado.setItens(carregarItens(diversoPagar));
        return carregado;
    }

    private List<ItemDiversoPagar> carregarItens(DiversoPagar diversoPagar) {
        return dataManager.load(ItemDiversoPagar.class)
                .query("select e from ItemDiversoPagar e where e.diversoPagar = :diversoPagar order by e.item")
                .parameter("diversoPagar", diversoPagar)
                .list();
    }

    private Lancamento carregarUnicoLancamento(String origem) {
        List<Lancamento> lancamentos = dataManager.load(Lancamento.class)
                .query("select e from Lancamento e where e.codEmpresa = :codEmpresa and e.origem = :origem")
                .parameter("codEmpresa", COD_EMPRESA)
                .parameter("origem", origem)
                .list();
        assertThat(lancamentos).hasSize(1);
        return lancamentos.get(0);
    }

    private ContaContabil criarConta(String codigo, String nome) {
        ContaContabil conta = dataManager.create(ContaContabil.class);
        conta.setCodigo(codigo);
        conta.setNome(nome);
        conta.setAno(ANO);
        conta.setCodEmpresa(COD_EMPRESA);
        conta.setGrau(1);
        conta.setCodContaSup(codigo);
        conta.setAnalitica(true);
        conta.setCodNat(CodNat.CONTAS_DE_ATIVO);
        return dataManager.save(conta);
    }

    private HistoricoFinanceiro criarHistoricoFinanceiro(int codigo, String nome, boolean baixa, boolean juros, boolean desconto) {
        HistoricoFinanceiro historico = dataManager.create(HistoricoFinanceiro.class);
        historico.setCodigo(codigo);
        historico.setCodEmpresa(COD_EMPRESA);
        historico.setNome(nome);
        historico.setEmissao(!baixa);
        historico.setBaixa(baixa);
        historico.setJuros(juros);
        historico.setDesconto(desconto);
        return dataManager.save(historico);
    }

    /**
     * Limpeza física — mesmo motivo do {@code ImportarLancamentosTest}: o índice único de
     * CONTA_CONTABIL não inclui DELETED_DATE, e o HSQLDB de teste é um arquivo persistente
     * entre execuções. Ordem respeita as FKs.
     */
    private void limparDadosDaEmpresa() {
        apagar(carregar(Lancamento.class, "select e from Lancamento e where e.codEmpresa = :codEmpresa"));
        apagar(carregar(ItemDiversoPagar.class, "select e from ItemDiversoPagar e where e.diversoPagar.codEmpresa = :codEmpresa"));
        apagar(carregar(DiversoPagar.class, "select e from DiversoPagar e where e.codEmpresa = :codEmpresa"));
        // Empresa (contaParceiroEntrada) e Banco (contaContabil) referenciam ContaContabil —
        // têm que sair antes dela, não depois.
        apagar(carregar(Empresa.class, "select e from Empresa e where e.codigo = :codEmpresa"));
        apagar(carregar(Banco.class, "select e from Banco e where e.codEmpresa = :codEmpresa"));
        apagar(carregar(ContaContabil.class, "select e from ContaContabil e where e.codEmpresa = :codEmpresa"));
        apagar(carregar(HistoricoFinanceiro.class, "select e from HistoricoFinanceiro e where e.codEmpresa = :codEmpresa"));
        apagar(carregar(Parceiro.class, "select e from Parceiro e where e.codEmpresa = :codEmpresa"));
    }

    private <E> List<E> carregar(Class<E> entityClass, String query) {
        return dataManager.load(entityClass)
                .query(query)
                .parameter("codEmpresa", COD_EMPRESA)
                .hint(PersistenceHints.SOFT_DELETION, false)
                .list();
    }

    private void apagar(List<?> entidades) {
        if (entidades.isEmpty()) {
            return;
        }
        dataManager.save(new SaveContext()
                .setHint(PersistenceHints.SOFT_DELETION, false)
                .setHint(PersistenceHints.SKIP_ENTITY_CHANGED_EVENT, true)
                .removing(entidades.toArray()));
    }
}
