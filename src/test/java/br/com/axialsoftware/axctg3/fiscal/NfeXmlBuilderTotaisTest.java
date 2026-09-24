package br.com.axialsoftware.axctg3.fiscal;

import br.com.axialsoftware.axctg3.entity.cadastros.Empresa;
import br.com.axialsoftware.axctg3.entity.cadastros.Parceiro;
import br.com.axialsoftware.axctg3.entity.enums.AmbienteNfe;
import br.com.axialsoftware.axctg3.entity.enums.CodRegimeTributario;
import br.com.axialsoftware.axctg3.entity.fiscal.ItemNotaSaida;
import br.com.axialsoftware.axctg3.entity.fiscal.NaturezaOperacao;
import br.com.axialsoftware.axctg3.entity.fiscal.NotaSaida;
import br.com.axialsoftware.axctg3.entity.fiscal.Produto;
import br.com.axialsoftware.axctg3.entity.tabelas.ClassTrib;
import br.com.axialsoftware.axctg3.entity.tabelas.ClassificacaoFiscal;
import br.com.axialsoftware.axctg3.entity.tabelas.Municipio;
import br.com.axialsoftware.axctg3.entity.tabelas.TabelaIbpt;
import br.com.axialsoftware.axctg3.service.fiscal.NfeXmlBuilder;
import br.com.axialsoftware.axctg3.test_support.AuthenticatedAsAdmin;
import io.jmix.core.DataManager;
import io.jmix.core.SaveContext;
import io.jmix.data.PersistenceHints;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Totais montados pelo {@link NfeXmlBuilder} contra a soma dos itens (a SEFAZ confere
 * cada um): o vTotTrib (Lei 12.741/2012, Tabela IBPT — item a item sobre o vItem, só
 * natureza de venda e fora dos CFOPs de remessa/retorno, totalizado em ICMSTot e descrito
 * no infCpl) e o IBSCBSTot com item de cClassTrib "Sem alíquota" (cStat=1076).
 */
@SpringBootTest
@ExtendWith(AuthenticatedAsAdmin.class)
@ActiveProfiles("test")
class NfeXmlBuilderTotaisTest {

    private static final String NS = "http://www.portalfiscal.inf.br/nfe";
    private static final int COD_EMPRESA = 9431;
    // NCM fictício (capítulo 99 não existe na NCM) — não colide com linhas reais de SP.
    private static final String NCM_TESTE = "99990001";

    // Tabelas globais com índice único sem filtro de soft delete no HSQLDB — base variável.
    private final int codigoMunicipioTeste = 9000000 + (int) (System.currentTimeMillis() % 900000);
    private final int classTribCodigoTeste = 9990000 + (int) (System.currentTimeMillis() % 9000);
    private final int classTribSemAliquotaTeste = 9970000 + (int) (System.currentTimeMillis() % 9000);
    private final int classificacaoCodigoTeste = 900000 + (int) (System.currentTimeMillis() % 90000);

    @Autowired
    private DataManager dataManager;

    @Autowired
    private NfeXmlBuilder nfeXmlBuilder;

    private Parceiro parceiro;
    private Produto produtoComNcm;
    private Produto produtoSemNcm;

