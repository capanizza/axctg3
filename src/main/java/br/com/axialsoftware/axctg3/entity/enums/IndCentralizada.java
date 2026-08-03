package br.com.axialsoftware.axctg3.entity.enums;

import io.jmix.core.metamodel.datatype.EnumClass;

import org.jspecify.annotations.Nullable;


public enum IndCentralizada implements EnumClass<Integer> {

    Escrituracao_centralizada(0),
    Escrituracao_descentralizada(1);

    private final Integer id;

    IndCentralizada(Integer id) {
        this.id = id;
    }

    public Integer getId() {
        return id;
    }

    @Nullable
    public static IndCentralizada fromId(Integer id) {
        for (IndCentralizada at : IndCentralizada.values()) {
            if (at.getId().equals(id)) {
                return at;
            }
        }
        return null;
    }
}