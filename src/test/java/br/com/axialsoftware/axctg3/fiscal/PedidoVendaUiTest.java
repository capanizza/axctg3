package br.com.axialsoftware.axctg3.fiscal;

import br.com.axialsoftware.axctg3.Axctg3Application;
import br.com.axialsoftware.axctg3.entity.User;
import br.com.axialsoftware.axctg3.entity.cadastros.Empresa;
import br.com.axialsoftware.axctg3.entity.cadastros.Parceiro;
import br.com.axialsoftware.axctg3.entity.fiscal.ItemNotaSaida;
import br.com.axialsoftware.axctg3.entity.fiscal.ItemPedidoVenda;
import br.com.axialsoftware.axctg3.entity.fiscal.NaturezaOperacao;
import br.com.axialsoftware.axctg3.entity.fiscal.NotaSaida;
import br.com.axialsoftware.axctg3.entity.fiscal.PedidoVenda;
import br.com.axialsoftware.axctg3.entity.fiscal.Produto;
import br.com.axialsoftware.axctg3.entity.tabelas.ClassTrib;
import br.com.axialsoftware.axctg3.entity.tabelas.Cst;
import br.com.axialsoftware.axctg3.service.fiscal.PedidoVendaService;
import br.com.axialsoftware.axctg3.view.fiscal.pedidovenda.PedidoVendaDetailView;
import br.com.axialsoftware.axctg3.view.fiscal.pedidovenda.PedidoVendaListView;
import io.jmix.core.DataManager;
import io.jmix.core.SaveContext;
import io.jmix.core.security.CurrentAuthentication;
import io.jmix.data.PersistenceHints;
import io.jmix.flowui.ViewNavigators;
import io.jmix.flowui.component.combobox.EntityComboBox;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.component.textfield.JmixIntegerField;
import io.jmix.flowui.data.grid.DataGridItems;
import io.jmix.flowui.kit.component.button.JmixButton;
import io.jmix.flowui.testassist.FlowuiTestAssistConfiguration;
import io.jmix.flowui.testassist.UiTest;
import io.jmix.flowui.testassist.UiTestUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Cobre a criação de PedidoVenda/ItemPedidoVenda: numeração via
 * PedidoVendaEventListener/ItemPedidoVendaEventListener (Sequence, mesmo padrão de
 * NotaSaida/ItemNotaSaida), o cálculo de subTotal (quantidade * valorUnitario), a
 * listagem e a abertura do detail view com os campos das abas "Informações gerais"
 * e "Complementos".
 */
@UiTest
@SpringBootTest(classes = {Axctg3Application.class, FlowuiTestAssistConfiguration.class})
@ActiveProfiles("test")
class PedidoVendaUiTest {

    private static final int COD_EMPRESA = 9420;
    private static final int CLASS_TRIB_DOACAO = 9990103;

    @Autowired
    private DataManager dataManager;
    @Autowired
    private ViewNavigators viewNavigators;
    @Autowired
    private CurrentAuthentication currentAuthentication;
    @Autowired
    private PedidoVendaService pedidoVendaService;

    private Parceiro parceiro;
    private NaturezaOperacao natureza;
    private ClassTrib classTrib;
    private Produto produto;

    @BeforeEach
    void setUp() {
        User admin = (User) currentAuthentication.getUser();
        admin.setCodEmpresa(COD_EMPRESA);

        limparDados();

        criarEmpresa();
        parceiro = criarParceiro();
        natureza = criarNatureza();
        classTrib = criarClassTrib();
        produto = criarProduto();
    }

    @Test
    void listenersNumeramPedidoEItemNaCriacao() {
        PedidoVenda pedido = criarPedido();
        ItemPedidoVenda item = criarItem(pedido, new BigDecimal("3"), new BigDecimal("10.50"));

        assertThat(pedido.getNumero()).isNotNull();
        assertThat(pedido.getCodEmpresa()).isEqualTo(COD_EMPRESA);
        assertThat(item.getItem()).isNotNull();
        assertThat(item.getSubTotal()).isEqualByComparingTo("31.50");
        assertThat(item.getDescricaoComplementar()).isBlank();
    }

