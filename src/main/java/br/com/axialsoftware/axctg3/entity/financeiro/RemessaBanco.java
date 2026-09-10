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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Cabeçalho de auditoria de uma remessa bancária gerada (CNAB) pra cobrança de
 * {@link TituloReceber} — ver {@code RemessaBancoService}. Só o resumo é persistido; os
 * títulos incluídos ficam rastreáveis por {@link TituloReceber#getNumRemessa()} apontando
 * pra {@link #getNumRemessa()} desta entidade (mesmo banco/empresa). O conteúdo binário
 * exato do arquivo enviado não é guardado — só metadados de auditoria.
 */
@JmixEntity
@Table(name = "REMESSA_BANCO", indexes = {
        @Index(name = "IDX_REMESSA_BANCO_COD_EMPRESA", columnList = "COD_EMPRESA"),
        @Index(name = "IDX_REMESSA_BANCO_BANCO", columnList = "BANCO_ID")
})
@Entity
public class RemessaBanco {
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

    @Column(name = "DATA_GERACAO", nullable = false)
    @NotNull
    private LocalDate dataGeracao;

    @NumberFormat(pattern = "#######0")
    @Column(name = "NUM_REMESSA", nullable = false)
    @NotNull
    private Integer numRemessa;

    // Caminho completo (pasta + nome do arquivo) onde a remessa foi gravada em disco —
    // RemessaBancoService.gerarRemessa monta com PastaCobrancaBanco +
    // BancoCobrancaHandler.nomeArquivoRemessa. Diferente de RetornoBanco.nomeArquivo (só o
    // nome, porque ali o arquivo vem de upload do navegador, sem caminho fixo em disco).
    @Column(name = "CAMINHO_ARQUIVO", length = 255)
    private String caminhoArquivo;

    @NumberFormat(pattern = "##0")
    @Column(name = "QUANTIDADE_TITULOS", nullable = false)
    @NotNull
    private Integer quantidadeTitulos = 0;

    @NumberFormat(pattern = "###,###,##0.00", decimalSeparator = ",", groupingSeparator = ".")
    @Column(name = "VALOR_TOTAL", nullable = false, precision = 19, scale = 2)
    @NotNull
    private BigDecimal valorTotal = BigDecimal.ZERO;

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

    public String getCaminhoArquivo() {
        return caminhoArquivo;
    }

    public void setCaminhoArquivo(String caminhoArquivo) {
        this.caminhoArquivo = caminhoArquivo;
    }

    public LocalDate getDataGeracao() {
        return dataGeracao;
    }

    public void setDataGeracao(LocalDate dataGeracao) {
        this.dataGeracao = dataGeracao;
    }

    public Integer getNumRemessa() {
        return numRemessa;
    }

    public void setNumRemessa(Integer numRemessa) {
        this.numRemessa = numRemessa;
    }

    public Integer getQuantidadeTitulos() {
        return quantidadeTitulos;
    }

    public void setQuantidadeTitulos(Integer quantidadeTitulos) {
        this.quantidadeTitulos = quantidadeTitulos;
    }

    public BigDecimal getValorTotal() {
        return valorTotal;
    }

    public void setValorTotal(BigDecimal valorTotal) {
        this.valorTotal = valorTotal;
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
    @DependsOnProperties({"numRemessa", "banco", "dataGeracao"})
    public String getInstanceName(MetadataTools metadataTools) {
        return String.format("%d %s %s",
                numRemessa,
                banco == null ? "" : metadataTools.format(banco),
                dataGeracao == null ? "" : dataGeracao.toString());
    }
}
