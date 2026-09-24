package br.com.axialsoftware.axctg3.service.fiscal;

import br.com.axialsoftware.axctg3.entity.fiscal.NaturezaOperacao;
import br.com.axialsoftware.axctg3.entity.tabelas.TabelaIbpt;
import io.jmix.core.DataManager;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Valor aproximado dos tributos (Lei 12.741/2012, "De Olho no Imposto") — tag
 * {@code vTotTrib} de cada {@code det/imposto} e de {@code ICMSTot}, mais o texto em
 * {@code infCpl}. Percentuais da {@link TabelaIbpt} da UF do emitente, pelo NCM do produto
 * (tipo 0, sem exceção TIPI — Produto não guarda EX), aplicados sobre o {@code vItem}.
 *
 * <p>Regras replicadas do sistema intermediário Axial (NotaSaidaGridController): só entra
 * item de nota com natureza de venda, fora das remessas/retornos da lista abaixo; soma a
 * alíquota federal nacional + estadual (sem municipal: NFe de mercadoria). Federal
 * nacional e não "importados" porque o {@code orig} do ICMS sai sempre 0 (nacional) hoje.
 * NCM sem linha na tabela dá zero, como no Axial.
 */
@Service
public class TributosAproximadosService {

    // Remessa/retorno de industrialização, conserto, simples faturamento — não são venda
    // ao consumidor final, mesmo quando a natureza da nota é de venda (lista do Axial).
    private static final Set<Integer> CFOPS_SEM_TRIBUTOS = Set.of(
            5124, 5125, 6124, 6125, 5902, 6902, 5925, 6925, 1901, 5922);

    private final DataManager dataManager;

    public TributosAproximadosService(DataManager dataManager) {
        this.dataManager = dataManager;
    }

    public record Valor(BigDecimal federal, BigDecimal estadual) {
        public static final Valor ZERO = new Valor(BigDecimal.ZERO, BigDecimal.ZERO);

        public BigDecimal total() {
            return federal.add(estadual);
        }

        public Valor somar(Valor outro) {
            return new Valor(federal.add(outro.federal), estadual.add(outro.estadual));
        }
    }

    public boolean aplicavel(NaturezaOperacao natureza, Integer cfop) {
        return natureza != null && Boolean.TRUE.equals(natureza.getVenda())
                && (cfop == null || !CFOPS_SEM_TRIBUTOS.contains(cfop));
    }

    /** Linhas da tabela por NCM, numa consulta só pra nota inteira. */
    public Map<String, TabelaIbpt> carregar(String uf, Collection<String> ncms) {
        Map<String, TabelaIbpt> porNcm = new HashMap<>();
        if (uf == null || ncms.isEmpty()) {
            return porNcm;
        }
        List<TabelaIbpt> linhas = dataManager.load(TabelaIbpt.class)
                .query("select e from TabelaIbpt e where e.uf = :uf and e.tipo = 0 and e.ex is null"
                        + " and e.codigo in :ncms")
                .parameter("uf", uf.toUpperCase(Locale.ROOT))
                .parameter("ncms", ncms)
                .list();
        linhas.forEach(t -> porNcm.put(t.getCodigo(), t));
        return porNcm;
    }

    public Valor calcular(TabelaIbpt linha, BigDecimal base) {
        if (linha == null || base == null || base.signum() == 0) {
            return Valor.ZERO;
        }
        return new Valor(percentual(base, linha.getAliqNacionalFederal()),
                percentual(base, linha.getAliqEstadual()));
    }

    /**
     * Texto do infCpl no formato sugerido pela AFRAC no manual do IBPT (valor separado por
     * ente tributante, como a lei exige) mais o percentual sobre o total dos itens.
     * {@code null} quando não há valor a informar.
     */
    public String textoInfCpl(Valor total, BigDecimal baseTotal, String chave) {
        if (total.total().signum() == 0 || baseTotal == null || baseTotal.signum() == 0) {
            return null;
        }
        BigDecimal pct = total.total().multiply(BigDecimal.valueOf(100))
                .divide(baseTotal, 2, RoundingMode.HALF_UP);
        return "Valor aproximado dos tributos: R$ " + moeda(total.total())
                + " (" + moeda(pct) + "%) - Federal R$ " + moeda(total.federal())
                + " e Estadual R$ " + moeda(total.estadual())
                + " - Fonte: IBPT" + (chave == null ? "" : " " + chave);
    }

    private BigDecimal percentual(BigDecimal base, BigDecimal aliquota) {
        if (aliquota == null) {
            return BigDecimal.ZERO;
        }
        return base.multiply(aliquota).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    private String moeda(BigDecimal valor) {
        DecimalFormat df = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.of("pt", "BR")));
        return df.format(valor);
    }
}
