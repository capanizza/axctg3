package br.com.axialsoftware.axctg3.entity.enums;

import io.jmix.core.metamodel.datatype.EnumClass;

import org.jspecify.annotations.Nullable;

/**
 * Modalidade do frete (modFrete do leiaute NFe 4.00, dentro de {@code transp}) — decide
 * se/como o grupo {@code transporta} é montado em {@link
 * br.com.axialsoftware.axctg3.service.fiscal.NfeXmlBuilder#construirTransp}. Valores
 * fixos da SEFAZ, não renumerar (mesma convenção de enum SPED/NFe do projeto).
 */
public enum ModFrete implements EnumClass<Integer> {

    CONTRATACAO_REMETENTE(0),
    CONTRATACAO_DESTINATARIO(1),
    CONTRATACAO_TERCEIROS(2),
    PROPRIO_REMETENTE(3),
    PROPRIO_DESTINATARIO(4),
    SEM_TRANSPORTE(9);

    private final Integer id;

    ModFrete(Integer id) {
        this.id = id;
    }

    public Integer getId() {
        return id;
    }

    @Nullable
    public static ModFrete fromId(Integer id) {
        for (ModFrete mf : ModFrete.values()) {
            if (mf.getId().equals(id)) {
                return mf;
            }
        }
        return null;
    }
}
