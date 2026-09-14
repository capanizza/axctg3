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
 * Cobre a precedência de CST de ICMS decidida em 2026-09-14 (ver Javadoc de {@link Cst}
 * e {@code ItemNotaSaidaEventListener}): substitui a árvore procedural do legado por um
 * catálogo, com a mesma precedência natureza/produto já usada pro cClassTrib do IBS/CBS.
 * Item de teste criado direto via {@link DataManager}, sem passar pela view — mesmo
 * padrão de {@code NfeEmissaoServiceTest}.
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

    private ClassTrib criarClassTrib() {
        ClassTrib classTrib = dataManager.create(ClassTrib.class);
        classTrib.setCodigo(CLASS_TRIB_CODIGO_TESTE);
        classTrib.setCst(1);
        classTrib.setDescricao("ClassTrib de teste");
        classTrib.setTipoAliquota("Padrão");
        classTrib.setNomenclatura("Teste");
        classTrib.setDescricaoTratamentoTributario("Teste");
        return dataManager.save(classTrib);
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
        NaturezaOperacao natureza = dataManager.create(NaturezaOperacao.class);
        natureza.setCodigo(1);
        natureza.setCodEmpresa(COD_EMPRESA);
        natureza.setNome("Venda de teste");
        natureza.setCfop(5102);
        natureza.setCst(cst);
        return dataManager.save(natureza);
    }

    private Produto criarProduto(Cst cst) {
        Produto produto = dataManager.create(Produto.class);
        produto.setCodigo(1);
        produto.setCodEmpresa(COD_EMPRESA);
        produto.setDescricao("Produto de teste");
        produto.setApelido("Teste");
        produto.setClassTrib(criarClassTrib());
        produto.setCst(cst);
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
        ItemNotaSaida item = dataManager.create(ItemNotaSaida.class);
        item.setNotaSaida(notaSaida);
        // fixo, pra não depender do codEmpresa da sessão pra numerar via Sequence
        item.setItem(1);
        item.setProduto(produto);
        item.setQuantidade(new BigDecimal("10"));
        item.setValorUnitario(new BigDecimal("10"));
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
}
