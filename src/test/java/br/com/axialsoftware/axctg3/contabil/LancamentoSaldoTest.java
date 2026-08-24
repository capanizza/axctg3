package br.com.axialsoftware.axctg3.contabil;

import br.com.axialsoftware.axctg3.entity.User;
import br.com.axialsoftware.axctg3.entity.contabil.ContaContabil;
import br.com.axialsoftware.axctg3.entity.contabil.Lancamento;
import br.com.axialsoftware.axctg3.entity.contabil.SaldoConta;
import br.com.axialsoftware.axctg3.entity.enums.CodNat;
import br.com.axialsoftware.axctg3.test_support.AuthenticatedAsAdmin;
import io.jmix.core.DataManager;
import io.jmix.core.security.CurrentAuthentication;
import io.jmix.data.PersistenceHints;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Cobre {@code LancamentoService.atualizarSaldos}/{@code excluirSaldosAnteriores}
 * (via {@code LancamentoEventListener}) — a área que o CLAUDE.md marca como de maior
 * risco do projeto. Usa período/empresa sintéticos pra não cruzar com outro dado do
 * HSQLDB de teste, que é um arquivo compartilhado entre execuções.
 */
@SpringBootTest
@ExtendWith(AuthenticatedAsAdmin.class)
@ActiveProfiles("test")
public class LancamentoSaldoTest {

    private static final int COD_EMPRESA = 9002;
    private static final int ANO = 2098;
    private static final int MES = 7;

    @Autowired
    DataManager dataManager;

    @Autowired
    CurrentAuthentication currentAuthentication;

    private ContaContabil devedora;
    private ContaContabil credora;

    @BeforeEach
    void setUp() {
        User admin = (User) currentAuthentication.getUser();
        admin.setCodEmpresa(COD_EMPRESA);
        admin.setAnoContabil(ANO);
        admin.setMesContabil(MES);

        limpar();

        devedora = criarConta("9001", "Caixa de teste", 1, "9001");
        credora = criarConta("9002", "Receita de teste", 1, "9002");
    }

    @AfterEach
    void tearDown() {
        limpar();
    }

    @Test
    void test_criarEExcluirLancamentoReverteSaldo() {
        Lancamento lancamento = dataManager.create(Lancamento.class);
        lancamento.setContaDevedora(devedora);
        lancamento.setContaCredora(credora);
        lancamento.setValor(new BigDecimal("150.00"));
        lancamento.setDia(1);
        lancamento = dataManager.save(lancamento);

        assertThat(saldoDoMes(devedora).getDebitoMes()).isEqualByComparingTo("150.00");
        assertThat(saldoDoMes(credora).getCreditoMes()).isEqualByComparingTo("150.00");

        dataManager.remove(lancamento);

        assertThat(saldoDoMes(devedora).getDebitoMes()).isEqualByComparingTo("0.00");
        assertThat(saldoDoMes(credora).getCreditoMes()).isEqualByComparingTo("0.00");
    }

    /**
     * Regressão do bug real: conta com {@code grau} &gt; 1 cujo {@code codContaSup} não
     * resolve pra nenhuma conta do ano/empresa (cadeia quebrada — cenário plausível se
     * "criar próximo exercício" for interrompido no meio, ou erro de digitação). Antes do
     * fix, {@code atualizarSaldosGrupo} engolia a exceção do load da conta-pai sem
     * reatribuir {@code contaAux}, e o {@code while(true)} girava pra sempre reaplicando o
     * mesmo delta — travava a requisição. Agora tem que falhar alto e rápido.
     */
    @Test
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void test_cadeiaDeContasQuebradaFalhaAltoEmVezDeTravar() {
        ContaContabil folhaComPaiInexistente = criarConta("9001.01", "Caixa filha órfã", 2, "9999");

        Lancamento lancamento = dataManager.create(Lancamento.class);
        lancamento.setContaDevedora(folhaComPaiInexistente);
        lancamento.setContaCredora(credora);
        lancamento.setValor(new BigDecimal("50.00"));
        lancamento.setDia(1);

        assertThatThrownBy(() -> dataManager.save(lancamento))
                .hasStackTraceContaining("cadeia de contas quebrada")
                .hasStackTraceContaining("9999");
    }

    private SaldoConta saldoDoMes(ContaContabil conta) {
        return dataManager.load(SaldoConta.class)
                .query("select e from SaldoConta e where e.contaContabil = :conta and e.mes = :mes")
                .parameter("conta", conta)
                .parameter("mes", MES)
                .one();
    }

    private ContaContabil criarConta(String codigo, String nome, int grau, String codContaSup) {
        ContaContabil conta = dataManager.create(ContaContabil.class);
        conta.setCodigo(codigo);
        conta.setNome(nome);
        conta.setAno(ANO);
        conta.setCodEmpresa(COD_EMPRESA);
        conta.setGrau(grau);
        conta.setCodContaSup(codContaSup);
        conta.setAnalitica(true);
        conta.setCodNat(CodNat.CONTAS_DE_ATIVO);
        return dataManager.save(conta);
    }

    private void limpar() {
        apagar(carregar(Lancamento.class,
                "select e from Lancamento e where e.codEmpresa = :codEmpresa"));
        apagar(carregar(SaldoConta.class,
                "select e from SaldoConta e where e.contaContabil.codEmpresa = :codEmpresa"));
        apagar(carregar(ContaContabil.class,
                "select e from ContaContabil e where e.codEmpresa = :codEmpresa"));
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
        // SKIP_ENTITY_CHANGED_EVENT: limpeza não deve disparar o estorno de saldos do
        // LancamentoEventListener, que em caso de erro tenta abrir um diálogo — não há UI
        // aqui. Mesmo padrão de ImportarLancamentosTest.
        dataManager.save(new io.jmix.core.SaveContext()
                .setHint(PersistenceHints.SOFT_DELETION, false)
                .setHint(PersistenceHints.SKIP_ENTITY_CHANGED_EVENT, true)
                .removing(entidades.toArray()));
    }
}
