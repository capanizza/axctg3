package br.com.axialsoftware.axctg3.fiscal;

import br.com.axialsoftware.axctg3.Axctg3Application;
import br.com.axialsoftware.axctg3.entity.cadastros.Parceiro;
import br.com.axialsoftware.axctg3.entity.fiscal.ItemNotaSaida;
import br.com.axialsoftware.axctg3.entity.fiscal.NaturezaOperacao;
import br.com.axialsoftware.axctg3.entity.fiscal.NotaSaida;
import br.com.axialsoftware.axctg3.entity.fiscal.Produto;
import br.com.axialsoftware.axctg3.entity.tabelas.ClassTrib;
import br.com.axialsoftware.axctg3.entity.tabelas.Cst;
import br.com.axialsoftware.axctg3.view.fiscal.itemnotasaida.ItemNotaSaidaDetailView;
import io.jmix.core.DataManager;
import io.jmix.flowui.DialogWindows;
import io.jmix.flowui.component.textfield.JmixBigDecimalField;
import io.jmix.flowui.component.textfield.TypedTextField;
import io.jmix.flowui.component.valuepicker.EntityPicker;
import io.jmix.flowui.testassist.FlowuiTestAssistConfiguration;
import io.jmix.flowui.testassist.UiTest;
import io.jmix.flowui.testassist.UiTestUtils;
import io.jmix.flowui.view.DialogWindow;
import io.jmix.flowui.view.View;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cobre o preview ao vivo de CST/ICMS decidido em 2026-09-14: o usuário reclamou que a
 * base de ICMS ficava em branco na tela do item enquanto digitava (só era calculada de
 * verdade no save, via {@code ItemNotaSaidaEventListener}). {@code
 * ItemNotaSaidaDetailView.onItemNotaSaidaDcItemPropertyChange} resolve isso escutando
 * {@code itemNotaSaidaDc} — este teste abre o item NOVO (sem salvar) via
 * {@code DialogWindows.detail(...).newEntity().withInitializer(...)} (única forma de
 * pré-popular {@code notaSaida} num item novo — não existe campo pra isso na tela em si,
 * quem preenche é a composição da NotaSaida; {@code ViewNavigators.detailView(...)} não
 * tem {@code withInitializer}, só {@code DialogWindows} tem) e simula escolher produto +
 * digitar quantidade/valorUnitario, conferindo que os campos read-only já mostram o
 * valor calculado antes de qualquer Salvar — inclusive que a alíquota vem da Natureza,
 * não do Produto (corrigido no mesmo dia).
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
    private DialogWindows dialogWindows;

    @AfterEach
    void tearDown() {
        dataManager.load(NotaSaida.class)
                .query("select e from NotaSaida e where e.codEmpresa = :codEmpresa")
                .parameter("codEmpresa", COD_EMPRESA)
                .list()
                .forEach(dataManager::remove);
        dataManager.load(NaturezaOperacao.class)
                .query("select e from NaturezaOperacao e where e.codEmpresa = :codEmpresa")
                .parameter("codEmpresa", COD_EMPRESA)
                .list()
                .forEach(dataManager::remove);
        dataManager.load(Parceiro.class)
                .query("select e from Parceiro e where e.codEmpresa = :codEmpresa")
                .parameter("codEmpresa", COD_EMPRESA)
                .list()
                .forEach(dataManager::remove);
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
    void previewDoIcmsApareceAntesDeSalvarUsandoAliquotaDaNatureza() {
        ClassTrib classTrib = dataManager.create(ClassTrib.class);
        classTrib.setCodigo(CLASS_TRIB_CODIGO_TESTE);
        classTrib.setCst(1);
        classTrib.setDescricao("ClassTrib de teste");
        classTrib.setTipoAliquota("Padrão");
        classTrib.setNomenclatura("Teste");
        classTrib.setDescricaoTratamentoTributario("Teste");
        classTrib = dataManager.save(classTrib);

        NaturezaOperacao natureza = dataManager.create(NaturezaOperacao.class);
        natureza.setCodigo(1);
        natureza.setCodEmpresa(COD_EMPRESA);
        natureza.setNome("Venda de teste");
        natureza.setCfop(5102);
        natureza.setCst(carregarCst("00")); // rasa: delega o CST pro produto
        natureza.setAliqIcms(new BigDecimal("18.00"));
        natureza = dataManager.save(natureza);

        Produto produto = dataManager.create(Produto.class);
        produto.setCodigo(1);
        produto.setCodEmpresa(COD_EMPRESA);
        produto.setDescricao("Produto de teste");
        produto.setApelido("Teste");
        produto.setClassTrib(classTrib);
        produto.setCst(carregarCst("10"));
        produto.setAliqIcms(new BigDecimal("99.00")); // não pode vazar pro cálculo
        produto = dataManager.save(produto);

        Parceiro parceiro = dataManager.create(Parceiro.class);
        parceiro.setCodigo(1L);
        parceiro.setCodEmpresa(COD_EMPRESA);
        parceiro.setNome("Cliente de Teste");
        parceiro.setApelido("Cliente Teste");
        parceiro.setCnpj("12345678000190");
        parceiro = dataManager.save(parceiro);

        NotaSaida notaSaida = dataManager.create(NotaSaida.class);
        notaSaida.setCodEmpresa(COD_EMPRESA);
        notaSaida.setDataEmissao(LocalDate.now());
        notaSaida.setDataSaida(LocalDate.now());
        notaSaida.setEspecie("NF");
        notaSaida.setSerie("1");
        notaSaida.setParceiro(parceiro);
        notaSaida.setNatureza(natureza);
        notaSaida = dataManager.save(notaSaida);

        View<?> origin = UiTestUtils.getCurrentView();
        Produto produtoFinal = produto;
        NotaSaida notaSaidaFinal = notaSaida;
        DialogWindow<ItemNotaSaidaDetailView> dialogWindow = dialogWindows.detail(origin, ItemNotaSaida.class)
                .withViewClass(ItemNotaSaidaDetailView.class)
                .newEntity()
                .withInitializer(item -> item.setNotaSaida(notaSaidaFinal))
                .open();
        ItemNotaSaidaDetailView view = dialogWindow.getView();

        EntityPicker<Produto> produtoField = UiTestUtils.getComponent(view, "produtoField");
        produtoField.setValue(produtoFinal);

        JmixBigDecimalField quantidadeField = UiTestUtils.getComponent(view, "quantidadeField");
        quantidadeField.setValue(new BigDecimal("10"));

        JmixBigDecimalField valorUnitarioField = UiTestUtils.getComponent(view, "valorUnitarioField");
        valorUnitarioField.setValue(new BigDecimal("10"));

        // nenhum Salvar clicado até aqui — é isso que o preview ao vivo precisa cobrir
        TypedTextField<String> cstField = UiTestUtils.getComponent(view, "cstField");
        assertThat(cstField.getValue()).isEqualTo("10"); // CST vem do produto (natureza é rasa)

        JmixBigDecimalField baseIcmsField = UiTestUtils.getComponent(view, "baseIcmsField");
        assertThat(baseIcmsField.getValue()).isEqualByComparingTo("100.00");

        JmixBigDecimalField aliqIcmsField = UiTestUtils.getComponent(view, "aliqIcmsField");
        assertThat(aliqIcmsField.getValue()).isEqualByComparingTo("18.00"); // alíquota vem da natureza

        JmixBigDecimalField valorIcmsField = UiTestUtils.getComponent(view, "valorIcmsField");
        assertThat(valorIcmsField.getValue()).isEqualByComparingTo("18.00");
    }
}
