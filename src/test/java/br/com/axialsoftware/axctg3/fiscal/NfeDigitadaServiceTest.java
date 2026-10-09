package br.com.axialsoftware.axctg3.fiscal;

import br.com.axialsoftware.axctg3.entity.User;
import br.com.axialsoftware.axctg3.entity.cadastros.Empresa;
import br.com.axialsoftware.axctg3.entity.enums.AmbienteNfe;
import br.com.axialsoftware.axctg3.entity.enums.CodRegimeTributario;
import br.com.axialsoftware.axctg3.entity.enums.FormaImportacao;
import br.com.axialsoftware.axctg3.entity.enums.ViaTransporteInternacional;
import br.com.axialsoftware.axctg3.entity.fiscal.Nfe;
import br.com.axialsoftware.axctg3.entity.fiscal.NfeDi;
import br.com.axialsoftware.axctg3.entity.fiscal.NfeDiAdicao;
import br.com.axialsoftware.axctg3.entity.fiscal.NfeItem;
import br.com.axialsoftware.axctg3.service.fiscal.NfeDigitadaEmissaoService;
import br.com.axialsoftware.axctg3.service.fiscal.NfeDigitadaService;
import br.com.axialsoftware.axctg3.test_support.AuthenticatedAsAdmin;
import io.jmix.core.DataManager;
import io.jmix.core.SaveContext;
import io.jmix.core.security.CurrentAuthentication;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link NfeDigitadaService} e as recusas de {@link NfeDigitadaEmissaoService} que acontecem
 * antes de falar com a SEFAZ. O envio de verdade (assinatura + webservice) só é exercitado em
 * homologação. Os valores do item são os do espelho do despachante usado como gabarito
 * (ver {@code NfeXmlSerializerTest}).
 */
@SpringBootTest
@ExtendWith(AuthenticatedAsAdmin.class)
@ActiveProfiles("test")
class NfeDigitadaServiceTest {

    private static final int COD_EMPRESA = 9611;

    @Autowired
    private DataManager dataManager;
    @Autowired
    private CurrentAuthentication currentAuthentication;
    @Autowired
    private NfeDigitadaService nfeDigitadaService;
    @Autowired
    private NfeDigitadaEmissaoService nfeDigitadaEmissaoService;

    @BeforeEach
    void setUp() {
        User admin = (User) currentAuthentication.getUser();
        admin.setCodEmpresa(COD_EMPRESA);
        limparDados();
        Empresa empresa = dataManager.create(Empresa.class);
        empresa.setCodigo(COD_EMPRESA);
        empresa.setNome("Cliente do Simples Ltda");
        empresa.setApelido("Cliente");
        empresa.setCnpj("12345678000199");
        empresa.setInscEst("111222333444");
        empresa.setCrt(CodRegimeTributario.SIMPLES_NACIONAL);
        empresa.setAmbienteNfe(AmbienteNfe.HOMOLOGACAO);
        empresa.setSerieNfe("1");
        dataManager.save(empresa);
    }

    @AfterEach
    void tearDown() {
        limparDados();
    }

    @Test
    void rascunhoNovo_temEmitenteDaEmpresaESemChave() {
        Nfe rascunho = nfeDigitadaService.criarRascunho();

        assertThat(rascunho.getDigitada()).isTrue();
        assertThat(rascunho.getChave()).isNull();
        assertThat(rascunho.getNumeroNf()).isNull();
        assertThat(rascunho.getCodEmpresa()).isEqualTo(COD_EMPRESA);
        assertThat(rascunho.getEmitCnpj()).isEqualTo("12345678000199");
        assertThat(rascunho.getEmitIe()).isEqualTo("111222333444");
        assertThat(rascunho.getEmitCrt()).isEqualTo(1);
        assertThat(rascunho.getTpAmb()).isEqualTo(2);
        assertThat(rascunho.getSerie()).isEqualTo(1);
        assertThat(rascunho.getMod()).isEqualTo(55);
    }

