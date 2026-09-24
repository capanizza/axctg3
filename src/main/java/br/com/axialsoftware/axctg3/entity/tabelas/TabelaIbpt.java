package br.com.axialsoftware.axctg3.entity.tabelas;

import io.jmix.core.entity.annotation.JmixGeneratedValue;
import io.jmix.core.metamodel.annotation.DependsOnProperties;
import io.jmix.core.metamodel.annotation.InstanceName;
import io.jmix.core.metamodel.annotation.JmixEntity;
import io.jmix.core.metamodel.annotation.NumberFormat;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

// Tabela IBPT ("De Olho no Imposto", Lei 12.741/2012): carga tributária aproximada por
// NCM/NBS/LC116, um CSV por UF (TabelaIBPTax<UF><versão>.csv) trocado a cada versão.
// Global, sem codEmpresa — a UF vem do município da empresa emitente. Mesma exceção da
// ClassificacaoFiscal: sem auditoria/soft delete (pedido explícito, 2026-09-24) porque o
// import apaga e regrava todas as linhas da UF de uma vez.
@JmixEntity
@Table(name = "TABELA_IBPT", indexes = {
        @Index(name = "IDX_TABELA_IBPT_UNQ", columnList = "UF, CODIGO, EX, TIPO", unique = true)
})
@Entity
public class TabelaIbpt {
    @JmixGeneratedValue
    @Column(name = "ID", nullable = false)
    @Id
    private UUID id;

    @Version
    @Column(name = "VERSION", nullable = false)
    private Integer version;

    @Column(name = "UF", nullable = false, length = 2)
    @NotNull
    private String uf;

    // NCM (8 dígitos), NBS (9) ou item da LC116 — texto pra preservar zeros à esquerda.
    @Column(name = "CODIGO", nullable = false, length = 9)
    @NotNull
    private String codigo;

    // Exceção da TIPI; null quando o CSV traz a coluna vazia (caso da grande maioria).
    @Column(name = "EX", length = 2)
    private String ex;

    // 0 = NCM, 1 = NBS, 2 = LC116 (coluna "tipo" do CSV).
    @Column(name = "TIPO", nullable = false)
    @NotNull
    private Integer tipo = 0;

    @Column(name = "DESCRICAO", length = 500)
    private String descricao;

    @Column(name = "ALIQ_NACIONAL_FEDERAL", precision = 7, scale = 2)
    @NumberFormat(pattern = "#,##0.00", decimalSeparator = ",", groupingSeparator = ".")
    private BigDecimal aliqNacionalFederal = BigDecimal.ZERO;

    @Column(name = "ALIQ_IMPORTADOS_FEDERAL", precision = 7, scale = 2)
    @NumberFormat(pattern = "#,##0.00", decimalSeparator = ",", groupingSeparator = ".")
    private BigDecimal aliqImportadosFederal = BigDecimal.ZERO;

    @Column(name = "ALIQ_ESTADUAL", precision = 7, scale = 2)
    @NumberFormat(pattern = "#,##0.00", decimalSeparator = ",", groupingSeparator = ".")
    private BigDecimal aliqEstadual = BigDecimal.ZERO;

    @Column(name = "ALIQ_MUNICIPAL", precision = 7, scale = 2)
    @NumberFormat(pattern = "#,##0.00", decimalSeparator = ",", groupingSeparator = ".")
    private BigDecimal aliqMunicipal = BigDecimal.ZERO;

    @Column(name = "VIGENCIA_INICIO")
    private LocalDate vigenciaInicio;

    @Column(name = "VIGENCIA_FIM")
    private LocalDate vigenciaFim;

    // Chave que identifica a versão do arquivo — vai no texto "Fonte: IBPT <chave>".
    @Column(name = "CHAVE", length = 10)
    private String chave;

    @Column(name = "VERSAO", length = 10)
    private String versao;

    @Column(name = "FONTE", length = 60)
    private String fonte;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public String getUf() {
        return uf;
    }

    public void setUf(String uf) {
        this.uf = uf;
    }

    public String getCodigo() {
        return codigo;
    }

    public void setCodigo(String codigo) {
        this.codigo = codigo;
    }

    public String getEx() {
        return ex;
    }

    public void setEx(String ex) {
        this.ex = ex;
    }

    public Integer getTipo() {
        return tipo;
    }

    public void setTipo(Integer tipo) {
        this.tipo = tipo;
    }

    public String getDescricao() {
        return descricao;
    }

    public void setDescricao(String descricao) {
        this.descricao = descricao;
    }

    public BigDecimal getAliqNacionalFederal() {
        return aliqNacionalFederal;
    }

    public void setAliqNacionalFederal(BigDecimal aliqNacionalFederal) {
        this.aliqNacionalFederal = aliqNacionalFederal;
    }

    public BigDecimal getAliqImportadosFederal() {
        return aliqImportadosFederal;
    }

    public void setAliqImportadosFederal(BigDecimal aliqImportadosFederal) {
        this.aliqImportadosFederal = aliqImportadosFederal;
    }

    public BigDecimal getAliqEstadual() {
        return aliqEstadual;
    }

    public void setAliqEstadual(BigDecimal aliqEstadual) {
        this.aliqEstadual = aliqEstadual;
    }

    public BigDecimal getAliqMunicipal() {
        return aliqMunicipal;
    }

    public void setAliqMunicipal(BigDecimal aliqMunicipal) {
        this.aliqMunicipal = aliqMunicipal;
    }

    public LocalDate getVigenciaInicio() {
        return vigenciaInicio;
    }

    public void setVigenciaInicio(LocalDate vigenciaInicio) {
        this.vigenciaInicio = vigenciaInicio;
    }

    public LocalDate getVigenciaFim() {
        return vigenciaFim;
    }

    public void setVigenciaFim(LocalDate vigenciaFim) {
        this.vigenciaFim = vigenciaFim;
    }

    public String getChave() {
        return chave;
    }

    public void setChave(String chave) {
        this.chave = chave;
    }

    public String getVersao() {
        return versao;
    }

    public void setVersao(String versao) {
        this.versao = versao;
    }

    public String getFonte() {
        return fonte;
    }

    public void setFonte(String fonte) {
        this.fonte = fonte;
    }

    @InstanceName
    @DependsOnProperties({"uf", "codigo", "ex"})
    public String getInstanceName() {
        return uf + " " + codigo + (ex == null ? "" : " ex " + ex);
    }
}
