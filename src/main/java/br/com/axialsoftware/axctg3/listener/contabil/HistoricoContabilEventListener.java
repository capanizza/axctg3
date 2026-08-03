package br.com.axialsoftware.axctg3.listener.contabil;

import br.com.axialsoftware.axctg3.entity.contabil.HistoricoContabil;
import br.com.axialsoftware.axctg3.service.UtilGeralService;
import io.jmix.core.event.EntitySavingEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class HistoricoContabilEventListener {

    private final UtilGeralService utilGeralService;

    public HistoricoContabilEventListener(UtilGeralService utilGeralService) {
        this.utilGeralService = utilGeralService;
    }

    @EventListener
    public void onHistoricoContabilSaving(final EntitySavingEvent<HistoricoContabil> event) {
        if (event.isNewEntity()) {
            HistoricoContabil historicoContabil = event.getEntity();
            if (historicoContabil.getCodEmpresa() == null) {
                historicoContabil.setCodEmpresa(utilGeralService.getCodEmpresa());
            }
        }
    }
}