package br.com.axialsoftware.axctg3.entity.fiscal;

import io.jmix.core.annotation.DeletedBy;
import io.jmix.core.annotation.DeletedDate;
import io.jmix.core.entity.annotation.JmixGeneratedValue;
import io.jmix.core.metamodel.annotation.InstanceName;
import io.jmix.core.metamodel.annotation.JmixEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Registro de um Pedido de Inutilização de Numeração de NF-e (schema {@code inutNFe} v4.00
 * — não é um {@code tpEvento}, é um tipo de mensagem próprio, com webservice dedicado
 * {@code NFeInutilizacao4}; ver {@link br.com.axialsoftware.axctg3.service.fiscal.NfeInutilizacaoService}).
 * Diferente do cancelamento ({@link Nfe#getCancCStat()}, que atualiza uma {@code Nfe} já
 * existente), a faixa inutilizada nunca teve nota emitida — não há linha de {@link Nfe}
 * correspondente pra pendurar o resultado, por isso esta entidade própria.
 *
 * <p>Sucesso é {@code cStat=102} ("Inutilização de número homologada"). {@code ano} é
 * gravado com 4 dígitos (mais legível na tela); {@code NfeInutilizacaoService} reduz pra 2
 * dígitos na hora de montar o XML, como exige o schema ({@code Tano}).
 */
@JmixEntity
@Table(name = "NFE_INUTILIZACAO", indexes = {
        @Index(name = "IDX_NFE_INUTILIZACAO_COD_EMPRESA", columnList = "COD_EMPRESA")
})
@Entity
public class NfeInutilizacao {
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

    @Column(name = "COD_EMPRESA", nullable = false)
    @NotNull
    private Integer codEmpresa;

    // ano civil de 4 dígitos (ex.: 2026) — convertido pra 2 dígitos só na hora de montar o XML
    @Column(name = "ANO", nullable = false)
    @NotNull
    private Integer ano;

    // mod — 55 (NF-e) ou 65 (NFC-e), mesmo default de Nfe.mod
    @Column(name = "MODELO", nullable = false)
    @NotNull
    private Integer modelo = 55;

    @Column(name = "SERIE", nullable = false)
    @NotNull
    private Integer serie;

    // nNFIni no schema — renomeado pra fugir do bug de introspecção Java Beans de campo
    // "1 letra minúscula + Maiúscula" (getNNfIni() não decapitaliza pra nNfIni; ver
    // NfeInutilizacaoService, que faz a conversão de volta pro nome de tag do XML)
    @Column(name = "NUMERO_INICIAL", nullable = false)
    @NotNull
    private Integer numeroInicial;

    // nNFFin no schema, mesmo motivo do campo acima
    @Column(name = "NUMERO_FINAL", nullable = false)
    @NotNull
    private Integer numeroFinal;

    // xJust (mínimo 15 caracteres exigido pelo schema — validado em NfeInutilizacaoService)
    @InstanceName
    @Column(name = "JUSTIFICATIVA", length = 255)
    private String justificativa;

    // cStat/xMotivo/nProt do retInutNFe — prefixados "ret" (em vez dos nomes crus do
    // schema) pelo mesmo motivo de numeroInicial/numeroFinal acima: "cStat"/"xMotivo"/
    // "nProt" caem no bug de introspecção Java Beans de campo "1 letra minúscula +
    // Maiúscula". 102 = "Inutilização de número homologada"
    @Column(name = "RET_C_STAT")
    private Integer retCStat;

    @Column(name = "RET_X_MOTIVO", length = 255)
    private String retXMotivo;

    @Column(name = "RET_N_PROT", length = 15)
    private String retNProt;

    @Column(name = "DH_RECBTO")
    private OffsetDateTime dhRecbto;

    // XML de retorno (retInutNFe) — útil pra reconsulta/auditoria, mesmo padrão de
    // Nfe.cancXmlRetorno
    @Column(name = "XML_RETORNO")
    @Lob
    private String xmlRetorno;

    public Integer getCodEmpresa() {
        return codEmpresa;
    }

    public void setCodEmpresa(Integer codEmpresa) {
        this.codEmpresa = codEmpresa;
    }

    public Integer getAno() {
        return ano;
    }

    public void setAno(Integer ano) {
        this.ano = ano;
    }

    public Integer getModelo() {
        return modelo;
    }

    public void setModelo(Integer modelo) {
        this.modelo = modelo;
    }

    public Integer getSerie() {
        return serie;
    }

    public void setSerie(Integer serie) {
        this.serie = serie;
    }

    public Integer getNumeroInicial() {
        return numeroInicial;
    }

    public void setNumeroInicial(Integer numeroInicial) {
        this.numeroInicial = numeroInicial;
    }

    public Integer getNumeroFinal() {
        return numeroFinal;
    }

    public void setNumeroFinal(Integer numeroFinal) {
        this.numeroFinal = numeroFinal;
    }

    public String getJustificativa() {
        return justificativa;
    }

    public void setJustificativa(String justificativa) {
        this.justificativa = justificativa;
    }

    public Integer getRetCStat() {
        return retCStat;
    }

    public void setRetCStat(Integer retCStat) {
        this.retCStat = retCStat;
    }

    public String getRetXMotivo() {
        return retXMotivo;
    }

    public void setRetXMotivo(String retXMotivo) {
        this.retXMotivo = retXMotivo;
    }

    public String getRetNProt() {
        return retNProt;
    }

    public void setRetNProt(String retNProt) {
        this.retNProt = retNProt;
    }

    public OffsetDateTime getDhRecbto() {
        return dhRecbto;
    }

    public void setDhRecbto(OffsetDateTime dhRecbto) {
        this.dhRecbto = dhRecbto;
    }

    public String getXmlRetorno() {
        return xmlRetorno;
    }

    public void setXmlRetorno(String xmlRetorno) {
        this.xmlRetorno = xmlRetorno;
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
