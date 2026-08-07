package br.com.axialsoftware.axctg3.entity.financeiro;

import br.com.axialsoftware.axctg3.entity.cadastros.Parceiro;
import br.com.axialsoftware.axctg3.entity.contabil.ContaContabil;
import io.jmix.core.DeletePolicy;
import io.jmix.core.annotation.DeletedBy;
import io.jmix.core.annotation.DeletedDate;
import io.jmix.core.entity.annotation.JmixGeneratedValue;
import io.jmix.core.entity.annotation.OnDelete;
import io.jmix.core.metamodel.annotation.Composition;
import io.jmix.core.metamodel.annotation.DependsOnProperties;
import io.jmix.core.metamodel.annotation.InstanceName;
import io.jmix.core.metamodel.annotation.JmixEntity;
import io.jmix.core.metamodel.annotation.JmixProperty;
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
 * Título a pagar avulso, sem nota fiscal de origem (numero vem de Sequence, não é
 * digitado). Trazido do axctg-flow. {@code itens} carrega item 1 (emissão, criado
 * automaticamente por {@code DiversoPagarEventListener}) e item 2+ (baixa, criado pelas
 * telas BaixaDiversoPagar). Os campos abaixo de {@code observacao} são calculados em
 * {@code DiversoPagarEventListener.onDiversoPagarLoading} — nunca persistidos.
 */
@JmixEntity
@Table(name = "DIVERSO_PAGAR", indexes = {
        @Index(name = "IDX_DIVERSO_PAGAR_DATA_EMISSAO", columnList = "DATA_EMISSAO"),
        @Index(name = "IDX_DIVERSO_PAGAR_DATA_VENCIMENTO", columnList = "DATA_VENCIMENTO"),
        @Index(name = "IDX_DIVERSO_PAGAR_PARCEIRO", columnList = "PARCEIRO_ID"),
        @Index(name = "IDX_DIVERSO_PAGAR_CONTA_CONTABIL", columnList = "CONTA_CONTABIL_ID"),
        @Index(name = "IDX_DIVERSO_PAGAR_UNQ_NUMERO_COD_EMPRESA", columnList = "NUMERO, COD_EMPRESA", unique = true)
})
@Entity
public class DiversoPagar {

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

    @NumberFormat(pattern = "########0")
    @Column(name = "NUMERO", nullable = false)
    @NotNull
    private Integer numero;

    @Column(name = "COD_EMPRESA", nullable = false)
    @NotNull
    private Integer codEmpresa;

    @InstanceName
    @Column(name = "DOCUMENTO", length = 20)
    private String documento;

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

    @NumberFormat(pattern = "###,###,##0.00", decimalSeparator = ",", groupingSeparator = ".")
    @Column(name = "VALOR", nullable = false, precision = 19, scale = 2)
    @NotNull
    private BigDecimal valor = BigDecimal.ZERO;

    @JoinColumn(name = "CONTA_CONTABIL_ID")
    @ManyToOne(fetch = FetchType.LAZY)
    private ContaContabil contaContabil;

    @Column(name = "OBSERVACAO")
    @Lob
    private String observacao;

    @OnDelete(DeletePolicy.CASCADE)
    @Composition
    @OrderBy("item")
    @OneToMany(mappedBy = "diversoPagar")
    private List<ItemDiversoPagar> itens;

    public List<ItemDiversoPagar> getItens() {
        return itens;
    }

    public void setItens(List<ItemDiversoPagar> itens) {
        this.itens = itens;
    }

    public String getObservacao() {
        return observacao;
    }

    public void setObservacao(String observacao) {
        this.observacao = observacao;
    }

    public ContaContabil getContaContabil() {
        return contaContabil;
    }

    public void setContaContabil(ContaContabil contaContabil) {
        this.contaContabil = contaContabil;
    }

    public BigDecimal getValor() {
        return valor;
    }

    public void setValor(BigDecimal valor) {
        this.valor = valor;
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

    public String getDocumento() {
        return documento;
    }

    public void setDocumento(String documento) {
        this.documento = documento;
    }

    public Integer getCodEmpresa() {
        return codEmpresa;
    }

    public void setCodEmpresa(Integer codEmpresa) {
        this.codEmpresa = codEmpresa;
    }

    public Integer getNumero() {
        return numero;
    }

    public void setNumero(Integer numero) {
        this.numero = numero;
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

    @Transient
    @JmixProperty
    private BigDecimal valorBaixado = BigDecimal.ZERO;

    public BigDecimal getValorBaixado() {
        return valorBaixado;
    }

    public void setValorBaixado(BigDecimal valorBaixado) {
        this.valorBaixado = valorBaixado;
    }

    @Transient
    @JmixProperty
    private BigDecimal valorAberto = BigDecimal.ZERO;

    public BigDecimal getValorAberto() {
        return valorAberto;
    }

    public void setValorAberto(BigDecimal valorAberto) {
        this.valorAberto = valorAberto;
    }

    @Transient
    @JmixProperty
    private Boolean aberto;

    public Boolean getAberto() {
        return aberto;
    }

    public void setAberto(Boolean aberto) {
        this.aberto = aberto;
    }

    @Transient
    @JmixProperty
    private Boolean contabilizadoEmissao;

    public Boolean getContabilizadoEmissao() {
        return contabilizadoEmissao;
    }

    public void setContabilizadoEmissao(Boolean contabilizadoEmissao) {
        this.contabilizadoEmissao = contabilizadoEmissao;
    }

    @Transient
    @JmixProperty
    private Boolean contabilizadoBaixa;

    public Boolean getContabilizadoBaixa() {
        return contabilizadoBaixa;
    }

    public void setContabilizadoBaixa(Boolean contabilizadoBaixa) {
        this.contabilizadoBaixa = contabilizadoBaixa;
    }

    @Transient
    @JmixProperty
    @DependsOnProperties({"dataVencimento", "numero"})
    private String ordemBaixa;

    public String getOrdemBaixa() {
        if (dataVencimento != null) {
            String ano = String.format("%d", dataVencimento.getYear());
            String mes = String.format("%02d", dataVencimento.getMonthValue());
            String dia = String.format("%02d", dataVencimento.getDayOfMonth());
            return ano + mes + dia + numero;
        } else {
            return "";
        }
    }

}
