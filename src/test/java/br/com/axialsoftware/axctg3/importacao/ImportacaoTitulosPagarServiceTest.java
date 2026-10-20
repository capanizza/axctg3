package br.com.axialsoftware.axctg3.importacao;

import br.com.axialsoftware.axctg3.entity.User;
import br.com.axialsoftware.axctg3.entity.cadastros.Empresa;
import br.com.axialsoftware.axctg3.entity.cadastros.Parceiro;
import br.com.axialsoftware.axctg3.entity.enums.SituacaoLote;
import br.com.axialsoftware.axctg3.entity.enums.TipoLote;
import br.com.axialsoftware.axctg3.entity.financeiro.Banco;
import br.com.axialsoftware.axctg3.entity.financeiro.HistoricoFinanceiro;
import br.com.axialsoftware.axctg3.entity.financeiro.ItemPagar;
import br.com.axialsoftware.axctg3.entity.financeiro.TituloPagar;
import br.com.axialsoftware.axctg3.entity.importacao.ImpItemPagar;
import br.com.axialsoftware.axctg3.entity.importacao.ImpLote;
import br.com.axialsoftware.axctg3.entity.importacao.ImpTituloPagar;
import br.com.axialsoftware.axctg3.service.importacao.ImportacaoPlanoContasService;
import br.com.axialsoftware.axctg3.service.importacao.ImportacaoTitulosPagarService;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Importação de um lote de títulos a pagar do legado, com emissão e baixas: na empresa do lote,
 * tudo contabilizado, banco em cada baixa, conferência das baixas contra o PAGO do legado,
 * movimento em branco ignorado, sequence de numeração ajustada e reimportação trocando os
 * movimentos — sem nunca apagar o que foi feito no próprio axctg3.
 */
@SpringBootTest
@ExtendWith(AuthenticatedAsAdmin.class)
@ActiveProfiles("test")
class ImportacaoTitulosPagarServiceTest {

    private static final int COD_EMPRESA = 9329;
    private static final int ANO = 2025;

    @Autowired
    DataManager dataManager;
    @Autowired
    ImportacaoTitulosPagarService service;
    @Autowired
    CurrentAuthentication currentAuthentication;
    @Autowired
    Sequences sequences;

    private final List<ImpItemPagar> movimentos = new ArrayList<>();

    @BeforeEach
    void setUp() {
        limpar();
        Empresa empresa = dataManager.create(Empresa.class);
        empresa.setCodigo(COD_EMPRESA);
        empresa.setNome("Teste da importação de títulos a pagar");
        empresa.setApelido("Imp pagar");
        dataManager.save(empresa);
        Parceiro parceiro = dataManager.create(Parceiro.class);
        parceiro.setCodigo(40L);
        parceiro.setCodEmpresa(COD_EMPRESA);
        parceiro.setNome("CLIENTE DE TESTE");
        parceiro.setApelido("CLIENTE");
        parceiro.setCnpj("11222333000181");
        dataManager.save(parceiro);
        banco(1, "SICREDI");
        historico(1, "EMISSAO", true, false, false);
        historico(2, "BAIXA", false, true, false);
        historico(34, "BAIXA COM JUROS", false, true, false);
        // a sessão aponta para outra empresa: a importação não pode usá-la
        User admin = (User) currentAuthentication.getUser();
        admin.setCodEmpresa(COD_EMPRESA + 1);
    }

    @AfterEach
    void tearDown() {
        limpar();
    }

