package br.com.axialsoftware.axctg3.tabelas;

import br.com.axialsoftware.axctg3.Axctg3Application;
import br.com.axialsoftware.axctg3.view.tabelas.tabelaibpt.TabelaIbptListView;
import io.jmix.flowui.ViewNavigators;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.component.upload.FileUploadField;
import io.jmix.flowui.kit.component.button.JmixButton;
import io.jmix.flowui.testassist.FlowuiTestAssistConfiguration;
import io.jmix.flowui.testassist.UiTest;
import io.jmix.flowui.testassist.UiTestUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Abre {@code TabelaIbpt.list} — prova que o descritor XML resolve (container, colunas,
 * upload e botão de import), coisa que compileJava e o clean test puro não enxergam. O
 * import em si está coberto em {@link TabelaIbptImportServiceTest}.
 */
@UiTest
@SpringBootTest(classes = {Axctg3Application.class, FlowuiTestAssistConfiguration.class})
@ActiveProfiles("test")
class TabelaIbptListViewUiTest {

    @Autowired
    ViewNavigators viewNavigators;

    @Test
    void abreComGridUploadEBotaoDeImportar() {
        viewNavigators.view(UiTestUtils.getCurrentView(), TabelaIbptListView.class).navigate();
        TabelaIbptListView view = UiTestUtils.getCurrentView();

        DataGrid<?> grid = UiTestUtils.getComponent(view, "tabelaIbptsDataGrid");
        assertThat(grid.getColumns()).hasSize(12);
        FileUploadField upload = UiTestUtils.getComponent(view, "importField");
        assertThat(upload).isNotNull();
        JmixButton importar = UiTestUtils.getComponent(view, "importButton");
        assertThat(importar.getText()).isEqualTo("Importar");
    }
}
