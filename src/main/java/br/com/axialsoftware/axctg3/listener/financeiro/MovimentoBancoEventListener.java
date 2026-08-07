package br.com.axialsoftware.axctg3.listener.financeiro;

import br.com.axialsoftware.axctg3.entity.financeiro.ItemMovimentoBanco;
import br.com.axialsoftware.axctg3.entity.financeiro.MovimentoBanco;
import br.com.axialsoftware.axctg3.service.UtilGeralService;
import io.jmix.core.DataManager;
import io.jmix.core.Id;
import io.jmix.core.event.EntityChangedEvent;
import io.jmix.core.event.EntityLoadingEvent;
import io.jmix.core.event.EntitySavingEvent;
import io.jmix.data.Sequence;
import io.jmix.data.Sequences;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Numeração via Sequence, criação automática do item interno (item 1) de um
 * {@link MovimentoBanco} e cópia de {@code lancado} a partir dele no load. Mesmo padrão
 * de {@code DiversoPagarEventListener} — ver Javadoc de {@link MovimentoBanco}.
 */
@Component
public class MovimentoBancoEventListener {

    private final UtilGeralService utilGeralService;
    private final DataManager dataManager;
    private final Sequences sequences;

    public MovimentoBancoEventListener(UtilGeralService utilGeralService, DataManager dataManager, Sequences sequences) {
        this.utilGeralService = utilGeralService;
        this.dataManager = dataManager;
        this.sequences = sequences;
    }

    @EventListener
    public void onMovimentoBancoSaving(final EntitySavingEvent<MovimentoBanco> event) {
        if (event.isNewEntity()) {
            MovimentoBanco movimentoBanco = event.getEntity();
            movimentoBanco.setCodEmpresa(utilGeralService.getCodEmpresa());
            if (movimentoBanco.getEntrada() == null) {
                movimentoBanco.setEntrada(false);
            }
            long numero = sequences.createNextValue(Sequence.withName("movimento_banco_seq"));
            movimentoBanco.setLancamento(Math.toIntExact(numero));
        }
    }

    @EventListener
    public void onMovimentoBancoLoading(final EntityLoadingEvent<MovimentoBanco> event) {
        MovimentoBanco movimentoBanco = event.getEntity();
        // Consulta explícita, não movimentoBanco.getItens() — o fetch plan de quem carregou
        // (telas, testes) não necessariamente inclui "itens" nem o atributo "lancado" de cada
        // item, e getItens() lançaria "unfetched attribute" nesse caso. Mesmo raciocínio dos
        // outros listeners de emissão/baixa (DiversoPagar, TituloPagar, TituloReceber).
        List<ItemMovimentoBanco> itensMovimentoBanco = dataManager.load(ItemMovimentoBanco.class)
                .query("select e from ItemMovimentoBanco e where e.movimentoBanco = :movimentoBanco")
                .parameter("movimentoBanco", movimentoBanco)
                .list();
        for (ItemMovimentoBanco itemMovimentoBanco : itensMovimentoBanco) {
            movimentoBanco.setLancado(itemMovimentoBanco.getLancado());
        }
    }

    @EventListener
    public void onMovimentoBancoChangedBeforeCommit(final EntityChangedEvent<MovimentoBanco> event) {
        if (event.getType() == EntityChangedEvent.Type.CREATED) {
            Id<MovimentoBanco> id = event.getEntityId();
            MovimentoBanco movimentoBanco = dataManager.load(id).one();
            ItemMovimentoBanco itemMovimentoBanco = dataManager.create(ItemMovimentoBanco.class);
            itemMovimentoBanco.setMovimentoBanco(movimentoBanco);
            itemMovimentoBanco.setItem(1);
            itemMovimentoBanco.setLancado(false);
            dataManager.save(itemMovimentoBanco);
        }
    }

}
