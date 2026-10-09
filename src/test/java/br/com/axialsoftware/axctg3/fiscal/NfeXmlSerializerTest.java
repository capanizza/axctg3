package br.com.axialsoftware.axctg3.fiscal;

import br.com.axialsoftware.axctg3.entity.enums.FormaImportacao;
import br.com.axialsoftware.axctg3.entity.enums.ViaTransporteInternacional;
import br.com.axialsoftware.axctg3.entity.fiscal.Nfe;
import br.com.axialsoftware.axctg3.entity.fiscal.NfeDi;
import br.com.axialsoftware.axctg3.entity.fiscal.NfeDiAdicao;
import br.com.axialsoftware.axctg3.entity.fiscal.NfeItem;
import br.com.axialsoftware.axctg3.entity.fiscal.NfePagamento;
import br.com.axialsoftware.axctg3.entity.fiscal.NfeVolume;
import br.com.axialsoftware.axctg3.service.fiscal.NfeXmlParser;
import br.com.axialsoftware.axctg3.service.fiscal.NfeXmlSerializer;
import br.com.axialsoftware.axctg3.test_support.AuthenticatedAsAdmin;
import io.jmix.core.DataManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;
import org.w3c.dom.Document;

import javax.xml.XMLConstants;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link NfeXmlSerializer}: o XML escrito a partir de uma {@link Nfe} passa no XSD oficial
 * (src/test/resources/nfe-schemas, ver README.txt de lá) e, lido de volta pelo
 * {@link NfeXmlParser}, devolve os mesmos valores em todos os campos — a ida e volta que
 * garante que o serializador e o parser cobrem os mesmos grupos.
 *
 * <p>O caso da importação reproduz o espelho do despachante usado como gabarito da NFe
 * digitada (cliente do Simples, 2026-10-09): valor aduaneiro 73.036,78, II 20%, PIS 2,10%,
 * COFINS 10,25%, Siscomex 154,23, ICMS 18% sobre base reduzida a 8,8/18 do total
 * (Convênio 52/91), CSOSN 900 com origem 1.
 */
@SpringBootTest
@ExtendWith(AuthenticatedAsAdmin.class)
@ActiveProfiles("test")
class NfeXmlSerializerTest {

    private static final String CHAVE_IMPORTACAO = "35261012345678000199550010000012341000012343";

    // fora da ida e volta: protocolo/cancelamento/XMLs gravados não fazem parte do infNFe, os
    // de controle interno não vêm de XML nenhum, e indDoacao saiu do leiaute atual (o parser
    // ainda lê de XML antigo, o serializador não escreve)
    private static final Set<String> FORA_DO_XML = Set.of(
            "indDoacao", "id", "version", "createdBy", "createdDate", "lastModifiedBy", "lastModifiedDate", "deletedBy",
            "deletedDate", "codEmpresa", "digitada", "chaveTentativa", "xmlEnvio", "xmlRetorno",
            "protTpAmb", "protVerAplic", "protDhRecbto", "protNProt", "protDigVal", "protCStat", "protXMotivo",
            "cancCStat", "cancXMotivo", "cancNProt", "cancDhRegEvento", "cancXJust", "cancXmlRetorno");

    @Autowired
    private DataManager dataManager;

    @Autowired
    private NfeXmlSerializer serializer;

    @Autowired
    private NfeXmlParser parser;

    @Test
    void amostraDeImportacao_idaEVolta_preservaTodosOsCampos() throws Exception {
        Nfe original = parser.parse(ler("br/com/axialsoftware/axctg3/fiscal/nfe_import_sample.xml"));

        byte[] xml = paraBytes(serializer.serializar(original));
        validarContraXsd(xml);
        Nfe relida = parser.parse(xml);

        assertThat(diferencas(original, relida)).isEmpty();
    }

