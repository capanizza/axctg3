package br.com.axialsoftware.axctg3.entity.contabil;

import io.jmix.core.entity.annotation.JmixGeneratedValue;
import io.jmix.core.entity.annotation.JmixId;
import io.jmix.core.metamodel.annotation.InstanceName;
import io.jmix.core.metamodel.annotation.JmixEntity;

import java.math.BigDecimal;
import java.sql.Date;
import java.util.UUID;

@JmixEntity
public class RazaoDto {
    @JmixGeneratedValue
    @JmixId
    private UUID id;

    @InstanceName
    public Date dataLancamento;

    public String codConta;

    public String nmConta;

    public String codContraPartida;

    public String nmContraPartida;

    public Integer numero;

    public String historico;

    public BigDecimal saldoAnterior;

    public BigDecimal valorDebito;

    public BigDecimal valorCredito;

    public BigDecimal saldoAtual;

    public String saldoAnteriorComLetra;

    public String saldoAtualComLetra;

    public String getSaldoAtualComLetra() {
        return saldoAtualComLetra;
    }

    public void setSaldoAtualComLetra(String saldoAtualComLetra) {
        this.saldoAtualComLetra = saldoAtualComLetra;
    }

    public String getSaldoAnteriorComLetra() {
        return saldoAnteriorComLetra;
    }

    public void setSaldoAnteriorComLetra(String saldoAnteriorComLetra) {
        this.saldoAnteriorComLetra = saldoAnteriorComLetra;
    }

    public BigDecimal getSaldoAtual() {
        return saldoAtual;
    }

    public void setSaldoAtual(BigDecimal saldoAtual) {
        this.saldoAtual = saldoAtual;
    }

    public BigDecimal getValorCredito() {
        return valorCredito;
    }

    public void setValorCredito(BigDecimal valorCredito) {
        this.valorCredito = valorCredito;
    }

    public BigDecimal getValorDebito() {
        return valorDebito;
    }

    public void setValorDebito(BigDecimal valorDebito) {
        this.valorDebito = valorDebito;
    }

    public BigDecimal getSaldoAnterior() {
        return saldoAnterior;
    }

    public String getHistorico() {
        return historico;
    }

    public void setHistorico(String historico) {
        this.historico = historico;
    }

    public Integer getNumero() {
        return numero;
    }

    public void setNumero(Integer numero) {
        this.numero = numero;
    }

    public void setSaldoAnterior(BigDecimal saldoAnterior) {
        this.saldoAnterior = saldoAnterior;
    }

    public String getNmContraPartida() {
        return nmContraPartida;
    }

    public void setNmContraPartida(String nmContraPartida) {
        this.nmContraPartida = nmContraPartida;
    }

    public String getCodContraPartida() {
        return codContraPartida;
    }

    public void setCodContraPartida(String codContraPartida) {
        this.codContraPartida = codContraPartida;
    }

    public String getNmConta() {
        return nmConta;
    }

    public void setNmConta(String nmConta) {
        this.nmConta = nmConta;
    }

    public String getCodConta() {
        return codConta;
    }

    public void setCodConta(String codConta) {
        this.codConta = codConta;
    }

    public Date getDataLancamento() {
        return dataLancamento;
    }

    public void setDataLancamento(Date dataLancamento) {
        this.dataLancamento = dataLancamento;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

}