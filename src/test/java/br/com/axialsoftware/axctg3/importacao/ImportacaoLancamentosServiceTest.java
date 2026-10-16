package br.com.axialsoftware.axctg3.importacao;

import br.com.axialsoftware.axctg3.entity.User;
import br.com.axialsoftware.axctg3.entity.cadastros.Empresa;
import br.com.axialsoftware.axctg3.entity.contabil.ContaContabil;
import br.com.axialsoftware.axctg3.entity.contabil.HistoricoContabil;
import br.com.axialsoftware.axctg3.entity.contabil.Lancamento;
import br.com.axialsoftware.axctg3.entity.enums.SituacaoLote;
import br.com.axialsoftware.axctg3.entity.enums.TipoLote;
import br.com.axialsoftware.axctg3.entity.importacao.ImpContaContabil;
import br.com.axialsoftware.axctg3.entity.importacao.ImpLancamento;
import br.com.axialsoftware.axctg3.entity.importacao.ImpLote;
import br.com.axialsoftware.axctg3.entity.importacao.ImpSaldoConta;
import br.com.axialsoftware.axctg3.service.importacao.ImportacaoLancamentosService;
import br.com.axialsoftware.axctg3.service.importacao.ImportacaoPlanoContasService;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Importação de um lote de lançamentos do legado sobre um plano de contas já importado com os
 * saldos: os lançamentos entram com número/período do legado, na empresa do lote, sem mexer
 * nos saldos, e a conferência compara lançamentos × saldos do plano.
 * <p>
 * Movimento do ano 2097: em janeiro, venda à vista (caixa × receita, 100); em fevereiro, uma
 * partida simples de 10 só a débito do caixa (o crédito vai para a coringa); em dezembro, o
 * encerramento da receita contra lucros acumulados (100).
 */
@SpringBootTest
@ExtendWith(AuthenticatedAsAdmin.class)
@ActiveProfiles("test")
class ImportacaoLancamentosServiceTest {

    private static final int COD_EMPRESA = 9308;
    private static final int ANO = 2097;

    @Autowired
    DataManager dataManager;
    @Autowired
    ImportacaoPlanoContasService planoService;
    @Autowired
    ImportacaoLancamentosService service;
    @Autowired
    CurrentAuthentication currentAuthentication;
    @Autowired
    Sequences sequences;

    @BeforeEach
    void setUp() {
        limpar();
        Empresa empresa = dataManager.create(Empresa.class);
        empresa.setCodigo(COD_EMPRESA);
        empresa.setNome("Empresa de teste da importação de lançamentos");
        empresa.setApelido("Imp lanc");
        dataManager.save(empresa);
        HistoricoContabil historico = dataManager.create(HistoricoContabil.class);
        historico.setCodigo(1);
        historico.setCodEmpresa(COD_EMPRESA);
        historico.setDescricao("VENDA A VISTA");
        dataManager.save(historico);
        // a sessão aponta para outra empresa e outro período: a importação não pode usá-los
        User admin = (User) currentAuthentication.getUser();
        admin.setCodEmpresa(COD_EMPRESA + 1);
        admin.setAnoContabil(ANO + 1);
        admin.setMesContabil(5);

        ImpLote plano = criarLotePlano();
        assertThat(planoService.importar(plano.getId()).getErros()).isEmpty();
    }

    @AfterEach
    void tearDown() {
        limpar();
    }

