package br.com.axialsoftware.axctg3.entity.financeiro;

import br.com.axialsoftware.axctg3.entity.cadastros.Parceiro;
import br.com.axialsoftware.axctg3.entity.fiscal.NotaSaida;
import io.jmix.core.DeletePolicy;
import io.jmix.core.annotation.DeletedBy;
import io.jmix.core.annotation.DeletedDate;
import io.jmix.core.entity.annotation.JmixGeneratedValue;
import io.jmix.core.entity.annotation.OnDelete;
import io.jmix.core.metamodel.annotation.Composition;
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
import java.util.List;
import java.util.UUID;

/**
 * Trazido do axctg-flow (módulo financeiro). Só entidade + changelog por ora — listener,
 * service (lançamentos de emissão, baixa) e telas ficam para quando o módulo fiscal
 * fornecer o restante de NotaSaida (natureza, valores) e a Empresa as contas de saída.
 */
@JmixEntity
@Table(name = "TITULO_RECEBER", indexes = {
        @Index(name = "IDX_TITULO_RECEBER_NOTA_SAIDA", columnList = "NOTA_SAIDA_ID"),
        @Index(name = "IDX_TITULO_RECEBER_DATA_EMISSAO", columnList = "DATA_EMISSAO"),
        @Index(name = "IDX_TITULO_RECEBER_DATA_VENCIMENTO", columnList = "DATA_VENCIMENTO"),
        @Index(name = "IDX_TITULO_RECEBER_PARCEIRO", columnList = "PARCEIRO_ID"),
        @Index(name = "IDX_TITULO_RECEBER_BANCO", columnList = "BANCO_ID"),
        @Index(name = "IDX_TITULO_RECEBER_UNQ_NUMERO_COD_EMPRESA", columnList = "NUMERO, COD_EMPRESA", unique = true)
})
@Entity
public class TituloReceber {
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

    @JoinColumn(name = "NOTA_SAIDA_ID")
    @ManyToOne(fetch = FetchType.LAZY)
    private NotaSaida notaSaida;

    @InstanceName
    @Column(name = "NUMERO", nullable = false, length = 10)
    @NotNull
    private String numero;

    @Column(name = "COD_EMPRESA", nullable = false)
    @NotNull
    private Integer codEmpresa;

    @Column(name = "DATA_EMISSAO", nullable = false)
    @NotNull
    private LocalDate dataEmissao;

    @Column(name = "DATA_VENCIMENTO", nullable = false)
    @NotNull
    private LocalDate dataVencimento;

    @JoinColumn(name = "PARCEIRO_ID", nullable = false)
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Parceiro parceiro;

    @JoinColumn(name = "BANCO_ID", nullable = false)
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Banco banco;

    @Column(name = "NUM_BANCO")
    private String numBanco;

    @NumberFormat(pattern = "###,###,##0.00", decimalSeparator = ",", groupingSeparator = ".")
    @Column(name = "VALOR", nullable = false, precision = 19, scale = 2)
    @NotNull
    private BigDecimal valor = BigDecimal.ZERO;

    @Column(name = "OBSERVACAO")
    @Lob
    private String observacao;

    @OnDelete(DeletePolicy.CASCADE)
    @Composition
    @OrderBy("item")
    @OneToMany(mappedBy = "tituloReceber")
    private List<ItemReceber> itens;

    public List<ItemReceber> getItens() {
        return itens;
    }

    public void setItens(List<ItemReceber> itens) {
        this.itens = itens;
    }

    public String getObservacao() {
        return observacao;
    }

    public void setObservacao(String observacao) {
        this.observacao = observacao;
    }

    public BigDecimal getValor() {
        return valor;
    }

    public void setValor(BigDecimal valor) {
        this.valor = valor;
    }

    public String getNumBanco() {
        return numBanco;
    }

    public void setNumBanco(String numBanco) {
        this.numBanco = numBanco;
    }

    public Banco getBanco() {
        return banco;
    }

    public void setBanco(Banco banco) {
        this.banco = banco;
    }

    public Parceiro getParceiro() {
        return parceiro;
    }

    public void setParceiro(Parceiro parceiro) {
        this.parceiro = parceiro;
    }

    public LocalDate getDataVencimento() {
        return dataVencimento;
    }

    public void setDataVencimento(LocalDate dataVencimento) {
        this.dataVencimento = dataVencimento;
    }

    public LocalDate getDataEmissao() {
        return dataEmissao;
    }

    public void setDataEmissao(LocalDate dataEmissao) {
        this.dataEmissao = dataEmissao;
    }

    public Integer getCodEmpresa() {
        return codEmpresa;
    }

    public void setCodEmpresa(Integer codEmpresa) {
        this.codEmpresa = codEmpresa;
    }

    public String getNumero() {
        return numero;
    }

    public void setNumero(String numero) {
        this.numero = numero;
    }

    public NotaSaida getNotaSaida() {
        return notaSaida;
    }

    public void setNotaSaida(NotaSaida notaSaida) {
        this.notaSaida = notaSaida;
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
