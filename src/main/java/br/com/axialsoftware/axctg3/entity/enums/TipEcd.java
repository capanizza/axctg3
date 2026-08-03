package br.com.axialsoftware.axctg3.entity.enums;

import io.jmix.core.metamodel.datatype.EnumClass;

import org.jspecify.annotations.Nullable;


public enum TipEcd implements EnumClass<Integer> {

    Nao_participante_de_SCP(0),
    Participante_de_SCP_como_socio(1),
    SCP(2);

    private final Integer id;

    TipEcd(Integer id) {
        this.id = id;
    }

    public Integer getId() {
        return id;
    }

    @Nullable
    public static TipEcd fromId(Integer id) {
        for (TipEcd at : TipEcd.values()) {
            if (at.getId().equals(id)) {
                return at;
            }
        }
        return null;
    }
}