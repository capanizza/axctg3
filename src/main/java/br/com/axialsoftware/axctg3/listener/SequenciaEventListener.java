package br.com.axialsoftware.axctg3.listener;

import br.com.axialsoftware.axctg3.entity.Sequencia;
import br.com.axialsoftware.axctg3.service.SequenciaService;
import br.com.axialsoftware.axctg3.service.UtilGeralService;
import io.jmix.core.DataManager;
import io.jmix.core.Id;
import io.jmix.core.event.EntityChangedEvent;
import io.jmix.core.event.EntitySavingEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class SequenciaEventListener {

    private final UtilGeralService utilGeralService;
    private final SequenciaService sequenciaService;
    private final DataManager dataManager;

    public SequenciaEventListener(UtilGeralService utilGeralService, SequenciaService sequenciaService, DataManager dataManager) {
        this.utilGeralService = utilGeralService;
        this.sequenciaService = sequenciaService;
        this.dataManager = dataManager;
    }

    @EventListener
    public void onSequenciaSaving(final EntitySavingEvent<Sequencia> event) {
        if (event.isNewEntity()) {
            Sequencia sequencia = event.getEntity();
            if (sequencia.getCodEmpresa() == null) {
                sequencia.setCodEmpresa(utilGeralService.getCodEmpresa());
            }
            if (sequencia.getValor() == null) {
                sequencia.setValor(0);
            }
            sequenciaService.atualizaSequencia(sequencia.getCodigo(), sequencia.getValor());
        }
    }

    @EventListener
    public void onSequenciaChangedBeforeCommit(final EntityChangedEvent<Sequencia> event) {
        if (event.getType() == EntityChangedEvent.Type.UPDATED) {
            Id<Sequencia> id = event.getEntityId();
            Sequencia sequencia = dataManager.load(id).one();
            sequenciaService.atualizaSequencia(sequencia.getCodigo(), sequencia.getValor());
        }
    }
}
