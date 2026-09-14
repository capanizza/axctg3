package br.com.axialsoftware.axctg3.fiscal;

import br.com.axialsoftware.axctg3.Axctg3Application;
import br.com.axialsoftware.axctg3.entity.fiscal.ItemNotaSaida;
import br.com.axialsoftware.axctg3.entity.fiscal.Produto;
import br.com.axialsoftware.axctg3.entity.tabelas.ClassTrib;
import br.com.axialsoftware.axctg3.entity.tabelas.Cst;
import br.com.axialsoftware.axctg3.view.fiscal.itemnotasaida.ItemNotaSaidaDetailView;
import io.jmix.core.DataManager;
import io.jmix.flowui.ViewNavigators;
import io.jmix.flowui.component.textfield.JmixBigDecimalField;
import io.jmix.flowui.component.textfield.TypedTextField;
import io.jmix.flowui.component.valuepicker.EntityPicker;
import io.jmix.flowui.testassist.FlowuiTestAssistConfiguration;
import io.jmix.flowui.testassist.UiTest;
import io.jmix.flowui.testassist.UiTestUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cobre o preview ao vivo de CST/ICMS decidido em 2026-09-14: o usuário reclamou que a
 * base de ICMS ficava em branco na tela do item enquanto digitava (só era calculada de
 * verdade no save, via {@code ItemNotaSaidaEventListener}). {@code
 * ItemNotaSaidaDetailView.onItemNotaSaidaDcItemPropertyChange} resolve isso escutando
 * {@code itemNotaSaidaDc} — este teste abre o item NOVO (sem salvar, sem notaSaida
 * associada — mesma limitação de {@code NotaSaidaComplementarDetailViewUiTest}: {@code
 * ViewNavigators.detailView(...).newEntity()} não aceita pré-popular o item como a
 * abertura real via composição faz) e simula escolher produto + digitar quantidade/
 * valorUnitario, conferindo que os campos read-only já mostram o valor calculado antes
 * de qualquer Salvar. Sem natureza associada, cai direto no ramo "produto decide" —
 * suficiente pra provar que a assinatura/target do {@code @Subscribe} está correta (o
 * cálculo em si já é coberto por {@code ItemNotaSaidaCstTest}, sem UI).
 */
@UiTest
@SpringBootTest(classes = {Axctg3Application.class, FlowuiTestAssistConfiguration.class})
@ActiveProfiles("test")
class ItemNotaSaidaDetailViewIcmsPreviewUiTest {

    private static final int COD_EMPRESA = 9112;
    private static final int CLASS_TRIB_CODIGO_TESTE = 9990003;

    @Autowired
    private DataManager dataManager;
    @Autowired
    private ViewNavigators viewNavigators;

    @AfterEach
    void tearDown() {
        dataManager.load(Produto.class)
                .query("select e from Produto e where e.codEmpresa = :codEmpresa")
                .parameter("codEmpresa", COD_EMPRESA)
                .list()
                .forEach(dataManager::remove);
        dataManager.load(ClassTrib.class)
                .query("select e from ClassTrib e where e.codigo = :codigo")
                .parameter("codigo", CLASS_TRIB_CODIGO_TESTE)
                .list()
                .forEach(dataManager::remove);
    }

    private Cst carregarCst(String codigo) {
        return dataManager.load(Cst.class)
                .query("select e from Cst e where e.codigo = :codigo")
                .parameter("codigo", codigo)
                .one();
    }

    @Test
    void previewDoIcmsApareceAntesDeSalvar() {
        ClassTrib classTrib = dataManager.create(ClassTrib.class);
        classTrib.setCodigo(CLASS_TRIB_CODIGO_TESTE);
        classTrib.setCst(1);
        classTrib.setDescricao("ClassTrib de teste");
        classTrib.setTipoAliquota("Padrão");
        classTrib.setNomenclatura("Teste");
        classTrib.setDescricaoTratamentoTributario("Teste");
        classTrib = dataManager.save(classTrib);

        Produto produto = dataManager.create(Produto.class);
        produto.setCodigo(1);
        produto.setCodEmpresa(COD_EMPRESA);
        produto.setDescricao("Produto de teste");
        produto.setApelido("Teste");
        produto.setClassTrib(classTrib);
        produto.setCst(carregarCst("10"));
        produto.setAliqIcms(new BigDecimal("18.00"));
        produto = dataManager.save(produto);

        viewNavigators.detailView(UiTestUtils.getCurrentView(), ItemNotaSaida.class)
                .withViewClass(ItemNotaSaidaDetailView.class)
                .newEntity()
                .navigate();
        ItemNotaSaidaDetailView view = UiTestUtils.getCurrentView();

        EntityPicker<Produto> produtoField = UiTestUtils.getComponent(view, "produtoField");
        produtoField.setValue(produto);

        JmixBigDecimalField quantidadeField = UiTestUtils.getComponent(view, "quantidadeField");
        quantidadeField.setValue(new BigDecimal("10"));

        JmixBigDecimalField valorUnitarioField = UiTestUtils.getComponent(view, "valorUnitarioField");
        valorUnitarioField.setValue(new BigDecimal("10"));

        // nenhum Salvar clicado até aqui — é isso que o preview ao vivo precisa cobrir
        TypedTextField<String> cstField = UiTestUtils.getComponent(view, "cstField");
        assertThat(cstField.getValue()).isEqualTo("10");

        JmixBigDecimalField baseIcmsField = UiTestUtils.getComponent(view, "baseIcmsField");
        assertThat(baseIcmsField.getValue()).isEqualByComparingTo("100.00");

        JmixBigDecimalField aliqIcmsField = UiTestUtils.getComponent(view, "aliqIcmsField");
        assertThat(aliqIcmsField.getValue()).isEqualByComparingTo("18.00");

        JmixBigDecimalField valorIcmsField = UiTestUtils.getComponent(view, "valorIcmsField");
        assertThat(valorIcmsField.getValue()).isEqualByComparingTo("18.00");
    }
}
