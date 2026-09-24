package br.com.axialsoftware.axctg3.view.tabelas.tabelaibpt;

import br.com.axialsoftware.axctg3.entity.tabelas.TabelaIbpt;
import br.com.axialsoftware.axctg3.service.tabelas.TabelaIbptImportService;
import br.com.axialsoftware.axctg3.view.main.MainView;

import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.Notifications;
import io.jmix.flowui.component.upload.FileUploadField;
import io.jmix.flowui.kit.component.button.JmixButton;
import io.jmix.flowui.kit.component.upload.event.FileUploadSucceededEvent;
import io.jmix.flowui.model.CollectionLoader;
import io.jmix.flowui.view.*;

import org.springframework.beans.factory.annotation.Autowired;

@Route(value = "tabela-ibpts", layout = MainView.class)
@ViewController(id = "TabelaIbpt.list")
@ViewDescriptor(path = "tabela-ibpt-list-view.xml")
public class TabelaIbptListView extends StandardListView<TabelaIbpt> {

    @ViewComponent
    private MessageBundle messageBundle;
    @ViewComponent
    private CollectionLoader<TabelaIbpt> tabelaIbptsDl;
    @ViewComponent
    private FileUploadField importField;
    @Autowired
    private Notifications notifications;
    @Autowired
    private TabelaIbptImportService tabelaIbptImportService;

    // A UF só existe no nome do arquivo (TabelaIBPTaxSP26.2.B.csv) — o conteúdo não traz.
    private String nomeArquivo;

    @Subscribe("importField")
    public void onImportFieldFileUploadSucceeded(final FileUploadSucceededEvent<FileUploadField, byte[]> event) {
        nomeArquivo = event.getFileName();
    }

    @Subscribe(id = "importButton", subject = "clickListener")
    public void onImportButtonClick(final ClickEvent<JmixButton> event) {
        byte[] conteudo = importField.getValue();
        if (conteudo == null || conteudo.length == 0) {
            notifications.create(messageBundle.getMessage("tabelaIbptListView.selecioneArquivo"))
                    .withType(Notifications.Type.WARNING)
                    .show();
            return;
        }

        TabelaIbptImportService.ImportResult resultado;
        try {
            resultado = tabelaIbptImportService.importar(conteudo, nomeArquivo);
        } catch (IllegalArgumentException e) {
            notifications.create(e.getMessage())
                    .withType(Notifications.Type.ERROR)
                    .show();
            return;
        }
        tabelaIbptsDl.load();
        importField.clear();
        nomeArquivo = null;

        notifications.create(messageBundle.formatMessage("tabelaIbptListView.importResultado",
                        resultado.uf(), resultado.versao(), resultado.importados(),
                        resultado.removidos(), resultado.ignorados()))
                .withType(Notifications.Type.SUCCESS)
                .show();
    }
}
