package br.com.axialsoftware.axctg3.entity.enums;

import io.jmix.core.metamodel.datatype.EnumClass;

import org.jspecify.annotations.Nullable;

/**
 * Ambiente de emissão (tpAmb do leiaute NFe 4.00: 1=Produção, 2=Homologação). Campo em
 * {@code Empresa} — decide qual endpoint SEFAZ {@code NfeWebserviceClient} chama. Ver
 * docs/EMISSAO-NFE.md.
 */
public enum AmbienteNfe implements EnumClass<Integer> {

    PRODUCAO(1),
    HOMOLOGACAO(2);

    private final Integer id;

    AmbienteNfe(Integer id) {
        this.id = id;
    }

    public Integer getId() {
        return id;
    }

    @Nullable
    public static AmbienteNfe fromId(Integer id) {
        for (AmbienteNfe at : AmbienteNfe.values()) {
            if (at.getId().equals(id)) {
                return at;
            }
        }
        return null;
    }
}