    @Test
    void listaMostraOPedidoCriado() {
        PedidoVenda pedido = criarPedido();

        viewNavigators.view(UiTestUtils.getCurrentView(), PedidoVendaListView.class).navigate();
        PedidoVendaListView view = UiTestUtils.getCurrentView();

        DataGrid<PedidoVenda> grid = UiTestUtils.getComponent(view, "pedidoVendasDataGrid");
        DataGridItems<PedidoVenda> items = grid.getItems();
        assertThat(items.getItems()).extracting(PedidoVenda::getNumero).contains(pedido.getNumero());

        JmixButton listagemButton = UiTestUtils.getComponent(view, "listagemButton");
        assertThat(listagemButton.isVisible()).isTrue();
    }

    @Test
    void listagemMontaDtosSemEstourarExcecao() {
        PedidoVenda pedido = criarPedido();
        criarItem(pedido, new BigDecimal("2"), new BigDecimal("15.00"));

        // sem PedidoVenda.jasper ainda (próxima etapa) — RelatorioService.emitirRelatorio
        // engole a exceção de template não encontrado, então isto só prova que a consulta/
        // fetch plan/resolução de instance name (PedidoVendaService.prepararPedidos) não
        // estoura em si.
        assertThatCode(() -> pedidoVendaService.listarPedidos()).doesNotThrowAnyException();
    }

    // Relato do usuário 2026-09-24: a listagem ignorava a seleção e saía sempre com
    // todos os pedidos (o 1 abrindo o PDF).
    @Test
    void listagemComPedidoSelecionadoTrazSoEle() {
        PedidoVenda pedido1 = criarPedido();
        criarItem(pedido1, new BigDecimal("1"), new BigDecimal("10"));
        PedidoVenda pedido2 = criarPedido();
        criarItem(pedido2, new BigDecimal("2"), new BigDecimal("10"));
        criarItem(pedido2, new BigDecimal("3"), new BigDecimal("10"));

        assertThat(pedidoVendaService.montarLinhasListagem(pedido2.getId()))
                .hasSize(2)
                .allSatisfy(linha -> assertThat(linha.getNumero()).isEqualTo(pedido2.getNumero()));
        assertThat(pedidoVendaService.montarLinhasListagem(null))
                .extracting(br.com.axialsoftware.axctg3.entity.fiscal.PedidoVendaDto::getNumero)
                .contains(pedido1.getNumero(), pedido2.getNumero());
        assertThatCode(() -> pedidoVendaService.listarPedidos(pedido2.getId())).doesNotThrowAnyException();
    }

    @Test
    void emitirNotaSaidaGeraNotaComItensAPartirDoPedido() {
        PedidoVenda pedido = criarPedido();
        criarItem(pedido, new BigDecimal("3"), new BigDecimal("10.50"));

        PedidoVendaService.ResultadoEmitirNotaSaida resultado = pedidoVendaService.emitirNotaSaida(pedido.getId());

        assertThat(resultado.sucesso()).isTrue();
        NotaSaida notaSaida = resultado.notaSaida();
        assertThat(notaSaida.getNumero()).isNotNull();
        assertThat(notaSaida.getEspecie()).isEqualTo("NF");
        assertThat(notaSaida.getSerie()).isEqualTo("1");
        assertThat(notaSaida.getParceiro()).isEqualTo(parceiro);
        assertThat(notaSaida.getNatureza()).isEqualTo(natureza);
        assertThat(notaSaida.getValorMercadoria()).isEqualByComparingTo("31.50");
        assertThat(notaSaida.getValor()).isEqualByComparingTo("31.50");
    }

