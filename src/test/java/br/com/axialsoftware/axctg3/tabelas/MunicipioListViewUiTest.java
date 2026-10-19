package br.com.axialsoftware.axctg3.tabelas;

import br.com.axialsoftware.axctg3.Axctg3Application;
import br.com.axialsoftware.axctg3.view.tabelas.municipio.MunicipioListView;
import io.jmix.flowui.ViewNavigators;
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

/** A lista abre com o upload da tabela de municípios visível para quem pode gravar. */
@UiTest
@SpringBootTest(classes = {Axctg3Application.class, FlowuiTestAssistConfiguration.class})
@ActiveProfiles("test")
class MunicipioListViewUiTest {

    @Autowired
    ViewNavigators viewNavigators;

    @Test
    void abreComImportacaoVisivel() {
        viewNavigators.view(UiTestUtils.getCurrentView(), MunicipioListView.class).navigate();
        MunicipioListView view = UiTestUtils.getCurrentView();

        FileUploadField importField = UiTestUtils.getComponent(view, "importField");
        JmixButton importButton = UiTestUtils.getComponent(view, "importButton");
        assertThat(importField.isVisible()).isTrue();
        assertThat(importButton.isVisible()).isTrue();
    }
}
