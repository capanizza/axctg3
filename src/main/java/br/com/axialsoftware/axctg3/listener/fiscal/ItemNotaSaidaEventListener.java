package br.com.axialsoftware.axctg3.listener.fiscal;

import br.com.axialsoftware.axctg3.entity.enums.FinNfe;
import br.com.axialsoftware.axctg3.entity.fiscal.ItemNotaSaida;
import br.com.axialsoftware.axctg3.entity.fiscal.NaturezaOperacao;
import br.com.axialsoftware.axctg3.entity.fiscal.NotaSaida;
import br.com.axialsoftware.axctg3.entity.fiscal.Produto;
import br.com.axialsoftware.axctg3.service.UtilGeralService;
import br.com.axialsoftware.axctg3.service.fiscal.ItemNotaSaidaTributacaoService;
import br.com.axialsoftware.axctg3.service.fiscal.NotaSaidaService;
import io.jmix.core.DataManager;
import io.jmix.core.EntityStates;
import io.jmix.core.FetchPlan;
import io.jmix.core.Id;
import io.jmix.core.event.EntityChangedEvent;
import io.jmix.core.event.EntitySavingEvent;
import io.jmix.data.Sequence;
import io.jmix.data.Sequences;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Numeração do item, cfop, e resolução/gravação de tributação (CST/cClassTrib/ICMS) —
 * a lógica de cálculo em si (precedência natureza/produto) vive em
 * {@link ItemNotaSaidaTributacaoService}, reaproveitada também pelo preview ao vivo de
 * {@code ItemNotaSaidaDetailView}.
 */
@Component
public class ItemNotaSaidaEventListener {

    private final Sequences sequences;
    private final UtilGeralService utilGeralService;
    private final DataManager dataManager;
    private final EntityStates entityStates;
    private final NotaSaidaService notaSaidaService;
    private final ItemNotaSaidaTributacaoService tributacaoService;

    public ItemNotaSaidaEventListener(Sequences sequences, UtilGeralService utilGeralService,
                                       DataManager dataManager, EntityStates entityStates,
                                       NotaSaidaService notaSaidaService,
                                       ItemNotaSaidaTributacaoService tributacaoService) {
        this.sequences = sequences;
        this.utilGeralService = utilGeralService;
        this.dataManager = dataManager;
        this.entityStates = entityStates;
        this.notaSaidaService = notaSaidaService;
        this.tributacaoService = tributacaoService;
    }

    @EventListener
    public void onItemNotaSaidaSaving(final EntitySavingEvent<ItemNotaSaida> event) {
        ItemNotaSaida itemNotaSaida = event.getEntity();
        NotaSaida notaSaida = itemNotaSaida.getNotaSaida();
        NaturezaOperacao naturezaRef = notaSaida == null ? null : notaSaida.getNatureza();
        // Recarrega natureza/produto com classTrib+cst garantidamente fetched: as
        // referências que chegam aqui (via notaSaida.getNatureza()/item.getProduto())
        // normalmente vêm de um entityPicker de tela, carregado só com "_instance_name"
        // — ler um ManyToOne LAZY não fetched dentro de EntitySavingEvent não faz
        // lazy-load, estoura ValidationException.instantiatingValueholderWithNullSession
        // porque a referência está desanexada da sessão nesse ponto do save (confirmado
        // ao vivo 2026-09-14, com Produto.cst preenchido). Só recarrega quando falta
        // mesmo — a maioria dos saves já vem com o suficiente.
        NaturezaOperacao natureza = carregarNaturezaComTributacao(naturezaRef);
        Produto produto = carregarProdutoComTributacao(itemNotaSaida.getProduto());

        if (event.isNewEntity()) {
            if (itemNotaSaida.getItem() == null) {
                Integer codEmpresa = utilGeralService.getCodEmpresa();
                long item = sequences.createNextValue(Sequence.withName("item_nota_saida_seq_" + codEmpresa));
                itemNotaSaida.setItem(Math.toIntExact(item));
            }
            if (itemNotaSaida.getCfop() == null && naturezaRef != null) {
                itemNotaSaida.setCfop(naturezaRef.getCfop());
            }
            if (itemNotaSaida.getCodClassTrib() == null) {
                itemNotaSaida.setCodClassTrib(tributacaoService.resolverCodClassTrib(natureza, produto));
            }
            if (itemNotaSaida.getCst() == null) {
                String cst = tributacaoService.resolverCstIcms(natureza, produto);
                if (cst != null) {
                    itemNotaSaida.setCst(cst);
                }
            }
        }

        // ICMS recalculado a cada save (criação OU edição, ao contrário de cst/
        // codClassTrib acima, que são snapshot só na criação): quantidade/valorUnitario
        // podem mudar depois, então base/valor têm que acompanhar. CST segue a
        // precedência natureza/produto; alíquota vem sempre de NaturezaOperacao.aliqIcms
        // (decidido com o usuário 2026-09-14 — independe de qual dos dois decidiu o CST).
        //
        // EXCETO pro pseudo item de uma complementar (NfeEmissaoService.gerarItemComplementar):
        // ali quantidade=1/valorUnitario=valorMercadoria é só um jeito de carregar o
        // total no XML, base/valor de ICMS vêm de propósito do cabeçalho da NotaSaida
        // (a complementar pode não ter mercadoria nenhuma, só ajuste de imposto) — deixar
        // esse cálculo automático rodar ali reescreveria os valores certos com 0.
        boolean notaSimples = notaSaida == null || notaSaida.getFinNfe() == null
                || notaSaida.getFinNfe() == FinNfe.NORMAL;
        if (notaSimples) {
            tributacaoService.aplicarCalculoIcms(itemNotaSaida, natureza, produto);
        }
    }

