package br.com.axialsoftware.axctg3.service.fiscal;

import br.com.axialsoftware.axctg3.entity.enums.FinNfe;
import br.com.axialsoftware.axctg3.entity.fiscal.ItemNotaSaida;
import br.com.axialsoftware.axctg3.entity.fiscal.NotaSaida;
import io.jmix.core.DataManager;
import io.jmix.core.FetchPlan;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Recalcula os totais do cabeçalho de {@link NotaSaida} (aba "Valores calculados" na
 * tela) somando os itens atuais — chamado por
 * {@code ItemNotaSaidaEventListener.onItemNotaSaidaChanged} toda vez que um item é
 * criado, editado ou removido. Decisão 2026-09-14: só {@code valorMercadoria}/
 * {@code baseIcms}/{@code valorIcms} são recalculados (ICMS é a única coisa que o item
 * calcula automaticamente por enquanto — ver {@code ItemNotaSaidaEventListener}); {@code
 * valor} (total da nota, inclui frete/seguro/despesas/desconto) e os campos de
 * ST/IPI continuam manuais, fora de escopo de "nota simples".
 */
@Service
public class NotaSaidaService {

    private final DataManager dataManager;

    public NotaSaidaService(DataManager dataManager) {
        this.dataManager = dataManager;
    }

    public void atualizarValoresCalculados(UUID notaSaidaId) {
        NotaSaida notaSaida = dataManager.load(NotaSaida.class)
                .id(notaSaidaId)
                .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE)
                        .add("itens", FetchPlan.BASE))
                .optional()
                .orElse(null);
        if (notaSaida == null) {
            // nota removida no meio do caminho (ex.: exclusão em cascata) — nada a atualizar
            return;
        }
        if (notaSaida.getFinNfe() != null && notaSaida.getFinNfe() != FinNfe.NORMAL) {
            // Complementar/ajuste/devolução: o pseudo item (NfeEmissaoService.
            // gerarItemComplementar) só espelha o que já está no cabeçalho, não o
            // contrário — nunca recalcula esses casos a partir dos itens.
            return;
        }

        BigDecimal valorMercadoria = BigDecimal.ZERO;
        BigDecimal baseIcms = BigDecimal.ZERO;
        BigDecimal valorIcms = BigDecimal.ZERO;
        for (ItemNotaSaida item : notaSaida.getItens()) {
            valorMercadoria = valorMercadoria.add(nvl(item.getSubTotal()));
            baseIcms = baseIcms.add(nvl(item.getBaseIcms()));
            valorIcms = valorIcms.add(nvl(item.getValorIcms()));
        }

        notaSaida.setValorMercadoria(valorMercadoria);
        notaSaida.setBaseIcms(baseIcms);
        notaSaida.setValorIcms(valorIcms);
        dataManager.saveWithoutReload(notaSaida);
    }

    private BigDecimal nvl(BigDecimal valor) {
        return valor == null ? BigDecimal.ZERO : valor;
    }
}
