package br.com.axialsoftware.axctg3.entity.importacao;

import io.jmix.core.annotation.DeletedBy;
import io.jmix.core.annotation.DeletedDate;
import io.jmix.core.entity.annotation.JmixGeneratedValue;
import io.jmix.core.metamodel.annotation.JmixEntity;
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
 * Item de nota de saída do legado (ITEMNOTASAIDA) dentro de um {@link ImpLote} — ver
 * {@code ImpLote}. Cru, como no Firebird; a conversão é feita na importação.
 */
@JmixEntity
@Table(name = "IMP_ITEM_NOTA_SAIDA", indexes = {
        @Index(name = "IDX_IMP_ITEM_NOTA_SAIDA_LOTE", columnList = "LOTE_ID")
})
@Entity
public class ImpItemNotaSaida {
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

    @JoinColumn(name = "LOTE_ID", nullable = false)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @NotNull
    private ImpLote lote;

    // ITEMNOTASAIDA como no legado: PAI é o REGISTRO da nota; CST, FRETE e SEGURO vêm da NFESPED
    // (vazios nos bancos sem essa tabela)
    @Column(name = "REGISTRO")
    private Integer registro;

    @Column(name = "PAI")
    private Integer pai;

    @Column(name = "PRODUTO")
    private Integer produto;

    @Column(name = "QUANTIDADE", precision = 19, scale = 8)
    private BigDecimal quantidade;

    @Column(name = "VALOR_UNITARIO", precision = 19, scale = 8)
    private BigDecimal valorUnitario;

    @Column(name = "PORC_DESCONTO", precision = 19, scale = 8)
    private BigDecimal porcDesconto;

    @Column(name = "DESCONTO", precision = 19, scale = 8)
    private BigDecimal desconto;

    @Column(name = "BASE_ICMS", precision = 19, scale = 8)
    private BigDecimal baseIcms;

    @Column(name = "ALIQ_ICMS", precision = 19, scale = 8)
    private BigDecimal aliqIcms;

    @Column(name = "VALOR_ICMS", precision = 19, scale = 8)
    private BigDecimal valorIcms;

    @Column(name = "BASE_IPI", precision = 19, scale = 8)
    private BigDecimal baseIpi;

    @Column(name = "ALIQ_IPI", precision = 19, scale = 8)
    private BigDecimal aliqIpi;

    @Column(name = "VALOR_IPI", precision = 19, scale = 8)
    private BigDecimal valorIpi;

    @Column(name = "BASE_SUBSTITUICAO", precision = 19, scale = 8)
    private BigDecimal baseSubstituicao;

    @Column(name = "VALOR_SUBSTITUICAO", precision = 19, scale = 8)
    private BigDecimal valorSubstituicao;

    @Column(name = "CFOP")
    private Integer cfop;

    @Column(name = "CST")
    private String cst;

    @Column(name = "FRETE", precision = 19, scale = 8)
    private BigDecimal frete;

    @Column(name = "SEGURO", precision = 19, scale = 8)
    private BigDecimal seguro;

    @Column(name = "PESO_LIQUIDO", precision = 19, scale = 8)
    private BigDecimal pesoLiquido;

    @Column(name = "PESO_BRUTO", precision = 19, scale = 8)
    private BigDecimal pesoBruto;

    @Lob
    @Column(name = "COMPL_PRODUTO")
    private String complProduto;

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

    public ImpLote getLote() {
        return lote;
    }

    public void setLote(ImpLote lote) {
        this.lote = lote;
    }

    public Integer getRegistro() {
        return registro;
    }

    public void setRegistro(Integer registro) {
        this.registro = registro;
    }

    public Integer getPai() {
        return pai;
    }

    public void setPai(Integer pai) {
        this.pai = pai;
    }

    public Integer getProduto() {
        return produto;
    }

    public void setProduto(Integer produto) {
        this.produto = produto;
    }

    public BigDecimal getQuantidade() {
        return quantidade;
    }

    public void setQuantidade(BigDecimal quantidade) {
        this.quantidade = quantidade;
    }

    public BigDecimal getValorUnitario() {
        return valorUnitario;
    }

    public void setValorUnitario(BigDecimal valorUnitario) {
        this.valorUnitario = valorUnitario;
    }

    public BigDecimal getPorcDesconto() {
        return porcDesconto;
    }

    public void setPorcDesconto(BigDecimal porcDesconto) {
        this.porcDesconto = porcDesconto;
    }

    public BigDecimal getDesconto() {
        return desconto;
    }

    public void setDesconto(BigDecimal desconto) {
        this.desconto = desconto;
    }

    public BigDecimal getBaseIcms() {
        return baseIcms;
    }

    public void setBaseIcms(BigDecimal baseIcms) {
        this.baseIcms = baseIcms;
    }

    public BigDecimal getAliqIcms() {
        return aliqIcms;
    }

    public void setAliqIcms(BigDecimal aliqIcms) {
        this.aliqIcms = aliqIcms;
    }

    public BigDecimal getValorIcms() {
        return valorIcms;
    }

    public void setValorIcms(BigDecimal valorIcms) {
        this.valorIcms = valorIcms;
    }

    public BigDecimal getBaseIpi() {
        return baseIpi;
    }

    public void setBaseIpi(BigDecimal baseIpi) {
        this.baseIpi = baseIpi;
    }

    public BigDecimal getAliqIpi() {
        return aliqIpi;
    }

    public void setAliqIpi(BigDecimal aliqIpi) {
        this.aliqIpi = aliqIpi;
    }

    public BigDecimal getValorIpi() {
        return valorIpi;
    }

    public void setValorIpi(BigDecimal valorIpi) {
        this.valorIpi = valorIpi;
    }

    public BigDecimal getBaseSubstituicao() {
        return baseSubstituicao;
    }

    public void setBaseSubstituicao(BigDecimal baseSubstituicao) {
        this.baseSubstituicao = baseSubstituicao;
    }

    public BigDecimal getValorSubstituicao() {
        return valorSubstituicao;
    }

    public void setValorSubstituicao(BigDecimal valorSubstituicao) {
        this.valorSubstituicao = valorSubstituicao;
    }

    public Integer getCfop() {
        return cfop;
    }

    public void setCfop(Integer cfop) {
        this.cfop = cfop;
    }

    public String getCst() {
        return cst;
    }

    public void setCst(String cst) {
        this.cst = cst;
    }

    public BigDecimal getFrete() {
        return frete;
    }

    public void setFrete(BigDecimal frete) {
        this.frete = frete;
    }

    public BigDecimal getSeguro() {
        return seguro;
    }

    public void setSeguro(BigDecimal seguro) {
        this.seguro = seguro;
    }

    public BigDecimal getPesoLiquido() {
        return pesoLiquido;
    }

    public void setPesoLiquido(BigDecimal pesoLiquido) {
        this.pesoLiquido = pesoLiquido;
    }

    public BigDecimal getPesoBruto() {
        return pesoBruto;
    }

    public void setPesoBruto(BigDecimal pesoBruto) {
        this.pesoBruto = pesoBruto;
    }

    public String getComplProduto() {
        return complProduto;
    }

    public void setComplProduto(String complProduto) {
        this.complProduto = complProduto;
    }
}
