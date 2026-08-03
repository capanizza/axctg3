package br.com.axialsoftware.axctg3.entity.enums;

import io.jmix.core.metamodel.datatype.EnumClass;

import org.jspecify.annotations.Nullable;


public enum IndMudancaPc implements EnumClass<Integer> {

    Nao_houve_mudanca(0),
    Houve_mudanca(1);

    private final Integer id;

    IndMudancaPc(Integer id) {
        this.id = id;
    }

    public Integer getId() {
        return id;
    }

    @Nullable
    public static IndMudancaPc fromId(Integer id) {
        for (IndMudancaPc at : IndMudancaPc.values()) {
            if (at.getId().equals(id)) {
                return at;
            }
        }
        return null;
    }
}