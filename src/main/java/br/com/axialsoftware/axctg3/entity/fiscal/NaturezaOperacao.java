package br.com.axialsoftware.axctg3.entity.fiscal;

import br.com.axialsoftware.axctg3.entity.contabil.ContaContabil;
import io.jmix.core.MetadataTools;
import io.jmix.core.annotation.DeletedBy;
import io.jmix.core.annotation.DeletedDate;
import io.jmix.core.entity.annotation.JmixGeneratedValue;
import io.jmix.core.metamodel.annotation.DependsOnProperties;
import io.jmix.core.metamodel.annotation.InstanceName;
import io.jmix.core.metamodel.annotation.JmixEntity;
import io.jmix.core.metamodel.annotation.NumberFormat;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@JmixEntity
@Table(name = "NATUREZA_OPERACAO", indexes = {
        @Index(name = "IDX_NATUREZA_OPERACAO_UNQ_CODIGO_COD_EMPRESA", columnList = "CODIGO, COD_EMPRESA", unique = true)
})
@Entity
public class NaturezaOperacao {
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

    @Column(name = "CODIGO", nullable = false)
    @NumberFormat(pattern = "##0")
    @NotNull
    private Integer codigo;

    // sem valor padrão: o NaturezaOperacaoEventListener preenche com a empresa
    // corrente quando está nulo
    @Column(name = "COD_EMPRESA", nullable = false)
    @NotNull
    private Integer codEmpresa;

    @Column(name = "NOME", nullable = false, length = 50)
    @NotNull
    private String nome;

    @Column(name = "CFOP", nullable = false)
    @NumberFormat(pattern = "0000")
    @NotNull
    private Integer cfop;

    // percentuais (não valores monetários): precision/scale menores que o
    // padrão de campos em dinheiro do projeto
    @Column(name = "ALIQ_ICMS", nullable = false, precision = 7, scale = 2)
    @NumberFormat(pattern = "##0.00")
    @NotNull
    private BigDecimal aliqIcms = BigDecimal.ZERO;

    @Column(name = "REDUCAO_ICMS", nullable = false, precision = 9, scale = 4)
    @NumberFormat(pattern = "##0.0000")
    @NotNull
    private BigDecimal reducaoIcms = BigDecimal.ZERO;

    @Column(name = "ALIQ_PIS", nullable = false, precision = 7, scale = 2)
    @NumberFormat(pattern = "##0.00")
    @NotNull
    private BigDecimal aliqPis = BigDecimal.ZERO;

    @Column(name = "ALIQ_COFINS", nullable = false, precision = 7, scale = 2)
    @NumberFormat(pattern = "##0.00")
    @NotNull
    private BigDecimal aliqCofins = BigDecimal.ZERO;

    // Reforma tributária (EC 132/2023, LC 214/2025): IBS e CBS substituem
    // ICMS/ISS e IPI/PIS/COFINS, mas coexistem com eles até 2033. São
    // tributos "por fora" (somam ao total do documento, não compõem a base
    // de cálculo do item, ao contrário do ICMS). Em 2026 vigora a
    // alíquota-teste fixa nacionalmente (0,1% IBS + 0,9% CBS), sem gerar
    // débito — compensável com PIS/COFINS já recolhidos.
    @Column(name = "ALIQ_IBS_UF", nullable = false, precision = 7, scale = 2)
    @NumberFormat(pattern = "##0.00")
    @NotNull
    private BigDecimal aliqIbsUf = BigDecimal.ZERO;

    @Column(name = "ALIQ_IBS_MUN", nullable = false, precision = 7, scale = 2)
    @NumberFormat(pattern = "##0.00")
    @NotNull
    private BigDecimal aliqIbsMun = BigDecimal.ZERO;

    @Column(name = "ALIQ_CBS", nullable = false, precision = 7, scale = 2)
    @NumberFormat(pattern = "##0.00")
    @NotNull
    private BigDecimal aliqCbs = BigDecimal.ZERO;

