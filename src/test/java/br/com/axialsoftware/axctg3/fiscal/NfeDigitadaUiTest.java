package br.com.axialsoftware.axctg3.fiscal;

import br.com.axialsoftware.axctg3.Axctg3Application;
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
import br.com.axialsoftware.axctg3.service.fiscal.NfeDigitadaService;
import br.com.axialsoftware.axctg3.service.fiscal.NfeImportService;
import br.com.axialsoftware.axctg3.test_support.AdminUiTestAuthenticator;
import br.com.axialsoftware.axctg3.view.fiscal.nfe.NfeDetailView;
import br.com.axialsoftware.axctg3.view.fiscal.nfe.NfeListView;
import br.com.axialsoftware.axctg3.view.fiscal.nfedi.NfeDiDetailView;
import br.com.axialsoftware.axctg3.view.fiscal.nfeitem.NfeItemDetailView;
import com.vaadin.flow.component.HasValueAndElement;
import io.jmix.core.DataManager;
import io.jmix.core.SaveContext;
import io.jmix.core.security.CurrentAuthentication;
import io.jmix.flowui.ViewNavigators;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.kit.component.button.JmixButton;
import io.jmix.flowui.testassist.FlowuiTestAssistConfiguration;
import io.jmix.flowui.testassist.UiTest;
import io.jmix.flowui.testassist.UiTestUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Telas da NFe digitada: o rascunho abre editável em {@code Nfe.detail} (com identificação,
 * emitente e protocolo travados), uma NFe importada continua só leitura, e as telas do item
 * (aba Importação) e da DI renderizam com as DIs/adições do rascunho.
 */
@UiTest(authenticator = AdminUiTestAuthenticator.class)
@SpringBootTest(classes = {Axctg3Application.class, FlowuiTestAssistConfiguration.class})
@ActiveProfiles("test")
class NfeDigitadaUiTest {

    private static final int COD_EMPRESA = 9612;
    private static final String CHAVE_AMOSTRA = "35240512345678000199550010000000011123456789";

    @Autowired
    private DataManager dataManager;
    @Autowired
    private ViewNavigators viewNavigators;
    @Autowired
    private CurrentAuthentication currentAuthentication;
    @Autowired
    private NfeDigitadaService nfeDigitadaService;
    @Autowired
    private NfeImportService nfeImportService;

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
    void novaNfeDigitada_abreEditavelComCamposDoSistemaTravados() {
        viewNavigators.view(UiTestUtils.getCurrentView(), NfeListView.class).navigate();
        NfeListView lista = UiTestUtils.getCurrentView();
        JmixButton novaDigitada = UiTestUtils.getComponent(lista, "novaDigitadaButton");
        assertThat(novaDigitada.isVisible()).isTrue();

        lista.onNfesDataGridNovaDigitadaAction(null);

        NfeDetailView detalhe = UiTestUtils.getCurrentView();
        assertThat(detalhe.isReadOnly()).isFalse();
        assertThat(detalhe.getEditedEntity().getDigitada()).isTrue();
        assertThat(((JmixButton) UiTestUtils.getComponent(detalhe, "saveAndCloseButton")).isVisible()).isTrue();
        assertThat(((JmixButton) UiTestUtils.getComponent(detalhe, "recalcularTotaisButton")).isVisible()).isTrue();
        // emitente (aba não selecionada) e identificação da emissão: sempre do sistema
        assertThat(campo(detalhe, "emitCnpjField").isReadOnly()).isTrue();
        assertThat(campo(detalhe, "numeroNfField").isReadOnly()).isTrue();
        assertThat(campo(detalhe, "chaveField").isReadOnly()).isTrue();
        // destinatário e itens: digitados
        assertThat(campo(detalhe, "destXNomeField").isReadOnly()).isFalse();
        assertThat(campo(detalhe, "destXPaisField").isReadOnly()).isFalse();
        DataGrid<NfeItem> itens = UiTestUtils.getComponent(detalhe, "nfeItensDataGrid");
        assertThat(itens.getAction("create").isEnabled()).isTrue();
    }

    @Test
    void nfeImportada_continuaSoLeitura() throws Exception {
        byte[] xml;
        try (InputStream is = new ClassPathResource("br/com/axialsoftware/axctg3/fiscal/nfe_import_sample.xml").getInputStream()) {
            xml = is.readAllBytes();
        }
        nfeImportService.importar("nfe_import_sample.xml", xml);
        Nfe importada = dataManager.load(Nfe.class)
                .query("select e from Nfe e where e.chave = :chave")
                .parameter("chave", CHAVE_AMOSTRA)
                .one();

        viewNavigators.detailView(UiTestUtils.getCurrentView(), Nfe.class).editEntity(importada).navigate();

        NfeDetailView detalhe = UiTestUtils.getCurrentView();
        assertThat(detalhe.isReadOnly()).isTrue();
        assertThat(((JmixButton) UiTestUtils.getComponent(detalhe, "saveAndCloseButton")).isVisible()).isFalse();
        assertThat(((JmixButton) UiTestUtils.getComponent(detalhe, "recalcularTotaisButton")).isVisible()).isFalse();
        DataGrid<NfeItem> itens = UiTestUtils.getComponent(detalhe, "nfeItensDataGrid");
        assertThat(itens.getAction("create").isEnabled()).isFalse();
        assertThat(campo(detalhe, "destXNomeField").isReadOnly()).isTrue();
    }

