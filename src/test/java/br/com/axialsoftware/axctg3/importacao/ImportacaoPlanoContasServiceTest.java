package br.com.axialsoftware.axctg3.importacao;

import br.com.axialsoftware.axctg3.entity.User;
import br.com.axialsoftware.axctg3.entity.contabil.ContaContabil;
import br.com.axialsoftware.axctg3.entity.contabil.SaldoConta;
import br.com.axialsoftware.axctg3.entity.enums.CodNat;
import br.com.axialsoftware.axctg3.entity.enums.SituacaoLote;
import br.com.axialsoftware.axctg3.entity.enums.TipoLote;
import br.com.axialsoftware.axctg3.entity.importacao.ImpContaContabil;
import br.com.axialsoftware.axctg3.entity.importacao.ImpLote;
import br.com.axialsoftware.axctg3.entity.importacao.ImpSaldoConta;
import br.com.axialsoftware.axctg3.service.importacao.ImportacaoPlanoContasService;
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
 * Importação de um lote de plano de contas do legado: monta um lote pequeno como o exportador
 * do projeto Axial gravaria (códigos crus, sem UUID de entidade real) e confere o que o
 * {@link ImportacaoPlanoContasService} grava, avisa e recusa.
 */
@SpringBootTest
@ExtendWith(AuthenticatedAsAdmin.class)
@ActiveProfiles("test")
class ImportacaoPlanoContasServiceTest {

    private static final int COD_EMPRESA = 9306;
    private static final int ANO = 2097;

    @Autowired
    DataManager dataManager;
    @Autowired
    ImportacaoPlanoContasService service;
    @Autowired
    CurrentAuthentication currentAuthentication;

    @BeforeEach
    void setUp() {
        limpar();
        User admin = (User) currentAuthentication.getUser();
        admin.setCodEmpresa(COD_EMPRESA);
        admin.setAnoContabil(ANO);
        admin.setMesContabil(1);
    }

    @AfterEach
    void tearDown() {
        limpar();
    }

    @Test
    void test_importaPlanoComSaldosEHierarquia() {
        ImpLote lote = criarLotePadrao(COD_EMPRESA);

        ImportacaoPlanoContasService.Relatorio relatorio = service.importar(lote.getId());

        assertThat(relatorio.getErros()).isEmpty();
        assertThat(relatorio.getDivergencias()).isEmpty();
        assertThat(relatorio.getAvisos()).anyMatch(a -> a.contains("coringa"));
        assertThat(relatorio.getImportadas()).isEqualTo(7);

        ContaContabil caixa = conta("110000001");
        assertThat(caixa.getCodContaSup()).isEqualTo("110000000");
        assertThat(caixa.getCodNat()).isEqualTo(CodNat.CONTAS_DE_ATIVO);
        assertThat(caixa.getAnalitica()).isTrue();
        assertThat(caixa.getSaldosConta()).hasSize(12);
        SaldoConta janeiro = caixa.getSaldosConta().get(0);
        assertThat(janeiro.getSaldoAnterior()).isEqualByComparingTo("100.00");
        assertThat(janeiro.getDebitoMes()).isEqualByComparingTo("50.00");
        assertThat(janeiro.getCreditoMes()).isEqualByComparingTo("20.00");
        assertThat(caixa.getSaldosConta().get(1).getSaldoAnterior()).isEqualByComparingTo("130.00");

        assertThat(conta("110000000").getCodContaSup()).isEqualTo("100000000");
        assertThat(conta("100000000").getCodContaSup()).isEmpty();
        assertThat(conta("210000001").getCodNat()).isEqualTo(CodNat.CONTAS_DE_PASSIVO);
        ContaContabil coringa = conta("000000000");
        assertThat(coringa.getGrau()).isEqualTo(1);
        assertThat(coringa.getNome()).isEqualTo("DIVERSOS");
        // 12 saldos vindos do lote, não os 12 zerados que o ContaContabilEventListener criaria a mais
        assertThat(conta("200000000").getSaldosConta()).hasSize(12);

        ImpLote recarregado = dataManager.load(ImpLote.class).id(lote.getId()).one();
        assertThat(recarregado.getSituacao()).isEqualTo(SituacaoLote.IMPORTADO);
        assertThat(recarregado.getMensagem()).contains("IMPORTADO");

        // um lote importado não entra de novo
        assertThat(service.importar(lote.getId()).getErros()).anyMatch(e -> e.contains("já foi importado"));
    }

