package br.com.axialsoftware.axctg3.importacao;

import br.com.axialsoftware.axctg3.entity.User;
import br.com.axialsoftware.axctg3.entity.cadastros.Empresa;
import br.com.axialsoftware.axctg3.entity.enums.SituacaoLote;
import br.com.axialsoftware.axctg3.entity.enums.TipoLote;
import br.com.axialsoftware.axctg3.entity.financeiro.Banco;
import br.com.axialsoftware.axctg3.entity.financeiro.HistoricoFinanceiro;
import br.com.axialsoftware.axctg3.entity.financeiro.ItemMovimentoBanco;
import br.com.axialsoftware.axctg3.entity.financeiro.MovimentoBanco;
import br.com.axialsoftware.axctg3.entity.importacao.ImpLote;
import br.com.axialsoftware.axctg3.entity.importacao.ImpMovimentoBanco;
import br.com.axialsoftware.axctg3.service.importacao.ImportacaoMovimentoBancoService;
import br.com.axialsoftware.axctg3.service.importacao.ImportacaoPlanoContasService;
import br.com.axialsoftware.axctg3.test_support.AuthenticatedAsAdmin;
import io.jmix.core.DataManager;
import io.jmix.core.FetchPlan;
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
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Importação de um lote de movimento bancário do legado: na empresa do lote, número = REGISTRO do
 * legado, item 1 já lançado, sequence ajustada, movimento sem valor ignorado e reimportação que
 * atualiza — sem passar por cima do que foi criado no próprio axctg3.
 */
@SpringBootTest
@ExtendWith(AuthenticatedAsAdmin.class)
@ActiveProfiles("test")
class ImportacaoMovimentoBancoServiceTest {

    private static final int COD_EMPRESA = 9337;
    private static final int ANO = 2025;

    @Autowired
    DataManager dataManager;
    @Autowired
    ImportacaoMovimentoBancoService service;
    @Autowired
    CurrentAuthentication currentAuthentication;
    @Autowired
    Sequences sequences;

    @BeforeEach
    void setUp() {
        limpar();
        Empresa empresa = dataManager.create(Empresa.class);
        empresa.setCodigo(COD_EMPRESA);
        empresa.setNome("Teste da importação de movimento bancário");
        empresa.setApelido("Imp movbanco");
        dataManager.save(empresa);
        Banco banco = dataManager.create(Banco.class);
        banco.setCodigo(1);
        banco.setCodEmpresa(COD_EMPRESA);
        banco.setNome("SICREDI");
        dataManager.save(banco);
        HistoricoFinanceiro h = dataManager.create(HistoricoFinanceiro.class);
        h.setCodigo(215);
        h.setCodEmpresa(COD_EMPRESA);
        h.setNome("TARIFA");
        dataManager.save(h);
        // a sessão aponta para outra empresa: a importação não pode usá-la
        User admin = (User) currentAuthentication.getUser();
        admin.setCodEmpresa(COD_EMPRESA + 1);
    }

    @AfterEach
    void tearDown() {
        limpar();
    }

    @Test
    void test_criaComONumeroDoLegadoJaLancado() {
        ImpLote lote = criarLote(
                linha(2, "2025-02-28", null, "4646.12", "N", "S", "Saldo Final do Dia"),
                linha(1337, "2025-08-25", 215, "104.84", "N", "N", "AV.DEB.- JRS UTIL CH"),
                linha(1338, "2025-08-31", 215, "5.13", "N", "N", "AV.DEB.- TA.CUS.COB."),
                linha(1339, "2025-08-20", 215, "500.00", "S", "N", "AV.CRED."),
                linha(1340, "2025-08-21", 215, null, "S", "N", "sem valor"));

        assertThat(service.conferir(lote.getId()).getErros()).isEmpty();
        assertThat(movimentos()).isEmpty(); // conferir não grava

        ImportacaoPlanoContasService.Relatorio relatorio = service.importar(lote.getId());

        assertThat(relatorio.getErros()).isEmpty();
        assertThat(relatorio.getImportadas()).isEqualTo(4);
        assertThat(relatorio.getAvisos()).anyMatch(a -> a.equals("1 movimento(s) sem valor no legado ignorado(s)."));

        Map<Integer, MovimentoBanco> movimentos = movimentos();
        assertThat(movimentos).containsOnlyKeys(2, 1337, 1338, 1339);
        MovimentoBanco saldo = movimentos.get(2);
        assertThat(saldo.getTipoSaldo()).isTrue();
        assertThat(saldo.getHistoricoFinanceiro()).isNull();
        MovimentoBanco tarifa = movimentos.get(1337);
        assertThat(tarifa.getCodEmpresa()).isEqualTo(COD_EMPRESA);
        assertThat(tarifa.getEntrada()).isFalse();
        assertThat(tarifa.getBanco().getCodigo()).isEqualTo(1);
        assertThat(tarifa.getHistoricoFinanceiro().getCodigo()).isEqualTo(215);
        assertThat(tarifa.getDescricao()).isEqualTo("AV.DEB.- JRS UTIL CH");
        assertThat(movimentos.get(1339).getEntrada()).isTrue();
        List<ItemMovimentoBanco> itens = dataManager.load(ItemMovimentoBanco.class)
                .query("select e from ItemMovimentoBanco e where e.movimentoBanco.codEmpresa = :cod")
                .parameter("cod", COD_EMPRESA)
                .list();
        assertThat(itens).hasSize(4).allMatch(i -> i.getItem() == 1 && i.getLancado());

        // a numeração continua do maior número: no Postgres o próximo é 1340, no HSQLDB de teste 1339
        assertThat(sequences.createNextValue(Sequence.withName("movimento_banco_seq_" + COD_EMPRESA)))
                .isBetween(1339L, 1340L);

        ImpLote recarregado = dataManager.load(ImpLote.class).id(lote.getId()).one();
        assertThat(recarregado.getSituacao()).isEqualTo(SituacaoLote.IMPORTADO);
        assertThat(recarregado.getMensagem())
                .contains("4 criados, 0 alterados, 0 iguais")
                .contains("entradas 500.00, saídas 109.97");
    }

