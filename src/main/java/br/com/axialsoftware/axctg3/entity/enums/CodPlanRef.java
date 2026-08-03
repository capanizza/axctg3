package br.com.axialsoftware.axctg3.entity.enums;

import io.jmix.core.metamodel.datatype.EnumClass;

import org.jspecify.annotations.Nullable;


public enum CodPlanRef implements EnumClass<Integer> {

    PJ_em_geral_lucro_real(1),
    PJ_em_geral_lucro_presumido(2),
    Financeiras_lucro_real(3),
    Seguradoras_lucro_real(4),
    Imunes_e_isentas_em_geral(5),
    Imunes_e_isentas_financeiras(6),
    Imunes_e_isentas_seguradoras(7),
    Previdencia_complementar(8),
    Partidos_politicos(9),
    Financeiras_lucro_presumido(10);

    private final Integer id;

    CodPlanRef(Integer id) {
        this.id = id;
    }

    public Integer getId() {
        return id;
    }

    @Nullable
    public static CodPlanRef fromId(Integer id) {
        for (CodPlanRef at : CodPlanRef.values()) {
            if (at.getId().equals(id)) {
                return at;
            }
        }
        return null;
    }
}