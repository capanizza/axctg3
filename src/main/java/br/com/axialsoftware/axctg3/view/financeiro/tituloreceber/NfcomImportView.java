package br.com.axialsoftware.axctg3.view.financeiro.tituloreceber;

import br.com.axialsoftware.axctg3.view.main.MainView;

import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.component.upload.JmixUpload;
import io.jmix.flowui.kit.component.button.JmixButton;
import io.jmix.flowui.view.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Tela de seleção de arquivos XML de NFCom (notas e eventos de cancelamento, misturados) pra
 * importação, aberta como diálogo a partir de {@link TituloReceberListView} — mesmo padrão de
 * {@code NfeImportView} (upload em memória, múltiplos arquivos, devolve o controle pro chamador
 * via {@link StandardOutcome#SAVE}/{@link StandardOutcome#DISCARD}).
 */
@Route(value = "nfcom-import", layout = MainView.class)
@ViewController(id = "Nfcom.import")
@ViewDescriptor(path = "nfcom-import-view.xml")
@DialogMode(width = "40em")
public class NfcomImportView extends View<VerticalLayout> {

    @ViewComponent
    private JmixUpload<byte[]> xmlUpload;
    @ViewComponent
    private JmixButton importarButton;

    private final Map<String, byte[]> arquivosXml = new LinkedHashMap<>();

    @Subscribe
    public void onInit(final InitEvent event) {
        importarButton.setEnabled(false);

        xmlUpload.addUploadSucceededListener(succeededEvent -> {
            arquivosXml.put(succeededEvent.getFileName(), succeededEvent.getData());
            importarButton.setEnabled(!arquivosXml.isEmpty());
        });
        xmlUpload.addFileRemovedListener(removedEvent -> {
            arquivosXml.remove(removedEvent.getFileName());
            importarButton.setEnabled(!arquivosXml.isEmpty());
        });
    }

    @Subscribe(id = "importarButton", subject = "clickListener")
    public void onImportarButtonClick(final ClickEvent<JmixButton> event) {
        close(StandardOutcome.SAVE);
    }

    @Subscribe(id = "cancelarButton", subject = "clickListener")
    public void onCancelarButtonClick(final ClickEvent<JmixButton> event) {
        close(StandardOutcome.DISCARD);
    }

    /**
     * Arquivos selecionados, chave = nome do arquivo. Só é significativo quando a tela fecha com
     * {@link StandardOutcome#SAVE} — lido pelo chamador via
     * {@code closeEvent.getView().getArquivosXml()}.
     */
    public Map<String, byte[]> getArquivosXml() {
        return arquivosXml;
    }
}
