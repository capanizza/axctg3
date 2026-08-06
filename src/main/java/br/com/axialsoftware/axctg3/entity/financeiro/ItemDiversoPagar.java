package br.com.axialsoftware.axctg3.entity.financeiro;

import io.jmix.core.annotation.DeletedBy;
import io.jmix.core.annotation.DeletedDate;
import io.jmix.core.entity.annotation.JmixGeneratedValue;
import io.jmix.core.metamodel.annotation.DependsOnProperties;
import io.jmix.core.metamodel.annotation.InstanceName;
import io.jmix.core.metamodel.annotation.JmixEntity;
import io.jmix.core.metamodel.annotation.JmixProperty;
import io.jmix.core.metamodel.annotation.NumberFormat;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Parcela/lançamento de um DiversoPagar (emissão ou baixa). Trazido do axctg-flow, só
 * entidade + changelog por ora — ver DiversoPagar.
 */
@JmixEntity
@Table(name = "ITEM_DIVERSO_PAGAR", indexes = {
        @Index(name = "IDX_ITEM_DIVERSO_PAGAR_DIVERSO_PAGAR", columnList = "DIVERSO_PAGAR_ID"),
        @Index(name = "IDX_ITEM_DIVERSO_PAGAR_HISTORICO_FINANCEIRO", columnList = "HISTORICO_FINANCEIRO_ID"),
        @Index(name = "IDX_ITEM_DIVERSO_PAGAR_BANCO", columnList = "BANCO_ID"),
        @Index(name = "IDX_ITEM_DIVERSO_PAGAR_DATA", columnList = "DATA")
})
@Entity
public class ItemDiversoPagar {

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

    @JoinColumn(name = "DIVERSO_PAGAR_ID", nullable = false)
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private DiversoPagar diversoPagar;

    @NumberFormat(pattern = "##0")
    @Column(name = "ITEM", nullable = false)
    @NotNull
    private Integer item;

    @Column(name = "DATA", nullable = false)
    @NotNull
    private LocalDate data;

    @InstanceName
    @JoinColumn(name = "HISTORICO_FINANCEIRO_ID", nullable = false)
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private HistoricoFinanceiro historicoFinanceiro;

    @JoinColumn(name = "BANCO_ID")
    @ManyToOne(fetch = FetchType.LAZY)
    private Banco banco;

    @NumberFormat(pattern = "###,###,##0.00", decimalSeparator = ",", groupingSeparator = ".")
    @Column(name = "VALOR", nullable = false, precision = 19, scale = 2)
    @NotNull
    private BigDecimal valor = BigDecimal.ZERO;

    @NumberFormat(pattern = "###,###,##0.00", decimalSeparator = ",", groupingSeparator = ".")
    @Column(name = "JUROS", nullable = false, precision = 19, scale = 2)
    @NotNull
    private BigDecimal juros = BigDecimal.ZERO;

    @NumberFormat(pattern = "###,###,##0.00", decimalSeparator = ",", groupingSeparator = ".")
    @Column(name = "DESCONTO", nullable = false, precision = 19, scale = 2)
    @NotNull
    private BigDecimal desconto = BigDecimal.ZERO;

    @Column(name = "CONTABILIZADO")
    private Boolean contabilizado = false;

    @DependsOnProperties({"valor", "juros", "desconto"})
    @NumberFormat(pattern = "###,###,##0.00", decimalSeparator = ",", groupingSeparator = ".")
    @JmixProperty
    public BigDecimal getValorLiquido() {
        return valor.add(juros).subtract(desconto);
    }

    public Boolean getContabilizado() {
        return contabilizado;
    }

    public void setContabilizado(Boolean contabilizado) {
        this.contabilizado = contabilizado;
    }

    public BigDecimal getDesconto() {
        return desconto;
    }

    public void setDesconto(BigDecimal desconto) {
        this.desconto = desconto;
    }

    public BigDecimal getJuros() {
        return juros;
    }

    public void setJuros(BigDecimal juros) {
        this.juros = juros;
    }

    public BigDecimal getValor() {
        return valor;
    }

    public void setValor(BigDecimal valor) {
        this.valor = valor;
    }

    public Banco getBanco() {
        return banco;
    }

    public void setBanco(Banco banco) {
        this.banco = banco;
    }

    public HistoricoFinanceiro getHistoricoFinanceiro() {
        return historicoFinanceiro;
    }

    public void setHistoricoFinanceiro(HistoricoFinanceiro historicoFinanceiro) {
        this.historicoFinanceiro = historicoFinanceiro;
    }

    public LocalDate getData() {
        return data;
    }

    public void setData(LocalDate data) {
        this.data = data;
    }

    public Integer getItem() {
        return item;
    }

    public void setItem(Integer item) {
        this.item = item;
    }

    public DiversoPagar getDiversoPagar() {
        return diversoPagar;
    }

    public void setDiversoPagar(DiversoPagar diversoPagar) {
        this.diversoPagar = diversoPagar;
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
