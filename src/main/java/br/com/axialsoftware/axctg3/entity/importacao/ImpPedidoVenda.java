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
 * Pedido de venda do legado (PEDVENDA) dentro de um {@link ImpLote} — ver {@code ImpLote}. Cru,
 * como no Firebird; a conversão é feita na importação.
 */
@JmixEntity
@Table(name = "IMP_PEDIDO_VENDA", indexes = {
        @Index(name = "IDX_IMP_PEDIDO_VENDA_LOTE", columnList = "LOTE_ID")
})
@Entity
public class ImpPedidoVenda {
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

    // PEDVENDA como no legado: PARCEIRO, NATUREZA, CONDICAO, BANCO, TRANSPO e VENDEDOR são
    // códigos; TIPOFRETE 1/2; CANCELADO e FATURATOTAL S/N; os pesos somam os volumes (DETPEDVENDA)
    @Column(name = "REGISTRO")
    private Integer registro;

    // texto no legado (com zeros à esquerda: "002677"); a importação converte
    @Column(name = "NUMERO")
    private String numero;

    @Column(name = "DATA")
    private LocalDate data;

    @Column(name = "DATA_ENTRADA")
    private LocalDate dataEntrada;

    @Column(name = "PARCEIRO")
    private Integer parceiro;

    @Column(name = "NATUREZA")
    private Integer natureza;

    @Column(name = "CONDICAO")
    private Integer condicao;

    @Column(name = "BANCO")
    private Integer banco;

    @Column(name = "TRANSPO")
    private Integer transpo;

    @Column(name = "VENDEDOR")
    private Integer vendedor;

    @Column(name = "MENSAGEM")
    private Integer mensagem;

    // segunda observação do legado: o texto que ia para a nota
    @Lob
    @Column(name = "OBS1")
    private String obs1;

    @Column(name = "TIPO_FRETE")
    private String tipoFrete;

    @Column(name = "FRETE", precision = 19, scale = 8)
    private BigDecimal frete;

    @Column(name = "SEGURO", precision = 19, scale = 8)
    private BigDecimal seguro;

    @Column(name = "DESCONTO_1", precision = 19, scale = 8)
    private BigDecimal desconto1;

    @Column(name = "DESCONTO_2", precision = 19, scale = 8)
    private BigDecimal desconto2;

    @Lob
    @Column(name = "OBSERVACAO")
    private String observacao;

    @Column(name = "CANCELADO")
    private String cancelado;

    @Column(name = "FATURA_TOTAL")
    private String faturaTotal;

    @Column(name = "PESO_LIQUIDO", precision = 19, scale = 8)
    private BigDecimal pesoLiquido;

    @Column(name = "PESO_BRUTO", precision = 19, scale = 8)
    private BigDecimal pesoBruto;

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

    public String getNumero() {
        return numero;
    }

    public void setNumero(String numero) {
        this.numero = numero;
    }

    public LocalDate getData() {
        return data;
    }

    public void setData(LocalDate data) {
        this.data = data;
    }

    public LocalDate getDataEntrada() {
        return dataEntrada;
    }

    public void setDataEntrada(LocalDate dataEntrada) {
        this.dataEntrada = dataEntrada;
    }

    public Integer getParceiro() {
        return parceiro;
    }

    public void setParceiro(Integer parceiro) {
        this.parceiro = parceiro;
    }

    public Integer getNatureza() {
        return natureza;
    }

    public void setNatureza(Integer natureza) {
        this.natureza = natureza;
    }

    public Integer getCondicao() {
        return condicao;
    }

    public void setCondicao(Integer condicao) {
        this.condicao = condicao;
    }

    public Integer getBanco() {
        return banco;
    }

    public void setBanco(Integer banco) {
        this.banco = banco;
    }

    public Integer getTranspo() {
        return transpo;
    }

    public void setTranspo(Integer transpo) {
        this.transpo = transpo;
    }

    public Integer getVendedor() {
        return vendedor;
    }

    public void setVendedor(Integer vendedor) {
        this.vendedor = vendedor;
    }

    public String getTipoFrete() {
        return tipoFrete;
    }

    public void setTipoFrete(String tipoFrete) {
        this.tipoFrete = tipoFrete;
    }

    public BigDecimal getFrete() {
        return frete;
    }

    public void setFrete(BigDecimal frete) {
        this.frete = frete;
    }

    public BigDecimal getSeguro() {
        return seguro;
    }

    public void setSeguro(BigDecimal seguro) {
        this.seguro = seguro;
    }

    public BigDecimal getDesconto1() {
        return desconto1;
    }

    public void setDesconto1(BigDecimal desconto1) {
        this.desconto1 = desconto1;
    }

    public BigDecimal getDesconto2() {
        return desconto2;
    }

    public void setDesconto2(BigDecimal desconto2) {
        this.desconto2 = desconto2;
    }

    public String getObservacao() {
        return observacao;
    }

    public void setObservacao(String observacao) {
        this.observacao = observacao;
    }

    public String getCancelado() {
        return cancelado;
    }

    public void setCancelado(String cancelado) {
        this.cancelado = cancelado;
    }

    public String getFaturaTotal() {
        return faturaTotal;
    }

    public void setFaturaTotal(String faturaTotal) {
        this.faturaTotal = faturaTotal;
    }

    public BigDecimal getPesoLiquido() {
        return pesoLiquido;
    }

    public void setPesoLiquido(BigDecimal pesoLiquido) {
        this.pesoLiquido = pesoLiquido;
    }

    public BigDecimal getPesoBruto() {
        return pesoBruto;
    }

    public void setPesoBruto(BigDecimal pesoBruto) {
        this.pesoBruto = pesoBruto;
    }

    public Integer getMensagem() {
        return mensagem;
    }

    public void setMensagem(Integer mensagem) {
        this.mensagem = mensagem;
    }

    public String getObs1() {
        return obs1;
    }

    public void setObs1(String obs1) {
        this.obs1 = obs1;
    }
}
