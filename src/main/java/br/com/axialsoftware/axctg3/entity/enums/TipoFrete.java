package br.com.axialsoftware.axctg3.entity.enums;

import io.jmix.core.metamodel.datatype.EnumClass;

import org.jspecify.annotations.Nullable;


public enum TipoFrete implements EnumClass<Integer> {

    EMITENTE(1),
    DESTINATARIO(2);

    private final Integer id;

    TipoFrete(Integer id) {
        this.id = id;
    }

    public Integer getId() {
        return id;
    }

    @Nullable
    public static TipoFrete fromId(Integer id) {
        for (TipoFrete at : TipoFrete.values()) {
            if (at.getId().equals(id)) {
                return at;
            }
        }
        return null;
    }
}