package br.com.axialsoftware.axctg3.fiscal;

import br.com.axialsoftware.axctg3.entity.cadastros.Empresa;
import br.com.axialsoftware.axctg3.entity.cadastros.Mensagem;
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

    @Autowired
    private br.com.axialsoftware.axctg3.service.fiscal.ItemNotaSaidaTributacaoService tributacaoService;

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
        ItemNotaSaida item1 = carregarItem(nota, 1);
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

    // cStat=531 real (doação CST 41, 2026-09-24): ICMSTot somava a base/valor de ICMS de
    // item cujo grupo ICMS40 sai sem vBC/vICMS.
    @Test
    void icmsTotSoSomaItensComIcmsProprioNoXml() {
        prepararFixtures();
        NotaSaida nota = criarNota(true);
        NaturezaOperacao natureza = dataManager.load(NaturezaOperacao.class).id(nota.getNatureza().getId()).one();
        // Como a natureza de doação real: CST 41 e alíquota de 7% cadastrados nela.
        natureza.setCst(dataManager.load(br.com.axialsoftware.axctg3.entity.tabelas.Cst.class)
                .query("select e from Cst e where e.codigo = '41'").one());
        natureza.setAliqIcms(new BigDecimal("7"));
        dataManager.save(natureza);

        ItemNotaSaida item1 = carregarItem(nota, 1);  // 3 x 10,50 = 31,50
        item1.setCst("41");
        dataManager.save(item1);
        ItemNotaSaida item3 = carregarItem(nota, 3);  // 2 x 7,00 = 14,00 — CST trocado à mão
        item3.setCst("00");
        dataManager.save(item3);
        // item 2 fica sem CST — sai como ICMS40/CST 40 (default da emissão)

        assertThat(carregarItem(nota, 1).getValorIcms()).isZero();
        assertThat(carregarItem(nota, 3).getValorIcms()).isEqualByComparingTo("0.98");

        nota = dataManager.load(NotaSaida.class).id(nota.getId()).one();
        Document doc = nfeXmlBuilder.construir(nota).documento();

        List<Element> dets = elementos(doc.getDocumentElement(), "det");
        assertThat(elementos(porNItem(dets, "1"), "ICMS40")).hasSize(1);
        assertThat(elementos(porNItem(dets, "2"), "ICMS40")).hasSize(1);
        Element icms00 = elementos(porNItem(dets, "3"), "ICMS00").get(0);
        assertThat(new BigDecimal(texto(icms00, "vBC"))).isEqualByComparingTo("14.00");
        assertThat(new BigDecimal(texto(icms00, "vICMS"))).isEqualByComparingTo("0.98");

        Element icmsTot = elementos(doc.getDocumentElement(), "ICMSTot").get(0);
        assertThat(new BigDecimal(texto(icmsTot, "vBC"))).isEqualByComparingTo("14.00");
        assertThat(new BigDecimal(texto(icmsTot, "vICMS"))).isEqualByComparingTo("0.98");
    }

    // Simples (CRT=1), no formato que o legado emite pra GB: CSOSN 101 com pCredSN da
    // alíquota da natureza, CSOSN 400 em ICMSSN102, PIS/COFINS NT 06, sem IBS/CBS.
    @Test
    void simplesNacionalSaiComoNoLegadoDaGb() {
        prepararFixtures();
        tornarSimples();
        NotaSaida nota = criarNota(true);
        NaturezaOperacao natureza = dataManager.load(NaturezaOperacao.class).id(nota.getNatureza().getId()).one();
        // Como a natureza de venda da GB: CSOSN 101 e a alíquota de crédito nela.
        natureza.setCst(dataManager.load(br.com.axialsoftware.axctg3.entity.tabelas.Cst.class)
                .query("select e from Cst e where e.codigo = '101'").one());
        natureza.setAliqIcms(new BigDecimal("4"));
        dataManager.save(natureza);

        trocarCst(nota, 1, "101");  // 3 x 10,50 = 31,50 → crédito 1,26
        trocarCst(nota, 2, "400");  // retorno de industrialização, sem crédito
        trocarCst(nota, 3, "101");  // 2 x 7,00 = 14,00 → crédito 0,56
        assertThat(carregarItem(nota, 2).getValorIcms()).isZero();

        nota = dataManager.load(NotaSaida.class).id(nota.getId()).one();
        Document doc = nfeXmlBuilder.construir(nota).documento();
        Element raiz = doc.getDocumentElement();
        List<Element> dets = elementos(raiz, "det");
        validarContraXsd(doc);

        assertThat(texto(raiz, "CRT")).isEqualTo("1");
        Element sn101 = elementos(porNItem(dets, "1"), "ICMSSN101").get(0);
        assertThat(texto(sn101, "CSOSN")).isEqualTo("101");
        assertThat(texto(sn101, "pCredSN")).isEqualTo("4.0000");
        assertThat(texto(sn101, "vCredICMSSN")).isEqualTo("1.26");
        Element sn102 = elementos(porNItem(dets, "2"), "ICMSSN102").get(0);
        assertThat(texto(sn102, "CSOSN")).isEqualTo("400");
        assertThat(elementos(sn102, "pCredSN")).isEmpty();

        assertThat(elementos(raiz, "PISNT")).hasSize(3);
        assertThat(elementos(raiz, "COFINSNT")).hasSize(3);
        assertThat(elementos(raiz, "PISAliq")).isEmpty();
        assertThat(texto(elementos(raiz, "PISNT").get(0), "CST")).isEqualTo("06");
        assertThat(elementos(raiz, "IBSCBS")).isEmpty();
        assertThat(elementos(raiz, "vItem")).isEmpty();
        assertThat(elementos(raiz, "IBSCBSTot")).isEmpty();
        assertThat(elementos(raiz, "vNFTot")).isEmpty();
        assertThat(elementos(raiz, "cBenef")).isEmpty();

        Element icmsTot = elementos(raiz, "ICMSTot").get(0);
        assertThat(new BigDecimal(texto(icmsTot, "vBC"))).isZero();
        assertThat(new BigDecimal(texto(icmsTot, "vICMS"))).isZero();
        // vTotTrib sobre o vProd do item (sem IBS/CBS somado)
        BigDecimal vProd1 = new BigDecimal("31.50");
        assertThat(new BigDecimal(texto(icmsTot, "vTotTrib")))
                .isEqualByComparingTo(pct(vProd1, "13.45").add(pct(vProd1, "18.00")));

        assertThat(texto(raiz, "infCpl"))
                .startsWith("I - Documento emitido por ME ou EPP optante pelo Simples Nacional.")
                .contains("crédito de ICMS no valor de R$ 1,82, correspondente à alíquota de 4,00%")
                .contains("Valor aproximado dos tributos");
    }

    // infCpl como no f_nfe.pas: observação da nota, Simples, mensagem padrão, tributos — numa
    // linha só (quebra de linha no TString dá cStat=225), sem repetir os incisos I/II quando a
    // mensagem padrão já fala do Simples (a mensagem 2 da GB)
    @Test
    void infCplLevaComplementoEMensagemSemRepetirOSimples() {
        prepararFixtures();
        tornarSimples();
        NotaSaida nota = criarNotaIndustrializacao();
        Mensagem mensagem = dataManager.create(Mensagem.class);
        mensagem.setCodigo(2);
        mensagem.setCodEmpresa(COD_EMPRESA);
        mensagem.setTexto("I - Documento emitido por ME ou EPP optante pelo Simples Nacional.\r\n"
                + "II - Nao gera direito a credito fiscal de IPI.");
        mensagem = dataManager.save(mensagem);
        NotaSaida editada = dataManager.load(NotaSaida.class).id(nota.getId()).one();
        editada.setMensagem(mensagem);
        editada.setComplementoMensagem("PEDIDO DE COMPRA: 38502.\nINSUMOS DEVOLVIDOS DE SUA NF 20943.");
        dataManager.save(editada);

        nota = dataManager.load(NotaSaida.class).id(nota.getId()).one();
        Document doc = nfeXmlBuilder.construir(nota).documento();
        validarContraXsd(doc);
        String infCpl = texto(doc.getDocumentElement(), "infCpl");

        // I e II da mensagem, depois o III gerado (na NF 12784 da GB o III saiu antes)
        assertThat(infCpl)
                .startsWith("PEDIDO DE COMPRA: 38502. INSUMOS DEVOLVIDOS DE SUA NF 20943. I - Documento emitido por ME "
                        + "ou EPP optante pelo Simples Nacional. II - Nao gera direito a credito fiscal de IPI. III - "
                        + "Permite o aproveitamento do crédito de ICMS no valor de R$ 8,00")
                .doesNotContain("\n")
                .doesNotContain("II - Não gera direito");   // o texto gerado (com til) não repete
    }

    @Test
    void simplesRecusaCsosnComStECstDoRegimeNormal() {
        prepararFixtures();
        tornarSimples();
        NotaSaida nota = criarNota(false);
        trocarCst(nota, 1, "201");
        NotaSaida comSt = dataManager.load(NotaSaida.class).id(nota.getId()).one();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> nfeXmlBuilder.construir(comSt))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("CSOSN 201");

        trocarCst(nota, 1, "00");
        NotaSaida comCst = dataManager.load(NotaSaida.class).id(nota.getId()).one();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> nfeXmlBuilder.construir(comCst))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("exige CSOSN");
    }

    // XSD oficial de src/test/resources/nfe-schemas (Signature opcional, ver README de lá).
    private static void validarContraXsd(Document doc) {
        try {
            java.io.StringWriter xml = new java.io.StringWriter();
            javax.xml.transform.TransformerFactory.newInstance().newTransformer().transform(
                    new javax.xml.transform.dom.DOMSource(doc), new javax.xml.transform.stream.StreamResult(xml));
            javax.xml.validation.SchemaFactory.newInstance(javax.xml.XMLConstants.W3C_XML_SCHEMA_NS_URI)
                    .newSchema(new org.springframework.core.io.ClassPathResource("nfe-schemas/nfe_v4.00.xsd").getURL())
                    .newValidator()
                    .validate(new javax.xml.transform.stream.StreamSource(new java.io.StringReader(xml.toString())));
        } catch (Exception e) {
            throw new AssertionError("XML fora do XSD: " + e.getMessage(), e);
        }
    }

    private void tornarSimples() {
        Empresa empresa = dataManager.load(Empresa.class)
                .query("select e from Empresa e where e.codigo = :c").parameter("c", COD_EMPRESA).one();
        empresa.setCrt(CodRegimeTributario.SIMPLES_NACIONAL);
        // endereço completo só pra o XML passar no XSD
        empresa.setLogradouro("Rua de Teste");
        empresa.setNumero("100");
        empresa.setBairro("Centro");
        empresa.setCep("01001000");
        dataManager.save(empresa);

        Parceiro cliente = dataManager.load(Parceiro.class).id(parceiro.getId()).one();
        cliente.setLogradouro("Avenida de Teste");
        cliente.setNumero("200");
        cliente.setBairro("Centro");
        cliente.setCep("01001000");
        cliente.setEstado("SP");
        cliente.setMunicipio(empresa.getMunicipio());
        cliente.setInscricao("123456789012");  // contribuinte
        parceiro = dataManager.save(cliente);
    }

    // Industrialização por encomenda da GB: natureza 5124 tributada (CSOSN 101, 4%), com o
    // material do cliente voltando em 5902. CSOSN de cada item resolvido no save, pela regra
    // da CALCULACSOSN do legado.
    private NotaSaida criarNotaIndustrializacao() {
        NaturezaOperacao natureza = dataManager.create(NaturezaOperacao.class);
        natureza.setCodigo(3);
        natureza.setCodEmpresa(COD_EMPRESA);
        natureza.setNome("Industrialização de teste");
        natureza.setCfop(5124);
        natureza.setVenda(true);
        natureza.setAliqIcms(new BigDecimal("4"));
        natureza.setCst(dataManager.load(br.com.axialsoftware.axctg3.entity.tabelas.Cst.class)
                .query("select e from Cst e where e.codigo = '101'").one());
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

        criarItem(notaSaida, 1, produtoComNcm, 5124, "1", "200.00");  // mão de obra
        criarItem(notaSaida, 2, produtoComNcm, 5124, "1", "150.00");
        criarItem(notaSaida, 3, produtoComNcm, 5902, "1", "350.00");  // material do cliente
        return dataManager.load(NotaSaida.class).id(notaSaida.getId()).one();
    }

    @Test
    void simplesResolveCsosnComoACalculaCsosnDoLegado() {
        prepararFixtures();
        tornarSimples();
        NotaSaida nota = criarNotaIndustrializacao();

        assertThat(carregarItem(nota, 1).getCst()).isEqualTo("101");
        assertThat(carregarItem(nota, 1).getValorIcms()).isEqualByComparingTo("8.00");
        assertThat(carregarItem(nota, 2).getCst()).isEqualTo("400");   // 5124 fora do 1º item
        assertThat(carregarItem(nota, 2).getValorIcms()).isZero();
        assertThat(carregarItem(nota, 3).getCst()).isEqualTo("400");   // 5902

        // não contribuinte (sem IE): 102, sem crédito
        Parceiro cliente = dataManager.load(Parceiro.class).id(parceiro.getId()).one();
        cliente.setInscricao(null);
        dataManager.save(cliente);
        NotaSaida outra = dataManager.load(NotaSaida.class).id(nota.getId()).one();
        criarItem(outra, 4, produtoComNcm, 5101, "1", "10.00");
        assertThat(carregarItem(outra, 4).getCst()).isEqualTo("102");
    }

    // Devolução simbólica (f_nfe.pas): retorno num item de nota de outra natureza sai do
    // total; nota cuja natureza já é o retorno continua somando.
    @Test
    void devolucaoSimbolicaForaDoTotalSoComNaturezaDeOutroCfop() {
        prepararFixtures();
        NotaSaida industrializacao = criarNotaIndustrializacao();
        assertThat(tributacaoService.foraDoTotalPorPadrao(industrializacao, 5902)).isFalse();  // regime normal

        tornarSimples();
        assertThat(tributacaoService.foraDoTotalPorPadrao(industrializacao, 5902)).isTrue();
        assertThat(tributacaoService.foraDoTotalPorPadrao(industrializacao, 6925)).isTrue();
        assertThat(tributacaoService.foraDoTotalPorPadrao(industrializacao, 5124)).isFalse();

        NaturezaOperacao soRetorno = dataManager.load(NaturezaOperacao.class)
                .id(industrializacao.getNatureza().getId()).one();
        soRetorno.setCfop(5902);
        dataManager.save(soRetorno);
        NotaSaida notaDeRetorno = dataManager.load(NotaSaida.class).id(industrializacao.getId()).one();
        assertThat(tributacaoService.foraDoTotalPorPadrao(notaDeRetorno, 5902)).isFalse();
    }

    @Test
    void itemForaDoTotalSaiComIndTotZeroEForaDoVnf() {
        prepararFixtures();
        tornarSimples();
        NotaSaida nota = criarNotaIndustrializacao();
        ItemNotaSaida retorno = carregarItem(nota, 3);
        retorno.setForaDoTotal(true);
        dataManager.save(retorno);

        nota = dataManager.load(NotaSaida.class).id(nota.getId()).one();
        assertThat(nota.getValorMercadoria()).isEqualByComparingTo("350.00");
        assertThat(nota.getValor()).isEqualByComparingTo("350.00");

        Document doc = nfeXmlBuilder.construir(nota).documento();
        validarContraXsd(doc);
        List<Element> dets = elementos(doc.getDocumentElement(), "det");
        assertThat(texto(porNItem(dets, "1"), "indTot")).isEqualTo("1");
        assertThat(texto(porNItem(dets, "3"), "indTot")).isEqualTo("0");
        assertThat(texto(porNItem(dets, "3"), "vProd")).isEqualTo("350.00");
        Element icmsTot = elementos(doc.getDocumentElement(), "ICMSTot").get(0);
        assertThat(texto(icmsTot, "vProd")).isEqualTo("350.00");
        assertThat(texto(icmsTot, "vNF")).isEqualTo("350.00");
        assertThat(texto(doc.getDocumentElement(), "infCpl"))
                .contains("crédito de ICMS no valor de R$ 8,00, correspondente à alíquota de 4,00%");
    }

    private void trocarCst(NotaSaida nota, int numero, String cst) {
        ItemNotaSaida item = carregarItem(nota, numero);
        item.setCst(cst);
        dataManager.save(item);
    }

    private ItemNotaSaida carregarItem(NotaSaida nota, int numero) {
        return dataManager.load(ItemNotaSaida.class)
                .query("select e from ItemNotaSaida e where e.notaSaida = :nota and e.item = :n")
                .parameter("nota", nota)
                .parameter("n", numero)
                .fetchPlan(fp -> fp.addFetchPlan(io.jmix.core.FetchPlan.BASE)
                        .add("produto", io.jmix.core.FetchPlan.BASE)
                        .add("notaSaida", fn -> fn.addFetchPlan(io.jmix.core.FetchPlan.BASE)
                                .add("natureza", io.jmix.core.FetchPlan.BASE)))
                .one();
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
        apagar(dataManager.load(Mensagem.class)
                .query("select e from Mensagem e where e.codEmpresa = :c").parameter("c", COD_EMPRESA).list());
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
