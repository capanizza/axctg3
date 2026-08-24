package br.com.axialsoftware.axctg3.listener.fiscal;

import br.com.axialsoftware.axctg3.entity.fiscal.ItemNotaSaida;
import br.com.axialsoftware.axctg3.entity.fiscal.NotaSaida;
import br.com.axialsoftware.axctg3.service.UtilGeralService;
import io.jmix.core.DataManager;
import io.jmix.core.Id;
import io.jmix.core.event.EntityChangedEvent;
import io.jmix.core.event.EntitySavingEvent;
import io.jmix.data.Sequence;
import io.jmix.data.Sequences;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Numeração via {@code Sequence} simples (não por período, diferente de
 * {@code LancamentoEventListener}) — mesmo padrão de {@code MovimentoBancoEventListener}.
 * Depois que a nota é criada, renumera os itens em sequência (1, 2, 3...); cada
 * {@link ItemNotaSaida} chega com um número provisório da própria sequência de itens
 * ({@code ItemNotaSaidaEventListener}), útil só pra ter um valor não-nulo enquanto o
 * usuário monta os itens antes de salvar a nota inteira.
 */
@Component
public class NotaSaidaEventListener {

    private final UtilGeralService utilGeralService;
    private final DataManager dataManager;
    private final Sequences sequences;

    public NotaSaidaEventListener(UtilGeralService utilGeralService, DataManager dataManager, Sequences sequences) {
        this.utilGeralService = utilGeralService;
        this.dataManager = dataManager;
        this.sequences = sequences;
    }

    @EventListener
    public void onNotaSaidaSaving(final EntitySavingEvent<NotaSaida> event) {
        if (event.isNewEntity()) {
            NotaSaida notaSaida = event.getEntity();
            if (notaSaida.getCodEmpresa() == null) {
                notaSaida.setCodEmpresa(utilGeralService.getCodEmpresa());
            }
            if (notaSaida.getNumero() == null) {
                long numero = sequences.createNextValue(Sequence.withName("nota_saida_seq_" + notaSaida.getCodEmpresa()));
                notaSaida.setNumero(Math.toIntExact(numero));
            }
        }
    }

    @EventListener
    public void onNotaSaidaChangedBeforeCommit(final EntityChangedEvent<NotaSaida> event) {
        if (event.getType() == EntityChangedEvent.Type.CREATED) {
            Id<NotaSaida> id = event.getEntityId();
            NotaSaida notaSaida = dataManager.load(id).one();
            List<ItemNotaSaida> itens = notaSaida.getItens();
            int numItem = 1;
            for (ItemNotaSaida item : itens) {
                item.setItem(numItem++);
                dataManager.save(item);
            }
        }
    }
}
