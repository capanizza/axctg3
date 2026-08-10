package br.com.axialsoftware.axctg3.entity.fiscal;

import io.jmix.core.annotation.DeletedBy;
import io.jmix.core.annotation.DeletedDate;
import io.jmix.core.entity.annotation.JmixGeneratedValue;
import io.jmix.core.metamodel.annotation.*;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Item ({@code det}) de uma {@link Nfe} — snapshot de produto/imposto tal como
 * transmitido, sem FK pra {@link Produto} (mesma lógica de {@code emit}/{@code dest} em
 * {@link Nfe}: é o que foi efetivamente declarado na nota, não uma referência viva ao
 * cadastro). Nomes de campo divergem das tags SEFAZ de 1-2 letras (ex.: {@code vBC} vira
 * {@code baseIcms}) porque esses nomes curtos quebram a introspecção de Java Beans do
 * Jmix — ver o comentário equivalente em {@link Nfe}. As tags originais ficam nos
 * comentários de cada campo pra rastreabilidade.
 *
 * <p>Simplificações conscientes: sem o grupo ICMS-ST-retido-em-operação-anterior
 * ({@code vBCSTRet}/{@code pST}/{@code vICMSSTRet} — revenda de mercadoria já tributada
 * por ST) e sem o detalhamento item a item de ICMSUFDest/DIFAL (fica só o agregado em
 * {@link Nfe}) — ambos casos de uso mais estreitos que uma revenda/indústria genérica.
 */
@JmixEntity
@Table(name = "NFE_ITEM", indexes = {
        @Index(name = "IDX_NFE_ITEM_NFE", columnList = "NFE_ID")
})
@Entity
public class NfeItem {
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

    @JoinColumn(name = "NFE_ID", nullable = false)
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Nfe nfe;

    // nItem
    @InstanceName
    @Column(name = "ITEM")
    private Integer item;

    // ---- prod ----

    // cProd
    @Column(name = "COD_PROD", length = 60)
    private String codProd;

    // cEAN
    @Column(name = "COD_EAN", length = 14)
    private String codEan;

    // xProd
    @Column(name = "DESC_PROD", length = 120)
    private String descProd;

    @Column(name = "NCM", length = 8)
    private String ncm;

    @Column(name = "CEST", length = 7)
    private String cest;

    @NumberFormat(pattern = "0000")
    @Column(name = "CFOP")
    private Integer cfop;

    // uCom
    @Column(name = "UN_COM", length = 6)
    private String unCom;

    // qCom
    @NumberFormat(pattern = "###,###,##0.0000", decimalSeparator = ",", groupingSeparator = ".")
    @Column(name = "QUANT_COM", precision = 19, scale = 4)
    private BigDecimal quantCom = BigDecimal.ZERO;

    // vUnCom
    @NumberFormat(pattern = "###,###,##0.000000", decimalSeparator = ",", groupingSeparator = ".")
    @Column(name = "VALOR_UN_COM", precision = 19, scale = 6)
    private BigDecimal valorUnCom = BigDecimal.ZERO;

    // vProd
    @NumberFormat(pattern = "###,###,##0.00", decimalSeparator = ",", groupingSeparator = ".")
    @Column(name = "VALOR_PROD", precision = 19, scale = 2)
    private BigDecimal valorProd = BigDecimal.ZERO;

    // cEANTrib
    @Column(name = "COD_EAN_TRIB", length = 14)
    private String codEanTrib;

    // uTrib
    @Column(name = "UN_TRIB", length = 6)
    private String unTrib;

    // qTrib
    @NumberFormat(pattern = "###,###,##0.0000", decimalSeparator = ",", groupingSeparator = ".")
    @Column(name = "QUANT_TRIB", precision = 19, scale = 4)
    private BigDecimal quantTrib = BigDecimal.ZERO;

    // vUnTrib
    @NumberFormat(pattern = "###,###,##0.000000", decimalSeparator = ",", groupingSeparator = ".")
    @Column(name = "VALOR_UN_TRIB", precision = 19, scale = 6)
    private BigDecimal valorUnTrib = BigDecimal.ZERO;

    // vFrete
    @NumberFormat(pattern = "###,###,##0.00", decimalSeparator = ",", groupingSeparator = ".")
    @Column(name = "VALOR_FRETE", precision = 19, scale = 2)
    private BigDecimal valorFrete = BigDecimal.ZERO;

