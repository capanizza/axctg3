package br.com.axialsoftware.axctg3.view.main;

import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.server.VaadinService;
import com.vaadin.flow.server.VaadinSession;
import io.jmix.core.Messages;
import io.jmix.flowui.Dialogs;
import io.jmix.flowui.action.DialogAction;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Empresa e período (User.codEmpresa/anoContabil/...) ficam no usuário da sessão, que é um só
 * para todas as abas do navegador. Trocar numa aba faria as outras mostrarem uma empresa no
 * cabeçalho e gravarem em outra — por isso a troca só é permitida com uma única aba aberta.
 */
@Component
public class OutrasAbasSupport {

    // Marca a UI já mandada embora: o aviso de fechamento do navegador (unload beacon) pode não
    // chegar, e sem a marca ela seguiria contando até o heartbeat expirar (minutos).
    private static final String ABA_ENCERRADA = "axctg3.abaEncerrada";

    private final Dialogs dialogs;
    private final Messages messages;

    public OutrasAbasSupport(Dialogs dialogs, Messages messages) {
        this.dialogs = dialogs;
        this.messages = messages;
    }

    /** Roda {@code acao} se esta for a única aba; senão pergunta se encerra as outras antes. */
    public void executarSemOutrasAbas(Runnable acao) {
        List<UI> outras = outrasAbas();
        if (outras.isEmpty()) {
            acao.run();
            return;
        }
        dialogs.createOptionDialog()
                .withHeader(messages.getMessage(getClass(), "outrasAbas.header"))
                .withText(messages.formatMessage(getClass(), "outrasAbas.text", outras.size()))
                .withActions(
                        new DialogAction(DialogAction.Type.OK)
                                .withText(messages.getMessage(getClass(), "outrasAbas.fechar"))
                                .withHandler(e -> {
                                    encerrar(outrasAbas());
                                    acao.run();
                                }),
                        new DialogAction(DialogAction.Type.CANCEL)
                )
                .open();
    }

    /** Aviso ao entrar na tela de troca; não faz nada se esta for a única aba. */
    public void avisarSeHouverOutrasAbas() {
        executarSemOutrasAbas(() -> {
        });
    }

    private List<UI> outrasAbas() {
        UI atual = UI.getCurrent();
        return VaadinSession.getCurrent().getUIs().stream()
                .filter(ui -> ui != atual)
                .filter(ui -> !ui.isClosing())
                .filter(ui -> ComponentUtil.getData(ui, ABA_ENCERRADA) == null)
                .toList();
    }

    // Manda cada aba para uma página estática: sair da página dispara o unload beacon, e o
    // Vaadin fecha aquela UI na hora. Chega pelo @Push do Axctg3Application, sem clique lá.
    private void encerrar(List<UI> abas) {
        String pagina = VaadinService.getCurrentRequest().getContextPath() + "/public/aba-encerrada.html";
        for (UI ui : abas) {
            ComponentUtil.setData(ui, ABA_ENCERRADA, Boolean.TRUE);
            ui.access(() -> ui.getPage().executeJs("window.location.replace($0)", pagina));
        }
    }
}
