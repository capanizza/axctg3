package br.com.axialsoftware.axctg3.view.fiscal.nfe;

import br.com.axialsoftware.axctg3.entity.fiscal.Nfe;
import br.com.axialsoftware.axctg3.view.main.MainView;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.view.*;

// Nfe é o espelho do XML autorizado pela SEFAZ (ou importado de terceiros) — não existe
// cenário de negócio em que o usuário deva editar esses dados pela UI, então a tela inteira
// (todas as abas) é travada em modo somente leitura via ReadOnlyAwareView.setReadOnly, o
// mecanismo nativo do Jmix (propaga pra todo componente da view, inclusive as ações
// list_create/list_edit/list_remove dos dataGrids filhos de itens/duplicatas/pagamentos/
// volumes, que carregam @AdjustWhenViewReadOnly). O botão de salvar foi removido do XML
// porque essa anotação não cobre detail_saveClose — sem isso ele ficaria visível e ativo
// sem nunca ter o que salvar.
@Route(value = "nfes/:id", layout = MainView.class)
@ViewController(id = "Nfe.detail")
@ViewDescriptor(path = "nfe-detail-view.xml")
@EditedEntityContainer("nfeDc")
public class NfeDetailView extends StandardDetailView<Nfe> {

    @Subscribe
    public void onInit(final InitEvent event) {
        setReadOnly(true);
    }
}
