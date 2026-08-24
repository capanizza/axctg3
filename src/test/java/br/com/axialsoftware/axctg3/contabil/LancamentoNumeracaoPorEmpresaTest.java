package br.com.axialsoftware.axctg3.contabil;

import br.com.axialsoftware.axctg3.entity.User;
import br.com.axialsoftware.axctg3.entity.contabil.ContaContabil;
import br.com.axialsoftware.axctg3.entity.contabil.Lancamento;
import br.com.axialsoftware.axctg3.entity.enums.CodNat;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regressão do bug real encontrado em 2026-08-24: antes desta correção, o nome da
 * sequence de numeração do Lancamento era só {@code lancamento_seq_<ano><mes>}, sem
 * codEmpresa — todas as empresas competiam pelo mesmo contador num mesmo período. Duas
 * empresas com lançamento no mesmo mês faziam a numeração de uma "pular" por causa da
 * outra, e o dev Postgres do usuário chegou a ficar num estado onde a próxima gravação
 * pra empresa 1 em dez/2025 colidiria com um número que ela mesma já tinha (índice
 * único NUMERO+ANO+MES+COD_EMPRESA). Ver {@code LancamentoEventListener} e
 * {@code SequenciaMigrationService}.
 */
@SpringBootTest
@ExtendWith(AuthenticatedAsAdmin.class)
@ActiveProfiles("test")
class LancamentoNumeracaoPorEmpresaTest {

    private static final int EMPRESA_A = 9101;
    private static final int EMPRESA_B = 9102;
    private static final int ANO = 2097;
    private static final int MES = 5;

    @Autowired
    DataManager dataManager;
    @Autowired
    CurrentAuthentication currentAuthentication;

    @BeforeEach
    void setUp() {
        limpar();
    }

    @AfterEach
    void tearDown() {
        limpar();
    }

    @Test
    void duasEmpresasNoMesmoMesNumeramIndependentemente() {
        // Asserções relativas, não valores absolutos: a sequence é um objeto de banco
        // que sobrevive entre execuções da suíte (HSQLDB de teste é um arquivo
        // compartilhado — limpar() apaga as linhas, não reseta a sequence, mesmo motivo
        // documentado em ImportarLancamentosTest/SequenciaMigrationServiceTest). O que
        // importa provar é a independência entre empresas, não o valor inicial.
        selecionarEmpresa(EMPRESA_A);
        ContaContabil devedoraA = criarConta(EMPRESA_A, "9001", "Caixa A");
        ContaContabil credoraA = criarConta(EMPRESA_A, "9002", "Receita A");

        int a1 = criarLancamento(devedoraA, credoraA, "10.00").getNumero();
        int a2 = criarLancamento(devedoraA, credoraA, "20.00").getNumero();
        assertThat(a2).isEqualTo(a1 + 1);

        // empresa B entra no meio da numeração de A — se as duas ainda dividissem a
        // mesma sequence (bug original), o primeiro lançamento de B sairia em a2+1, não
        // no início da própria faixa de B.
        selecionarEmpresa(EMPRESA_B);
        ContaContabil devedoraB = criarConta(EMPRESA_B, "9001", "Caixa B");
        ContaContabil credoraB = criarConta(EMPRESA_B, "9002", "Receita B");

        int b1 = criarLancamento(devedoraB, credoraB, "30.00").getNumero();
        int b2 = criarLancamento(devedoraB, credoraB, "40.00").getNumero();
        assertThat(b2).isEqualTo(b1 + 1);
        assertThat(b1).isNotEqualTo(a2 + 1);

        // e A continua exatamente de onde parou, sem interferência de B
        selecionarEmpresa(EMPRESA_A);
        int a3 = criarLancamento(devedoraA, credoraA, "50.00").getNumero();
        assertThat(a3).isEqualTo(a2 + 1);
    }

    private void selecionarEmpresa(int codEmpresa) {
        User admin = (User) currentAuthentication.getUser();
        admin.setCodEmpresa(codEmpresa);
        admin.setAnoContabil(ANO);
        admin.setMesContabil(MES);
    }

    private Lancamento criarLancamento(ContaContabil devedora, ContaContabil credora, String valor) {
        Lancamento lancamento = dataManager.create(Lancamento.class);
        lancamento.setContaDevedora(devedora);
        lancamento.setContaCredora(credora);
        lancamento.setValor(new BigDecimal(valor));
        lancamento.setDia(1);
        return dataManager.save(lancamento);
    }

    private ContaContabil criarConta(int codEmpresa, String codigo, String nome) {
        ContaContabil conta = dataManager.create(ContaContabil.class);
        conta.setCodigo(codigo);
        conta.setNome(nome);
        conta.setAno(ANO);
        conta.setCodEmpresa(codEmpresa);
        conta.setGrau(1);
        conta.setCodContaSup(codigo);
        conta.setAnalitica(true);
        conta.setCodNat(CodNat.CONTAS_DE_ATIVO);
        return dataManager.save(conta);
    }

    private void limpar() {
        apagar(carregar(Lancamento.class, EMPRESA_A));
        apagar(carregar(Lancamento.class, EMPRESA_B));
        apagar(carregarContas(EMPRESA_A));
        apagar(carregarContas(EMPRESA_B));
    }

    private List<Lancamento> carregar(Class<Lancamento> entityClass, int codEmpresa) {
        return dataManager.load(entityClass)
                .query("select e from Lancamento e where e.codEmpresa = :codEmpresa")
                .parameter("codEmpresa", codEmpresa)
                .hint(PersistenceHints.SOFT_DELETION, false)
                .list();
    }

    private List<ContaContabil> carregarContas(int codEmpresa) {
        return dataManager.load(ContaContabil.class)
                .query("select e from ContaContabil e where e.codEmpresa = :codEmpresa")
                .parameter("codEmpresa", codEmpresa)
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
