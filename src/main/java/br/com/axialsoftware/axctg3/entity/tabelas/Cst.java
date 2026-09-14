package br.com.axialsoftware.axctg3.entity.tabelas;

import io.jmix.core.MetadataTools;
import io.jmix.core.annotation.DeletedBy;
import io.jmix.core.annotation.DeletedDate;
import io.jmix.core.entity.annotation.JmixGeneratedValue;
import io.jmix.core.metamodel.annotation.DependsOnProperties;
import io.jmix.core.metamodel.annotation.InstanceName;
import io.jmix.core.metamodel.annotation.JmixEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Código de Situação Tributária do ICMS (CST, regime normal) ou Código de Situação da
 * Operação no Simples Nacional (CSOSN) — os dois catálogos oficiais que o layout da NFe
 * define pro grupo {@code ICMS} (Anexo do Convênio s/n de 1970, Tabela B / Ato COTEPE
 * 44/2018 pro CSOSN). Tabela global, sem {@code codEmpresa} — não muda por empresa nem
 * por ano, ao contrário de {@link br.com.axialsoftware.axctg3.entity.contabil.SaldoConta}
 * e afins. Populada por seed fixo no changelog (21 linhas, ambos os catálogos), não por
 * import — ao contrário de {@link ClassTrib}/{@code ContaReferencial}, aqui não existe
 * fonte externa que mude com frequência.
 *
 * <p>Substitui a árvore de decisão procedural que o legado (Delphi/Firebird, e antes dele
 * C/c-tree e Cobol) calculava na stored procedure {@code SituacaoTributaria} a partir de
 * flags cruzadas de {@code Natureza}/{@code Produto} (condição do ICMS, situação dentro/
 * fora do estado, redução, substituição...). Decisão 2026-09-14: em vez de replicar essa
 * árvore (que varia por cliente — pelo menos 10 variantes divergentes encontradas nos
 * scripts do sistema legado), o CST agora é só um valor de catálogo, referenciado
 * diretamente por {@link br.com.axialsoftware.axctg3.entity.fiscal.NaturezaOperacao#getCst()}
 * e {@link br.com.axialsoftware.axctg3.entity.fiscal.Produto#getCst()} — mesmo padrão de
 * {@code classTrib} pro IBS/CBS, inclusive a mesma precedência (ver
 * {@code ItemNotaSaidaEventListener}).
 */
@JmixEntity
@Table(name = "CST", indexes = {
        @Index(name = "IDX_CST_UNQ", columnList = "CODIGO", unique = true)
})
@Entity
public class Cst {
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

    // "00".."90" (regime normal, sempre 2 dígitos) ou "101".."900" (Simples/CSOSN,
    // sempre 3 dígitos) — o mesmo valor que vai literal no <CST>/<CSOSN> do XML da NFe.
    @Column(name = "CODIGO", nullable = false, length = 3)
    @NotNull
    private String codigo;

    @Column(name = "DESCRICAO", nullable = false, length = 150)
    @NotNull
    private String descricao;

    // false = tabela B (CST, regime normal); true = CSOSN (Simples Nacional). Só
    // informativo por ora — não filtra o picker automaticamente pelo CRT da empresa.
    @Column(name = "CSOSN", nullable = false)
    @NotNull
    private Boolean csosn = false;

    public String getCodigo() {
        return codigo;
    }

    public void setCodigo(String codigo) {
        this.codigo = codigo;
    }

    public String getDescricao() {
        return descricao;
    }

    public void setDescricao(String descricao) {
        this.descricao = descricao;
    }

    public Boolean getCsosn() {
        return csosn;
    }

    public void setCsosn(Boolean csosn) {
        this.csosn = csosn;
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
        return String.format("%s - %s", codigo, metadataTools.format(descricao));
    }
}
