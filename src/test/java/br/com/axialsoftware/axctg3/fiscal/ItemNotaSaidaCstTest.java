package br.com.axialsoftware.axctg3.fiscal;

import br.com.axialsoftware.axctg3.entity.cadastros.Parceiro;
import br.com.axialsoftware.axctg3.entity.fiscal.ItemNotaSaida;
import br.com.axialsoftware.axctg3.entity.fiscal.NaturezaOperacao;
import br.com.axialsoftware.axctg3.entity.fiscal.NotaSaida;
import br.com.axialsoftware.axctg3.entity.fiscal.Produto;
import br.com.axialsoftware.axctg3.entity.tabelas.ClassTrib;
import br.com.axialsoftware.axctg3.entity.tabelas.Cst;
import br.com.axialsoftware.axctg3.test_support.AuthenticatedAsAdmin;
import io.jmix.core.DataManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cobre a precedência de CST de ICMS e o cálculo automático de ICMS (base/alíquota/
 * valor, no item e no total da nota) decididos em 2026-09-14 (ver Javadoc de {@link Cst},
 * {@code ItemNotaSaidaEventListener} e {@code NotaSaidaService}): substitui a árvore
 * procedural do legado por um catálogo, com a mesma precedência natureza/produto já
 * usada pro cClassTrib do IBS/CBS — e a alíquota usada no cálculo vem sempre do MESMO
 * lado que decidiu o CST. Item de teste criado direto via {@link DataManager}, sem
 * passar pela view — mesmo padrão de {@code NfeEmissaoServiceTest}.
 */
@SpringBootTest
@ExtendWith(AuthenticatedAsAdmin.class)
@ActiveProfiles("test")
class ItemNotaSaidaCstTest {

    private static final int COD_EMPRESA = 9111;
    private static final int CLASS_TRIB_CODIGO_TESTE = 9990002;

    @Autowired
    private DataManager dataManager;

    @AfterEach
    void tearDown() {
        dataManager.load(NotaSaida.class)
                .query("select e from NotaSaida e where e.codEmpresa = :codEmpresa")
                .parameter("codEmpresa", COD_EMPRESA)
                .list()
                .forEach(dataManager::remove);
        dataManager.load(NaturezaOperacao.class)
                .query("select e from NaturezaOperacao e where e.codEmpresa = :codEmpresa")
                .parameter("codEmpresa", COD_EMPRESA)
                .list()
                .forEach(dataManager::remove);
        dataManager.load(Parceiro.class)
                .query("select e from Parceiro e where e.codEmpresa = :codEmpresa")
                .parameter("codEmpresa", COD_EMPRESA)
                .list()
                .forEach(dataManager::remove);
        dataManager.load(Produto.class)
                .query("select e from Produto e where e.codEmpresa = :codEmpresa")
                .parameter("codEmpresa", COD_EMPRESA)
                .list()
                .forEach(dataManager::remove);
        // ClassTrib é tabela global (sem codEmpresa) — limpa só o código reservado de teste.
        dataManager.load(ClassTrib.class)
                .query("select e from ClassTrib e where e.codigo = :codigo")
                .parameter("codigo", CLASS_TRIB_CODIGO_TESTE)
                .list()
                .forEach(dataManager::remove);
    }

    private ClassTrib classTribCompartilhado;

    // Vários produtos de teste podem precisar de um ClassTrib (obrigatório em Produto);
    // CODIGO é único, então reaproveita a mesma linha em vez de criar de novo por produto.
    private ClassTrib criarClassTrib() {
        if (classTribCompartilhado != null) {
            return classTribCompartilhado;
        }
        ClassTrib classTrib = dataManager.create(ClassTrib.class);
        classTrib.setCodigo(CLASS_TRIB_CODIGO_TESTE);
        classTrib.setCst(1);
        classTrib.setDescricao("ClassTrib de teste");
        classTrib.setTipoAliquota("Padrão");
        classTrib.setNomenclatura("Teste");
        classTrib.setDescricaoTratamentoTributario("Teste");
        classTribCompartilhado = dataManager.save(classTrib);
        return classTribCompartilhado;
    }

    private Cst carregarCst(String codigo) {
        return dataManager.load(Cst.class)
                .query("select e from Cst e where e.codigo = :codigo")
                .parameter("codigo", codigo)
                .one();
    }

    private Parceiro criarParceiro() {
        Parceiro parceiro = dataManager.create(Parceiro.class);
        parceiro.setCodigo(1L);
        parceiro.setCodEmpresa(COD_EMPRESA);
        parceiro.setNome("Cliente de Teste");
        parceiro.setApelido("Cliente Teste");
        parceiro.setCnpj("12345678000190");
        return dataManager.save(parceiro);
    }

