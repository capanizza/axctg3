package br.com.axialsoftware.axctg3.contabil;

import br.com.axialsoftware.axctg3.Axctg3Application;
import br.com.axialsoftware.axctg3.entity.User;
import br.com.axialsoftware.axctg3.entity.contabil.ContaContabil;
import br.com.axialsoftware.axctg3.entity.contabil.Lancamento;
import br.com.axialsoftware.axctg3.entity.enums.CodNat;
import br.com.axialsoftware.axctg3.test_support.AuthenticatedAsAdmin;
import br.com.axialsoftware.axctg3.view.contabil.lancamento.LancamentoDetailView;
import io.jmix.core.DataManager;
import io.jmix.core.SaveContext;
import io.jmix.core.security.CurrentAuthentication;
import io.jmix.data.PersistenceHints;
import io.jmix.flowui.ViewNavigators;
import io.jmix.flowui.component.textfield.JmixBigDecimalField;
import io.jmix.flowui.component.textfield.JmixIntegerField;
import io.jmix.flowui.component.valuepicker.EntityPicker;
import io.jmix.flowui.kit.component.button.JmixButton;
import io.jmix.flowui.testassist.FlowuiTestAssistConfiguration;
import io.jmix.flowui.testassist.UiTest;
import io.jmix.flowui.testassist.UiTestUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cobre a correção de 2026-08-24: numero/ano/mes/codEmpresa/dataLancamento são
 * {@code @NotNull} na entidade mas carimbados pelo {@code LancamentoEventListener} no
 * save (numero via sequence, dataLancamento a partir de dia+período contábil) — não
 * digitados pelo usuário. Antes da correção, deixar esses campos em branco no formulário
 * (o fluxo normal de uso) travava o botão salvar: o Bean Validation por-componente do
 * Jmix (anexado automaticamente a todo campo ligado via {@code property=} a um atributo
 * {@code @NotNull}) rejeitava o valor nulo antes do listener sequer rodar. A correção
 * desvincula esses 5 campos de {@code property=} no XML (viram só exibição) e
 * {@link LancamentoDetailView#onBeforeShow} sincroniza o valor real manualmente — {@code
 * dia}, o único campo realmente digitado pelo usuário, continua vinculado normalmente.
 */
@UiTest
@SpringBootTest(classes = {Axctg3Application.class, FlowuiTestAssistConfiguration.class})
@ActiveProfiles("test")
class LancamentoDetailViewSalvarTest {

    private static final int COD_EMPRESA = 9104;
    private static final int ANO = 2097;
    private static final int MES = 8;

    @Autowired
    DataManager dataManager;
    @Autowired
    ViewNavigators viewNavigators;
    @Autowired
    CurrentAuthentication currentAuthentication;

    private ContaContabil devedora;
    private ContaContabil credora;

    @BeforeEach
    void setUp() {
        User admin = (User) currentAuthentication.getUser();
        admin.setCodEmpresa(COD_EMPRESA);
        admin.setAnoContabil(ANO);
        admin.setMesContabil(MES);

        limpar();

        devedora = criarConta("9001", "Caixa de teste");
        credora = criarConta("9002", "Receita de teste");
    }

    @AfterEach
    void tearDown() {
        limpar();
    }

    @Test
    void salvarComNumeroEDataEmBrancoDeixaOListenerPreencher() {
        viewNavigators.detailView(UiTestUtils.getCurrentView(), Lancamento.class)
                .newEntity()
                .withViewClass(LancamentoDetailView.class)
                .navigate();
        LancamentoDetailView view = UiTestUtils.getCurrentView();

        // numeroField e dataLancamentoField ficam em branco de propósito — só
        // contaDevedora/contaCredora/valor/dia são preenchidos, como um usuário faria
        EntityPicker<ContaContabil> contaDevedoraField = UiTestUtils.getComponent(view, "contaDevedoraField");
        contaDevedoraField.setValue(devedora);
        EntityPicker<ContaContabil> contaCredoraField = UiTestUtils.getComponent(view, "contaCredoraField");
        contaCredoraField.setValue(credora);
        JmixBigDecimalField valorField = UiTestUtils.getComponent(view, "valorField");
        valorField.setValue(new BigDecimal("77.00"));
        JmixIntegerField diaField = UiTestUtils.getComponent(view, "diaField");
        diaField.setValue(15);

        JmixButton saveAndCloseButton = UiTestUtils.getComponent(view, "saveAndCloseButton");
        saveAndCloseButton.click();

        // se o clique acima não navegou de volta pra LancamentoListView, o save foi
        // barrado (validação bloqueou por causa de numero/dataLancamento em branco)
        Object currentView = UiTestUtils.getCurrentView();
        assertThat(currentView).isNotInstanceOf(LancamentoDetailView.class);

        List<Lancamento> salvos = dataManager.load(Lancamento.class)
                .query("select e from Lancamento e where e.codEmpresa = :codEmpresa")
                .parameter("codEmpresa", COD_EMPRESA)
                .list();
        assertThat(salvos).hasSize(1);
        Lancamento salvo = salvos.get(0);
        assertThat(salvo.getNumero()).isNotNull();
        assertThat(salvo.getDataLancamento()).isNotNull();
    }

    private ContaContabil criarConta(String codigo, String nome) {
        ContaContabil conta = dataManager.create(ContaContabil.class);
        conta.setCodigo(codigo);
        conta.setNome(nome);
        conta.setAno(ANO);
        conta.setCodEmpresa(COD_EMPRESA);
        conta.setGrau(1);
        conta.setCodContaSup(codigo);
        conta.setAnalitica(true);
        conta.setCodNat(CodNat.CONTAS_DE_ATIVO);
        return dataManager.save(conta);
    }

    private void limpar() {
        apagar(dataManager.load(Lancamento.class)
                .query("select e from Lancamento e where e.codEmpresa = :codEmpresa")
                .parameter("codEmpresa", COD_EMPRESA)
                .hint(PersistenceHints.SOFT_DELETION, false)
                .list());
        apagar(dataManager.load(ContaContabil.class)
                .query("select e from ContaContabil e where e.codEmpresa = :codEmpresa")
                .parameter("codEmpresa", COD_EMPRESA)
                .hint(PersistenceHints.SOFT_DELETION, false)
                .list());
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
