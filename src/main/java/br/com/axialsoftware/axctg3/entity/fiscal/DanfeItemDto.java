package br.com.axialsoftware.axctg3.entity.fiscal;

import io.jmix.core.entity.annotation.JmixGeneratedValue;
import io.jmix.core.entity.annotation.JmixId;
import io.jmix.core.metamodel.annotation.InstanceName;
import io.jmix.core.metamodel.annotation.JmixEntity;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Uma linha da tabela "Dados dos produtos/serviços" do DANFE (modelo 55) — DTO não
 * persistente {@code @JmixEntity}, mesmo molde de {@link br.com.axialsoftware.axctg3.entity.contabil.BalanceteDto},
 * usado só como bean do {@code JRBeanCollectionDataSource} passado pro {@code Danfe.jasper}
 * (ver {@link br.com.axialsoftware.axctg3.service.fiscal.NfeDanfeService}). Uma instância por
 * {@link NfeItem}; {@code cst} já vem combinado (CST do Regime Normal ou CSOSN do Simples
 * Nacional, o que estiver preenchido) pra não precisar de lógica condicional no template.
 */
@JmixEntity
public class DanfeItemDto {
    @JmixGeneratedValue
    @JmixId
    private UUID id;

    private Integer item;

    @InstanceName
    private String codProd;

    private String descProd;

    private String ncm;

    private String cst;

    private Integer cfop;

    private String unCom;

    private BigDecimal quantCom;

    private BigDecimal valorUnCom;

    private BigDecimal valorProd;

    private BigDecimal baseIcms;

    private BigDecimal valorIcms;

    private BigDecimal valorIpi;

    private BigDecimal aliqIcms;

    private BigDecimal aliqIpi;

    public Integer getItem() {
        return item;
    }

    public void setItem(Integer item) {
        this.item = item;
    }

    public String getCodProd() {
        return codProd;
    }

    public void setCodProd(String codProd) {
        this.codProd = codProd;
    }

    public String getDescProd() {
        return descProd;
    }

    public void setDescProd(String descProd) {
        this.descProd = descProd;
    }

    public String getNcm() {
        return ncm;
    }

    public void setNcm(String ncm) {
        this.ncm = ncm;
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

    public String getUnCom() {
        return unCom;
    }

    public void setUnCom(String unCom) {
        this.unCom = unCom;
    }

    public BigDecimal getQuantCom() {
        return quantCom;
    }

    public void setQuantCom(BigDecimal quantCom) {
        this.quantCom = quantCom;
    }

    public BigDecimal getValorUnCom() {
        return valorUnCom;
    }

    public void setValorUnCom(BigDecimal valorUnCom) {
        this.valorUnCom = valorUnCom;
    }

    public BigDecimal getValorProd() {
        return valorProd;
    }

    public void setValorProd(BigDecimal valorProd) {
        this.valorProd = valorProd;
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

    public BigDecimal getValorIpi() {
        return valorIpi;
    }

    public void setValorIpi(BigDecimal valorIpi) {
        this.valorIpi = valorIpi;
    }

    public BigDecimal getAliqIcms() {
        return aliqIcms;
    }

    public void setAliqIcms(BigDecimal aliqIcms) {
        this.aliqIcms = aliqIcms;
    }

    public BigDecimal getAliqIpi() {
        return aliqIpi;
    }

    public void setAliqIpi(BigDecimal aliqIpi) {
        this.aliqIpi = aliqIpi;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }
}
