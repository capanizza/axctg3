package br.com.axialsoftware.axctg3.entity.fiscal;

import br.com.axialsoftware.axctg3.entity.enums.TipoProduto;
import br.com.axialsoftware.axctg3.entity.tabelas.ClassTrib;
import br.com.axialsoftware.axctg3.entity.tabelas.ClassificacaoFiscal;
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
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

@JmixEntity
@Table(name = "PRODUTO", indexes = {
        @Index(name = "IDX_PRODUTO_GRUPO_PRODUTO", columnList = "GRUPO_PRODUTO_ID"),
        @Index(name = "IDX_PRODUTO_CLASSIFICACAO_FISCAL", columnList = "CLASSIFICACAO_FISCAL_ID"),
        @Index(name = "IDX_PRODUTO_UNQ", columnList = "CODIGO, COD_EMPRESA", unique = true)
})
@Entity
public class Produto {
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

    @NumberFormat(pattern = "########0")
    @Column(name = "CODIGO", nullable = false)
    @NotNull
    private Integer codigo;

    // sem valor padrão: o ProdutoEventListener preenche com a empresa
    // corrente quando está nulo
    @Column(name = "COD_EMPRESA", nullable = false)
    @NotNull
    private Integer codEmpresa;

    @Column(name = "DESCRICAO", nullable = false)
    @NotNull
    @Lob
    private String descricao;

    @Column(name = "APELIDO", nullable = false, length = 20)
    @NotNull
    private String apelido;

    @Column(name = "UNIDADE", length = 6)
    private String unidade;

    @Column(name = "EMBALAGEM", length = 6)
    private String embalagem;

    @NumberFormat(pattern = "###,##0.000")
    @Column(name = "QUANT_EMBALAGEM")
    private BigDecimal quantEmbalagem = BigDecimal.ZERO;

    @NumberFormat(pattern = "###,##0.00000")
    @Column(name = "PESO_LIQUIDO")
    private BigDecimal pesoLiquido = BigDecimal.ZERO;

    @NumberFormat(pattern = "###,##0.00000")
    @Column(name = "PESO_BRUTO")
    private BigDecimal pesoBruto = BigDecimal.ZERO;

    @JoinColumn(name = "GRUPO_PRODUTO_ID")
    @ManyToOne(fetch = FetchType.LAZY)
    private GrupoProduto grupoProduto;

    @Column(name = "TIPO_PRODUTO")
    private Integer tipoProduto;

    @NumberFormat(pattern = "##0.00")
    @Column(name = "ALIQ_ICMS")
    private BigDecimal aliqIcms = BigDecimal.ZERO;

    @NumberFormat(pattern = "##0.0000")
    @Column(name = "REDUCAO_ICMS", nullable = false)
    @NotNull
    private BigDecimal reducaoIcms = BigDecimal.ZERO;

    @NumberFormat(pattern = "##0.00")
    @Column(name = "ALIQ_IPI")
    private BigDecimal aliqIpi = BigDecimal.ZERO;

    @JoinColumn(name = "CLASSIFICACAO_FISCAL_ID")
    @ManyToOne(fetch = FetchType.LAZY)
    private ClassificacaoFiscal classificacaoFiscal;

    @Column(name = "RUA", length = 6)
    private String rua;

    @Column(name = "LADO", length = 10)
    private String lado;

    @Column(name = "CAIXA")
    @NumberFormat(pattern = "##0")
    private Integer caixa;

    @Column(name = "INATIVO")
    private Boolean inativo = false;

    @Column(name = "NCM", length = 20)
    private String ncm;

    // Código de Classificação Tributária (cClassTrib no leiaute NFe/NFCe, grupo gIBSCBS)
    // — referência real pra ClassTrib (mesmo padrão de ContaContabil.contaReferencial),
    // obrigatória por decisão de projeto (ver docs/REFORMA-TRIBUTARIA-IBS-CBS.md): ao
    // contrário de NaturezaOperacao.classTrib, aqui não há fallback silencioso pra "sem
    // classificação". É consultado na resolução de precedência feita por
    // ItemNotaSaidaEventListener quando a natureza da nota é "rasa" (CST 000).
    @JoinColumn(name = "CLASS_TRIB_ID", nullable = false)
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private ClassTrib classTrib;

