package br.com.axialsoftware.axctg3.listener.financeiro;

import br.com.axialsoftware.axctg3.entity.financeiro.HistoricoFinanceiro;
import br.com.axialsoftware.axctg3.entity.financeiro.ItemPagar;
import br.com.axialsoftware.axctg3.entity.financeiro.TituloPagar;
import br.com.axialsoftware.axctg3.service.UtilGeralService;
import br.com.axialsoftware.axctg3.service.financeiro.TituloPagarService;
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
 * Numeração via Sequence (só se {@code numero} ainda não vier setado — a tela de
 * confirmação de pedido do axctg-flow, não portada, o preenchia a partir de outra
 * lógica), campos calculados (aberto/valorBaixado/...) e criação automática do item 1
 * (emissão) de um {@link TituloPagar}. Mesmo padrão de {@code DiversoPagarEventListener}.
 */
@Component
public class TituloPagarEventListener {

    private final UtilGeralService utilGeralService;
    private final DataManager dataManager;
    private final TituloPagarService tituloPagarService;
    private final Sequences sequences;

    public TituloPagarEventListener(UtilGeralService utilGeralService, DataManager dataManager, TituloPagarService tituloPagarService, Sequences sequences) {
        this.utilGeralService = utilGeralService;
        this.dataManager = dataManager;
        this.tituloPagarService = tituloPagarService;
        this.sequences = sequences;
    }

    @EventListener
    public void onTituloPagarSaving(final EntitySavingEvent<TituloPagar> event) {
        if (event.isNewEntity()) {
            TituloPagar tituloPagar = event.getEntity();
            tituloPagar.setCodEmpresa(utilGeralService.getCodEmpresa());
            if (tituloPagar.getNumero() == null) {
                long numero = sequences.createNextValue(Sequence.withName("titulo_pagar_seq"));
                tituloPagar.setNumero(Math.toIntExact(numero));
            }
        }
    }

    @EventListener
    public void onTituloPagarLoading(final EntityLoadingEvent<TituloPagar> event) {
        TituloPagar tituloPagar = event.getEntity();
        List<ItemPagar> itensPagar = dataManager.load(ItemPagar.class)
                .query("select e from ItemPagar e " +
                        "where e.tituloPagar = :tituloPagar " +
                        "order by e.item")
                .parameter("tituloPagar", tituloPagar)
                .list();
        Boolean contabilizadoEmissao = false;
        Boolean contabilizadoBaixa = false;
        for (ItemPagar itemPagar : itensPagar) {
            if (itemPagar.getItem() == 1) {
                contabilizadoEmissao = itemPagar.getContabilizado();
            } else {
                contabilizadoBaixa = itemPagar.getContabilizado();
            }
        }
        tituloPagar.setValorBaixado(tituloPagarService.valorRecebidoTitulo(tituloPagar));
        tituloPagar.setValorAberto(tituloPagar.getValor().subtract(tituloPagar.getValorBaixado()));
        tituloPagar.setAberto(tituloPagarService.aberto(tituloPagar));
        tituloPagar.setContabilizadoEmissao(contabilizadoEmissao);
        tituloPagar.setContabilizadoBaixa(contabilizadoBaixa);
    }

    @EventListener
    public void onTituloPagarChangedBeforeCommit(final EntityChangedEvent<TituloPagar> event) {
        if (event.getType() == EntityChangedEvent.Type.DELETED) {
            return;
        }
        Integer codEmpresa = utilGeralService.getCodEmpresa();
        Id<TituloPagar> id = event.getEntityId();
        TituloPagar tituloPagar = dataManager.load(id).one();
        if (event.getType() == EntityChangedEvent.Type.CREATED) {
            HistoricoFinanceiro historicoFinanceiro = dataManager.load(HistoricoFinanceiro.class)
                    .query("select e from HistoricoFinanceiro e " +
                            "where e.codigo = 1 " +
                            "and e.codEmpresa = :codEmpresa")
                    .parameter("codEmpresa", codEmpresa)
                    .one();
            ItemPagar itemPagar = dataManager.create(ItemPagar.class);
            itemPagar.setTituloPagar(tituloPagar);
            itemPagar.setHistoricoFinanceiro(historicoFinanceiro);
            itemPagar.setData(tituloPagar.getDataEmissao());
            itemPagar.setValor(tituloPagar.getValor());
            itemPagar.setJuros(BigDecimal.ZERO);
            itemPagar.setDesconto(BigDecimal.ZERO);
            itemPagar.setItem(1);
            dataManager.save(itemPagar);
        }
        if (event.getType() == EntityChangedEvent.Type.UPDATED) {
            ItemPagar itemPagar = dataManager.load(ItemPagar.class)
                    .query("select e from ItemPagar e " +
                            "where e.tituloPagar = :tituloPagar " +
                            "and e.item = 1")
                    .parameter("tituloPagar", tituloPagar)
                    .one();
            boolean alterar = false;
            if (event.getChanges().isChanged("dataEmissao")) {
                itemPagar.setData(tituloPagar.getDataEmissao());
                alterar = true;
            }
            if (event.getChanges().isChanged("valor")) {
                itemPagar.setValor(tituloPagar.getValor());
                alterar = true;
            }
            if (alterar) {
                dataManager.save(itemPagar);
            }
        }
    }

}
