package br.com.axialsoftware.axctg3.entity.enums;

import io.jmix.core.metamodel.datatype.EnumClass;

import org.jspecify.annotations.Nullable;

/**
 * Finalidade da NFe ({@code finNFe} no leiaute 4.00, grupo {@code ide}). Códigos fixos do
 * schema — nunca renumerar. {@code AJUSTE}/{@code DEVOLUCAO} existem só por completude do
 * código oficial; esta versão do projeto só emite/valida {@code NORMAL} e
 * {@code COMPLEMENTAR} (ver {@link br.com.axialsoftware.axctg3.service.fiscal.NfeEmissaoService}
 * — devolução fica fora de escopo, mesma decisão já registrada em docs/EMISSAO-NFE.md).
 */
public enum FinNfe implements EnumClass<Integer> {

    NORMAL(1),
    COMPLEMENTAR(2),
    AJUSTE(3),
    DEVOLUCAO(4);

    private final Integer id;

    FinNfe(Integer id) {
        this.id = id;
    }

    public Integer getId() {
        return id;
    }

    @Nullable
    public static FinNfe fromId(Integer id) {
        for (FinNfe at : FinNfe.values()) {
            if (at.getId().equals(id)) {
                return at;
            }
        }
        return null;
    }
}
