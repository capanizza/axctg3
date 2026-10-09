package br.com.axialsoftware.axctg3.entity.enums;

import io.jmix.core.metamodel.datatype.EnumClass;

import org.jspecify.annotations.Nullable;

/**
 * Forma de importação quanto à intermediação ({@code tpIntermedio} do grupo
 * {@code det/prod/DI} do leiaute NFe 4.00). Por conta e ordem e por encomenda exigem o
 * CNPJ/CPF e a UF do adquirente ou encomendante ({@code NfeDi.cnpjCpfAdquirente}/
 * {@code ufTerceiro}). Valores fixos da SEFAZ, não renumerar.
 */
public enum FormaImportacao implements EnumClass<Integer> {

    CONTA_PROPRIA(1),
    CONTA_E_ORDEM(2),
    ENCOMENDA(3);

    private final Integer id;

    FormaImportacao(Integer id) {
        this.id = id;
    }

    public Integer getId() {
        return id;
    }

    @Nullable
    public static FormaImportacao fromId(Integer id) {
        for (FormaImportacao forma : FormaImportacao.values()) {
            if (forma.getId().equals(id)) {
                return forma;
            }
        }
        return null;
    }
}
