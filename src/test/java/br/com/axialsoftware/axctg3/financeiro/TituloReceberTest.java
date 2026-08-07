package br.com.axialsoftware.axctg3.financeiro;

import br.com.axialsoftware.axctg3.entity.User;
import br.com.axialsoftware.axctg3.entity.cadastros.Empresa;
import br.com.axialsoftware.axctg3.entity.cadastros.Parceiro;
import br.com.axialsoftware.axctg3.entity.contabil.ContaContabil;
import br.com.axialsoftware.axctg3.entity.contabil.Lancamento;
import br.com.axialsoftware.axctg3.entity.enums.CodNat;
import br.com.axialsoftware.axctg3.entity.financeiro.Banco;
import br.com.axialsoftware.axctg3.entity.financeiro.HistoricoFinanceiro;
import br.com.axialsoftware.axctg3.entity.financeiro.ItemReceber;
import br.com.axialsoftware.axctg3.entity.financeiro.TituloReceber;
import br.com.axialsoftware.axctg3.service.financeiro.ItemReceberService;
import br.com.axialsoftware.axctg3.service.financeiro.TituloReceberService;
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
 * Cobre {@code TituloReceberEventListener} (item 1 de emissão automático, campos
 * calculados — sem numeração via Sequence, {@code numero} aqui é digitado) e a postagem
 * contábil de baixa de {@code ItemReceberService}. A postagem de <b>emissão</b> não foi
 * portada (ver Javadoc de {@code TituloReceber}), então não há teste para ela.
 */
@SpringBootTest
@ExtendWith(AuthenticatedAsAdmin.class)
@ActiveProfiles("test")
public class TituloReceberTest {

    private static final int COD_EMPRESA = 9102;
    private static final int ANO = 2099;
    private static final int MES = 8;

    @Autowired
    DataManager dataManager;

    @Autowired
    CurrentAuthentication currentAuthentication;

    @Autowired
    TituloReceberService tituloReceberService;

    @Autowired
    ItemReceberService itemReceberService;

    private ContaContabil contaBanco;
    private ContaContabil contaParceiroSaida;
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

        contaBanco = criarConta("9201", "Banco de teste");
        contaParceiroSaida = criarConta("9202", "Clientes de teste");

        parceiro = dataManager.create(Parceiro.class);
        parceiro.setCodigo(9201L);
        parceiro.setCodEmpresa(COD_EMPRESA);
        parceiro.setNome("Cliente de teste");
        parceiro.setApelido("Cliente");
        parceiro.setCnpj("00000000000191");
        parceiro.setCliente(true);
        parceiro = dataManager.save(parceiro);

