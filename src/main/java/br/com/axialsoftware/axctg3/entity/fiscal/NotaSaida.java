package br.com.axialsoftware.axctg3.entity.fiscal;

import br.com.axialsoftware.axctg3.entity.cadastros.CondicaoPagamento;
import br.com.axialsoftware.axctg3.entity.cadastros.Mensagem;
import br.com.axialsoftware.axctg3.entity.cadastros.Parceiro;
import br.com.axialsoftware.axctg3.entity.cadastros.Transportadora;
import br.com.axialsoftware.axctg3.entity.cadastros.Vendedor;
import br.com.axialsoftware.axctg3.entity.financeiro.Banco;
import br.com.axialsoftware.axctg3.entity.tabelas.ClassTrib;
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
import jakarta.validation.constraints.NotBlank;
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
 * Nota fiscal de saída. Deliberadamente **não** é a NFe completa — {@code chave} apenas
 * guarda a chave de acesso da NFe correspondente (quando emitida), que é uma entidade
 * separada, ainda não modelada. Portada de
 * {@code axctg-flow/.../entity/fiscal/NotaSaida.java}, referenciada por
 * {@link br.com.axialsoftware.axctg3.entity.financeiro.TituloReceber}.
 */
@JmixEntity
@Table(name = "NOTA_SAIDA", indexes = {
        @Index(name = "IDX_NOTA_SAIDA_DATA_EMISSAO", columnList = "DATA_EMISSAO"),
        @Index(name = "IDX_NOTA_SAIDA_PARCEIRO", columnList = "PARCEIRO_ID"),
        @Index(name = "IDX_NOTA_SAIDA_NATUREZA", columnList = "NATUREZA_ID"),
        @Index(name = "IDX_NOTA_SAIDA_CONDICAO_PAGAMENTO", columnList = "CONDICAO_PAGAMENTO_ID"),
        @Index(name = "IDX_NOTA_SAIDA_BANCO", columnList = "BANCO_ID"),
        @Index(name = "IDX_NOTA_SAIDA_TRANSPORTADORA", columnList = "TRANSPORTADORA_ID"),
        @Index(name = "IDX_NOTA_SAIDA_VENDEDOR", columnList = "VENDEDOR_ID"),
        @Index(name = "IDX_NOTA_SAIDA_CLASS_TRIB", columnList = "CLASS_TRIB_ID"),
        @Index(name = "IDX_NOTA_SAIDA_MENSAGEM", columnList = "MENSAGEM_ID"),
        @Index(name = "IDX_NOTA_SAIDA_UNQ", columnList = "NUMERO, COD_EMPRESA, ESPECIE, SERIE", unique = true)
})
@Entity
public class NotaSaida {
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

    @InstanceName
    @NumberFormat(pattern = "########0")
    @Column(name = "NUMERO", nullable = false)
    @NotNull
    private Integer numero;

    // sem valor padrão: o NotaSaidaEventListener preenche com a empresa
    // corrente quando está nulo
    @Column(name = "COD_EMPRESA", nullable = false)
    @NotNull
    private Integer codEmpresa;

    @Column(name = "DATA_EMISSAO", nullable = false)
    @NotNull
    private LocalDate dataEmissao;

    @Column(name = "DATA_SAIDA", nullable = false)
    @NotNull
    private LocalDate dataSaida;

    @Column(name = "ESPECIE", nullable = false, length = 4)
    @NotNull
    private String especie;

    @Column(name = "SERIE", nullable = false, length = 2)
    @NotBlank
    private String serie;

    @JoinColumn(name = "PARCEIRO_ID", nullable = false)
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Parceiro parceiro;

    @JoinColumn(name = "NATUREZA_ID", nullable = false)
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private NaturezaOperacao natureza;

    @JoinColumn(name = "CONDICAO_PAGAMENTO_ID")
    @ManyToOne(fetch = FetchType.LAZY)
    private CondicaoPagamento condicaoPagamento;

    @JoinColumn(name = "BANCO_ID")
    @ManyToOne(fetch = FetchType.LAZY)
    private Banco banco;

    @JoinColumn(name = "TRANSPORTADORA_ID")
    @ManyToOne(fetch = FetchType.LAZY)
    private Transportadora transportadora;

    @JoinColumn(name = "VENDEDOR_ID")
    @ManyToOne(fetch = FetchType.LAZY)
    private Vendedor vendedor;

    // Código de Classificação Tributária (cClassTrib no leiaute NFe/NFCe, grupo
    // gIBSCBS) — referência real pra ClassTrib, mesmo padrão de
    // NaturezaOperacao.classTrib/Produto.classTrib. Nullable: a nota pode nascer sem
    // classificação escolhida e ser completada depois, antes da emissão.
    @JoinColumn(name = "CLASS_TRIB_ID")
    @ManyToOne(fetch = FetchType.LAZY)
    private ClassTrib classTrib;

