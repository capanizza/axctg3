package br.com.axialsoftware.axctg3.entity.enums;

import io.jmix.core.metamodel.datatype.EnumClass;

import org.jspecify.annotations.Nullable;


public enum IndGrandePorte implements EnumClass<Integer> {

    // Ids conferidos contra o Manual de Orientação do Leiaute 9 da ECD (RFB, jan/2026),
    // registro 0000 campo 16: "0 Empresa não é entidade sujeita a auditoria independente.
    // 1 Empresa é entidade sujeita a auditoria independente [...]". Estavam invertidos —
    // achado pesquisando por que o PVA cobrava o registro J935 (só obrigatório quando esse
    // campo = 1) mesmo com o valor gravado como 0.
    Nao_e_empresa_de_grande_porte(0),
    Empresa_de_grande_porte(1);

    private final Integer id;

    IndGrandePorte(Integer id) {
        this.id = id;
    }

    public Integer getId() {
        return id;
    }

    @Nullable
    public static IndGrandePorte fromId(Integer id) {
        for (IndGrandePorte at : IndGrandePorte.values()) {
            if (at.getId().equals(id)) {
                return at;
            }
        }
        return null;
    }
}