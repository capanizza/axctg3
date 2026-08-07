package br.com.axialsoftware.axctg3.entity.financeiro;

import br.com.axialsoftware.axctg3.entity.contabil.ContaContabil;
import io.jmix.core.DeletePolicy;
import io.jmix.core.annotation.DeletedBy;
import io.jmix.core.annotation.DeletedDate;
import io.jmix.core.entity.annotation.JmixGeneratedValue;
import io.jmix.core.entity.annotation.OnDelete;
import io.jmix.core.metamodel.annotation.Composition;
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
 * Lançamento no extrato de um {@link Banco} — entrada ou saída, com contrapartida
 * contábil opcional ({@code contaContabil}; se vazia, {@code lancamentos} usa
 * {@code Empresa.contaMovimentoBancario}). {@code itens} é uma composição de um único
 * item interno (criado automaticamente por {@code MovimentoBancoEventListener}), que só
 * espelha o {@code contabilizado} do próprio movimento — não é editável em tela.
 */
@JmixEntity
@Table(name = "MOVIMENTO_BANCO", indexes = {
        @Index(name = "IDX_MOVIMENTO_BANCO_DATA", columnList = "DATA"),
        @Index(name = "IDX_MOVIMENTO_BANCO_CONTA_CONTABIL", columnList = "CONTA_CONTABIL_ID"),
        @Index(name = "IDX_MOVIMENTO_BANCO_BANCO", columnList = "BANCO_ID"),
        @Index(name = "IDX_MOVIMENTO_BANCO_UNQ_LANCAMENTO_COD_EMPRESA", columnList = "LANCAMENTO, COD_EMPRESA", unique = true)
})
@Entity
public class MovimentoBanco {

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
    @Column(name = "LANCAMENTO", nullable = false)
    @NotNull
    private Integer lancamento;

    @Column(name = "COD_EMPRESA", nullable = false)
    @NotNull
    private Integer codEmpresa;

    @Column(name = "DATA", nullable = false)
    @NotNull
    private LocalDate data;

    @JoinColumn(name = "BANCO_ID", nullable = false)
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Banco banco;

    @InstanceName
    @JoinColumn(name = "HISTORICO_FINANCEIRO_ID")
    @ManyToOne(fetch = FetchType.LAZY)
    private HistoricoFinanceiro historicoFinanceiro;

    @NumberFormat(pattern = "###,###,##0.00", decimalSeparator = ",", groupingSeparator = ".")
    @Column(name = "VALOR", nullable = false, precision = 19, scale = 2)
    @NotNull
    private BigDecimal valor = BigDecimal.ZERO;

    @Column(name = "DOCUMENTO", length = 20)
    private String documento;

    @Column(name = "DESCRICAO", length = 30)
    private String descricao;

    @NumberFormat(pattern = "#####0")
    @Column(name = "CHEQUE")
    private Integer cheque;

    @JoinColumn(name = "CONTA_CONTABIL_ID")
    @ManyToOne(fetch = FetchType.LAZY)
    private ContaContabil contaContabil;

    @Column(name = "ENTRADA")
    private Boolean entrada = false;

    @Column(name = "CONTABILIZADO")
    private Boolean contabilizado = false;

    @Column(name = "TIPO_SALDO")
    private Boolean tipoSaldo = false;

    @OnDelete(DeletePolicy.CASCADE)
    @Composition
    @OrderBy("item")
    @OneToMany(mappedBy = "movimentoBanco")
    private List<ItemMovimentoBanco> itens;

    public List<ItemMovimentoBanco> getItens() {
        return itens;
    }

    public void setItens(List<ItemMovimentoBanco> itens) {
        this.itens = itens;
    }

    public Boolean getTipoSaldo() {
        return tipoSaldo;
    }

    public void setTipoSaldo(Boolean tipoSaldo) {
        this.tipoSaldo = tipoSaldo;
    }

    public Boolean getContabilizado() {
        return contabilizado;
    }

    public void setContabilizado(Boolean contabilizado) {
        this.contabilizado = contabilizado;
    }

    public Boolean getEntrada() {
        return entrada;
    }

    public void setEntrada(Boolean entrada) {
        this.entrada = entrada;
    }

    public ContaContabil getContaContabil() {
        return contaContabil;
    }

    public void setContaContabil(ContaContabil contaContabil) {
        this.contaContabil = contaContabil;
    }

    public Integer getCheque() {
        return cheque;
    }

    public void setCheque(Integer cheque) {
        this.cheque = cheque;
    }

    public String getDescricao() {
        return descricao;
    }

    public void setDescricao(String descricao) {
        this.descricao = descricao;
    }

    public String getDocumento() {
        return documento;
    }

    public void setDocumento(String documento) {
        this.documento = documento;
    }

    public BigDecimal getValor() {
        return valor;
    }

    public void setValor(BigDecimal valor) {
        this.valor = valor;
    }

    public HistoricoFinanceiro getHistoricoFinanceiro() {
        return historicoFinanceiro;
    }

    public void setHistoricoFinanceiro(HistoricoFinanceiro historicoFinanceiro) {
        this.historicoFinanceiro = historicoFinanceiro;
    }

    public Banco getBanco() {
        return banco;
    }

    public void setBanco(Banco banco) {
        this.banco = banco;
    }

    public LocalDate getData() {
        return data;
    }

    public void setData(LocalDate data) {
        this.data = data;
    }

    public Integer getCodEmpresa() {
        return codEmpresa;
    }

    public void setCodEmpresa(Integer codEmpresa) {
        this.codEmpresa = codEmpresa;
    }

    public Integer getLancamento() {
        return lancamento;
    }

    public void setLancamento(Integer lancamento) {
        this.lancamento = lancamento;
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
    private Boolean lancado;

    public Boolean getLancado() {
        return lancado;
    }

    public void setLancado(Boolean lancado) {
        this.lancado = lancado;
    }

}
