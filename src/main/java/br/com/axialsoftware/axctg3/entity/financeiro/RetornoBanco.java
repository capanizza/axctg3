package br.com.axialsoftware.axctg3.entity.financeiro;

import io.jmix.core.MetadataTools;
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

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Cabeçalho de auditoria de um arquivo de retorno bancário (CNAB) processado — ver
 * {@code RetornoBancoService}. Guarda só o resumo do processamento (contadores); o efeito
 * em cada {@link TituloReceber}/{@link ItemReceber} já fica registrado nessas entidades
 * (via {@code numBanco} e as baixas criadas). Não persiste o conteúdo binário do arquivo
 * nem o detalhe linha a linha — resumo mostrado ao usuário na hora do processamento.
 */
@JmixEntity
@Table(name = "RETORNO_BANCO", indexes = {
        @Index(name = "IDX_RETORNO_BANCO_COD_EMPRESA", columnList = "COD_EMPRESA"),
        @Index(name = "IDX_RETORNO_BANCO_BANCO", columnList = "BANCO_ID")
})
@Entity
public class RetornoBanco {
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

    @Column(name = "COD_EMPRESA", nullable = false)
    @NotNull
    private Integer codEmpresa;

    @JoinColumn(name = "BANCO_ID", nullable = false)
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Banco banco;

    @InstanceName
    @Column(name = "DATA_PROCESSAMENTO", nullable = false)
    @NotNull
    private LocalDate dataProcessamento;

    @Column(name = "NOME_ARQUIVO", length = 255)
    private String nomeArquivo;

    @NumberFormat(pattern = "##0")
    @Column(name = "QTD_CONFIRMADOS", nullable = false)
    @NotNull
    private Integer quantidadeConfirmados = 0;

    @NumberFormat(pattern = "##0")
    @Column(name = "QTD_BAIXADOS", nullable = false)
    @NotNull
    private Integer quantidadeBaixados = 0;

    @NumberFormat(pattern = "##0")
    @Column(name = "QTD_REJEITADOS", nullable = false)
    @NotNull
    private Integer quantidadeRejeitados = 0;

    @NumberFormat(pattern = "##0")
    @Column(name = "QTD_NAO_ENCONTRADOS", nullable = false)
    @NotNull
    private Integer quantidadeNaoEncontrados = 0;

    public Integer getCodEmpresa() {
        return codEmpresa;
    }

    public void setCodEmpresa(Integer codEmpresa) {
        this.codEmpresa = codEmpresa;
    }

    public Banco getBanco() {
        return banco;
    }

    public void setBanco(Banco banco) {
        this.banco = banco;
    }

    public LocalDate getDataProcessamento() {
        return dataProcessamento;
    }

    public void setDataProcessamento(LocalDate dataProcessamento) {
        this.dataProcessamento = dataProcessamento;
    }

    public String getNomeArquivo() {
        return nomeArquivo;
    }

    public void setNomeArquivo(String nomeArquivo) {
        this.nomeArquivo = nomeArquivo;
    }

    public Integer getQuantidadeConfirmados() {
        return quantidadeConfirmados;
    }

    public void setQuantidadeConfirmados(Integer quantidadeConfirmados) {
        this.quantidadeConfirmados = quantidadeConfirmados;
    }

    public Integer getQuantidadeBaixados() {
        return quantidadeBaixados;
    }

    public void setQuantidadeBaixados(Integer quantidadeBaixados) {
        this.quantidadeBaixados = quantidadeBaixados;
    }

    public Integer getQuantidadeRejeitados() {
        return quantidadeRejeitados;
    }

    public void setQuantidadeRejeitados(Integer quantidadeRejeitados) {
        this.quantidadeRejeitados = quantidadeRejeitados;
    }

    public Integer getQuantidadeNaoEncontrados() {
        return quantidadeNaoEncontrados;
    }

    public void setQuantidadeNaoEncontrados(Integer quantidadeNaoEncontrados) {
        this.quantidadeNaoEncontrados = quantidadeNaoEncontrados;
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
