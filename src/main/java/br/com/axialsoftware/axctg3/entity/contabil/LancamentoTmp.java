package br.com.axialsoftware.axctg3.entity.contabil;

import br.com.axialsoftware.axctg3.entity.cadastros.CentroCusto;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@JmixEntity
@Table(name = "LANCAMENTO_TMP",
        indexes = {
                @Index(name = "IDX_LANCAMENTO_TMP_UNQ", columnList = "NUMERO, ANO, MES, COD_EMPRESA", unique = true)
        })
@Entity
public class LancamentoTmp {
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

    @NumberFormat(pattern = "#####0")
    @Column(name = "NUMERO", nullable = false)
    @NotNull
    private Integer numero;

    @NumberFormat(pattern = "###0")
    @Column(name = "ANO", nullable = false)
    @NotNull
    private Integer ano;

    @NumberFormat(pattern = "#0")
    @Column(name = "MES", nullable = false)
    @NotNull
    private Integer mes;

    @NumberFormat(pattern = "00")
    @Column(name = "DIA", nullable = false)
    @NotNull
    private Integer dia;

    @Column(name = "COD_EMPRESA", nullable = false)
    @NotNull
    private Integer codEmpresa;

    @Column(name = "DATA_LANCAMENTO", nullable = false)
    @NotNull
    private LocalDate dataLancamento;

    @JoinColumn(name = "CONTA_DEVEDORA_ID")
    @ManyToOne(fetch = FetchType.LAZY)
    private ContaContabil contaDevedora;

    @JoinColumn(name = "CONTA_CREDORA_ID")
    @ManyToOne(fetch = FetchType.LAZY)
    private ContaContabil contaCredora;

    @JoinColumn(name = "CENTRO_CUSTO_ID")
    @ManyToOne(fetch = FetchType.LAZY)
    private CentroCusto centroCusto;

    @JoinColumn(name = "HISTORICO_CONTABIL_ID")
    @ManyToOne(fetch = FetchType.LAZY)
    private HistoricoContabil historicoContabil;

    @Column(name = "COMPLEMENTO_HISTORICO")
    @Lob
    private String complementoHistorico;

    @Column(name = "ORIGEM", length = 25)
    private String origem;

    @NumberFormat(pattern = "###,###,###,##0.00", decimalSeparator = ",", groupingSeparator = ".")
    @Column(name = "VALOR", nullable = false, precision = 19, scale = 2)
    @NotNull
    private BigDecimal valor;

    @Column(name = "DEVEDORA", length = 30)
    private String devedora;

    @Column(name = "CREDORA", length = 30)
    private String credora;

    @Column(name = "CCUSTO", length = 10)
    private String cCusto;

    @Column(name = "HIST")
    private Integer hist;

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

    public Integer getNumero() {
        return numero;
    }

    public void setNumero(Integer numero) {
        this.numero = numero;
    }

    public Integer getAno() {
        return ano;
    }

    public void setAno(Integer ano) {
        this.ano = ano;
    }

    public Integer getMes() {
        return mes;
    }

    public void setMes(Integer mes) {
        this.mes = mes;
    }

    public Integer getDia() {
        return dia;
    }

    public void setDia(Integer dia) {
        this.dia = dia;
    }

    public Integer getCodEmpresa() {
        return codEmpresa;
    }

    public void setCodEmpresa(Integer codEmpresa) {
        this.codEmpresa = codEmpresa;
    }

    public LocalDate getDataLancamento() {
        return dataLancamento;
    }

    public void setDataLancamento(LocalDate dataLancamento) {
        this.dataLancamento = dataLancamento;
    }

    public ContaContabil getContaDevedora() {
        return contaDevedora;
    }

    public void setContaDevedora(ContaContabil contaDevedora) {
        this.contaDevedora = contaDevedora;
    }

    public ContaContabil getContaCredora() {
        return contaCredora;
    }

    public void setContaCredora(ContaContabil contaCredora) {
        this.contaCredora = contaCredora;
    }

    public CentroCusto getCentroCusto() {
        return centroCusto;
    }

    public void setCentroCusto(CentroCusto centroCusto) {
        this.centroCusto = centroCusto;
    }

    public HistoricoContabil getHistoricoContabil() {
        return historicoContabil;
    }

    public void setHistoricoContabil(HistoricoContabil historicoContabil) {
        this.historicoContabil = historicoContabil;
    }

    public String getComplementoHistorico() {
        return complementoHistorico;
    }

    public void setComplementoHistorico(String complementoHistorico) {
        this.complementoHistorico = complementoHistorico;
    }

    public String getOrigem() {
        return origem;
    }

    public void setOrigem(String origem) {
        this.origem = origem;
    }

    public BigDecimal getValor() {
        return valor;
    }

    public void setValor(BigDecimal valor) {
        this.valor = valor;
    }

    public String getDevedora() {
        return devedora;
    }

    public void setDevedora(String devedora) {
        this.devedora = devedora;
    }

    public String getCredora() {
        return credora;
    }

    public void setCredora(String credora) {
        this.credora = credora;
    }

    public String getcCusto() {
        return cCusto;
    }

    public void setcCusto(String cCusto) {
        this.cCusto = cCusto;
    }

    public Integer getHist() {
        return hist;
    }

    public void setHist(Integer hist) {
        this.hist = hist;
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