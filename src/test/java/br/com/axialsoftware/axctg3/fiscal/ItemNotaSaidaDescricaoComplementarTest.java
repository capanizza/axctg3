package br.com.axialsoftware.axctg3.fiscal;

import br.com.axialsoftware.axctg3.entity.cadastros.Parceiro;
import br.com.axialsoftware.axctg3.entity.fiscal.ItemNotaSaida;
import br.com.axialsoftware.axctg3.entity.fiscal.NaturezaOperacao;
import br.com.axialsoftware.axctg3.entity.fiscal.NotaSaida;
import br.com.axialsoftware.axctg3.entity.fiscal.Produto;
import br.com.axialsoftware.axctg3.entity.tabelas.ClassTrib;
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
 * Cobre o campo {@code ItemNotaSaida.descricaoComplementar} (varchar(255), default
 * blank) — vai pro {@code infAdProd} da NFe quando preenchido
 * ({@code NfeXmlBuilder.construirDet}), mas a montagem de XML em si não tem teste
 * automatizado neste projeto (ver Javadoc de {@code NfeEmissaoServiceTest}), só
 * validação contra homologação de verdade.
 */
@SpringBootTest
@ExtendWith(AuthenticatedAsAdmin.class)
@ActiveProfiles("test")
class ItemNotaSaidaDescricaoComplementarTest {

    private static final int COD_EMPRESA = 9112;
    private static final int CLASS_TRIB_CODIGO_TESTE = 9990003;

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
        dataManager.load(ClassTrib.class)
                .query("select e from ClassTrib e where e.codigo = :codigo")
                .parameter("codigo", CLASS_TRIB_CODIGO_TESTE)
                .list()
                .forEach(dataManager::remove);
    }

    @Test
    void defaultEmBrancoNaCriacao() {
        ItemNotaSaida item = criarItem(criarProduto());

        assertThat(item.getDescricaoComplementar()).isBlank();
    }

    @Test
    void valorDigitadoPersisteERecarrega() {
        ItemNotaSaida item = criarItem(criarProduto());
        item.setDescricaoComplementar("Peça sob encomenda, prazo 15 dias");
        item = dataManager.save(item);

        ItemNotaSaida recarregado = dataManager.load(ItemNotaSaida.class).id(item.getId()).one();
        assertThat(recarregado.getDescricaoComplementar()).isEqualTo("Peça sob encomenda, prazo 15 dias");
    }

    private ItemNotaSaida criarItem(Produto produto) {
        NaturezaOperacao natureza = criarNatureza();
        NotaSaida notaSaida = criarNotaSaida(natureza);
        ItemNotaSaida item = dataManager.create(ItemNotaSaida.class);
        item.setNotaSaida(notaSaida);
        item.setItem(1);
        item.setProduto(produto);
        item.setQuantidade(new BigDecimal("1"));
        item.setValorUnitario(new BigDecimal("10"));
        return dataManager.save(item);
    }

    private Produto criarProduto() {
        Produto produto = dataManager.create(Produto.class);
        produto.setCodigo(1);
        produto.setCodEmpresa(COD_EMPRESA);
        produto.setDescricao("Produto de teste");
        produto.setApelido("Teste");
        produto.setClassTrib(criarClassTrib());
        return dataManager.save(produto);
    }

    private NaturezaOperacao criarNatureza() {
        NaturezaOperacao n = dataManager.create(NaturezaOperacao.class);
        n.setCodigo(1);
        n.setCodEmpresa(COD_EMPRESA);
        n.setNome("Venda de teste");
        n.setCfop(5102);
        return dataManager.save(n);
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

    private Parceiro criarParceiro() {
        Parceiro p = dataManager.create(Parceiro.class);
        p.setCodigo(1L);
        p.setCodEmpresa(COD_EMPRESA);
        p.setNome("Cliente de Teste");
        p.setApelido("Cliente Teste");
        p.setCnpj("12345678000190");
        return dataManager.save(p);
    }

    private ClassTrib criarClassTrib() {
        ClassTrib c = dataManager.create(ClassTrib.class);
        c.setCodigo(CLASS_TRIB_CODIGO_TESTE);
        c.setCst(1);
        c.setDescricao("ClassTrib de teste");
        c.setTipoAliquota("Padrão");
        c.setNomenclatura("Teste");
        c.setDescricaoTratamentoTributario("Teste");
        return dataManager.save(c);
    }
}