    // vSeg
    @NumberFormat(pattern = "###,###,##0.00", decimalSeparator = ",", groupingSeparator = ".")
    @Column(name = "VALOR_SEG", precision = 19, scale = 2)
    private BigDecimal valorSeg = BigDecimal.ZERO;

    // vDesc
    @NumberFormat(pattern = "###,###,##0.00", decimalSeparator = ",", groupingSeparator = ".")
    @Column(name = "VALOR_DESC", precision = 19, scale = 2)
    private BigDecimal valorDesc = BigDecimal.ZERO;

    // vOutro
    @NumberFormat(pattern = "###,###,##0.00", decimalSeparator = ",", groupingSeparator = ".")
    @Column(name = "VALOR_OUTRO", precision = 19, scale = 2)
    private BigDecimal valorOutro = BigDecimal.ZERO;

    // indTot: item soma (1) ou não (0) o total da nota
    @Column(name = "IND_TOT")
    private Integer indTot;

    // ---- imposto / ICMS ----

    // orig: 0=nacional, 1..8=estrangeira/nacional com conteúdo importado
    @Column(name = "ORIGEM_ICMS")
    private Integer origemIcms;

    // CST (Regime Normal) — mutuamente exclusivo com csosnIcms (Simples Nacional)
    @Column(name = "CST_ICMS", length = 3)
    private String cstIcms;

    // CSOSN (Simples Nacional)
    @Column(name = "CSOSN_ICMS", length = 3)
    private String csosnIcms;

    // modBC
    @Column(name = "MOD_BC_ICMS")
    private Integer modBcIcms;

    // vBC
    @NumberFormat(pattern = "###,###,##0.00", decimalSeparator = ",", groupingSeparator = ".")
    @Column(name = "BASE_ICMS", precision = 19, scale = 2)
    private BigDecimal baseIcms = BigDecimal.ZERO;

    // pRedBC
    @NumberFormat(pattern = "##0.0000")
    @Column(name = "PERC_REDUCAO_BC_ICMS", precision = 9, scale = 4)
    private BigDecimal percReducaoBcIcms = BigDecimal.ZERO;

    // pICMS
    @NumberFormat(pattern = "##0.0000")
    @Column(name = "ALIQ_ICMS", precision = 9, scale = 4)
    private BigDecimal aliqIcms = BigDecimal.ZERO;

    // vICMS
    @NumberFormat(pattern = "###,###,##0.00", decimalSeparator = ",", groupingSeparator = ".")
    @Column(name = "VALOR_ICMS", precision = 19, scale = 2)
    private BigDecimal valorIcms = BigDecimal.ZERO;

    // modBCST
    @Column(name = "MOD_BC_ICMS_ST")
    private Integer modBcIcmsSt;

    // pMVAST
    @NumberFormat(pattern = "##0.0000")
    @Column(name = "PERC_MVA_ICMS_ST", precision = 9, scale = 4)
    private BigDecimal percMvaIcmsSt = BigDecimal.ZERO;

    // pRedBCST
    @NumberFormat(pattern = "##0.0000")
    @Column(name = "PERC_REDUCAO_BC_ICMS_ST", precision = 9, scale = 4)
    private BigDecimal percReducaoBcIcmsSt = BigDecimal.ZERO;

    // vBCST
    @NumberFormat(pattern = "###,###,##0.00", decimalSeparator = ",", groupingSeparator = ".")
    @Column(name = "BASE_ICMS_ST", precision = 19, scale = 2)
    private BigDecimal baseIcmsSt = BigDecimal.ZERO;

    // pICMSST
    @NumberFormat(pattern = "##0.0000")
    @Column(name = "ALIQ_ICMS_ST", precision = 9, scale = 4)
    private BigDecimal aliqIcmsSt = BigDecimal.ZERO;

    // vICMSST
    @NumberFormat(pattern = "###,###,##0.00", decimalSeparator = ",", groupingSeparator = ".")
    @Column(name = "VALOR_ICMS_ST", precision = 19, scale = 2)
    private BigDecimal valorIcmsSt = BigDecimal.ZERO;

    // pFCP
    @NumberFormat(pattern = "##0.0000")
    @Column(name = "ALIQ_FCP", precision = 9, scale = 4)
    private BigDecimal aliqFcp = BigDecimal.ZERO;

