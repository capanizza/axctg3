package br.com.axialsoftware.axctg3.contabil;

import br.com.axialsoftware.axctg3.Axctg3Application;
import br.com.axialsoftware.axctg3.entity.User;
import br.com.axialsoftware.axctg3.entity.cadastros.Empresa;
import br.com.axialsoftware.axctg3.entity.contabil.ContaContabil;
import br.com.axialsoftware.axctg3.entity.enums.CodNat;
import br.com.axialsoftware.axctg3.view.contabil.contacontabil.ContaContabilListView;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import io.jmix.core.DataManager;
import io.jmix.core.SaveContext;
import io.jmix.core.security.CurrentAuthentication;
import io.jmix.data.PersistenceHints;
import io.jmix.flowui.DialogWindows;
import io.jmix.flowui.ViewNavigators;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.data.grid.DataGridItems;
import io.jmix.flowui.testassist.FlowuiTestAssistConfiguration;
import io.jmix.flowui.testassist.UiTest;
import io.jmix.flowui.testassist.UiTestUtils;
import io.jmix.flowui.view.View;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lançamentos contábeis só podem ser feitos em contas analíticas — cobre o filtro de
 * {@code ContaContabilListView.onBeforeShow} que restringe a listagem a elas quando a
 * tela abre como dialog (lookup de {@code contaDevedoraField}/{@code contaCredoraField}
 * do {@code Lancamento}), e mantém tudo visível fora do dialog (gestão do plano de contas).
 */
@UiTest
@SpringBootTest(classes = {Axctg3Application.class, FlowuiTestAssistConfiguration.class})
@ActiveProfiles("test")
class ContaContabilListViewUiTest {

    private static final int COD_EMPRESA = 9105;
    private static final int ANO = 2099;

    @Autowired
    DataManager dataManager;
    @Autowired
    ViewNavigators viewNavigators;
    @Autowired
    DialogWindows dialogWindows;
    @Autowired
    CurrentAuthentication currentAuthentication;

    @BeforeEach
    void setUp() {
        User admin = (User) currentAuthentication.getUser();
        admin.setCodEmpresa(COD_EMPRESA);
        admin.setAnoContabil(ANO);

        limparDados();

        // A coluna codigo do dataGrid formata via FormatacaoService.formatacaoMascContabil,
        // que carrega a Empresa da sessão — sem essa linha, UtilGeralService.getEmpresa()
        // lança NoResultException ao renderizar a grid (mesma armadilha de MovimentoBancoTest).
        Empresa empresa = dataManager.create(Empresa.class);
        empresa.setCodigo(COD_EMPRESA);
        empresa.setNome("Empresa de teste");
        empresa.setApelido("Teste");
        dataManager.save(empresa);

        criarConta("9601", "Sintética de teste", false);
        criarConta("9601.01", "Analítica de teste", true);
    }

    @Test
    void navegacaoDiretaMostraTodasAsContasEBotoes() {
        viewNavigators.view(UiTestUtils.getCurrentView(), ContaContabilListView.class).navigate();
        ContaContabilListView view = UiTestUtils.getCurrentView();

        HorizontalLayout buttonsPanel = UiTestUtils.getComponent(view, "buttonsPanel");
        assertThat(buttonsPanel.isVisible()).isTrue();

        assertThat(codigosDoGrid(view)).contains("9601", "9601.01");
    }

    @Test
    void lookupComoDialogEscondeBotoesEMostraApenasAnaliticas() {
        View<?> baseView = UiTestUtils.getCurrentView();

        dialogWindows.lookup(baseView, ContaContabil.class)
                .withViewClass(ContaContabilListView.class)
                .open();

        ContaContabilListView dialogView = UiTestUtils.getLastOpenedViewDialog();

        HorizontalLayout buttonsPanel = UiTestUtils.getComponent(dialogView, "buttonsPanel");
        assertThat(buttonsPanel.isVisible()).isFalse();

        List<String> codigos = codigosDoGrid(dialogView);
        assertThat(codigos).contains("9601.01");
        assertThat(codigos).doesNotContain("9601");
    }

    private List<String> codigosDoGrid(ContaContabilListView view) {
        DataGrid<ContaContabil> grid = UiTestUtils.getComponent(view, "contaContabilsDataGrid");
        DataGridItems<ContaContabil> items = grid.getItems();
        return items.getItems().stream().map(ContaContabil::getCodigo).toList();
    }

    private ContaContabil criarConta(String codigo, String nome, boolean analitica) {
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

    // Hard delete: os índices únicos de ContaContabil/Empresa no HSQLDB não excluem
    // registros com soft delete, ao contrário do Postgres — um dataManager.remove() comum
    // deixaria a linha ocupando a chave natural e a próxima criação desta suíte colidiria.
    // Mesmo padrão de MovimentoBancoTest.apagar()/carregar().
    private void limparDados() {
        apagar(carregar(ContaContabil.class, "select e from ContaContabil e where e.codEmpresa = :codEmpresa"));
        apagar(carregar(Empresa.class, "select e from Empresa e where e.codigo = :codEmpresa"));
    }

    private <E> List<E> carregar(Class<E> entityClass, String query) {
        return dataManager.load(entityClass)
                .query(query)
                .parameter("codEmpresa", COD_EMPRESA)
                .hint(PersistenceHints.SOFT_DELETION, false)
                .list();
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
