package br.com.axialsoftware.axctg3.view.cadastros.empresa;

import br.com.axialsoftware.axctg3.entity.cadastros.Empresa;
import br.com.axialsoftware.axctg3.service.fiscal.NfeWebserviceClient;
import br.com.axialsoftware.axctg3.view.main.MainView;

import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.router.Route;
import io.jmix.core.Messages;
import io.jmix.flowui.Dialogs;
import io.jmix.flowui.kit.component.button.JmixButton;
import io.jmix.flowui.model.InstanceContainer;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;

@Route(value = "empresas/:id", layout = MainView.class)
@ViewController(id = "Empresa.detail")
@ViewDescriptor(path = "empresa-detail-view.xml")
@EditedEntityContainer("empresaDc")
public class EmpresaDetailView extends StandardDetailView<Empresa> {

    @ViewComponent
    private InstanceContainer<Empresa> empresaDc;
    @ViewComponent
    private MessageBundle messageBundle;
    @Autowired
    private Dialogs dialogs;
    @Autowired
    private NfeWebserviceClient nfeWebserviceClient;
    @Autowired
    private Messages messages;

    @Subscribe(id = "testarConexaoSefazButton", subject = "clickListener")
    public void onTestarConexaoSefazButtonClick(final ClickEvent<JmixButton> event) {
        Empresa empresa = empresaDc.getItem();
        if (empresa.getCrt() == null || empresa.getAmbienteNfe() == null
                || empresa.getCertificadoArquivo() == null || empresa.getCertificadoSenha() == null) {
            dialogs.createMessageDialog()
                    .withHeader(messageBundle.getMessage("empresaDetailView.testarConexaoSefaz.button"))
                    .withText(messageBundle.getMessage("empresaDetailView.testarConexaoSefaz.semConfig"))
                    .open();
            return;
        }
        try {
            NfeWebserviceClient.Resposta resposta = nfeWebserviceClient.consultarStatusServico(empresa);
            String ambiente = messages.getMessage(empresa.getAmbienteNfe());
            dialogs.createMessageDialog()
                    .withHeader(messageBundle.getMessage("empresaDetailView.testarConexaoSefaz.sucesso.header"))
                    .withText(messageBundle.formatMessage("empresaDetailView.testarConexaoSefaz.sucesso.text",
                            ambiente, resposta.cStat(), resposta.xMotivo()))
                    .open();
        } catch (Exception e) {
            dialogs.createMessageDialog()
                    .withHeader(messageBundle.getMessage("empresaDetailView.testarConexaoSefaz.falha.header"))
                    .withText(messageBundle.formatMessage("empresaDetailView.testarConexaoSefaz.falha.text", e.getMessage()))
                    .open();
        }
    }
}
