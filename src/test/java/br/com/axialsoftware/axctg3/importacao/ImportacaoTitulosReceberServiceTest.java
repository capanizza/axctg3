package br.com.axialsoftware.axctg3.importacao;

import br.com.axialsoftware.axctg3.entity.User;
import br.com.axialsoftware.axctg3.entity.cadastros.Empresa;
import br.com.axialsoftware.axctg3.entity.cadastros.Parceiro;
import br.com.axialsoftware.axctg3.entity.enums.SituacaoLote;
import br.com.axialsoftware.axctg3.entity.enums.TipoLote;
import br.com.axialsoftware.axctg3.entity.financeiro.Banco;
import br.com.axialsoftware.axctg3.entity.financeiro.HistoricoFinanceiro;
import br.com.axialsoftware.axctg3.entity.financeiro.ItemReceber;
import br.com.axialsoftware.axctg3.entity.financeiro.TituloReceber;
import br.com.axialsoftware.axctg3.entity.importacao.ImpItemReceber;
import br.com.axialsoftware.axctg3.entity.importacao.ImpLote;
import br.com.axialsoftware.axctg3.entity.importacao.ImpTituloReceber;
import br.com.axialsoftware.axctg3.service.importacao.ImportacaoPlanoContasService;
import br.com.axialsoftware.axctg3.service.importacao.ImportacaoTitulosReceberService;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Importação de um lote de títulos a receber do legado, com emissão e baixas: na empresa do lote,
 * tudo contabilizado, emissão como item 1, conferência das baixas contra o RECEBIDO do legado, e
 * reimportação trocando os movimentos — sem nunca apagar o que foi feito no próprio axctg3.
 */
@SpringBootTest
@ExtendWith(AuthenticatedAsAdmin.class)
@ActiveProfiles("test")
class ImportacaoTitulosReceberServiceTest {

    private static final int COD_EMPRESA = 9323;
    private static final int ANO = 2025;

    @Autowired
    DataManager dataManager;
    @Autowired
    ImportacaoTitulosReceberService service;
    @Autowired
    CurrentAuthentication currentAuthentication;

    private final List<ImpItemReceber> movimentos = new ArrayList<>();

    @BeforeEach
    void setUp() {
        limpar();
        Empresa empresa = dataManager.create(Empresa.class);
        empresa.setCodigo(COD_EMPRESA);
        empresa.setNome("Teste da importação de títulos a receber");
        empresa.setApelido("Imp receber");
        dataManager.save(empresa);
        Parceiro parceiro = dataManager.create(Parceiro.class);
        parceiro.setCodigo(40L);
        parceiro.setCodEmpresa(COD_EMPRESA);
        parceiro.setNome("CLIENTE DE TESTE");
        parceiro.setApelido("CLIENTE");
        parceiro.setCnpj("11222333000181");
        dataManager.save(parceiro);
        banco(1, "SICREDI");
        banco(22, "PERDA RECEBIMENTOS");
        historico(1, "EMISSAO", true, false, false);
        historico(2, "BAIXA", false, true, false);
        historico(5, "BAIXA COM DESCONTO", false, true, true);
        // a sessão aponta para outra empresa: a importação não pode usá-la
        User admin = (User) currentAuthentication.getUser();
        admin.setCodEmpresa(COD_EMPRESA + 1);
    }

    @AfterEach
    void tearDown() {
        limpar();
    }

