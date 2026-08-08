package br.com.axialsoftware.axctg3.listener.cadastros;

import br.com.axialsoftware.axctg3.entity.cadastros.Vendedor;
import br.com.axialsoftware.axctg3.service.UtilGeralService;
import io.jmix.core.event.EntitySavingEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class VendedorEventListener {

    private final UtilGeralService utilGeralService;

    public VendedorEventListener(UtilGeralService utilGeralService) {
        this.utilGeralService = utilGeralService;
    }

    @EventListener
    public void onVendedorSaving(final EntitySavingEvent<Vendedor> event) {
        if (event.isNewEntity()) {
            Vendedor vendedor = event.getEntity();
            if (vendedor.getCodEmpresa() == null) {
                vendedor.setCodEmpresa(utilGeralService.getCodEmpresa());
            }
        }
    }
}