    @Test
    void test_importaSemRecalcularSaldosEAjustaSequence() {
        ImpLote lote = criarLoteLancamentos();

        ImportacaoPlanoContasService.Relatorio relatorio = service.importar(lote.getId());

        assertThat(relatorio.getErros()).isEmpty();
        assertThat(relatorio.getDivergencias()).isEmpty();
        assertThat(relatorio.getAvisos())
                .anyMatch(a -> a.contains("1 lançamento(s) de partida simples"))
                .anyMatch(a -> a.contains("1 lançamento(s) de dezembro reconhecidos como encerramento"));
        assertThat(relatorio.getImportadas()).isEqualTo(3);

        List<Lancamento> gravados = lancamentos();
        assertThat(gravados).hasSize(3)
                .allSatisfy(l -> {
                    assertThat(l.getCodEmpresa()).isEqualTo(COD_EMPRESA);
                    assertThat(l.getAno()).isEqualTo(ANO);
                });
        Lancamento venda = gravados.get(0);
        assertThat(venda.getMes()).isEqualTo(1);
        assertThat(venda.getNumero()).isEqualTo(7);
        assertThat(venda.getDataLancamento()).isEqualTo(LocalDate.of(ANO, 1, 15));
        assertThat(venda.getDia()).isEqualTo(15);
        assertThat(venda.getContaDevedora().getCodigo()).isEqualTo("110000001");
        assertThat(venda.getHistoricoContabil().getCodigo()).isEqualTo(1);
        assertThat(venda.getComplementoHistorico()).isEqualTo("NF 123");
        assertThat(venda.getOrigem()).isNull();
        assertThat(gravados.get(1).getContaCredora().getCodigo()).isEqualTo("000000000");
        assertThat(gravados.get(2).getOrigem()).isEqualTo("enc");

        // saldos continuam os do plano: a venda não foi somada outra vez ao caixa
        ContaContabil caixa = conta("110000001");
        assertThat(caixa.getSaldosConta().get(0).getDebitoMes()).isEqualByComparingTo("100.00");
        assertThat(caixa.getSaldosConta().get(1).getDebitoMes()).isEqualByComparingTo("10.00");

        // a sequence de janeiro foi posta no maior número importado (7). No Postgres o Jmix faz
        // setval(7) e o próximo é 8; no HSQLDB de teste faz "restart with 7" e o próximo é 7 —
        // diferença do SequenceSupport de cada banco, não da importação
        String nome = "lancamento_seq_" + COD_EMPRESA + "_" + ANO + "01";
        assertThat(sequences.createNextValue(Sequence.withName(nome))).isBetween(7L, 8L);

        ImpLote recarregado = dataManager.load(ImpLote.class).id(lote.getId()).one();
        assertThat(recarregado.getSituacao()).isEqualTo(SituacaoLote.IMPORTADO);
        assertThat(service.importar(lote.getId()).getErros()).anyMatch(e -> e.contains("já foi importado"));

        // conferir o mesmo lote contra o que ele gravou: nada a apontar
        ImportacaoPlanoContasService.Relatorio igual = service.conferir(lote.getId());
        assertThat(igual.getErros()).isEmpty();
        assertThat(igual.getDivergencias()).isEmpty();
        assertThat(igual.getAvisos()).anyMatch(a -> a.contains("já tem 3 lançamentos gravados"));
    }

    @Test
    void test_lancamentosQueNaoBatemComOsSaldosVaoProRelatorio() {
        ImpLote lote = criarLoteLancamentos();
        ImpLancamento venda = dataManager.load(ImpLancamento.class)
                .query("select e from ImpLancamento e where e.lote = :lote and e.mes = 1")
                .parameter("lote", lote)
                .one();
        venda.setValor(new BigDecimal("90.00"));
        dataManager.save(venda);

        ImportacaoPlanoContasService.Relatorio relatorio = service.conferir(lote.getId());

        assertThat(relatorio.getErros()).isEmpty();
        assertThat(relatorio.getDivergencias())
                .contains("Conta 110000001, mês 1: débito 100.00 no plano, 90.00 nos lançamentos.")
                .contains("Conta 311000001, mês 1: crédito 100.00 no plano, 90.00 nos lançamentos.");
        assertThat(lancamentos()).isEmpty();
    }

