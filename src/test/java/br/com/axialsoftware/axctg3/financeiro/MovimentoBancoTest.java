package br.com.axialsoftware.axctg3.financeiro;

import br.com.axialsoftware.axctg3.entity.User;
import br.com.axialsoftware.axctg3.entity.cadastros.Empresa;
import br.com.axialsoftware.axctg3.entity.contabil.ContaContabil;
import br.com.axialsoftware.axctg3.entity.contabil.Lancamento;
import br.com.axialsoftware.axctg3.entity.enums.CodNat;
import br.com.axialsoftware.axctg3.entity.financeiro.Banco;
import br.com.axialsoftware.axctg3.entity.financeiro.HistoricoFinanceiro;
import br.com.axialsoftware.axctg3.entity.financeiro.ItemMovimentoBanco;
import br.com.axialsoftware.axctg3.entity.financeiro.MovimentoBanco;
import br.com.axialsoftware.axctg3.service.financeiro.MovimentoBancoService;
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
 * Cobre {@code MovimentoBancoEventListener} (numeração via Sequence, item interno único
 * criado automaticamente, cópia de {@code lancado} no load) e a postagem contábil de
 * {@code MovimentoBancoService.lancamentos} — nos dois ramos: com {@code contaContabil}
 * própria e caindo no fallback {@code Empresa.contaMovimentoBancario}.
 */
@SpringBootTest
@ExtendWith(AuthenticatedAsAdmin.class)
@ActiveProfiles("test")
public class MovimentoBancoTest {

    private static final int COD_EMPRESA = 9104;
    private static final int ANO = 2099;
    private static final int MES = 8;

    @Autowired
    DataManager dataManager;

    @Autowired
    CurrentAuthentication currentAuthentication;

    @Autowired
    MovimentoBancoService movimentoBancoService;

    private ContaContabil contaBanco;
    private ContaContabil contaEspecifica;
    private ContaContabil contaMovimentoBancario;
    private Banco banco;
    private HistoricoFinanceiro historico;

    @BeforeEach
    void setUp() {
        User admin = (User) currentAuthentication.getUser();
        admin.setCodEmpresa(COD_EMPRESA);
        admin.setAnoContabil(ANO);
        admin.setMesContabil(MES);

        limparDadosDaEmpresa();

        contaBanco = criarConta("9401", "Banco de teste");
        contaEspecifica = criarConta("9402", "Despesa específica de teste");
        contaMovimentoBancario = criarConta("9403", "Movimento bancário genérico de teste");

        banco = dataManager.create(Banco.class);
        banco.setCodigo(9401);
        banco.setCodEmpresa(COD_EMPRESA);
        banco.setNome("Banco de teste");
        banco.setContaContabil(contaBanco);
        banco = dataManager.save(banco);

        historico = dataManager.create(HistoricoFinanceiro.class);
        historico.setCodigo(1);
        historico.setCodEmpresa(COD_EMPRESA);
        historico.setNome("Histórico de teste");
        historico = dataManager.save(historico);

        Empresa empresa = dataManager.create(Empresa.class);
        empresa.setCodigo(COD_EMPRESA);
        empresa.setNome("Empresa de teste");
        empresa.setApelido("Teste");
        empresa.setContaMovimentoBancario(contaMovimentoBancario);
        dataManager.save(empresa);
    }

    @Test
    void test_criarMovimentoBancoGeraNumeroECriaItemInterno() {
        MovimentoBanco movimentoBanco = criarMovimento(true, new BigDecimal("500.00"), null);

        assertThat(movimentoBanco.getLancamento()).isNotNull();
        assertThat(movimentoBanco.getCodEmpresa()).isEqualTo(COD_EMPRESA);

        List<ItemMovimentoBanco> itens = carregarItens(movimentoBanco);
        assertThat(itens).hasSize(1);
        assertThat(itens.get(0).getItem()).isEqualTo(1);
        assertThat(itens.get(0).getLancado()).isFalse();
    }

    @Test
    void test_lancadoNoLoadEspelhaOItemInterno() {
        MovimentoBanco movimentoBanco = criarMovimento(true, new BigDecimal("500.00"), null);

        MovimentoBanco antesDoLancamento = recarregar(movimentoBanco);
        assertThat(antesDoLancamento.getLancado()).isFalse();

        ItemMovimentoBanco item = carregarItens(movimentoBanco).get(0);
        item.setLancado(true);
        dataManager.save(item);

        MovimentoBanco depoisDoLancamento = recarregar(movimentoBanco);
        assertThat(depoisDoLancamento.getLancado()).isTrue();
    }