        banco = dataManager.create(Banco.class);
        banco.setCodigo(9201);
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
        empresa.setContaParceiroSaida(contaParceiroSaida);
        dataManager.save(empresa);
    }

    @Test
    void test_criarTituloReceberCriaItemDeEmissao() {
        TituloReceber tituloReceber = criarTituloReceber("TST0001", new BigDecimal("500.00"));

        assertThat(tituloReceber.getCodEmpresa()).isEqualTo(COD_EMPRESA);

        List<ItemReceber> itens = carregarItens(tituloReceber);
        assertThat(itens).hasSize(1);
        ItemReceber item1 = itens.get(0);
        assertThat(item1.getItem()).isEqualTo(1);
        assertThat(item1.getHistoricoFinanceiro().getId()).isEqualTo(histEmissao.getId());
        assertThat(item1.getValor()).isEqualByComparingTo("500.00");
        assertThat(item1.getContabilizado()).isFalse();
    }

    @Test
    void test_camposCalculadosRefletemBaixaParcial() {
        TituloReceber tituloReceber = criarTituloReceber("TST0002", new BigDecimal("500.00"));

        TituloReceber semBaixa = recarregar(tituloReceber);
        assertThat(semBaixa.getAberto()).isTrue();
        assertThat(semBaixa.getValorBaixado()).isEqualByComparingTo("0");
        assertThat(semBaixa.getValorAberto()).isEqualByComparingTo("500.00");

        criarItemBaixa(tituloReceber, new BigDecimal("200.00"));

        TituloReceber comBaixaParcial = recarregar(tituloReceber);
        assertThat(comBaixaParcial.getAberto()).isTrue();
        assertThat(comBaixaParcial.getValorBaixado()).isEqualByComparingTo("200.00");

        criarItemBaixa(tituloReceber, new BigDecimal("300.00"));

        TituloReceber quitado = recarregar(tituloReceber);
        assertThat(quitado.getAberto()).isFalse();
        assertThat(quitado.getValorBaixado()).isEqualByComparingTo("500.00");
    }

    @Test
    void test_lancamentosBaixaPostaContaBancoXContaParceiroSaidaEMarcaContabilizado() {
        TituloReceber tituloReceber = criarTituloReceber("TST0003", new BigDecimal("500.00"));
        criarItemBaixa(tituloReceber, new BigDecimal("500.00"));
        TituloReceber comItens = carregarComItens(tituloReceber);

        itemReceberService.lancamentosBaixa(comItens);

        Lancamento lancamento = carregarUnicoLancamento("bx receber");
        assertThat(lancamento.getContaDevedora().getId()).isEqualTo(contaBanco.getId());
        assertThat(lancamento.getContaCredora().getId()).isEqualTo(contaParceiroSaida.getId());
        assertThat(lancamento.getValor()).isEqualByComparingTo("500.00");

        List<ItemReceber> itens = carregarItens(tituloReceber);
        ItemReceber itemBaixa = itens.stream().filter(i -> i.getItem() == 2).findFirst().orElseThrow();
        assertThat(itemBaixa.getContabilizado()).isTrue();
    }

    @AfterEach
    void tearDown() {
        limparDadosDaEmpresa();
    }

    private TituloReceber criarTituloReceber(String numero, BigDecimal valor) {
        TituloReceber tituloReceber = dataManager.create(TituloReceber.class);
        tituloReceber.setNumero(numero);
        tituloReceber.setDataEmissao(LocalDate.of(ANO, MES, 10));
        tituloReceber.setDataVencimento(LocalDate.of(ANO, MES, 20));
        tituloReceber.setParceiro(parceiro);
        tituloReceber.setBanco(banco);
        tituloReceber.setValor(valor);
        return dataManager.save(tituloReceber);
    }

    private void criarItemBaixa(TituloReceber tituloReceber, BigDecimal valor) {
        ItemReceber item = dataManager.create(ItemReceber.class);
        item.setTituloReceber(dataManager.load(TituloReceber.class).id(tituloReceber.getId()).one());
        item.setItem(2);
        item.setData(LocalDate.of(ANO, MES, 15));
        item.setHistoricoFinanceiro(histBaixa);
        item.setValor(valor);
        dataManager.save(item);
    }

    private TituloReceber recarregar(TituloReceber tituloReceber) {
        return dataManager.load(TituloReceber.class).id(tituloReceber.getId()).one();
    }

    private TituloReceber carregarComItens(TituloReceber tituloReceber) {
        TituloReceber carregado = dataManager.load(TituloReceber.class).id(tituloReceber.getId()).one();
        carregado.setItens(carregarItens(tituloReceber));
        return carregado;
    }

    private List<ItemReceber> carregarItens(TituloReceber tituloReceber) {
        return dataManager.load(ItemReceber.class)
                .query("select e from ItemReceber e where e.tituloReceber = :tituloReceber order by e.item")
                .parameter("tituloReceber", tituloReceber)
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
        apagar(carregar(ItemReceber.class, "select e from ItemReceber e where e.tituloReceber.codEmpresa = :codEmpresa"));
        apagar(carregar(TituloReceber.class, "select e from TituloReceber e where e.codEmpresa = :codEmpresa"));
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