    // vFCP
    @NumberFormat(pattern = "###,###,##0.00", decimalSeparator = ",", groupingSeparator = ".")
    @Column(name = "VALOR_FCP", precision = 19, scale = 2)
    private BigDecimal valorFcp = BigDecimal.ZERO;

    // ---- imposto / IPI ----

    // cEnq
    @Column(name = "COD_ENQ_IPI", length = 3)
    private String codEnqIpi;

    // CST IPI
    @Column(name = "CST_IPI", length = 2)
    private String cstIpi;

    // vBC
    @NumberFormat(pattern = "###,###,##0.00", decimalSeparator = ",", groupingSeparator = ".")
    @Column(name = "BASE_IPI", precision = 19, scale = 2)
    private BigDecimal baseIpi = BigDecimal.ZERO;

    // pIPI
    @NumberFormat(pattern = "##0.0000")
    @Column(name = "ALIQ_IPI", precision = 9, scale = 4)
    private BigDecimal aliqIpi = BigDecimal.ZERO;

    // vIPI
    @NumberFormat(pattern = "###,###,##0.00", decimalSeparator = ",", groupingSeparator = ".")
    @Column(name = "VALOR_IPI", precision = 19, scale = 2)
    private BigDecimal valorIpi = BigDecimal.ZERO;

    // ---- imposto / PIS ----

    @Column(name = "CST_PIS", length = 2)
    private String cstPis;

    @NumberFormat(pattern = "###,###,##0.00", decimalSeparator = ",", groupingSeparator = ".")
    @Column(name = "BASE_PIS", precision = 19, scale = 2)
    private BigDecimal basePis = BigDecimal.ZERO;

    @NumberFormat(pattern = "##0.0000")
    @Column(name = "ALIQ_PIS", precision = 9, scale = 4)
    private BigDecimal aliqPis = BigDecimal.ZERO;

    @NumberFormat(pattern = "###,###,##0.00", decimalSeparator = ",", groupingSeparator = ".")
    @Column(name = "VALOR_PIS", precision = 19, scale = 2)
    private BigDecimal valorPis = BigDecimal.ZERO;

    // ---- imposto / COFINS ----

    @Column(name = "CST_COFINS", length = 2)
    private String cstCofins;

    @NumberFormat(pattern = "###,###,##0.00", decimalSeparator = ",", groupingSeparator = ".")
    @Column(name = "BASE_COFINS", precision = 19, scale = 2)
    private BigDecimal baseCofins = BigDecimal.ZERO;

    @NumberFormat(pattern = "##0.0000")
    @Column(name = "ALIQ_COFINS", precision = 9, scale = 4)
    private BigDecimal aliqCofins = BigDecimal.ZERO;

    @NumberFormat(pattern = "###,###,##0.00", decimalSeparator = ",", groupingSeparator = ".")
    @Column(name = "VALOR_COFINS", precision = 19, scale = 2)
    private BigDecimal valorCofins = BigDecimal.ZERO;

    // vTotTrib (aproximado, informativo — Lei da Transparência Fiscal)
    @NumberFormat(pattern = "###,###,##0.00", decimalSeparator = ",", groupingSeparator = ".")
    @Column(name = "VALOR_TOT_TRIBUTOS", precision = 19, scale = 2)
    private BigDecimal valorTotTributos = BigDecimal.ZERO;

    // infAdProd
    @Column(name = "INFO_ADICIONAL_PRODUTO")
    @Lob
    private String infoAdicionalProduto;

    public String getInfoAdicionalProduto() {
        return infoAdicionalProduto;
    }

    public void setInfoAdicionalProduto(String infoAdicionalProduto) {
        this.infoAdicionalProduto = infoAdicionalProduto;
    }

    public BigDecimal getValorTotTributos() {
        return valorTotTributos;
    }

    public void setValorTotTributos(BigDecimal valorTotTributos) {
        this.valorTotTributos = valorTotTributos;
    }

    public BigDecimal getValorCofins() {
        return valorCofins;
    }

    public void setValorCofins(BigDecimal valorCofins) {
        this.valorCofins = valorCofins;
    }

    public BigDecimal getAliqCofins() {
        return aliqCofins;
    }

    public void setAliqCofins(BigDecimal aliqCofins) {
        this.aliqCofins = aliqCofins;
    }

