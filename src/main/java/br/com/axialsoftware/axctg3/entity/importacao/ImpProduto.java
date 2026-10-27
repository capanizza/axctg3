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
 * Produto do legado (PRODUTO) dentro de um {@link ImpLote} — ver {@code ImpLote}. Cru, como no
 * Firebird; a conversão é feita na importação.
 */
@JmixEntity
@Table(name = "IMP_PRODUTO", indexes = {
        @Index(name = "IDX_IMP_PRODUTO_LOTE", columnList = "LOTE_ID")
})
@Entity
public class ImpProduto {
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

    // PRODUTO como no legado; NCM é o CODNBM da CLASSIFICACAO do produto (o axctg3 liga pelo
    // NCM, não pelo código da classificação); INATIVO é S/N ("N" nos bancos sem a coluna)
    @Column(name = "CODIGO")
    private Integer codigo;

    @Column(name = "DESCRICAO")
    private String descricao;

    @Column(name = "APELIDO")
    private String apelido;

    @Column(name = "UNIDADE")
    private String unidade;

    @Column(name = "EMBALAGEM")
    private String embalagem;

    @Column(name = "QUANT_EMBALAGEM", precision = 19, scale = 6)
    private BigDecimal quantEmbalagem;

    @Column(name = "PESO_LIQUIDO", precision = 19, scale = 6)
    private BigDecimal pesoLiquido;

    @Column(name = "PESO_BRUTO", precision = 19, scale = 6)
    private BigDecimal pesoBruto;

    @Column(name = "ALIQ_ICMS", precision = 19, scale = 6)
    private BigDecimal aliqIcms;

    @Column(name = "REDUCAO_ICMS", precision = 19, scale = 6)
    private BigDecimal reducaoIcms;

    @Column(name = "ALIQ_IPI", precision = 19, scale = 6)
    private BigDecimal aliqIpi;

    @Column(name = "GRUPO_PROD")
    private Integer grupoProd;

    @Column(name = "CLASSIFICACAO")
    private Integer classificacao;

    @Column(name = "NCM")
    private String ncm;

    @Column(name = "INATIVO")
    private String inativo;

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

    public Integer getCodigo() {
        return codigo;
    }

    public void setCodigo(Integer codigo) {
        this.codigo = codigo;
    }

    public String getDescricao() {
        return descricao;
    }

    public void setDescricao(String descricao) {
        this.descricao = descricao;
    }

    public String getApelido() {
        return apelido;
    }

    public void setApelido(String apelido) {
        this.apelido = apelido;
    }

    public String getUnidade() {
        return unidade;
    }

    public void setUnidade(String unidade) {
        this.unidade = unidade;
    }

    public String getEmbalagem() {
        return embalagem;
    }

    public void setEmbalagem(String embalagem) {
        this.embalagem = embalagem;
    }

    public BigDecimal getQuantEmbalagem() {
        return quantEmbalagem;
    }

    public void setQuantEmbalagem(BigDecimal quantEmbalagem) {
        this.quantEmbalagem = quantEmbalagem;
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

    public BigDecimal getAliqIcms() {
        return aliqIcms;
    }

    public void setAliqIcms(BigDecimal aliqIcms) {
        this.aliqIcms = aliqIcms;
    }

    public BigDecimal getReducaoIcms() {
        return reducaoIcms;
    }

    public void setReducaoIcms(BigDecimal reducaoIcms) {
        this.reducaoIcms = reducaoIcms;
    }

    public BigDecimal getAliqIpi() {
        return aliqIpi;
    }

    public void setAliqIpi(BigDecimal aliqIpi) {
        this.aliqIpi = aliqIpi;
    }

    public Integer getGrupoProd() {
        return grupoProd;
    }

    public void setGrupoProd(Integer grupoProd) {
        this.grupoProd = grupoProd;
    }

    public Integer getClassificacao() {
        return classificacao;
    }

    public void setClassificacao(Integer classificacao) {
        this.classificacao = classificacao;
    }

    public String getNcm() {
        return ncm;
    }

    public void setNcm(String ncm) {
        this.ncm = ncm;
    }

    public String getInativo() {
        return inativo;
    }

    public void setInativo(String inativo) {
        this.inativo = inativo;
    }
}
