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

    @NumberFormat(pattern = "###,###,##0.00", decimalSeparator = ",", groupingSeparator = ".")
    @Column(name = "VALOR_DESCONTO", precision = 19, scale = 2)
    private BigDecimal valorDesconto = BigDecimal.ZERO;

    @NumberFormat(pattern = "##0.00")
    @Column(name = "PORC_DESCONTO", precision = 19, scale = 2)
    private BigDecimal porcDesconto = BigDecimal.ZERO;

    @NumberFormat(pattern = "###,###,##0.00", decimalSeparator = ",", groupingSeparator = ".")
    @Column(name = "BASE_ICMS", precision = 19, scale = 2)
    private BigDecimal baseIcms = BigDecimal.ZERO;

    @NumberFormat(pattern = "##0.00")
    @Column(name = "ALIQ_ICMS", precision = 19, scale = 2)
    private BigDecimal aliqIcms = BigDecimal.ZERO;

    @NumberFormat(pattern = "###,###,##0.00", decimalSeparator = ",", groupingSeparator = ".")
    @Column(name = "VALOR_ICMS", precision = 19, scale = 2)
    private BigDecimal valorIcms = BigDecimal.ZERO;

    @NumberFormat(pattern = "###,###,##0.00", decimalSeparator = ",", groupingSeparator = ".")
    @Column(name = "BASE_IPI", precision = 19, scale = 2)
    private BigDecimal baseIpi = BigDecimal.ZERO;

    @NumberFormat(pattern = "##0.00")
    @Column(name = "ALIQ_IPI", precision = 19, scale = 2)
    private BigDecimal aliqIpi = BigDecimal.ZERO;

    @NumberFormat(pattern = "###,###,##0.00", decimalSeparator = ",", groupingSeparator = ".")
    @Column(name = "VALOR_IPI", precision = 19, scale = 2)
    private BigDecimal valorIpi = BigDecimal.ZERO;

    @NumberFormat(pattern = "###,###,##0.00", decimalSeparator = ",", groupingSeparator = ".")
    @Column(name = "BASE_ST", precision = 19, scale = 2)
    private BigDecimal baseSt = BigDecimal.ZERO;

    @NumberFormat(pattern = "###,###,##0.00", decimalSeparator = ",", groupingSeparator = ".")
    @Column(name = "VALOR_ST", precision = 19, scale = 2)
    private BigDecimal valorSt = BigDecimal.ZERO;

    @NumberFormat(pattern = "###,###,##0.00", decimalSeparator = ",", groupingSeparator = ".")
    @Column(name = "FRETE", precision = 19, scale = 2)
    private BigDecimal frete = BigDecimal.ZERO;

    @NumberFormat(pattern = "###,###,##0.00", decimalSeparator = ",", groupingSeparator = ".")
    @Column(name = "SEGURO", precision = 19, scale = 2)
    private BigDecimal seguro = BigDecimal.ZERO;

    @NumberFormat(pattern = "###,###,##0.00", decimalSeparator = ",", groupingSeparator = ".")
    @Column(name = "DESPESAS", precision = 19, scale = 2)
    private BigDecimal despesas = BigDecimal.ZERO;

    @NumberFormat(pattern = "0000")
    @Column(name = "CFOP")
    private Integer cfop;

    @Column(name = "CST", length = 4)
    private String cst;

    // Código de Classificação Tributária (cClassTrib IBS/CBS) resolvido pra este item.
    // Sem valor padrão: o ItemNotaSaidaEventListener preenche na primeira gravação
    // aplicando a regra de precedência (docs/REFORMA-TRIBUTARIA-IBS-CBS.md) — CST 000 da
    // NaturezaOperacao usa o valor do Produto, senão usa o da própria NaturezaOperacao.
    // É um snapshot congelado no momento da gravação, não recalculado depois: uma
    // mudança posterior no cadastro do produto ou da natureza não deve reescrever
    // retroativamente o que já foi declarado num item existente.
    @Column(name = "COD_CLASS_TRIB")
    @NumberFormat(pattern = "000000")
    private Integer codClassTrib;

    public Integer getCodClassTrib() {
        return codClassTrib;
    }

    public void setCodClassTrib(Integer codClassTrib) {
        this.codClassTrib = codClassTrib;
    }

    @NumberFormat(pattern = "###,###,##0.00000", decimalSeparator = ",", groupingSeparator = ".")
    @Column(name = "PESO_LIQUIDO", precision = 19, scale = 5)
    private BigDecimal pesoLiquido = BigDecimal.ZERO;

    @NumberFormat(pattern = "###,###,##0.00000", decimalSeparator = ",", groupingSeparator = ".")
    @Column(name = "PESO_BRUTO", precision = 19, scale = 5)
    private BigDecimal pesoBruto = BigDecimal.ZERO;

    @DependsOnProperties({"quantidade", "valorUnitario"})
    @NumberFormat(pattern = "###,###,##0.00", decimalSeparator = ",", groupingSeparator = ".")
    @JmixProperty
    public BigDecimal getSubTotal() {
        return quantidade.multiply(valorUnitario).setScale(2, RoundingMode.HALF_UP);
    }

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

    public BigDecimal getDespesas() {
        return despesas;
    }

    public void setDespesas(BigDecimal despesas) {
        this.despesas = despesas;
    }

    public BigDecimal getSeguro() {
        return seguro;
    }

    public void setSeguro(BigDecimal seguro) {
        this.seguro = seguro;
    }

    public BigDecimal getFrete() {
        return frete;
    }

    public void setFrete(BigDecimal frete) {
        this.frete = frete;
    }

    public BigDecimal getValorSt() {
        return valorSt;
    }

    public void setValorSt(BigDecimal valorSt) {
        this.valorSt = valorSt;
    }

    public BigDecimal getBaseSt() {
        return baseSt;
    }

    public void setBaseSt(BigDecimal baseSt) {
        this.baseSt = baseSt;
    }

    public BigDecimal getValorIpi() {
        return valorIpi;
    }

    public void setValorIpi(BigDecimal valorIpi) {
        this.valorIpi = valorIpi;
    }

    public BigDecimal getAliqIpi() {
        return aliqIpi;
    }

    public void setAliqIpi(BigDecimal aliqIpi) {
        this.aliqIpi = aliqIpi;
    }

    public BigDecimal getBaseIpi() {
        return baseIpi;
    }

    public void setBaseIpi(BigDecimal baseIpi) {
        this.baseIpi = baseIpi;
    }

    public BigDecimal getValorIcms() {
        return valorIcms;
    }

    public void setValorIcms(BigDecimal valorIcms) {
        this.valorIcms = valorIcms;
    }

    public BigDecimal getAliqIcms() {
        return aliqIcms;
    }

    public void setAliqIcms(BigDecimal aliqIcms) {
        this.aliqIcms = aliqIcms;
    }

    public BigDecimal getBaseIcms() {
        return baseIcms;
    }

    public void setBaseIcms(BigDecimal baseIcms) {
        this.baseIcms = baseIcms;
    }

    public BigDecimal getPorcDesconto() {
        return porcDesconto;
    }

    public void setPorcDesconto(BigDecimal porcDesconto) {
        this.porcDesconto = porcDesconto;
    }

    public BigDecimal getValorDesconto() {
        return valorDesconto;
    }

    public void setValorDesconto(BigDecimal valorDesconto) {
        this.valorDesconto = valorDesconto;
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
