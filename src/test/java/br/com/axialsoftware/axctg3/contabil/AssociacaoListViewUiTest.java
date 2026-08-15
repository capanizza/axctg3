package br.com.axialsoftware.axctg3.contabil;

import br.com.axialsoftware.axctg3.Axctg3Application;
import br.com.axialsoftware.axctg3.entity.User;
import br.com.axialsoftware.axctg3.entity.cadastros.Empresa;
import br.com.axialsoftware.axctg3.entity.contabil.ContaContabil;
import br.com.axialsoftware.axctg3.entity.enums.CodNat;
import br.com.axialsoftware.axctg3.entity.enums.CodPlanRef;
import br.com.axialsoftware.axctg3.entity.tabelas.ContaReferencial;
import br.com.axialsoftware.axctg3.view.contabil.associacao.AssociacaoListView;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import io.jmix.core.DataManager;
import io.jmix.core.SaveContext;
import io.jmix.core.security.CurrentAuthentication;
import io.jmix.data.PersistenceHints;
import io.jmix.flowui.ViewNavigators;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.component.textfield.TypedTextField;
import io.jmix.flowui.component.valuepicker.EntityPicker;
import io.jmix.flowui.kit.component.button.JmixButton;
import io.jmix.flowui.testassist.FlowuiTestAssistConfiguration;
import io.jmix.flowui.testassist.UiTest;
import io.jmix.flowui.testassist.UiTestUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cobre {@code AssociacaoListView.onBeforeShow} (só contas analíticas da empresa/ano da
 * sessão) e o fluxo associar → sidePanel → salvar, que grava
 * {@code ContaContabil.contaReferencial} — sem este teste, um XML quebrado nesta tela
 * nunca entraria no code path do {@code clean test} puro.
 */
@UiTest
@SpringBootTest(classes = {Axctg3Application.class, FlowuiTestAssistConfiguration.class})
@ActiveProfiles("test")
class AssociacaoListViewUiTest {

    private static final int COD_EMPRESA = 9107;
    private static final int ANO = 2099;

    @Autowired
    DataManager dataManager;
    @Autowired
    ViewNavigators viewNavigators;
    @Autowired
    CurrentAuthentication currentAuthentication;

    private ContaReferencial contaReferencial;

    @BeforeEach
    void setUp() {
        User admin = (User) currentAuthentication.getUser();
        admin.setCodEmpresa(COD_EMPRESA);
        admin.setAnoContabil(ANO);

        limparDados();

        // A coluna codigo do dataGrid formata via FormatacaoService.formatacaoMascContabil,
        // que carrega a Empresa da sessão — mesma armadilha de ContaContabilListViewUiTest.
        Empresa empresa = dataManager.create(Empresa.class);
        empresa.setCodigo(COD_EMPRESA);
        empresa.setNome("Empresa de teste");
        empresa.setApelido("Teste");
        empresa.setCodPlanRef(CodPlanRef.PJ_em_geral_lucro_real);
        dataManager.save(empresa);

        criarContaContabil("9107", "Sintética de teste", false);
        criarContaContabil("9107.01", "Analítica de teste", true);

        contaReferencial = dataManager.create(ContaReferencial.class);
        contaReferencial.setCodigo("9107.01");
        contaReferencial.setDescricao("Caixa");
        contaReferencial.setDtIni(LocalDate.of(2015, 1, 1));
        contaReferencial.setGrau(3);
        contaReferencial.setAnalitica(true);
        contaReferencial.setCodNat(CodNat.CONTAS_DE_ATIVO);
        contaReferencial.setCodPlanRef(CodPlanRef.PJ_em_geral_lucro_real);
        contaReferencial = dataManager.save(contaReferencial);
    }

    @Test
    void mostraSoContasAnaliticasDaEmpresaEAno() {
        viewNavigators.view(UiTestUtils.getCurrentView(), AssociacaoListView.class).navigate();
        AssociacaoListView view = UiTestUtils.getCurrentView();

        HorizontalLayout buttonsPanel = UiTestUtils.getComponent(view, "buttonsPanel");
        assertThat(buttonsPanel.isVisible()).isTrue();

        assertThat(codigosDoGrid(view)).containsExactly("9107.01");
    }

