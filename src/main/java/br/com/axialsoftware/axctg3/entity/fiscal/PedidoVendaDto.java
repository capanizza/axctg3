package br.com.axialsoftware.axctg3.entity.fiscal;

import io.jmix.core.entity.annotation.JmixGeneratedValue;
import io.jmix.core.entity.annotation.JmixId;
import io.jmix.core.metamodel.annotation.InstanceName;
import io.jmix.core.metamodel.annotation.JmixEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Uma linha da listagem de pedidos de venda (cabeçalho do {@link PedidoVenda} repetido
 * por {@link ItemPedidoVenda}) — DTO não persistente {@code @JmixEntity}, mesmo molde de
 * {@link br.com.axialsoftware.axctg3.entity.contabil.ContaContabilDto}, usado só como
 * bean do {@code JRBeanCollectionDataSource} passado pro relatório. Referências
 * (cliente/naturezaOperacao/classificacaoTributaria/condicaoPagamento/banco/vendedor/
 * mensagem/produto) já vêm resolvidas pra instance name — o template não precisa navegar
 * entidade nenhuma.
 */
@JmixEntity
public class PedidoVendaDto {
    @JmixGeneratedValue
    @JmixId
    private UUID id;

    @InstanceName
    private Integer numero;

    private LocalDate dataEntrada;

    private String cliente;

    private String naturezaOperacao;

    private String classificacaoTributaria;

    private String condicaoPagamento;

    private String banco;

    private String vendedor;

    private String mensagem;

    private String complementoMensagem;

    private String modFrete;

    private BigDecimal frete;

    private BigDecimal seguro;

    private BigDecimal desconto;

    private BigDecimal despesas;

    private BigDecimal pesoLiquido;

    private BigDecimal pesoBruto;

    private Integer item;

    private String produto;

    private Integer cfop;

    private String cst;

    private Integer codClassTrib;

    private String descricaoComplementar;

    private BigDecimal quantidade;

    private BigDecimal valorUnitario;

    private BigDecimal subTotal;

    public BigDecimal getSubTotal() {
        return subTotal;
    }

    public void setSubTotal(BigDecimal subTotal) {
        this.subTotal = subTotal;
    }

    public BigDecimal getValorUnitario() {
        return valorUnitario;
    }

    public void setValorUnitario(BigDecimal valorUnitario) {
        this.valorUnitario = valorUnitario;
    }

    public BigDecimal getQuantidade() {
        return quantidade;
    }

    public void setQuantidade(BigDecimal quantidade) {
        this.quantidade = quantidade;
    }

    public String getDescricaoComplementar() {
        return descricaoComplementar;
    }

    public void setDescricaoComplementar(String descricaoComplementar) {
        this.descricaoComplementar = descricaoComplementar;
    }

    public Integer getCodClassTrib() {
        return codClassTrib;
    }

    public void setCodClassTrib(Integer codClassTrib) {
        this.codClassTrib = codClassTrib;
    }

    public String getCst() {
        return cst;
    }

    public void setCst(String cst) {
        this.cst = cst;
    }

    public Integer getCfop() {
        return cfop;
    }

    public void setCfop(Integer cfop) {
        this.cfop = cfop;
    }

    public String getProduto() {
        return produto;
    }

    public void setProduto(String produto) {
        this.produto = produto;
    }

    public Integer getItem() {
        return item;
    }

    public void setItem(Integer item) {
        this.item = item;
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

    public String getModFrete() {
        return modFrete;
    }

    public void setModFrete(String modFrete) {
        this.modFrete = modFrete;
    }

    public String getComplementoMensagem() {
        return complementoMensagem;
    }

    public void setComplementoMensagem(String complementoMensagem) {
        this.complementoMensagem = complementoMensagem;
    }

    public String getMensagem() {
        return mensagem;
    }

    public void setMensagem(String mensagem) {
        this.mensagem = mensagem;
    }

    public String getVendedor() {
        return vendedor;
    }

    public void setVendedor(String vendedor) {
        this.vendedor = vendedor;
    }

    public String getBanco() {
        return banco;
    }

    public void setBanco(String banco) {
        this.banco = banco;
    }

    public String getCondicaoPagamento() {
        return condicaoPagamento;
    }

    public void setCondicaoPagamento(String condicaoPagamento) {
        this.condicaoPagamento = condicaoPagamento;
    }

    public String getClassificacaoTributaria() {
        return classificacaoTributaria;
    }

    public void setClassificacaoTributaria(String classificacaoTributaria) {
        this.classificacaoTributaria = classificacaoTributaria;
    }

    public String getNaturezaOperacao() {
        return naturezaOperacao;
    }

    public void setNaturezaOperacao(String naturezaOperacao) {
        this.naturezaOperacao = naturezaOperacao;
    }

    public String getCliente() {
        return cliente;
    }

    public void setCliente(String cliente) {
        this.cliente = cliente;
    }

    public LocalDate getDataEntrada() {
        return dataEntrada;
    }

    public void setDataEntrada(LocalDate dataEntrada) {
        this.dataEntrada = dataEntrada;
    }

    public Integer getNumero() {
        return numero;
    }

    public void setNumero(Integer numero) {
        this.numero = numero;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }
}