    @Test
    void notaDeImportacaoDoSimples_passaNoXsdEPreservaDiEII() throws Exception {
        Nfe nfe = notaDeImportacao();

        byte[] xml = paraBytes(serializer.serializar(nfe));
        validarContraXsd(xml);
        String texto = new String(xml, java.nio.charset.StandardCharsets.UTF_8);
        assertThat(texto).contains("<idEstrangeiro/>", "<UF>EX</UF>", "<cPais>230</cPais>", "<tpNF>0</tpNF>",
                "<idDest>3</idDest>", "<ICMSSN900>", "<orig>1</orig>", "<pRedBC>51.1111</pRedBC>",
                "<vBC>51900.71</vBC>", "<vICMS>9342.13</vICMS>", "<nDI>2608986187</nDI>",
                "<tpViaTransp>4</tpViaTransp>", "<tpIntermedio>1</tpIntermedio>", "<nSeqAdic>1</nSeqAdic>",
                "<vII>14607.36</vII>", "<vNF>106160.54</vNF>");
        // o II vem logo depois do IPI e antes do PIS, dentro de imposto
        assertThat(texto.indexOf("<II>")).isBetween(texto.indexOf("</IPI>"), texto.indexOf("<PIS>"));

        Nfe relida = parser.parse(xml);
        assertThat(diferencas(nfe, relida)).isEmpty();
        NfeDi di = relida.getItens().get(0).getDis().get(0);
        assertThat(di.getViaTransporte()).isEqualTo(ViaTransporteInternacional.AEREA);
        assertThat(di.getFormaImportacao()).isEqualTo(FormaImportacao.CONTA_PROPRIA);
        assertThat(di.getAdicoes()).hasSize(1);
        assertThat(di.getAdicoes().get(0).getCodFabricante()).isEqualTo("KJELLBERG");
    }

    // quebra de linha digitada em caixa de texto: TString não aceita (cStat=225 em
    // homologação-SP, 2026-10-09) — vira espaço
    @Test
    void quebraDeLinhaNoTextoViraEspacoEPassaNoXsd() throws Exception {
        Nfe nfe = notaDeImportacao();
        nfe.setInfCpl("DI 2608986187. PIS R$ 1.533,77;\r\nCOFINS R$ 7.486,27;\n\tTaxa Siscomex R$ 154,23.\r\n");
        nfe.getItens().get(0).setInfoAdicionalProduto("linha 1\nlinha 2");

        byte[] xml = paraBytes(serializer.serializar(nfe));
        validarContraXsd(xml);

        String texto = new String(xml, java.nio.charset.StandardCharsets.UTF_8);
        assertThat(texto).contains("<infCpl>DI 2608986187. PIS R$ 1.533,77; COFINS R$ 7.486,27; Taxa Siscomex R$ 154,23.</infCpl>",
                "<infAdProd>linha 1 linha 2</infAdProd>");
    }

    @Test
    void recusaDadosQueOLeiauteNaoAceitaOuANfeNaoModela() {
        Nfe semChave = notaDeImportacao();
        semChave.setChave(null);
        assertThatThrownBy(() -> serializer.serializar(semChave)).hasMessageContaining("chave");

        Nfe semAdicao = notaDeImportacao();
        semAdicao.getItens().get(0).getDis().get(0).setAdicoes(new ArrayList<>());
        assertThatThrownBy(() -> serializer.serializar(semAdicao)).hasMessageContaining("adição");

        Nfe monofasico = notaDeImportacao();
        monofasico.getItens().get(0).setCsosnIcms(null);
        monofasico.getItens().get(0).setCstIcms("02");
        assertThatThrownBy(() -> serializer.serializar(monofasico)).hasMessageContaining("monofásico");

        Nfe semCfop = notaDeImportacao();
        semCfop.getItens().get(0).setCfop(null);
        assertThatThrownBy(() -> serializer.serializar(semCfop)).hasMessageContaining("CFOP");
    }

    // ---- montagem da nota de importação (valores do espelho do despachante) ----

