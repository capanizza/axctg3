package br.com.axialsoftware.axctg3.view.fiscal.nfe;

import br.com.axialsoftware.axctg3.entity.fiscal.Nfe;
import br.com.axialsoftware.axctg3.entity.fiscal.NfeCartaCorrecao;
import br.com.axialsoftware.axctg3.service.fiscal.NfeCartaCorrecaoComprovanteService;
import br.com.axialsoftware.axctg3.view.main.MainView;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.Dialogs;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.kit.action.ActionPerformedEvent;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;

// Nfe é o espelho do XML autorizado pela SEFAZ (ou importado de terceiros) — não existe
// cenário de negócio em que o usuário deva editar esses dados pela UI, então a tela inteira
// (todas as abas) é travada em modo somente leitura via ReadOnlyAwareView.setReadOnly, o
// mecanismo nativo do Jmix (propaga pra todo componente da view, inclusive as ações
// list_create/list_edit/list_remove dos dataGrids filhos de itens/duplicatas/pagamentos/
// volumes, que carregam @AdjustWhenViewReadOnly). O botão de salvar foi removido do XML
// porque essa anotação não cobre detail_saveClose — sem isso ele ficaria visível e ativo
// sem nunca ter o que salvar.
//
// A aba "Carta de Correção" foge um pouco desse espírito só-histórico: tem um botão
// "Imprimir" que reemite o comprovante de uma CC-e já registrada (NfeCartaCorrecaoComprovanteService).
// Isso não é uma ação mutadora — só reimpressão — e o botão é uma ação "bare" (sem type=
// list_*), que fica fora do alcance de setReadOnly/@AdjustWhenViewReadOnly (mesma observação
// já registrada acima pro botão "Visualizar" da aba Itens). A ação de EMITIR uma CC-e nova
// fica de fora daqui de propósito — mora em NfeListView/NotaSaidaListView, junto das outras
// ações contra a SEFAZ (cancelar, consultar, inutilizar), mantendo a separação "lista = ações,
// detalhe = espelho somente-leitura".
@Route(value = "nfes/:id", layout = MainView.class)
@ViewController(id = "Nfe.detail")
@ViewDescriptor(path = "nfe-detail-view.xml")
@EditedEntityContainer("nfeDc")
public class NfeDetailView extends StandardDetailView<Nfe> {

    @ViewComponent
    private DataGrid<NfeCartaCorrecao> nfeCartasCorrecaoDataGrid;
    @Autowired
    private NfeCartaCorrecaoComprovanteService nfeCartaCorrecaoComprovanteService;
    @Autowired
    private Dialogs dialogs;
    @ViewComponent
    private MessageBundle messageBundle;

    @Subscribe
    public void onInit(final InitEvent event) {
        setReadOnly(true);
    }

    @Subscribe("nfeCartasCorrecaoDataGrid.imprimirCceAction")
    public void onNfeCartasCorrecaoDataGridImprimirCceAction(final ActionPerformedEvent event) {
        NfeCartaCorrecao selecionada = nfeCartasCorrecaoDataGrid.getSingleSelectedItem();
        if (selecionada == null) {
            dialogs.createMessageDialog()
                    .withHeader(messageBundle.getMessage("nfeDetailView.imprimirCceAction.text"))
                    .withText(messageBundle.getMessage("nfeDetailView.imprimirCce.naoSelecionado"))
                    .open();
            return;
        }
        nfeCartaCorrecaoComprovanteService.imprimir(selecionada.getId());
    }
}