    @Test
    void test_reimportacaoAtualiza() {
        assertThat(service.importar(criarLote(
                linha(1337, "2025-08-25", 215, "104.84", "N", "N", "AV.DEB.")).getId()).getErros()).isEmpty();

        ImportacaoPlanoContasService.Relatorio relatorio = service.importar(criarLote(
                linha(1337, "2025-08-26", 215, "104.84", "N", "N", "AV.DEB.")).getId());

        assertThat(relatorio.getErros()).isEmpty();
        assertThat(relatorio.getAvisos()).anyMatch(a -> a.equals("Movimento 1337: data 2025-08-25 passa a 2025-08-26."));
        assertThat(movimentos().get(1337).getData()).isEqualTo(LocalDate.of(ANO, 8, 26));
    }

    @Test
    void test_movimentoDigitadoNoAxctg3Impede() {
        // movimento digitado no axctg3: o listener numera pela sequence, e o lote traz o mesmo número
        sequences.setCurrentValue(Sequence.withName("movimento_banco_seq_" + COD_EMPRESA), 100);
        ((User) currentAuthentication.getUser()).setCodEmpresa(COD_EMPRESA);
        MovimentoBanco manual = dataManager.create(MovimentoBanco.class);
        manual.setData(LocalDate.of(ANO, 9, 1));
        manual.setBanco(dataManager.load(Banco.class)
                .query("select e from Banco e where e.codEmpresa = :cod").parameter("cod", COD_EMPRESA).one());
        manual.setValor(new BigDecimal("1.00"));
        int numero = dataManager.save(manual).getLancamento();

        assertThat(service.importar(criarLote(
                linha(numero, "2025-09-01", 215, "9.99", "N", "N", "LEGADO")).getId()).getErros())
                .containsExactly("Movimento " + numero + " já existe no axctg3 e não veio da importação do legado.");
    }

    private ImpMovimentoBanco linha(int registro, String data, Integer historico, String valor, String entrada,
                                    String tipoSaldo, String descricao) {
        ImpMovimentoBanco m = dataManager.create(ImpMovimentoBanco.class);
        m.setRegistro(registro);
        m.setData(LocalDate.parse(data));
        m.setHistorico(historico);
        m.setBanco(1);
        m.setValor(valor == null ? null : new BigDecimal(valor));
        m.setEntrada(entrada);
        m.setTipoSaldo(tipoSaldo);
        m.setDocumento("EXTR.BANC.");
        m.setDescricao(descricao);
        m.setCheque(0);
        return m;
    }

    private ImpLote criarLote(ImpMovimentoBanco... linhas) {
        ImpLote lote = dataManager.create(ImpLote.class);
        lote.setTipo(TipoLote.MOVIMENTO_BANCO);
        lote.setSituacao(SituacaoLote.PRONTO);
        lote.setCodEmpresa(COD_EMPRESA);
        lote.setCodEmpresaLegado(1);
        lote.setAno(ANO);
        lote = dataManager.save(lote);
        SaveContext ctx = new SaveContext();
        for (ImpMovimentoBanco l : linhas) {
            l.setLote(lote);
            ctx.saving(l);
        }
        dataManager.save(ctx);
        return lote;
    }

    private Map<Integer, MovimentoBanco> movimentos() {
        return dataManager.load(MovimentoBanco.class)
                .query("select e from MovimentoBanco e where e.codEmpresa = :cod")
                .parameter("cod", COD_EMPRESA)
                .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE)
                        .add("banco", FetchPlan.BASE)
                        .add("historicoFinanceiro", FetchPlan.BASE))
                .list()
                .stream()
                .collect(Collectors.toMap(MovimentoBanco::getLancamento, m -> m));
    }

    private void limpar() {
        apagar(carregar(ItemMovimentoBanco.class, "select e from ItemMovimentoBanco e where e.movimentoBanco.codEmpresa = :cod"));
        apagar(carregar(MovimentoBanco.class, "select e from MovimentoBanco e where e.codEmpresa = :cod"));
        apagar(carregar(ImpMovimentoBanco.class, "select e from ImpMovimentoBanco e where e.lote.codEmpresa = :cod"));
        apagar(carregar(ImpLote.class, "select e from ImpLote e where e.codEmpresa = :cod"));
        apagar(carregar(HistoricoFinanceiro.class, "select e from HistoricoFinanceiro e where e.codEmpresa = :cod"));
        apagar(carregar(Banco.class, "select e from Banco e where e.codEmpresa = :cod"));
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
