package br.com.axialsoftware.axctg3.listener.cadastros;

import br.com.axialsoftware.axctg3.entity.cadastros.Transportadora;
import br.com.axialsoftware.axctg3.service.UtilGeralService;
import io.jmix.core.event.EntitySavingEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class TransportadoraEventListener {

    private final UtilGeralService utilGeralService;

    public TransportadoraEventListener(UtilGeralService utilGeralService) {
        this.utilGeralService = utilGeralService;
    }

    @EventListener
    public void onTransportadoraSaving(final EntitySavingEvent<Transportadora> event) {
        if (event.isNewEntity()) {
            Transportadora transportadora = event.getEntity();
            if (transportadora.getCodEmpresa() == null) {
                transportadora.setCodEmpresa(utilGeralService.getCodEmpresa());
            }
        }
    }
}
