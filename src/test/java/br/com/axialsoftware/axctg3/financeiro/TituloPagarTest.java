package br.com.axialsoftware.axctg3.financeiro;

import br.com.axialsoftware.axctg3.entity.User;
import br.com.axialsoftware.axctg3.entity.cadastros.Empresa;
import br.com.axialsoftware.axctg3.entity.cadastros.Parceiro;
import br.com.axialsoftware.axctg3.entity.contabil.ContaContabil;
import br.com.axialsoftware.axctg3.entity.contabil.Lancamento;
import br.com.axialsoftware.axctg3.entity.enums.CodNat;
import br.com.axialsoftware.axctg3.entity.financeiro.Banco;
import br.com.axialsoftware.axctg3.entity.financeiro.HistoricoFinanceiro;
import br.com.axialsoftware.axctg3.entity.financeiro.ItemPagar;
import br.com.axialsoftware.axctg3.entity.financeiro.TituloPagar;
import br.com.axialsoftware.axctg3.service.financeiro.ItemPagarService;
import br.com.axialsoftware.axctg3.service.financeiro.TituloPagarService;
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
 * Cobre {@code TituloPagarEventListener} (numeração via Sequence, item 1 de emissão
 * automático, campos calculados) e a postagem contábil de emissão/baixa de
 * {@code TituloPagarService}/{@code ItemPagarService}. Mesma forma de
 * {@code DiversoPagarTest}, mais a numeração guardada por "se ainda não vier setado".
 */
@SpringBootTest
@ExtendWith(AuthenticatedAsAdmin.class)
@ActiveProfiles("test")
public class TituloPagarTest {

    private static final int COD_EMPRESA = 9103;
    private static final int ANO = 2099;
    private static final int MES = 8;

    @Autowired
    DataManager dataManager;

    @Autowired
    CurrentAuthentication currentAuthentication;

    @Autowired
    TituloPagarService tituloPagarService;

    @Autowired
    ItemPagarService itemPagarService;

    private ContaContabil contaEntrada;
    private ContaContabil contaParceiro;
    private ContaContabil contaBanco;
    private Parceiro parceiro;
    private Banco banco;
    private HistoricoFinanceiro histEmissao;
    private HistoricoFinanceiro histBaixa;

    @BeforeEach
    void setUp() {
        User admin = (User) currentAuthentication.getUser();
        admin.setCodEmpresa(COD_EMPRESA);
        admin.setAnoContabil(ANO);
        admin.setMesContabil(MES);

        limparDadosDaEmpresa();

        contaEntrada = criarConta("9301", "Despesa de teste");
        contaParceiro = criarConta("9302", "Fornecedores de teste");
        contaBanco = criarConta("9303", "Banco de teste");

        parceiro = dataManager.create(Parceiro.class);
        parceiro.setCodigo(9301L);
        parceiro.setCodEmpresa(COD_EMPRESA);
        parceiro.setNome("Fornecedor de teste");
        parceiro.setApelido("Fornecedor");
        parceiro.setCnpj("00000000000191");
        parceiro.setFornecedor(true);
        parceiro = dataManager.save(parceiro);

        banco = dataManager.create(Banco.class);
        banco.setCodigo(9301);
        banco.setCodEmpresa(COD_EMPRESA);
        banco.setNome("Banco de teste");
        banco.setContaContabil(contaBanco);
        banco = dataManager.save(banco);

        histEmissao = criarHistoricoFinanceiro(1, "Emissão de teste", false, false, false);
        histBaixa = criarHistoricoFinanceiro(2, "Baixa de teste", true, false, false);

        Empresa empresa = dataManager.create(Empresa.class);
        empresa.setCodigo(COD_EMPRESA);
        empresa.setNome("Empresa de teste");
        empresa.setApelido("Teste");
        empresa.setContaParceiroEntrada(contaParceiro);
        dataManager.save(empresa);
    }

    @Test
    void test_criarTituloPagarGeraNumeroECriaItemDeEmissao() {
        TituloPagar tituloPagar = criarTituloPagar(new BigDecimal("500.00"));

        assertThat(tituloPagar.getNumero()).isNotNull();
        assertThat(tituloPagar.getCodEmpresa()).isEqualTo(COD_EMPRESA);

        List<ItemPagar> itens = carregarItens(tituloPagar);
        assertThat(itens).hasSize(1);
        ItemPagar item1 = itens.get(0);
        assertThat(item1.getItem()).isEqualTo(1);
        assertThat(item1.getHistoricoFinanceiro().getId()).isEqualTo(histEmissao.getId());
        assertThat(item1.getValor()).isEqualByComparingTo("500.00");
        assertThat(item1.getContabilizado()).isFalse();
    }

    @Test
    void test_numeroPreenchidoNaoEhSobrescritoPelaSequence() {
        TituloPagar tituloPagar = dataManager.create(TituloPagar.class);
        tituloPagar.setNumero(424242);
        tituloPagar.setDataEmissao(LocalDate.of(ANO, MES, 10));
        tituloPagar.setDataVencimento(LocalDate.of(ANO, MES, 20));
        tituloPagar.setParceiro(parceiro);
        tituloPagar.setValor(new BigDecimal("500.00"));
        tituloPagar.setContaContabil(contaEntrada);
        tituloPagar = dataManager.save(tituloPagar);

        assertThat(tituloPagar.getNumero()).isEqualTo(424242);
    }

