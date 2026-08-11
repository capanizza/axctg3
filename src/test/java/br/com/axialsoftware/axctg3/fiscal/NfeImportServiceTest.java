package br.com.axialsoftware.axctg3.fiscal;

import br.com.axialsoftware.axctg3.entity.User;
import br.com.axialsoftware.axctg3.entity.fiscal.Nfe;
import br.com.axialsoftware.axctg3.entity.fiscal.NfeDuplicata;
import br.com.axialsoftware.axctg3.entity.fiscal.NfeItem;
import br.com.axialsoftware.axctg3.entity.fiscal.NfePagamento;
import br.com.axialsoftware.axctg3.entity.fiscal.NfeVolume;
import br.com.axialsoftware.axctg3.service.fiscal.NfeImportService;
import br.com.axialsoftware.axctg3.test_support.AuthenticatedAsAdmin;
import io.jmix.core.DataManager;
import io.jmix.core.security.CurrentAuthentication;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cobre {@link NfeImportService} + {@link br.com.axialsoftware.axctg3.service.fiscal.NfeXmlParser}
 * com um XML de amostra que exercita todos os grupos mapeados (ide/emit/dest/det/total/transp/
 * cobr/pag/infAdic/protNFe, incluindo o núcleo IBS/CBS/IS da Reforma Tributária) — o que o botão
 * "Importar XML" da {@code Nfe.list} exercita via {@code NfeListView.ImportarXmlTask}.
 */
@SpringBootTest
@ExtendWith(AuthenticatedAsAdmin.class)
@ActiveProfiles("test")
class NfeImportServiceTest {

    private static final int COD_EMPRESA = 9104;
    private static final String CHAVE = "35240512345678000199550010000000011123456789";

    @Autowired
    private DataManager dataManager;

    @Autowired
    private CurrentAuthentication currentAuthentication;

    @Autowired
    private NfeImportService nfeImportService;

    @BeforeEach
    void setUp() {
        User admin = (User) currentAuthentication.getUser();
        admin.setCodEmpresa(COD_EMPRESA);
        limparNfeDeTeste();
    }

    @AfterEach
    void tearDown() {
        limparNfeDeTeste();
    }

    private void limparNfeDeTeste() {
        dataManager.load(Nfe.class)
                .query("select e from Nfe e where e.codEmpresa = :codEmpresa")
                .parameter("codEmpresa", COD_EMPRESA)
                .list()
                .forEach(dataManager::remove);
    }

    private byte[] xmlAmostra() throws IOException {
        try (InputStream is = new ClassPathResource(
                "br/com/axialsoftware/axctg3/fiscal/nfe_import_sample.xml").getInputStream()) {
            return is.readAllBytes();
        }
    }

