package br.com.axialsoftware.axctg3.entity.fiscal;

import io.jmix.core.MetadataTools;
import io.jmix.core.annotation.DeletedBy;
import io.jmix.core.annotation.DeletedDate;
import io.jmix.core.entity.annotation.JmixGeneratedValue;
import io.jmix.core.metamodel.annotation.*;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@JmixEntity
@Table(name = "SALDO_PRODUTO", indexes = {
        @Index(name = "IDX_SALDO_PRODUTO_DATA", columnList = "DATA"),
        @Index(name = "IDX_SALDO_PRODUTO_PRODUTO", columnList = "PRODUTO_ID")
})
@Entity
public class SaldoProduto {
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

    @JoinColumn(name = "PRODUTO_ID", nullable = false)
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Produto produto;

    @Column(name = "DATA", nullable = false)
    @NotNull
    private LocalDate data;

    @NumberFormat(pattern = "###,###,##0.000", decimalSeparator = ",", groupingSeparator = ".")
    @Column(name = "SALDO", precision = 19, scale = 3, nullable = false)
    @NotNull
    private BigDecimal saldo = BigDecimal.ZERO;

    @NumberFormat(pattern = "###,###,##0.000000", decimalSeparator = ",", groupingSeparator = ".")
    @Column(name = "VALOR_UNITARIO", precision = 19, scale = 6, nullable = false)
    @NotNull
    private BigDecimal valorUnitario = BigDecimal.ZERO;

    @NumberFormat(pattern = "###,###,##0.00", decimalSeparator = ",", groupingSeparator = ".")
    @Column(name = "VALOR", precision = 19, scale = 2, nullable = false)
    @NotNull
    private BigDecimal valor = BigDecimal.ZERO;

    public BigDecimal getValor() {
        return valor;
    }

    public void setValor(BigDecimal valor) {
        this.valor = valor;
    }

    public BigDecimal getValorUnitario() {
        return valorUnitario;
    }

    public void setValorUnitario(BigDecimal valorUnitario) {
        this.valorUnitario = valorUnitario;
    }

    public BigDecimal getSaldo() {
        return saldo;
    }

    public void setSaldo(BigDecimal saldo) {
        this.saldo = saldo;
    }

    public LocalDate getData() {
        return data;
    }

    public void setData(LocalDate data) {
        this.data = data;
    }

    public Produto getProduto() {
        return produto;
    }

    public void setProduto(Produto produto) {
        this.produto = produto;
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

    @DependsOnProperties({"produto"})
    @JmixProperty
    @NumberFormat(pattern = "########0")
    public Integer getCodigoProduto() {
        return produto == null ? null : produto.getCodigo();
    }

    @DependsOnProperties({"produto"})
    @JmixProperty
    public String getDescricaoProduto() {
        return produto == null ? null : produto.getDescricao();
    }

    @DependsOnProperties({"saldo", "valorUnitario"})
    @JmixProperty
    public BigDecimal getValorTotal() {
        return saldo.multiply(valorUnitario).setScale(2, RoundingMode.HALF_UP);
    }

    @InstanceName
    @DependsOnProperties({"codigoProduto", "descricaoProduto"})
    public String getInstanceName(MetadataTools metadataTools) {
        return String.format("%d %s",
                getCodigoProduto(),
                getDescricaoProduto());
    }
}
