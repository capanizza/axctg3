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
 * Nota de saída do legado (NOTASAIDA) dentro de um {@link ImpLote} — ver {@code ImpLote}. Crua,
 * como no Firebird; a conversão é feita na importação.
 */
@JmixEntity
@Table(name = "IMP_NOTA_SAIDA", indexes = {
        @Index(name = "IDX_IMP_NOTA_SAIDA_LOTE", columnList = "LOTE_ID")
})
@Entity
public class ImpNotaSaida {
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

    // NOTASAIDA como no legado: PARCEIRO, NATUREZA, CONDICAO, BANCO, TRANSPO e VENDEDOR são
    // códigos; TIPOFRETE 1/2; CANCELADA S/N; os pesos somam os volumes (DETNOTASAIDA)
    @Column(name = "REGISTRO")
    private Integer registro;

    @Column(name = "NUMERO")
    private Integer numero;

    @Column(name = "ESPECIE")
    private String especie;

    @Column(name = "SERIE")
    private String serie;

    @Column(name = "DATA_EMISSAO")
    private LocalDate dataEmissao;

    @Column(name = "DATA_SAIDA")
    private LocalDate dataSaida;

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

    @Column(name = "TIPO_FRETE")
    private String tipoFrete;

    @Column(name = "VALOR", precision = 19, scale = 8)
    private BigDecimal valor;

    @Column(name = "VALOR_MERCADORIA", precision = 19, scale = 8)
    private BigDecimal valorMercadoria;

    @Column(name = "BASE_ICMS", precision = 19, scale = 8)
    private BigDecimal baseIcms;

    @Column(name = "VALOR_ICMS", precision = 19, scale = 8)
    private BigDecimal valorIcms;

    @Column(name = "BASE_SUBSTITUICAO", precision = 19, scale = 8)
    private BigDecimal baseSubstituicao;

    @Column(name = "VALOR_SUBSTITUICAO", precision = 19, scale = 8)
    private BigDecimal valorSubstituicao;

    @Column(name = "VALOR_GNRE", precision = 19, scale = 8)
    private BigDecimal valorGnre;

    @Column(name = "BASE_IPI", precision = 19, scale = 8)
    private BigDecimal baseIpi;

    @Column(name = "VALOR_IPI", precision = 19, scale = 8)
    private BigDecimal valorIpi;

    @Column(name = "FRETE", precision = 19, scale = 8)
    private BigDecimal frete;

    @Column(name = "SEGURO", precision = 19, scale = 8)
    private BigDecimal seguro;

    @Column(name = "DESPESAS", precision = 19, scale = 8)
    private BigDecimal despesas;

    @Column(name = "DESCONTO", precision = 19, scale = 8)
    private BigDecimal desconto;

    @Column(name = "CANCELADA")
    private String cancelada;

    @Column(name = "CHAVE_NFE")
    private String chaveNfe;

    @Lob
    @Column(name = "OBSERVACAO")
    private String observacao;

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

    public Integer getNumero() {
        return numero;
    }

    public void setNumero(Integer numero) {
        this.numero = numero;
    }

    public String getEspecie() {
        return especie;
    }

    public void setEspecie(String especie) {
        this.especie = especie;
    }

    public String getSerie() {
        return serie;
    }

    public void setSerie(String serie) {
        this.serie = serie;
    }

    public LocalDate getDataEmissao() {
        return dataEmissao;
    }

    public void setDataEmissao(LocalDate dataEmissao) {
        this.dataEmissao = dataEmissao;
    }

    public LocalDate getDataSaida() {
        return dataSaida;
    }

    public void setDataSaida(LocalDate dataSaida) {
        this.dataSaida = dataSaida;
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

    public BigDecimal getValor() {
        return valor;
    }

    public void setValor(BigDecimal valor) {
        this.valor = valor;
    }

    public BigDecimal getValorMercadoria() {
        return valorMercadoria;
    }

    public void setValorMercadoria(BigDecimal valorMercadoria) {
        this.valorMercadoria = valorMercadoria;
    }

    public BigDecimal getBaseIcms() {
        return baseIcms;
    }

    public void setBaseIcms(BigDecimal baseIcms) {
        this.baseIcms = baseIcms;
    }

    public BigDecimal getValorIcms() {
        return valorIcms;
    }

    public void setValorIcms(BigDecimal valorIcms) {
        this.valorIcms = valorIcms;
    }

    public BigDecimal getBaseSubstituicao() {
        return baseSubstituicao;
    }

    public void setBaseSubstituicao(BigDecimal baseSubstituicao) {
        this.baseSubstituicao = baseSubstituicao;
    }

    public BigDecimal getValorSubstituicao() {
        return valorSubstituicao;
    }

    public void setValorSubstituicao(BigDecimal valorSubstituicao) {
        this.valorSubstituicao = valorSubstituicao;
    }

    public BigDecimal getValorGnre() {
        return valorGnre;
    }

    public void setValorGnre(BigDecimal valorGnre) {
        this.valorGnre = valorGnre;
    }

    public BigDecimal getBaseIpi() {
        return baseIpi;
    }

    public void setBaseIpi(BigDecimal baseIpi) {
        this.baseIpi = baseIpi;
    }

    public BigDecimal getValorIpi() {
        return valorIpi;
    }

    public void setValorIpi(BigDecimal valorIpi) {
        this.valorIpi = valorIpi;
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

    public BigDecimal getDespesas() {
        return despesas;
    }

    public void setDespesas(BigDecimal despesas) {
        this.despesas = despesas;
    }

    public BigDecimal getDesconto() {
        return desconto;
    }

    public void setDesconto(BigDecimal desconto) {
        this.desconto = desconto;
    }

    public String getCancelada() {
        return cancelada;
    }

    public void setCancelada(String cancelada) {
        this.cancelada = cancelada;
    }

    public String getChaveNfe() {
        return chaveNfe;
    }

    public void setChaveNfe(String chaveNfe) {
        this.chaveNfe = chaveNfe;
    }

    public String getObservacao() {
        return observacao;
    }

    public void setObservacao(String observacao) {
        this.observacao = observacao;
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
}
