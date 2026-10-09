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
 * Adição ({@code det/prod/DI/adi}) de uma {@link NfeDi}.
 */
@JmixEntity
@Table(name = "NFE_DI_ADICAO", indexes = {
        @Index(name = "IDX_NFE_DI_ADICAO_NFE_DI", columnList = "NFE_DI_ID")
})
@Entity
public class NfeDiAdicao {
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

    @JoinColumn(name = "NFE_DI_ID", nullable = false)
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private NfeDi nfeDi;

    // nAdicao — opcional no leiaute (a DUIMP não tem adição)
    @Column(name = "NUMERO_ADICAO")
    private Integer numeroAdicao;

    // nSeqAdic — sequencial do item dentro da adição
    @InstanceName
    @Column(name = "SEQUENCIAL")
    private Integer sequencial;

    // cFabricante — código do fabricante estrangeiro
    @Column(name = "COD_FABRICANTE", length = 60)
    private String codFabricante;

    // vDescDI — desconto do item da DI/adição
    @NumberFormat(pattern = "###,###,##0.00", decimalSeparator = ",", groupingSeparator = ".")
    @Column(name = "VALOR_DESCONTO", precision = 19, scale = 2)
    private BigDecimal valorDesconto = BigDecimal.ZERO;

    // nDraw — número do ato concessório de drawback
    @Column(name = "NUM_DRAWBACK", length = 20)
    private String numDrawback;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public OffsetDateTime getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(OffsetDateTime createdDate) {
        this.createdDate = createdDate;
    }

    public String getLastModifiedBy() {
        return lastModifiedBy;
    }

    public void setLastModifiedBy(String lastModifiedBy) {
        this.lastModifiedBy = lastModifiedBy;
    }

    public OffsetDateTime getLastModifiedDate() {
        return lastModifiedDate;
    }

    public void setLastModifiedDate(OffsetDateTime lastModifiedDate) {
        this.lastModifiedDate = lastModifiedDate;
    }

    public String getDeletedBy() {
        return deletedBy;
    }

    public void setDeletedBy(String deletedBy) {
        this.deletedBy = deletedBy;
    }

    public OffsetDateTime getDeletedDate() {
        return deletedDate;
    }

    public void setDeletedDate(OffsetDateTime deletedDate) {
        this.deletedDate = deletedDate;
    }

    public NfeDi getNfeDi() {
        return nfeDi;
    }

    public void setNfeDi(NfeDi nfeDi) {
        this.nfeDi = nfeDi;
    }

    public Integer getNumeroAdicao() {
        return numeroAdicao;
    }

    public void setNumeroAdicao(Integer numeroAdicao) {
        this.numeroAdicao = numeroAdicao;
    }

    public Integer getSequencial() {
        return sequencial;
    }

    public void setSequencial(Integer sequencial) {
        this.sequencial = sequencial;
    }

    public String getCodFabricante() {
        return codFabricante;
    }

    public void setCodFabricante(String codFabricante) {
        this.codFabricante = codFabricante;
    }

    public BigDecimal getValorDesconto() {
        return valorDesconto;
    }

    public void setValorDesconto(BigDecimal valorDesconto) {
        this.valorDesconto = valorDesconto;
    }

    public String getNumDrawback() {
        return numDrawback;
    }

    public void setNumDrawback(String numDrawback) {
        this.numDrawback = numDrawback;
    }
}