    private Nfe notaDeImportacao() {
        Nfe nfe = dataManager.create(Nfe.class);
        nfe.setDigitada(true);
        nfe.setChave(CHAVE_IMPORTACAO);
        nfe.setCodUf(35);
        nfe.setCodNf(1);
        nfe.setNatOp("Compra para o ativo imobilizado - importacao");
        nfe.setSerie(1);
        nfe.setNumeroNf(1234);
        nfe.setDhEmi(OffsetDateTime.of(2026, 10, 9, 14, 0, 0, 0, ZoneOffset.of("-03:00")));
        nfe.setDhSaiEnt(OffsetDateTime.of(2026, 10, 9, 14, 0, 0, 0, ZoneOffset.of("-03:00")));
        nfe.setTpNf(0);
        nfe.setIdDest(3);
        nfe.setCodMunFg(3509502);
        nfe.setTpImp(1);
        nfe.setTpEmis(1);
        nfe.setCodDv(3);
        nfe.setTpAmb(2);
        nfe.setFinNfe(1);
        nfe.setIndFinal(1);
        nfe.setIndPres(0);
        nfe.setProcEmi(0);
        nfe.setVerProc("axctg3");

        nfe.setEmitCnpj("12345678000199");
        nfe.setEmitXNome("Cliente do Simples Ltda");
        nfe.setEmitXLgr("Rua das Industrias");
        nfe.setEmitNro("100");
        nfe.setEmitXBairro("Centro");
        nfe.setEmitCMun(3509502);
        nfe.setEmitXMun("Campinas");
        nfe.setEmitUf("SP");
        nfe.setEmitCep("13000000");
        nfe.setEmitIe("111222333444");
        nfe.setEmitCrt(1);

        nfe.setDestXNome("Kjellberg Finsterwalde Plasma und Maschinen GmbH");
        nfe.setDestXLgr("Oscar-Kjellberg-Strasse");
        nfe.setDestNro("20");
        nfe.setDestXBairro("Finsterwalde");
        nfe.setDestCMun(9999999);
        nfe.setDestXMun("EXTERIOR");
        nfe.setDestUf("EX");
        nfe.setDestCPais(230);
        nfe.setDestXPais("ALEMANHA");
        nfe.setDestIndIe(9);

        NfeItem item = dataManager.create(NfeItem.class);
        item.setNfe(nfe);
        item.setItem(1);
        item.setCodProd("PGV3-440");
        item.setCodEan("SEM GTIN");
        item.setCodEanTrib("SEM GTIN");
        item.setDescProd("Controlador de gas plasma PGV3-440");
        item.setNcm("84669360");
        item.setCfop(3551);
        item.setUnCom("UN");
        item.setQuantCom(BigDecimal.ONE);
        item.setValorUnCom(new BigDecimal("73036.78"));
        item.setValorProd(new BigDecimal("73036.78"));
        item.setUnTrib("UN");
        item.setQuantTrib(BigDecimal.ONE);
        item.setValorUnTrib(new BigDecimal("73036.78"));
        item.setValorOutro(new BigDecimal("18516.40"));
        item.setIndTot(1);
        item.setOrigemIcms(1);
        item.setCsosnIcms("900");
        item.setModBcIcms(3);
        item.setBaseIcms(new BigDecimal("51900.71"));
        item.setPercReducaoBcIcms(new BigDecimal("51.1111"));
        item.setAliqIcms(new BigDecimal("18.0000"));
        item.setValorIcms(new BigDecimal("9342.13"));
        item.setCodEnqIpi("999");
        item.setCstIpi("00");
        item.setBaseIi(new BigDecimal("73036.78"));
        item.setValorDespAdu(new BigDecimal("154.23"));
        item.setValorIi(new BigDecimal("14607.36"));
        item.setCstPis("98");
        item.setBasePis(new BigDecimal("73036.78"));
        item.setAliqPis(new BigDecimal("2.1000"));
        item.setValorPis(new BigDecimal("1533.77"));
        item.setCstCofins("98");
        item.setBaseCofins(new BigDecimal("73036.78"));
        item.setAliqCofins(new BigDecimal("10.2500"));
        item.setValorCofins(new BigDecimal("7486.27"));

        NfeDi di = dataManager.create(NfeDi.class);
        di.setNfeItem(item);
        di.setNumeroDi("2608986187");
        di.setDataDi(LocalDate.of(2026, 10, 6));
        di.setLocalDesembaraco("Aeroporto Internacional de Viracopos");
        di.setUfDesembaraco("SP");
        di.setDataDesembaraco(LocalDate.of(2026, 10, 8));
        di.setViaTransporte(ViaTransporteInternacional.AEREA);
        di.setFormaImportacao(FormaImportacao.CONTA_PROPRIA);
        di.setCodExportador("KJELLBERG");
        NfeDiAdicao adicao = dataManager.create(NfeDiAdicao.class);
        adicao.setNfeDi(di);
        adicao.setNumeroAdicao(1);
        adicao.setSequencial(1);
        adicao.setCodFabricante("KJELLBERG");
        di.setAdicoes(new ArrayList<>(List.of(adicao)));
        item.setDis(new ArrayList<>(List.of(di)));
        nfe.setItens(new ArrayList<>(List.of(item)));

        nfe.setValorBc(new BigDecimal("51900.71"));
        nfe.setValorIcms(new BigDecimal("9342.13"));
        nfe.setValorProd(new BigDecimal("73036.78"));
        nfe.setValorIi(new BigDecimal("14607.36"));
        nfe.setValorPis(new BigDecimal("1533.77"));
        nfe.setValorCofins(new BigDecimal("7486.27"));
        nfe.setValorOutro(new BigDecimal("18516.40"));
        nfe.setValorNf(new BigDecimal("106160.54"));

        nfe.setModFrete(1);
        nfe.setTranspCnpj("28090718000135");
        nfe.setTranspXNome("IMPERIAL LOG - TRANSPORTES ADUANEIROS LTDA");
        nfe.setTranspIe("795858797113");
        nfe.setTranspXEnder("AVENIDA CAMPOS SALLES");
        nfe.setTranspXMun("CAMPINAS");
        nfe.setTranspUf("SP");
        NfeVolume volume = dataManager.create(NfeVolume.class);
        volume.setNfe(nfe);
        volume.setQuantVol(1);
        volume.setEspecie("OUTROS");
        volume.setPesoLiquido(new BigDecimal("38.000"));
        volume.setPesoBruto(new BigDecimal("73.000"));
        nfe.setVolumes(new ArrayList<>(List.of(volume)));
        nfe.setDuplicatas(new ArrayList<>());
        NfePagamento semPagamento = dataManager.create(NfePagamento.class);
        semPagamento.setNfe(nfe);
        semPagamento.setIndPag(0);
        semPagamento.setTipoPag("90");
        semPagamento.setValorPag(BigDecimal.ZERO);
        nfe.setPagamentos(new ArrayList<>(List.of(semPagamento)));
        nfe.setInfCpl("DI 2608986187. II 14.607,36; PIS 1.533,77; COFINS 7.486,27; Taxa Siscomex 154,23.");
        return nfe;
    }