    /**
     * Depois que o item é gravado (criação, edição OU remoção), recalcula os totais do
     * cabeçalho da {@code NotaSaida} (valorMercadoria/baseIcms/valorIcms) somando todos
     * os itens atuais. Usa {@code EntityChangedEvent} (não {@code EntitySavingEvent}) de
     * propósito: só depois do item estar de fato gravado (flush feito, ainda dentro da
     * mesma transação do save da nota) é que somar "todos os itens" inclui o que acabou
     * de mudar — mesmo padrão de {@code LancamentoEventListener} reagindo a saldo.
     */
    @EventListener
    public void onItemNotaSaidaChanged(final EntityChangedEvent<ItemNotaSaida> event) {
        UUID notaSaidaId = notaSaidaIdDoEvento(event);
        if (notaSaidaId != null) {
            notaSaidaService.atualizarValoresCalculados(notaSaidaId);
        }
    }

    private UUID notaSaidaIdDoEvento(EntityChangedEvent<ItemNotaSaida> event) {
        if (event.getType() == EntityChangedEvent.Type.DELETED) {
            // Item já removido — não dá pra carregar por getEntityId(); pega o id da
            // nota que ele apontava antes de ser removido (ver jmix-add-entity-event-listener).
            Id<NotaSaida> notaSaidaId = event.getChanges().getOldReferenceId("notaSaida");
            return notaSaidaId == null ? null : (UUID) notaSaidaId.getValue();
        }
        ItemNotaSaida item = dataManager.load(event.getEntityId()).one();
        NotaSaida notaSaida = item.getNotaSaida();
        return notaSaida == null ? null : (UUID) notaSaida.getId();
    }

    private NaturezaOperacao carregarNaturezaComTributacao(NaturezaOperacao natureza) {
        if (natureza == null) {
            return null;
        }
        if (entityStates.isLoaded(natureza, "classTrib") && entityStates.isLoaded(natureza, "cst")) {
            return natureza;
        }
        return dataManager.load(NaturezaOperacao.class)
                .id(natureza.getId())
                .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE)
                        .add("classTrib", FetchPlan.BASE)
                        .add("cst", FetchPlan.BASE))
                .optional()
                .orElse(natureza);
    }

    private Produto carregarProdutoComTributacao(Produto produto) {
        if (produto == null) {
            return null;
        }
        if (entityStates.isLoaded(produto, "classTrib") && entityStates.isLoaded(produto, "cst")) {
            return produto;
        }
        return dataManager.load(Produto.class)
                .id(produto.getId())
                .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE)
                        .add("classTrib", FetchPlan.BASE)
                        .add("cst", FetchPlan.BASE))
                .optional()
                .orElse(produto);
    }
}