    public BigDecimal getBaseCofins() {
        return baseCofins;
    }

    public void setBaseCofins(BigDecimal baseCofins) {
        this.baseCofins = baseCofins;
    }

    public String getCstCofins() {
        return cstCofins;
    }

    public void setCstCofins(String cstCofins) {
        this.cstCofins = cstCofins;
    }

    public BigDecimal getValorPis() {
        return valorPis;
    }

    public void setValorPis(BigDecimal valorPis) {
        this.valorPis = valorPis;
    }

    public BigDecimal getAliqPis() {
        return aliqPis;
    }

    public void setAliqPis(BigDecimal aliqPis) {
        this.aliqPis = aliqPis;
    }

    public BigDecimal getBasePis() {
        return basePis;
    }

    public void setBasePis(BigDecimal basePis) {
        this.basePis = basePis;
    }

    public String getCstPis() {
        return cstPis;
    }

    public void setCstPis(String cstPis) {
        this.cstPis = cstPis;
    }

    public BigDecimal getValorIpi() {
        return valorIpi;
    }

    public void setValorIpi(BigDecimal valorIpi) {
        this.valorIpi = valorIpi;
    }

    public BigDecimal getAliqIpi() {
        return aliqIpi;
    }

    public void setAliqIpi(BigDecimal aliqIpi) {
        this.aliqIpi = aliqIpi;
    }

    public BigDecimal getBaseIpi() {
        return baseIpi;
    }

    public void setBaseIpi(BigDecimal baseIpi) {
        this.baseIpi = baseIpi;
    }

    public String getCstIpi() {
        return cstIpi;
    }

    public void setCstIpi(String cstIpi) {
        this.cstIpi = cstIpi;
    }

    public String getCodEnqIpi() {
        return codEnqIpi;
    }

    public void setCodEnqIpi(String codEnqIpi) {
        this.codEnqIpi = codEnqIpi;
    }

    public BigDecimal getValorFcp() {
        return valorFcp;
    }

    public void setValorFcp(BigDecimal valorFcp) {
        this.valorFcp = valorFcp;
    }

    public BigDecimal getAliqFcp() {
        return aliqFcp;
    }

    public void setAliqFcp(BigDecimal aliqFcp) {
        this.aliqFcp = aliqFcp;
    }

    public BigDecimal getValorIcmsSt() {
        return valorIcmsSt;
    }

    public void setValorIcmsSt(BigDecimal valorIcmsSt) {
        this.valorIcmsSt = valorIcmsSt;
    }

    public BigDecimal getAliqIcmsSt() {
        return aliqIcmsSt;
    }

    public void setAliqIcmsSt(BigDecimal aliqIcmsSt) {
        this.aliqIcmsSt = aliqIcmsSt;
    }

    public BigDecimal getBaseIcmsSt() {
        return baseIcmsSt;
    }

    public void setBaseIcmsSt(BigDecimal baseIcmsSt) {
        this.baseIcmsSt = baseIcmsSt;
    }

    public BigDecimal getPercReducaoBcIcmsSt() {
        return percReducaoBcIcmsSt;
    }

    public void setPercReducaoBcIcmsSt(BigDecimal percReducaoBcIcmsSt) {
        this.percReducaoBcIcmsSt = percReducaoBcIcmsSt;
    }

    public BigDecimal getPercMvaIcmsSt() {
        return percMvaIcmsSt;
    }

    public void setPercMvaIcmsSt(BigDecimal percMvaIcmsSt) {
        this.percMvaIcmsSt = percMvaIcmsSt;
    }

    public Integer getModBcIcmsSt() {
        return modBcIcmsSt;
    }

    public void setModBcIcmsSt(Integer modBcIcmsSt) {
        this.modBcIcmsSt = modBcIcmsSt;
    }

    public BigDecimal getValorIcms() {
        return valorIcms;
    }

    public void setValorIcms(BigDecimal valorIcms) {
        this.valorIcms = valorIcms;
    }

    public BigDecimal getAliqIcms() {
        return aliqIcms;
    }

    public void setAliqIcms(BigDecimal aliqIcms) {
        this.aliqIcms = aliqIcms;
    }

    public BigDecimal getPercReducaoBcIcms() {
        return percReducaoBcIcms;
    }