    @JoinColumn(name = "MENSAGEM_ID")
    @ManyToOne(fetch = FetchType.LAZY)
    private Mensagem mensagem;

    // mesmo padrão de Lancamento.complementoHistorico: texto livre que complementa a
    // Mensagem cadastrada, digitado nesta nota específica
    @Column(name = "COMPLEMENTO_MENSAGEM")
    @Lob
    private String complementoMensagem;

    @NumberFormat(pattern = "###,###,##0.00", decimalSeparator = ",", groupingSeparator = ".")
    @Column(name = "VALOR", precision = 19, scale = 2)
    private BigDecimal valor = BigDecimal.ZERO;

    @NumberFormat(pattern = "###,###,##0.00", decimalSeparator = ",", groupingSeparator = ".")
    @Column(name = "VALOR_MERCADORIA", precision = 19, scale = 2)
    private BigDecimal valorMercadoria = BigDecimal.ZERO;

    @NumberFormat(pattern = "###,###,##0.00", decimalSeparator = ",", groupingSeparator = ".")
    @Column(name = "BASE_ICMS", precision = 19, scale = 2)
    private BigDecimal baseIcms = BigDecimal.ZERO;

    @NumberFormat(pattern = "###,###,##0.00", decimalSeparator = ",", groupingSeparator = ".")
    @Column(name = "VALOR_ICMS", precision = 19, scale = 2)
    private BigDecimal valorIcms = BigDecimal.ZERO;

    @NumberFormat(pattern = "###,###,##0.00", decimalSeparator = ",", groupingSeparator = ".")
    @Column(name = "BASE_ST", precision = 19, scale = 2)
    private BigDecimal baseSt = BigDecimal.ZERO;

    @NumberFormat(pattern = "###,###,##0.00", decimalSeparator = ",", groupingSeparator = ".")
    @Column(name = "VALOR_ST", precision = 19, scale = 2)
    private BigDecimal valorSt = BigDecimal.ZERO;

    @NumberFormat(pattern = "###,###,##0.00", decimalSeparator = ",", groupingSeparator = ".")
    @Column(name = "BASE_IPI", precision = 19, scale = 2)
    private BigDecimal baseIpi = BigDecimal.ZERO;

    @NumberFormat(pattern = "###,###,##0.00", decimalSeparator = ",", groupingSeparator = ".")
    @Column(name = "VALOR_IPI", precision = 19, scale = 2)
    private BigDecimal valorIpi = BigDecimal.ZERO;

    @NumberFormat(pattern = "###,###,##0.00", decimalSeparator = ",", groupingSeparator = ".")
    @Column(name = "FRETE", precision = 19, scale = 2)
    private BigDecimal frete = BigDecimal.ZERO;

    @NumberFormat(pattern = "###,###,##0.00", decimalSeparator = ",", groupingSeparator = ".")
    @Column(name = "SEGURO", precision = 19, scale = 2)
    private BigDecimal seguro = BigDecimal.ZERO;

    @NumberFormat(pattern = "###,###,##0.00", decimalSeparator = ",", groupingSeparator = ".")
    @Column(name = "DESPESAS", precision = 19, scale = 2)
    private BigDecimal despesas = BigDecimal.ZERO;

    @NumberFormat(pattern = "###,###,##0.00", decimalSeparator = ",", groupingSeparator = ".")
    @Column(name = "DESCONTO", precision = 19, scale = 2)
    private BigDecimal desconto = BigDecimal.ZERO;

    @Column(name = "CANCELADA")
    private Boolean cancelada = false;

    @NumberFormat(pattern = "###,###,##0.00000", decimalSeparator = ",", groupingSeparator = ".")
    @Column(name = "PESO_LIQUIDO", precision = 19, scale = 5)
    private BigDecimal pesoLiquido = BigDecimal.ZERO;

    @NumberFormat(pattern = "###,###,##0.00000", decimalSeparator = ",", groupingSeparator = ".")
    @Column(name = "PESO_BRUTO", precision = 19, scale = 5)
    private BigDecimal pesoBruto = BigDecimal.ZERO;

    // chave de acesso da NFe correspondente (44 dígitos no leiaute oficial, mas o legado
    // guarda com folga em 50) — liga esta NotaSaida à entidade NFe, que ainda não existe
    // no projeto. Preenchida só depois da nota autorizada, então fica somente leitura na
    // tela.
    @Column(name = "CHAVE", length = 50)
    private String chave;

    @OnDelete(DeletePolicy.CASCADE)
    @Composition
    @OrderBy("item")
    @OneToMany(mappedBy = "notaSaida")
    private List<ItemNotaSaida> itens;

    public List<ItemNotaSaida> getItens() {
        return itens;
    }

    public void setItens(List<ItemNotaSaida> itens) {
        this.itens = itens;
    }

    public String getChave() {
        return chave;
    }

    public void setChave(String chave) {
        this.chave = chave;
    }

    public BigDecimal getPesoBruto() {
        return pesoBruto;
    }

