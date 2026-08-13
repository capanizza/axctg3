package br.com.axialsoftware.axctg3.entity.contabil;

import br.com.axialsoftware.axctg3.entity.cadastros.Parceiro;
import io.jmix.core.DeletePolicy;
import io.jmix.core.MetadataTools;
import io.jmix.core.annotation.DeletedBy;
import io.jmix.core.annotation.DeletedDate;
import io.jmix.core.entity.annotation.JmixGeneratedValue;
import io.jmix.core.entity.annotation.OnDelete;
import io.jmix.core.metamodel.annotation.*;
import io.jmix.core.metamodel.datatype.DatatypeFormatter;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@JmixEntity
@Table(name = "BEM", indexes = {
        @Index(name = "IDX_BEM_UNQ", columnList = "CODIGO, COD_EMPRESA", unique = true),
        @Index(name = "IDX_BEM_PARCEIRO", columnList = "PARCEIRO_ID"),
        @Index(name = "IDX_BEM_CONTA_CONTABIL", columnList = "CONTA_CONTABIL_ID"),
        @Index(name = "IDX_BEM_CONTA_CONTABIL_DEPR", columnList = "CONTA_CONTABIL_DEPR_ID"),
        @Index(name = "IDX_BEM_CONTA_CONTABIL_DESP_DEPR", columnList = "CONTA_CONTABIL_DESP_DEPR_ID")
})
@Entity
public class Bem {
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

    @NumberFormat(pattern = "###000000")
    @Column(name = "CODIGO", nullable = false)
    @NotNull
    private Long codigo;

    // sem valor padrão: BemEventListener preenche com a empresa corrente quando está nulo
    @Column(name = "COD_EMPRESA", nullable = false)
    @NotNull
    private Integer codEmpresa;

    @Column(name = "DESCRICAO", nullable = false, length = 80)
    @NotNull
    private String descricao;

    @Column(name = "RESUMIDA", length = 20)
    private String resumida;

    @Column(name = "CHAPA", length = 25)
    private String chapa;

    @Column(name = "LOCAL", length = 25)
    private String local;

    @JoinColumn(name = "PARCEIRO_ID")
    @ManyToOne(fetch = FetchType.LAZY)
    private Parceiro parceiro;

    @Column(name = "DOCUMENTO", length = 10)
    private String documento;

    @Column(name = "DATA_COMPRA", nullable = false)
    @NotNull
    private LocalDate dataCompra;

    @NumberFormat(pattern = "###,###,##0.00", decimalSeparator = ",", groupingSeparator = ".")
    @Column(name = "VALOR_COMPRA", nullable = false, precision = 19, scale = 2)
    @NotNull
    private BigDecimal valorCompra;

    // sem valor padrão: BemEventListener preenche com zero quando está nulo
    @NumberFormat(pattern = "#0.00", decimalSeparator = ",", groupingSeparator = ".")
    @Column(name = "TAXA_DEPR", precision = 19, scale = 2)
    private BigDecimal taxaDepr;

    @Column(name = "DATA_BAIXA")
    private LocalDate dataBaixa;

    @NumberFormat(pattern = "###,###,##0.00", decimalSeparator = ",", groupingSeparator = ".")
    @Column(name = "VALOR_BAIXA", precision = 19, scale = 2)
    private BigDecimal valorBaixa;

    // sem valor padrão explícito: BemEventListener preenche com valorCompra na criação
    @NumberFormat(pattern = "###,###,##0.00", decimalSeparator = ",", groupingSeparator = ".")
    @Column(name = "VALOR_ATUAL", precision = 19, scale = 2)
    private BigDecimal valorAtual;

    @NumberFormat(pattern = "###,###,##0.00", decimalSeparator = ",", groupingSeparator = ".")
    @Column(name = "VALOR_FINAL", precision = 19, scale = 2)
    private BigDecimal valorFinal;

    // sem valor padrão explícito: BemEventListener preenche com zero na criação
    @NumberFormat(pattern = "###,###,##0.00", decimalSeparator = ",", groupingSeparator = ".")
    @Column(name = "DEPR_ACUM", precision = 19, scale = 2)
    private BigDecimal deprAcum;

    @NumberFormat(pattern = "###,###,##0.00", decimalSeparator = ",", groupingSeparator = ".")
    @Column(name = "DEPR_MES", precision = 19, scale = 2)
    private BigDecimal deprMes;

    @Column(name = "DATA_INI_DEPR")
    private LocalDate dataIniDepr;

    // sem valor padrão: BemEventListener preenche com zero quando está nulo
    @NumberFormat(pattern = "###,###,##0.00", decimalSeparator = ",", groupingSeparator = ".")
    @Column(name = "VALOR_INI_DEPR", precision = 19, scale = 2)
    private BigDecimal valorIniDepr;

    @Column(name = "DATA_DEPR")
    private LocalDate dataDepr;

    @JoinColumn(name = "CONTA_CONTABIL_ID")
    @ManyToOne(fetch = FetchType.LAZY)
    private ContaContabil contaContabil;

    @JoinColumn(name = "CONTA_CONTABIL_DEPR_ID")
    @ManyToOne(fetch = FetchType.LAZY)
    private ContaContabil contaContabilDepr;

    @JoinColumn(name = "CONTA_CONTABIL_DESP_DEPR_ID")
    @ManyToOne(fetch = FetchType.LAZY)
    private ContaContabil contaContabilDespDepr;

