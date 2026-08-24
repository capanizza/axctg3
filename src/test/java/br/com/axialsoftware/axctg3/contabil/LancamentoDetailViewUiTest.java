package br.com.axialsoftware.axctg3.contabil;

import br.com.axialsoftware.axctg3.Axctg3Application;
import br.com.axialsoftware.axctg3.entity.User;
import br.com.axialsoftware.axctg3.entity.contabil.ContaContabil;
import br.com.axialsoftware.axctg3.entity.contabil.Lancamento;
import br.com.axialsoftware.axctg3.entity.enums.CodNat;
import br.com.axialsoftware.axctg3.view.contabil.lancamento.LancamentoDetailView;
import io.jmix.core.DataManager;
import io.jmix.core.SaveContext;
import io.jmix.core.security.CurrentAuthentication;
import io.jmix.data.PersistenceHints;
import io.jmix.flowui.ViewNavigators;
import io.jmix.flowui.component.textfield.JmixIntegerField;
import io.jmix.flowui.component.textfield.JmixBigDecimalField;
import io.jmix.flowui.component.valuepicker.EntityPicker;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cobre o render do lancamento-detail-view.xml depois da migração de formLayout pro
 * padrão hbox/vbox do projeto (ver conversa 2026-08-24) — compileJava e clean test não
 * abrem a tela, então nenhum dos dois pegaria um id de componente errado ou um
 * msg://... não resolvido.
 */
@UiTest
@SpringBootTest(classes = {Axctg3Application.class, FlowuiTestAssistConfiguration.class})
@ActiveProfiles("test")
class LancamentoDetailViewUiTest {

    private static final int COD_EMPRESA = 9003;
    private static final int ANO = 2097;
    private static final int MES = 7;

    @Autowired
    DataManager dataManager;
    @Autowired
    ViewNavigators viewNavigators;
    @Autowired
    CurrentAuthentication currentAuthentication;

    private ContaContabil devedora;
    private ContaContabil credora;
    private Lancamento lancamento;

    @BeforeEach
    void setUp() {
        User admin = (User) currentAuthentication.getUser();
        admin.setCodEmpresa(COD_EMPRESA);
        admin.setAnoContabil(ANO);
        admin.setMesContabil(MES);

        limparDados();

        devedora = criarConta("9001", "Caixa de teste");
        credora = criarConta("9002", "Receita de teste");

        lancamento = dataManager.create(Lancamento.class);
        lancamento.setContaDevedora(devedora);
        lancamento.setContaCredora(credora);
        lancamento.setValor(new BigDecimal("150.00"));
        lancamento.setDia(5);
        lancamento.setOrigem("teste");
        lancamento.setComplementoHistorico("lançamento de teste");
        lancamento = dataManager.save(lancamento);
    }

    @AfterEach
    void tearDown() {
        limparDados();
    }

    @Test
    void detailAbreComOsCamposCarregados() {
        viewNavigators.detailView(UiTestUtils.getCurrentView(), Lancamento.class)
                .editEntity(lancamento)
                .withViewClass(LancamentoDetailView.class)
                .navigate();
        LancamentoDetailView view = UiTestUtils.getCurrentView();

        JmixIntegerField numeroField = UiTestUtils.getComponent(view, "numeroField");
        assertThat(numeroField.getValue()).isEqualTo(lancamento.getNumero());

        JmixBigDecimalField valorField = UiTestUtils.getComponent(view, "valorField");
        assertThat(valorField.getValue()).isEqualByComparingTo("150.00");

        EntityPicker<ContaContabil> contaDevedoraField = UiTestUtils.getComponent(view, "contaDevedoraField");
        assertThat(contaDevedoraField.getValue()).isEqualTo(devedora);

        EntityPicker<ContaContabil> contaCredoraField = UiTestUtils.getComponent(view, "contaCredoraField");
        assertThat(contaCredoraField.getValue()).isEqualTo(credora);
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

    private void limparDados() {
        apagar(carregar(Lancamento.class, "select e from Lancamento e where e.codEmpresa = :codEmpresa"));
        apagar(carregar(ContaContabil.class, "select e from ContaContabil e where e.codEmpresa = :codEmpresa"));
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
