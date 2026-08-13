package br.com.axialsoftware.axctg3.entity.contabil;

import io.jmix.core.entity.annotation.JmixGeneratedValue;
import io.jmix.core.entity.annotation.JmixId;
import io.jmix.core.metamodel.annotation.InstanceName;
import io.jmix.core.metamodel.annotation.JmixEntity;

import java.math.BigDecimal;
import java.sql.Date;
import java.util.UUID;

// DTO não-persistente usado como bean do Jasper pelos relatórios de depreciação
// (Depreciacao.jasper) em DepreciacaoService.calcularDepreciacoes/listarDepreciacoes.
@JmixEntity
public class BemDto {
    @JmixGeneratedValue
    @JmixId
    private UUID id;

    @InstanceName
    private Long codigo;

    private String descricao;

    private String codigoConta;

    private String nomeConta;

    private String resumida;

    private BigDecimal taxaDepr;

    private Date dataCompra;

    private BigDecimal valorCompra;

    private BigDecimal deprAnterior;

    private BigDecimal deprPeriodo;

    private BigDecimal deprAtual;

    public BigDecimal getDeprAtual() {
        return deprAtual;
    }

    public void setDeprAtual(BigDecimal deprAtual) {
        this.deprAtual = deprAtual;
    }

    public BigDecimal getDeprPeriodo() {
        return deprPeriodo;
    }

    public void setDeprPeriodo(BigDecimal deprPeriodo) {
        this.deprPeriodo = deprPeriodo;
    }

    public BigDecimal getDeprAnterior() {
        return deprAnterior;
    }

    public void setDeprAnterior(BigDecimal deprAnterior) {
        this.deprAnterior = deprAnterior;
    }

    public BigDecimal getValorCompra() {
        return valorCompra;
    }

    public void setValorCompra(BigDecimal valorCompra) {
        this.valorCompra = valorCompra;
    }

    public Date getDataCompra() {
        return dataCompra;
    }

    public void setDataCompra(Date dataCompra) {
        this.dataCompra = dataCompra;
    }

    public BigDecimal getTaxaDepr() {
        return taxaDepr;
    }

    public void setTaxaDepr(BigDecimal taxaDepr) {
        this.taxaDepr = taxaDepr;
    }

    public String getResumida() {
        return resumida;
    }

    public void setResumida(String resumida) {
        this.resumida = resumida;
    }

    public String getNomeConta() {
        return nomeConta;
    }

    public void setNomeConta(String nomeConta) {
        this.nomeConta = nomeConta;
    }

    public String getCodigoConta() {
        return codigoConta;
    }

    public void setCodigoConta(String codigoConta) {
        this.codigoConta = codigoConta;
    }

    public String getDescricao() {
        return descricao;
    }

    public void setDescricao(String descricao) {
        this.descricao = descricao;
    }

    public Long getCodigo() {
        return codigo;
    }

    public void setCodigo(Long codigo) {
        this.codigo = codigo;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }
}
