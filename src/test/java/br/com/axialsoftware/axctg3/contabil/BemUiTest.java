package br.com.axialsoftware.axctg3.contabil;

import br.com.axialsoftware.axctg3.Axctg3Application;
import br.com.axialsoftware.axctg3.entity.User;
import br.com.axialsoftware.axctg3.entity.cadastros.Parceiro;
import br.com.axialsoftware.axctg3.entity.contabil.Bem;
import br.com.axialsoftware.axctg3.entity.contabil.ContaContabil;
import br.com.axialsoftware.axctg3.entity.contabil.Depreciacao;
import br.com.axialsoftware.axctg3.entity.enums.CodNat;
import br.com.axialsoftware.axctg3.view.contabil.bem.BemDetailView;
import br.com.axialsoftware.axctg3.view.contabil.bem.BemListView;
import io.jmix.core.DataManager;
import io.jmix.core.SaveContext;
import io.jmix.core.security.CurrentAuthentication;
import io.jmix.data.PersistenceHints;
import io.jmix.flowui.ViewNavigators;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.component.valuepicker.EntityPicker;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cobre o CRUD de Bem/Depreciacao: BemEventListener preenchendo os padrões na criação
 * (codEmpresa, valorAtual, deprAcum, valorIniDepr, taxaDepr), a listagem, e o detail view
 * com a composição de depreciações editada inline (dataGrid property-bound, sem loader).
 */
@UiTest
@SpringBootTest(classes = {Axctg3Application.class, FlowuiTestAssistConfiguration.class})
@ActiveProfiles("test")
class BemUiTest {

    private static final int COD_EMPRESA = 9305;
    private static final int ANO = 2097;

    @Autowired
    DataManager dataManager;
    @Autowired
    ViewNavigators viewNavigators;
    @Autowired
    CurrentAuthentication currentAuthentication;

    private ContaContabil conta;
    private Parceiro parceiro;
    private Bem bem;

    @BeforeEach
    void setUp() {
        User admin = (User) currentAuthentication.getUser();
        admin.setCodEmpresa(COD_EMPRESA);
        admin.setAnoContabil(ANO);

        limparDados();

        conta = criarConta("9801.01", "Móveis e utensílios", true);
        parceiro = criarParceiro(1L, "Fornecedor de teste");

        bem = dataManager.create(Bem.class);
        bem.setCodigo(1L);
        bem.setDescricao("Mesa de reunião");
        bem.setDataCompra(LocalDate.of(ANO, 1, 10));
        bem.setValorCompra(new BigDecimal("1000.00"));
        bem.setContaContabil(conta);
        bem.setParceiro(parceiro);
        bem = dataManager.save(bem);

        Depreciacao depreciacao = dataManager.create(Depreciacao.class);
        depreciacao.setBem(bem);
        depreciacao.setDataDepr(LocalDate.of(ANO, 2, 28));
        depreciacao.setValorDepr(new BigDecimal("10.00"));
        dataManager.save(depreciacao);
    }

    @Test
    void listenerPreencheOsPadroesNaCriacao() {
        assertThat(bem.getCodEmpresa()).isEqualTo(COD_EMPRESA);
        assertThat(bem.getValorAtual()).isEqualByComparingTo("1000.00");
        assertThat(bem.getDeprAcum()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(bem.getValorIniDepr()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(bem.getTaxaDepr()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void listaMostraOBemCriado() {
        viewNavigators.view(UiTestUtils.getCurrentView(), BemListView.class).navigate();
        BemListView view = UiTestUtils.getCurrentView();

        DataGrid<Bem> grid = UiTestUtils.getComponent(view, "bemsDataGrid");
        DataGridItems<Bem> items = grid.getItems();
        assertThat(items.getItems()).extracting(Bem::getCodigo).contains(1L);
    }

    @Test
    void detailAbreComOsCamposEComADepreciacaoInline() {
        viewNavigators.detailView(UiTestUtils.getCurrentView(), Bem.class)
                .editEntity(bem)
                .withViewClass(BemDetailView.class)
                .navigate();
        BemDetailView view = UiTestUtils.getCurrentView();

        EntityPicker<ContaContabil> contaField = UiTestUtils.getComponent(view, "contaContabilField");
        assertThat(contaField.getValue()).isEqualTo(conta);

        EntityPicker<Parceiro> parceiroField = UiTestUtils.getComponent(view, "parceiroField");
        assertThat(parceiroField.getValue()).isEqualTo(parceiro);

        DataGrid<Depreciacao> depreciacoesGrid = UiTestUtils.getComponent(view, "depreciacaosDataGrid");
        assertThat(depreciacoesGrid.getItems().getItems()).hasSize(1);
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

    private Parceiro criarParceiro(Long codigo, String nome) {
        Parceiro parceiro = dataManager.create(Parceiro.class);
        parceiro.setCodigo(codigo);
        parceiro.setCodEmpresa(COD_EMPRESA);
        parceiro.setNome(nome);
        parceiro.setApelido(nome);
        parceiro.setCnpj("00000000000000");
        return dataManager.save(parceiro);
    }

    @AfterEach
    void tearDown() {
        limparDados();
    }

    // Hard delete: o índice único de Bem (CODIGO, COD_EMPRESA) e o de ContaContabil só
    // existem no Postgres (dbms="postgresql" no changelog) — no HSQLDB um soft delete não
    // libera a chave natural mesmo assim, então uma segunda rodada da suíte colidiria.
    // Mesmo padrão de ContaContabilListViewUiTest/MovimentoBancoTest.
    private void limparDados() {
        apagar(carregar(Depreciacao.class, "select e from Depreciacao e where e.bem.codEmpresa = :codEmpresa"));
        apagar(carregar(Bem.class, "select e from Bem e where e.codEmpresa = :codEmpresa"));
        apagar(carregar(ContaContabil.class, "select e from ContaContabil e where e.codEmpresa = :codEmpresa"));
        apagar(carregar(Parceiro.class, "select e from Parceiro e where e.codEmpresa = :codEmpresa"));
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
