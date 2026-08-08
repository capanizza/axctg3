package br.com.axialsoftware.axctg3.listener.financeiro;

import br.com.axialsoftware.axctg3.entity.financeiro.HistoricoFinanceiro;
import br.com.axialsoftware.axctg3.entity.financeiro.ItemReceber;
import br.com.axialsoftware.axctg3.entity.financeiro.TituloReceber;
import br.com.axialsoftware.axctg3.service.UtilGeralService;
import br.com.axialsoftware.axctg3.service.financeiro.TituloReceberService;
import io.jmix.core.DataManager;
import io.jmix.core.Id;
import io.jmix.core.event.EntityChangedEvent;
import io.jmix.core.event.EntityLoadingEvent;
import io.jmix.core.event.EntitySavingEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * Campos calculados (aberto/valorBaixado/...) e criação automática do item 1 (emissão)
 * de um {@link TituloReceber}. Ao contrário de {@code DiversoPagarEventListener}, não há
 * numeração via Sequence aqui — {@code numero} é digitado (ligado ao número da nota
 * fiscal/duplicata), não gerado.
 */
@Component
public class TituloReceberEventListener {

    private final UtilGeralService utilGeralService;
    private final DataManager dataManager;
    private final TituloReceberService tituloReceberService;

    public TituloReceberEventListener(UtilGeralService utilGeralService, DataManager dataManager, TituloReceberService tituloReceberService) {
        this.utilGeralService = utilGeralService;
        this.dataManager = dataManager;
        this.tituloReceberService = tituloReceberService;
    }

    @EventListener
    public void onTituloReceberSaving(final EntitySavingEvent<TituloReceber> event) {
        if (event.isNewEntity()) {
            event.getEntity().setCodEmpresa(utilGeralService.getCodEmpresa());
        }
    }

    @EventListener
    public void onTituloReceberLoading(final EntityLoadingEvent<TituloReceber> event) {
        TituloReceber tituloReceber = event.getEntity();
        List<ItemReceber> itensReceber = dataManager.load(ItemReceber.class)
                .query("select e from ItemReceber e " +
                        "where e.tituloReceber = :tituloReceber " +
                        "order by e.item")
                .parameter("tituloReceber", tituloReceber)
                .list();
        Boolean contabilizadoEmissao = false;
        Boolean contabilizadoBaixa = false;
        for (ItemReceber itemReceber : itensReceber) {
            if (itemReceber.getItem() == 1) {
                contabilizadoEmissao = itemReceber.getContabilizado();
            } else {
                contabilizadoBaixa = itemReceber.getContabilizado();
            }
        }
        tituloReceber.setValorBaixado(tituloReceberService.valorRecebidoTitulo(tituloReceber));
        tituloReceber.setValorAberto(tituloReceber.getValor().subtract(tituloReceber.getValorBaixado()));
        tituloReceber.setAberto(tituloReceberService.aberto(tituloReceber));
        tituloReceber.setContabilizadoEmissao(contabilizadoEmissao);
        tituloReceber.setContabilizadoBaixa(contabilizadoBaixa);
    }

    @EventListener
    public void onTituloReceberChangedBeforeCommit(final EntityChangedEvent<TituloReceber> event) {
        if (event.getType() == EntityChangedEvent.Type.DELETED) {
            return;
        }
        Integer codEmpresa = utilGeralService.getCodEmpresa();
        Id<TituloReceber> id = event.getEntityId();
        TituloReceber tituloReceber = dataManager.load(id).one();
        if (event.getType() == EntityChangedEvent.Type.CREATED) {
            HistoricoFinanceiro historicoFinanceiro = dataManager.load(HistoricoFinanceiro.class)
                    .query("select e from HistoricoFinanceiro e " +
                            "where e.codigo = 1 " +
                            "and e.codEmpresa = :codEmpresa")
                    .parameter("codEmpresa", codEmpresa)
                    .one();
            ItemReceber itemReceber = dataManager.create(ItemReceber.class);
            itemReceber.setTituloReceber(tituloReceber);
            itemReceber.setHistoricoFinanceiro(historicoFinanceiro);
            itemReceber.setData(tituloReceber.getDataEmissao());
            itemReceber.setValor(tituloReceber.getValor());
            itemReceber.setJuros(BigDecimal.ZERO);
            itemReceber.setDesconto(BigDecimal.ZERO);
            itemReceber.setItem(1);
            dataManager.saveWithoutReload(itemReceber);
        }
        if (event.getType() == EntityChangedEvent.Type.UPDATED) {
            ItemReceber itemReceber = dataManager.load(ItemReceber.class)
                    .query("select e from ItemReceber e " +
                            "where e.tituloReceber = :tituloReceber " +
                            "and e.item = 1")
                    .parameter("tituloReceber", tituloReceber)
                    .one();
            boolean alterar = false;
            if (event.getChanges().isChanged("dataEmissao")) {
                itemReceber.setData(tituloReceber.getDataEmissao());
                alterar = true;
            }
            if (event.getChanges().isChanged("valor")) {
                itemReceber.setValor(tituloReceber.getValor());
                alterar = true;
            }
            if (alterar) {
                dataManager.saveWithoutReload(itemReceber);
            }
        }
    }
}
