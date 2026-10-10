package br.com.axialsoftware.axctg3.entity.importacao;

import br.com.axialsoftware.axctg3.entity.enums.SituacaoLote;
import br.com.axialsoftware.axctg3.entity.enums.TipoLote;
import io.jmix.core.annotation.DeletedBy;
import io.jmix.core.annotation.DeletedDate;
import io.jmix.core.entity.annotation.JmixGeneratedValue;
import io.jmix.core.metamodel.annotation.JmixEntity;
import io.jmix.core.metamodel.annotation.NumberFormat;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Um pacote de dados exportado do sistema legado pelo exportador do projeto Axial
 * ({@code exportacao2}), que grava este cabeçalho e as linhas ({@code ImpContaContabil},
 * {@code ImpSaldoConta}...) direto no banco, com os códigos do legado e sem UUID de nenhuma
 * entidade real. A tradução código→entidade, as regras e a conferência ficam do lado de cá,
 * na tela de importação ({@code ImpLote.list}). Ver {@code ImportacaoPlanoContasService}.
 */
@JmixEntity
@Table(name = "IMP_LOTE")
@Entity
public class ImpLote {
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

    @Column(name = "TIPO", nullable = false)
    @NotNull
    private Integer tipo;

    @Column(name = "SITUACAO", nullable = false)
    @NotNull
    private Integer situacao;

    // empresa de destino no axctg3 (os códigos foram renumerados na migração)
    @Column(name = "COD_EMPRESA", nullable = false)
    @NotNull
    private Integer codEmpresa;

    @Column(name = "COD_EMPRESA_LEGADO")
    private Integer codEmpresaLegado;

    @NumberFormat(pattern = "###0")
    @Column(name = "ANO")
    private Integer ano;

    @Column(name = "QTD_LINHAS")
    private Integer qtdLinhas;

    @Column(name = "DATA_IMPORTACAO")
    private OffsetDateTime dataImportacao;

    @Column(name = "MENSAGEM")
    @Lob
    private String mensagem;

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

    public TipoLote getTipo() {
        return tipo == null ? null : TipoLote.fromId(tipo);
    }

    public void setTipo(TipoLote tipo) {
        this.tipo = tipo == null ? null : tipo.getId();
    }

    public SituacaoLote getSituacao() {
        return situacao == null ? null : SituacaoLote.fromId(situacao);
    }

    public void setSituacao(SituacaoLote situacao) {
        this.situacao = situacao == null ? null : situacao.getId();
    }

    public Integer getCodEmpresa() {
        return codEmpresa;
    }

    public void setCodEmpresa(Integer codEmpresa) {
        this.codEmpresa = codEmpresa;
    }

    public Integer getCodEmpresaLegado() {
        return codEmpresaLegado;
    }

    public void setCodEmpresaLegado(Integer codEmpresaLegado) {
        this.codEmpresaLegado = codEmpresaLegado;
    }

    public Integer getAno() {
        return ano;
    }

    public void setAno(Integer ano) {
        this.ano = ano;
    }

    public Integer getQtdLinhas() {
        return qtdLinhas;
    }

    public void setQtdLinhas(Integer qtdLinhas) {
        this.qtdLinhas = qtdLinhas;
    }

    public OffsetDateTime getDataImportacao() {
        return dataImportacao;
    }

    public void setDataImportacao(OffsetDateTime dataImportacao) {
        this.dataImportacao = dataImportacao;
    }

    public String getMensagem() {
        return mensagem;
    }

    public void setMensagem(String mensagem) {
        this.mensagem = mensagem;
    }
}
