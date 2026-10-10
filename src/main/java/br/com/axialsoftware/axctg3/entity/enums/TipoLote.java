package br.com.axialsoftware.axctg3.entity.enums;

import io.jmix.core.metamodel.datatype.EnumClass;

import org.jspecify.annotations.Nullable;

/**
 * O que um {@code ImpLote} traz do sistema legado. O id é gravado pelo exportador do projeto
 * Axial direto no banco (JDBC) — nunca renumerar.
 */
public enum TipoLote implements EnumClass<Integer> {

    PLANO_CONTAS(1);

    private final Integer id;

    TipoLote(Integer id) {
        this.id = id;
    }

    public Integer getId() {
        return id;
    }

    @Nullable
    public static TipoLote fromId(Integer id) {
        for (TipoLote at : TipoLote.values()) {
            if (at.getId().equals(id)) {
                return at;
            }
        }
        return null;
    }
}