    private NaturezaOperacao criarNatureza(Cst cst) {
        return criarNatureza(cst, BigDecimal.ZERO);
    }

    private NaturezaOperacao criarNatureza(Cst cst, BigDecimal aliqIcms) {
        NaturezaOperacao natureza = dataManager.create(NaturezaOperacao.class);
        natureza.setCodigo(1);
        natureza.setCodEmpresa(COD_EMPRESA);
        natureza.setNome("Venda de teste");
        natureza.setCfop(5102);
        natureza.setCst(cst);
        natureza.setAliqIcms(aliqIcms);
        return dataManager.save(natureza);
    }

    private Produto criarProduto(Cst cst) {
        return criarProduto(cst, BigDecimal.ZERO);
    }

    private Produto criarProduto(Cst cst, BigDecimal aliqIcms) {
        return criarProduto(cst, aliqIcms, 1);
    }

    private Produto criarProduto(Cst cst, BigDecimal aliqIcms, int codigo) {
        Produto produto = dataManager.create(Produto.class);
        produto.setCodigo(codigo);
        produto.setCodEmpresa(COD_EMPRESA);
        produto.setDescricao("Produto de teste " + codigo);
        produto.setApelido("Teste" + codigo);
        produto.setClassTrib(criarClassTrib());
        produto.setCst(cst);
        produto.setAliqIcms(aliqIcms);
        return dataManager.save(produto);
    }

    private NotaSaida criarNotaSaida(NaturezaOperacao natureza) {
        NotaSaida notaSaida = dataManager.create(NotaSaida.class);
        notaSaida.setCodEmpresa(COD_EMPRESA);
        notaSaida.setDataEmissao(LocalDate.now());
        notaSaida.setDataSaida(LocalDate.now());
        notaSaida.setEspecie("NF");
        notaSaida.setSerie("1");
        notaSaida.setParceiro(criarParceiro());
        notaSaida.setNatureza(natureza);
        return dataManager.save(notaSaida);
    }

    private ItemNotaSaida criarItem(NotaSaida notaSaida, Produto produto) {
        return criarItem(notaSaida, produto, 1, new BigDecimal("10"), new BigDecimal("10"));
    }

    private ItemNotaSaida criarItem(NotaSaida notaSaida, Produto produto, int numero,
                                     BigDecimal quantidade, BigDecimal valorUnitario) {
        ItemNotaSaida item = dataManager.create(ItemNotaSaida.class);
        item.setNotaSaida(notaSaida);
        // fixo, pra não depender do codEmpresa da sessão pra numerar via Sequence
        item.setItem(numero);
        item.setProduto(produto);
        item.setQuantidade(quantidade);
        item.setValorUnitario(valorUnitario);
        return dataManager.save(item);
    }

    @Test
    void naturezaComCstProprioDecideIndependenteDoProduto() {
        NaturezaOperacao natureza = criarNatureza(carregarCst("40"));
        Produto produto = criarProduto(carregarCst("10"));
        NotaSaida notaSaida = criarNotaSaida(natureza);

        ItemNotaSaida item = criarItem(notaSaida, produto);

        assertThat(item.getCst()).isEqualTo("40");
    }

    @Test
    void naturezaRasaComCst00DelegaProDoProduto() {
        NaturezaOperacao natureza = criarNatureza(carregarCst("00"));
        Produto produto = criarProduto(carregarCst("10"));
        NotaSaida notaSaida = criarNotaSaida(natureza);

        ItemNotaSaida item = criarItem(notaSaida, produto);

        assertThat(item.getCst()).isEqualTo("10");
    }

    @Test
    void naturezaSemCstDelegaProDoProduto() {
        NaturezaOperacao natureza = criarNatureza(null);
        Produto produto = criarProduto(carregarCst("60"));
        NotaSaida notaSaida = criarNotaSaida(natureza);

        ItemNotaSaida item = criarItem(notaSaida, produto);

        assertThat(item.getCst()).isEqualTo("60");
    }

    @Test
    void semCstNaNaturezaNemNoProdutoDeixaItemEmBranco() {
        NaturezaOperacao natureza = criarNatureza(null);
        Produto produto = criarProduto(null);
        NotaSaida notaSaida = criarNotaSaida(natureza);

        ItemNotaSaida item = criarItem(notaSaida, produto);

        assertThat(item.getCst()).isNull();
    }

