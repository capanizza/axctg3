package br.com.axialsoftware.axctg3.entity.enums;

import io.jmix.core.metamodel.datatype.EnumClass;

import org.jspecify.annotations.Nullable;

/**
 * Via de transporte internacional da Declaração de Importação ({@code tpViaTransp} do
 * grupo {@code det/prod/DI} do leiaute NFe 4.00). Valores fixos da SEFAZ, não renumerar
 * (mesma convenção de enum SPED/NFe do projeto). O AFRMM ({@code vAFRMM}) só se aplica
 * à via marítima.
 */
public enum ViaTransporteInternacional implements EnumClass<Integer> {

    MARITIMA(1),
    FLUVIAL(2),
    LACUSTRE(3),
    AEREA(4),
    POSTAL(5),
    FERROVIARIA(6),
    RODOVIARIA(7),
    CONDUTO_REDE_TRANSMISSAO(8),
    MEIOS_PROPRIOS(9),
    ENTRADA_SAIDA_FICTA(10),
    COURIER(11),
    EM_MAOS(12),
    POR_REBOQUE(13);

    private final Integer id;

    ViaTransporteInternacional(Integer id) {
        this.id = id;
    }

    public Integer getId() {
        return id;
    }

    @Nullable
    public static ViaTransporteInternacional fromId(Integer id) {
        for (ViaTransporteInternacional via : ViaTransporteInternacional.values()) {
            if (via.getId().equals(id)) {
                return via;
            }
        }
        return null;
    }
}
