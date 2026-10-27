package br.com.axialsoftware.axctg3.importacao;

import br.com.axialsoftware.axctg3.entity.User;
import br.com.axialsoftware.axctg3.entity.cadastros.Empresa;
import br.com.axialsoftware.axctg3.entity.cadastros.Mensagem;
import br.com.axialsoftware.axctg3.entity.cadastros.Parceiro;
import br.com.axialsoftware.axctg3.entity.enums.SituacaoLote;
import br.com.axialsoftware.axctg3.entity.enums.TipoLote;
import br.com.axialsoftware.axctg3.entity.fiscal.ItemNotaSaida;
import br.com.axialsoftware.axctg3.entity.fiscal.ItemPedidoVenda;
import br.com.axialsoftware.axctg3.entity.fiscal.NaturezaOperacao;
import br.com.axialsoftware.axctg3.entity.fiscal.NotaSaida;
import br.com.axialsoftware.axctg3.entity.fiscal.PedidoVenda;
import br.com.axialsoftware.axctg3.entity.fiscal.Produto;
import br.com.axialsoftware.axctg3.entity.importacao.ImpItemNotaSaida;
import br.com.axialsoftware.axctg3.entity.importacao.ImpItemPedidoVenda;
import br.com.axialsoftware.axctg3.entity.importacao.ImpLote;
import br.com.axialsoftware.axctg3.entity.importacao.ImpNotaSaida;
import br.com.axialsoftware.axctg3.entity.importacao.ImpPedidoVenda;
import br.com.axialsoftware.axctg3.entity.tabelas.ClassTrib;
import br.com.axialsoftware.axctg3.service.importacao.ImportacaoNotasSaidaService;
import br.com.axialsoftware.axctg3.service.importacao.ImportacaoPedidosVendaService;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Importação de notas de saída e pedidos de venda do legado: na empresa do lote, sem passar
 * pelos listeners (número, CST e ICMS como no legado), reimportação trocando os itens e proteção
 * do que foi criado no axctg3.
 */
@SpringBootTest
@ExtendWith(AuthenticatedAsAdmin.class)
@ActiveProfiles("test")
class ImportacaoNotasPedidosTest {

    private static final int COD_EMPRESA = 9331;

    @Autowired
    DataManager dataManager;
    @Autowired
    CurrentAuthentication currentAuthentication;
    @Autowired
    ImportacaoNotasSaidaService notasService;
    @Autowired
    ImportacaoPedidosVendaService pedidosService;

    @BeforeEach
    void setUp() {
        limpar();
        Empresa empresa = dataManager.create(Empresa.class);
        empresa.setCodigo(COD_EMPRESA);
        empresa.setNome("Teste da importação de notas e pedidos");
        empresa.setApelido("Imp notas");
        empresa.setEspecieNfe("NFe");
        dataManager.save(empresa);
        Parceiro parceiro = dataManager.create(Parceiro.class);
        parceiro.setCodigo(221L);
        parceiro.setCodEmpresa(COD_EMPRESA);
        parceiro.setNome("Cliente");
        parceiro.setApelido("Cliente");
        parceiro.setCnpj("12345678000190");
        dataManager.save(parceiro);
        NaturezaOperacao natureza = dataManager.create(NaturezaOperacao.class);
        natureza.setCodigo(3);
        natureza.setCodEmpresa(COD_EMPRESA);
        natureza.setNome("Industrialização");
        natureza.setCfop(5124);
        dataManager.save(natureza);
        Produto produto = dataManager.create(Produto.class);
        produto.setCodigo(10);
        produto.setCodEmpresa(COD_EMPRESA);
        produto.setDescricao("CHAPA A/C");
        produto.setApelido("CHAPA");
        produto.setClassTrib(dataManager.load(ClassTrib.class).query("select e from ClassTrib e where e.codigo = 1").one());
        dataManager.save(produto);
        Mensagem mensagem = dataManager.create(Mensagem.class);
        mensagem.setCodigo(6);
        mensagem.setCodEmpresa(COD_EMPRESA);
        mensagem.setTexto("ICMS suspenso - Decreto 45.490/2000");
        dataManager.save(mensagem);
        // a sessão aponta para outra empresa: a importação não pode usá-la
        User admin = (User) currentAuthentication.getUser();
        admin.setCodEmpresa(COD_EMPRESA + 1);
    }