    @Test
    void associarGravaVinculoEAtualizaGrid() {
        viewNavigators.view(UiTestUtils.getCurrentView(), AssociacaoListView.class).navigate();
        AssociacaoListView view = UiTestUtils.getCurrentView();

        DataGrid<ContaContabil> grid = UiTestUtils.getComponent(view, "contaContabilsDataGrid");
        ContaContabil analitica = grid.getItems().getItems().stream()
                .filter(c -> "9107.01".equals(c.getCodigo()))
                .findFirst().orElseThrow();
        grid.select(analitica);

        JmixButton associarButton = UiTestUtils.getComponent(view, "associarButton");
        associarButton.click();

        TypedTextField<Object> codigoField = UiTestUtils.getComponent(view, "codigoField");
        assertThat(codigoField.getValue()).isNotNull();
        TypedTextField<Object> nomeField = UiTestUtils.getComponent(view, "nomeField");
        assertThat(nomeField.getValue()).isEqualTo("Analítica de teste");

        EntityPicker<ContaReferencial> contaReferencialField = UiTestUtils.getComponent(view, "contaReferencialField");
        contaReferencialField.setValue(contaReferencial);

        JmixButton saveAndCloseBtn = UiTestUtils.getComponent(view, "saveAndCloseBtn");
        saveAndCloseBtn.click();

        ContaContabil atualizada = grid.getItems().getItems().stream()
                .filter(c -> "9107.01".equals(c.getCodigo()))
                .findFirst().orElseThrow();
        assertThat(atualizada.getContaReferencial()).isNotNull();
        assertThat(atualizada.getContaReferencial().getCodigo()).isEqualTo("9107.01");
    }

    private List<String> codigosDoGrid(AssociacaoListView view) {
        DataGrid<ContaContabil> grid = UiTestUtils.getComponent(view, "contaContabilsDataGrid");
        return grid.getItems().getItems().stream().map(ContaContabil::getCodigo).toList();
    }

    private ContaContabil criarContaContabil(String codigo, String nome, boolean analitica) {
        ContaContabil conta = dataManager.create(ContaContabil.class);
        conta.setCodigo(codigo);
        conta.setNome(nome);
        conta.setAno(ANO);
        conta.setCodEmpresa(COD_EMPRESA);
        conta.setGrau(1);
        conta.setCodContaSup(codigo);
        conta.setAnalitica(analitica);
        conta.setCodNat(CodNat.CONTAS_DE_ATIVO);
        return dataManager.save(conta);
    }

    @AfterEach
    void tearDown() {
        limparDados();
    }

    // Hard delete: os índices únicos de ContaContabil/ContaReferencial/Empresa no HSQLDB não
    // excluem registros com soft delete, ao contrário do Postgres — mesmo padrão de
    // ContaContabilListViewUiTest/ContaReferencialListViewUiTest.
    private void limparDados() {
        apagar(dataManager.load(ContaContabil.class)
                .query("select e from ContaContabil e where e.codEmpresa = :codEmpresa")
                .parameter("codEmpresa", COD_EMPRESA)
                .hint(PersistenceHints.SOFT_DELETION, false)
                .list());
        apagar(dataManager.load(ContaReferencial.class)
                .query("select e from ContaReferencial e where e.codigo = :codigo")
                .parameter("codigo", "9107.01")
                .hint(PersistenceHints.SOFT_DELETION, false)
                .list());
        apagar(dataManager.load(Empresa.class)
                .query("select e from Empresa e where e.codigo = :codEmpresa")
                .parameter("codEmpresa", COD_EMPRESA)
                .hint(PersistenceHints.SOFT_DELETION, false)
                .list());
    }

    private void apagar(List<?> entidades) {
        if (entidades.isEmpty()) {
            return;
        }
        dataManager.save(new SaveContext()
                .setHint(PersistenceHints.SOFT_DELETION, false)
                .setHint(PersistenceHints.SKIP_ENTITY_CHANGED_EVENT, true)
                .removing(entidades.toArray()));
    }
}