    // Código de Classificação Tributária (cClassTrib no leiaute NFe/NFCe,
    // grupo gIBSCBS) — 6 dígitos, define se a operação está sujeita à
    // alíquota-teste ou é isenta/diferida/imune (ex.: bonificação,
    // devolução, transferência entre estabelecimentos). Sem valor
    // preenchido a NFe não é autorizada desde 03/08/2026; nullable aqui
    // porque o cadastro de naturezas existentes precisa ser migrado aos
    // poucos e o código correto depende de análise contábil caso a caso.
    @Column(name = "COD_CLASS_TRIB")
    @NumberFormat(pattern = "000000")
    private Integer codClassTrib;

    // sem nullable=false: um Boolean NOT NULL vira propriedade "obrigatória" pro Jmix, e
    // como o valor vazio do Checkbox do Vaadin é justamente `false`, desmarcá-lo passa a
    // ser rejeitado como campo em branco. Ver Parceiro.cliente/fornecedor,
    // MovimentoBanco.entrada/tipoSaldo, User.active, Empresa.identMf/indEscCons — nenhum
    // booleano editável do projeto usa nullable=false por esse motivo.
    @Column(name = "VENDA")
    private Boolean venda = false;

    @JoinColumn(name = "CONTA_CONTABIL_ID")
    @ManyToOne(fetch = FetchType.LAZY)
    private ContaContabil contaContabil;

    public ContaContabil getContaContabil() {
        return contaContabil;
    }

    public void setContaContabil(ContaContabil contaContabil) {
        this.contaContabil = contaContabil;
    }

    public Boolean getVenda() {
        return venda;
    }

    public void setVenda(Boolean venda) {
        this.venda = venda;
    }

    public BigDecimal getAliqCofins() {
        return aliqCofins;
    }

    public void setAliqCofins(BigDecimal aliqCofins) {
        this.aliqCofins = aliqCofins;
    }

    public BigDecimal getAliqPis() {
        return aliqPis;
    }

    public void setAliqPis(BigDecimal aliqPis) {
        this.aliqPis = aliqPis;
    }

    public BigDecimal getReducaoIcms() {
        return reducaoIcms;
    }

    public void setReducaoIcms(BigDecimal reducaoIcms) {
        this.reducaoIcms = reducaoIcms;
    }

    public BigDecimal getAliqIcms() {
        return aliqIcms;
    }

    public void setAliqIcms(BigDecimal aliqIcms) {
        this.aliqIcms = aliqIcms;
    }

    public BigDecimal getAliqIbsUf() {
        return aliqIbsUf;
    }

    public void setAliqIbsUf(BigDecimal aliqIbsUf) {
        this.aliqIbsUf = aliqIbsUf;
    }

    public BigDecimal getAliqIbsMun() {
        return aliqIbsMun;
    }

    public void setAliqIbsMun(BigDecimal aliqIbsMun) {
        this.aliqIbsMun = aliqIbsMun;
    }

    public BigDecimal getAliqCbs() {
        return aliqCbs;
    }

    public void setAliqCbs(BigDecimal aliqCbs) {
        this.aliqCbs = aliqCbs;
    }

    public Integer getCodClassTrib() {
        return codClassTrib;
    }

    public void setCodClassTrib(Integer codClassTrib) {
        this.codClassTrib = codClassTrib;
    }

    public Integer getCfop() {
        return cfop;
    }

    public void setCfop(Integer cfop) {
        this.cfop = cfop;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public Integer getCodEmpresa() {
        return codEmpresa;
    }

    public void setCodEmpresa(Integer codEmpresa) {
        this.codEmpresa = codEmpresa;
    }

    public Integer getCodigo() {
        return codigo;
    }

    public void setCodigo(Integer codigo) {
        this.codigo = codigo;
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

    @InstanceName
    @DependsOnProperties({"codigo", "cfop", "nome"})
    public String getInstanceName(MetadataTools metadataTools) {
        return String.format("%d %d %s",
                codigo,
                cfop,
                metadataTools.format(nome));
    }
}
