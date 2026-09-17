package br.com.axialsoftware.axctg3.entity.fiscal;

import br.com.axialsoftware.axctg3.entity.cadastros.CondicaoPagamento;
import br.com.axialsoftware.axctg3.entity.cadastros.Mensagem;
import br.com.axialsoftware.axctg3.entity.cadastros.Parceiro;
import br.com.axialsoftware.axctg3.entity.cadastros.Transportadora;
import br.com.axialsoftware.axctg3.entity.cadastros.Vendedor;
import br.com.axialsoftware.axctg3.entity.enums.ModFrete;
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
 * Pedido de venda. Mesma estrutura de campos das abas "Informações gerais" e
 * "Complementos" de {@link NotaSaida}, sem os campos exclusivos de emissão de NFe
 * (chave, chaveTentativa, finNfe, chaveNotaOriginal) — o pedido é anterior à nota
 * fiscal, ainda não modelado o fluxo de gerar a {@link NotaSaida} a partir dele.
 */
@JmixEntity
@Table(name = "PEDIDO_VENDA", indexes = {
        @Index(name = "IDX_PEDIDO_VENDA_DATA_EMISSAO", columnList = "DATA_EMISSAO"),
        @Index(name = "IDX_PEDIDO_VENDA_PARCEIRO", columnList = "PARCEIRO_ID"),
        @Index(name = "IDX_PEDIDO_VENDA_NATUREZA", columnList = "NATUREZA_ID"),
        @Index(name = "IDX_PEDIDO_VENDA_CONDICAO_PAGAMENTO", columnList = "CONDICAO_PAGAMENTO_ID"),
        @Index(name = "IDX_PEDIDO_VENDA_BANCO", columnList = "BANCO_ID"),
        @Index(name = "IDX_PEDIDO_VENDA_TRANSPORTADORA", columnList = "TRANSPORTADORA_ID"),
        @Index(name = "IDX_PEDIDO_VENDA_VENDEDOR", columnList = "VENDEDOR_ID"),
        @Index(name = "IDX_PEDIDO_VENDA_CLASS_TRIB", columnList = "CLASS_TRIB_ID"),
        @Index(name = "IDX_PEDIDO_VENDA_MENSAGEM", columnList = "MENSAGEM_ID"),
        @Index(name = "IDX_PEDIDO_VENDA_UNQ", columnList = "NUMERO, COD_EMPRESA, ESPECIE, SERIE", unique = true)
})
@Entity
public class PedidoVenda {
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

    // sem valor padrão: o PedidoVendaEventListener numera via Sequence, mesmo padrão
    // de NotaSaida.numero
    @InstanceName
    @NumberFormat(pattern = "########0")
    @Column(name = "NUMERO", nullable = false)
    @NotNull
    private Integer numero;

    // sem valor padrão: o PedidoVendaEventListener preenche com a empresa corrente
    // quando está nulo
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

    @Column(name = "MOD_FRETE")
    private Integer modFrete;

    @JoinColumn(name = "VENDEDOR_ID")
    @ManyToOne(fetch = FetchType.LAZY)
    private Vendedor vendedor;

    @JoinColumn(name = "CLASS_TRIB_ID")
    @ManyToOne(fetch = FetchType.LAZY)
    private ClassTrib classTrib;

    @JoinColumn(name = "MENSAGEM_ID")
    @ManyToOne(fetch = FetchType.LAZY)
    private Mensagem mensagem;

    @Column(name = "COMPLEMENTO_MENSAGEM")
    @Lob
    private String complementoMensagem;

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

    @NumberFormat(pattern = "###,###,##0.00000", decimalSeparator = ",", groupingSeparator = ".")
    @Column(name = "PESO_LIQUIDO", precision = 19, scale = 5)
    private BigDecimal pesoLiquido = BigDecimal.ZERO;

    @NumberFormat(pattern = "###,###,##0.00000", decimalSeparator = ",", groupingSeparator = ".")
    @Column(name = "PESO_BRUTO", precision = 19, scale = 5)
    private BigDecimal pesoBruto = BigDecimal.ZERO;

    @Column(name = "CANCELADO")
    private Boolean cancelado = false;

    @OnDelete(DeletePolicy.CASCADE)
    @Composition
    @OrderBy("item")
    @OneToMany(mappedBy = "pedidoVenda")
    private List<ItemPedidoVenda> itens;

    public List<ItemPedidoVenda> getItens() {
        return itens;
    }

    public void setItens(List<ItemPedidoVenda> itens) {
        this.itens = itens;
    }

    public Boolean getCancelado() {
        return cancelado;
    }

    public void setCancelado(Boolean cancelado) {
        this.cancelado = cancelado;
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

    public String getComplementoMensagem() {
        return complementoMensagem;
    }

    public void setComplementoMensagem(String complementoMensagem) {
        this.complementoMensagem = complementoMensagem;
    }

    public Mensagem getMensagem() {
        return mensagem;
    }

    public void setMensagem(Mensagem mensagem) {
        this.mensagem = mensagem;
    }

    public ClassTrib getClassTrib() {
        return classTrib;
    }

    public void setClassTrib(ClassTrib classTrib) {
        this.classTrib = classTrib;
    }

    public Vendedor getVendedor() {
        return vendedor;
    }

    public void setVendedor(Vendedor vendedor) {
        this.vendedor = vendedor;
    }

    public ModFrete getModFrete() {
        return modFrete == null ? null : ModFrete.fromId(modFrete);
    }

    public void setModFrete(ModFrete modFrete) {
        this.modFrete = modFrete == null ? null : modFrete.getId();
    }

    public Transportadora getTransportadora() {
        return transportadora;
    }

    public void setTransportadora(Transportadora transportadora) {
        this.transportadora = transportadora;
    }

    public Banco getBanco() {
        return banco;
    }

    public void setBanco(Banco banco) {
        this.banco = banco;
    }

    public CondicaoPagamento getCondicaoPagamento() {
        return condicaoPagamento;
    }

    public void setCondicaoPagamento(CondicaoPagamento condicaoPagamento) {
        this.condicaoPagamento = condicaoPagamento;
    }

    public @NotNull NaturezaOperacao getNatureza() {
        return natureza;
    }

    public void setNatureza(@NotNull NaturezaOperacao natureza) {
        this.natureza = natureza;
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
