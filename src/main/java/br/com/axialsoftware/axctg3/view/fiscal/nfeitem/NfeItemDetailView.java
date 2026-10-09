package br.com.axialsoftware.axctg3.view.fiscal.nfeitem;

import br.com.axialsoftware.axctg3.entity.fiscal.Nfe;
import br.com.axialsoftware.axctg3.entity.fiscal.NfeItem;
import br.com.axialsoftware.axctg3.view.main.MainView;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.kit.component.button.JmixButton;
import io.jmix.flowui.view.*;

import java.util.Objects;

/**
 * Item da NFe, sempre em diálogo a partir de {@code Nfe.detail}. Somente leitura nas notas
 * emitidas/importadas (aberto por {@code list_read}); editável no rascunho de uma NFe
 * digitada, gravando no contexto de dados da nota (só vai pro banco quando a nota é salva).
 */
@Route(value = "nfe-itens/:id", layout = MainView.class)
@ViewController(id = "NfeItem.detail")
@ViewDescriptor(path = "nfe-item-detail-view.xml")
@EditedEntityContainer("nfeItemDc")
@DialogMode(width = "80%")
public class NfeItemDetailView extends StandardDetailView<NfeItem> {

    @ViewComponent
    private JmixButton saveAndCloseButton;

    // nItem do item novo: o próximo depois do maior já usado na nota ("Recalcular totais"
    // renumera 1..n de qualquer forma, antes da transmissão)
    @Subscribe
    public void onInitEntity(final InitEntityEvent<NfeItem> event) {
        NfeItem item = event.getEntity();
        Nfe nfe = item.getNfe();
        int maior = nfe == null || nfe.getItens() == null ? 0 : nfe.getItens().stream()
                .map(NfeItem::getItem)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .max()
                .orElse(0);
        item.setItem(maior + 1);
        item.setCodEan("SEM GTIN");
        item.setCodEanTrib("SEM GTIN");
        item.setIndTot(1);
    }

    @Subscribe
    public void onReady(final ReadyEvent event) {
        saveAndCloseButton.setVisible(!isReadOnly());
    }
}
