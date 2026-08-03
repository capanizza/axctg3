package br.com.axialsoftware.axctg3.entity.enums;

import io.jmix.core.metamodel.datatype.EnumClass;

import org.jspecify.annotations.Nullable;


public enum Situacao implements EnumClass<Integer> {

    LANCADA(0),
    AUTORIZADA(1),
    PROCESSADA(2),
    APROVADA(3),
    RECEBIDA(4),
    REPROVADA(5);

    private final Integer id;

    Situacao(Integer id) {
        this.id = id;
    }

    public Integer getId() {
        return id;
    }

    @Nullable
    public static Situacao fromId(Integer id) {
        for (Situacao at : Situacao.values()) {
            if (at.getId().equals(id)) {
                return at;
            }
        }
        return null;
    }
}