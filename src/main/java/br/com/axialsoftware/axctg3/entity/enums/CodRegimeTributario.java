package br.com.axialsoftware.axctg3.entity.enums;

import io.jmix.core.metamodel.datatype.EnumClass;

import org.jspecify.annotations.Nullable;

/**
 * Código de Regime Tributário (CRT) — mesmo código do campo {@code CRT} do grupo
 * {@code emit} do leiaute NFe 4.00, já usado como {@code Integer} solto em
 * {@link br.com.axialsoftware.axctg3.entity.fiscal.Nfe#getEmitCrt()}. Aqui vira campo
 * de verdade em {@code Empresa} — decide qual bloco de ICMS a NFe emitida usa (CST vs
 * CSOSN). Ver docs/EMISSAO-NFE.md.
 */
public enum CodRegimeTributario implements EnumClass<Integer> {

    SIMPLES_NACIONAL(1),
    SIMPLES_NACIONAL_EXCESSO_SUBLIMITE(2),
    REGIME_NORMAL(3);

    private final Integer id;

    CodRegimeTributario(Integer id) {
        this.id = id;
    }

    public Integer getId() {
        return id;
    }

    @Nullable
    public static CodRegimeTributario fromId(Integer id) {
        for (CodRegimeTributario at : CodRegimeTributario.values()) {
            if (at.getId().equals(id)) {
                return at;
            }
        }
        return null;
    }
}
