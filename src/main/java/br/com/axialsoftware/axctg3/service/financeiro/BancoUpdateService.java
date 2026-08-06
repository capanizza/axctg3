package br.com.axialsoftware.axctg3.service.financeiro;

import br.com.axialsoftware.axctg3.entity.financeiro.Banco;
import io.jmix.core.DataManager;
import io.jmix.core.RemoveDelegate;
import io.jmix.core.SaveContext;
import io.jmix.core.SaveDelegate;
import org.springframework.stereotype.Service;

@Service
public class BancoUpdateService implements SaveDelegate<Banco>, RemoveDelegate<Banco> {
    private final DataManager dataManager;

    public BancoUpdateService(DataManager dataManager) {
        this.dataManager = dataManager;
    }

    @Override
    public Banco save(Banco banco, SaveContext saveContext) {
        return SaveDelegate.save(dataManager, banco, saveContext);
    }

    @Override
    public void remove(Banco banco) {
        dataManager.remove(banco);
    }
}
