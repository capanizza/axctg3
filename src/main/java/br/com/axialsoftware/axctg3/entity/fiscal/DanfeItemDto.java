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
 *
 * <p>As colunas "BASES DE CÁLCULO"/"ALÍQUOTAS"/"VALOR DOS TRIBUTOS" do leiaute da Reforma
 * Tributária (NT 2026.010) chegam já formatadas, uma linha por tributo separada por
 * {@code \n} ({@code basesTexto}, {@code aliqEsqTexto}/{@code aliqDirTexto},
 * {@code valoresEsqTexto}/{@code valoresDirTexto}) — o {@code pattern} do JasperReports não
 * serve pra texto de várias linhas, e a linha de um tributo ausente no XML precisa sair em
 * branco (não 0,00), decisão que fica em {@code NfeDanfeService}. Os rótulos de cada linha
 * são fixos no {@code Danfe.jrxml}.
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

    private String codClassTrib;

    // Quantidade formatada + "\n" + unidade — coluna "QTD / UN" do Danfe.jrxml.
    private String quantUnTexto;

    // ICMS / IBS-CBS / IS / IPI, uma linha cada.
    private String basesTexto;

    // ICMS / IBS UF / IBS Município.
    private String aliqEsqTexto;

    // CBS / IPI / IS.
    private String aliqDirTexto;

    // ICMS / IBS UF / IBS Município.
    private String valoresEsqTexto;

    // CBS / IPI / IS.
    private String valoresDirTexto;

    public String getCodClassTrib() {
        return codClassTrib;
    }

    public void setCodClassTrib(String codClassTrib) {
        this.codClassTrib = codClassTrib;
    }

    public String getQuantUnTexto() {
        return quantUnTexto;
    }

    public void setQuantUnTexto(String quantUnTexto) {
        this.quantUnTexto = quantUnTexto;
    }

    public String getBasesTexto() {
        return basesTexto;
    }

    public void setBasesTexto(String basesTexto) {
        this.basesTexto = basesTexto;
    }

    public String getAliqEsqTexto() {
        return aliqEsqTexto;
    }

    public void setAliqEsqTexto(String aliqEsqTexto) {
        this.aliqEsqTexto = aliqEsqTexto;
    }

    public String getAliqDirTexto() {
        return aliqDirTexto;
    }

    public void setAliqDirTexto(String aliqDirTexto) {
        this.aliqDirTexto = aliqDirTexto;
    }

    public String getValoresEsqTexto() {
        return valoresEsqTexto;
    }

    public void setValoresEsqTexto(String valoresEsqTexto) {
        this.valoresEsqTexto = valoresEsqTexto;
    }

    public String getValoresDirTexto() {
        return valoresDirTexto;
    }

    public void setValoresDirTexto(String valoresDirTexto) {
        this.valoresDirTexto = valoresDirTexto;
    }

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