    @AfterEach
    void tearDown() {
        limpar();
    }

    @Test
    void test_notasComoNoLegadoEReimportacaoTrocaOsItens() {
        ImpLote lote = loteNotas(
                nota(1, 12543, "111", "S", "700.00", "0.00", item(1, 1, "200.00", "5124"), item(2, 1, "500.00", "5902")),
                nota(2, 12544, "NF", "N", "350.00", "12.50", item(3, 1, "300.00", "5124")));

        ImportacaoPlanoContasService.Relatorio r = notasService.importar(lote.getId());

        assertThat(r.getErros()).isEmpty();
        assertThat(r.getImportadas()).isEqualTo(2);
        assertThat(r.getAvisos()).contains("1 nota(s) com espécie \"111\" no legado: gravadas como \"NFe\".");
        assertThat(r.getDivergencias()).contains("Nota 12544: itens somam 300.00 e o valor das mercadorias é 350.00.");
        NotaSaida cancelada = nota(12543);
        assertThat(cancelada.getEspecie()).isEqualTo("NFe");
        assertThat(cancelada.getCancelada()).isTrue();
        assertThat(cancelada.getCodEmpresa()).isEqualTo(COD_EMPRESA);
        assertThat(cancelada.getMensagem()).isNotNull();
        assertThat(itens(cancelada)).extracting(ItemNotaSaida::getCfop).containsExactly(5124, 5902);
        assertThat(itens(cancelada)).extracting(ItemNotaSaida::getCst).containsOnlyNulls();   // listener não rodou
        NotaSaida outra = nota(12544);
        assertThat(outra.getEspecie()).isEqualTo("NF");
        assertThat(outra.getValorSt()).isEqualByComparingTo("17.50");   // ST + GNRE
        assertThat(outra.getModFrete()).isEqualTo(br.com.axialsoftware.axctg3.entity.enums.ModFrete.CONTRATACAO_DESTINATARIO);

        // reimportar: a 12544 ganha um item; a 12543 não muda
        ImpLote denovo = loteNotas(
                nota(1, 12543, "111", "S", "700.00", "0.00", item(1, 1, "200.00", "5124"), item(2, 1, "500.00", "5902")),
                nota(2, 12544, "NF", "N", "350.00", "12.50", item(3, 1, "300.00", "5124"), item(4, 1, "50.00", "5124")));
        ImportacaoPlanoContasService.Relatorio r2 = notasService.importar(denovo.getId());
        assertThat(r2.getErros()).isEmpty();
        assertThat(r2.getImportadas()).isEqualTo(1);
        assertThat(r2.getAvisos()).anyMatch(a -> a.equals("Nota 12544: itens substituídos (1 → 2)."));
        assertThat(itens(nota(12544))).hasSize(2);
    }

    @Test
    void test_notaCriadaNoAxctg3ComOMesmoNumeroImpede() {
        NotaSaida doAxctg3 = dataManager.create(NotaSaida.class);
        doAxctg3.setNumero(12600);
        doAxctg3.setCodEmpresa(COD_EMPRESA);
        doAxctg3.setEspecie("NFe");
        doAxctg3.setSerie("1");
        doAxctg3.setDataEmissao(LocalDate.of(2026, 7, 1));
        doAxctg3.setDataSaida(LocalDate.of(2026, 7, 1));
        doAxctg3.setParceiro(dataManager.load(Parceiro.class)
                .query("select e from Parceiro e where e.codEmpresa = :cod").parameter("cod", COD_EMPRESA).one());
        doAxctg3.setNatureza(dataManager.load(NaturezaOperacao.class)
                .query("select e from NaturezaOperacao e where e.codEmpresa = :cod").parameter("cod", COD_EMPRESA).one());
        dataManager.save(doAxctg3);

        ImpLote lote = loteNotas(nota(1, 12600, "NFe", "N", "200.00", "0.00", item(1, 1, "200.00", "5124")));

        assertThat(notasService.importar(lote.getId()).getErros())
                .contains("Nota 12600 já existe no axctg3 e não veio da importação do legado.");
    }