    /**
     * Bug real reproduzido ao vivo 2026-09-14: o produto que chega em
     * {@code ItemNotaSaidaEventListener} vem de um {@code entityPicker} de tela,
     * carregado só com {@code _instance_name} — sem {@code cst}/{@code classTrib}. Ler
     * esse ManyToOne LAZY não fetched dentro de {@code EntitySavingEvent} não faz
     * lazy-load; estoura {@code ValidationException.instantiatingValueholderWithNullSession}.
     * Recarrega aqui a mesma condição (produto "raso") pra provar que o listener se
     * recupera sozinho em vez de propagar a exceção.
     */
    @Test
    void produtoCarregadoSoComInstanceNameNaoEstouraAoResolverCst() {
        NaturezaOperacao natureza = criarNatureza(null);
        Produto produtoCompleto = criarProduto(carregarCst("60"));
        NotaSaida notaSaida = criarNotaSaida(natureza);

        Produto produtoRaso = dataManager.load(Produto.class)
                .id(produtoCompleto.getId())
                .fetchPlan("_instance_name")
                .one();

        ItemNotaSaida item = criarItem(notaSaida, produtoRaso);

        assertThat(item.getCst()).isEqualTo("60");
    }

    @Test
    void icmsDoItemUsaAliquotaDoMesmoLadoQueDecidiuOCst() {
        // natureza rasa (CST "00") delega CST *e* alíquota pro produto — aliqIcms da
        // natureza (99%, absurdo de propósito) não pode vazar pro cálculo.
        NaturezaOperacao natureza = criarNatureza(carregarCst("00"), new BigDecimal("99.00"));
        Produto produto = criarProduto(carregarCst("10"), new BigDecimal("18.00"));
        NotaSaida notaSaida = criarNotaSaida(natureza);

        ItemNotaSaida item = criarItem(notaSaida, produto, 1, new BigDecimal("10"), new BigDecimal("10"));

        assertThat(item.getAliqIcms()).isEqualByComparingTo("18.00");
        assertThat(item.getBaseIcms()).isEqualByComparingTo("100.00");
        assertThat(item.getValorIcms()).isEqualByComparingTo("18.00");
    }

    @Test
    void icmsRecalculadoAoEditarQuantidadeDoItem() {
        NaturezaOperacao natureza = criarNatureza(carregarCst("00"));
        Produto produto = criarProduto(carregarCst("00"), new BigDecimal("18.00"));
        NotaSaida notaSaida = criarNotaSaida(natureza);
        ItemNotaSaida item = criarItem(notaSaida, produto, 1, new BigDecimal("10"), new BigDecimal("10"));
        assertThat(item.getValorIcms()).isEqualByComparingTo("18.00");

        item.setQuantidade(new BigDecimal("20"));
        item = dataManager.save(item);

        assertThat(item.getBaseIcms()).isEqualByComparingTo("200.00");
        assertThat(item.getValorIcms()).isEqualByComparingTo("36.00");
    }

    @Test
    void totalDaNotaSomaIcmsDeTodosOsItens() {
        NaturezaOperacao natureza = criarNatureza(carregarCst("00"));
        Produto produto1 = criarProduto(carregarCst("00"), new BigDecimal("18.00"), 1);
        Produto produto2 = criarProduto(carregarCst("00"), new BigDecimal("12.00"), 2);
        NotaSaida notaSaida = criarNotaSaida(natureza);

        criarItem(notaSaida, produto1, 1, new BigDecimal("10"), new BigDecimal("10")); // base 100, ICMS 18.00
        criarItem(notaSaida, produto2, 2, new BigDecimal("5"), new BigDecimal("20"));  // base 100, ICMS 12.00

        NotaSaida notaSaidaAtualizada = dataManager.load(NotaSaida.class).id(notaSaida.getId()).one();
        assertThat(notaSaidaAtualizada.getValorMercadoria()).isEqualByComparingTo("200.00");
        assertThat(notaSaidaAtualizada.getBaseIcms()).isEqualByComparingTo("200.00");
        assertThat(notaSaidaAtualizada.getValorIcms()).isEqualByComparingTo("30.00");
        // sem frete/seguro/despesas/desconto: valor da nota = valorMercadoria (ICMS é "por
        // dentro" do preço, não soma — ver NotaSaidaService.calcularValorTotal)
        assertThat(notaSaidaAtualizada.getValor()).isEqualByComparingTo("200.00");
    }

    @Test
    void valorDaNotaRecalculadoAoEditarFreteSemTocarEmItem() {
        NaturezaOperacao natureza = criarNatureza(carregarCst("00"));
        Produto produto = criarProduto(carregarCst("00"), new BigDecimal("18.00"));
        NotaSaida notaSaida = criarNotaSaida(natureza);
        criarItem(notaSaida, produto, 1, new BigDecimal("10"), new BigDecimal("10")); // valorMercadoria 100

        NotaSaida recarregada = dataManager.load(NotaSaida.class).id(notaSaida.getId()).one();
        assertThat(recarregada.getValor()).isEqualByComparingTo("100.00");

        recarregada.setFrete(new BigDecimal("15.00"));
        recarregada = dataManager.save(recarregada);

        assertThat(recarregada.getValor()).isEqualByComparingTo("115.00");
    }
}