    public @NotNull ClassTrib getClassTrib() {
        return classTrib;
    }

    public void setClassTrib(@NotNull ClassTrib classTrib) {
        this.classTrib = classTrib;
    }

    public String getNcm() {
        return ncm;
    }

    public void setNcm(String ncm) {
        this.ncm = ncm;
    }

    public Boolean getInativo() {
        return inativo;
    }

    public void setInativo(Boolean inativo) {
        this.inativo = inativo;
    }

    public Integer getCaixa() {
        return caixa;
    }

    public void setCaixa(Integer caixa) {
        this.caixa = caixa;
    }

    public String getLado() {
        return lado;
    }

    public void setLado(String lado) {
        this.lado = lado;
    }

    public String getRua() {
        return rua;
    }

    public void setRua(String rua) {
        this.rua = rua;
    }

    public ClassificacaoFiscal getClassificacaoFiscal() {
        return classificacaoFiscal;
    }

    public void setClassificacaoFiscal(ClassificacaoFiscal classificacaoFiscal) {
        this.classificacaoFiscal = classificacaoFiscal;
    }

    public BigDecimal getAliqIpi() {
        return aliqIpi;
    }

    public void setAliqIpi(BigDecimal aliqIpi) {
        this.aliqIpi = aliqIpi;
    }

    public @NotNull BigDecimal getReducaoIcms() {
        return reducaoIcms;
    }

    public void setReducaoIcms(@NotNull BigDecimal reducaoIcms) {
        this.reducaoIcms = reducaoIcms;
    }

    public BigDecimal getAliqIcms() {
        return aliqIcms;
    }

    public void setAliqIcms(BigDecimal aliqIcms) {
        this.aliqIcms = aliqIcms;
    }

    public TipoProduto getTipoProduto() {
        return tipoProduto == null ? null : TipoProduto.fromId(tipoProduto);
    }

    public void setTipoProduto(TipoProduto tipoProduto) {
        this.tipoProduto = tipoProduto == null ? null : tipoProduto.getId();
    }

    public GrupoProduto getGrupoProduto() {
        return grupoProduto;
    }

    public void setGrupoProduto(GrupoProduto grupoProduto) {
        this.grupoProduto = grupoProduto;
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

    public BigDecimal getQuantEmbalagem() {
        return quantEmbalagem;
    }

    public void setQuantEmbalagem(BigDecimal quantEmbalagem) {
        this.quantEmbalagem = quantEmbalagem;
    }

    public String getEmbalagem() {
        return embalagem;
    }

    public void setEmbalagem(String embalagem) {
        this.embalagem = embalagem;
    }

    public String getUnidade() {
        return unidade;
    }

    public void setUnidade(String unidade) {
        this.unidade = unidade;
    }

    public String getApelido() {
        return apelido;
    }

    public void setApelido(String apelido) {
        this.apelido = apelido;
    }

    public @NotNull String getDescricao() {
        return descricao;
    }

    public void setDescricao(@NotNull String descricao) {
        this.descricao = descricao;
    }

    public @NotNull Integer getCodEmpresa() {
        return codEmpresa;
    }

    public void setCodEmpresa(@NotNull Integer codEmpresa) {
        this.codEmpresa = codEmpresa;
    }

    public Integer getCodigo() {
        return codigo;
    }

    public void setCodigo(Integer codigo) {
        this.codigo = codigo;
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

    @InstanceName
    @DependsOnProperties({"codigo", "descricao"})
    public String getInstanceName(MetadataTools metadataTools) {
        return String.format("%d %s",
                codigo,
                metadataTools.format(descricao));
    }

    @DependsOnProperties({"tipoProduto"})
    @JmixProperty
    public String getDescricaoTipoProduto() {
        return tipoProduto == null ? null : Objects.requireNonNull(TipoProduto.fromId(tipoProduto)).toString();
    }
}