    @Test
    void importaNfeComItensEGruposFilhos() throws IOException {
        byte[] xml = xmlAmostra();

        NfeImportService.ImportResult resultado = nfeImportService.importar("nfe_import_sample.xml", xml);
        assertThat(resultado.resultado()).isEqualTo(NfeImportService.Resultado.CRIADA);

        Nfe nfe = dataManager.load(Nfe.class)
                .query("select e from Nfe e where e.chave = :chave")
                .parameter("chave", CHAVE)
                .one();

        // codEmpresa vem do NfeEventListener (campo não existe no XML)
        assertThat(nfe.getCodEmpresa()).isEqualTo(COD_EMPRESA);

        // ide
        assertThat(nfe.getCodUf()).isEqualTo(35);
        assertThat(nfe.getNatOp()).isEqualTo("Venda de mercadoria");
        assertThat(nfe.getMod()).isEqualTo(55);
        assertThat(nfe.getSerie()).isEqualTo(1);
        assertThat(nfe.getNumeroNf()).isEqualTo(1);
        assertThat(nfe.getDhEmi()).isEqualTo(OffsetDateTime.parse("2026-08-05T10:15:30-03:00"));
        assertThat(nfe.getTpAmb()).isEqualTo(2);

        // emit / dest (snapshot)
        assertThat(nfe.getEmitCnpj()).isEqualTo("12345678000199");
        assertThat(nfe.getEmitXNome()).isEqualTo("Empresa Emitente Teste Ltda");
        assertThat(nfe.getEmitUf()).isEqualTo("SP");
        assertThat(nfe.getDestCnpj()).isEqualTo("98765432000188");
        assertThat(nfe.getDestXNome()).isEqualTo("Cliente Destinatario Teste S.A.");
        assertThat(nfe.getDestEmail()).isEqualTo("cliente@teste.com.br");

        // total / ICMSTot
        assertThat(nfe.getValorProd()).isEqualByComparingTo(new BigDecimal("500.00"));
        assertThat(nfe.getValorIcms()).isEqualByComparingTo(new BigDecimal("90.00"));
        assertThat(nfe.getValorIpi()).isEqualByComparingTo(new BigDecimal("25.00"));
        assertThat(nfe.getValorNf()).isEqualByComparingTo(new BigDecimal("533.00"));

        // total / IBSCBSTot+ISTot — Reforma Tributária
        assertThat(nfe.getValorBcIbsCbs()).isEqualByComparingTo(new BigDecimal("500.00"));
        assertThat(nfe.getValorIbsUf()).isEqualByComparingTo(new BigDecimal("45.00"));
        assertThat(nfe.getValorIbsMun()).isEqualByComparingTo(new BigDecimal("5.00"));
        assertThat(nfe.getValorIbs()).isEqualByComparingTo(new BigDecimal("50.00"));
        assertThat(nfe.getValorCbs()).isEqualByComparingTo(new BigDecimal("4.50"));
        assertThat(nfe.getValorIs()).isEqualByComparingTo(new BigDecimal("5.00"));
        assertThat(nfe.getValorNfTot()).isEqualByComparingTo(new BigDecimal("587.50"));

        // transp
        assertThat(nfe.getTranspCnpj()).isEqualTo("11222333000144");
        assertThat(nfe.getVeicPlaca()).isEqualTo("ABC1D23");

        // cobr / fat
        assertThat(nfe.getNumFat()).isEqualTo("1");
        assertThat(nfe.getValorLiqFat()).isEqualByComparingTo(new BigDecimal("533.00"));

        // pag
        assertThat(nfe.getValorTroco()).isEqualByComparingTo(BigDecimal.ZERO);

        // infAdic
        assertThat(nfe.getInfCpl()).isEqualTo("Nota emitida para fins de teste automatizado");

        // protNFe — chave reconciliada com infProt/chNFe (idêntica aqui, mas é o caminho preferido)
        assertThat(nfe.getProtCStat()).isEqualTo(100);
        assertThat(nfe.getProtNProt()).isEqualTo("135260000123456");

        List<NfeItem> itens = dataManager.load(NfeItem.class)
                .query("select e from NfeItem e where e.nfe.id = :nfeId")
                .parameter("nfeId", nfe.getId())
                .list();
        assertThat(itens).hasSize(1);
        NfeItem item = itens.get(0);
        assertThat(item.getItem()).isEqualTo(1);
        assertThat(item.getCodProd()).isEqualTo("PROD001");
        assertThat(item.getNcm()).isEqualTo("84713012");
        assertThat(item.getCfop()).isEqualTo(5102);
        assertThat(item.getQuantCom()).isEqualByComparingTo(new BigDecimal("10.0000"));
        assertThat(item.getValorProd()).isEqualByComparingTo(new BigDecimal("500.00"));
        // ICMS00 -> CST, não CSOSN
        assertThat(item.getCstIcms()).isEqualTo("00");
        assertThat(item.getCsosnIcms()).isNull();
        assertThat(item.getBaseIcms()).isEqualByComparingTo(new BigDecimal("500.00"));
        assertThat(item.getValorIcms()).isEqualByComparingTo(new BigDecimal("90.00"));
        // grupo ST ausente no ICMS00 da amostra -> ZERO, não null (campo monetário default)
        assertThat(item.getBaseIcmsSt()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(item.getValorIcmsSt()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(item.getModBcIcmsSt()).isNull();
        // IPITrib
        assertThat(item.getCstIpi()).isEqualTo("50");
        assertThat(item.getValorIpi()).isEqualByComparingTo(new BigDecimal("25.00"));
        // PIS/COFINS
        assertThat(item.getCstPis()).isEqualTo("01");
        assertThat(item.getValorPis()).isEqualByComparingTo(new BigDecimal("8.25"));
        assertThat(item.getCstCofins()).isEqualTo("01");
        assertThat(item.getValorCofins()).isEqualByComparingTo(new BigDecimal("38.00"));
        // IS
        assertThat(item.getCstIs()).isEqualTo("000");
        assertThat(item.getValorIs()).isEqualByComparingTo(new BigDecimal("5.00"));
        // IBSCBS — núcleo + os 3 sub-tributos, inclusive gDif/gDevTrib/gRed sem colisão entre eles
        assertThat(item.getCstIbsCbs()).isEqualTo("000");
        assertThat(item.getCodClassTrib()).isEqualTo("000001");
        assertThat(item.getBaseIbsCbs()).isEqualByComparingTo(new BigDecimal("500.00"));
        assertThat(item.getAliqIbsUf()).isEqualByComparingTo(new BigDecimal("9.0000"));
        assertThat(item.getAliqEfetIbsUf()).isEqualByComparingTo(new BigDecimal("9.0000"));
        assertThat(item.getValorIbsUf()).isEqualByComparingTo(new BigDecimal("45.00"));
        assertThat(item.getAliqIbsMun()).isEqualByComparingTo(new BigDecimal("1.0000"));
        assertThat(item.getAliqEfetIbsMun()).isEqualByComparingTo(new BigDecimal("1.0000"));
        assertThat(item.getValorIbsMun()).isEqualByComparingTo(new BigDecimal("5.00"));
        assertThat(item.getValorIbs()).isEqualByComparingTo(new BigDecimal("50.00"));
        assertThat(item.getAliqCbs()).isEqualByComparingTo(new BigDecimal("0.9000"));
        assertThat(item.getValorCbs()).isEqualByComparingTo(new BigDecimal("4.50"));
        assertThat(item.getInfoAdicionalProduto()).isEqualTo("Lote 2026A");

        List<NfeVolume> volumes = dataManager.load(NfeVolume.class)
                .query("select e from NfeVolume e where e.nfe.id = :nfeId")
                .parameter("nfeId", nfe.getId())
                .list();
        assertThat(volumes).hasSize(1);
        assertThat(volumes.get(0).getQuantVol()).isEqualTo(2);
        assertThat(volumes.get(0).getPesoBruto()).isEqualByComparingTo(new BigDecimal("17.000"));

        List<NfeDuplicata> duplicatas = dataManager.load(NfeDuplicata.class)
                .query("select e from NfeDuplicata e where e.nfe.id = :nfeId")
                .parameter("nfeId", nfe.getId())
                .list();
        assertThat(duplicatas).hasSize(1);
        assertThat(duplicatas.get(0).getNumDup()).isEqualTo("001");
        assertThat(duplicatas.get(0).getDataVenc()).isEqualTo(LocalDate.of(2026, 9, 5));
        assertThat(duplicatas.get(0).getValorDup()).isEqualByComparingTo(new BigDecimal("533.00"));

        List<NfePagamento> pagamentos = dataManager.load(NfePagamento.class)
                .query("select e from NfePagamento e where e.nfe.id = :nfeId")
                .parameter("nfeId", nfe.getId())
                .list();
        assertThat(pagamentos).hasSize(1);
        assertThat(pagamentos.get(0).getTipoPag()).isEqualTo("03");
        assertThat(pagamentos.get(0).getValorPag()).isEqualByComparingTo(new BigDecimal("533.00"));
        assertThat(pagamentos.get(0).getBandeiraCartao()).isEqualTo("01");
        assertThat(pagamentos.get(0).getNumeroAutorizacaoCartao()).isEqualTo("987654");
    }

    @Test
    void reimportarMesmaChaveEIgnoradoComoDuplicada() throws IOException {
        byte[] xml = xmlAmostra();

        NfeImportService.ImportResult primeira = nfeImportService.importar("nfe_import_sample.xml", xml);
        assertThat(primeira.resultado()).isEqualTo(NfeImportService.Resultado.CRIADA);

        NfeImportService.ImportResult segunda = nfeImportService.importar("nfe_import_sample.xml", xml);
        assertThat(segunda.resultado()).isEqualTo(NfeImportService.Resultado.DUPLICADA);

        long total = dataManager.load(Nfe.class)
                .query("select e from Nfe e where e.chave = :chave")
                .parameter("chave", CHAVE)
                .list()
                .size();
        assertThat(total).isEqualTo(1);
    }

    @Test
    void xmlSemInfNFeRetornaErroSemLancarExcecao() {
        byte[] xmlInvalido = "<algumaCoisa><outraTag/></algumaCoisa>".getBytes();

        NfeImportService.ImportResult resultado = nfeImportService.importar("invalido.xml", xmlInvalido);

        assertThat(resultado.resultado()).isEqualTo(NfeImportService.Resultado.ERRO);
        assertThat(resultado.mensagem()).contains("invalido.xml");
    }
}