    // Pedido do usuário 2026-09-24: na doação, CST 41 (natureza) e 410999 (cabeçalho)
    // já vêm no item, em vez do CST/cClassTrib de venda do produto.
    @Test
    void itemNovoPegaCstDaNaturezaEClassTribDoCabecalho() {
        ClassTrib doacao = criarClassTribDoacao();
        natureza.setCst(dataManager.load(Cst.class).query("select e from Cst e where e.codigo = '41'").one());
        natureza = dataManager.save(natureza);
        PedidoVenda pedido = dataManager.create(PedidoVenda.class);
        pedido.setDataEntrada(LocalDate.now());
        pedido.setParceiro(parceiro);
        pedido.setNatureza(natureza);
        pedido.setClassTrib(doacao);
        pedido = dataManager.save(pedido);

        ItemPedidoVenda item = criarItem(pedido, new BigDecimal("1"), new BigDecimal("10"));

        assertThat(item.getCst()).isEqualTo("41");
        assertThat(item.getCodClassTrib()).isEqualTo(CLASS_TRIB_DOACAO);
        assertThat(item.getCfop()).isEqualTo(5102); // CFOP da natureza
    }

    @Test
    void escolherNaturezaTrazOClassTribDelaProCabecalho() {
        ClassTrib doacao = criarClassTribDoacao();
        natureza.setClassTrib(doacao);
        natureza = dataManager.save(natureza);

        viewNavigators.detailView(UiTestUtils.getCurrentView(), PedidoVenda.class)
                .newEntity()
                .withViewClass(PedidoVendaDetailView.class)
                .navigate();
        PedidoVendaDetailView view = UiTestUtils.getCurrentView();

        EntityComboBox<NaturezaOperacao> naturezaField = UiTestUtils.getComponent(view, "naturezaField");
        naturezaField.setValue(natureza);

        EntityComboBox<ClassTrib> classTribField = UiTestUtils.getComponent(view, "classTribField");
        assertThat(classTribField.getValue()).isNotNull();
        assertThat(classTribField.getValue().getCodigo()).isEqualTo(CLASS_TRIB_DOACAO);
    }

    private ClassTrib criarClassTribDoacao() {
        ClassTrib c = dataManager.create(ClassTrib.class);
        c.setCodigo(CLASS_TRIB_DOACAO);
        c.setCst(410);
        c.setDescricao("Doação de teste");
        c.setTipoAliquota("Sem alíquota");
        c.setNomenclatura("Teste");
        c.setDescricaoTratamentoTributario("Teste");
        return dataManager.save(c);
    }

    @Test
    void emitirNotaSaidaFalhaSemItens() {
        PedidoVenda pedido = criarPedido();

        PedidoVendaService.ResultadoEmitirNotaSaida resultado = pedidoVendaService.emitirNotaSaida(pedido.getId());

        assertThat(resultado.sucesso()).isFalse();
        assertThat(resultado.motivo()).contains("sem itens");
        assertThat(resultado.notaSaida()).isNull();
    }

    @Test
    void detailAbreComOsCamposDasAbas() {
        PedidoVenda pedido = criarPedido();

        viewNavigators.detailView(UiTestUtils.getCurrentView(), PedidoVenda.class)
                .editEntity(pedido)
                .withViewClass(PedidoVendaDetailView.class)
                .navigate();
        PedidoVendaDetailView view = UiTestUtils.getCurrentView();

        JmixIntegerField numeroField = UiTestUtils.getComponent(view, "numeroField");
        assertThat(numeroField.getValue()).isEqualTo(pedido.getNumero());

        EntityComboBox<Parceiro> parceiroField = UiTestUtils.getComponent(view, "parceiroField");
        assertThat(parceiroField.getValue()).isEqualTo(parceiro);

        EntityComboBox<NaturezaOperacao> naturezaField = UiTestUtils.getComponent(view, "naturezaField");
        assertThat(naturezaField.getValue()).isEqualTo(natureza);
    }

    private PedidoVenda criarPedido() {
        PedidoVenda pedido = dataManager.create(PedidoVenda.class);
        pedido.setDataEntrada(LocalDate.now());
        pedido.setParceiro(parceiro);
        pedido.setNatureza(natureza);
        pedido.setClassTrib(classTrib);
        return dataManager.save(pedido);
    }

    private ItemPedidoVenda criarItem(PedidoVenda pedido, BigDecimal quantidade, BigDecimal valorUnitario) {
        ItemPedidoVenda item = dataManager.create(ItemPedidoVenda.class);
        item.setPedidoVenda(pedido);
        item.setProduto(produto);
        item.setQuantidade(quantidade);
        item.setValorUnitario(valorUnitario);
        return dataManager.save(item);
    }

