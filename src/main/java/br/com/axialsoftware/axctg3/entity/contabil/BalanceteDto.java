package br.com.axialsoftware.axctg3.entity.contabil;

import io.jmix.core.entity.annotation.JmixGeneratedValue;
import io.jmix.core.entity.annotation.JmixId;
import io.jmix.core.metamodel.annotation.InstanceName;
import io.jmix.core.metamodel.annotation.JmixEntity;
import jakarta.persistence.Entity;

import java.math.BigDecimal;
import java.util.UUID;

@JmixEntity
public class BalanceteDto {
    @JmixGeneratedValue
    @JmixId
    private UUID id;

    @InstanceName
    private String conta;

    private String nome;

    private Integer grau;

    private Boolean analitica;

    private BigDecimal sldAnt;

    private BigDecimal debMes;

    private BigDecimal credMes;

    private BigDecimal sldAtual;

    private Integer grauAnterior;

    private Boolean totalizar;

    private String nomeAnterior;

    private BigDecimal anterior;

    private BigDecimal debito;

    private BigDecimal credito;

    private BigDecimal atual;

    private Integer posicao;

    private String tipoSaldoAnterior;

    private String tipoSaldoAtual;

    private String saldoAnteriorComLetra;

    private String saldoAtualComLetra;

    private String anteriorComLetra;

    private String atualComLetra;

    private String anteriorTotalComLetra;

    private String atualTotalComLetra;

    public String getAtualTotalComLetra() {
        return atualTotalComLetra;
    }

    public void setAtualTotalComLetra(String atualTotalComLetra) {
        this.atualTotalComLetra = atualTotalComLetra;
    }

    public String getAnteriorTotalComLetra() {
        return anteriorTotalComLetra;
    }

    public void setAnteriorTotalComLetra(String anteriorTotalComLetra) {
        this.anteriorTotalComLetra = anteriorTotalComLetra;
    }

    public String getAtualComLetra() {
        return atualComLetra;
    }

    public void setAtualComLetra(String atualComLetra) {
        this.atualComLetra = atualComLetra;
    }

    public String getAnteriorComLetra() {
        return anteriorComLetra;
    }

    public void setAnteriorComLetra(String anteriorComLetra) {
        this.anteriorComLetra = anteriorComLetra;
    }

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

    public String getTipoSaldoAtual() {
        return tipoSaldoAtual;
    }

    public void setTipoSaldoAtual(String tipoSaldoAtual) {
        this.tipoSaldoAtual = tipoSaldoAtual;
    }

    public String getTipoSaldoAnterior() {
        return tipoSaldoAnterior;
    }

    public void setTipoSaldoAnterior(String tipoSaldoAnterior) {
        this.tipoSaldoAnterior = tipoSaldoAnterior;
    }

    public Integer getPosicao() {
        return posicao;
    }

    public void setPosicao(Integer posicao) {
        this.posicao = posicao;
    }

    public BigDecimal getAtual() {
        return atual;
    }

    public void setAtual(BigDecimal atual) {
        this.atual = atual;
    }

    public BigDecimal getCredito() {
        return credito;
    }

    public void setCredito(BigDecimal credito) {
        this.credito = credito;
    }

    public BigDecimal getDebito() {
        return debito;
    }

    public void setDebito(BigDecimal debito) {
        this.debito = debito;
    }

    public BigDecimal getAnterior() {
        return anterior;
    }

    public void setAnterior(BigDecimal anterior) {
        this.anterior = anterior;
    }

    public String getNomeAnterior() {
        return nomeAnterior;
    }

    public void setNomeAnterior(String nomeAnterior) {
        this.nomeAnterior = nomeAnterior;
    }

    public Boolean getTotalizar() {
        return totalizar;
    }

    public void setTotalizar(Boolean totalizar) {
        this.totalizar = totalizar;
    }

    public Integer getGrauAnterior() {
        return grauAnterior;
    }

    public void setGrauAnterior(Integer grauAnterior) {
        this.grauAnterior = grauAnterior;
    }

    public BigDecimal getSldAtual() {
        return sldAtual;
    }

    public void setSldAtual(BigDecimal sldAtual) {
        this.sldAtual = sldAtual;
    }

    public BigDecimal getCredMes() {
        return credMes;
    }

    public void setCredMes(BigDecimal credMes) {
        this.credMes = credMes;
    }

    public BigDecimal getDebMes() {
        return debMes;
    }

    public void setDebMes(BigDecimal debMes) {
        this.debMes = debMes;
    }

    public BigDecimal getSldAnt() {
        return sldAnt;
    }

    public void setSldAnt(BigDecimal sldAnt) {
        this.sldAnt = sldAnt;
    }

    public Boolean getAnalitica() {
        return analitica;
    }

    public void setAnalitica(Boolean analitica) {
        this.analitica = analitica;
    }

    public Integer getGrau() {
        return grau;
    }

    public void setGrau(Integer grau) {
        this.grau = grau;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getConta() {
        return conta;
    }

    public void setConta(String conta) {
        this.conta = conta;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

}