    // envio caiu sem resposta: a SEFAZ pode ter autorizado o XML já enviado, então o rascunho
    // não pode mais ser alterado até "Transmitir" consultar e resolver
    @Test
    void rascunhoComTentativaPendente_abreTravado() {
        Nfe rascunho = nfeDigitadaService.criarRascunho();
        rascunho.setChaveTentativa("3526101234567800019955001" + String.format("%019d", System.currentTimeMillis()));
        rascunho = dataManager.save(rascunho);

        viewNavigators.detailView(UiTestUtils.getCurrentView(), Nfe.class).editEntity(rascunho).navigate();

        NfeDetailView detalhe = UiTestUtils.getCurrentView();
        assertThat(detalhe.isReadOnly()).isTrue();
        assertThat(((JmixButton) UiTestUtils.getComponent(detalhe, "saveAndCloseButton")).isVisible()).isFalse();
    }

    @Test
    void recalcularTotais_somaOItemNaTela() {
        Nfe rascunho = rascunhoComItemEDi();
        viewNavigators.detailView(UiTestUtils.getCurrentView(), Nfe.class).editEntity(rascunho).navigate();
        NfeDetailView detalhe = UiTestUtils.getCurrentView();

        detalhe.onRecalcularTotaisButtonClick(null);

        assertThat(detalhe.getEditedEntity().getValorNf()).isEqualByComparingTo("106160.54");
        assertThat(detalhe.getEditedEntity().getValorIi()).isEqualByComparingTo("14607.36");
    }

    @Test
    void textoImportacao_preencheInfCplVazio() {
        Nfe rascunho = rascunhoComItemEDi();
        viewNavigators.detailView(UiTestUtils.getCurrentView(), Nfe.class).editEntity(rascunho).navigate();
        NfeDetailView detalhe = UiTestUtils.getCurrentView();
        assertThat(((JmixButton) UiTestUtils.getComponent(detalhe, "textoImportacaoButton")).isVisible()).isTrue();

        detalhe.onTextoImportacaoButtonClick(null);

        assertThat(detalhe.getEditedEntity().getInfCpl()).startsWith("Importação: DI 2608986187 de 06/10/2026");
        assertThat(campo(detalhe, "infCplField").getValue()).isEqualTo(detalhe.getEditedEntity().getInfCpl());
    }

    @Test
    void telasDoItemEDaDi_mostramDiEAdicoes() {
        Nfe rascunho = rascunhoComItemEDi();
        NfeItem item = nfeDigitadaService.carregarCompleta(rascunho.getId()).getItens().get(0);

        viewNavigators.detailView(UiTestUtils.getCurrentView(), NfeItem.class).editEntity(item).navigate();
        NfeItemDetailView telaItem = UiTestUtils.getCurrentView();
        DataGrid<NfeDi> dis = UiTestUtils.getComponent(telaItem, "disDataGrid");
        assertThat(dis.getItems().getItems()).hasSize(1);
        assertThat(campo(telaItem, "valorIiField").getValue()).isEqualTo(new BigDecimal("14607.36"));

        NfeDi di = item.getDis().get(0);
        viewNavigators.detailView(UiTestUtils.getCurrentView(), NfeDi.class).editEntity(di).navigate();
        NfeDiDetailView telaDi = UiTestUtils.getCurrentView();
        DataGrid<NfeDiAdicao> adicoes = UiTestUtils.getComponent(telaDi, "adicoesDataGrid");
        assertThat(adicoes.getItems().getItems()).hasSize(1);
        assertThat(campo(telaDi, "viaTransporteField").getValue()).isEqualTo(ViaTransporteInternacional.AEREA);
    }

    // ---- dados ----

    @SuppressWarnings("unchecked")
    private static HasValueAndElement<?, Object> campo(io.jmix.flowui.view.View<?> view, String id) {
        return (HasValueAndElement<?, Object>) UiTestUtils.getComponent(view, id);
    }

    private Nfe rascunhoComItemEDi() {
        Nfe nfe = nfeDigitadaService.criarRascunho();
        NfeItem item = dataManager.create(NfeItem.class);
        item.setNfe(nfe);
        item.setItem(1);
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
        item.setCsosnIcms("900");
        item.setBaseIi(new BigDecimal("73036.78"));
        item.setValorIi(new BigDecimal("14607.36"));
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
        adicao.setSequencial(1);
        adicao.setCodFabricante("KJELLBERG");
        dataManager.save(new SaveContext().saving(nfe, item, di, adicao));
        return dataManager.load(Nfe.class).id(nfe.getId()).one();
    }

    private void limparDados() {
        dataManager.load(Nfe.class)
                .query("select e from Nfe e where e.codEmpresa = :codEmpresa or e.chave = :chave")
                .parameter("codEmpresa", COD_EMPRESA)
                .parameter("chave", CHAVE_AMOSTRA)
                .list()
                .forEach(dataManager::remove);
        dataManager.load(Empresa.class)
                .query("select e from Empresa e where e.codigo = :codEmpresa")
                .parameter("codEmpresa", COD_EMPRESA)
                .list()
                .forEach(dataManager::remove);
    }
}
