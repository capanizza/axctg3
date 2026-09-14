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
 * criado, editado ou removido, e por {@code NotaSaidaEventListener.onNotaSaidaSaving}
 * (via {@link #calcularValorTotal}) toda vez que a nota é salva, pra cobrir também o
 * caso de o usuário editar frete/seguro/despesas/desconto sem tocar em item nenhum.
 * Decisão 2026-09-14: {@code valorMercadoria}/{@code baseIcms}/{@code valorIcms}/
 * {@code valor} são recalculados; os campos de ST/IPI continuam manuais, fora de escopo
 * de "nota simples".
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
        if (!isNotaSimples(notaSaida)) {
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
        notaSaida.setValor(calcularValorTotal(notaSaida));
        dataManager.saveWithoutReload(notaSaida);
    }

    /**
     * Total da nota (campo {@code valor}, vira {@code vNF} no XML) = mercadoria + frete +
     * seguro + despesas − desconto. O ICMS não entra aqui: é "por dentro" do preço do
     * produto (embutido em {@code valorMercadoria}), diferente do IBS/CBS, que é "por
     * fora" e soma direto no total da NFe em {@code NfeXmlBuilder} (nunca no cabeçalho da
     * NotaSaida). Sem ST/IPI nesta versão — fora de escopo de "nota simples".
     */
    public BigDecimal calcularValorTotal(NotaSaida notaSaida) {
        return nvl(notaSaida.getValorMercadoria())
                .add(nvl(notaSaida.getFrete()))
                .add(nvl(notaSaida.getSeguro()))
                .add(nvl(notaSaida.getDespesas()))
                .subtract(nvl(notaSaida.getDesconto()));
    }

    /** Complementar/ajuste/devolução ficam de fora do cálculo automático — o pseudo item
     * (NfeEmissaoService.gerarItemComplementar) e os campos da tela dedicada de
     * complementar já são preenchidos direto pelo usuário/serviço, não a partir de itens
     * de verdade. */
    public boolean isNotaSimples(NotaSaida notaSaida) {
        return notaSaida.getFinNfe() == null || notaSaida.getFinNfe() == FinNfe.NORMAL;
    }

    private BigDecimal nvl(BigDecimal valor) {
        return valor == null ? BigDecimal.ZERO : valor;
    }
}
