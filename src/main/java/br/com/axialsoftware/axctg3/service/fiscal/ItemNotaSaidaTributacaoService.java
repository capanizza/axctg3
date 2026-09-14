package br.com.axialsoftware.axctg3.service.fiscal;

import br.com.axialsoftware.axctg3.entity.fiscal.ItemNotaSaida;
import br.com.axialsoftware.axctg3.entity.fiscal.NaturezaOperacao;
import br.com.axialsoftware.axctg3.entity.fiscal.Produto;
import br.com.axialsoftware.axctg3.entity.tabelas.ClassTrib;
import br.com.axialsoftware.axctg3.entity.tabelas.Cst;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Resolução de CST/cClassTrib e cálculo de ICMS de um {@link ItemNotaSaida} — extraído de
 * {@code ItemNotaSaidaEventListener} em 2026-09-14 pra ser reaproveitado também pela tela
 * (preview ao vivo enquanto o usuário digita quantidade/valorUnitário/produto, antes de
 * salvar — ver {@code ItemNotaSaidaDetailView}). O listener continua sendo quem GRAVA o
 * resultado (é o único lugar com autoridade sobre o valor persistido); esta classe só
 * calcula.
 */
@Service
public class ItemNotaSaidaTributacaoService {

    // CST-IBS/CBS "tributação integral", os 3 primeiros dígitos de um cClassTrib de 6
    // dígitos (ex.: 000000..000499) — ver docs/REFORMA-TRIBUTARIA-IBS-CBS.md. Natureza
    // com esse CST é "rasa": não fixa um tratamento tributário próprio, então quem decide
    // é o produto do item.
    private static final int CST_TRIBUTACAO_INTEGRAL = 0;

    // CST de ICMS "tributação integral" (Cst.codigo, não o CST-IBS/CBS acima — catálogos
    // diferentes, mesmo nome). Mesma ideia de "rasa": natureza com esse CST (ou sem Cst
    // nenhum) não fixa tratamento próprio, quem decide é o Cst do produto.
    private static final String CST_ICMS_TRIBUTACAO_INTEGRAL = "00";

    /** CST + alíquota resolvidos do MESMO lado (natureza ou produto) — decidido com o
     * usuário 2026-09-14 que a alíquota nunca pode vir de um lado diferente de quem
     * decidiu o CST, pra não ter CST "00" com a alíquota do produto errado. */
    public record TributacaoIcms(String cst, BigDecimal aliqIcms) {
    }

    public TributacaoIcms resolverTributacaoIcms(NaturezaOperacao natureza, Produto produto) {
        Cst cstNatureza = natureza == null ? null : natureza.getCst();
        boolean rasa = cstNatureza == null || CST_ICMS_TRIBUTACAO_INTEGRAL.equals(cstNatureza.getCodigo());
        if (!rasa) {
            return new TributacaoIcms(cstNatureza.getCodigo(), natureza.getAliqIcms());
        }
        Cst cstProduto = produto == null ? null : produto.getCst();
        if (cstProduto == null) {
            return null;
        }
        return new TributacaoIcms(cstProduto.getCodigo(), produto.getAliqIcms());
    }

    /**
     * Precedência do CST de ICMS — mesma regra de {@link #resolverCodClassTrib}, catálogo
     * diferente (ver Javadoc de {@link Cst}): a natureza decide quando fixa um CST próprio
     * (qualquer um diferente de "00"); quando a natureza é "rasa" (Cst "00" ou sem Cst),
     * quem decide é o Cst do produto. Sem nenhum dos dois, retorna {@code null} — o
     * default final ("40"/"102" conforme o regime) é aplicado só na emissão, em
     * {@code NfeXmlBuilder.resolverCstIcms}, pra não gravar um valor no item que a UI não
     * mostrou ao usuário.
     */
    public String resolverCstIcms(NaturezaOperacao natureza, Produto produto) {
        TributacaoIcms tributacao = resolverTributacaoIcms(natureza, produto);
        return tributacao == null ? null : tributacao.cst();
    }

    /**
     * Regra de precedência do cClassTrib decidida em docs/REFORMA-TRIBUTARIA-IBS-CBS.md
     * (fora do escopo aqui: devolução, que deve espelhar a operação original — ainda não
     * modelada). {@code NaturezaOperacao.classTrib}/{@code Produto.classTrib} são
     * referências reais pra {@link ClassTrib} (cadastro); o valor gravado aqui em
     * {@code ItemNotaSaida.codClassTrib} é só o código numérico resolvido — snapshot
     * congelado, não uma referência viva (mesmo motivo de {@code NfeItem.codClassTrib}
     * ser String, não FK).
     */
    public Integer resolverCodClassTrib(NaturezaOperacao natureza, Produto produto) {
        ClassTrib classTribNatureza = natureza == null ? null : natureza.getClassTrib();
        // Natureza sem ClassTrib ainda (cadastro pendente de migração) é tratada como
        // "rasa", mesma consequência prática de CST 000: nada de especial foi fixado na
        // natureza, então o produto decide.
        int cst = classTribNatureza == null ? CST_TRIBUTACAO_INTEGRAL : classTribNatureza.getCst();
        if (cst == CST_TRIBUTACAO_INTEGRAL) {
            ClassTrib classTribProduto = produto == null ? null : produto.getClassTrib();
            return classTribProduto == null ? null : classTribProduto.getCodigo();
        }
        return classTribNatureza.getCodigo();
    }

    /**
     * Base/alíquota/valor do ICMS do item, calculados a partir da precedência acima e do
     * subtotal do item (quantidade×valorUnitario) — grava direto no {@code item} passado
     * (chamador decide se persiste ou só usa como preview de tela). Sem redução de base
     * nem ICMS-ST nesta versão (fora do escopo "nota simples" de 2026-09-14).
     */
    public void aplicarCalculoIcms(ItemNotaSaida item, NaturezaOperacao natureza, Produto produto) {
        TributacaoIcms tributacao = resolverTributacaoIcms(natureza, produto);
        BigDecimal aliqIcms = tributacao == null ? BigDecimal.ZERO : tributacao.aliqIcms();
        BigDecimal baseIcms = item.getSubTotal() == null ? BigDecimal.ZERO : item.getSubTotal();
        BigDecimal valorIcms = baseIcms.multiply(aliqIcms)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        item.setAliqIcms(aliqIcms);
        item.setBaseIcms(baseIcms);
        item.setValorIcms(valorIcms);
    }
}
