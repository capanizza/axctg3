package br.com.axialsoftware.axctg3.view.fiscal.nfedi;

import br.com.axialsoftware.axctg3.entity.fiscal.NfeDi;
import br.com.axialsoftware.axctg3.view.main.MainView;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.kit.component.button.JmixButton;
import io.jmix.flowui.view.*;

/**
 * Declaração de Importação de um item da NFe — aberta em diálogo a partir de
 * {@code NfeItem.detail}, com as adições como composição (cada uma no seu diálogo). Somente
 * leitura quando a NFe já foi emitida; aí o botão de salvar some (ver NfeDetailView).
 */
@Route(value = "nfe-dis/:id", layout = MainView.class)
@ViewController(id = "NfeDi.detail")
@ViewDescriptor(path = "nfe-di-detail-view.xml")
@EditedEntityContainer("nfeDiDc")
@DialogMode(width = "70%")
public class NfeDiDetailView extends StandardDetailView<NfeDi> {

    @ViewComponent
    private JmixButton saveAndCloseButton;

    @Subscribe
    public void onReady(final ReadyEvent event) {
        saveAndCloseButton.setVisible(!isReadOnly());
    }
}
