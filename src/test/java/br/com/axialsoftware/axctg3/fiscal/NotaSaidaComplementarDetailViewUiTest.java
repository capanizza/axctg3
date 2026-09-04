package br.com.axialsoftware.axctg3.fiscal;

import br.com.axialsoftware.axctg3.Axctg3Application;
import br.com.axialsoftware.axctg3.entity.cadastros.Parceiro;
import br.com.axialsoftware.axctg3.entity.fiscal.NaturezaOperacao;
import br.com.axialsoftware.axctg3.entity.fiscal.NotaSaida;
import br.com.axialsoftware.axctg3.entity.tabelas.ClassTrib;
import br.com.axialsoftware.axctg3.test_support.AuthenticatedAsAdmin;
import br.com.axialsoftware.axctg3.view.fiscal.notasaida.NotaSaidaComplementarDetailView;
import io.jmix.core.DataManager;
import io.jmix.flowui.ViewNavigators;
import io.jmix.flowui.component.datepicker.TypedDatePicker;
import io.jmix.flowui.component.textfield.JmixBigDecimalField;
import io.jmix.flowui.component.textfield.JmixIntegerField;
import io.jmix.flowui.component.valuepicker.EntityPicker;
import io.jmix.flowui.testassist.FlowuiTestAssistConfiguration;
import io.jmix.flowui.testassist.UiTest;
import io.jmix.flowui.testassist.UiTestUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cobre o render de nota-saida-complementar-detail-view.xml (tela dedicada de entrada
 * de NFe complementar, ver conversa 2026-09-04) — compileJava e clean test não abrem a
 * tela, então nenhum dos dois pegaria um id de componente errado, um readOnly esquecido
 * ou um msg://... não resolvido. Não cobre o botão "Emitir NFe complementar" de
 * NotaSaidaListView (abre a tela via {@code DialogWindows.detail(...).withInitializer(...)},
 * fora do escopo deste teste) — só o descritor da tela em si, aberto em modo de criação
 * via {@code ViewNavigators.detailView(...).newEntity()} (que não tem {@code
 * withInitializer}, só {@code DialogWindows.detail(...)} tem); os valores que a produção
 * preencheria pelo initializer são setados direto nos componentes depois do navigate,
 * igual {@code LancamentoDetailViewSalvarTest}.
 */
@UiTest
@SpringBootTest(classes = {Axctg3Application.class, FlowuiTestAssistConfiguration.class})
@ActiveProfiles("test")
@ExtendWith(AuthenticatedAsAdmin.class)
class NotaSaidaComplementarDetailViewUiTest {

    private static final int COD_EMPRESA = 9111;

    @Autowired
    private DataManager dataManager;
    @Autowired
    private ViewNavigators viewNavigators;

    private Parceiro parceiro;
    private NaturezaOperacao natureza;
    private ClassTrib classTrib;

    @AfterEach
    void tearDown() {
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
        if (classTrib != null) {
            dataManager.load(ClassTrib.class)
                    .id(classTrib.getId())
                    .optional()
                    .ifPresent(dataManager::remove);
        }
    }

