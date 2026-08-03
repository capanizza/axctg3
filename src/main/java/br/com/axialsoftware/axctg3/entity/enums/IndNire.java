package br.com.axialsoftware.axctg3.entity.enums;

import io.jmix.core.metamodel.datatype.EnumClass;

import org.jspecify.annotations.Nullable;


public enum IndNire implements EnumClass<Integer> {

    Nao_possui_NIRE(0),
    Possui_NIRE(1);

    private final Integer id;

    IndNire(Integer id) {
        this.id = id;
    }

    public Integer getId() {
        return id;
    }

    @Nullable
    public static IndNire fromId(Integer id) {
        for (IndNire at : IndNire.values()) {
            if (at.getId().equals(id)) {
                return at;
            }
        }
        return null;
    }
}