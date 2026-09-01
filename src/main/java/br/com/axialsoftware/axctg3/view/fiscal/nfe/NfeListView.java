package br.com.axialsoftware.axctg3.view.fiscal.nfe;

import br.com.axialsoftware.axctg3.entity.cadastros.Empresa;
import br.com.axialsoftware.axctg3.entity.fiscal.Nfe;
import br.com.axialsoftware.axctg3.service.UtilGeralService;
import br.com.axialsoftware.axctg3.service.fiscal.NfeDanfeService;
import br.com.axialsoftware.axctg3.service.fiscal.NfeImportService;
import br.com.axialsoftware.axctg3.service.fiscal.NfeWebserviceClient;
import br.com.axialsoftware.axctg3.view.main.MainView;

import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.router.Route;
import io.jmix.core.Messages;
import io.jmix.flowui.DialogWindows;
import io.jmix.flowui.Dialogs;
import io.jmix.flowui.backgroundtask.BackgroundTask;
import io.jmix.flowui.backgroundtask.TaskLifeCycle;
import io.jmix.flowui.component.UiComponentUtils;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.kit.action.ActionPerformedEvent;
import io.jmix.flowui.kit.component.button.JmixButton;
import io.jmix.flowui.model.CollectionLoader;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Route(value = "nfes", layout = MainView.class)
@ViewController(id = "Nfe.list")
@ViewDescriptor(path = "nfe-list-view.xml")
@LookupComponent("nfesDataGrid")
@DialogMode(width = "64em")
public class NfeListView extends StandardListView<Nfe> {

    /** Folga para a importação inteira, no padrão do import de lançamentos contábeis. */
    private static final long TIMEOUT_IMPORTACAO_MINUTOS = 30;

    @ViewComponent
    private CollectionLoader<Nfe> nfesDl;
    @ViewComponent
    private DataGrid<Nfe> nfesDataGrid;
    @ViewComponent
    private MessageBundle messageBundle;
    @Autowired
    private UtilGeralService utilGeralService;
    @ViewComponent
    private HorizontalLayout buttonsPanel;
    @Autowired
    private DialogWindows dialogWindows;
    @Autowired
    private Dialogs dialogs;
    @Autowired
    private NfeImportService nfeImportService;
    @Autowired
    private NfeDanfeService nfeDanfeService;
    @Autowired
    private NfeWebserviceClient nfeWebserviceClient;
    @Autowired
    private Messages messages;

    @Subscribe
    public void onBeforeShow(final BeforeShowEvent event) {
        nfesDl.setParameter("codEmpresa", utilGeralService.getCodEmpresa());
        nfesDl.load();

        Dialog dialog = UiComponentUtils.findDialog(this);
        buttonsPanel.setVisible(dialog == null);
    }

    @Subscribe(id = "importXmlButton", subject = "clickListener")
    public void onImportXmlButtonClick(final ClickEvent<JmixButton> event) {
        dialogWindows.view(this, NfeImportView.class)
                .withAfterCloseListener(closeEvent -> {
                    if (!closeEvent.closedWith(StandardOutcome.SAVE)) {
                        return;
                    }
                    Map<String, byte[]> arquivosXml = closeEvent.getView().getArquivosXml();
                    if (arquivosXml.isEmpty()) {
                        return;
                    }
                    dialogs.createBackgroundTaskDialog(new ImportarXmlTask(arquivosXml))
                            .withHeader("Importação de NFe")
                            .withText("Importando arquivos XML...")
                            .withTotal(arquivosXml.size())
                            .withShowProgressInPercentage(true)
                            .withCancelAllowed(true)
                            .open();
                })
                .open();
    }

    @Subscribe("nfesDataGrid.emitirDanfeAction")
    public void onNfesDataGridEmitirDanfeAction(final ActionPerformedEvent event) {
        Nfe selecionada = nfesDataGrid.getSingleSelectedItem();
        if (selecionada == null) {
            dialogs.createMessageDialog()
                    .withHeader(messageBundle.getMessage("nfeListView.emitirDanfeAction.text"))
                    .withText(messageBundle.getMessage("nfeListView.emitirDanfe.naoSelecionado"))
                    .open();
            return;
        }
        nfeDanfeService.emitirDanfe(selecionada.getId());
    }

    /*
     * Emitir NFe, Cancelar NFe, Consultar NFe e Inutilizar números de notas ainda não têm
     * service implementado — só EmitirNfe (a partir de NotaSaidaListView), EmitirDanfe e
     * VerificarStatusServico (copiado de EmpresaDetailView) existem hoje. Placeholders no
     * dropDownButton pra já fixar a estrutura do menu; cada um vira handler de verdade
     * quando o service correspondente for implementado.
     */
    @Subscribe("nfesDataGrid.emitirNfeAction")
    public void onNfesDataGridEmitirNfeAction(final ActionPerformedEvent event) {
        mostrarEmDesenvolvimento("nfeListView.emitirNfeAction.text");
    }

    @Subscribe("nfesDataGrid.cancelarNfeAction")
    public void onNfesDataGridCancelarNfeAction(final ActionPerformedEvent event) {
        mostrarEmDesenvolvimento("nfeListView.cancelarNfeAction.text");
    }

