package br.com.axialsoftware.axctg3.entity.enums;

import io.jmix.core.metamodel.datatype.EnumClass;

import org.jspecify.annotations.Nullable;


public enum IndSitEsp implements EnumClass<Integer> {

    Cisao(1),
    Fusao(2),
    Incorporacao(3),
    Extincao(4);

    private final Integer id;

    IndSitEsp(Integer id) {
        this.id = id;
    }

    public Integer getId() {
        return id;
    }

    @Nullable
    public static IndSitEsp fromId(Integer id) {
        for (IndSitEsp at : IndSitEsp.values()) {
            if (at.getId().equals(id)) {
                return at;
            }
        }
        return null;
    }
}