package br.com.axialsoftware.axctg3.listener.cadastros;

import br.com.axialsoftware.axctg3.entity.cadastros.Parceiro;
import br.com.axialsoftware.axctg3.service.UtilGeralService;
import io.jmix.core.event.EntitySavingEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class ParceiroEventListener {

    private final UtilGeralService utilGeralService;

    public ParceiroEventListener(UtilGeralService utilGeralService) {
        this.utilGeralService = utilGeralService;
    }

    @EventListener
    public void onParceiroSaving(final EntitySavingEvent<Parceiro> event) {
        if (event.isNewEntity()) {
            Parceiro parceiro = event.getEntity();
            if (parceiro.getCodEmpresa() == null) {
                parceiro.setCodEmpresa(utilGeralService.getCodEmpresa());
            }
        }
    }
}