    private void prepararFixtures() {
        Municipio municipio = dataManager.create(Municipio.class);
        municipio.setCodigo(codigoMunicipioTeste);
        municipio.setNome("São Paulo");
        municipio.setUf("SP");
        dataManager.save(municipio);

        ClassTrib classTrib = dataManager.create(ClassTrib.class);
        classTrib.setCodigo(classTribCodigoTeste);
        classTrib.setCst(1);
        classTrib.setDescricao("ClassTrib de teste");
        classTrib.setTipoAliquota("Padrão");
        classTrib.setNomenclatura("Teste");
        classTrib.setDescricaoTratamentoTributario("Teste");
        dataManager.save(classTrib);

        ClassificacaoFiscal classificacao = dataManager.create(ClassificacaoFiscal.class);
        classificacao.setCodigo(classificacaoCodigoTeste);
        classificacao.setCodNcm(NCM_TESTE);
        classificacao.setDescricao("NCM de teste");
        dataManager.save(classificacao);

        TabelaIbpt ibpt = dataManager.create(TabelaIbpt.class);
        ibpt.setUf("SP");
        ibpt.setCodigo(NCM_TESTE);
        ibpt.setTipo(0);
        ibpt.setAliqNacionalFederal(new BigDecimal("13.45"));
        ibpt.setAliqImportadosFederal(new BigDecimal("15.45"));
        ibpt.setAliqEstadual(new BigDecimal("18.00"));
        ibpt.setChave("TST999");
        dataManager.save(ibpt);

        Empresa empresa = dataManager.create(Empresa.class);
        empresa.setCodigo(COD_EMPRESA);
        empresa.setNome("Empresa de Teste");
        empresa.setApelido("Teste");
        empresa.setCnpj("12345678000190");
        empresa.setInscEst("ISENTO");
        empresa.setCrt(CodRegimeTributario.REGIME_NORMAL);
        empresa.setAmbienteNfe(AmbienteNfe.HOMOLOGACAO);
        empresa.setMunicipio(municipio);
        dataManager.save(empresa);

        parceiro = dataManager.create(Parceiro.class);
        parceiro.setCodigo(1L);
        parceiro.setCodEmpresa(COD_EMPRESA);
        parceiro.setNome("Cliente de Teste");
        parceiro.setApelido("Cliente Teste");
        parceiro.setCnpj("98765432000155");
        parceiro.setCliente(true);
        dataManager.save(parceiro);

        produtoComNcm = criarProduto(1, classTrib, classificacao);
        produtoSemNcm = criarProduto(2, classTrib, null);
    }

    private Produto criarProduto(int codigo, ClassTrib classTrib, ClassificacaoFiscal classificacao) {
        Produto produto = dataManager.create(Produto.class);
        produto.setCodigo(codigo);
        produto.setCodEmpresa(COD_EMPRESA);
        produto.setDescricao("Produto de Teste " + codigo);
        produto.setApelido("Teste" + codigo);
        produto.setClassTrib(classTrib);
        produto.setClassificacaoFiscal(classificacao);
        return dataManager.save(produto);
    }

    private NotaSaida criarNota(boolean venda) {
        NaturezaOperacao natureza = dataManager.create(NaturezaOperacao.class);
        natureza.setCodigo(venda ? 1 : 2);
        natureza.setCodEmpresa(COD_EMPRESA);
        natureza.setNome(venda ? "Venda de teste" : "Remessa de teste");
        natureza.setCfop(5102);
        natureza.setVenda(venda);
        dataManager.save(natureza);

        NotaSaida notaSaida = dataManager.create(NotaSaida.class);
        notaSaida.setCodEmpresa(COD_EMPRESA);
        notaSaida.setDataEmissao(LocalDate.now());
        notaSaida.setDataSaida(LocalDate.now());
        notaSaida.setEspecie("NF");
        notaSaida.setSerie("1");
        notaSaida.setParceiro(parceiro);
        notaSaida.setNatureza(natureza);
        notaSaida = dataManager.save(notaSaida);

        criarItem(notaSaida, 1, produtoComNcm, 5102, "3", "10.50");
        criarItem(notaSaida, 2, produtoComNcm, 5902, "1", "100.00");
        criarItem(notaSaida, 3, produtoSemNcm, 5102, "2", "7.00");

        return dataManager.load(NotaSaida.class).id(notaSaida.getId()).one();
    }

    private void criarItem(NotaSaida notaSaida, int seq, Produto produto, int cfop, String qtd, String valor) {
        ItemNotaSaida item = dataManager.create(ItemNotaSaida.class);
        item.setNotaSaida(notaSaida);
        item.setItem(seq);
        item.setProduto(produto);
        item.setCfop(cfop);
        item.setQuantidade(new BigDecimal(qtd));
        item.setValorUnitario(new BigDecimal(valor));
        dataManager.save(item);
    }