    @Test
    void test_conferirComparaComPlanoJaGravado() {
        ImpLote lote = criarLotePadrao(COD_EMPRESA);
        service.importar(lote.getId());

        // o mesmo lote contra o plano que ele mesmo gravou: nada a apontar
        ImportacaoPlanoContasService.Relatorio igual = service.conferir(lote.getId());
        assertThat(igual.getErros()).isEmpty();
        assertThat(igual.getDivergencias()).isEmpty();
        assertThat(igual.getAvisos()).anyMatch(a -> a.contains("já tem 7 contas gravadas"));

        SaldoConta janeiro = conta("110000001").getSaldosConta().get(0);
        janeiro.setDebitoMes(new BigDecimal("51.00"));
        dataManager.save(janeiro);

        ImportacaoPlanoContasService.Relatorio diferente = service.conferir(lote.getId());
        assertThat(diferente.getDivergencias())
                .containsExactly("Gravado: conta 110000001, mês 1, débito 50.00 no lote, 51.00 gravado.");
    }

    @Test
    void test_divergenciaDeSaldoVaiProRelatorioMasNaoImpede() {
        ImpLote lote = criarLotePadrao(COD_EMPRESA);
        ImpSaldoConta sintetica = dataManager.load(ImpSaldoConta.class)
                .query("select e from ImpSaldoConta e where e.lote = :lote and e.conta = '110000000' and e.mes = 1")
                .parameter("lote", lote)
                .one();
        sintetica.setSaldoAnterior(new BigDecimal("999.00"));
        dataManager.save(sintetica);

        ImportacaoPlanoContasService.Relatorio conferencia = service.conferir(lote.getId());

        assertThat(conferencia.getErros()).isEmpty();
        assertThat(conferencia.getDivergencias())
                .anyMatch(d -> d.contains("Sintética 110000000, mês 1: saldo anterior"))
                .anyMatch(d -> d.contains("Conta 110000000, mês 2"));
        assertThat(contasDaEmpresa()).isEmpty();

        assertThat(service.importar(lote.getId()).getErros()).isEmpty();
        assertThat(contasDaEmpresa()).hasSize(7);
    }

    @Test
    void test_loteDeOutraEmpresaNaoGravaNada() {
        ImpLote lote = criarLotePadrao(COD_EMPRESA + 1);

        ImportacaoPlanoContasService.Relatorio relatorio = service.importar(lote.getId());

        assertThat(relatorio.getErros()).anyMatch(e -> e.contains("Selecione a empresa"));
        assertThat(contasDaEmpresa()).isEmpty();
        assertThat(dataManager.load(ImpLote.class).id(lote.getId()).one().getSituacao())
                .isEqualTo(SituacaoLote.PRONTO);
    }

    @Test
    void test_mascaraDaContaReferencial() {
        assertThat(mascarar("101")).isEqualTo("1.01");
        assertThat(mascarar("10101")).isEqualTo("1.01.01");
        assertThat(mascarar("101010102")).isEqualTo("1.01.01.01.02");
        assertThat(mascarar("1.01")).isEqualTo("1.01");
    }

    private static String mascarar(String codigo) {
        return ImportacaoPlanoContasService.mascararReferencial(codigo);
    }

