package br.com.axialsoftware.axctg3.view.fiscal.nfe;

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
 * Tela de seleção de arquivos XML de NFe para importação, aberta como diálogo a partir de
 * {@link NfeListView}. Não é uma detail view — não edita entidade nenhuma, só coleta os
 * arquivos escolhidos em memória e devolve o controle pro chamador via
 * {@link StandardOutcome#SAVE}/{@link StandardOutcome#DISCARD}.
 * <p>
 * {@code xmlUpload} usa {@code uploadHandlerType="IN_MEMORY"}, que despacha um evento de
 * sucesso por arquivo com o conteúdo já em {@code byte[]} — sem passar por
 * {@code TemporaryStorage}/disco, igual ao {@code FileUploadField} do import de ClassTrib,
 * só que aceitando múltiplos arquivos de uma vez (o componente nativo do Vaadin já suporta
 * seleção múltipla). {@code addFileRemovedListener} mantém {@link #arquivosXml} em sincronia
 * quando o usuário remove um arquivo da lista antes de confirmar.
 */
@Route(value = "nfe-import", layout = MainView.class)
@ViewController(id = "Nfe.import")
@ViewDescriptor(path = "nfe-import-view.xml")
@DialogMode(width = "40em")
public class NfeImportView extends View<VerticalLayout> {

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
     * Arquivos selecionados, chave = nome do arquivo. Só é significativo quando a tela
     * fecha com {@link StandardOutcome#SAVE} — lido pelo chamador via
     * {@code closeEvent.getView().getArquivosXml()}.
     */
    public Map<String, byte[]> getArquivosXml() {
        return arquivosXml;
    }
}
