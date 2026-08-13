package br.com.axialsoftware.axctg3.contabil;

import br.com.axialsoftware.axctg3.entity.User;
import br.com.axialsoftware.axctg3.entity.cadastros.ConfigRel;
import br.com.axialsoftware.axctg3.entity.cadastros.Empresa;
import br.com.axialsoftware.axctg3.entity.contabil.Bem;
import br.com.axialsoftware.axctg3.entity.contabil.ContaContabil;
import br.com.axialsoftware.axctg3.entity.contabil.Depreciacao;
import br.com.axialsoftware.axctg3.entity.contabil.HistoricoContabil;
import br.com.axialsoftware.axctg3.entity.contabil.Lancamento;
import br.com.axialsoftware.axctg3.entity.enums.CodNat;
import br.com.axialsoftware.axctg3.service.UtilGeralService;
import br.com.axialsoftware.axctg3.service.contabil.DepreciacaoService;
import br.com.axialsoftware.axctg3.test_support.AuthenticatedAsAdmin;
import io.jmix.core.DataManager;
import io.jmix.core.FetchPlan;
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
 * Cobre DepreciacaoService.calcularDepreciacoes/lancarDepreciacoes/listarDepreciacoes —
 * a leva de fluxos de cálculo/lançamento deliberadamente adiada do CRUD de Bem/Depreciacao
 * (ver docs/MIGRACAO.md). O que compileJava/clean test sozinhos não pegariam aqui é
 * justamente o que os testes verificam: o fetchPlan explícito da coleção
 * {@code depreciacaos} e das referências contaContabilDepr/contaContabilDespDepr (o
 * default do DataManager não as carrega), e o carimbo automático de
 * codEmpresa/ano/mes/numero pelo LancamentoEventListener ao gravar o Lancamento da
 * depreciação.
 * <p>
 * emitirRelatorio() engole qualquer exceção da emissão do PDF em si (log.info), então os
 * testes não verificam o PDF — verificam o efeito colateral no banco, que é o que
 * realmente importa (Depreciacao/Bem/Lancamento criados corretamente).
 */
@SpringBootTest
@ExtendWith(AuthenticatedAsAdmin.class)
@ActiveProfiles("test")
class DepreciacaoServiceTest {

    private static final int COD_EMPRESA = 9302;
    private static final int ANO = 2098;
    private static final int MES = 3;
    private static final LocalDate DATA_DEPRECIACAO = LocalDate.of(ANO, MES, 31);

    @Autowired
    DataManager dataManager;
    @Autowired
    DepreciacaoService depreciacaoService;
    @Autowired
    UtilGeralService utilGeralService;
    @Autowired
    CurrentAuthentication currentAuthentication;

    private ContaContabil contaAtivo;
    private ContaContabil contaDepr;
    private ContaContabil contaDespDepr;
    private HistoricoContabil historico;
    private Bem bem;
    private ConfigRel configRel;

    @BeforeEach
    void setUp() {
        User admin = (User) currentAuthentication.getUser();
        admin.setCodEmpresa(COD_EMPRESA);
        admin.setAnoContabil(ANO);
        admin.setMesContabil(MES);

        limparDadosDaEmpresa();

        contaAtivo = criarConta("9401", "Móveis e utensílios");
        contaDepr = criarConta("9402", "Depreciação acumulada");
        contaDespDepr = criarConta("9403", "Despesa de depreciação");
        historico = criarHistorico(1, "Depreciação mensal");

        // UtilGeralService.getNomeEmpresa()/getLogoEmpresa() (usados na emissão dos
        // relatórios) fazem .one() em Empresa pelo codEmpresa da sessão — sem essa linha
        // dá NoResultException antes de qualquer coisa do fluxo de depreciação rodar.
        Empresa empresa = dataManager.create(Empresa.class);
        empresa.setCodigo(COD_EMPRESA);
        empresa.setNome("Empresa de teste");
        empresa.setApelido("Teste");
        dataManager.save(empresa);

        bem = dataManager.create(Bem.class);
        bem.setCodigo(1L);
        bem.setDescricao("Mesa de reunião");
        bem.setDataCompra(LocalDate.of(ANO, 1, 10));
        bem.setValorCompra(new BigDecimal("1200.00"));
        bem.setTaxaDepr(new BigDecimal("10.00")); // taxa ANUAL: 1200 * 10% / 12 = 10,00/mês
        bem.setContaContabil(contaAtivo);
        bem.setContaContabilDepr(contaDepr);
        bem.setContaContabilDespDepr(contaDespDepr);
        bem = dataManager.save(bem);

        configRel = utilGeralService.prepararConfigRel();
        configRel.setDataDepreciacao(DATA_DEPRECIACAO);
        configRel.setHistoricoDepreciacao(historico.getCodigo());
        configRel = dataManager.save(configRel);
    }