    @Test
    void test_criaComEmissaoEBaixasContabilizadas() {
        movimentos.clear();
        ImpLote lote = criarLote(
                titulo(100, "000317", 1, "1000.00", "0.00"),
                titulo(101, "000318", 1, "400.00", "400.00"),
                titulo(102, "000319", 1, "500.00", "500.00"));
        mov(100, 1, "2025-03-03", "1000.00", "0", 1);
        // baixas fora de ordem no legado: a importação numera pela data
        mov(101, 2, "2025-04-10", "300.00", "0", 1);
        mov(101, 1, "2025-03-03", "400.00", "0", 1);
        mov(101, 5, "2025-03-20", "100.00", "5.00", 22);
        mov(102, 1, "2025-03-03", "500.00", "0", 1);
        mov(102, 2, "2025-03-30", "450.00", "0", 1);
        gravarMovimentos(lote);

        ImportacaoPlanoContasService.Relatorio conferencia = service.conferir(lote.getId());
        assertThat(conferencia.getErros()).isEmpty();
        assertThat(titulos()).isEmpty(); // conferir não grava

        ImportacaoPlanoContasService.Relatorio relatorio = service.importar(lote.getId());

        assertThat(relatorio.getErros()).isEmpty();
        assertThat(relatorio.getImportadas()).isEqualTo(3);
        assertThat(relatorio.getDivergencias())
                .containsExactly("Título 000319: baixas somam 450.00 e o legado diz recebido 500.00.");
        assertThat(relatorio.getAvisos()).anyMatch(a -> a.startsWith("1 baixa(s) feitas no legado por banco diferente"));

        Map<String, TituloReceber> titulos = titulos();
        assertThat(titulos).containsOnlyKeys("000317", "000318", "000319");
        TituloReceber t = titulos.get("000318");
        assertThat(t.getCodEmpresa()).isEqualTo(COD_EMPRESA);
        assertThat(t.getCreatedBy()).isEqualTo(ImportacaoTitulosReceberService.IMPORTACAO);
        assertThat(t.getBanco().getCodigo()).isEqualTo(1);
        assertThat(t.getParceiro().getCodigo()).isEqualTo(40L);
        List<ItemReceber> itens = itens(t);
        assertThat(itens).extracting(i -> i.getItem() + " " + i.getHistoricoFinanceiro().getCodigo() + " " + i.getData())
                .containsExactly("1 1 2025-03-03", "2 5 2025-03-20", "3 2 2025-04-10");
        assertThat(itens).allMatch(ItemReceber::getContabilizado);
        assertThat(itens.get(1).getDesconto()).isEqualByComparingTo("5.00");
        assertThat(itens(titulos.get("000317"))).hasSize(1); // só a emissão: em aberto

        ImpLote recarregado = dataManager.load(ImpLote.class).id(lote.getId()).one();
        assertThat(recarregado.getSituacao()).isEqualTo(SituacaoLote.IMPORTADO);
        assertThat(recarregado.getMensagem())
                .contains("3 criados, 0 alterados, 0 iguais")
                .contains("em aberto 1050.00 (2 títulos)");
    }

    @Test
    void test_reimportacaoTrocaOsMovimentosSoDoQueMudou() {
        movimentos.clear();
        ImpLote primeiro = criarLote(
                titulo(100, "000317", 1, "1000.00", "0.00"),
                titulo(101, "000318", 1, "400.00", "0.00"));
        mov(100, 1, "2025-03-03", "1000.00", "0", 1);
        mov(101, 1, "2025-03-03", "400.00", "0", 1);
        gravarMovimentos(primeiro);
        assertThat(service.importar(primeiro.getId()).getErros()).isEmpty();
        List<ItemReceber> antes = itens(titulos().get("000318"));

        movimentos.clear();
        ImpLote segundo = criarLote(
                titulo(100, "000317", 1, "1000.00", "0.00"),
                titulo(101, "000318", 1, "400.00", "400.00"));
        mov(100, 1, "2025-03-03", "1000.00", "0", 1);
        mov(101, 1, "2025-03-03", "400.00", "0", 1);
        mov(101, 2, "2025-04-01", "400.00", "0", 1);
        gravarMovimentos(segundo);

        ImportacaoPlanoContasService.Relatorio relatorio = service.importar(segundo.getId());

        assertThat(relatorio.getErros()).isEmpty();
        assertThat(relatorio.getImportadas()).isEqualTo(1);
        assertThat(relatorio.getAvisos()).anyMatch(a -> a.equals("Título 000318: movimentos substituídos (1 → 2)."));
        assertThat(itens(titulos().get("000318"))).extracting(ItemReceber::getItem).containsExactly(1, 2);
        // os itens antigos foram apagados (soft delete), não regravados
        assertThat(dataManager.load(ItemReceber.class).id(antes.get(0).getId()).optional()).isEmpty();
        assertThat(dataManager.load(ImpLote.class).id(segundo.getId()).one().getMensagem())
                .contains("0 criados, 1 alterados, 1 iguais");
    }

