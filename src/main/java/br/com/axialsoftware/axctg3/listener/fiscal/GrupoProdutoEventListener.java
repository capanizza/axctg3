package br.com.axialsoftware.axctg3.listener.fiscal;

import br.com.axialsoftware.axctg3.entity.fiscal.GrupoProduto;
import br.com.axialsoftware.axctg3.service.UtilGeralService;
import io.jmix.core.event.EntitySavingEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class GrupoProdutoEventListener {

    private final UtilGeralService utilGeralService;

    public GrupoProdutoEventListener(UtilGeralService utilGeralService) {
        this.utilGeralService = utilGeralService;
    }

    @EventListener
    public void onGrupoProdutoSaving(final EntitySavingEvent<GrupoProduto> event) {
        if (event.isNewEntity()) {
            GrupoProduto grupoProduto = event.getEntity();
            if (grupoProduto.getCodEmpresa() == null) {
                grupoProduto.setCodEmpresa(utilGeralService.getCodEmpresa());
            }
        }
    }
}
