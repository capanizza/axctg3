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

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Histórico financeiro do legado (HISTFIN) dentro de um {@link ImpLote} — ver {@code ImpLote}.
 * Cru, como no Firebird; a conversão é feita na importação.
 */
@JmixEntity
@Table(name = "IMP_HISTORICO_FINANCEIRO", indexes = {
        @Index(name = "IDX_IMP_HISTORICO_FINANCEIRO_LOTE", columnList = "LOTE_ID")
})
@Entity
public class ImpHistoricoFinanceiro {
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

    // HISTFIN como no legado: marcas S/N em texto; ENT (entrada) é o lado de pagar e SAI (saída)
    // o de receber; histórico contábil 0 ou vazio = sem histórico
    @Column(name = "CODIGO")
    private Integer codigo;

    @Column(name = "NOME")
    private String nome;

    @Column(name = "EMISSAO")
    private String emissao;

    @Column(name = "BAIXA")
    private String baixa;

    @Column(name = "PARCIAL")
    private String parcial;

    @Column(name = "JUROS")
    private String juros;

    @Column(name = "DESCONTO")
    private String desconto;

    @Column(name = "COD_CONTABIL_ENT")
    private String codContabilEnt;

    @Column(name = "HIST_ENT")
    private Integer histEnt;

    @Column(name = "COD_CONTABIL_SAI")
    private String codContabilSai;

    @Column(name = "HIST_SAI")
    private Integer histSai;

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

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getEmissao() {
        return emissao;
    }

    public void setEmissao(String emissao) {
        this.emissao = emissao;
    }

    public String getBaixa() {
        return baixa;
    }

    public void setBaixa(String baixa) {
        this.baixa = baixa;
    }

    public String getParcial() {
        return parcial;
    }

    public void setParcial(String parcial) {
        this.parcial = parcial;
    }

    public String getJuros() {
        return juros;
    }

    public void setJuros(String juros) {
        this.juros = juros;
    }

    public String getDesconto() {
        return desconto;
    }

    public void setDesconto(String desconto) {
        this.desconto = desconto;
    }

    public String getCodContabilEnt() {
        return codContabilEnt;
    }

    public void setCodContabilEnt(String codContabilEnt) {
        this.codContabilEnt = codContabilEnt;
    }

    public Integer getHistEnt() {
        return histEnt;
    }

    public void setHistEnt(Integer histEnt) {
        this.histEnt = histEnt;
    }

    public String getCodContabilSai() {
        return codContabilSai;
    }

    public void setCodContabilSai(String codContabilSai) {
        this.codContabilSai = codContabilSai;
    }

    public Integer getHistSai() {
        return histSai;
    }

    public void setHistSai(Integer histSai) {
        this.histSai = histSai;
    }
}