    @Test
    void vendaCalculaSoItemTributavelComNcmNaTabela() {
        prepararFixtures();
        Document doc = nfeXmlBuilder.construir(criarNota(true)).documento();

        List<Element> dets = elementos(doc.getDocumentElement(), "det");
        assertThat(dets).hasSize(3);
        Element det1 = porNItem(dets, "1");
        BigDecimal vItem1 = new BigDecimal(texto(det1, "vItem"));
        BigDecimal esperado = pct(vItem1, "13.45").add(pct(vItem1, "18.00"));
        assertThat(esperado).isPositive();

        assertThat(new BigDecimal(texto(elementos(det1, "imposto").get(0), "vTotTrib"))).isEqualByComparingTo(esperado);
        assertThat(new BigDecimal(texto(elementos(porNItem(dets, "2"), "imposto").get(0), "vTotTrib"))).isZero();
        assertThat(new BigDecimal(texto(elementos(porNItem(dets, "3"), "imposto").get(0), "vTotTrib"))).isZero();

        Element icmsTot = elementos(doc.getDocumentElement(), "ICMSTot").get(0);
        assertThat(new BigDecimal(texto(icmsTot, "vTotTrib"))).isEqualByComparingTo(esperado);

        String infCpl = texto(doc.getDocumentElement(), "infCpl");
        assertThat(infCpl)
                .startsWith("Valor aproximado dos tributos: R$ ")
                .contains("Federal R$ " + moeda(pct(vItem1, "13.45")))
                .contains("Estadual R$ " + moeda(pct(vItem1, "18.00")))
                .endsWith("Fonte: IBPT TST999");
    }

    @Test
    void itemSemAliquotaIbsCbsFicaForaDosTotaisDeIbsCbs() {
        prepararFixtures();
        ClassTrib semAliquota = dataManager.create(ClassTrib.class);
        semAliquota.setCodigo(classTribSemAliquotaTeste);
        semAliquota.setCst(410);
        semAliquota.setDescricao("Sem alíquota de teste");
        semAliquota.setTipoAliquota("Sem alíquota");
        semAliquota.setNomenclatura("Teste");
        semAliquota.setDescricaoTratamentoTributario("Teste");
        dataManager.save(semAliquota);

        NotaSaida nota = criarNota(false);
        // item 1 passa a ser "Sem alíquota"; itens 2 e 3 continuam com o ClassTrib "Padrão"
        ItemNotaSaida item1 = dataManager.load(ItemNotaSaida.class)
                .query("select e from ItemNotaSaida e where e.notaSaida = :nota and e.item = 1")
                .parameter("nota", nota)
                .fetchPlan(fp -> fp.addFetchPlan(io.jmix.core.FetchPlan.BASE)
                        .add("produto", io.jmix.core.FetchPlan.BASE)
                        .add("notaSaida", fn -> fn.addFetchPlan(io.jmix.core.FetchPlan.BASE)
                                .add("natureza", io.jmix.core.FetchPlan.BASE)))
                .one();
        item1.setCodClassTrib(classTribSemAliquotaTeste);
        dataManager.save(item1);
        nota = dataManager.load(NotaSaida.class).id(nota.getId()).one();

        Document doc = nfeXmlBuilder.construir(nota).documento();
        List<Element> dets = elementos(doc.getDocumentElement(), "det");

        Element det1 = porNItem(dets, "1");
        assertThat(elementos(det1, "gIBSCBS")).isEmpty();
        assertThat(new BigDecimal(texto(det1, "vItem"))).isEqualByComparingTo("31.50");

        BigDecimal somaVbc = BigDecimal.ZERO;
        BigDecimal somaVIbs = BigDecimal.ZERO;
        BigDecimal somaVCbs = BigDecimal.ZERO;
        for (Element det : dets) {
            for (Element g : elementos(det, "gIBSCBS")) {
                somaVbc = somaVbc.add(new BigDecimal(texto(g, "vBC")));
                somaVIbs = somaVIbs.add(new BigDecimal(texto(g, "vIBS")));
                somaVCbs = somaVCbs.add(new BigDecimal(texto(g, "vCBS")));
            }
        }
        assertThat(somaVbc).isEqualByComparingTo("114.00");

        Element tot = elementos(doc.getDocumentElement(), "IBSCBSTot").get(0);
        assertThat(new BigDecimal(texto(tot, "vBCIBSCBS"))).isEqualByComparingTo(somaVbc);
        assertThat(new BigDecimal(texto(tot, "vIBS"))).isEqualByComparingTo(somaVIbs);
        assertThat(new BigDecimal(texto(tot, "vCBS"))).isEqualByComparingTo(somaVCbs);
    }

