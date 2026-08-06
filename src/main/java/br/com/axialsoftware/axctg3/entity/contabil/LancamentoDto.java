package br.com.axialsoftware.axctg3.entity.contabil;

import io.jmix.core.entity.annotation.JmixGeneratedValue;
import io.jmix.core.entity.annotation.JmixId;
import io.jmix.core.metamodel.annotation.InstanceName;
import io.jmix.core.metamodel.annotation.JmixEntity;

import java.math.BigDecimal;
import java.sql.Date;
import java.util.UUID;

@JmixEntity
public class LancamentoDto {
    @JmixGeneratedValue
    @JmixId
    private UUID id;

    @InstanceName
    private Integer numero;

    private Date dataLancamento;

    private String codCtDev;

    private String dscCtDev;

    private String codCtCre;

    private String dscCtCre;

    private Integer codHistCont;

    private String dscHistCont;

    private String complHist;

    private BigDecimal valor;

    private String origem;

    public Integer getNumero() {
        return numero;
    }

    public void setNumero(Integer numero) {
        this.numero = numero;
    }

    public Date getDataLancamento() {
        return dataLancamento;
    }

    public void setDataLancamento(Date dataLancamento) {
        this.dataLancamento = dataLancamento;
    }

    public String getCodCtDev() {
        return codCtDev;
    }

    public void setCodCtDev(String codCtDev) {
        this.codCtDev = codCtDev;
    }

    public String getDscCtDev() {
        return dscCtDev;
    }

    public void setDscCtDev(String dscCtDev) {
        this.dscCtDev = dscCtDev;
    }

    public String getCodCtCre() {
        return codCtCre;
    }

    public void setCodCtCre(String codCtCre) {
        this.codCtCre = codCtCre;
    }

    public String getDscCtCre() {
        return dscCtCre;
    }

    public void setDscCtCre(String dscCtCre) {
        this.dscCtCre = dscCtCre;
    }

    public Integer getCodHistCont() {
        return codHistCont;
    }

    public void setCodHistCont(Integer codHistCont) {
        this.codHistCont = codHistCont;
    }

    public String getDscHistCont() {
        return dscHistCont;
    }

    public void setDscHistCont(String dscHistCont) {
        this.dscHistCont = dscHistCont;
    }

    public String getComplHist() {
        return complHist;
    }

    public void setComplHist(String complHist) {
        this.complHist = complHist;
    }

    public BigDecimal getValor() {
        return valor;
    }

    public void setValor(BigDecimal valor) {
        this.valor = valor;
    }

    public String getOrigem() {
        return origem;
    }

    public void setOrigem(String origem) {
        this.origem = origem;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }
}