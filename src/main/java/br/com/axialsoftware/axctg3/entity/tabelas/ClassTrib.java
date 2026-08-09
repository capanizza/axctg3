package br.com.axialsoftware.axctg3.entity.tabelas;

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
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Código de Classificação Tributária do IBS/CBS (cClassTrib no leiaute NFe/NFCe, grupo
 * gIBSCBS — Informe Técnico RT 2025.002/LC 214/2025). Tabela global, sem codEmpresa
 * (como Municipio/TipoLogradouro) — não é reforma tributária "por empresa". Referenciada
 * por NaturezaOperacao.codClassTrib (Integer solto, sem FK — padrão do projeto).
 *
 * <p>Populada por seed inicial (changelog) e atualizável pela tela via import do JSON
 * oficial (formato NFe.io — mesma estrutura que a fonte distribui a cada nova versão do
 * Informe Técnico), porque a tabela muda com frequência e não há garantia de
 * estabilidade no layout do arquivo oficial da Receita/CGIBS.
 *
 * <p>Campos textuais (tipoAliquota, nomenclatura, creditoOperacaoAntecedente) são String
 * livre, não enum: são rótulos de uma fonte terceira, sem id numérico oficial (diferente
 * dos códigos SPED/ECD do resto do projeto) — um enum exigiria inventar ids e remapear a
 * cada import, com risco de quebrar se a fonte mudar grafia.
 */
@JmixEntity
@Table(name = "CLASS_TRIB", indexes = {
        @Index(name = "IDX_CLASS_TRIB_APLICAVEL_NFE", columnList = "APLICAVEL_NFE")
}, uniqueConstraints = {
        @UniqueConstraint(name = "IDX_CLASS_TRIB_UNQ", columnNames = {"CODIGO"})
})
@Entity
public class ClassTrib {
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

    // cClassTrib: 6 dígitos
    @NumberFormat(pattern = "000000")
    @Column(name = "CODIGO", nullable = false)
    @NotNull
    private Integer codigo;

    // CST-IBS/CBS: 3 dígitos, os 3 primeiros dígitos do código — agrupa vários
    // cClassTrib. Derivado no import, guardado à parte pra filtrar/agrupar sem
    // parsear o código toda hora.
    @NumberFormat(pattern = "000")
    @Column(name = "CST", nullable = false)
    @NotNull
    private Integer cst;

    @Column(name = "DESCRICAO", nullable = false)
    @Lob
    @NotNull
    private String descricao;

    // Padrão | Sem alíquota | Uniforme setorial | Uniforme nacional (referência) | Fixa
    @Column(name = "TIPO_ALIQUOTA", nullable = false, length = 60)
    @NotNull
    private String tipoAliquota;

    // NCM | NBS | NBS ou NCM | Não possui
    @Column(name = "NOMENCLATURA", nullable = false, length = 30)
    @NotNull
    private String nomenclatura;

    @Column(name = "DESCRICAO_TRATAMENTO_TRIBUTARIO", nullable = false, length = 200)
    @NotNull
    private String descricaoTratamentoTributario;

    @Column(name = "INCOMPATIVEL_COM_SUSPENSAO")
    private Boolean incompativelComSuspensao = false;

    @Column(name = "EXIGE_GRUPO_DESONERACAO")
    private Boolean exigeGrupoDesoneracao = false;

    @Column(name = "POSSUI_PERCENTUAL_REDUCAO")
    private Boolean possuiPercentualReducao = false;

    @Column(name = "IND_APROP_CREDITO_ADQUIRENTE_CBS")
    private Boolean indicaApropriacaoCreditoAdquirenteCbs = false;

    @Column(name = "IND_APROP_CREDITO_ADQUIRENTE_IBS")
    private Boolean indicaApropriacaoCreditoAdquirenteIbs = false;

    @Column(name = "IND_CREDITO_PRESUMIDO_FORNECEDOR")
    private Boolean indicaCreditoPresumidoFornecedor = false;

    @Column(name = "IND_CREDITO_PRESUMIDO_ADQUIRENTE")
    private Boolean indicaCreditoPresumidoAdquirente = false;

    // Manutenção | Anulação — nullable: 2 registros da fonte vêm sem valor
    @Column(name = "CREDITO_OPERACAO_ANTECEDENTE", length = 30)
    private String creditoOperacaoAntecedente;

    @Column(name = "PCT_REDUCAO_CBS", nullable = false, precision = 9, scale = 4)
    @NumberFormat(pattern = "##0.0000")
    @NotNull
    private BigDecimal percentualReducaoCbs = BigDecimal.ZERO;

    @Column(name = "PCT_REDUCAO_IBS_UF", nullable = false, precision = 9, scale = 4)
    @NumberFormat(pattern = "##0.0000")
    @NotNull
    private BigDecimal percentualReducaoIbsUf = BigDecimal.ZERO;

    @Column(name = "PCT_REDUCAO_IBS_MUN", nullable = false, precision = 9, scale = 4)
    @NumberFormat(pattern = "##0.0000")
    @NotNull
    private BigDecimal percentualReducaoIbsMun = BigDecimal.ZERO;

    // derivado no import a partir de tiposDfeClassificacao (sigla "NFe") — o axctg3 só
    // emite NFe hoje, então é o filtro relevante pra tela de NaturezaOperacao.
    @Column(name = "APLICAVEL_NFE")
    private Boolean aplicavelNfe = false;

    // lista denormalizada das siglas de documento onde o código vale (ex.: "NFe,NFCe,NFSe")
    // — só informativo/exibição, não vira tabela filha.
    @Column(name = "SIGLAS_DFE", length = 200)
    private String siglasDfe;

