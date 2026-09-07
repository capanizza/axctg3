package br.com.axialsoftware.axctg3.view.financeiro.tituloreceber;

import br.com.axialsoftware.axctg3.entity.financeiro.Banco;
import br.com.axialsoftware.axctg3.service.UtilGeralService;
import br.com.axialsoftware.axctg3.view.main.MainView;

import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;
import io.jmix.core.DataManager;
import io.jmix.flowui.component.upload.JmixUpload;
import io.jmix.flowui.kit.component.button.JmixButton;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Diálogo de escolha do banco + upload do arquivo de retorno CNAB, aberto a partir de
 * {@link TituloReceberListView}. Mesmo padrão de {@code NfeImportView}: coleta em memória
 * (sem passar por {@code FileStorage}) e devolve o controle pro chamador via
 * {@link StandardOutcome#SAVE}/{@link StandardOutcome#DISCARD}.
 */
@Route(value = "retorno-banco-import", layout = MainView.class)
@ViewController(id = "RetornoBanco.import")
@ViewDescriptor(path = "retorno-banco-import-view.xml")
@DialogMode(width = "40em")
public class RetornoBancoImportView extends View<VerticalLayout> {

    @ViewComponent
    private ComboBox<Banco> bancoField;
    @ViewComponent
    private JmixUpload<byte[]> retornoUpload;
    @ViewComponent
    private JmixButton processarButton;
    @Autowired
    private DataManager dataManager;
    @Autowired
    private UtilGeralService utilGeralService;

    private byte[] arquivo;
    private String nomeArquivo;

    @Subscribe
    public void onInit(final InitEvent event) {
        processarButton.setEnabled(false);

        bancoField.setItems(dataManager.load(Banco.class)
                .query("select e from Banco e where e.codEmpresa = :codEmpresa order by e.codigo")
                .parameter("codEmpresa", utilGeralService.getCodEmpresa())
                .list());
        bancoField.addValueChangeListener(e -> atualizarHabilitacaoProcessar());

        retornoUpload.addUploadSucceededListener(succeededEvent -> {
            arquivo = succeededEvent.getData();
            nomeArquivo = succeededEvent.getFileName();
            atualizarHabilitacaoProcessar();
        });
        retornoUpload.addFileRemovedListener(removedEvent -> {
            arquivo = null;
            nomeArquivo = null;
            atualizarHabilitacaoProcessar();
        });
    }

    private void atualizarHabilitacaoProcessar() {
        processarButton.setEnabled(bancoField.getValue() != null && arquivo != null);
    }

    @Subscribe(id = "processarButton", subject = "clickListener")
    public void onProcessarButtonClick(final ClickEvent<JmixButton> event) {
        close(StandardOutcome.SAVE);
    }

    @Subscribe(id = "cancelarButton", subject = "clickListener")
    public void onCancelarButtonClick(final ClickEvent<JmixButton> event) {
        close(StandardOutcome.DISCARD);
    }

    /** Só significativo quando a tela fecha com {@link StandardOutcome#SAVE}. */
    public Banco getBanco() {
        return bancoField.getValue();
    }

    public byte[] getArquivo() {
        return arquivo;
    }

    public String getNomeArquivo() {
        return nomeArquivo;
    }
}