    @Test
    void test_camposCalculadosRefletemBaixaParcial() {
        TituloPagar tituloPagar = criarTituloPagar(new BigDecimal("500.00"));

        TituloPagar semBaixa = recarregar(tituloPagar);
        assertThat(semBaixa.getAberto()).isTrue();
        assertThat(semBaixa.getValorBaixado()).isEqualByComparingTo("0");

        criarItemBaixa(tituloPagar, new BigDecimal("500.00"));

        TituloPagar quitado = recarregar(tituloPagar);
        assertThat(quitado.getAberto()).isFalse();
        assertThat(quitado.getValorBaixado()).isEqualByComparingTo("500.00");
    }

    @Test
    void test_lancamentosEmissaoPostaContaEntradaXContaParceiroEMarcaContabilizado() {
        TituloPagar tituloPagar = criarTituloPagar(new BigDecimal("500.00"));
        TituloPagar comItens = carregarComItens(tituloPagar);

        tituloPagarService.lancamentosEmissao(comItens);

        Lancamento lancamento = carregarUnicoLancamento("em pagar");
        assertThat(lancamento.getContaDevedora().getId()).isEqualTo(contaEntrada.getId());
        assertThat(lancamento.getContaCredora().getId()).isEqualTo(contaParceiro.getId());
        assertThat(lancamento.getValor()).isEqualByComparingTo("500.00");

        ItemPagar item1 = carregarItens(tituloPagar).get(0);
        assertThat(item1.getContabilizado()).isTrue();
    }

    @Test
    void test_lancamentosBaixaPostaContaParceiroXContaBancoEMarcaContabilizado() {
        TituloPagar tituloPagar = criarTituloPagar(new BigDecimal("500.00"));
        criarItemBaixa(tituloPagar, new BigDecimal("500.00"));
        TituloPagar comItens = carregarComItens(tituloPagar);

        itemPagarService.lancamentosBaixa(comItens);

        Lancamento lancamento = carregarUnicoLancamento("bx pagar");
        assertThat(lancamento.getContaDevedora().getId()).isEqualTo(contaParceiro.getId());
        assertThat(lancamento.getContaCredora().getId()).isEqualTo(contaBanco.getId());
        assertThat(lancamento.getValor()).isEqualByComparingTo("500.00");

        List<ItemPagar> itens = carregarItens(tituloPagar);
        ItemPagar itemBaixa = itens.stream().filter(i -> i.getItem() == 2).findFirst().orElseThrow();
        assertThat(itemBaixa.getContabilizado()).isTrue();
    }

    @AfterEach
    void tearDown() {
        limparDadosDaEmpresa();
    }

    private TituloPagar criarTituloPagar(BigDecimal valor) {
        TituloPagar tituloPagar = dataManager.create(TituloPagar.class);
        tituloPagar.setDataEmissao(LocalDate.of(ANO, MES, 10));
        tituloPagar.setDataVencimento(LocalDate.of(ANO, MES, 20));
        tituloPagar.setParceiro(parceiro);
        tituloPagar.setValor(valor);
        tituloPagar.setContaContabil(contaEntrada);
        return dataManager.save(tituloPagar);
    }

    private void criarItemBaixa(TituloPagar tituloPagar, BigDecimal valor) {
        ItemPagar item = dataManager.create(ItemPagar.class);
        item.setTituloPagar(dataManager.load(TituloPagar.class).id(tituloPagar.getId()).one());
        item.setItem(2);
        item.setData(LocalDate.of(ANO, MES, 15));
        item.setHistoricoFinanceiro(histBaixa);
        item.setBanco(banco);
        item.setValor(valor);
        dataManager.save(item);
    }

    private TituloPagar recarregar(TituloPagar tituloPagar) {
        return dataManager.load(TituloPagar.class).id(tituloPagar.getId()).one();
    }

    private TituloPagar carregarComItens(TituloPagar tituloPagar) {
        TituloPagar carregado = dataManager.load(TituloPagar.class).id(tituloPagar.getId()).one();
        carregado.setItens(carregarItens(tituloPagar));
        return carregado;
    }

    private List<ItemPagar> carregarItens(TituloPagar tituloPagar) {
        return dataManager.load(ItemPagar.class)
                .query("select e from ItemPagar e where e.tituloPagar = :tituloPagar order by e.item")
                .parameter("tituloPagar", tituloPagar)
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

    /** Mesmo motivo/ordem do {@code DiversoPagarTest} — Empresa/Banco referenciam ContaContabil. */
    private void limparDadosDaEmpresa() {
        apagar(carregar(Lancamento.class, "select e from Lancamento e where e.codEmpresa = :codEmpresa"));
        apagar(carregar(ItemPagar.class, "select e from ItemPagar e where e.tituloPagar.codEmpresa = :codEmpresa"));
        apagar(carregar(TituloPagar.class, "select e from TituloPagar e where e.codEmpresa = :codEmpresa"));
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
