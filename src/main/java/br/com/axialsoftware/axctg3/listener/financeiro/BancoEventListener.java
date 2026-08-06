package br.com.axialsoftware.axctg3.listener.financeiro;

import br.com.axialsoftware.axctg3.entity.financeiro.Banco;
import br.com.axialsoftware.axctg3.service.UtilGeralService;
import io.jmix.core.event.EntitySavingEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class BancoEventListener {

    private final UtilGeralService utilGeralService;

    public BancoEventListener(UtilGeralService utilGeralService) {
        this.utilGeralService = utilGeralService;
    }

    @EventListener
    public void onBancoSaving(final EntitySavingEvent<Banco> event) {
        if (event.isNewEntity()) {
            Banco banco = event.getEntity();
            if (banco.getCodEmpresa() == null) {
                banco.setCodEmpresa(utilGeralService.getCodEmpresa());
            }
        }
    }
}