    public void setPesoBruto(BigDecimal pesoBruto) {
        this.pesoBruto = pesoBruto;
    }

    public BigDecimal getPesoLiquido() {
        return pesoLiquido;
    }

    public void setPesoLiquido(BigDecimal pesoLiquido) {
        this.pesoLiquido = pesoLiquido;
    }

    public Boolean getCancelada() {
        return cancelada;
    }

    public void setCancelada(Boolean cancelada) {
        this.cancelada = cancelada;
    }

    public BigDecimal getDesconto() {
        return desconto;
    }

    public void setDesconto(BigDecimal desconto) {
        this.desconto = desconto;
    }

    public BigDecimal getDespesas() {
        return despesas;
    }

    public void setDespesas(BigDecimal despesas) {
        this.despesas = despesas;
    }

    public BigDecimal getSeguro() {
        return seguro;
    }

    public void setSeguro(BigDecimal seguro) {
        this.seguro = seguro;
    }

    public BigDecimal getFrete() {
        return frete;
    }

    public void setFrete(BigDecimal frete) {
        this.frete = frete;
    }

    public BigDecimal getValorIpi() {
        return valorIpi;
    }

    public void setValorIpi(BigDecimal valorIpi) {
        this.valorIpi = valorIpi;
    }

    public BigDecimal getBaseIpi() {
        return baseIpi;
    }

    public void setBaseIpi(BigDecimal baseIpi) {
        this.baseIpi = baseIpi;
    }

    public BigDecimal getValorSt() {
        return valorSt;
    }

    public void setValorSt(BigDecimal valorSt) {
        this.valorSt = valorSt;
    }

    public BigDecimal getBaseSt() {
        return baseSt;
    }

    public void setBaseSt(BigDecimal baseSt) {
        this.baseSt = baseSt;
    }

    public BigDecimal getValorIcms() {
        return valorIcms;
    }

    public void setValorIcms(BigDecimal valorIcms) {
        this.valorIcms = valorIcms;
    }

    public BigDecimal getBaseIcms() {
        return baseIcms;
    }

    public void setBaseIcms(BigDecimal baseIcms) {
        this.baseIcms = baseIcms;
    }

    public BigDecimal getValorMercadoria() {
        return valorMercadoria;
    }

    public void setValorMercadoria(BigDecimal valorMercadoria) {
        this.valorMercadoria = valorMercadoria;
    }

    public BigDecimal getValor() {
        return valor;
    }

    public void setValor(BigDecimal valor) {
        this.valor = valor;
    }

    public @NotNull NaturezaOperacao getNatureza() {
        return natureza;
    }

    public void setNatureza(@NotNull NaturezaOperacao natureza) {
        this.natureza = natureza;
    }

    public CondicaoPagamento getCondicaoPagamento() {
        return condicaoPagamento;
    }

    public void setCondicaoPagamento(CondicaoPagamento condicaoPagamento) {
        this.condicaoPagamento = condicaoPagamento;
    }

    public Banco getBanco() {
        return banco;
    }

    public void setBanco(Banco banco) {
        this.banco = banco;
    }

    public Transportadora getTransportadora() {
        return transportadora;
    }

    public void setTransportadora(Transportadora transportadora) {
        this.transportadora = transportadora;
    }

    public Vendedor getVendedor() {
        return vendedor;
    }

    public void setVendedor(Vendedor vendedor) {
        this.vendedor = vendedor;
    }

    public ClassTrib getClassTrib() {
        return classTrib;
    }

    public void setClassTrib(ClassTrib classTrib) {
        this.classTrib = classTrib;
    }

    public Mensagem getMensagem() {
        return mensagem;
    }

    public void setMensagem(Mensagem mensagem) {
        this.mensagem = mensagem;
    }

    public String getComplementoMensagem() {
        return complementoMensagem;
    }

    public void setComplementoMensagem(String complementoMensagem) {
        this.complementoMensagem = complementoMensagem;
    }

    public @NotNull Parceiro getParceiro() {
        return parceiro;
    }

    public void setParceiro(@NotNull Parceiro parceiro) {
        this.parceiro = parceiro;
    }

    public @NotNull String getSerie() {
        return serie;
    }

    public void setSerie(@NotNull String serie) {
        this.serie = serie == null ? null : serie.trim();
    }

    public @NotNull String getEspecie() {
        return especie;
    }

    public void setEspecie(@NotNull String especie) {
        this.especie = especie;
    }

    public LocalDate getDataSaida() {
        return dataSaida;
    }

    public void setDataSaida(LocalDate dataSaida) {
        this.dataSaida = dataSaida;
    }

    public LocalDate getDataEmissao() {
        return dataEmissao;
    }

    public void setDataEmissao(LocalDate dataEmissao) {
        this.dataEmissao = dataEmissao;
    }

    public @NotNull Integer getCodEmpresa() {
        return codEmpresa;
    }

    public void setCodEmpresa(@NotNull Integer codEmpresa) {
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
}