    @Column(name = "COD_CONTABIL", length = 25)
    private String codContabil;

    @Column(name = "COD_CONTABIL_DEPR", length = 28)
    private String codContabilDepr;

    @Column(name = "COD_CONTABIL_DESP_DEPR", length = 25)
    private String codContabilDespDepr;

    @OnDelete(DeletePolicy.CASCADE)
    @Composition
    @OneToMany(mappedBy = "bem")
    @OrderBy("dataDepr DESC")
    private List<Depreciacao> depreciacaos;

    public List<Depreciacao> getDepreciacaos() {
        return depreciacaos;
    }

    public void setDepreciacaos(List<Depreciacao> depreciacaos) {
        this.depreciacaos = depreciacaos;
    }

    public String getCodContabilDespDepr() {
        return codContabilDespDepr;
    }

    public void setCodContabilDespDepr(String codContabilDespDepr) {
        this.codContabilDespDepr = codContabilDespDepr;
    }

    public String getCodContabilDepr() {
        return codContabilDepr;
    }

    public void setCodContabilDepr(String codContabilDepr) {
        this.codContabilDepr = codContabilDepr;
    }

    public String getCodContabil() {
        return codContabil;
    }

    public void setCodContabil(String codContabil) {
        this.codContabil = codContabil;
    }

    public ContaContabil getContaContabilDespDepr() {
        return contaContabilDespDepr;
    }

    public void setContaContabilDespDepr(ContaContabil contaContabilDespDepr) {
        this.contaContabilDespDepr = contaContabilDespDepr;
    }

    public ContaContabil getContaContabilDepr() {
        return contaContabilDepr;
    }

    public void setContaContabilDepr(ContaContabil contaContabilDepr) {
        this.contaContabilDepr = contaContabilDepr;
    }

    public ContaContabil getContaContabil() {
        return contaContabil;
    }

    public void setContaContabil(ContaContabil contaContabil) {
        this.contaContabil = contaContabil;
    }

    public LocalDate getDataDepr() {
        return dataDepr;
    }

    public void setDataDepr(LocalDate dataDepr) {
        this.dataDepr = dataDepr;
    }

    public BigDecimal getValorIniDepr() {
        return valorIniDepr;
    }

    public void setValorIniDepr(BigDecimal valorIniDepr) {
        this.valorIniDepr = valorIniDepr;
    }

    public LocalDate getDataIniDepr() {
        return dataIniDepr;
    }

    public void setDataIniDepr(LocalDate dataIniDepr) {
        this.dataIniDepr = dataIniDepr;
    }

    public BigDecimal getDeprMes() {
        return deprMes;
    }

    public void setDeprMes(BigDecimal deprMes) {
        this.deprMes = deprMes;
    }

    public BigDecimal getDeprAcum() {
        return deprAcum;
    }

    public void setDeprAcum(BigDecimal deprAcum) {
        this.deprAcum = deprAcum;
    }

    public BigDecimal getValorFinal() {
        return valorFinal;
    }

    public void setValorFinal(BigDecimal valorFinal) {
        this.valorFinal = valorFinal;
    }

    public BigDecimal getValorAtual() {
        return valorAtual;
    }

    public void setValorAtual(BigDecimal valorAtual) {
        this.valorAtual = valorAtual;
    }

    public BigDecimal getValorBaixa() {
        return valorBaixa;
    }

    public void setValorBaixa(BigDecimal valorBaixa) {
        this.valorBaixa = valorBaixa;
    }

    public LocalDate getDataBaixa() {
        return dataBaixa;
    }

    public void setDataBaixa(LocalDate dataBaixa) {
        this.dataBaixa = dataBaixa;
    }

    public BigDecimal getTaxaDepr() {
        return taxaDepr;
    }

    public void setTaxaDepr(BigDecimal taxaDepr) {
        this.taxaDepr = taxaDepr;
    }

    public BigDecimal getValorCompra() {
        return valorCompra;
    }

    public void setValorCompra(BigDecimal valorCompra) {
        this.valorCompra = valorCompra;
    }

    public LocalDate getDataCompra() {
        return dataCompra;
    }

    public void setDataCompra(LocalDate dataCompra) {
        this.dataCompra = dataCompra;
    }

    public String getDocumento() {
        return documento;
    }

    public void setDocumento(String documento) {
        this.documento = documento;
    }

    public Parceiro getParceiro() {
        return parceiro;
    }

    public void setParceiro(Parceiro parceiro) {
        this.parceiro = parceiro;
    }

    public String getLocal() {
        return local;
    }

    public void setLocal(String local) {
        this.local = local;
    }

    public String getChapa() {
        return chapa;
    }

    public void setChapa(String chapa) {
        this.chapa = chapa;
    }

    public String getResumida() {
        return resumida;
    }

    public void setResumida(String resumida) {
        this.resumida = resumida;
    }

    public String getDescricao() {
        return descricao;
    }

    public void setDescricao(String descricao) {
        this.descricao = descricao;
    }

    public Integer getCodEmpresa() {
        return codEmpresa;
    }

    public void setCodEmpresa(Integer codEmpresa) {
        this.codEmpresa = codEmpresa;
    }

    public Long getCodigo() {
        return codigo;
    }

    public void setCodigo(Long codigo) {
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
    public String getInstanceName(MetadataTools metadataTools, DatatypeFormatter datatypeFormatter) {
        return String.format("%s %s",
                datatypeFormatter.formatLong(codigo),
                metadataTools.format(descricao));
    }
}
