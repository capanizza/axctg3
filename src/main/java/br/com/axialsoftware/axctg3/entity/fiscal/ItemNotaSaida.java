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
 * Item (linha) de uma {@link NotaSaida}. Portado de
 * {@code axctg-flow/.../entity/fiscal/ItemNotaSaida.java}; {@code produto} aponta pro
 * novo {@link Produto} deste projeto (também em {@code entity.fiscal}, diferente do
 * legado onde ficava em {@code entity.cadastros}).
 *
 * <p><b>Decisão de arquitetura (2026-09-13):</b> minimalista de propósito — só o que é
 * <b>digitado</b> pelo operador (produto/quantidade/preço) mais duas classificações que
 * variam por item e não têm de onde ser derivadas automaticamente ({@code cfop},
 * {@code cst}). Os valores monetários de ICMS/IPI (base/alíquota/valor) NÃO ficam mais
 * aqui — são calculados ao vivo na emissão ({@code NfeXmlBuilder}, mesma técnica já usada
 * pra IBS/CBS), a partir da alíquota de {@link Produto} (quando {@code
 * NaturezaOperacao.venda}) ou de {@link NaturezaOperacao} (quando não é venda). ICMS-ST
 * não é suportado (nenhum cliente ativo usa) — só os CSTs sem substituição tributária.
 */
@JmixEntity
@Table(name = "ITEM_NOTA_SAIDA", indexes = {
        @Index(name = "IDX_ITEM_NOTA_SAIDA_NOTA_SAIDA", columnList = "NOTA_SAIDA_ID"),
        @Index(name = "IDX_ITEM_NOTA_SAIDA_PRODUTO", columnList = "PRODUTO_ID")
})
@Entity
public class ItemNotaSaida {
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

    @JoinColumn(name = "NOTA_SAIDA_ID", nullable = false)
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private NotaSaida notaSaida;

    // sem valor padrão: o ItemNotaSaidaEventListener numera via Sequence
    @InstanceName
    @Column(name = "ITEM")
    private Integer item;

    @JoinColumn(name = "PRODUTO_ID", nullable = false)
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Produto produto;

    @NumberFormat(pattern = "###,##0.000")
    @Column(name = "QUANTIDADE", precision = 19, scale = 3)
    private BigDecimal quantidade = BigDecimal.ZERO;

    @NumberFormat(pattern = "###,###,##0.000000", decimalSeparator = ",", groupingSeparator = ".")
    @Column(name = "VALOR_UNITARIO", precision = 19, scale = 6)
    private BigDecimal valorUnitario = BigDecimal.ZERO;

    // pré-preenchido com NaturezaOperacao.cfop da nota (ItemNotaSaidaEventListener), mas
    // editável — dentro da mesma nota, itens podem ter CFOP diferente do padrão.
    @NumberFormat(pattern = "0000")
    @Column(name = "CFOP")
    private Integer cfop;

    // Situação Tributária do ICMS — varia por item por cliente real (não dá pra derivar
    // de Produto/NaturezaOperacao), então fica só digitação manual, sem pré-preenchimento.
    @Column(name = "CST", length = 4)
    private String cst;

    @DependsOnProperties({"quantidade", "valorUnitario"})
    @NumberFormat(pattern = "###,###,##0.00", decimalSeparator = ",", groupingSeparator = ".")
    @JmixProperty
    public BigDecimal getSubTotal() {
        return quantidade.multiply(valorUnitario).setScale(2, RoundingMode.HALF_UP);
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

    public @NotNull NotaSaida getNotaSaida() {
        return notaSaida;
    }

    public void setNotaSaida(@NotNull NotaSaida notaSaida) {
        this.notaSaida = notaSaida;
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
