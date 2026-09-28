package br.com.axialsoftware.axctg3.view.financeiro.tituloreceber;

import br.com.axialsoftware.axctg3.entity.financeiro.RemessaBanco;
import br.com.axialsoftware.axctg3.service.UtilGeralService;
import br.com.axialsoftware.axctg3.view.main.MainView;

import com.vaadin.flow.data.renderer.Renderer;
import com.vaadin.flow.data.renderer.TextRenderer;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.Dialogs;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.download.DownloadFormat;
import io.jmix.flowui.download.Downloader;
import io.jmix.flowui.kit.action.ActionPerformedEvent;
import io.jmix.flowui.model.CollectionLoader;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Histórico de {@link RemessaBanco} — remessas bancárias já geradas (via
 * {@code TituloReceberListView.gerarRemessaAction}). Sem ação de criar/remover: uma
 * remessa só existe depois de gerada de verdade, mesmo raciocínio de
 * {@code NfeInutilizacao.list}. "Baixar arquivo" entrega de novo a cópia guardada no
 * FileStorage ({@link RemessaBanco#getArquivo()}) — remessas geradas antes de 2026-09-28
 * (quando o arquivo ia pra uma pasta do servidor) não têm cópia.
 */
@Route(value = "remessa-bancos", layout = MainView.class)
@ViewController(id = "RemessaBanco.list")
@ViewDescriptor(path = "remessa-banco-list-view.xml")
@LookupComponent("remessaBancosDataGrid")
@DialogMode(width = "56em")
public class RemessaBancoListView extends StandardListView<RemessaBanco> {

    @ViewComponent
    private CollectionLoader<RemessaBanco> remessaBancosDl;
    @ViewComponent
    private DataGrid<RemessaBanco> remessaBancosDataGrid;
    @Autowired
    private UtilGeralService utilGeralService;
    @Autowired
    private Downloader downloader;
    @Autowired
    private Dialogs dialogs;

    @Subscribe
    public void onBeforeShow(final BeforeShowEvent event) {
        remessaBancosDl.setParameter("codEmpresa", utilGeralService.getCodEmpresa());
        remessaBancosDl.load();
    }

    @Supply(to = "remessaBancosDataGrid.arquivo", subject = "renderer")
    private Renderer<RemessaBanco> remessaBancosDataGridArquivoRenderer() {
        return new TextRenderer<>(remessaBanco ->
                remessaBanco.getArquivo() == null ? "" : remessaBanco.getArquivo().getFileName());
    }

    @Subscribe("remessaBancosDataGrid.baixarArquivoAction")
    public void onRemessaBancosDataGridBaixarArquivoAction(final ActionPerformedEvent event) {
        RemessaBanco remessaBanco = remessaBancosDataGrid.getSingleSelectedItem();
        if (remessaBanco == null) {
            return;
        }
        if (remessaBanco.getArquivo() == null) {
            dialogs.createMessageDialog()
                    .withHeader("Remessa bancária")
                    .withText("A remessa nº " + remessaBanco.getNumRemessa()
                            + " foi gerada antes de o arquivo passar a ser guardado no sistema — não há cópia pra baixar.")
                    .open();
            return;
        }
        downloader.download(remessaBanco.getArquivo(), DownloadFormat.OCTET_STREAM);
    }
}