    @Test
    void test_tituloCriadoNoAxctg3ImpedeAImportacao() {
        // título digitado no axctg3 com o mesmo número: o listener cria a emissão pela empresa da sessão
        ((User) currentAuthentication.getUser()).setCodEmpresa(COD_EMPRESA);
        TituloReceber manual = dataManager.create(TituloReceber.class);
        manual.setNumero("000317");
        manual.setCodEmpresa(COD_EMPRESA);
        manual.setDataEmissao(LocalDate.of(ANO, 3, 3));
        manual.setDataVencimento(LocalDate.of(ANO, 3, 10));
        manual.setParceiro(dataManager.load(Parceiro.class)
                .query("select e from Parceiro e where e.codEmpresa = :cod").parameter("cod", COD_EMPRESA).one());
        manual.setBanco(dataManager.load(Banco.class)
                .query("select e from Banco e where e.codEmpresa = :cod and e.codigo = 1").parameter("cod", COD_EMPRESA).one());
        manual.setValor(new BigDecimal("10.00"));
        dataManager.save(manual);

        movimentos.clear();
        ImpLote lote = criarLote(titulo(100, "000317", 1, "1000.00", "0.00"));
        mov(100, 1, "2025-03-03", "1000.00", "0", 1);
        mov(100, 7, "2025-03-05", "1.00", "0", 1);
        gravarMovimentos(lote);

        ImportacaoPlanoContasService.Relatorio relatorio = service.importar(lote.getId());

        assertThat(relatorio.getErros())
                .anyMatch(e -> e.startsWith("Histórico financeiro 7 não existe na empresa (1 movimentos)"));

        movimentos.clear();
        ImpLote outro = criarLote(titulo(100, "000317", 1, "1000.00", "0.00"));
        mov(100, 1, "2025-03-03", "1000.00", "0", 1);
        gravarMovimentos(outro);

        assertThat(service.importar(outro.getId()).getErros())
                .containsExactly("Título 000317 já existe no axctg3 e não veio da importação do legado.");
        assertThat(titulos().get("000317").getValor()).isEqualByComparingTo("10.00");
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

    private ImpTituloReceber titulo(int registro, String numero, int banco, String valor, String recebido) {
        ImpTituloReceber t = dataManager.create(ImpTituloReceber.class);
        t.setRegistro(registro);
        t.setNumero(numero);
        t.setParceiro(40);
        t.setDataEmissao(LocalDate.of(ANO, 3, 3));
        t.setDataVencimento(LocalDate.of(ANO, 3, 10));
        t.setBanco(banco);
        t.setValor(new BigDecimal(valor));
        t.setRecebido(new BigDecimal(recebido));
        return t;
    }

    private void mov(int pai, int historico, String data, String valor, String desconto, int banco) {
        ImpItemReceber m = dataManager.create(ImpItemReceber.class);
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

    private ImpLote criarLote(ImpTituloReceber... titulos) {
        ImpLote lote = dataManager.create(ImpLote.class);
        lote.setTipo(TipoLote.TITULOS_RECEBER);
        lote.setSituacao(SituacaoLote.PRONTO);
        lote.setCodEmpresa(COD_EMPRESA);
        lote.setCodEmpresaLegado(1);
        lote.setAno(ANO);
        lote = dataManager.save(lote);
        SaveContext ctx = new SaveContext();
        for (ImpTituloReceber t : titulos) {
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

    private Map<String, TituloReceber> titulos() {
        return dataManager.load(TituloReceber.class)
                .query("select e from TituloReceber e where e.codEmpresa = :cod")
                .parameter("cod", COD_EMPRESA)
                .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE)
                        .add("banco", FetchPlan.BASE)
                        .add("parceiro", FetchPlan.BASE))
                .list()
                .stream()
                .collect(Collectors.toMap(TituloReceber::getNumero, t -> t));
    }

    private List<ItemReceber> itens(TituloReceber titulo) {
        return dataManager.load(ItemReceber.class)
                .query("select e from ItemReceber e where e.tituloReceber = :t order by e.item")
                .parameter("t", titulo)
                .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE).add("historicoFinanceiro", FetchPlan.BASE))
                .list();
    }

    private void limpar() {
        apagar(carregar(ItemReceber.class, "select e from ItemReceber e where e.tituloReceber.codEmpresa = :cod"));
        apagar(carregar(TituloReceber.class, "select e from TituloReceber e where e.codEmpresa = :cod"));
        apagar(carregar(ImpItemReceber.class, "select e from ImpItemReceber e where e.lote.codEmpresa = :cod"));
        apagar(carregar(ImpTituloReceber.class, "select e from ImpTituloReceber e where e.lote.codEmpresa = :cod"));
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
