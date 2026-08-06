package br.com.axialsoftware.axctg3.entity.financeiro;

import br.com.axialsoftware.axctg3.entity.contabil.ContaContabil;
import br.com.axialsoftware.axctg3.entity.contabil.HistoricoContabil;
import io.jmix.core.MetadataTools;
import io.jmix.core.annotation.DeletedBy;
import io.jmix.core.annotation.DeletedDate;
import io.jmix.core.entity.annotation.JmixGeneratedValue;
import io.jmix.core.metamodel.annotation.DependsOnProperties;
import io.jmix.core.metamodel.annotation.InstanceName;
import io.jmix.core.metamodel.annotation.JmixEntity;
import io.jmix.core.metamodel.datatype.DatatypeFormatter;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;

import java.time.OffsetDateTime;
import java.util.UUID;

@JmixEntity
@Table(name = "HISTORICO_FINANCEIRO", indexes = {
        @Index(name = "IDX_HISTORICO_FINANCEIRO_UNQ", columnList = "CODIGO, COD_EMPRESA", unique = true)
})
@Entity
public class HistoricoFinanceiro {
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

    @Column(name = "CODIGO", nullable = false)
    @NotNull
    private Integer codigo;

    @Column(name = "COD_EMPRESA", nullable = false)
    @NotNull
    private Integer codEmpresa;

    @Column(name = "NOME", nullable = false, length = 30)
    @NotNull
    private String nome;

    @Column(name = "EMISSAO")
    private Boolean emissao = false;

    @Column(name = "BAIXA")
    private Boolean baixa = false;

    @Column(name = "PARCIAL")
    private Boolean parcial = false;

    @Column(name = "JUROS")
    private Boolean juros = false;

    @Column(name = "DESCONTO")
    private Boolean desconto = false;

    @JoinColumn(name = "CONTA_CONTABIL_RECEBER_ID")
    @ManyToOne(fetch = FetchType.LAZY)
    private ContaContabil contaContabilReceber;

    @JoinColumn(name = "HISTORICO_CONTABIL_RECEBER_ID")
    @ManyToOne(fetch = FetchType.LAZY)
    private HistoricoContabil historicoContabilReceber;

    @JoinColumn(name = "CONTA_CONTABIL_PAGAR_ID")
    @ManyToOne(fetch = FetchType.LAZY)
    private ContaContabil contaContabilPagar;

    @JoinColumn(name = "HISTORICO_CONTABIL_PAGAR_ID")
    @ManyToOne(fetch = FetchType.LAZY)
    private HistoricoContabil historicoContabilPagar;

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

    public Integer getCodigo() {
        return codigo;
    }

    public void setCodigo(Integer codigo) {
        this.codigo = codigo;
    }

    public Integer getCodEmpresa() {
        return codEmpresa;
    }

    public void setCodEmpresa(Integer codEmpresa) {
        this.codEmpresa = codEmpresa;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public Boolean getEmissao() {
        return emissao;
    }

    public void setEmissao(Boolean emissao) {
        this.emissao = emissao;
    }

    public Boolean getBaixa() {
        return baixa;
    }

    public void setBaixa(Boolean baixa) {
        this.baixa = baixa;
    }

    public Boolean getParcial() {
        return parcial;
    }

    public void setParcial(Boolean parcial) {
        this.parcial = parcial;
    }

    public Boolean getJuros() {
        return juros;
    }

    public void setJuros(Boolean juros) {
        this.juros = juros;
    }

    public Boolean getDesconto() {
        return desconto;
    }

    public void setDesconto(Boolean desconto) {
        this.desconto = desconto;
    }

    public ContaContabil getContaContabilReceber() {
        return contaContabilReceber;
    }

    public void setContaContabilReceber(ContaContabil contaContabilReceber) {
        this.contaContabilReceber = contaContabilReceber;
    }

    public HistoricoContabil getHistoricoContabilReceber() {
        return historicoContabilReceber;
    }

    public void setHistoricoContabilReceber(HistoricoContabil historicoContabilReceber) {
        this.historicoContabilReceber = historicoContabilReceber;
    }

    public ContaContabil getContaContabilPagar() {
        return contaContabilPagar;
    }

    public void setContaContabilPagar(ContaContabil contaContabilPagar) {
        this.contaContabilPagar = contaContabilPagar;
    }

    public HistoricoContabil getHistoricoContabilPagar() {
        return historicoContabilPagar;
    }

    public void setHistoricoContabilPagar(HistoricoContabil historicoContabilPagar) {
        this.historicoContabilPagar = historicoContabilPagar;
    }

    @InstanceName
    @DependsOnProperties({"codigo", "nome"})
    public String getInstanceName(MetadataTools metadataTools, DatatypeFormatter datatypeFormatter) {
        return String.format("%03d %s",
                codigo,
                metadataTools.format(nome));
    }
}