    @Subscribe("nfesDataGrid.consultarNfeAction")
    public void onNfesDataGridConsultarNfeAction(final ActionPerformedEvent event) {
        mostrarEmDesenvolvimento("nfeListView.consultarNfeAction.text");
    }

    /** Mesma lógica de {@code EmpresaDetailView.onTestarConexaoSefazButtonClick}. */
    @Subscribe("nfesDataGrid.verificarStatusServicoAction")
    public void onNfesDataGridVerificarStatusServicoAction(final ActionPerformedEvent event) {
        Empresa empresa = utilGeralService.getEmpresa();
        if (empresa.getCrt() == null || empresa.getAmbienteNfe() == null
                || empresa.getCertificadoArquivo() == null || empresa.getCertificadoSenha() == null) {
            dialogs.createMessageDialog()
                    .withHeader(messageBundle.getMessage("nfeListView.verificarStatusServicoAction.text"))
                    .withText(messageBundle.getMessage("nfeListView.verificarStatusServico.semConfig"))
                    .open();
            return;
        }
        try {
            NfeWebserviceClient.Resposta resposta = nfeWebserviceClient.consultarStatusServico(empresa);
            String ambiente = messages.getMessage(empresa.getAmbienteNfe());
            dialogs.createMessageDialog()
                    .withHeader(messageBundle.getMessage("nfeListView.verificarStatusServico.sucesso.header"))
                    .withText(messageBundle.formatMessage("nfeListView.verificarStatusServico.sucesso.text",
                            ambiente, resposta.cStat(), resposta.xMotivo()))
                    .open();
        } catch (Exception e) {
            dialogs.createMessageDialog()
                    .withHeader(messageBundle.getMessage("nfeListView.verificarStatusServico.falha.header"))
                    .withText(messageBundle.formatMessage("nfeListView.verificarStatusServico.falha.text", e.getMessage()))
                    .open();
        }
    }

    @Subscribe("nfesDataGrid.inutilizarNumerosAction")
    public void onNfesDataGridInutilizarNumerosAction(final ActionPerformedEvent event) {
        mostrarEmDesenvolvimento("nfeListView.inutilizarNumerosAction.text");
    }

    private void mostrarEmDesenvolvimento(String chaveTextoAcao) {
        dialogs.createMessageDialog()
                .withHeader(messageBundle.getMessage(chaveTextoAcao))
                .withText(messageBundle.getMessage("nfeListView.emDesenvolvimento.text"))
                .open();
    }

    /**
     * Um {@code NfeImportService.importar} por iteração, publicando o progresso a cada arquivo
     * pra barra andar junto do trabalho real. Nada de UI dentro de {@link #run}, igual ao import
     * de lançamentos: só em {@link #done} e {@link #canceled}.
     */
    protected class ImportarXmlTask extends BackgroundTask<Integer, List<NfeImportService.ImportResult>> {

        private final Map<String, byte[]> arquivosXml;
        private final List<NfeImportService.ImportResult> resultados = new ArrayList<>();

        protected ImportarXmlTask(Map<String, byte[]> arquivosXml) {
            super(TIMEOUT_IMPORTACAO_MINUTOS, TimeUnit.MINUTES, NfeListView.this);
            this.arquivosXml = arquivosXml;
        }

        @Override
        public List<NfeImportService.ImportResult> run(TaskLifeCycle<Integer> taskLifeCycle) throws Exception {
            for (Map.Entry<String, byte[]> arquivo : arquivosXml.entrySet()) {
                if (taskLifeCycle.isCancelled() || taskLifeCycle.isInterrupted()) {
                    break;
                }
                resultados.add(nfeImportService.importar(arquivo.getKey(), arquivo.getValue()));
                taskLifeCycle.publish(resultados.size());
            }
            return resultados;
        }

        @Override
        public void done(List<NfeImportService.ImportResult> resultados) {
            nfesDl.load();
            dialogs.createMessageDialog()
                    .withHeader("Importação de NFe")
                    .withText(resumoImportacao(resultados))
                    .open();
        }

        @Override
        public void canceled() {
            nfesDl.load();
            dialogs.createMessageDialog()
                    .withHeader("Importação de NFe")
                    .withText("Importação interrompida — " + resumoImportacao(resultados))
                    .open();
        }
    }

    private String resumoImportacao(List<NfeImportService.ImportResult> resultados) {
        long criadas = resultados.stream()
                .filter(r -> r.resultado() == NfeImportService.Resultado.CRIADA)
                .count();
        long duplicadas = resultados.stream()
                .filter(r -> r.resultado() == NfeImportService.Resultado.DUPLICADA)
                .count();
        List<String> erros = resultados.stream()
                .filter(r -> r.resultado() == NfeImportService.Resultado.ERRO)
                .map(NfeImportService.ImportResult::mensagem)
                .toList();

        StringBuilder texto = new StringBuilder();
        texto.append(criadas).append(" importada(s), ").append(duplicadas).append(" já existente(s)");
        if (!erros.isEmpty()) {
            texto.append(", ").append(erros.size()).append(" com erro:\n").append(String.join("\n", erros));
        }
        return texto.toString();
    }
}
