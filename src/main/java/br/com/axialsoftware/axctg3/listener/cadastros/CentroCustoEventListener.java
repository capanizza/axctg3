package br.com.axialsoftware.axctg3.listener.cadastros;

import br.com.axialsoftware.axctg3.entity.cadastros.CentroCusto;
import br.com.axialsoftware.axctg3.service.UtilGeralService;
import io.jmix.core.event.EntitySavingEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class CentroCustoEventListener {

    private final UtilGeralService utilGeralService;

    public CentroCustoEventListener(UtilGeralService utilGeralService) {
        this.utilGeralService = utilGeralService;
    }

    @EventListener
    public void onCentroCustoSaving(final EntitySavingEvent<CentroCusto> event) {
        if (event.isNewEntity()) {
            CentroCusto centroCusto = event.getEntity();
            if (centroCusto.getCodEmpresa() == null) {
                centroCusto.setCodEmpresa(utilGeralService.getCodEmpresa());
            }
        }
    }
}