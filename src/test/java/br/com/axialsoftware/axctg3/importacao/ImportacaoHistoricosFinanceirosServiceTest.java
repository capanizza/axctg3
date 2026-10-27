package br.com.axialsoftware.axctg3.importacao;

import br.com.axialsoftware.axctg3.entity.User;
import br.com.axialsoftware.axctg3.entity.cadastros.Empresa;
import br.com.axialsoftware.axctg3.entity.contabil.ContaContabil;
import br.com.axialsoftware.axctg3.entity.contabil.HistoricoContabil;
import br.com.axialsoftware.axctg3.entity.enums.CodNat;
import br.com.axialsoftware.axctg3.entity.enums.SituacaoLote;
import br.com.axialsoftware.axctg3.entity.enums.TipoLote;
import br.com.axialsoftware.axctg3.entity.financeiro.HistoricoFinanceiro;
import br.com.axialsoftware.axctg3.entity.importacao.ImpHistoricoFinanceiro;
import br.com.axialsoftware.axctg3.entity.importacao.ImpLote;
import br.com.axialsoftware.axctg3.service.importacao.ImportacaoHistoricosFinanceirosService;
import br.com.axialsoftware.axctg3.service.importacao.ImportacaoPlanoContasService;
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

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Importação de um lote de históricos financeiros do legado: na empresa do lote (não na da
 * sessão), ENT no lado de pagar e SAI no de receber, histórico 0 = sem histórico, e
 * reimportação atualizando só o que mudou.
 */
@SpringBootTest
@ExtendWith(AuthenticatedAsAdmin.class)
@ActiveProfiles("test")
class ImportacaoHistoricosFinanceirosServiceTest {

    private static final int COD_EMPRESA = 9313;

    @Autowired
    DataManager dataManager;
    @Autowired
    ImportacaoHistoricosFinanceirosService service;
    @Autowired
    CurrentAuthentication currentAuthentication;

    @BeforeEach
    void setUp() {
        limpar();
        Empresa empresa = dataManager.create(Empresa.class);
        empresa.setCodigo(COD_EMPRESA);
        empresa.setNome("Teste da importação de históricos financeiros");
        empresa.setApelido("Imp histfin");
        dataManager.save(empresa);
        conta("311010001", 2026);
        conta("333020002", 2026);
        conta("321010001", 2025);
        historicoContabil(61, "VENDA");
        historicoContabil(67, "JUROS");
        // a sessão aponta para outra empresa: a importação não pode usá-la
        User admin = (User) currentAuthentication.getUser();
        admin.setCodEmpresa(COD_EMPRESA + 1);
    }

    @AfterEach
    void tearDown() {
        limpar();
    }

    @Test
    void test_criaComContasEHistoricosDosDoisLados() {
        ImpLote lote = criarLote(
                linha(1, "EMISSAO", "S", "N", null, 0, "311010001", 61),
                linha(4, "BAIXA COM JUROS", "N", "S", "333020002", 67, "321010001", 157));

        ImportacaoPlanoContasService.Relatorio conferencia = service.conferir(lote.getId());
        assertThat(conferencia.getErros()).isEmpty();
        assertThat(historicos()).isEmpty(); // conferir não grava

        ImportacaoPlanoContasService.Relatorio relatorio = service.importar(lote.getId());

        assertThat(relatorio.getErros()).isEmpty();
        assertThat(relatorio.getImportadas()).isEqualTo(2);
        assertThat(relatorio.getAvisos())
                .anyMatch(a -> a.startsWith("Histórico financeiro 4: conta de receber 321010001 não existe"))
                .anyMatch(a -> a.startsWith("Histórico financeiro 4: histórico de receber 157 não existe"))
                .noneMatch(a -> a.startsWith("Histórico financeiro 1: histórico de pagar"));

        Map<Integer, HistoricoFinanceiro> historicos = historicos();
        assertThat(historicos).containsOnlyKeys(1, 4);
        HistoricoFinanceiro emissao = historicos.get(1);
        assertThat(emissao.getEmissao()).isTrue();
        assertThat(emissao.getBaixa()).isFalse();
        assertThat(emissao.getContaContabilPagar()).isNull();
        assertThat(emissao.getHistoricoContabilPagar()).isNull();
        assertThat(emissao.getContaContabilReceber().getCodigo()).isEqualTo("311010001");
        assertThat(emissao.getHistoricoContabilReceber().getCodigo()).isEqualTo(61);
        HistoricoFinanceiro juros = historicos.get(4);
        assertThat(juros.getBaixa()).isTrue();
        assertThat(juros.getContaContabilPagar().getCodigo()).isEqualTo("333020002");
        assertThat(juros.getHistoricoContabilPagar().getCodigo()).isEqualTo(67);
        assertThat(juros.getContaContabilReceber()).isNull();
        assertThat(dataManager.load(HistoricoFinanceiro.class)
                .query("select e from HistoricoFinanceiro e where e.codEmpresa = :cod")
                .parameter("cod", COD_EMPRESA + 1)
                .list()).isEmpty();

        ImpLote recarregado = dataManager.load(ImpLote.class).id(lote.getId()).one();
        assertThat(recarregado.getSituacao()).isEqualTo(SituacaoLote.IMPORTADO);
        assertThat(recarregado.getMensagem()).contains("2 criados, 0 alterados, 0 iguais");
    }

