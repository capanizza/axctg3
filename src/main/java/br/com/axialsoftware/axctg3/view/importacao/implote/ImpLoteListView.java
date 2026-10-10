package br.com.axialsoftware.axctg3.view.importacao.implote;

import br.com.axialsoftware.axctg3.entity.enums.TipoLote;
import br.com.axialsoftware.axctg3.entity.importacao.ImpLote;
import br.com.axialsoftware.axctg3.service.importacao.ImportacaoPlanoContasService;
import br.com.axialsoftware.axctg3.view.main.MainView;

import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.Dialogs;
import io.jmix.flowui.action.DialogAction;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.component.textarea.JmixTextArea;
import io.jmix.flowui.kit.action.ActionPerformedEvent;
import io.jmix.flowui.kit.component.button.JmixButton;
import io.jmix.flowui.model.CollectionContainer;
import io.jmix.flowui.model.CollectionLoader;
import io.jmix.flowui.model.InstanceContainer;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Lotes exportados do sistema legado pelo projeto Axial ({@code exportacao2}). Não há criar
 * nem editar: o lote nasce no exportador. Daqui só se confere (relatório sem gravar nada) e
 * importa (grava, se não houver erro estrutural). O relatório fica no próprio lote e aparece
 * no quadro abaixo da grade.
 * <p>
 * A importação grava na empresa do lote, não na selecionada na sessão: quem importa é a Axial,
 * para empresas de outros grupos.
 */
@Route(value = "imp-lotes", layout = MainView.class)
@ViewController(id = "ImpLote.list")
@ViewDescriptor(path = "imp-lote-list-view.xml")
@DialogMode(width = "64em")
public class ImpLoteListView extends StandardListView<ImpLote> {

    @ViewComponent
    private CollectionLoader<ImpLote> impLotesDl;
    @ViewComponent
    private CollectionContainer<ImpLote> impLotesDc;
    @ViewComponent
    private DataGrid<ImpLote> impLotesDataGrid;
    @ViewComponent
    private JmixTextArea mensagemField;
    @ViewComponent
    private MessageBundle messageBundle;
    @Autowired
    private Dialogs dialogs;
    @Autowired
    private ImportacaoPlanoContasService importacaoPlanoContasService;

    @Subscribe(id = "impLotesDc", target = Target.DATA_CONTAINER)
    public void onImpLotesDcItemChange(final InstanceContainer.ItemChangeEvent<ImpLote> event) {
        ImpLote lote = event.getItem();
        mensagemField.setValue(lote == null || lote.getMensagem() == null ? "" : lote.getMensagem());
    }

    @Subscribe("impLotesDataGrid.conferirAction")
    public void onImpLotesDataGridConferirAction(final ActionPerformedEvent event) {
        ImpLote lote = loteSelecionado();
        if (lote == null) {
            return;
        }
        ImportacaoPlanoContasService.Relatorio relatorio = importacaoPlanoContasService.conferir(lote.getId());
        recarregar(lote);
        dialogs.createMessageDialog()
                .withHeader(messageBundle.getMessage("impLoteListView.conferirAction.text"))
                .withText(messageBundle.formatMessage("impLoteListView.resumo",
                        relatorio.getErros().size(), relatorio.getDivergencias().size(), relatorio.getAvisos().size()))
                .open();
    }

    @Subscribe("impLotesDataGrid.importarAction")
    public void onImpLotesDataGridImportarAction(final ActionPerformedEvent event) {
        ImpLote lote = loteSelecionado();
        if (lote == null) {
            return;
        }
        dialogs.createOptionDialog()
                .withHeader(messageBundle.getMessage("impLoteListView.importarAction.text"))
                .withText(messageBundle.formatMessage("impLoteListView.importar.confirmar",
                        lote.getAno(), lote.getCodEmpresa()))
                .withActions(
                        new DialogAction(DialogAction.Type.YES).withHandler(e -> importar(lote)),
                        new DialogAction(DialogAction.Type.NO))
                .open();
    }

    private void importar(ImpLote lote) {
        ImportacaoPlanoContasService.Relatorio relatorio = importacaoPlanoContasService.importar(lote.getId());
        recarregar(lote);
        String texto = relatorio.getErros().isEmpty()
                ? messageBundle.formatMessage("impLoteListView.importar.ok", relatorio.getImportadas(),
                relatorio.getDivergencias().size(), relatorio.getAvisos().size())
                : messageBundle.formatMessage("impLoteListView.importar.erro", relatorio.getErros().size());
        dialogs.createMessageDialog()
                .withHeader(messageBundle.getMessage("impLoteListView.importarAction.text"))
                .withText(texto)
                .open();
    }

    @Subscribe(id = "atualizarButton", subject = "clickListener")
    public void onAtualizarButtonClick(final ClickEvent<JmixButton> event) {
        impLotesDl.load();
    }

    private ImpLote loteSelecionado() {
        ImpLote lote = impLotesDataGrid.getSingleSelectedItem();
        if (lote == null) {
            dialogs.createMessageDialog()
                    .withHeader(messageBundle.getMessage("impLoteListView.title"))
                    .withText(messageBundle.getMessage("impLoteListView.naoSelecionado"))
                    .open();
            return null;
        }
        if (lote.getTipo() != TipoLote.PLANO_CONTAS) {
            dialogs.createMessageDialog()
                    .withHeader(messageBundle.getMessage("impLoteListView.title"))
                    .withText(messageBundle.getMessage("impLoteListView.tipoNaoSuportado"))
                    .open();
            return null;
        }
        return lote;
    }

    /** Recarrega a grade e reposiciona no lote, para o quadro mostrar o relatório novo. */
    private void recarregar(ImpLote lote) {
        impLotesDl.load();
        ImpLote recarregado = impLotesDc.getItemOrNull(lote.getId());
        if (recarregado != null) {
            impLotesDataGrid.select(recarregado);
        }
    }
}