    @Test
    void test_criaComBancoNasBaixasEAjustaASequence() {
        movimentos.clear();
        ImpLote lote = criarLote(
                titulo(100, 10, "1000.00", "0.00"),
                titulo(101, 11, "400.00", "400.00"),
                titulo(102, 170, "500.00", "500.00"));
        mov(100, 1, "2025-03-03", "1000.00", "0", 0);
        // baixas fora de ordem no legado: a importação numera pela data
        mov(101, 2, "2025-04-10", "300.00", "0", 1);
        mov(101, 1, "2025-03-03", "400.00", "0", 0);
        mov(101, 34, "2025-03-20", "100.00", "0", 1);
        movVazio(101, "2025-04-14");
        mov(102, 1, "2025-03-03", "500.00", "0", 0);
        mov(102, 2, "2025-03-30", "450.00", "0", 1);
        gravarMovimentos(lote);

        ImportacaoPlanoContasService.Relatorio conferencia = service.conferir(lote.getId());
        assertThat(conferencia.getErros()).isEmpty();
        assertThat(titulos()).isEmpty(); // conferir não grava

        ImportacaoPlanoContasService.Relatorio relatorio = service.importar(lote.getId());

        assertThat(relatorio.getErros()).isEmpty();
        assertThat(relatorio.getImportadas()).isEqualTo(3);
        assertThat(relatorio.getDivergencias())
                .containsExactly("Título 170: baixas somam 450.00 e o legado diz pago 500.00.");
        assertThat(relatorio.getAvisos()).anyMatch(a -> a.startsWith("1 movimento(s) sem histórico e sem valor"));

        Map<Integer, TituloPagar> titulos = titulos();
        assertThat(titulos).containsOnlyKeys(10, 11, 170);
        TituloPagar t = titulos.get(11);
        assertThat(t.getCodEmpresa()).isEqualTo(COD_EMPRESA);
        assertThat(t.getCreatedBy()).isEqualTo(ImportacaoTitulosPagarService.IMPORTACAO);
        assertThat(t.getDocumento()).isEqualTo("NF 11");
        assertThat(t.getParceiro().getCodigo()).isEqualTo(40L);
        List<ItemPagar> itens = itens(t);
        assertThat(itens).extracting(i -> i.getItem() + " " + i.getHistoricoFinanceiro().getCodigo() + " " + i.getData()
                        + " " + (i.getBanco() == null ? "-" : i.getBanco().getCodigo()))
                .containsExactly("1 1 2025-03-03 -", "2 34 2025-03-20 1", "3 2 2025-04-10 1");
        assertThat(itens).allMatch(ItemPagar::getContabilizado);
        assertThat(itens(titulos.get(10))).hasSize(1); // só a emissão: em aberto

        // a numeração continua do maior número. No Postgres o próximo é 171; no HSQLDB de teste,
        // "restart with 170" faz o próximo ser 170 (mesma diferença da sequence de lançamentos)
        assertThat(sequences.createNextValue(Sequence.withName("titulo_pagar_seq_" + COD_EMPRESA)))
                .isBetween(170L, 171L);

        ImpLote recarregado = dataManager.load(ImpLote.class).id(lote.getId()).one();
        assertThat(recarregado.getSituacao()).isEqualTo(SituacaoLote.IMPORTADO);
        assertThat(recarregado.getMensagem())
                .contains("3 criados, 0 alterados, 0 iguais")
                .contains("em aberto 1050.00 (2 em aberto)");
    }

    @Test
    void test_reimportacaoTrocaOsMovimentosSoDoQueMudou() {
        movimentos.clear();
        ImpLote primeiro = criarLote(
                titulo(100, 10, "1000.00", "0.00"),
                titulo(101, 11, "400.00", "0.00"));
        mov(100, 1, "2025-03-03", "1000.00", "0", 0);
        mov(101, 1, "2025-03-03", "400.00", "0", 0);
        gravarMovimentos(primeiro);
        assertThat(service.importar(primeiro.getId()).getErros()).isEmpty();
        List<ItemPagar> antes = itens(titulos().get(11));

        movimentos.clear();
        ImpLote segundo = criarLote(
                titulo(100, 10, "1000.00", "0.00"),
                titulo(101, 11, "400.00", "400.00"));
        mov(100, 1, "2025-03-03", "1000.00", "0", 0);
        mov(101, 1, "2025-03-03", "400.00", "0", 0);
        mov(101, 2, "2025-04-01", "400.00", "0", 1);
        gravarMovimentos(segundo);

        ImportacaoPlanoContasService.Relatorio relatorio = service.importar(segundo.getId());

        assertThat(relatorio.getErros()).isEmpty();
        assertThat(relatorio.getImportadas()).isEqualTo(1);
        assertThat(relatorio.getAvisos()).anyMatch(a -> a.equals("Título 11: movimentos substituídos (1 → 2)."));
        assertThat(itens(titulos().get(11))).extracting(ItemPagar::getItem).containsExactly(1, 2);
        // os itens antigos foram apagados (soft delete), não regravados
        assertThat(dataManager.load(ItemPagar.class).id(antes.get(0).getId()).optional()).isEmpty();
        assertThat(dataManager.load(ImpLote.class).id(segundo.getId()).one().getMensagem())
                .contains("0 criados, 1 alterados, 1 iguais");
    }

    @Test
    void test_tituloCriadoNoAxctg3ImpedeAImportacao() {
        // título digitado no axctg3 com o mesmo número: o listener cria a emissão pela empresa da sessão
        ((User) currentAuthentication.getUser()).setCodEmpresa(COD_EMPRESA);
        TituloPagar manual = dataManager.create(TituloPagar.class);
        manual.setNumero(10);
        manual.setCodEmpresa(COD_EMPRESA);
        manual.setDataEmissao(LocalDate.of(ANO, 3, 3));
        manual.setDataVencimento(LocalDate.of(ANO, 3, 10));
        manual.setParceiro(dataManager.load(Parceiro.class)
                .query("select e from Parceiro e where e.codEmpresa = :cod").parameter("cod", COD_EMPRESA).one());
        manual.setValor(new BigDecimal("10.00"));
        dataManager.save(manual);

        movimentos.clear();
        ImpLote lote = criarLote(titulo(100, 10, "1000.00", "0.00"));
        mov(100, 1, "2025-03-03", "1000.00", "0", 0);
        mov(100, 2, "2025-03-05", "1.00", "0", 5);
        gravarMovimentos(lote);

        assertThat(service.importar(lote.getId()).getErros())
                .anyMatch(e -> e.startsWith("Banco 5 não existe na empresa (1 movimentos)"));

        movimentos.clear();
        ImpLote outro = criarLote(titulo(100, 10, "1000.00", "0.00"));
        mov(100, 1, "2025-03-03", "1000.00", "0", 0);
        gravarMovimentos(outro);

        assertThat(service.importar(outro.getId()).getErros())
                .containsExactly("Título 10 já existe no axctg3 e não veio da importação do legado.");
        assertThat(titulos().get(10).getValor()).isEqualByComparingTo("10.00");
    }