    @Test
    void test_calcularDepreciacoesCriaDepreciacaoEAtualizaOBem() {
        depreciacaoService.calcularDepreciacoes(configRel);

        List<Depreciacao> depreciacoes = dataManager.load(Depreciacao.class)
                .query("select e from Depreciacao e where e.bem = :bem")
                .parameter("bem", bem)
                .list();
        assertThat(depreciacoes).hasSize(1);
        // 1200,00 * 10% / 12 = 10,00/mês
        assertThat(depreciacoes.get(0).getValorDepr()).isEqualByComparingTo("10.00");
        assertThat(depreciacoes.get(0).getDataDepr()).isEqualTo(DATA_DEPRECIACAO);

        Bem recarregado = dataManager.load(Bem.class).id(bem.getId()).one();
        assertThat(recarregado.getDeprAcum()).isEqualByComparingTo("10.00");
        assertThat(recarregado.getDataDepr()).isEqualTo(DATA_DEPRECIACAO);

        // rodar de novo sem avançar a data não deve gerar uma segunda depreciação para o
        // mesmo bem: a query de elegibilidade exige dataDepr < :dataDepreciacao
        depreciacaoService.calcularDepreciacoes(configRel);
        depreciacoes = dataManager.load(Depreciacao.class)
                .query("select e from Depreciacao e where e.bem = :bem")
                .parameter("bem", bem)
                .list();
        assertThat(depreciacoes).hasSize(1);
    }

    @Test
    void test_lancarDepreciacoesCriaLancamentoEEIdempotente() {
        depreciacaoService.calcularDepreciacoes(configRel);
        depreciacaoService.lancarDepreciacoes(configRel);

        Depreciacao depreciacao = dataManager.load(Depreciacao.class)
                .query("select e from Depreciacao e where e.bem = :bem")
                .parameter("bem", bem)
                .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE))
                .one();
        assertThat(depreciacao.getLancamento()).isNotNull();

        Lancamento lancamento = dataManager.load(Lancamento.class)
                .query("select e from Lancamento e where e.numero = :numero and e.codEmpresa = :codEmpresa")
                .parameter("numero", depreciacao.getLancamento().intValue())
                .parameter("codEmpresa", COD_EMPRESA)
                .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE)
                        .add("contaDevedora", FetchPlan.BASE)
                        .add("contaCredora", FetchPlan.BASE)
                        .add("historicoContabil", FetchPlan.BASE))
                .one();
        assertThat(lancamento.getCodEmpresa()).isEqualTo(COD_EMPRESA);
        assertThat(lancamento.getAno()).isEqualTo(ANO);
        assertThat(lancamento.getMes()).isEqualTo(MES);
        assertThat(lancamento.getDia()).isEqualTo(DATA_DEPRECIACAO.getDayOfMonth());
        assertThat(lancamento.getValor()).isEqualByComparingTo("10.00");
        assertThat(lancamento.getContaDevedora().getCodigo()).isEqualTo(contaDespDepr.getCodigo());
        assertThat(lancamento.getContaCredora().getCodigo()).isEqualTo(contaDepr.getCodigo());
        assertThat(lancamento.getHistoricoContabil().getCodigo()).isEqualTo(historico.getCodigo());
        assertThat(lancamento.getOrigem()).isEqualTo("depr");

        // idempotência: rodar lançamento de novo não deve criar um segundo Lancamento
        // pra depreciação que já foi lançada
        depreciacaoService.lancarDepreciacoes(configRel);
        long total = dataManager.load(Lancamento.class)
                .query("select e from Lancamento e where e.codEmpresa = :codEmpresa and e.origem = 'depr'")
                .parameter("codEmpresa", COD_EMPRESA)
                .list()
                .size();
        assertThat(total).isEqualTo(1);
    }

    @Test
    void test_listarDepreciacoesNaoLancaExcecao() {
        depreciacaoService.calcularDepreciacoes(configRel);

        // Só garante que a query/fetchPlan da listagem não quebra em runtime — a emissão
        // do PDF em si engole exceção (RelatorioService.emitirRelatorio).
        depreciacaoService.listarDepreciacoes(configRel, true);
        depreciacaoService.listarDepreciacoes(configRel, false);
    }

    @AfterEach
    void tearDown() {
        limparDadosDaEmpresa();
        if (configRel != null) {
            dataManager.remove(configRel);
        }
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

    private HistoricoContabil criarHistorico(int codigo, String descricao) {
        HistoricoContabil historico = dataManager.create(HistoricoContabil.class);
        historico.setCodigo(codigo);
        historico.setCodEmpresa(COD_EMPRESA);
        historico.setDescricao(descricao);
        return dataManager.save(historico);
    }

    private void limparDadosDaEmpresa() {
        apagar(carregar(Lancamento.class,
                "select e from Lancamento e where e.codEmpresa = :codEmpresa"));
        apagar(carregar(Depreciacao.class,
                "select e from Depreciacao e where e.bem.codEmpresa = :codEmpresa"));
        apagar(carregar(Bem.class,
                "select e from Bem e where e.codEmpresa = :codEmpresa"));
        apagar(carregar(HistoricoContabil.class,
                "select e from HistoricoContabil e where e.codEmpresa = :codEmpresa"));
        apagar(carregar(ContaContabil.class,
                "select e from ContaContabil e where e.codEmpresa = :codEmpresa"));
        apagar(carregar(Empresa.class,
                "select e from Empresa e where e.codigo = :codEmpresa"));
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