    @Test
    void test_codigoInexistenteNaoGravaNada() {
        ImpLote lote = criarLoteLancamentos();
        ImpLancamento venda = dataManager.load(ImpLancamento.class)
                .query("select e from ImpLancamento e where e.lote = :lote and e.mes = 1")
                .parameter("lote", lote)
                .one();
        venda.setHistorico(99);
        venda.setCredora("399999999");
        dataManager.save(venda);

        ImportacaoPlanoContasService.Relatorio relatorio = service.importar(lote.getId());

        assertThat(relatorio.getErros())
                .anyMatch(e -> e.startsWith("Conta 399999999 não existe no plano de " + ANO))
                .anyMatch(e -> e.startsWith("Histórico 99 não cadastrado"));
        assertThat(lancamentos()).isEmpty();
        assertThat(dataManager.load(ImpLote.class).id(lote.getId()).one().getSituacao())
                .isEqualTo(SituacaoLote.PRONTO);
    }

    // ---------------------------------------------------------------- lotes

    /** (código, nome, grau, analítica) do plano do teste, em ordem de código. */
    private static final String[][] PLANO = {
            {"000000000", "", "0", "S"},
            {"100000000", "ATIVO", "1", "N"},
            {"110000001", "CAIXA", "2", "S"},
            {"200000000", "PASSIVO", "1", "N"},
            {"240000000", "PATRIMONIO LIQUIDO", "2", "N"},
            {"245010001", "LUCROS ACUMULADOS", "3", "S"},
            {"300000000", "RESULTADO", "1", "N"},
            {"311000001", "RECEITA DE VENDAS", "2", "S"},
    };

    /** Saldos do plano coerentes com o movimento do ano descrito na classe. */
    private ImpLote criarLotePlano() {
        ImpLote lote = novoLote(TipoLote.PLANO_CONTAS);
        SaveContext ctx = new SaveContext();
        for (String[] c : PLANO) {
            ImpContaContabil conta = dataManager.create(ImpContaContabil.class);
            conta.setLote(lote);
            conta.setCodigo(c[0]);
            conta.setNome(c[1]);
            conta.setGrau(Integer.valueOf(c[2]));
            conta.setAnalitica(c[3]);
            ctx.saving(conta);
        }
        // movimento por conta: {mês, débito, crédito}
        Map<String, int[][]> movimento = new LinkedHashMap<>();
        movimento.put("000000000", new int[][]{{2, 0, 10}});
        movimento.put("110000001", new int[][]{{1, 100, 0}, {2, 10, 0}});
        movimento.put("100000000", new int[][]{{1, 100, 0}, {2, 10, 0}});
        movimento.put("245010001", new int[][]{{12, 0, 100}});
        movimento.put("240000000", new int[][]{{12, 0, 100}});
        movimento.put("200000000", new int[][]{{12, 0, 100}});
        movimento.put("311000001", new int[][]{{1, 0, 100}, {12, 100, 0}});
        movimento.put("300000000", new int[][]{{1, 0, 100}, {12, 100, 0}});
        for (String[] c : PLANO) {
            int[][] mov = movimento.getOrDefault(c[0], new int[0][]);
            BigDecimal anterior = BigDecimal.ZERO;
            for (int mes = 1; mes <= 12; mes++) {
                BigDecimal deb = BigDecimal.ZERO, cre = BigDecimal.ZERO;
                for (int[] m : mov) {
                    if (m[0] == mes) {
                        deb = BigDecimal.valueOf(m[1]);
                        cre = BigDecimal.valueOf(m[2]);
                    }
                }
                ImpSaldoConta s = dataManager.create(ImpSaldoConta.class);
                s.setLote(lote);
                s.setConta(c[0]);
                s.setMes(mes);
                s.setSaldoAnterior(anterior);
                s.setDebitoMes(deb);
                s.setCreditoMes(cre);
                // receita encerrada em dezembro com saldo credor de 100 (legado guarda também na sintética)
                boolean transferida = mes == 12 && (c[0].equals("311000001") || c[0].equals("300000000"));
                s.setSaldoTransf(transferida ? new BigDecimal("-100.00") : BigDecimal.ZERO);
                ctx.saving(s);
                anterior = anterior.add(deb).subtract(cre);
            }
        }
        dataManager.save(ctx);
        return lote;
    }

