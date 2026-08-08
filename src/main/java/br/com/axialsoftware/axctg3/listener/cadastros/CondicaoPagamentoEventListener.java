package br.com.axialsoftware.axctg3.listener.cadastros;

import br.com.axialsoftware.axctg3.entity.cadastros.CondicaoPagamento;
import br.com.axialsoftware.axctg3.service.UtilGeralService;
import io.jmix.core.event.EntitySavingEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class CondicaoPagamentoEventListener {

    private final UtilGeralService utilGeralService;

    public CondicaoPagamentoEventListener(UtilGeralService utilGeralService) {
        this.utilGeralService = utilGeralService;
    }

    @EventListener
    public void onCondicaoPagamentoSaving(final EntitySavingEvent<CondicaoPagamento> event) {
        if (event.isNewEntity()) {
            CondicaoPagamento condicaoPagamento = event.getEntity();
            if (condicaoPagamento.getCodEmpresa() == null) {
                condicaoPagamento.setCodEmpresa(utilGeralService.getCodEmpresa());
            }
        }
    }
}
