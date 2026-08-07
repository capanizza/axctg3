package br.com.axialsoftware.axctg3.entity.financeiro;

import io.jmix.core.entity.annotation.JmixGeneratedValue;
import io.jmix.core.entity.annotation.JmixId;
import io.jmix.core.metamodel.annotation.InstanceName;
import io.jmix.core.metamodel.annotation.JmixEntity;

import java.math.BigDecimal;
import java.sql.Date;
import java.util.UUID;

/**
 * Linha dos relatórios "Movimento banco" (MovimentoBanco.jasper) e "Movimento
 * financeiro" (MovimentoFinanceiro.jasper). Ver {@code MovimentoBancoService}.
 */
@JmixEntity
public class MovimentoBancoDto {

    @InstanceName
    @JmixGeneratedValue
    @JmixId
    private UUID id;

    private Integer lancamento;

    private Date data;

    private String nomeBanco;

    private String nomeHistoricoFinanceiro;

    private BigDecimal valor;

    private BigDecimal valorEntrada;

    private BigDecimal valorSaida;

    private String documento;

    private String descricao;

    private String nomeContaContabil;

    private Boolean entrada;

    private Boolean contabilizado;

    private BigDecimal saldo;

    private Integer tipo;

    public Integer getTipo() {
        return tipo;
    }

    public void setTipo(Integer tipo) {
        this.tipo = tipo;
    }

    public BigDecimal getSaldo() {
        return saldo;
    }

    public void setSaldo(BigDecimal saldo) {
        this.saldo = saldo;
    }

    public BigDecimal getValorEntrada() {
        return valorEntrada;
    }

    public void setValorEntrada(BigDecimal valorEntrada) {
        this.valorEntrada = valorEntrada;
    }

    public BigDecimal getValorSaida() {
        return valorSaida;
    }

    public void setValorSaida(BigDecimal valorSaida) {
        this.valorSaida = valorSaida;
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

    public String getNomeContaContabil() {
        return nomeContaContabil;
    }

    public void setNomeContaContabil(String nomeContaContabil) {
        this.nomeContaContabil = nomeContaContabil;
    }

    public Boolean getEntrada() {
        return entrada;
    }

    public void setEntrada(Boolean entrada) {
        this.entrada = entrada;
    }

    public Boolean getContabilizado() {
        return contabilizado;
    }

    public void setContabilizado(Boolean contabilizado) {
        this.contabilizado = contabilizado;
    }

    public BigDecimal getValor() {
        return valor;
    }

    public void setValor(BigDecimal valor) {
        this.valor = valor;
    }

    public String getNomeHistoricoFinanceiro() {
        return nomeHistoricoFinanceiro;
    }

    public void setNomeHistoricoFinanceiro(String nomeHistoricoFinanceiro) {
        this.nomeHistoricoFinanceiro = nomeHistoricoFinanceiro;
    }

    public String getNomeBanco() {
        return nomeBanco;
    }

    public void setNomeBanco(String nomeBanco) {
        this.nomeBanco = nomeBanco;
    }

    public Date getData() {
        return data;
    }

    public void setData(Date data) {
        this.data = data;
    }

    public Integer getLancamento() {
        return lancamento;
    }

    public void setLancamento(Integer lancamento) {
        this.lancamento = lancamento;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

}
