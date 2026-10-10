package br.com.axialsoftware.axctg3.entity.enums;

import io.jmix.core.metamodel.datatype.EnumClass;

import org.jspecify.annotations.Nullable;

/**
 * Ciclo de vida de um {@code ImpLote}. O exportador do projeto Axial grava EXPORTANDO ao abrir
 * o lote e PRONTO só depois da última linha — um lote que ficou em EXPORTANDO (túnel caiu,
 * erro no meio) nunca é importado. Os ids são gravados pelo exportador via JDBC: nunca renumerar.
 */
public enum SituacaoLote implements EnumClass<Integer> {

    EXPORTANDO(1),
    PRONTO(2),
    IMPORTADO(3);

    private final Integer id;

    SituacaoLote(Integer id) {
        this.id = id;
    }

    public Integer getId() {
        return id;
    }

    @Nullable
    public static SituacaoLote fromId(Integer id) {
        for (SituacaoLote at : SituacaoLote.values()) {
            if (at.getId().equals(id)) {
                return at;
            }
        }
        return null;
    }
}
