package br.com.axialsoftware.axctg3.importacao;

import br.com.axialsoftware.axctg3.Axctg3Application;
import br.com.axialsoftware.axctg3.entity.enums.SituacaoLote;
import br.com.axialsoftware.axctg3.entity.enums.TipoLote;
import br.com.axialsoftware.axctg3.entity.importacao.ImpLote;
import br.com.axialsoftware.axctg3.view.importacao.implote.ImpLoteListView;
import io.jmix.core.DataManager;
import io.jmix.core.SaveContext;
import io.jmix.data.PersistenceHints;
import io.jmix.flowui.ViewNavigators;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.component.textarea.JmixTextArea;
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
 * Abre a tela de importação do legado (descritor XML, menu e mensagens) e confere que
 * selecionar um lote mostra o relatório dele no quadro de baixo.
 */
@UiTest
@SpringBootTest(classes = {Axctg3Application.class, FlowuiTestAssistConfiguration.class})
@ActiveProfiles("test")
class ImpLoteListViewUiTest {

    private static final int COD_EMPRESA = 9307;

    @Autowired
    DataManager dataManager;
    @Autowired
    ViewNavigators viewNavigators;

    private ImpLote lote;

    @BeforeEach
    void setUp() {
        limpar();
        lote = dataManager.create(ImpLote.class);
        lote.setTipo(TipoLote.PLANO_CONTAS);
        lote.setSituacao(SituacaoLote.PRONTO);
        lote.setCodEmpresa(COD_EMPRESA);
        lote.setAno(2097);
        lote.setMensagem("relatório de teste 9307");
        lote = dataManager.save(lote);
    }

    @AfterEach
    void tearDown() {
        limpar();
    }

    @Test
    @SuppressWarnings("unchecked")
    void selecionarLoteMostraRelatorio() {
        viewNavigators.view(UiTestUtils.getCurrentView(), ImpLoteListView.class).navigate();
        ImpLoteListView view = UiTestUtils.getCurrentView();

        DataGrid<ImpLote> grid = (DataGrid<ImpLote>) UiTestUtils.getComponent(view, "impLotesDataGrid");
        JmixTextArea mensagem = (JmixTextArea) UiTestUtils.getComponent(view, "mensagemField");
        ImpLote naGrade = grid.getItems().getItems().stream()
                .filter(l -> l.getId().equals(lote.getId()))
                .findFirst()
                .orElseThrow();

        grid.select(naGrade);

        assertThat(mensagem.getValue()).isEqualTo("relatório de teste 9307");
        JmixButton conferir = (JmixButton) UiTestUtils.getComponent(view, "conferirButton");
        JmixButton importar = (JmixButton) UiTestUtils.getComponent(view, "importarButton");
        assertThat(conferir.getText()).isEqualTo("Conferir");
        assertThat(importar.getText()).isEqualTo("Importar");
    }

    private void limpar() {
        List<ImpLote> lotes = dataManager.load(ImpLote.class)
                .query("select e from ImpLote e where e.codEmpresa = :codEmpresa")
                .parameter("codEmpresa", COD_EMPRESA)
                .hint(PersistenceHints.SOFT_DELETION, false)
                .list();
        if (!lotes.isEmpty()) {
            dataManager.save(new SaveContext()
                    .setHint(PersistenceHints.SOFT_DELETION, false)
                    .removing(lotes.toArray()));
        }
    }
}