    @Test
    void naturezaClassTribParceiroVemTravadosEDataEmissaoEValoresPermanecemEditaveis() {
        parceiro = criarParceiro();
        natureza = criarNatureza();
        classTrib = criarClassTrib();

        viewNavigators.detailView(UiTestUtils.getCurrentView(), NotaSaida.class)
                .withViewClass(NotaSaidaComplementarDetailView.class)
                .newEntity()
                .navigate();
        NotaSaidaComplementarDetailView view = UiTestUtils.getCurrentView();

        // número: readOnly, em branco até salvar (Sequence por empresa, ver NotaSaidaEventListener)
        JmixIntegerField numeroField = UiTestUtils.getComponent(view, "numeroField");
        assertThat(numeroField.isReadOnly()).isTrue();
        assertThat(numeroField.getValue()).isNull();

        // data de emissão: único campo do cabeçalho que continua editável — StandardDetailView
        // já inicializa uma entidade nova com os defaults declarados nela (NotaSaida não tem
        // default de dataEmissao, então parte de null até o operador escolher)
        TypedDatePicker<LocalDate> dataEmissaoField = UiTestUtils.getComponent(view, "dataEmissaoField");
        assertThat(dataEmissaoField.isReadOnly()).isFalse();
        LocalDate hoje = LocalDate.now();
        dataEmissaoField.setValue(hoje);
        assertThat(dataEmissaoField.getValue()).isEqualTo(hoje);

        // natureza/classTrib/cliente: travados na descrição da tela (readOnly="true" no XML);
        // em produção quem preenche o valor é o initializer de
        // NotaSaidaListView.onNotaSaidasDataGridEmitirNfeComplementarAction (via
        // DialogWindows, que aceita withInitializer — ViewNavigators.detailView não aceita,
        // por isso o valor é setado direto no componente aqui, só pra provar que o binding
        // aceita o valor mesmo com readOnly="true")
        EntityPicker<NaturezaOperacao> naturezaField = UiTestUtils.getComponent(view, "naturezaField");
        assertThat(naturezaField.isReadOnly()).isTrue();
        naturezaField.setValue(natureza);
        assertThat(naturezaField.getValue()).isEqualTo(natureza);

        EntityPicker<ClassTrib> classTribField = UiTestUtils.getComponent(view, "classTribField");
        assertThat(classTribField.isReadOnly()).isTrue();
        classTribField.setValue(classTrib);
        assertThat(classTribField.getValue()).isEqualTo(classTrib);

        EntityPicker<Parceiro> parceiroField = UiTestUtils.getComponent(view, "parceiroField");
        assertThat(parceiroField.isReadOnly()).isTrue();
        parceiroField.setValue(parceiro);
        assertThat(parceiroField.getValue()).isEqualTo(parceiro);

        // valores: todos editáveis, é o que o operador realmente digita nesta tela
        JmixBigDecimalField valorMercadoriaField = UiTestUtils.getComponent(view, "valorMercadoriaField");
        assertThat(valorMercadoriaField.isReadOnly()).isFalse();
        valorMercadoriaField.setValue(new BigDecimal("100.00"));

        JmixBigDecimalField baseIcmsField = UiTestUtils.getComponent(view, "baseIcmsField");
        assertThat(baseIcmsField.isReadOnly()).isFalse();
        JmixBigDecimalField valorIcmsField = UiTestUtils.getComponent(view, "valorIcmsField");
        assertThat(valorIcmsField.isReadOnly()).isFalse();
        JmixBigDecimalField baseStField = UiTestUtils.getComponent(view, "baseStField");
        assertThat(baseStField.isReadOnly()).isFalse();
        JmixBigDecimalField valorStField = UiTestUtils.getComponent(view, "valorStField");
        assertThat(valorStField.isReadOnly()).isFalse();
        JmixBigDecimalField baseIpiField = UiTestUtils.getComponent(view, "baseIpiField");
        assertThat(baseIpiField.isReadOnly()).isFalse();
        JmixBigDecimalField valorIpiField = UiTestUtils.getComponent(view, "valorIpiField");
        assertThat(valorIpiField.isReadOnly()).isFalse();
        JmixBigDecimalField freteField = UiTestUtils.getComponent(view, "freteField");
        assertThat(freteField.isReadOnly()).isFalse();
        JmixBigDecimalField seguroField = UiTestUtils.getComponent(view, "seguroField");
        assertThat(seguroField.isReadOnly()).isFalse();
        JmixBigDecimalField descontoField = UiTestUtils.getComponent(view, "descontoField");
        assertThat(descontoField.isReadOnly()).isFalse();
        JmixBigDecimalField despesasField = UiTestUtils.getComponent(view, "despesasField");
        assertThat(despesasField.isReadOnly()).isFalse();

        assertThat(valorMercadoriaField.getValue()).isEqualByComparingTo("100.00");
    }

    private Parceiro criarParceiro() {
        Parceiro p = dataManager.create(Parceiro.class);
        p.setCodigo(1L);
        p.setCodEmpresa(COD_EMPRESA);
        p.setNome("Cliente de Teste");
        p.setApelido("Cliente Teste");
        p.setCnpj("12345678000190");
        return dataManager.save(p);
    }

    private NaturezaOperacao criarNatureza() {
        NaturezaOperacao n = dataManager.create(NaturezaOperacao.class);
        n.setCodigo(1);
        n.setCodEmpresa(COD_EMPRESA);
        n.setNome("Venda de teste");
        n.setCfop(5102);
        return dataManager.save(n);
    }

    private ClassTrib criarClassTrib() {
        ClassTrib c = dataManager.create(ClassTrib.class);
        c.setCodigo(9990101);
        c.setCst(1);
        c.setDescricao("ClassTrib de teste");
        c.setTipoAliquota("Padrão");
        c.setNomenclatura("Teste");
        c.setDescricaoTratamentoTributario("Teste");
        return dataManager.save(c);
    }
}
