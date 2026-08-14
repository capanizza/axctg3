package br.com.axialsoftware.axctg3.view.tabelas.contareferencial;

import br.com.axialsoftware.axctg3.entity.tabelas.ContaReferencial;
import br.com.axialsoftware.axctg3.service.UtilGeralService;
import br.com.axialsoftware.axctg3.service.tabelas.ContaReferencialImportService;
import br.com.axialsoftware.axctg3.view.main.MainView;

import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.data.renderer.Renderer;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.Dialogs;
import io.jmix.flowui.Notifications;
import io.jmix.flowui.UiComponents;
import io.jmix.flowui.action.DialogAction;
import io.jmix.flowui.backgroundtask.BackgroundTask;
import io.jmix.flowui.backgroundtask.TaskLifeCycle;
import io.jmix.flowui.component.UiComponentUtils;
import io.jmix.flowui.component.checkbox.JmixCheckbox;
import io.jmix.flowui.component.upload.FileUploadField;
import io.jmix.flowui.kit.component.button.JmixButton;
import io.jmix.flowui.model.CollectionLoader;
import io.jmix.flowui.view.*;

import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Route(value = "conta-referencials", layout = MainView.class)
@ViewController(id = "ContaReferencial.list")
@ViewDescriptor(path = "conta-referencial-list-view.xml")
@LookupComponent("contaReferencialsDataGrid")
@DialogMode(width = "64em")
public class ContaReferencialListView extends StandardListView<ContaReferencial> {

    /** Folga pra ~1100 linhas — mesmo raciocínio do timeout de importação de Lancamento. */
    private static final long TIMEOUT_IMPORTACAO_MINUTOS = 15;

    @ViewComponent
    private MessageBundle messageBundle;
    @ViewComponent
    private CollectionLoader<ContaReferencial> contaReferencialsDl;
    @ViewComponent
    private HorizontalLayout buttonsPanel;
    @ViewComponent
    private FileUploadField importField;
    @Autowired
    private UtilGeralService utilGeralService;
    @Autowired
    private ContaReferencialImportService contaReferencialImportService;
    @Autowired
    private Notifications notifications;
    @Autowired
    private Dialogs dialogs;
    @Autowired
    private UiComponents uiComponents;

    @Subscribe
    public void onBeforeShow(final BeforeShowEvent event) {
        Dialog dialog = UiComponentUtils.findDialog(this);

        contaReferencialsDl.setParameter("codPlanRef", utilGeralService.getEmpresa().getCodPlanRef().getId());
        // aberta como dialog (lookup de um entityPicker, ex.: ContaContabil.contaReferencial),
        // só contas analíticas fazem sentido pra associar — fora do dialog, mostra tudo
        contaReferencialsDl.setParameter("apenasAnaliticas", dialog != null);
        contaReferencialsDl.load();

        buttonsPanel.setVisible(dialog == null);
    }

    @Supply(to = "contaReferencialsDataGrid.analitica", subject = "renderer")
    private Renderer<ContaReferencial> contaReferencialsDataGridAnaliticaRenderer() {
        return new ComponentRenderer<>(contaReferencial -> {
            JmixCheckbox checkbox = uiComponents.create(JmixCheckbox.class);
            checkbox.setValue(Boolean.TRUE.equals(contaReferencial.getAnalitica()));
            checkbox.setReadOnly(true);
            checkbox.addClassName("grid-value-checkbox");
            return checkbox;
        });
    }

