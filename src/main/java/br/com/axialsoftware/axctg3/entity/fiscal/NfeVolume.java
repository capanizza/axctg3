package br.com.axialsoftware.axctg3.entity.fiscal;

import io.jmix.core.annotation.DeletedBy;
import io.jmix.core.annotation.DeletedDate;
import io.jmix.core.entity.annotation.JmixGeneratedValue;
import io.jmix.core.metamodel.annotation.InstanceName;
import io.jmix.core.metamodel.annotation.JmixEntity;
import io.jmix.core.metamodel.annotation.NumberFormat;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Volume de transporte ({@code transp/vol}) de uma {@link Nfe}.
 */
@JmixEntity
@Table(name = "NFE_VOLUME", indexes = {
        @Index(name = "IDX_NFE_VOLUME_NFE", columnList = "NFE_ID")
})
@Entity
public class NfeVolume {
    @JmixGeneratedValue
    @Column(name = "ID", nullable = false)
    @Id
    private UUID id;

    @Column(name = "VERSION", nullable = false)
    @Version
    private Integer version;

    @CreatedBy
    @Column(name = "CREATED_BY")
    private String createdBy;

    @CreatedDate
    @Column(name = "CREATED_DATE")
    private OffsetDateTime createdDate;

    @LastModifiedBy
    @Column(name = "LAST_MODIFIED_BY")
    private String lastModifiedBy;

    @LastModifiedDate
    @Column(name = "LAST_MODIFIED_DATE")
    private OffsetDateTime lastModifiedDate;

    @DeletedBy
    @Column(name = "DELETED_BY")
    private String deletedBy;

    @DeletedDate
    @Column(name = "DELETED_DATE")
    private OffsetDateTime deletedDate;

    @JoinColumn(name = "NFE_ID", nullable = false)
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Nfe nfe;

    // qVol
    @InstanceName
    @Column(name = "QUANT_VOL")
    private Integer quantVol;

    @Column(name = "ESPECIE", length = 60)
    private String especie;

    @Column(name = "MARCA", length = 60)
    private String marca;

    // nVol
    @Column(name = "NUMERACAO", length = 60)
    private String numeracao;

    @NumberFormat(pattern = "###,###,##0.000", decimalSeparator = ",", groupingSeparator = ".")
    @Column(name = "PESO_LIQUIDO", precision = 19, scale = 3)
    private BigDecimal pesoLiquido = BigDecimal.ZERO;

    @NumberFormat(pattern = "###,###,##0.000", decimalSeparator = ",", groupingSeparator = ".")
    @Column(name = "PESO_BRUTO", precision = 19, scale = 3)
    private BigDecimal pesoBruto = BigDecimal.ZERO;

    public BigDecimal getPesoBruto() {
        return pesoBruto;
    }

    public void setPesoBruto(BigDecimal pesoBruto) {
        this.pesoBruto = pesoBruto;
    }

    public BigDecimal getPesoLiquido() {
        return pesoLiquido;
    }

    public void setPesoLiquido(BigDecimal pesoLiquido) {
        this.pesoLiquido = pesoLiquido;
    }

    public String getNumeracao() {
        return numeracao;
    }

    public void setNumeracao(String numeracao) {
        this.numeracao = numeracao;
    }

    public String getMarca() {
        return marca;
    }

    public void setMarca(String marca) {
        this.marca = marca;
    }

    public String getEspecie() {
        return especie;
    }

    public void setEspecie(String especie) {
        this.especie = especie;
    }

    public Integer getQuantVol() {
        return quantVol;
    }

    public void setQuantVol(Integer quantVol) {
        this.quantVol = quantVol;
    }

    public @NotNull Nfe getNfe() {
        return nfe;
    }

    public void setNfe(@NotNull Nfe nfe) {
        this.nfe = nfe;
    }

    public OffsetDateTime getDeletedDate() {
        return deletedDate;
    }

    public void setDeletedDate(OffsetDateTime deletedDate) {
        this.deletedDate = deletedDate;
    }

    public String getDeletedBy() {
        return deletedBy;
    }

    public void setDeletedBy(String deletedBy) {
        this.deletedBy = deletedBy;
    }

    public OffsetDateTime getLastModifiedDate() {
        return lastModifiedDate;
    }

    public void setLastModifiedDate(OffsetDateTime lastModifiedDate) {
        this.lastModifiedDate = lastModifiedDate;
    }

    public String getLastModifiedBy() {
        return lastModifiedBy;
    }

    public void setLastModifiedBy(String lastModifiedBy) {
        this.lastModifiedBy = lastModifiedBy;
    }

    public OffsetDateTime getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(OffsetDateTime createdDate) {
        this.createdDate = createdDate;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }
}