    public void setPercReducaoBcIcms(BigDecimal percReducaoBcIcms) {
        this.percReducaoBcIcms = percReducaoBcIcms;
    }

    public BigDecimal getBaseIcms() {
        return baseIcms;
    }

    public void setBaseIcms(BigDecimal baseIcms) {
        this.baseIcms = baseIcms;
    }

    public Integer getModBcIcms() {
        return modBcIcms;
    }

    public void setModBcIcms(Integer modBcIcms) {
        this.modBcIcms = modBcIcms;
    }

    public String getCsosnIcms() {
        return csosnIcms;
    }

    public void setCsosnIcms(String csosnIcms) {
        this.csosnIcms = csosnIcms;
    }

    public String getCstIcms() {
        return cstIcms;
    }

    public void setCstIcms(String cstIcms) {
        this.cstIcms = cstIcms;
    }

    public Integer getOrigemIcms() {
        return origemIcms;
    }

    public void setOrigemIcms(Integer origemIcms) {
        this.origemIcms = origemIcms;
    }

    public Integer getIndTot() {
        return indTot;
    }

    public void setIndTot(Integer indTot) {
        this.indTot = indTot;
    }

    public BigDecimal getValorOutro() {
        return valorOutro;
    }

    public void setValorOutro(BigDecimal valorOutro) {
        this.valorOutro = valorOutro;
    }

    public BigDecimal getValorDesc() {
        return valorDesc;
    }

    public void setValorDesc(BigDecimal valorDesc) {
        this.valorDesc = valorDesc;
    }

    public BigDecimal getValorSeg() {
        return valorSeg;
    }

    public void setValorSeg(BigDecimal valorSeg) {
        this.valorSeg = valorSeg;
    }

    public BigDecimal getValorFrete() {
        return valorFrete;
    }

    public void setValorFrete(BigDecimal valorFrete) {
        this.valorFrete = valorFrete;
    }

    public BigDecimal getValorUnTrib() {
        return valorUnTrib;
    }

    public void setValorUnTrib(BigDecimal valorUnTrib) {
        this.valorUnTrib = valorUnTrib;
    }

    public BigDecimal getQuantTrib() {
        return quantTrib;
    }

    public void setQuantTrib(BigDecimal quantTrib) {
        this.quantTrib = quantTrib;
    }

    public String getUnTrib() {
        return unTrib;
    }

    public void setUnTrib(String unTrib) {
        this.unTrib = unTrib;
    }

    public String getCodEanTrib() {
        return codEanTrib;
    }

    public void setCodEanTrib(String codEanTrib) {
        this.codEanTrib = codEanTrib;
    }

    public BigDecimal getValorProd() {
        return valorProd;
    }

    public void setValorProd(BigDecimal valorProd) {
        this.valorProd = valorProd;
    }

    public BigDecimal getValorUnCom() {
        return valorUnCom;
    }

    public void setValorUnCom(BigDecimal valorUnCom) {
        this.valorUnCom = valorUnCom;
    }

    public BigDecimal getQuantCom() {
        return quantCom;
    }

    public void setQuantCom(BigDecimal quantCom) {
        this.quantCom = quantCom;
    }

    public String getUnCom() {
        return unCom;
    }

    public void setUnCom(String unCom) {
        this.unCom = unCom;
    }

    public Integer getCfop() {
        return cfop;
    }

    public void setCfop(Integer cfop) {
        this.cfop = cfop;
    }

    public String getCest() {
        return cest;
    }

    public void setCest(String cest) {
        this.cest = cest;
    }

    public String getNcm() {
        return ncm;
    }

    public void setNcm(String ncm) {
        this.ncm = ncm;
    }

    public String getDescProd() {
        return descProd;
    }

    public void setDescProd(String descProd) {
        this.descProd = descProd;
    }

    public String getCodEan() {
        return codEan;
    }

    public void setCodEan(String codEan) {
        this.codEan = codEan;
    }

    public String getCodProd() {
        return codProd;
    }

    public void setCodProd(String codProd) {
        this.codProd = codProd;
    }

    public Integer getItem() {
        return item;
    }

    public void setItem(Integer item) {
        this.item = item;
    }

    public @NotNull Nfe getNfe() {
        return nfe;
    }

    public void setNfe(@NotNull Nfe nfe) {
        this.nfe = nfe;
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
