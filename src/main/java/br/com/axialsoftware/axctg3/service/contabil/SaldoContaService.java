package br.com.axialsoftware.axctg3.service.contabil;

import br.com.axialsoftware.axctg3.entity.contabil.ContaContabil;
import br.com.axialsoftware.axctg3.entity.contabil.SaldoConta;
import io.jmix.core.DataManager;
import io.jmix.core.SaveContext;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class SaldoContaService {

    private final DataManager dataManager;

    public SaldoContaService(DataManager dataManager) {
        this.dataManager = dataManager;
    }

    public void criarSaldos(ContaContabil conta) {
        List<SaldoConta> saldos = new ArrayList<SaldoConta>();
        for (int mes = 1; mes <= 12; mes++) {
            UUID uuid = UUID.randomUUID();
            SaldoConta saldo = dataManager.create(SaldoConta.class);
            saldo.setId(uuid);
            saldo.setContaContabil(conta);
            saldo.setMes(mes);
            saldo.setSaldoAnterior(BigDecimal.ZERO);
            saldo.setDebitoMes(BigDecimal.ZERO);
            saldo.setCreditoMes(BigDecimal.ZERO);
            saldos.add(saldo);
        }
        conta.setSaldosConta(saldos);
        SaveContext saveContext = new SaveContext(); // .setDiscardSaved(true);
        for (SaldoConta saldo : saldos) {
            saveContext.saving(saldo);
        }
        dataManager.save(saveContext);
    }
}