    private void banco(int codigo, String nome) {
        Banco b = dataManager.create(Banco.class);
        b.setCodigo(codigo);
        b.setCodEmpresa(COD_EMPRESA);
        b.setNome(nome);
        dataManager.save(b);
    }

    private void historico(int codigo, String nome, boolean emissao, boolean baixa, boolean desconto) {
        HistoricoFinanceiro h = dataManager.create(HistoricoFinanceiro.class);
        h.setCodigo(codigo);
        h.setCodEmpresa(COD_EMPRESA);
        h.setNome(nome);
        h.setEmissao(emissao);
        h.setBaixa(baixa);
        h.setDesconto(desconto);
        dataManager.save(h);
    }

    private ImpTituloPagar titulo(int registro, int numero, String valor, String pago) {
        ImpTituloPagar t = dataManager.create(ImpTituloPagar.class);
        t.setRegistro(registro);
        t.setNumero(numero);
        t.setDocumento("NF " + numero);
        t.setParceiro(40);
        t.setDataEmissao(LocalDate.of(ANO, 3, 3));
        t.setDataVencimento(LocalDate.of(ANO, 3, 10));
        t.setValor(new BigDecimal(valor));
        t.setPago(new BigDecimal(pago));
        return t;
    }

    /** Linha em branco do legado: sem histórico e sem valor. */
    private void movVazio(int pai, String data) {
        ImpItemPagar m = dataManager.create(ImpItemPagar.class);
        m.setRegistro(1000 + movimentos.size());
        m.setPai(pai);
        m.setData(LocalDate.parse(data));
        m.setBanco(0);
        movimentos.add(m);
    }

    private void mov(int pai, int historico, String data, String valor, String desconto, int banco) {
        ImpItemPagar m = dataManager.create(ImpItemPagar.class);
        m.setRegistro(1000 + movimentos.size());
        m.setPai(pai);
        m.setHistorico(historico);
        m.setData(LocalDate.parse(data));
        m.setValor(new BigDecimal(valor));
        m.setJuros(BigDecimal.ZERO);
        m.setDesconto(new BigDecimal(desconto));
        m.setBanco(banco);
        movimentos.add(m);
    }

    private ImpLote criarLote(ImpTituloPagar... titulos) {
        ImpLote lote = dataManager.create(ImpLote.class);
        lote.setTipo(TipoLote.TITULOS_PAGAR);
        lote.setSituacao(SituacaoLote.PRONTO);
        lote.setCodEmpresa(COD_EMPRESA);
        lote.setCodEmpresaLegado(1);
        lote.setAno(ANO);
        lote = dataManager.save(lote);
        SaveContext ctx = new SaveContext();
        for (ImpTituloPagar t : titulos) {
            t.setLote(lote);
            ctx.saving(t);
        }
        dataManager.save(ctx);
        return lote;
    }

    private void gravarMovimentos(ImpLote lote) {
        SaveContext ctx = new SaveContext();
        movimentos.forEach(m -> {
            m.setLote(lote);
            ctx.saving(m);
        });
        dataManager.save(ctx);
    }

    private Map<Integer, TituloPagar> titulos() {
        return dataManager.load(TituloPagar.class)
                .query("select e from TituloPagar e where e.codEmpresa = :cod")
                .parameter("cod", COD_EMPRESA)
                .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE)
                        .add("parceiro", FetchPlan.BASE))
                .list()
                .stream()
                .collect(Collectors.toMap(TituloPagar::getNumero, t -> t));
    }

    private List<ItemPagar> itens(TituloPagar titulo) {
        return dataManager.load(ItemPagar.class)
                .query("select e from ItemPagar e where e.tituloPagar = :t order by e.item")
                .parameter("t", titulo)
                .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE)
                        .add("historicoFinanceiro", FetchPlan.BASE)
                        .add("banco", FetchPlan.BASE))
                .list();
    }

    private void limpar() {
        apagar(carregar(ItemPagar.class, "select e from ItemPagar e where e.tituloPagar.codEmpresa = :cod"));
        apagar(carregar(TituloPagar.class, "select e from TituloPagar e where e.codEmpresa = :cod"));
        apagar(carregar(ImpItemPagar.class, "select e from ImpItemPagar e where e.lote.codEmpresa = :cod"));
        apagar(carregar(ImpTituloPagar.class, "select e from ImpTituloPagar e where e.lote.codEmpresa = :cod"));
        apagar(carregar(ImpLote.class, "select e from ImpLote e where e.codEmpresa = :cod"));
        apagar(carregar(HistoricoFinanceiro.class, "select e from HistoricoFinanceiro e where e.codEmpresa = :cod"));
        apagar(carregar(Banco.class, "select e from Banco e where e.codEmpresa = :cod"));
        apagar(carregar(Parceiro.class, "select e from Parceiro e where e.codEmpresa = :cod"));
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
