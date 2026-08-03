package br.com.axialsoftware.axctg3.entity.enums;

import io.jmix.core.metamodel.datatype.EnumClass;

import org.jspecify.annotations.Nullable;


public enum IndSitIniPer implements EnumClass<Integer> {

    Normal(0),
    Abertura(1),
    Resultante_de_cisao_fusao_ou_remanescente_de_cisao_ou_realizou_incorporacao(2),
    Inicio_de_obrigatoriedade_de_entrega_da_ECD_no_curso_do_ano_calendario(3);

    private final Integer id;

    IndSitIniPer(Integer id) {
        this.id = id;
    }

    public Integer getId() {
        return id;
    }

    @Nullable
    public static IndSitIniPer fromId(Integer id) {
        for (IndSitIniPer at : IndSitIniPer.values()) {
            if (at.getId().equals(id)) {
                return at;
            }
        }
        return null;
    }
}