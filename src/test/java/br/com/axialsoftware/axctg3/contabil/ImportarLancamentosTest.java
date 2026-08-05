package br.com.axialsoftware.axctg3.contabil;

import br.com.axialsoftware.axctg3.entity.User;
import br.com.axialsoftware.axctg3.entity.cadastros.ConfigRel;
import br.com.axialsoftware.axctg3.entity.contabil.ContaContabil;
import br.com.axialsoftware.axctg3.entity.contabil.Lancamento;
import br.com.axialsoftware.axctg3.entity.contabil.LancamentoTmp;
import br.com.axialsoftware.axctg3.entity.contabil.SaldoConta;
import br.com.axialsoftware.axctg3.entity.enums.CodNat;
import br.com.axialsoftware.axctg3.service.UtilGeralService;
import br.com.axialsoftware.axctg3.service.contabil.LancamentoService;
import br.com.axialsoftware.axctg3.test_support.AuthenticatedAsAdmin;
import io.jmix.core.DataManager;
import io.jmix.core.SaveContext;
import io.jmix.core.security.CurrentAuthentication;
import io.jmix.data.PersistenceHints;
import io.jmix.data.Sequence;
import io.jmix.data.Sequences;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cobre as peças da importação de lançamentos do sistema antigo — a ação "Importar" da
 * {@code LancamentoListView}. A tarefa de background em si é um laço fino sobre
 * {@code gravarLancamento}; o que dá para verificar sem navegador está aqui.
 * <p>
 * Usa período e empresa sintéticos (2099/07, empresa 9001) para não cruzar com nenhum outro
 * dado do HSQLDB de teste, que é um arquivo compartilhado entre execuções.
 */
@SpringBootTest
@ExtendWith(AuthenticatedAsAdmin.class)
@ActiveProfiles("test")
public class ImportarLancamentosTest {

    private static final int COD_EMPRESA = 9001;
    private static final int ANO = 2099;
    private static final int MES = 7;
    private static final LocalDate DATA_LANCAMENTO = LocalDate.of(ANO, MES, 15);

    @Autowired
    DataManager dataManager;

    @Autowired
    LancamentoService lancamentoService;

    @Autowired
    UtilGeralService utilGeralService;

    @Autowired
    CurrentAuthentication currentAuthentication;

    @Autowired
    Sequences sequences;

    private ContaContabil devedora;
    private ContaContabil credora;
    private LancamentoTmp lancamentoTmp;
    private ConfigRel configRel;

    @BeforeEach
    void setUp() {
        // empresa e período contábil moram no principal — é de lá que UtilGeralService (e
        // portanto os listeners) os leem. Mesmo padrão da SelecionarEmpresaListView.
        User admin = (User) currentAuthentication.getUser();
        admin.setCodEmpresa(COD_EMPRESA);
        admin.setAnoContabil(ANO);
        admin.setMesContabil(MES);

        limparDadosDaEmpresa();

        configRel = utilGeralService.prepararConfigRel();
        delimitar(DATA_LANCAMENTO.withDayOfMonth(1),
                DATA_LANCAMENTO.with(TemporalAdjusters.lastDayOfMonth()));

        devedora = criarConta("9001", "Caixa de teste");
        credora = criarConta("9002", "Receita de teste");
        lancamentoTmp = criarLancamentoTmp(1, new BigDecimal("150.00"));
    }

    @Test
    void test_importaPendenteEDepoisNaoSeRepete() {
        assertThat(lancamentoService.lancamentosTmpPendentes())
                .extracting(LancamentoTmp::getId)
                .containsExactly(lancamentoTmp.getId());

        lancamentoService.gravarLancamento(lancamentoTmp);

        Lancamento importado = dataManager.load(Lancamento.class).id(lancamentoTmp.getId()).one();
        assertThat(importado.getNumero()).isEqualTo(1);
        assertThat(importado.getCodEmpresa()).isEqualTo(COD_EMPRESA);
        assertThat(importado.getAno()).isEqualTo(ANO);
        assertThat(importado.getMes()).isEqualTo(MES);
        assertThat(importado.getValor()).isEqualByComparingTo("150.00");
        assertThat(importado.getContaDevedora().getCodigo()).isEqualTo("9001");
        assertThat(importado.getContaCredora().getCodigo()).isEqualTo("9002");

        // invariante que torna a importação repetível: o que já foi gravado sai da fila, então
        // retomar depois de um cancelamento não colide com a chave primária
        assertThat(lancamentoService.lancamentosTmpPendentes()).isEmpty();
    }

