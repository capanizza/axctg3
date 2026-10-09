package br.com.axialsoftware.axctg3.fiscal;

import br.com.axialsoftware.axctg3.Axctg3Application;
import br.com.axialsoftware.axctg3.entity.tabelas.ClassificacaoFiscal;
import br.com.axialsoftware.axctg3.view.tabelas.classificacaofiscal.ClassificacaoFiscalListView;
import io.jmix.core.DataManager;
import io.jmix.flowui.ViewNavigators;
import io.jmix.flowui.component.textarea.JmixTextArea;
import io.jmix.flowui.component.textfield.JmixIntegerField;
import io.jmix.flowui.component.textfield.TypedTextField;
import io.jmix.flowui.component.upload.FileUploadField;
import io.jmix.flowui.kit.component.button.JmixButton;
import io.jmix.flowui.testassist.FlowuiTestAssistConfiguration;
import io.jmix.flowui.testassist.UiTest;
import io.jmix.flowui.testassist.UiTestUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A lista abre com o upload da tabela NCM visível para quem pode gravar, e o sidePanel
 * grava a descrição longa (textArea, até 2000) que a importação passou a usar.
 */
@UiTest
@SpringBootTest(classes = {Axctg3Application.class, FlowuiTestAssistConfiguration.class})
@ActiveProfiles("test")
class ClassificacaoFiscalListViewUiTest {

    private static final int CODIGO = 990009;

    @Autowired
    DataManager dataManager;
    @Autowired
    ViewNavigators viewNavigators;

    @AfterEach
    void tearDown() {
        dataManager.load(ClassificacaoFiscal.class)
                .query("select e from ClassificacaoFiscal e where e.codigo = :codigo")
                .parameter("codigo", CODIGO)
                .list()
                .forEach(dataManager::remove);
    }

    @Test
    void abreComImportacaoESalvaDescricaoLonga() {
        viewNavigators.view(UiTestUtils.getCurrentView(), ClassificacaoFiscalListView.class).navigate();
        ClassificacaoFiscalListView view = UiTestUtils.getCurrentView();

        FileUploadField importField = UiTestUtils.getComponent(view, "importField");
        JmixButton importButton = UiTestUtils.getComponent(view, "importButton");
        assertThat(importField.isVisible()).isTrue();
        assertThat(importButton.isVisible()).isTrue();

        JmixButton createBtn = UiTestUtils.getComponent(view, "createButton");
        createBtn.click();
        JmixIntegerField codigoField = UiTestUtils.getComponent(view, "codigoField");
        codigoField.setValue(CODIGO);
        TypedTextField<String> codNcmField = UiTestUtils.getComponent(view, "codNcmField");
        codNcmField.setValue("98777777");
        String descricao = "Descrição longa ".repeat(10).trim();
        JmixTextArea descricaoField = UiTestUtils.getComponent(view, "descricaoField");
        descricaoField.setValue(descricao);
        JmixButton saveBtn = UiTestUtils.getComponent(view, "saveAndCloseBtn");
        saveBtn.click();

        ClassificacaoFiscal salvo = dataManager.load(ClassificacaoFiscal.class)
                .query("select e from ClassificacaoFiscal e where e.codigo = :codigo")
                .parameter("codigo", CODIGO)
                .one();
        assertThat(salvo.getCodNcm()).isEqualTo("98777777");
        assertThat(salvo.getDescricao()).isEqualTo(descricao);
    }
}
