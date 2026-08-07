package br.com.axialsoftware.axctg3.listener.financeiro;

import br.com.axialsoftware.axctg3.entity.financeiro.DiversoPagar;
import br.com.axialsoftware.axctg3.entity.financeiro.HistoricoFinanceiro;
import br.com.axialsoftware.axctg3.entity.financeiro.ItemDiversoPagar;
import br.com.axialsoftware.axctg3.service.UtilGeralService;
import br.com.axialsoftware.axctg3.service.financeiro.DiversoPagarService;
import io.jmix.core.DataManager;
import io.jmix.core.Id;
import io.jmix.core.event.EntityChangedEvent;
import io.jmix.core.event.EntityLoadingEvent;
import io.jmix.core.event.EntitySavingEvent;
import io.jmix.data.Sequence;
import io.jmix.data.Sequences;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * Numeração via Sequence, campos calculados (aberto/valorBaixado/...) e criação
 * automática do item 1 (emissão) de um {@link DiversoPagar}. Ver
 * {@code LancamentoEventListener} para o padrão equivalente do lançamento contábil.
 */
@Component
public class DiversoPagarEventListener {

    private final UtilGeralService utilGeralService;
    private final DataManager dataManager;
    private final DiversoPagarService diversoPagarService;
    private final Sequences sequences;

    public DiversoPagarEventListener(UtilGeralService utilGeralService, DataManager dataManager, DiversoPagarService diversoPagarService, Sequences sequences) {
        this.utilGeralService = utilGeralService;
        this.dataManager = dataManager;
        this.diversoPagarService = diversoPagarService;
        this.sequences = sequences;
    }

    @EventListener
    public void onDiversoPagarSaving(final EntitySavingEvent<DiversoPagar> event) {
        if (event.isNewEntity()) {
            DiversoPagar diversoPagar = event.getEntity();
            diversoPagar.setCodEmpresa(utilGeralService.getCodEmpresa());
            long numero = sequences.createNextValue(Sequence.withName("diverso_pagar_seq"));
            diversoPagar.setNumero(Math.toIntExact(numero));
        }
    }

    @EventListener
    public void onDiversoPagarLoading(final EntityLoadingEvent<DiversoPagar> event) {
        DiversoPagar diversoPagar = event.getEntity();
        List<ItemDiversoPagar> itensDiversoPagar = dataManager.load(ItemDiversoPagar.class)
                .query("select e from ItemDiversoPagar e " +
                        "where e.diversoPagar = :diversoPagar " +
                        "order by e.item")
                .parameter("diversoPagar", diversoPagar)
                .list();
        Boolean contabilizadoEmissao = false;
        Boolean contabilizadoBaixa = false;
        for (ItemDiversoPagar itemDiversoPagar : itensDiversoPagar) {
            if (itemDiversoPagar.getItem() == 1) {
                contabilizadoEmissao = itemDiversoPagar.getContabilizado();
            } else {
                contabilizadoBaixa = itemDiversoPagar.getContabilizado();
            }
        }
        diversoPagar.setValorBaixado(diversoPagarService.valorRecebidoDiverso(diversoPagar));
        diversoPagar.setValorAberto(diversoPagar.getValor().subtract(diversoPagar.getValorBaixado()));
        diversoPagar.setAberto(diversoPagarService.aberto(diversoPagar));
        diversoPagar.setContabilizadoEmissao(contabilizadoEmissao);
        diversoPagar.setContabilizadoBaixa(contabilizadoBaixa);
    }

    @EventListener
    public void onDiversoPagarChangedBeforeCommit(final EntityChangedEvent<DiversoPagar> event) {
        if (event.getType() == EntityChangedEvent.Type.DELETED) {
            return;
        }
        Integer codEmpresa = utilGeralService.getCodEmpresa();
        Id<DiversoPagar> id = event.getEntityId();
        DiversoPagar diversoPagar = dataManager.load(id).one();
        if (event.getType() == EntityChangedEvent.Type.CREATED) {
            HistoricoFinanceiro historicoFinanceiro = dataManager.load(HistoricoFinanceiro.class)
                    .query("select e from HistoricoFinanceiro e " +
                            "where e.codigo = 1 " +
                            "and e.codEmpresa = :codEmpresa")
                    .parameter("codEmpresa", codEmpresa)
                    .one();
            ItemDiversoPagar itemDiversoPagar = dataManager.create(ItemDiversoPagar.class);
            itemDiversoPagar.setDiversoPagar(diversoPagar);
            itemDiversoPagar.setHistoricoFinanceiro(historicoFinanceiro);
            itemDiversoPagar.setData(diversoPagar.getDataEmissao());
            itemDiversoPagar.setValor(diversoPagar.getValor());
            itemDiversoPagar.setJuros(BigDecimal.ZERO);
            itemDiversoPagar.setDesconto(BigDecimal.ZERO);
            itemDiversoPagar.setItem(1);
            dataManager.save(itemDiversoPagar);
        }
        if (event.getType() == EntityChangedEvent.Type.UPDATED) {
            ItemDiversoPagar itemDiversoPagar = dataManager.load(ItemDiversoPagar.class)
                    .query("select e from ItemDiversoPagar e " +
                            "where e.diversoPagar = :diversoPagar " +
                            "and e.item = 1")
                    .parameter("diversoPagar", diversoPagar)
                    .one();
            boolean alterar = false;
            if (event.getChanges().isChanged("dataEmissao")) {
                itemDiversoPagar.setData(diversoPagar.getDataEmissao());
                alterar = true;
            }
            if (event.getChanges().isChanged("valor")) {
                itemDiversoPagar.setValor(diversoPagar.getValor());
                alterar = true;
            }
            if (alterar) {
                dataManager.save(itemDiversoPagar);
            }
        }
    }

}