    @Subscribe(id = "importButton", subject = "clickListener")
    public void onImportButtonClick(final ClickEvent<JmixButton> event) {
        byte[] conteudo = importField.getValue();
        if (conteudo == null || conteudo.length == 0) {
            notifications.create(messageBundle.getMessage("contaReferencialListView.selecioneArquivo"))
                    .withType(Notifications.Type.WARNING)
                    .show();
            return;
        }

        ContaReferencialImportService.ParseResult parseResult;
        try {
            parseResult = contaReferencialImportService.parsear(conteudo);
        } catch (IllegalArgumentException e) {
            notifications.create(e.getMessage())
                    .withType(Notifications.Type.ERROR)
                    .show();
            return;
        }
        importField.clear();

        List<ContaReferencialImportService.Linha> linhas = parseResult.linhas();
        if (linhas.isEmpty()) {
            dialogs.createMessageDialog()
                    .withHeader(messageBundle.getMessage("contaReferencialListView.import.button"))
                    .withText(messageBundle.getMessage("contaReferencialListView.import.nadaEncontrado"))
                    .open();
            return;
        }

        dialogs.createOptionDialog()
                .withHeader(messageBundle.getMessage("contaReferencialListView.import.button"))
                .withText(messageBundle.formatMessage("contaReferencialListView.import.confirmar", linhas.size()))
                .withActions(
                        new DialogAction(DialogAction.Type.YES)
                                .withHandler(e -> dialogs
                                        .createBackgroundTaskDialog(new ImportarTask(linhas, parseResult.ignoradas()))
                                        .withHeader(messageBundle.getMessage("contaReferencialListView.import.button"))
                                        .withText(messageBundle.getMessage("contaReferencialListView.import.importando"))
                                        .withTotal(linhas.size())
                                        .withShowProgressInPercentage(true)
                                        .withCancelAllowed(true)
                                        .open()),
                        new DialogAction(DialogAction.Type.NO)
                )
                .open();
    }

    /**
     * Grava linha a linha — é o {@code DataManager.save} de cada linha que demora com ~1100
     * registros, não o parse do xlsx (feito antes, fora da task). {@code BackgroundWorker} do
     * Jmix copia a {@code Authentication} da sessão pra thread do worker, então
     * {@code ContaReferencialImportService} continua autenticado aqui dentro; só nada de UI
     * dentro de {@link #run} — diálogos e recarga do grid só em {@link #done}/{@link #canceled}.
     */
    protected class ImportarTask extends BackgroundTask<Integer, Integer> {

        private final List<ContaReferencialImportService.Linha> linhas;
        private final int ignoradasNoParse;
        private final AtomicInteger processadas = new AtomicInteger();
        private final AtomicInteger criadas = new AtomicInteger();
        private final AtomicInteger atualizadas = new AtomicInteger();

        protected ImportarTask(List<ContaReferencialImportService.Linha> linhas, int ignoradasNoParse) {
            super(TIMEOUT_IMPORTACAO_MINUTOS, TimeUnit.MINUTES, ContaReferencialListView.this);
            this.linhas = linhas;
            this.ignoradasNoParse = ignoradasNoParse;
        }

        @Override
        public Integer run(TaskLifeCycle<Integer> taskLifeCycle) throws Exception {
            for (ContaReferencialImportService.Linha linha : linhas) {
                if (taskLifeCycle.isCancelled() || taskLifeCycle.isInterrupted()) {
                    break;
                }
                boolean novo = contaReferencialImportService.salvarLinha(linha);
                if (novo) {
                    criadas.incrementAndGet();
                } else {
                    atualizadas.incrementAndGet();
                }
                taskLifeCycle.publish(processadas.incrementAndGet());
            }
            return processadas.get();
        }

        @Override
        public void done(Integer total) {
            contaReferencialsDl.load();
            dialogs.createMessageDialog()
                    .withHeader(messageBundle.getMessage("contaReferencialListView.import.button"))
                    .withText(messageBundle.formatMessage("contaReferencialListView.importResultado",
                            criadas.get(), atualizadas.get(), ignoradasNoParse))
                    .open();
        }

        @Override
        public void canceled() {
            contaReferencialsDl.load();
            dialogs.createMessageDialog()
                    .withHeader(messageBundle.getMessage("contaReferencialListView.import.button"))
                    .withText(messageBundle.formatMessage("contaReferencialListView.import.cancelada",
                            processadas.get()))
                    .open();
        }
    }
}