    @Test
    void test_reimportacaoAtualizaSoOQueMudou() {
        service.importar(criarLote(
                linha(1, "EMISSAO", "S", "N", null, 0, "311010001", 61),
                linha(2, "BAIXA", "N", "S", null, 0, null, null)).getId());

        ImportacaoPlanoContasService.Relatorio relatorio = service.importar(criarLote(
                linha(1, "EMISSAO", "S", "N", null, 0, "311010001", 61),
                linha(2, "BAIXA", "N", "S", "333020002", 67, null, null)).getId());

        assertThat(relatorio.getErros()).isEmpty();
        assertThat(relatorio.getImportadas()).isEqualTo(1);
        assertThat(relatorio.getAvisos()).anyMatch(a -> a.equals("Histórico financeiro 2: "
                + "conta de pagar (vazio) passa a \"333020002 (2026)\"; histórico de pagar (vazio) passa a \"67\"."));
        assertThat(historicos().get(2).getContaContabilPagar().getCodigo()).isEqualTo("333020002");
    }

    // empresa com a contabilidade fora (GB): sem históricos contábeis nem plano, o histórico
    // financeiro entra sem eles, com um aviso de cada
    @Test
    void test_semContabilidadeImportaComAviso() {
        apagar(carregar(HistoricoContabil.class, "select e from HistoricoContabil e where e.codEmpresa = :cod"));
        apagar(carregar(ContaContabil.class, "select e from ContaContabil e where e.codEmpresa = :cod"));
        ImpLote lote = criarLote(linha(1, "EMISSAO", "S", "N", null, 0, "311010001", 61));

        ImportacaoPlanoContasService.Relatorio relatorio = service.importar(lote.getId());

        assertThat(relatorio.getErros()).isEmpty();
        assertThat(relatorio.getAvisos())
                .anyMatch(a -> a.contains("não tem históricos contábeis"))
                .anyMatch(a -> a.contains("não tem plano de contas"))
                .noneMatch(a -> a.contains("não existe no plano mais recente") || a.contains("não existe nos históricos"));
        assertThat(historicos()).hasSize(1);
    }

    private void conta(String codigo, int ano) {
        ContaContabil c = dataManager.create(ContaContabil.class);
        c.setCodigo(codigo);
        c.setAno(ano);
        c.setCodEmpresa(COD_EMPRESA);
        c.setNome("CONTA " + codigo);
        c.setGrau(1);
        c.setCodContaSup(codigo);
        c.setAnalitica(true);
        c.setCodNat(CodNat.CONTAS_DE_ATIVO);
        dataManager.save(c);
    }

    private void historicoContabil(int codigo, String descricao) {
        HistoricoContabil h = dataManager.create(HistoricoContabil.class);
        h.setCodigo(codigo);
        h.setCodEmpresa(COD_EMPRESA);
        h.setDescricao(descricao);
        dataManager.save(h);
    }

    private ImpHistoricoFinanceiro linha(Integer codigo, String nome, String emissao, String baixa,
                                         String codContabilEnt, Integer histEnt,
                                         String codContabilSai, Integer histSai) {
        ImpHistoricoFinanceiro l = dataManager.create(ImpHistoricoFinanceiro.class);
        l.setCodigo(codigo);
        l.setNome(nome);
        l.setEmissao(emissao);
        l.setBaixa(baixa);
        l.setParcial("N");
        l.setJuros("N");
        l.setDesconto("N");
        l.setCodContabilEnt(codContabilEnt);
        l.setHistEnt(histEnt);
        l.setCodContabilSai(codContabilSai);
        l.setHistSai(histSai);
        return l;
    }

    private ImpLote criarLote(ImpHistoricoFinanceiro... linhas) {
        ImpLote lote = dataManager.create(ImpLote.class);
        lote.setTipo(TipoLote.HISTORICOS_FINANCEIROS);
        lote.setSituacao(SituacaoLote.PRONTO);
        lote.setCodEmpresa(COD_EMPRESA);
        lote.setCodEmpresaLegado(1);
        lote = dataManager.save(lote);
        SaveContext ctx = new SaveContext();
        for (ImpHistoricoFinanceiro l : linhas) {
            l.setLote(lote);
            ctx.saving(l);
        }
        dataManager.save(ctx);
        return lote;
    }

    private Map<Integer, HistoricoFinanceiro> historicos() {
        return dataManager.load(HistoricoFinanceiro.class)
                .query("select e from HistoricoFinanceiro e where e.codEmpresa = :cod")
                .parameter("cod", COD_EMPRESA)
                .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE)
                        .add("contaContabilPagar", FetchPlan.BASE)
                        .add("historicoContabilPagar", FetchPlan.BASE)
                        .add("contaContabilReceber", FetchPlan.BASE)
                        .add("historicoContabilReceber", FetchPlan.BASE))
                .list()
                .stream()
                .collect(Collectors.toMap(HistoricoFinanceiro::getCodigo, h -> h));
    }

    private void limpar() {
        apagar(carregar(HistoricoFinanceiro.class, "select e from HistoricoFinanceiro e where e.codEmpresa = :cod"));
        apagar(carregar(HistoricoContabil.class, "select e from HistoricoContabil e where e.codEmpresa = :cod"));
        apagar(carregar(ContaContabil.class, "select e from ContaContabil e where e.codEmpresa = :cod"));
        apagar(carregar(ImpHistoricoFinanceiro.class, "select e from ImpHistoricoFinanceiro e where e.lote.codEmpresa = :cod"));
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
