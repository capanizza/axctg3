package br.com.axialsoftware.axctg3.listener.contabil;

import br.com.axialsoftware.axctg3.entity.contabil.ContaContabil;
import br.com.axialsoftware.axctg3.service.UtilGeralService;
import br.com.axialsoftware.axctg3.service.contabil.SaldoContaService;
import io.jmix.core.DataManager;
import io.jmix.core.Id;
import io.jmix.core.event.EntityChangedEvent;
import io.jmix.core.event.EntitySavingEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class ContaContabilEventListener {

    private final UtilGeralService utilGeralService;
    private final DataManager dataManager;
    private final SaldoContaService saldoContaService;

    public ContaContabilEventListener(UtilGeralService utilGeralService, DataManager dataManager, SaldoContaService saldoContaService) {
        this.utilGeralService = utilGeralService;
        this.dataManager = dataManager;
        this.saldoContaService = saldoContaService;
    }

    @EventListener
    public void onContaContabilSaving(final EntitySavingEvent<ContaContabil> event) {
        if (event.isNewEntity()) {
            ContaContabil contaContabil = event.getEntity();
            contaContabil.setCodEmpresa(utilGeralService.getCodEmpresa());
        }
    }

    @EventListener
    public void onContaContabilChangedBeforeCommit(final EntityChangedEvent<ContaContabil> event) {
        if (event.getType() != EntityChangedEvent.Type.DELETED) {
            Id<ContaContabil> id = event.getEntityId();
            ContaContabil conta = dataManager.load(id).one();
            int tamanho;
            if (conta.getSaldosConta() == null) {
                tamanho = 0;
            } else {
                tamanho = conta.getSaldosConta().size();
            }
            if (tamanho == 0) {
                saldoContaService.criarSaldos(conta);
            }
        }
    }
}