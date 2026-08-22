package br.com.axialsoftware.axctg3.listener.cadastros;

import br.com.axialsoftware.axctg3.entity.cadastros.Mensagem;
import br.com.axialsoftware.axctg3.service.UtilGeralService;
import io.jmix.core.event.EntitySavingEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class MensagemEventListener {

    private final UtilGeralService utilGeralService;

    public MensagemEventListener(UtilGeralService utilGeralService) {
        this.utilGeralService = utilGeralService;
    }

    @EventListener
    public void onMensagemSaving(final EntitySavingEvent<Mensagem> event) {
        if (event.isNewEntity()) {
            Mensagem mensagem = event.getEntity();
            if (mensagem.getCodEmpresa() == null) {
                mensagem.setCodEmpresa(utilGeralService.getCodEmpresa());
            }
        }
    }
}