    @Test
    void recalcularTotais_somaOsItensComOsNumerosDoEspelho() {
        Nfe nfe = rascunhoDeImportacao();

        nfeDigitadaService.recalcularTotais(nfe);

        assertThat(nfe.getValorProd()).isEqualByComparingTo("73036.78");
        assertThat(nfe.getValorIi()).isEqualByComparingTo("14607.36");
        assertThat(nfe.getValorOutro()).isEqualByComparingTo("18516.40");
        assertThat(nfe.getValorBc()).isEqualByComparingTo("51900.71");
        assertThat(nfe.getValorIcms()).isEqualByComparingTo("9342.13");
        assertThat(nfe.getValorPis()).isEqualByComparingTo("1533.77");
        assertThat(nfe.getValorCofins()).isEqualByComparingTo("7486.27");
        assertThat(nfe.getValorNf()).isEqualByComparingTo("106160.54");
        // Simples sem IBS/CBS no item: sem vItem nem vNFTot
        assertThat(nfe.getItens().get(0).getValorItem()).isEqualByComparingTo("0");
        assertThat(nfe.getValorNfTot()).isEqualByComparingTo("0");
        assertThat(nfeDigitadaService.divergenciasDeTotais(nfe)).isEmpty();
    }

    @Test
    void divergenciasDeTotais_apontaTotalDesatualizado() {
        Nfe nfe = rascunhoDeImportacao();
        nfeDigitadaService.recalcularTotais(nfe);
        nfe.getItens().get(0).setValorIi(new BigDecimal("14000.00"));

        List<String> divergencias = nfeDigitadaService.divergenciasDeTotais(nfe);

        assertThat(divergencias).anyMatch(d -> d.startsWith("vII"));
        assertThat(divergencias).anyMatch(d -> d.startsWith("vNF"));
        // a conferência não mexe na numeração dos itens
        assertThat(nfe.getItens().get(0).getItem()).isEqualTo(1);
    }

    @Test
    void copiarComoRascunho_levaItensEDiSemIdentificacaoDaEmissao() {
        Nfe origem = rascunhoDeImportacao();
        nfeDigitadaService.recalcularTotais(origem);
        // chave nova a cada execução: o índice único de CHAVE no HSQLDB de teste também vale
        // pras linhas soft-deletadas de rodadas anteriores
        origem.setChave("3526101234567800019955001" + String.format("%019d", System.currentTimeMillis()));
        origem.setNumeroNf(1234);
        origem.setProtNProt("135260000000001");
        origem.setProtCStat(100);
        origem.setDigitada(false);
        salvarComFilhos(origem);

        Nfe copia = nfeDigitadaService.carregarCompleta(nfeDigitadaService.copiarComoRascunho(origem.getId()).getId());

        assertThat(copia.getId()).isNotEqualTo(origem.getId());
        assertThat(copia.getDigitada()).isTrue();
        assertThat(copia.getChave()).isNull();
        assertThat(copia.getNumeroNf()).isNull();
        assertThat(copia.getProtNProt()).isNull();
        assertThat(copia.getProtCStat()).isNull();
        assertThat(copia.getDestXPais()).isEqualTo("ALEMANHA");
        assertThat(copia.getValorNf()).isEqualByComparingTo("106160.54");
        assertThat(copia.getItens()).hasSize(1);
        NfeItem item = copia.getItens().get(0);
        assertThat(item.getCsosnIcms()).isEqualTo("900");
        assertThat(item.getValorIi()).isEqualByComparingTo("14607.36");
        assertThat(item.getDis()).hasSize(1);
        assertThat(item.getDis().get(0).getNumeroDi()).isEqualTo("2608986187");
        assertThat(item.getDis().get(0).getViaTransporte()).isEqualTo(ViaTransporteInternacional.AEREA);
        assertThat(item.getDis().get(0).getAdicoes()).hasSize(1);
        assertThat(item.getDis().get(0).getAdicoes().get(0).getCodFabricante()).isEqualTo("KJELLBERG");
    }

    @Test
    void copiarComoRascunho_naoTrazONomeDeHomologacao() {
        Nfe origem = rascunhoDeImportacao();
        origem.setDestXNome(NfeDigitadaService.HOMOLOGACAO_X_NOME);
        salvarComFilhos(origem);

        Nfe copia = nfeDigitadaService.copiarComoRascunho(origem.getId());

        assertThat(copia.getDestXNome()).isNull();
        assertThat(copia.getDestXLgr()).isEqualTo("Oscar-Kjellberg-Strasse");
    }

    @Test
    void transmitir_emProducaoRecusaDestinatarioDeHomologacao() {
        Empresa empresa = dataManager.load(Empresa.class)
                .query("select e from Empresa e where e.codigo = :codigo")
                .parameter("codigo", COD_EMPRESA)
                .one();
        empresa.setAmbienteNfe(AmbienteNfe.PRODUCAO);
        dataManager.save(empresa);
        Nfe nfe = rascunhoDeImportacao();
        nfeDigitadaService.recalcularTotais(nfe);
        nfe.setDestXNome(NfeDigitadaService.HOMOLOGACAO_X_NOME);
        salvarComFilhos(nfe);

        NfeDigitadaEmissaoService.Resultado resultado = nfeDigitadaEmissaoService.transmitir(nfe.getId());

        assertThat(resultado.sucesso()).isFalse();
        assertThat(resultado.motivo()).contains("nome do destinatário");
        assertThat(dataManager.load(Nfe.class).id(nfe.getId()).one().getNumeroNf()).isNull();
    }

