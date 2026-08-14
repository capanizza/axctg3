package br.com.axialsoftware.axctg3.entity.contabil;

import io.jmix.core.entity.annotation.JmixGeneratedValue;
import io.jmix.core.entity.annotation.JmixId;
import io.jmix.core.metamodel.annotation.InstanceName;
import io.jmix.core.metamodel.annotation.JmixEntity;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Bean de relatório para {@code ContaContabilService.verificarContas()} — não é entidade
 * persistente, só carrega as divergências encontradas para o Jasper
 * {@code VerificacaoContas.jasper}. Ver docs/MIGRACAO.md.
 */
@JmixEntity
public class VerificacaoDto {
    @JmixGeneratedValue
    @JmixId
    private UUID id;

    private Integer tipo;

    private String texto;

    private String codigo = "";

    @InstanceName
    private String nome = "";

    private Integer mes;

    private BigDecimal saldoDevedor = BigDecimal.ZERO;

    private BigDecimal movimentoDevedor = BigDecimal.ZERO;

    private BigDecimal saldoCredor = BigDecimal.ZERO;

    private BigDecimal movimentoCredor = BigDecimal.ZERO;

    public BigDecimal getMovimentoCredor() {
        return movimentoCredor;
    }

    public void setMovimentoCredor(BigDecimal movimentoCredor) {
        this.movimentoCredor = movimentoCredor;
    }

    public BigDecimal getSaldoCredor() {
        return saldoCredor;
    }

    public void setSaldoCredor(BigDecimal saldoCredor) {
        this.saldoCredor = saldoCredor;
    }

    public BigDecimal getMovimentoDevedor() {
        return movimentoDevedor;
    }

    public void setMovimentoDevedor(BigDecimal movimentoDevedor) {
        this.movimentoDevedor = movimentoDevedor;
    }

    public BigDecimal getSaldoDevedor() {
        return saldoDevedor;
    }

    public void setSaldoDevedor(BigDecimal saldoDevedor) {
        this.saldoDevedor = saldoDevedor;
    }

    public Integer getMes() {
        return mes;
    }

    public void setMes(Integer mes) {
        this.mes = mes;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getCodigo() {
        return codigo;
    }

    public void setCodigo(String codigo) {
        this.codigo = codigo;
    }

    public String getTexto() {
        return texto;
    }

    public void setTexto(String texto) {
        this.texto = texto;
    }

    public Integer getTipo() {
        return tipo;
    }

    public void setTipo(Integer tipo) {
        this.tipo = tipo;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

}
