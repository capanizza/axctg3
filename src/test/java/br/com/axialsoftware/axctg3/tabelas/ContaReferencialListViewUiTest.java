package br.com.axialsoftware.axctg3.tabelas;

import br.com.axialsoftware.axctg3.Axctg3Application;
import br.com.axialsoftware.axctg3.entity.User;
import br.com.axialsoftware.axctg3.entity.cadastros.Empresa;
import br.com.axialsoftware.axctg3.entity.enums.CodNat;
import br.com.axialsoftware.axctg3.entity.enums.CodPlanRef;
import br.com.axialsoftware.axctg3.entity.tabelas.ContaReferencial;
import br.com.axialsoftware.axctg3.view.tabelas.contareferencial.ContaReferencialListView;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import io.jmix.core.DataManager;
import io.jmix.core.SaveContext;
import io.jmix.core.security.CurrentAuthentication;
import io.jmix.data.PersistenceHints;
import io.jmix.flowui.ViewNavigators;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.data.grid.DataGridItems;
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
 * Abre {@code ContaReferencial.list} e cobre o filtro de
 * {@code ContaReferencialListView.onBeforeShow}, que restringe a listagem ao
 * {@code CodPlanRef} da empresa da sessão — sem esse teste, um XML quebrado na tela nunca
 * entraria no code path do {@code clean test} puro.
 */
@UiTest
@SpringBootTest(classes = {Axctg3Application.class, FlowuiTestAssistConfiguration.class})
@ActiveProfiles("test")
class ContaReferencialListViewUiTest {

    private static final int COD_EMPRESA = 9106;

    @Autowired
    DataManager dataManager;
    @Autowired
    ViewNavigators viewNavigators;
    @Autowired
    CurrentAuthentication currentAuthentication;

    @BeforeEach
    void setUp() {
        User admin = (User) currentAuthentication.getUser();
        admin.setCodEmpresa(COD_EMPRESA);

        limparDados();

        Empresa empresa = dataManager.create(Empresa.class);
        empresa.setCodigo(COD_EMPRESA);
        empresa.setNome("Empresa de teste");
        empresa.setApelido("Teste");
        empresa.setCodPlanRef(CodPlanRef.PJ_em_geral_lucro_real);
        dataManager.save(empresa);

        criarConta("9", CodPlanRef.PJ_em_geral_lucro_real);
        criarConta("9.01", CodPlanRef.PJ_em_geral_lucro_real);
        // de outro plano — não deve aparecer quando a empresa usa PJ_em_geral_lucro_real
        criarConta("9", CodPlanRef.PJ_em_geral_lucro_presumido);
    }

    @Test
    void mostraSoAsContasDoPlanoReferencialDaEmpresa() {
        viewNavigators.view(UiTestUtils.getCurrentView(), ContaReferencialListView.class).navigate();
        ContaReferencialListView view = UiTestUtils.getCurrentView();

        HorizontalLayout buttonsPanel = UiTestUtils.getComponent(view, "buttonsPanel");
        assertThat(buttonsPanel.isVisible()).isTrue();

        DataGrid<ContaReferencial> grid = UiTestUtils.getComponent(view, "contaReferencialsDataGrid");
        DataGridItems<ContaReferencial> items = grid.getItems();
        List<String> codigos = items.getItems().stream().map(ContaReferencial::getCodigo).toList();

        assertThat(codigos).containsExactlyInAnyOrder("9", "9.01");
    }

    private ContaReferencial criarConta(String codigo, CodPlanRef codPlanRef) {
        ContaReferencial conta = dataManager.create(ContaReferencial.class);
        conta.setCodigo(codigo);
        conta.setDescricao("Conta de teste " + codigo);
        conta.setDtIni(LocalDate.of(2015, 1, 1));
        conta.setGrau(1);
        conta.setCodNat(CodNat.CONTAS_DE_ATIVO);
        conta.setCodPlanRef(codPlanRef);
        return dataManager.save(conta);
    }

    @AfterEach
    void tearDown() {
        limparDados();
    }

    // Hard delete: o índice único de ContaReferencial/Empresa no HSQLDB não exclui registros
    // com soft delete, ao contrário do Postgres — mesmo padrão de ContaContabilListViewUiTest.
    private void limparDados() {
        apagar(dataManager.load(ContaReferencial.class)
                .query("select e from ContaReferencial e where e.codigo like '9%'")
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