    // data de atualização do registro na fonte (NFe.io) — ajuda a comparar o que mudou
    // num reimport.
    @Column(name = "DATA_ATUALIZACAO")
    private LocalDate dataAtualizacao;

    public Integer getCodigo() {
        return codigo;
    }

    public void setCodigo(Integer codigo) {
        this.codigo = codigo;
    }

    public Integer getCst() {
        return cst;
    }

    public void setCst(Integer cst) {
        this.cst = cst;
    }

    public String getDescricao() {
        return descricao;
    }

    public void setDescricao(String descricao) {
        this.descricao = descricao;
    }

    public String getTipoAliquota() {
        return tipoAliquota;
    }

    public void setTipoAliquota(String tipoAliquota) {
        this.tipoAliquota = tipoAliquota;
    }

    public String getNomenclatura() {
        return nomenclatura;
    }

    public void setNomenclatura(String nomenclatura) {
        this.nomenclatura = nomenclatura;
    }

    public String getDescricaoTratamentoTributario() {
        return descricaoTratamentoTributario;
    }

    public void setDescricaoTratamentoTributario(String descricaoTratamentoTributario) {
        this.descricaoTratamentoTributario = descricaoTratamentoTributario;
    }

    public Boolean getIncompativelComSuspensao() {
        return incompativelComSuspensao;
    }

    public void setIncompativelComSuspensao(Boolean incompativelComSuspensao) {
        this.incompativelComSuspensao = incompativelComSuspensao;
    }

    public Boolean getExigeGrupoDesoneracao() {
        return exigeGrupoDesoneracao;
    }

    public void setExigeGrupoDesoneracao(Boolean exigeGrupoDesoneracao) {
        this.exigeGrupoDesoneracao = exigeGrupoDesoneracao;
    }

    public Boolean getPossuiPercentualReducao() {
        return possuiPercentualReducao;
    }

    public void setPossuiPercentualReducao(Boolean possuiPercentualReducao) {
        this.possuiPercentualReducao = possuiPercentualReducao;
    }

    public Boolean getIndicaApropriacaoCreditoAdquirenteCbs() {
        return indicaApropriacaoCreditoAdquirenteCbs;
    }

    public void setIndicaApropriacaoCreditoAdquirenteCbs(Boolean indicaApropriacaoCreditoAdquirenteCbs) {
        this.indicaApropriacaoCreditoAdquirenteCbs = indicaApropriacaoCreditoAdquirenteCbs;
    }

    public Boolean getIndicaApropriacaoCreditoAdquirenteIbs() {
        return indicaApropriacaoCreditoAdquirenteIbs;
    }

    public void setIndicaApropriacaoCreditoAdquirenteIbs(Boolean indicaApropriacaoCreditoAdquirenteIbs) {
        this.indicaApropriacaoCreditoAdquirenteIbs = indicaApropriacaoCreditoAdquirenteIbs;
    }

    public Boolean getIndicaCreditoPresumidoFornecedor() {
        return indicaCreditoPresumidoFornecedor;
    }

    public void setIndicaCreditoPresumidoFornecedor(Boolean indicaCreditoPresumidoFornecedor) {
        this.indicaCreditoPresumidoFornecedor = indicaCreditoPresumidoFornecedor;
    }

    public Boolean getIndicaCreditoPresumidoAdquirente() {
        return indicaCreditoPresumidoAdquirente;
    }

    public void setIndicaCreditoPresumidoAdquirente(Boolean indicaCreditoPresumidoAdquirente) {
        this.indicaCreditoPresumidoAdquirente = indicaCreditoPresumidoAdquirente;
    }

    public String getCreditoOperacaoAntecedente() {
        return creditoOperacaoAntecedente;
    }

    public void setCreditoOperacaoAntecedente(String creditoOperacaoAntecedente) {
        this.creditoOperacaoAntecedente = creditoOperacaoAntecedente;
    }

    public BigDecimal getPercentualReducaoCbs() {
        return percentualReducaoCbs;
    }

    public void setPercentualReducaoCbs(BigDecimal percentualReducaoCbs) {
        this.percentualReducaoCbs = percentualReducaoCbs;
    }

    public BigDecimal getPercentualReducaoIbsUf() {
        return percentualReducaoIbsUf;
    }

    public void setPercentualReducaoIbsUf(BigDecimal percentualReducaoIbsUf) {
        this.percentualReducaoIbsUf = percentualReducaoIbsUf;
    }

    public BigDecimal getPercentualReducaoIbsMun() {
        return percentualReducaoIbsMun;
    }

    public void setPercentualReducaoIbsMun(BigDecimal percentualReducaoIbsMun) {
        this.percentualReducaoIbsMun = percentualReducaoIbsMun;
    }

    public Boolean getAplicavelNfe() {
        return aplicavelNfe;
    }

    public void setAplicavelNfe(Boolean aplicavelNfe) {
        this.aplicavelNfe = aplicavelNfe;
    }

    public String getSiglasDfe() {
        return siglasDfe;
    }

    public void setSiglasDfe(String siglasDfe) {
        this.siglasDfe = siglasDfe;
    }

    public LocalDate getDataAtualizacao() {
        return dataAtualizacao;
    }

    public void setDataAtualizacao(LocalDate dataAtualizacao) {
        this.dataAtualizacao = dataAtualizacao;
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
    @DependsOnProperties({"codigo", "descricao"})
    public String getInstanceName(MetadataTools metadataTools) {
        return String.format("%06d %s",
                codigo,
                metadataTools.format(descricao));
    }
}
