package br.com.axialsoftware.axctg3.entity.fiscal;

import io.jmix.core.entity.annotation.JmixGeneratedValue;
import io.jmix.core.entity.annotation.JmixId;
import io.jmix.core.metamodel.annotation.InstanceName;
import io.jmix.core.metamodel.annotation.JmixEntity;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Uma parcela do quadro "FATURA/DUPLICATAS" do DANFE — DTO não persistente
 * {@code @JmixEntity}, mesmo molde de {@link DanfeItemDto}, usado como bean do
 * {@code jr:list} do {@code Danfe.jrxml} (ver
 * {@link br.com.axialsoftware.axctg3.service.fiscal.NfeDanfeService}). {@code dataVenc}
 * já vem formatada como String ("dd/MM/yyyy") — o atributo {@code pattern} do
 * JasperReports não formata campo {@code java.time.LocalDate} como esperado (testado:
 * sai em ISO "2026-09-02"), diferente de {@code BigDecimal}, que formata certinho.
 */
@JmixEntity
public class DanfeDuplicataDto {
    @JmixGeneratedValue
    @JmixId
    private UUID id;

    @InstanceName
    private String numDup;

    private String dataVenc;

    private BigDecimal valorDup;

    public String getNumDup() {
        return numDup;
    }

    public void setNumDup(String numDup) {
        this.numDup = numDup;
    }

    public String getDataVenc() {
        return dataVenc;
    }

    public void setDataVenc(String dataVenc) {
        this.dataVenc = dataVenc;
    }

    public BigDecimal getValorDup() {
        return valorDup;
    }

    public void setValorDup(BigDecimal valorDup) {
        this.valorDup = valorDup;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }
}
