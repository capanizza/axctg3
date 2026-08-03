package br.com.axialsoftware.axctg3.entity.enums;

import io.jmix.core.metamodel.datatype.EnumClass;

import org.jspecify.annotations.Nullable;


public enum IndFinEsc implements EnumClass<Integer> {

    Original(0),
    Substituta(1);

    private final Integer id;

    IndFinEsc(Integer id) {
        this.id = id;
    }

    public Integer getId() {
        return id;
    }

    @Nullable
    public static IndFinEsc fromId(Integer id) {
        for (IndFinEsc at : IndFinEsc.values()) {
            if (at.getId().equals(id)) {
                return at;
            }
        }
        return null;
    }
}