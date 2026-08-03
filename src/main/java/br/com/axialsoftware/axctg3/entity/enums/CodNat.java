package br.com.axialsoftware.axctg3.entity.enums;

import io.jmix.core.metamodel.datatype.EnumClass;

import org.jspecify.annotations.Nullable;


public enum CodNat implements EnumClass<Integer> {

    CONTAS_DE_ATIVO(1),
    CONTAS_DE_PASSIVO(2),
    PATRIMONIO_LIQUIDO(3),
    CONTAS_DE_RESULTADO(4),
    CONTAS_DE_COMPENSACAO(5),
    OUTRAS(9);

    private final Integer id;

    CodNat(Integer id) {
        this.id = id;
    }

    public Integer getId() {
        return id;
    }

    @Nullable
    public static CodNat fromId(Integer id) {
        for (CodNat at : CodNat.values()) {
            if (at.getId().equals(id)) {
                return at;
            }
        }
        return null;
    }
}