    /**
     * Ativo: 100000000 > 110000000 > caixa (110000001) e banco (110000002); passivo:
     * 200000000 > fornecedores (210000001); e a coringa 000000000 como vinha do legado (grau 0,
     * sem nome). Caixa abre com 100, debita 50 e credita 20 em janeiro; fornecedores, o espelho.
     */
    private ImpLote criarLotePadrao(int codEmpresa) {
        ImpLote lote = dataManager.create(ImpLote.class);
        lote.setTipo(TipoLote.PLANO_CONTAS);
        lote.setSituacao(SituacaoLote.PRONTO);
        lote.setCodEmpresa(codEmpresa);
        lote.setCodEmpresaLegado(2);
        lote.setAno(ANO);
        lote = dataManager.save(lote);

        SaveContext ctx = new SaveContext();
        conta(ctx, lote, "000000000", "", 0, "N");
        conta(ctx, lote, "100000000", "ATIVO", 1, "N");
        conta(ctx, lote, "110000000", "CIRCULANTE", 2, "N");
        conta(ctx, lote, "110000001", "CAIXA", 3, "S");
        conta(ctx, lote, "110000002", "BANCO", 3, "S");
        conta(ctx, lote, "200000000", "PASSIVO", 1, "N");
        conta(ctx, lote, "210000001", "FORNECEDORES", 2, "S");

        saldos(ctx, lote, "000000000", "0", "0", "0");
        saldos(ctx, lote, "100000000", "100", "50", "20");
        saldos(ctx, lote, "110000000", "100", "50", "20");
        saldos(ctx, lote, "110000001", "100", "50", "20");
        saldos(ctx, lote, "110000002", "0", "0", "0");
        saldos(ctx, lote, "200000000", "-100", "20", "50");
        saldos(ctx, lote, "210000001", "-100", "20", "50");
        dataManager.save(ctx);
        return lote;
    }

    private void conta(SaveContext ctx, ImpLote lote, String codigo, String nome, int grau, String analitica) {
        ImpContaContabil c = dataManager.create(ImpContaContabil.class);
        c.setLote(lote);
        c.setCodigo(codigo);
        c.setNome(nome);
        c.setGrau(grau);
        c.setAnalitica(analitica);
        ctx.saving(c);
    }

    /** Janeiro com o movimento dado; de fevereiro em diante, parado no saldo de fechamento de janeiro. */
    private void saldos(SaveContext ctx, ImpLote lote, String conta, String anterior, String debito, String credito) {
        BigDecimal ant = new BigDecimal(anterior);
        BigDecimal fechamento = ant.add(new BigDecimal(debito)).subtract(new BigDecimal(credito));
        for (int mes = 1; mes <= 12; mes++) {
            ImpSaldoConta s = dataManager.create(ImpSaldoConta.class);
            s.setLote(lote);
            s.setConta(conta);
            s.setMes(mes);
            s.setSaldoAnterior(mes == 1 ? ant : fechamento);
            s.setDebitoMes(mes == 1 ? new BigDecimal(debito) : BigDecimal.ZERO);
            s.setCreditoMes(mes == 1 ? new BigDecimal(credito) : BigDecimal.ZERO);
            s.setSaldoTransf(BigDecimal.ZERO);
            ctx.saving(s);
        }
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

    private List<ContaContabil> contasDaEmpresa() {
        return dataManager.load(ContaContabil.class)
                .query("select e from ContaContabil e where e.codEmpresa = :codEmpresa and e.ano = :ano")
                .parameter("codEmpresa", COD_EMPRESA)
                .parameter("ano", ANO)
                .list();
    }

    private void limpar() {
        apagar(dataManager.load(ContaContabil.class)
                .query("select e from ContaContabil e where e.codEmpresa in :codEmpresas")
                .parameter("codEmpresas", List.of(COD_EMPRESA, COD_EMPRESA + 1))
                .hint(PersistenceHints.SOFT_DELETION, false)
                .list());
        List<ImpLote> lotes = dataManager.load(ImpLote.class)
                .query("select e from ImpLote e where e.codEmpresa in :codEmpresas")
                .parameter("codEmpresas", List.of(COD_EMPRESA, COD_EMPRESA + 1))
                .hint(PersistenceHints.SOFT_DELETION, false)
                .list();
        for (ImpLote lote : lotes) {
            apagar(dataManager.load(ImpSaldoConta.class)
                    .query("select e from ImpSaldoConta e where e.lote = :lote")
                    .parameter("lote", lote)
                    .hint(PersistenceHints.SOFT_DELETION, false)
                    .list());
            apagar(dataManager.load(ImpContaContabil.class)
                    .query("select e from ImpContaContabil e where e.lote = :lote")
                    .parameter("lote", lote)
                    .hint(PersistenceHints.SOFT_DELETION, false)
                    .list());
        }
        apagar(lotes);
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
