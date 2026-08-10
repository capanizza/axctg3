package br.com.axialsoftware.axctg3.listener.fiscal;

import br.com.axialsoftware.axctg3.entity.fiscal.Nfe;
import br.com.axialsoftware.axctg3.service.UtilGeralService;
import io.jmix.core.event.EntitySavingEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Sem numeração via Sequence — diferente de {@link NotaSaidaEventListener} — porque o
 * número da NFe ({@code nNF}) e a chave vêm de fora (XML já emitido/autorizado, importado
 * ou digitado manualmente), não são gerados por este sistema.
 */
@Component
public class NfeEventListener {

    private final UtilGeralService utilGeralService;

    public NfeEventListener(UtilGeralService utilGeralService) {
        this.utilGeralService = utilGeralService;
    }

    @EventListener
    public void onNfeSaving(final EntitySavingEvent<Nfe> event) {
        if (event.isNewEntity()) {
            Nfe nfe = event.getEntity();
            if (nfe.getCodEmpresa() == null) {
                nfe.setCodEmpresa(utilGeralService.getCodEmpresa());
            }
        }
    }
}
