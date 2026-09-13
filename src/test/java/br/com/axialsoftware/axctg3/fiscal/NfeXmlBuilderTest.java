package br.com.axialsoftware.axctg3.fiscal;

import br.com.axialsoftware.axctg3.entity.cadastros.Empresa;
import br.com.axialsoftware.axctg3.entity.cadastros.Parceiro;
import br.com.axialsoftware.axctg3.entity.enums.AmbienteNfe;
import br.com.axialsoftware.axctg3.entity.enums.CodRegimeTributario;
import br.com.axialsoftware.axctg3.entity.enums.FinNfe;
import br.com.axialsoftware.axctg3.entity.fiscal.ItemNotaSaida;
import br.com.axialsoftware.axctg3.entity.fiscal.NaturezaOperacao;
import br.com.axialsoftware.axctg3.entity.fiscal.NotaSaida;
import br.com.axialsoftware.axctg3.entity.fiscal.NotaSaidaComplementarValores;
import br.com.axialsoftware.axctg3.entity.fiscal.Produto;
import br.com.axialsoftware.axctg3.entity.tabelas.ClassTrib;
import br.com.axialsoftware.axctg3.entity.tabelas.Municipio;
import br.com.axialsoftware.axctg3.service.fiscal.NfeXmlBuilder;
import br.com.axialsoftware.axctg3.test_support.AuthenticatedAsAdmin;
import io.jmix.core.DataManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.w3c.dom.Document;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cobre o cálculo de ICMS/IPI que {@link NfeXmlBuilder} passou a fazer ao vivo em
 * 2026-09-13 (antes era transcrito de campos digitados em {@code ItemNotaSaida}, agora
 * {@code ItemNotaSaida} não guarda mais esses valores — ver Javadoc da classe). Só monta
 * o XML (sem assinar/transmitir, sem certificado) e inspeciona o {@link Document}
 * resultante via XPath — mesmo limite dos outros testes de emissão (montagem de XML tem
 * teste automatizado, assinatura/transmissão só em homologação real).
 */
@SpringBootTest
@ExtendWith(AuthenticatedAsAdmin.class)
@ActiveProfiles("test")
class NfeXmlBuilderTest {

    private static final int COD_EMPRESA = 9112;
    private static final int MUNICIPIO_TESTE = 3550308;
    private static final int CLASS_TRIB_CODIGO_TESTE = 9990002;

    private static final String NS_NFE = "http://www.portalfiscal.inf.br/nfe";

    @Autowired
    private DataManager dataManager;
    @Autowired
    private NfeXmlBuilder xmlBuilder;

    @AfterEach
    void tearDown() {
        dataManager.load(NotaSaidaComplementarValores.class)
                .query("select e from NotaSaidaComplementarValores e where e.notaSaida.codEmpresa = :codEmpresa")
                .parameter("codEmpresa", COD_EMPRESA)
                .list()
                .forEach(dataManager::remove);
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
        dataManager.load(Empresa.class)
                .query("select e from Empresa e where e.codigo = :codigo")
                .parameter("codigo", COD_EMPRESA)
                .list()
                .forEach(dataManager::remove);
        dataManager.load(ClassTrib.class)
                .query("select e from ClassTrib e where e.codigo = :codigo")
                .parameter("codigo", CLASS_TRIB_CODIGO_TESTE)
                .list()
                .forEach(dataManager::remove);
        // Município é tabela compartilhada — não apaga (outros testes podem já ter
        // criado o mesmo código; garantirMunicipio() abaixo reaproveita se existir).
    }

    private Municipio garantirMunicipio() {
        return dataManager.load(Municipio.class)
                .query("select e from Municipio e where e.codigo = :codigo")
                .parameter("codigo", MUNICIPIO_TESTE)
                .optional()
                .orElseGet(() -> {
                    Municipio municipio = dataManager.create(Municipio.class);
                    municipio.setCodigo(MUNICIPIO_TESTE);
                    municipio.setNome("Município Teste");
                    municipio.setUf("SP");
                    return dataManager.save(municipio);
                });
    }

