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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Movimento bancário do legado (MOVBANCO) dentro de um {@link ImpLote} — ver {@code ImpLote}. Cru,
 * como no Firebird; a conversão é feita na importação.
 */
@JmixEntity
@Table(name = "IMP_MOVIMENTO_BANCO", indexes = {
        @Index(name = "IDX_IMP_MOVIMENTO_BANCO_LOTE", columnList = "LOTE_ID")
})
@Entity
public class ImpMovimentoBanco {
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

    // MOVBANCO como no legado: ENTRADA e TIPOSALDO (registro de saldo do dia) são marcas S/N;
    // REGISTRO vira o número do lançamento no axctg3
    @Column(name = "REGISTRO")
    private Integer registro;

    @Column(name = "DATA")
    private LocalDate data;

    @Column(name = "HISTORICO")
    private Integer historico;

    @Column(name = "BANCO")
    private Integer banco;

    @Column(name = "DOCUMENTO")
    private String documento;

    @Column(name = "DESCRICAO")
    private String descricao;

    @Column(name = "VALOR", precision = 19, scale = 2)
    private BigDecimal valor;

    @Column(name = "CHEQUE")
    private Integer cheque;

    @Column(name = "ENTRADA")
    private String entrada;

    @Column(name = "TIPO_SALDO")
    private String tipoSaldo;

    @Column(name = "COD_CONTABIL")
    private String codContabil;

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

    public Integer getRegistro() {
        return registro;
    }

    public void setRegistro(Integer registro) {
        this.registro = registro;
    }

    public LocalDate getData() {
        return data;
    }

    public void setData(LocalDate data) {
        this.data = data;
    }

    public Integer getHistorico() {
        return historico;
    }

    public void setHistorico(Integer historico) {
        this.historico = historico;
    }

    public Integer getBanco() {
        return banco;
    }

    public void setBanco(Integer banco) {
        this.banco = banco;
    }

    public String getDocumento() {
        return documento;
    }

    public void setDocumento(String documento) {
        this.documento = documento;
    }

    public String getDescricao() {
        return descricao;
    }

    public void setDescricao(String descricao) {
        this.descricao = descricao;
    }

    public BigDecimal getValor() {
        return valor;
    }

    public void setValor(BigDecimal valor) {
        this.valor = valor;
    }

    public Integer getCheque() {
        return cheque;
    }

    public void setCheque(Integer cheque) {
        this.cheque = cheque;
    }

    public String getEntrada() {
        return entrada;
    }

    public void setEntrada(String entrada) {
        this.entrada = entrada;
    }

    public String getTipoSaldo() {
        return tipoSaldo;
    }

    public void setTipoSaldo(String tipoSaldo) {
        this.tipoSaldo = tipoSaldo;
    }

    public String getCodContabil() {
        return codContabil;
    }

    public void setCodContabil(String codContabil) {
        this.codContabil = codContabil;
    }
}
