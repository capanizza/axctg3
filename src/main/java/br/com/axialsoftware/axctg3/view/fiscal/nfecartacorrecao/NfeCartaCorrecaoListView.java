package br.com.axialsoftware.axctg3.view.fiscal.nfecartacorrecao;

import br.com.axialsoftware.axctg3.entity.fiscal.NfeCartaCorrecao;
import br.com.axialsoftware.axctg3.service.fiscal.NfeCartaCorrecaoComprovanteService;
import br.com.axialsoftware.axctg3.view.main.MainView;

import com.vaadin.flow.router.Route;
import io.jmix.flowui.Dialogs;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.kit.action.ActionPerformedEvent;
import io.jmix.flowui.model.CollectionLoader;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Histórico de {@link NfeCartaCorrecao} de UMA NFe específica — os mesmos dados já visíveis
 * na aba "Carta de Correção" de {@code NfeDetailView}, mas como atalho de um clique a partir
 * do dropdown de ações de {@code NfeListView}/{@code NotaSaidaListView}, mesmo motivo de
 * {@code NfeInutilizacaoListView} ter item de dropdown próprio em vez de forçar o usuário a
 * caçar a aba certa numa tela de detalhe com muitas abas.
 *
 * <p>{@link #setChave(String)} precisa ser chamado ANTES de {@code open()} — filtra por
 * {@code nfe.chave} (não por id) porque {@code NotaSaidaListView} só tem a chave gravada,
 * mesmo padrão de {@code NfeCancelamentoService.corrigirPorChave}. Aberta via
 * {@code dialogWindows.view(this, NfeCartaCorrecaoListView.class).build()}, configurando o
 * controller com {@code .getView()} antes do {@code .open()}.
 */
@Route(value = "nfe-cartas-correcao", layout = MainView.class)
@ViewController(id = "NfeCartaCorrecao.list")
@ViewDescriptor(path = "nfe-carta-correcao-list-view.xml")
@LookupComponent("nfeCartasCorrecaoDataGrid")
@DialogMode(width = "64em")
public class NfeCartaCorrecaoListView extends StandardListView<NfeCartaCorrecao> {

    @ViewComponent
    private CollectionLoader<NfeCartaCorrecao> nfeCartasCorrecaoDl;
    @ViewComponent
    private DataGrid<NfeCartaCorrecao> nfeCartasCorrecaoDataGrid;
    @ViewComponent
    private MessageBundle messageBundle;
    @Autowired
    private NfeCartaCorrecaoComprovanteService nfeCartaCorrecaoComprovanteService;
    @Autowired
    private Dialogs dialogs;

    private String chave;

    public void setChave(String chave) {
        this.chave = chave;
    }

    @Subscribe
    public void onBeforeShow(final BeforeShowEvent event) {
        nfeCartasCorrecaoDl.setParameter("chave", chave);
        nfeCartasCorrecaoDl.load();
    }

    @Subscribe("nfeCartasCorrecaoDataGrid.imprimirAction")
    public void onNfeCartasCorrecaoDataGridImprimirAction(final ActionPerformedEvent event) {
        NfeCartaCorrecao selecionada = nfeCartasCorrecaoDataGrid.getSingleSelectedItem();
        if (selecionada == null) {
            dialogs.createMessageDialog()
                    .withHeader(messageBundle.getMessage("nfeCartaCorrecaoListView.imprimirAction.text"))
                    .withText(messageBundle.getMessage("nfeCartaCorrecaoListView.imprimir.naoSelecionado"))
                    .open();
            return;
        }
        nfeCartaCorrecaoComprovanteService.imprimir(selecionada.getId());
    }
}
