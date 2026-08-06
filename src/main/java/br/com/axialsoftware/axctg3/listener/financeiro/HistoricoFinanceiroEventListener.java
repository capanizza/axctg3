package br.com.axialsoftware.axctg3.listener.financeiro;

import br.com.axialsoftware.axctg3.entity.financeiro.HistoricoFinanceiro;
import br.com.axialsoftware.axctg3.service.UtilGeralService;
import io.jmix.core.event.EntitySavingEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class HistoricoFinanceiroEventListener {

    private final UtilGeralService utilGeralService;

    public HistoricoFinanceiroEventListener(UtilGeralService utilGeralService) {
        this.utilGeralService = utilGeralService;
    }

    @EventListener
    public void onHistoricoFinanceiroSaving(final EntitySavingEvent<HistoricoFinanceiro> event) {
        if (event.isNewEntity()) {
            HistoricoFinanceiro entity = event.getEntity();
            entity.setCodEmpresa(utilGeralService.getCodEmpresa());
        }
    }
}
