package br.com.axialsoftware.axctg3.listener.contabil;

import br.com.axialsoftware.axctg3.entity.contabil.Bem;
import br.com.axialsoftware.axctg3.service.UtilGeralService;
import io.jmix.core.event.EntitySavingEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class BemEventListener {

    private final UtilGeralService utilGeralService;

    public BemEventListener(UtilGeralService utilGeralService) {
        this.utilGeralService = utilGeralService;
    }

    @EventListener
    public void onBemSaving(final EntitySavingEvent<Bem> event) {
        if (event.isNewEntity()) {
            Bem bem = event.getEntity();
            bem.setCodEmpresa(utilGeralService.getCodEmpresa());
            bem.setValorAtual(bem.getValorCompra());
            bem.setDeprAcum(BigDecimal.ZERO);
            if (bem.getValorIniDepr() == null) {
                bem.setValorIniDepr(BigDecimal.ZERO);
            }
            if (bem.getTaxaDepr() == null) {
                bem.setTaxaDepr(BigDecimal.ZERO);
            }
        }
    }
}