    @Test
    void test_lancamentosEntradaComContaPropriaPostaBancoXContaPropria() {
        MovimentoBanco movimentoBanco = criarMovimento(true, new BigDecimal("500.00"), contaEspecifica);
        MovimentoBanco comItens = carregarComItens(movimentoBanco);

        movimentoBancoService.lancamentos(comItens);

        Lancamento lancamento = carregarUnicoLancamento();
        // entrada: banco é devedora, contrapartida é credora
        assertThat(lancamento.getContaDevedora().getId()).isEqualTo(contaBanco.getId());
        assertThat(lancamento.getContaCredora().getId()).isEqualTo(contaEspecifica.getId());
        assertThat(lancamento.getValor()).isEqualByComparingTo("500.00");

        ItemMovimentoBanco item = carregarItens(movimentoBanco).get(0);
        assertThat(item.getLancado()).isTrue();
    }

    @Test
    void test_lancamentosSaidaSemContaPropriaUsaContaMovimentoBancarioDaEmpresa() {
        MovimentoBanco movimentoBanco = criarMovimento(false, new BigDecimal("300.00"), null);
        MovimentoBanco comItens = carregarComItens(movimentoBanco);

        movimentoBancoService.lancamentos(comItens);

        Lancamento lancamento = carregarUnicoLancamento();
        // saída sem contaContabil própria: cai no fallback Empresa.contaMovimentoBancario,
        // que é a devedora; banco é a credora
        assertThat(lancamento.getContaDevedora().getId()).isEqualTo(contaMovimentoBancario.getId());
        assertThat(lancamento.getContaCredora().getId()).isEqualTo(contaBanco.getId());
        assertThat(lancamento.getValor()).isEqualByComparingTo("300.00");
    }

    @AfterEach
    void tearDown() {
        limparDadosDaEmpresa();
    }

    private MovimentoBanco criarMovimento(boolean entrada, BigDecimal valor, ContaContabil contaContabil) {
        MovimentoBanco movimentoBanco = dataManager.create(MovimentoBanco.class);
        movimentoBanco.setData(LocalDate.of(ANO, MES, 10));
        movimentoBanco.setBanco(banco);
        movimentoBanco.setHistoricoFinanceiro(historico);
        movimentoBanco.setValor(valor);
        movimentoBanco.setEntrada(entrada);
        movimentoBanco.setDescricao("Movimento de teste");
        movimentoBanco.setContaContabil(contaContabil);
        return dataManager.save(movimentoBanco);
    }

    private MovimentoBanco recarregar(MovimentoBanco movimentoBanco) {
        return dataManager.load(MovimentoBanco.class).id(movimentoBanco.getId()).one();
    }

    private MovimentoBanco carregarComItens(MovimentoBanco movimentoBanco) {
        MovimentoBanco carregado = dataManager.load(MovimentoBanco.class).id(movimentoBanco.getId()).one();
        carregado.setItens(carregarItens(movimentoBanco));
        return carregado;
    }

    private List<ItemMovimentoBanco> carregarItens(MovimentoBanco movimentoBanco) {
        return dataManager.load(ItemMovimentoBanco.class)
                .query("select e from ItemMovimentoBanco e where e.movimentoBanco = :movimentoBanco order by e.item")
                .parameter("movimentoBanco", movimentoBanco)
                .list();
    }

    private Lancamento carregarUnicoLancamento() {
        List<Lancamento> lancamentos = dataManager.load(Lancamento.class)
                .query("select e from Lancamento e where e.codEmpresa = :codEmpresa and e.origem = :origem")
                .parameter("codEmpresa", COD_EMPRESA)
                .parameter("origem", "mov banco")
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

    /** Mesmo motivo/ordem do {@code DiversoPagarTest} — Empresa/Banco referenciam ContaContabil. */
    private void limparDadosDaEmpresa() {
        apagar(carregar(Lancamento.class, "select e from Lancamento e where e.codEmpresa = :codEmpresa"));
        apagar(carregar(ItemMovimentoBanco.class, "select e from ItemMovimentoBanco e where e.movimentoBanco.codEmpresa = :codEmpresa"));
        apagar(carregar(MovimentoBanco.class, "select e from MovimentoBanco e where e.codEmpresa = :codEmpresa"));
        apagar(carregar(Empresa.class, "select e from Empresa e where e.codigo = :codEmpresa"));
        apagar(carregar(Banco.class, "select e from Banco e where e.codEmpresa = :codEmpresa"));
        apagar(carregar(ContaContabil.class, "select e from ContaContabil e where e.codEmpresa = :codEmpresa"));
        apagar(carregar(HistoricoFinanceiro.class, "select e from HistoricoFinanceiro e where e.codEmpresa = :codEmpresa"));
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