    // ---- comparação campo a campo ----

    private List<String> diferencas(Nfe a, Nfe b) throws Exception {
        List<String> diferencas = new ArrayList<>();
        comparar("Nfe", a, b, diferencas);
        compararListas("item", a.getItens(), b.getItens(), diferencas);
        for (int i = 0; i < Math.min(tamanho(a.getItens()), tamanho(b.getItens())); i++) {
            NfeItem itemA = a.getItens().get(i);
            NfeItem itemB = b.getItens().get(i);
            compararListas("item" + i + ".DI", itemA.getDis(), itemB.getDis(), diferencas);
            for (int d = 0; d < Math.min(tamanho(itemA.getDis()), tamanho(itemB.getDis())); d++) {
                compararListas("item" + i + ".DI" + d + ".adi", itemA.getDis().get(d).getAdicoes(),
                        itemB.getDis().get(d).getAdicoes(), diferencas);
            }
        }
        compararListas("volume", a.getVolumes(), b.getVolumes(), diferencas);
        compararListas("duplicata", a.getDuplicatas(), b.getDuplicatas(), diferencas);
        compararListas("pagamento", a.getPagamentos(), b.getPagamentos(), diferencas);
        return diferencas;
    }

    private <T> void compararListas(String rotulo, List<T> a, List<T> b, List<String> diferencas) throws Exception {
        if (tamanho(a) != tamanho(b)) {
            diferencas.add(rotulo + ": " + tamanho(a) + " x " + tamanho(b) + " elementos");
            return;
        }
        for (int i = 0; i < tamanho(a); i++) {
            comparar(rotulo + i, a.get(i), b.get(i), diferencas);
        }
    }

    private static int tamanho(List<?> lista) {
        return lista == null ? 0 : lista.size();
    }

    // só atributos simples (texto, número, data, enum) — referências e coleções são
    // comparadas à parte, elemento a elemento
    private void comparar(String rotulo, Object a, Object b, List<String> diferencas) throws Exception {
        for (Method getter : a.getClass().getMethods()) {
            String nome = getter.getName();
            if (!nome.startsWith("get") || getter.getParameterCount() != 0 || getter.getDeclaringClass() == Object.class) {
                continue;
            }
            Class<?> tipo = getter.getReturnType();
            boolean simples = tipo == String.class || tipo == Integer.class || tipo == BigDecimal.class
                    || tipo == LocalDate.class || tipo == OffsetDateTime.class || tipo == Boolean.class || tipo.isEnum();
            String propriedade = Character.toLowerCase(nome.charAt(3)) + nome.substring(4);
            if (!simples || FORA_DO_XML.contains(propriedade)) {
                continue;
            }
            Object va = getter.invoke(a);
            Object vb = getter.invoke(b);
            boolean iguais = va instanceof BigDecimal da && vb instanceof BigDecimal db
                    ? da.compareTo(db) == 0
                    : Objects.equals(va, vb);
            if (!iguais) {
                diferencas.add(rotulo + "." + propriedade + ": " + va + " x " + vb);
            }
        }
    }

    // ---- XML ----

    private static byte[] ler(String recurso) throws Exception {
        try (InputStream is = new ClassPathResource(recurso).getInputStream()) {
            return is.readAllBytes();
        }
    }

    private static byte[] paraBytes(Document doc) throws Exception {
        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        transformer.transform(new DOMSource(doc), new StreamResult(out));
        return out.toByteArray();
    }

    private static void validarContraXsd(byte[] xml) throws Exception {
        SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        Validator validator = factory.newSchema(new ClassPathResource("nfe-schemas/nfe_v4.00.xsd").getURL())
                .newValidator();
        validator.validate(new javax.xml.transform.stream.StreamSource(new java.io.ByteArrayInputStream(xml)));
    }
}
