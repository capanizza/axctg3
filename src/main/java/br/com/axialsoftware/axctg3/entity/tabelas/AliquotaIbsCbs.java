package br.com.axialsoftware.axctg3.entity.tabelas;

import io.jmix.core.annotation.DeletedBy;
import io.jmix.core.annotation.DeletedDate;
import io.jmix.core.entity.annotation.JmixGeneratedValue;
import io.jmix.core.metamodel.annotation.DependsOnProperties;
import io.jmix.core.metamodel.annotation.InstanceName;
import io.jmix.core.metamodel.annotation.JmixEntity;
import io.jmix.core.metamodel.annotation.NumberFormat;
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
 * Alíquota de referência nacional do IBS/CBS por ano civil (EC 132/2023, LC 214/2025).
 * Tabela global, sem codEmpresa — assim como {@link ClassTrib}, não é uma configuração
 * "por empresa": o cronograma de transição (alíquota-teste em 2026, elevação gradual até
 * o regime pleno em 2033) é o mesmo valor pra qualquer empresa do país. Este projeto não
 * modela regime tributário (Simples Nacional trata IBS/CBS de forma diferente) — se isso
 * um dia entrar em escopo, aí sim a alíquota pode deixar de ser só "por ano".
 *
 * <p>Só informativo/referência por enquanto: não é lida automaticamente por
 * {@code NaturezaOperacao}/{@code Produto} — cada natureza continua com sua própria
 * {@code aliqIbsUf}/{@code aliqIbsMun}/{@code aliqCbs} preenchida manualmente. Ver
 * docs/REFORMA-TRIBUTARIA-IBS-CBS.md.
 *
 * <p>Imposto Seletivo (IS) fica de fora: ainda não tem lei definindo alíquotas, e mesmo
 * quando tiver, não vai ser "uma alíquota básica" — é por produto/NCM, como o IPI.
 */
@JmixEntity
@Table(name = "ALIQUOTA_IBS_CBS", indexes = {
        @Index(name = "IDX_ALIQUOTA_IBS_CBS_UNQ", columnList = "ANO", unique = true)
})
@Entity
public class AliquotaIbsCbs {
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

    @Column(name = "ANO", nullable = false)
    @NumberFormat(pattern = "0000")
    @NotNull
    private Integer ano;

    @Column(name = "ALIQ_IBS_UF", nullable = false, precision = 7, scale = 2)
    @NumberFormat(pattern = "##0.00")
    @NotNull
    private BigDecimal aliqIbsUf = BigDecimal.ZERO;

    @Column(name = "ALIQ_IBS_MUN", nullable = false, precision = 7, scale = 2)
    @NumberFormat(pattern = "##0.00")
    @NotNull
    private BigDecimal aliqIbsMun = BigDecimal.ZERO;

    @Column(name = "ALIQ_CBS", nullable = false, precision = 7, scale = 2)
    @NumberFormat(pattern = "##0.00")
    @NotNull
    private BigDecimal aliqCbs = BigDecimal.ZERO;

    public BigDecimal getAliqCbs() {
        return aliqCbs;
    }

    public void setAliqCbs(BigDecimal aliqCbs) {
        this.aliqCbs = aliqCbs;
    }

    public BigDecimal getAliqIbsMun() {
        return aliqIbsMun;
    }

    public void setAliqIbsMun(BigDecimal aliqIbsMun) {
        this.aliqIbsMun = aliqIbsMun;
    }

    public BigDecimal getAliqIbsUf() {
        return aliqIbsUf;
    }

    public void setAliqIbsUf(BigDecimal aliqIbsUf) {
        this.aliqIbsUf = aliqIbsUf;
    }

    public Integer getAno() {
        return ano;
    }

    public void setAno(Integer ano) {
        this.ano = ano;
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
    @DependsOnProperties({"ano", "aliqIbsUf", "aliqIbsMun", "aliqCbs"})
    public String getInstanceName() {
        BigDecimal aliqIbs = (aliqIbsUf == null ? BigDecimal.ZERO : aliqIbsUf)
                .add(aliqIbsMun == null ? BigDecimal.ZERO : aliqIbsMun);
        return String.format("%d — IBS %s%% / CBS %s%%", ano, aliqIbs, aliqCbs);
    }
}