    private Empresa criarEmpresa() {
        Empresa empresa = dataManager.create(Empresa.class);
        empresa.setCodigo(COD_EMPRESA);
        empresa.setNome("Empresa de Teste");
        empresa.setApelido("Teste");
        empresa.setCnpj("12345678000190");
        empresa.setInscEst("ISENTO");
        empresa.setCrt(CodRegimeTributario.REGIME_NORMAL);
        empresa.setAmbienteNfe(AmbienteNfe.HOMOLOGACAO);
        empresa.setMunicipio(garantirMunicipio());
        return dataManager.save(empresa);
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

    private NaturezaOperacao criarNatureza(boolean venda, BigDecimal aliqIcms) {
        NaturezaOperacao natureza = dataManager.create(NaturezaOperacao.class);
        natureza.setCodigo(1);
        natureza.setCodEmpresa(COD_EMPRESA);
        natureza.setNome("Natureza de teste");
        natureza.setCfop(5102);
        natureza.setVenda(venda);
        natureza.setAliqIcms(aliqIcms);
        return dataManager.save(natureza);
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

    private Produto criarProduto(BigDecimal aliqIcms) {
        Produto produto = dataManager.create(Produto.class);
        produto.setCodigo(1);
        produto.setCodEmpresa(COD_EMPRESA);
        produto.setDescricao("Produto de teste");
        produto.setApelido("Produto");
        produto.setAliqIcms(aliqIcms);
        produto.setClassTrib(criarClassTrib());
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

    private ItemNotaSaida criarItem(NotaSaida notaSaida, Produto produto, BigDecimal quantidade,
                                     BigDecimal valorUnitario) {
        ItemNotaSaida item = dataManager.create(ItemNotaSaida.class);
        item.setNotaSaida(notaSaida);
        item.setItem(1);
        item.setProduto(produto);
        item.setQuantidade(quantidade);
        item.setValorUnitario(valorUnitario);
        item.setCfop(5102);
        // "00" explícito — sem isso o fallback de resolverCstIcms (empresa não-Simples)
        // cai em "40" (isenta), que não emite vBC/pICMS/vICMS nenhum.
        item.setCst("00");
        return dataManager.save(item);
    }

    private String xpathText(Document doc, String expression) {
        try {
            XPath xpath = XPathFactory.newInstance().newXPath();
            return (String) xpath.evaluate(expression, doc, XPathConstants.STRING);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** ICMS00: base = subTotal (quantidade×valorUnitario), alíquota/valor da alíquota
     * DO PRODUTO — natureza é venda (regra decidida 2026-09-13). Alíquota da natureza
     * (99%) deliberadamente diferente da do produto pra provar que é o produto que
     * vale, não a natureza. */
    @Test
    void icmsUsaAliquotaDoProdutoQuandoNaturezaEhVenda() {
        criarEmpresa();
        NaturezaOperacao natureza = criarNatureza(true, new BigDecimal("99.00"));
        Produto produto = criarProduto(new BigDecimal("18.00"));
        NotaSaida notaSaida = criarNotaSaida(natureza);
        criarItem(notaSaida, produto, new BigDecimal("2"), new BigDecimal("50.00"));
        notaSaida = dataManager.load(NotaSaida.class).id(notaSaida.getId())
                .fetchPlan(fp -> fp.addFetchPlan(io.jmix.core.FetchPlan.BASE)
                        .add("itens", fpItens -> fpItens.addFetchPlan(io.jmix.core.FetchPlan.BASE)
                                .add("produto", io.jmix.core.FetchPlan.BASE))
                        .add("natureza", io.jmix.core.FetchPlan.BASE)
                        .add("parceiro", io.jmix.core.FetchPlan.BASE))
                .one();

        Document doc = xmlBuilder.construir(notaSaida).documento();

        String base = "//*[local-name()='ICMS00']/*[local-name()='vBC']";
        String aliq = "//*[local-name()='ICMS00']/*[local-name()='pICMS']";
        String valor = "//*[local-name()='ICMS00']/*[local-name()='vICMS']";
        assertThat(xpathText(doc, base)).isEqualTo("100.00");
        assertThat(xpathText(doc, aliq)).isEqualTo("18.00");
        assertThat(xpathText(doc, valor)).isEqualTo("18.00");
    }

    /** Natureza NÃO é venda — a alíquota passa a ser a DA NATUREZA, não a do produto
     * (produto deliberadamente com uma alíquota diferente, pra provar que é ignorada). */
    @Test
    void icmsUsaAliquotaDaNaturezaQuandoNaoEhVenda() {
        criarEmpresa();
        NaturezaOperacao natureza = criarNatureza(false, new BigDecimal("7.00"));
        Produto produto = criarProduto(new BigDecimal("18.00"));
        NotaSaida notaSaida = criarNotaSaida(natureza);
        criarItem(notaSaida, produto, BigDecimal.ONE, new BigDecimal("100.00"));
        notaSaida = dataManager.load(NotaSaida.class).id(notaSaida.getId())
                .fetchPlan(fp -> fp.addFetchPlan(io.jmix.core.FetchPlan.BASE)
                        .add("itens", fpItens -> fpItens.addFetchPlan(io.jmix.core.FetchPlan.BASE)
                                .add("produto", io.jmix.core.FetchPlan.BASE))
                        .add("natureza", io.jmix.core.FetchPlan.BASE)
                        .add("parceiro", io.jmix.core.FetchPlan.BASE))
                .one();

        Document doc = xmlBuilder.construir(notaSaida).documento();

        assertThat(xpathText(doc, "//*[local-name()='ICMS00']/*[local-name()='pICMS']")).isEqualTo("7.00");
        assertThat(xpathText(doc, "//*[local-name()='ICMS00']/*[local-name()='vICMS']")).isEqualTo("7.00");
    }

    /** Numa complementar, ICMS não é calculado de Produto/NaturezaOperacao — vem
     * digitado direto de {@link NotaSaidaComplementarValores}. */
    @Test
    void icmsDaComplementarVemDosValoresDigitadosNaoCalculado() {
        criarEmpresa();
        NaturezaOperacao natureza = criarNatureza(true, BigDecimal.ZERO);
        // aliqIcms do produto (55%) deliberadamente bem diferente do que o teste espera,
        // pra provar que não é usado — o valor vem só da entidade satélite.
        Produto produto = criarProduto(new BigDecimal("55.00"));
        NotaSaida notaSaida = criarNotaSaida(natureza);
        notaSaida.setFinNfe(FinNfe.COMPLEMENTAR);
        notaSaida.setChaveNotaOriginal("35240512345678000199550010000000041123456782");
        dataManager.save(notaSaida);
        criarItem(notaSaida, produto, BigDecimal.ONE, BigDecimal.ZERO);
        NotaSaidaComplementarValores valores = dataManager.create(NotaSaidaComplementarValores.class);
        valores.setNotaSaida(notaSaida);
        valores.setBaseIcms(new BigDecimal("100.00"));
        valores.setValorIcms(new BigDecimal("25.00"));
        dataManager.save(valores);
        notaSaida = dataManager.load(NotaSaida.class).id(notaSaida.getId())
                .fetchPlan(fp -> fp.addFetchPlan(io.jmix.core.FetchPlan.BASE)
                        .add("itens", fpItens -> fpItens.addFetchPlan(io.jmix.core.FetchPlan.BASE)
                                .add("produto", io.jmix.core.FetchPlan.BASE))
                        .add("natureza", io.jmix.core.FetchPlan.BASE)
                        .add("parceiro", io.jmix.core.FetchPlan.BASE))
                .one();

        Document doc = xmlBuilder.construir(notaSaida).documento();

        assertThat(xpathText(doc, "//*[local-name()='ICMS00']/*[local-name()='vBC']")).isEqualTo("100.00");
        assertThat(xpathText(doc, "//*[local-name()='ICMS00']/*[local-name()='vICMS']")).isEqualTo("25.00");
        // alíquota derivada de vICMS/vBC (25/100), não de Produto.aliqIcms (55%)
        assertThat(xpathText(doc, "//*[local-name()='ICMS00']/*[local-name()='pICMS']")).isEqualTo("25.00");
    }
}
