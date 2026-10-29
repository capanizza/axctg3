package br.com.axialsoftware.axctg3.entity.enums;

import io.jmix.core.metamodel.datatype.EnumClass;

import org.jspecify.annotations.Nullable;

/**
 * CST de PIS/COFINS (tabela 4.3.3/4.3.4 do leiaute NFe 4.00) — o mesmo código vale pros
 * dois tributos, por isso um enum só. Decide em {@link
 * br.com.axialsoftware.axctg3.service.fiscal.NfeXmlBuilder} se o item sai em PISAliq,
 * PISNT ou PISOutr (idem COFINS). Só os códigos usados nas saídas das empresas do Axial;
 * valores fixos da Receita, não renumerar.
 */
public enum CstPisCofins implements EnumClass<Integer> {

    TRIBUTAVEL_ALIQUOTA_BASICA(1),
    MONOFASICA_ALIQUOTA_ZERO(4),
    SUBSTITUICAO_TRIBUTARIA(5),
    ALIQUOTA_ZERO(6),
    ISENTA(7),
    SEM_INCIDENCIA(8),
    SUSPENSAO(9),
    OUTRAS_OPERACOES_SAIDA(49);

    private final Integer id;

    CstPisCofins(Integer id) {
        this.id = id;
    }

    public Integer getId() {
        return id;
    }

    @Nullable
    public static CstPisCofins fromId(Integer id) {
        for (CstPisCofins cst : CstPisCofins.values()) {
            if (cst.getId().equals(id)) {
                return cst;
            }
        }
        return null;
    }
}
