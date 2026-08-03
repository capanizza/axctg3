package br.com.axialsoftware.axctg3.entity.enums;

import io.jmix.core.metamodel.datatype.EnumClass;

import org.jspecify.annotations.Nullable;


public enum IndGrandePorte implements EnumClass<Integer> {

    Empresa_de_grande_porte(0),
    Nao_e_empresa_de_grande_porte(1);

    private final Integer id;

    IndGrandePorte(Integer id) {
        this.id = id;
    }

    public Integer getId() {
        return id;
    }

    @Nullable
    public static IndGrandePorte fromId(Integer id) {
        for (IndGrandePorte at : IndGrandePorte.values()) {
            if (at.getId().equals(id)) {
                return at;
            }
        }
        return null;
    }
}