    @Test
    void transmitir_recusaNotaQueNaoEDigitada() {
        Nfe nfe = rascunhoDeImportacao();
        nfe.setDigitada(false);
        salvarComFilhos(nfe);

        NfeDigitadaEmissaoService.Resultado resultado = nfeDigitadaEmissaoService.transmitir(nfe.getId());

        assertThat(resultado.sucesso()).isFalse();
        assertThat(resultado.motivo()).contains("não é digitada");
    }

    @Test
    void transmitir_recusaEmpresaSemCertificadoSemGastarNumero() {
        Nfe nfe = rascunhoDeImportacao();
        nfeDigitadaService.recalcularTotais(nfe);
        salvarComFilhos(nfe);

        NfeDigitadaEmissaoService.Resultado resultado = nfeDigitadaEmissaoService.transmitir(nfe.getId());

        assertThat(resultado.sucesso()).isFalse();
        assertThat(resultado.motivo()).contains("certificado");
        assertThat(dataManager.load(Nfe.class).id(nfe.getId()).one().getNumeroNf()).isNull();
    }

    // ---- dados ----

    private Nfe rascunhoDeImportacao() {
        Nfe nfe = nfeDigitadaService.criarRascunho();
        nfe.setNatOp("Compra para o ativo imobilizado - importacao");
        nfe.setTpNf(0);
        nfe.setIdDest(3);
        nfe.setDestXNome("Kjellberg Finsterwalde Plasma und Maschinen GmbH");
        nfe.setDestXLgr("Oscar-Kjellberg-Strasse");
        nfe.setDestNro("20");
        nfe.setDestXBairro("Finsterwalde");
        nfe.setDestCMun(9999999);
        nfe.setDestXMun("EXTERIOR");
        nfe.setDestUf("EX");
        nfe.setDestCPais(230);
        nfe.setDestXPais("ALEMANHA");

        NfeItem item = dataManager.create(NfeItem.class);
        item.setNfe(nfe);
        item.setItem(7);
        item.setCodProd("PGV3-440");
        item.setDescProd("Controlador de gas plasma PGV3-440");
        item.setNcm("84669360");
        item.setCfop(3551);
        item.setUnCom("UN");
        item.setQuantCom(BigDecimal.ONE);
        item.setValorUnCom(new BigDecimal("73036.78"));
        item.setValorProd(new BigDecimal("73036.78"));
        item.setValorOutro(new BigDecimal("18516.40"));
        item.setIndTot(1);
        item.setOrigemIcms(1);
        item.setCsosnIcms("900");
        item.setBaseIcms(new BigDecimal("51900.71"));
        item.setPercReducaoBcIcms(new BigDecimal("51.1111"));
        item.setAliqIcms(new BigDecimal("18"));
        item.setValorIcms(new BigDecimal("9342.13"));
        item.setBaseIi(new BigDecimal("73036.78"));
        item.setValorDespAdu(new BigDecimal("154.23"));
        item.setValorIi(new BigDecimal("14607.36"));
        item.setCstPis("98");
        item.setValorPis(new BigDecimal("1533.77"));
        item.setCstCofins("98");
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
        return nfe;
    }

    private void salvarComFilhos(Nfe nfe) {
        SaveContext saveContext = new SaveContext().saving(nfe);
        for (NfeItem item : nfe.getItens()) {
            saveContext.saving(item);
            for (NfeDi di : item.getDis()) {
                saveContext.saving(di);
                di.getAdicoes().forEach(saveContext::saving);
            }
        }
        dataManager.save(saveContext);
    }

    private void limparDados() {
        dataManager.load(Nfe.class)
                .query("select e from Nfe e where e.codEmpresa = :codEmpresa")
                .parameter("codEmpresa", COD_EMPRESA)
                .list()
                .forEach(dataManager::remove);
        dataManager.load(Empresa.class)
                .query("select e from Empresa e where e.codigo = :codEmpresa")
                .parameter("codEmpresa", COD_EMPRESA)
                .list()
                .forEach(dataManager::remove);
    }
}