    @Test
    void test_pedidosCanceladoFicaDeForaEFaturadoEAvisado() {
        ImpLote lote = lote(TipoLote.PEDIDOS_VENDA);
        SaveContext ctx = new SaveContext();
        ctx.saving(pedido(lote, 1, 156519, "N", "N"), itemPedido(lote, 1, 1, "62945.00"));
        ctx.saving(pedido(lote, 2, 156520, "S", "N"), itemPedido(lote, 2, 1, "10.00"));
        ctx.saving(pedido(lote, 3, 156521, "N", "S"), itemPedido(lote, 3, 3, "50.00"));
        ImpPedidoVenda semNatureza = pedido(lote, 4, 24, "N", "N");   // natureza 0 no legado
        semNatureza.setNatureza(0);
        ctx.saving(semNatureza);
        dataManager.save(ctx);

        ImportacaoPlanoContasService.Relatorio r = pedidosService.importar(lote.getId());

        assertThat(r.getErros()).isEmpty();
        assertThat(r.getImportadas()).isEqualTo(2);
        assertThat(r.getAvisos())
                .contains("1 pedido(s) cancelado(s) no legado não entram: 156520.")
                .contains("1 pedido(s) sem natureza ou sem parceiro no legado (incompletos) não entram: 24.")
                .noneMatch(a -> a.startsWith("Mensagem que não existe"));
        List<PedidoVenda> pedidos = dataManager.load(PedidoVenda.class)
                .query("select e from PedidoVenda e where e.codEmpresa = :cod order by e.numero")
                .parameter("cod", COD_EMPRESA).list();
        assertThat(pedidos).extracting(PedidoVenda::getNumero).containsExactly(156519, 156521);
        PedidoVenda primeiro = dataManager.load(PedidoVenda.class).id(pedidos.get(0).getId())
                .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE).add("mensagem", FetchPlan.BASE)).one();
        assertThat(primeiro.getDataEntrada()).isEqualTo(LocalDate.of(2026, 6, 17));
        assertThat(primeiro.getFaturado()).isFalse();
        assertThat(pedidos.get(1).getFaturado()).isTrue();
        assertThat(primeiro.getMensagem().getCodigo()).isEqualTo(6);
        assertThat(primeiro.getComplementoMensagem()).isEqualTo("VENCIMENTO: 17/07/2026. DANFE.");
        assertThat(primeiro.getMensagemNota()).isEqualTo("PEDIDO DE COMPRA: 4996234584.");
        assertThat(primeiro.getDesconto()).isEqualByComparingTo("15.00");
        List<ItemPedidoVenda> itens = dataManager.load(ItemPedidoVenda.class)
                .query("select e from ItemPedidoVenda e where e.pedidoVenda = :p").parameter("p", primeiro).list();
        assertThat(itens).singleElement().satisfies(i -> {
            assertThat(i.getCfop()).isEqualTo(5124);
            assertThat(i.getValorUnitario()).isEqualByComparingTo("62945");
        });
        assertThat(dataManager.load(ImpLote.class).id(lote.getId()).one().getSituacao()).isEqualTo(SituacaoLote.IMPORTADO);
    }

    // ---- lote de notas ----

    private record LinhaItem(int registro, int quantidade, String unitario, String cfop) {
    }

    private record LinhaNota(int registro, int numero, String especie, String cancelada, String mercadorias,
                             String st, List<LinhaItem> itens) {
    }

    private LinhaItem item(int registro, int quantidade, String unitario, String cfop) {
        return new LinhaItem(registro, quantidade, unitario, cfop);
    }

    private LinhaNota nota(int registro, int numero, String especie, String cancelada, String mercadorias, String st,
                           LinhaItem... itens) {
        return new LinhaNota(registro, numero, especie, cancelada, mercadorias, st, List.of(itens));
    }

    private ImpLote loteNotas(LinhaNota... notas) {
        ImpLote lote = lote(TipoLote.NOTAS_SAIDA);
        SaveContext ctx = new SaveContext();
        for (LinhaNota n : notas) {
            ImpNotaSaida i = dataManager.create(ImpNotaSaida.class);
            i.setLote(lote);
            i.setRegistro(n.registro());
            i.setNumero(n.numero());
            i.setEspecie(n.especie());
            i.setSerie("1");
            i.setDataEmissao(LocalDate.of(2026, 7, 22));
            i.setParceiro(221);
            i.setNatureza(3);
            i.setTipoFrete("2");
            i.setValor(new BigDecimal(n.mercadorias()));
            i.setValorMercadoria(new BigDecimal(n.mercadorias()));
            i.setValorSubstituicao(new BigDecimal(n.st()));
            i.setValorGnre(new BigDecimal("5.00"));
            i.setCancelada(n.cancelada());
            i.setMensagem(6);
            ctx.saving(i);
            for (LinhaItem li : n.itens()) {
                ImpItemNotaSaida it = dataManager.create(ImpItemNotaSaida.class);
                it.setLote(lote);
                it.setRegistro(li.registro());
                it.setPai(n.registro());
                it.setProduto(10);
                it.setQuantidade(new BigDecimal(li.quantidade()));
                it.setValorUnitario(new BigDecimal(li.unitario()));
                it.setCfop(Integer.valueOf(li.cfop()));
                ctx.saving(it);
            }
        }
        dataManager.save(ctx);
        return lote;
    }

    private ImpPedidoVenda pedido(ImpLote lote, int registro, int numero, String cancelado, String faturado) {
        ImpPedidoVenda p = dataManager.create(ImpPedidoVenda.class);
        p.setLote(lote);
        p.setRegistro(registro);
        p.setNumero(String.format("%06d", numero));   // texto com zeros, como no legado
        p.setData(LocalDate.of(2026, 6, 17));
        p.setParceiro(221);
        p.setNatureza(3);
        p.setDesconto1(new BigDecimal("10.00"));
        p.setDesconto2(new BigDecimal("5.00"));
        p.setCancelado(cancelado);
        p.setFaturaTotal(faturado);
        p.setMensagem(6);
        p.setObservacao("VENCIMENTO: 17/07/2026. DANFE.");
        p.setObs1("PEDIDO DE COMPRA: 4996234584.");
        return p;
    }

    private ImpItemPedidoVenda itemPedido(ImpLote lote, int pai, int quantidade, String unitario) {
        ImpItemPedidoVenda i = dataManager.create(ImpItemPedidoVenda.class);
        i.setLote(lote);
        i.setRegistro(pai * 10);
        i.setPai(pai);
        i.setProduto(10);
        i.setQuantidade(new BigDecimal(quantidade));
        i.setValorUnitario(new BigDecimal(unitario));
        i.setCfo(5124);
        return i;
    }

    private ImpLote lote(TipoLote tipo) {
        ImpLote lote = dataManager.create(ImpLote.class);
        lote.setTipo(tipo);
        lote.setSituacao(SituacaoLote.PRONTO);
        lote.setCodEmpresa(COD_EMPRESA);
        lote.setCodEmpresaLegado(1);
        lote.setAno(2026);
        return dataManager.save(lote);
    }

    private NotaSaida nota(int numero) {
        return dataManager.load(NotaSaida.class)
                .query("select e from NotaSaida e where e.codEmpresa = :cod and e.numero = :numero")
                .parameter("cod", COD_EMPRESA)
                .parameter("numero", numero)
                .one();
    }

    private List<ItemNotaSaida> itens(NotaSaida nota) {
        return dataManager.load(ItemNotaSaida.class)
                .query("select e from ItemNotaSaida e where e.notaSaida = :nota order by e.item")
                .parameter("nota", nota)
                .fetchPlan(FetchPlan.BASE)
                .list();
    }

    private void limpar() {
        apagar(carregar(ItemNotaSaida.class, "select e from ItemNotaSaida e where e.notaSaida.codEmpresa = :cod"));
        apagar(carregar(NotaSaida.class, "select e from NotaSaida e where e.codEmpresa = :cod"));
        apagar(carregar(ItemPedidoVenda.class, "select e from ItemPedidoVenda e where e.pedidoVenda.codEmpresa = :cod"));
        apagar(carregar(PedidoVenda.class, "select e from PedidoVenda e where e.codEmpresa = :cod"));
        for (Class<?> imp : List.of(ImpItemNotaSaida.class, ImpNotaSaida.class, ImpItemPedidoVenda.class,
                ImpPedidoVenda.class)) {
            apagar(carregar(imp, "select e from " + imp.getSimpleName() + " e where e.lote.codEmpresa = :cod"));
        }
        apagar(carregar(ImpLote.class, "select e from ImpLote e where e.codEmpresa = :cod"));
        apagar(carregar(Produto.class, "select e from Produto e where e.codEmpresa = :cod"));
        apagar(carregar(Mensagem.class, "select e from Mensagem e where e.codEmpresa = :cod"));
        apagar(carregar(NaturezaOperacao.class, "select e from NaturezaOperacao e where e.codEmpresa = :cod"));
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
