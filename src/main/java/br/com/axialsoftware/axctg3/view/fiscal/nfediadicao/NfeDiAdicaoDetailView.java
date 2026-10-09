package br.com.axialsoftware.axctg3.view.fiscal.nfediadicao;

import br.com.axialsoftware.axctg3.entity.fiscal.NfeDiAdicao;
import br.com.axialsoftware.axctg3.view.main.MainView;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.kit.component.button.JmixButton;
import io.jmix.flowui.view.*;

/**
 * Adição de uma DI — aberta em diálogo a partir de {@code NfeDi.detail}, dentro do contexto
 * de dados da NFe digitada (composição Nfe → NfeItem → NfeDi → NfeDiAdicao). Somente leitura
 * quando a NFe já foi emitida; aí o botão de salvar some (ver NfeDetailView).
 */
@Route(value = "nfe-di-adicoes/:id", layout = MainView.class)
@ViewController(id = "NfeDiAdicao.detail")
@ViewDescriptor(path = "nfe-di-adicao-detail-view.xml")
@EditedEntityContainer("nfeDiAdicaoDc")
@DialogMode(width = "60%")
public class NfeDiAdicaoDetailView extends StandardDetailView<NfeDiAdicao> {

    @ViewComponent
    private JmixButton saveAndCloseButton;

    @Subscribe
    public void onReady(final ReadyEvent event) {
        saveAndCloseButton.setVisible(!isReadOnly());
    }
}
