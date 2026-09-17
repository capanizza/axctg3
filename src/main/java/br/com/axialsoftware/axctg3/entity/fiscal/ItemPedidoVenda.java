package br.com.axialsoftware.axctg3.entity.fiscal;

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
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Item (linha) de um {@link PedidoVenda}. Mesmo conjunto de campos identificadores de
 * {@link ItemNotaSaida} (item, produto, cfop, cst, codClassTrib, quantidade,
 * valorUnitario) — sem a resolução automática de tributação nem o cálculo de ICMS que
 * {@code ItemNotaSaidaEventListener} aplica lá, ainda não modelados pro pedido.
 */
@JmixEntity
@Table(name = "ITEM_PEDIDO_VENDA", indexes = {
        @Index(name = "IDX_ITEM_PEDIDO_VENDA_PEDIDO_VENDA", columnList = "PEDIDO_VENDA_ID"),
        @Index(name = "IDX_ITEM_PEDIDO_VENDA_PRODUTO", columnList = "PRODUTO_ID")
})
@Entity
public class ItemPedidoVenda {
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

    @JoinColumn(name = "PEDIDO_VENDA_ID", nullable = false)
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private PedidoVenda pedidoVenda;

    // sem valor padrão: o ItemPedidoVendaEventListener numera via Sequence
    @InstanceName
    @Column(name = "ITEM")
    private Integer item;

    @JoinColumn(name = "PRODUTO_ID", nullable = false)
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Produto produto;

    @NumberFormat(pattern = "0000")
    @Column(name = "CFOP")
    private Integer cfop;

    @Column(name = "CST", length = 4)
    private String cst;

    @Column(name = "COD_CLASS_TRIB")
    @NumberFormat(pattern = "000000")
    private Integer codClassTrib;

    @NumberFormat(pattern = "###,##0.000")
    @Column(name = "QUANTIDADE", precision = 19, scale = 3)
    private BigDecimal quantidade = BigDecimal.ZERO;

    @NumberFormat(pattern = "###,###,##0.000000", decimalSeparator = ",", groupingSeparator = ".")
    @Column(name = "VALOR_UNITARIO", precision = 19, scale = 6)
    private BigDecimal valorUnitario = BigDecimal.ZERO;

    @DependsOnProperties({"quantidade", "valorUnitario"})
    @NumberFormat(pattern = "###,###,##0.00", decimalSeparator = ",", groupingSeparator = ".")
    @JmixProperty
    public BigDecimal getSubTotal() {
        return quantidade.multiply(valorUnitario).setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal getValorUnitario() {
        return valorUnitario;
    }

    public void setValorUnitario(BigDecimal valorUnitario) {
        this.valorUnitario = valorUnitario;
    }

    public BigDecimal getQuantidade() {
        return quantidade;
    }

    public void setQuantidade(BigDecimal quantidade) {
        this.quantidade = quantidade;
    }

    public Integer getCodClassTrib() {
        return codClassTrib;
    }

    public void setCodClassTrib(Integer codClassTrib) {
        this.codClassTrib = codClassTrib;
    }

    public String getCst() {
        return cst;
    }

    public void setCst(String cst) {
        this.cst = cst == null ? null : cst.trim();
    }

    public Integer getCfop() {
        return cfop;
    }

    public void setCfop(Integer cfop) {
        this.cfop = cfop;
    }

    public @NotNull Produto getProduto() {
        return produto;
    }

    public void setProduto(@NotNull Produto produto) {
        this.produto = produto;
    }

    public Integer getItem() {
        return item;
    }

    public void setItem(Integer item) {
        this.item = item;
    }

    public @NotNull PedidoVenda getPedidoVenda() {
        return pedidoVenda;
    }

    public void setPedidoVenda(@NotNull PedidoVenda pedidoVenda) {
        this.pedidoVenda = pedidoVenda;
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
