package br.com.axialsoftware.axctg3.administracao;

import br.com.axialsoftware.axctg3.Axctg3Application;
import br.com.axialsoftware.axctg3.entity.Sequencia;
import br.com.axialsoftware.axctg3.entity.User;
import br.com.axialsoftware.axctg3.view.sequencia.SequenciaListView;
import io.jmix.core.DataManager;
import io.jmix.core.SaveContext;
import io.jmix.core.security.CurrentAuthentication;
import io.jmix.data.PersistenceHints;
import io.jmix.data.Sequence;
import io.jmix.data.Sequences;
import io.jmix.flowui.ViewNavigators;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.component.textfield.JmixIntegerField;
import io.jmix.flowui.component.textfield.TypedTextField;
import io.jmix.flowui.data.grid.DataGridItems;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Portada do axctg-flow (entity/bean/listener {@code Sequencia}) — ver conversa
 * 2026-08-24. Cobre o fluxo do sidePanel (padrão CentroCusto, não detail view separado)
 * e confirma que salvar de fato empurra o valor pra sequence real via
 * {@code SequenciaEventListener}/{@code SequenciaService} — é esse write-through que
 * responde "sim" pra pergunta original do usuário sobre alterar o valor de uma sequence.
 */
@UiTest
@SpringBootTest(classes = {Axctg3Application.class, FlowuiTestAssistConfiguration.class})
@ActiveProfiles("test")
class SequenciaListViewUiTest {

    private static final int COD_EMPRESA = 9006;
    private static final String CODIGO_SEQ = "seq_teste_ui_9006";

    @Autowired
    DataManager dataManager;
    @Autowired
    ViewNavigators viewNavigators;
    @Autowired
    CurrentAuthentication currentAuthentication;
    @Autowired
    Sequences sequences;

    @BeforeEach
    void setUp() {
        User admin = (User) currentAuthentication.getUser();
        admin.setCodEmpresa(COD_EMPRESA);
        limparDados();
    }

    @AfterEach
    void tearDown() {
        limparDados();
    }

    @Test
    void criarSequenciaPeloSidePanelEmpurraValorPraSequenceReal() {
        viewNavigators.view(UiTestUtils.getCurrentView(), SequenciaListView.class).navigate();
        SequenciaListView view = UiTestUtils.getCurrentView();

        JmixButton createBtn = UiTestUtils.getComponent(view, "createButton");
        createBtn.click();

        TypedTextField<String> codigoField = UiTestUtils.getComponent(view, "codigoField");
        codigoField.setValue(CODIGO_SEQ);
        JmixIntegerField valorField = UiTestUtils.getComponent(view, "valorField");
        valorField.setValue(500);

        JmixButton saveBtn = UiTestUtils.getComponent(view, "saveAndCloseBtn");
        saveBtn.click();

        DataGrid<Sequencia> grid = UiTestUtils.getComponent(view, "sequenciasDataGrid");
        DataGridItems<Sequencia> items = grid.getItems();
        assertThat(items.getItems())
                .extracting(Sequencia::getCodigo, Sequencia::getValor)
                .contains(org.assertj.core.groups.Tuple.tuple(CODIGO_SEQ, 500));

        // a prova real: o listener empurrou 500 pra sequence de banco de nome CODIGO_SEQ.
        // Faixa, não valor exato: setCurrentValue depende do store — no PostgreSQL (setval)
        // o próximo createNextValue sai value+increment (501), no HSQLDB destes testes
        // (alter sequence restart with) sai o próprio value (500). Mesma diferença documentada
        // em ImportarLancamentosTest.test_sequenciaReposicionadaNaFaixaImportada.
        long proximo = sequences.createNextValue(Sequence.withName(CODIGO_SEQ));
        assertThat(proximo).isBetween(500L, 501L);
    }

    @Test
    void editarSequenciaPeloSidePanelAtualizaOValorNaSequenceReal() {
        Sequencia sequencia = dataManager.create(Sequencia.class);
        sequencia.setCodigo(CODIGO_SEQ);
        sequencia.setCodEmpresa(COD_EMPRESA);
        sequencia.setValor(10);
        dataManager.save(sequencia);

        viewNavigators.view(UiTestUtils.getCurrentView(), SequenciaListView.class).navigate();
        SequenciaListView view = UiTestUtils.getCurrentView();

        DataGrid<Sequencia> grid = UiTestUtils.getComponent(view, "sequenciasDataGrid");
        grid.select(sequencia);

        JmixButton editBtn = UiTestUtils.getComponent(view, "editButton");
        editBtn.click();

        JmixIntegerField valorField = UiTestUtils.getComponent(view, "valorField");
        assertThat(valorField.getValue()).isEqualTo(10);
        valorField.setValue(999);

        JmixButton saveBtn = UiTestUtils.getComponent(view, "saveAndCloseBtn");
        saveBtn.click();

        long proximo = sequences.createNextValue(Sequence.withName(CODIGO_SEQ));
        assertThat(proximo).isBetween(999L, 1000L);
    }

    private void limparDados() {
        List<Sequencia> registros = dataManager.load(Sequencia.class)
                .query("select e from Sequencia e where e.codEmpresa = :codEmpresa")
                .parameter("codEmpresa", COD_EMPRESA)
                .hint(PersistenceHints.SOFT_DELETION, false)
                .list();
        if (registros.isEmpty()) {
            return;
        }
        dataManager.save(new SaveContext()
                .setHint(PersistenceHints.SOFT_DELETION, false)
                .setHint(PersistenceHints.SKIP_ENTITY_CHANGED_EVENT, true)
                .removing(registros.toArray()));
    }
}