    @Test
    void test_periodoForaDoMesContabilNaoImporta() {
        assertThat(lancamentoService.periodoDelimitadoDentroDoMesContabil()).isTrue();

        delimitar(DATA_LANCAMENTO, DATA_LANCAMENTO.plusMonths(1));

        assertThat(lancamentoService.periodoDelimitadoDentroDoMesContabil()).isFalse();
    }

    @Test
    void test_sequenciaReposicionadaNaFaixaImportada() {
        lancamentoService.ajustarSequenciaLancamento(57);

        String nomeSequencia = "lancamento_seq_" + String.format("%4d%02d", ANO, MES);
        long proximo = sequences.createNextValue(Sequence.withName(nomeSequencia));

        // O SQL de reposicionamento depende do store: no PostgreSQL (dev/produção) é
        // "select setval(seq, N)" e o próximo valor sai N+1; no HSQLDB destes testes é
        // "alter sequence restart with N", que devolve o próprio N. A asserção é sobre a
        // faixa justamente por causa dessa diferença — sem o ajuste, viria 1.
        assertThat(proximo).isBetween(57L, 58L);
    }

    @AfterEach
    void tearDown() {
        limparDadosDaEmpresa();
        if (configRel != null) {
            dataManager.remove(configRel);
        }
    }

    private void delimitar(LocalDate inicial, LocalDate fim) {
        configRel.setDataLancamentoInicial(inicial);
        configRel.setDataLancamentoFinal(fim);
        configRel = dataManager.save(configRel);
    }

    private ContaContabil criarConta(String codigo, String nome) {
        ContaContabil conta = dataManager.create(ContaContabil.class);
        conta.setCodigo(codigo);
        conta.setNome(nome);
        conta.setAno(ANO);
        conta.setCodEmpresa(COD_EMPRESA);
        conta.setGrau(1);                 // grau 1 encerra a subida pela árvore de contas
        conta.setCodContaSup(codigo);
        conta.setAnalitica(true);
        conta.setCodNat(CodNat.CONTAS_DE_ATIVO);
        return dataManager.save(conta);
    }

    private LancamentoTmp criarLancamentoTmp(int numero, BigDecimal valor) {
        LancamentoTmp lancamento = dataManager.create(LancamentoTmp.class);
        lancamento.setNumero(numero);
        lancamento.setAno(ANO);
        lancamento.setMes(MES);
        lancamento.setDia(DATA_LANCAMENTO.getDayOfMonth());
        lancamento.setDataLancamento(DATA_LANCAMENTO);
        lancamento.setCodEmpresa(COD_EMPRESA);
        lancamento.setValor(valor);
        lancamento.setDevedora(devedora.getCodigo());
        lancamento.setCredora(credora.getCodigo());
        return dataManager.save(lancamento);
    }

    /**
     * Limpeza <b>física</b> dos dados da empresa de teste. O índice único de CONTA_CONTABIL
     * (CODIGO, COD_EMPRESA, ANO) não inclui DELETED_DATE, então uma linha excluída logicamente
     * continua ocupando a chave e a execução seguinte não conseguiria recriar a conta — e o
     * HSQLDB destes testes é um arquivo que sobrevive entre execuções. A ordem respeita as FKs:
     * lançamentos, saldos e só então as contas.
     */
    private void limparDadosDaEmpresa() {
        apagar(carregar(Lancamento.class,
                "select e from Lancamento e where e.codEmpresa = :codEmpresa"));
        apagar(carregar(LancamentoTmp.class,
                "select e from LancamentoTmp e where e.codEmpresa = :codEmpresa"));
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
        // LancamentoEventListener, que em caso de erro tenta abrir um diálogo — não há UI aqui.
        dataManager.save(new SaveContext()
                .setHint(PersistenceHints.SOFT_DELETION, false)
                .setHint(PersistenceHints.SKIP_ENTITY_CHANGED_EVENT, true)
                .removing(entidades.toArray()));
    }
}