    private ImpLote criarLoteLancamentos() {
        ImpLote lote = novoLote(TipoLote.LANCAMENTOS);
        SaveContext ctx = new SaveContext();
        ctx.saving(lancamento(lote, 7, 1, LocalDate.of(ANO, 1, 15), "110000001", "311000001", 1, "NF 123", "100.00"));
        ctx.saving(lancamento(lote, 1, 2, LocalDate.of(ANO, 2, 3), "110000001", "", null, null, "10.00"));
        ctx.saving(lancamento(lote, 1, 12, LocalDate.of(ANO, 12, 31), "311000001", "245010001", 1, null, "100.00"));
        dataManager.save(ctx);
        return lote;
    }

    private ImpLancamento lancamento(ImpLote lote, int numero, int mes, LocalDate data, String devedora,
                                     String credora, Integer historico, String complemento, String valor) {
        ImpLancamento l = dataManager.create(ImpLancamento.class);
        l.setLote(lote);
        l.setNumero(numero);
        l.setAno(ANO);
        l.setMes(mes);
        l.setDia(data.getDayOfMonth());
        l.setDataLancamento(data);
        l.setDevedora(devedora);
        l.setCredora(credora);
        l.setHistorico(historico);
        l.setComplementoHistorico(complemento);
        l.setValor(new BigDecimal(valor));
        return l;
    }

    private ImpLote novoLote(TipoLote tipo) {
        ImpLote lote = dataManager.create(ImpLote.class);
        lote.setTipo(tipo);
        lote.setSituacao(SituacaoLote.PRONTO);
        lote.setCodEmpresa(COD_EMPRESA);
        lote.setCodEmpresaLegado(4);
        lote.setAno(ANO);
        return dataManager.save(lote);
    }

    // ---------------------------------------------------------------- consultas e limpeza

    private List<Lancamento> lancamentos() {
        return dataManager.load(Lancamento.class)
                .query("select e from Lancamento e where e.codEmpresa = :codEmpresa order by e.mes, e.numero")
                .parameter("codEmpresa", COD_EMPRESA)
                .fetchPlan(fp -> fp.addFetchPlan("_base")
                        .add("contaDevedora", "_base")
                        .add("contaCredora", "_base")
                        .add("historicoContabil", "_base"))
                .list();
    }

    private ContaContabil conta(String codigo) {
        return dataManager.load(ContaContabil.class)
                .query("select e from ContaContabil e where e.codigo = :codigo and e.codEmpresa = :codEmpresa and e.ano = :ano")
                .parameter("codigo", codigo)
                .parameter("codEmpresa", COD_EMPRESA)
                .parameter("ano", ANO)
                .fetchPlan(fp -> fp.addFetchPlan("_base").add("saldosConta", "_base"))
                .one();
    }

    private void limpar() {
        apagar(carregar(Lancamento.class, "select e from Lancamento e where e.codEmpresa = :cod"));
        apagar(carregar(ContaContabil.class, "select e from ContaContabil e where e.codEmpresa = :cod"));
        apagar(carregar(HistoricoContabil.class, "select e from HistoricoContabil e where e.codEmpresa = :cod"));
        apagar(carregar(ImpLancamento.class, "select e from ImpLancamento e where e.lote.codEmpresa = :cod"));
        apagar(carregar(ImpSaldoConta.class, "select e from ImpSaldoConta e where e.lote.codEmpresa = :cod"));
        apagar(carregar(ImpContaContabil.class, "select e from ImpContaContabil e where e.lote.codEmpresa = :cod"));
        apagar(carregar(ImpLote.class, "select e from ImpLote e where e.codEmpresa = :cod"));
        apagar(carregar(Empresa.class, "select e from Empresa e where e.codigo = :cod"));
    }

    private <T> List<T> carregar(Class<T> classe, String jpql) {
        return dataManager.load(classe)
                .query(jpql)
                .parameter("cod", COD_EMPRESA)
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
