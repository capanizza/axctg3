package br.com.axialsoftware.axctg3.listener.fiscal;

import br.com.axialsoftware.axctg3.entity.fiscal.NaturezaOperacao;
import br.com.axialsoftware.axctg3.service.UtilGeralService;
import io.jmix.core.event.EntitySavingEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class NaturezaOperacaoEventListener {

    private final UtilGeralService utilGeralService;

    public NaturezaOperacaoEventListener(UtilGeralService utilGeralService) {
        this.utilGeralService = utilGeralService;
    }

    @EventListener
    public void onNaturezaOperacaoSaving(final EntitySavingEvent<NaturezaOperacao> event) {
        if (event.isNewEntity()) {
            NaturezaOperacao naturezaOperacao = event.getEntity();
            if (naturezaOperacao.getCodEmpresa() == null) {
                naturezaOperacao.setCodEmpresa(utilGeralService.getCodEmpresa());
            }
        }
    }
}