    @Test
    void naturezaQueNaoEhVendaZeraTudoESemInfCpl() {
        prepararFixtures();
        Document doc = nfeXmlBuilder.construir(criarNota(false)).documento();

        for (Element imposto : elementos(doc.getDocumentElement(), "imposto")) {
            assertThat(new BigDecimal(texto(imposto, "vTotTrib"))).isZero();
        }
        Element icmsTot = elementos(doc.getDocumentElement(), "ICMSTot").get(0);
        assertThat(new BigDecimal(texto(icmsTot, "vTotTrib"))).isZero();
        assertThat(elementos(doc.getDocumentElement(), "infAdic")).isEmpty();
    }

    private BigDecimal pct(BigDecimal base, String aliq) {
        return base.multiply(new BigDecimal(aliq)).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    private String moeda(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP).toPlainString().replace('.', ',');
    }

    private Element porNItem(List<Element> dets, String nItem) {
        return dets.stream().filter(d -> nItem.equals(d.getAttribute("nItem"))).findFirst().orElseThrow();
    }

    private List<Element> elementos(Element pai, String tag) {
        NodeList nl = pai.getElementsByTagNameNS(NS, tag);
        List<Element> r = new java.util.ArrayList<>();
        for (int i = 0; i < nl.getLength(); i++) {
            r.add((Element) nl.item(i));
        }
        return r;
    }

    // Primeiro descendente com a tag (o vTotTrib de imposto vem antes de qualquer outro).
    private String texto(Element pai, String tag) {
        List<Element> l = elementos(pai, tag);
        assertThat(l).as("tag " + tag).isNotEmpty();
        return l.get(0).getTextContent();
    }

    @AfterEach
    void tearDown() {
        apagar(dataManager.load(ItemNotaSaida.class)
                .query("select e from ItemNotaSaida e where e.notaSaida.codEmpresa = :c")
                .parameter("c", COD_EMPRESA).list());
        apagar(dataManager.load(NotaSaida.class)
                .query("select e from NotaSaida e where e.codEmpresa = :c").parameter("c", COD_EMPRESA).list());
        apagar(dataManager.load(Produto.class)
                .query("select e from Produto e where e.codEmpresa = :c").parameter("c", COD_EMPRESA).list());
        apagar(dataManager.load(NaturezaOperacao.class)
                .query("select e from NaturezaOperacao e where e.codEmpresa = :c").parameter("c", COD_EMPRESA).list());
        apagar(dataManager.load(Parceiro.class)
                .query("select e from Parceiro e where e.codEmpresa = :c").parameter("c", COD_EMPRESA).list());
        apagar(dataManager.load(Empresa.class)
                .query("select e from Empresa e where e.codigo = :c").parameter("c", COD_EMPRESA).list());
        apagar(dataManager.load(Municipio.class)
                .query("select e from Municipio e where e.codigo = :c").parameter("c", codigoMunicipioTeste).list());
        apagar(dataManager.load(ClassTrib.class)
                .query("select e from ClassTrib e where e.codigo = :c").parameter("c", classTribCodigoTeste).list());
        apagar(dataManager.load(ClassTrib.class)
                .query("select e from ClassTrib e where e.codigo = :c").parameter("c", classTribSemAliquotaTeste).list());
        apagar(dataManager.load(ClassificacaoFiscal.class)
                .query("select e from ClassificacaoFiscal e where e.codigo = :c").parameter("c", classificacaoCodigoTeste).list());
        apagar(dataManager.load(TabelaIbpt.class)
                .query("select e from TabelaIbpt e where e.codigo = :c").parameter("c", NCM_TESTE).list());
    }

    private void apagar(List<?> entidades) {
        if (!entidades.isEmpty()) {
            dataManager.save(new SaveContext()
                    .setHint(PersistenceHints.SOFT_DELETION, false)
                    .removing(entidades.toArray()));
        }
    }
}