    private void criarEmpresa() {
        Empresa empresa = dataManager.create(Empresa.class);
        empresa.setCodigo(COD_EMPRESA);
        empresa.setNome("Empresa de teste");
        empresa.setApelido("Teste");
        empresa.setEspecieNfe("NF");
        empresa.setSerieNfe("1");
        dataManager.save(empresa);
    }

    private Parceiro criarParceiro() {
        Parceiro p = dataManager.create(Parceiro.class);
        p.setCodigo(1L);
        p.setCodEmpresa(COD_EMPRESA);
        p.setNome("Cliente de Teste");
        p.setApelido("Cliente Teste");
        p.setCnpj("12345678000190");
        p.setCliente(true);
        return dataManager.save(p);
    }

    private NaturezaOperacao criarNatureza() {
        NaturezaOperacao n = dataManager.create(NaturezaOperacao.class);
        n.setCodigo(1);
        n.setCodEmpresa(COD_EMPRESA);
        n.setNome("Venda de teste");
        n.setCfop(5102);
        return dataManager.save(n);
    }

    private ClassTrib criarClassTrib() {
        ClassTrib c = dataManager.create(ClassTrib.class);
        c.setCodigo(9990102);
        c.setCst(1);
        c.setDescricao("ClassTrib de teste");
        c.setTipoAliquota("Padrão");
        c.setNomenclatura("Teste");
        c.setDescricaoTratamentoTributario("Teste");
        return dataManager.save(c);
    }

    private Produto criarProduto() {
        Produto p = dataManager.create(Produto.class);
        p.setCodigo(1);
        p.setCodEmpresa(COD_EMPRESA);
        p.setDescricao("Produto de teste");
        p.setApelido("Teste");
        p.setClassTrib(classTrib);
        return dataManager.save(p);
    }

    @AfterEach
    void tearDown() {
        limparDados();
    }

    // Hard delete: o índice único de PedidoVenda (NUMERO, COD_EMPRESA) existe também no
    // HSQLDB (changelog 17-101711/17-104248), então um soft delete não libera a chave
    // natural pra uma segunda rodada da suíte — mesmo padrão de BemUiTest.
    private void limparDados() {
        apagar(carregar(ItemNotaSaida.class,
                "select e from ItemNotaSaida e where e.notaSaida.codEmpresa = :codEmpresa"));
        apagar(carregar(NotaSaida.class, "select e from NotaSaida e where e.codEmpresa = :codEmpresa"));
        apagar(carregar(ItemPedidoVenda.class,
                "select e from ItemPedidoVenda e where e.pedidoVenda.codEmpresa = :codEmpresa"));
        apagar(carregar(PedidoVenda.class, "select e from PedidoVenda e where e.codEmpresa = :codEmpresa"));
        apagar(carregar(Produto.class, "select e from Produto e where e.codEmpresa = :codEmpresa"));
        apagar(carregar(NaturezaOperacao.class, "select e from NaturezaOperacao e where e.codEmpresa = :codEmpresa"));
        apagar(carregar(Parceiro.class, "select e from Parceiro e where e.codEmpresa = :codEmpresa"));
        apagar(carregar(Empresa.class, "select e from Empresa e where e.codigo = :codEmpresa"));
        if (classTrib != null) {
            apagar(carregar(ClassTrib.class, "select e from ClassTrib e where e.codigo = :codigo",
                    "codigo", classTrib.getCodigo()));
        }
        apagar(carregar(ClassTrib.class, "select e from ClassTrib e where e.codigo = :codigo",
                "codigo", CLASS_TRIB_DOACAO));
    }

    private <E> List<E> carregar(Class<E> entityClass, String query) {
        return dataManager.load(entityClass)
                .query(query)
                .parameter("codEmpresa", COD_EMPRESA)
                .hint(PersistenceHints.SOFT_DELETION, false)
                .list();
    }

    private <E> List<E> carregar(Class<E> entityClass, String query, String paramName, Object paramValue) {
        return dataManager.load(entityClass)
                .query(query)
                .parameter(paramName, paramValue)
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
