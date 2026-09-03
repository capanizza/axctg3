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
 * Uma Carta de Correção Eletrônica ({@code tpEvento} 110110) emitida pra uma {@link Nfe}.
 * Diferente do cancelamento ({@link Nfe#getCancCStat()}, evento único por NFe, gravado como
 * campos soltos na própria entidade), a mesma NFe pode receber várias CC-e's ao longo do
 * tempo — SEFAZ permite até 20 — cada uma com {@code nSeqEvento} incremental. Por isso vira
 * filho de composição de {@link Nfe}, mesmo padrão de {@link NfeItem}/{@link NfeDuplicata}/
 * {@link NfePagamento}/{@link NfeVolume}, em vez de mais um grupo de campos únicos.
 *
 * <p>Ver {@link br.com.axialsoftware.axctg3.service.fiscal.NfeCartaCorrecaoService}.
 */
@JmixEntity
@Table(name = "NFE_CARTA_CORRECAO", indexes = {
        @Index(name = "IDX_NFE_CARTA_CORRECAO_NFE", columnList = "NFE_ID")
})
@Entity
public class NfeCartaCorrecao {
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

    // nSeqEvento (1, 2, 3...) — nome por extenso, não "nSeqEvento": "getNSeqEvento()" cai no
    // bug de introspecção Java Beans de campo "1 letra minúscula + Maiúscula" ("NS" maiúsculo-
    // maiúsculo trava o decapitalize), mesmo motivo por que NfeInutilizacao renomeou
    // nNFIni/nNFFin para numeroInicial/numeroFinal
    @Column(name = "NUMERO_SEQUENCIAL", nullable = false)
    @NotNull
    private Integer numeroSequencial;

    // xCorrecao — schema permite até 1000 caracteres (mais que os 255 de xJust/justificativa)
    @InstanceName
    @Column(name = "TEXTO_CORRECAO", length = 1000, nullable = false)
    @NotNull
    private String textoCorrecao;

    // dhEvento enviado no evento
    @Column(name = "DATA_HORA_EVENTO")
    private OffsetDateTime dataHoraEvento;

    // Campos de retorno do evento, prefixados "cce" pelo mesmo motivo do prefixo "canc"/"ret"
    // já usado em Nfe.cancCStat/NfeInutilizacao.retCStat — evita o mesmo bug de introspecção
    // em cStat/xMotivo/nProt crus (ex.: "getCStat()" -> "CS" maiúsculo-maiúsculo)

    // cStat do evento (135 = "Evento registrado e vinculado a NF-e")
    @Column(name = "CCE_C_STAT")
    private Integer cceCStat;

    @Column(name = "CCE_X_MOTIVO", length = 255)
    private String cceXMotivo;

    // protocolo do EVENTO de CC-e (nProt de retEvento/infEvento)
    @Column(name = "CCE_N_PROT", length = 15)
    private String cceNProt;

    @Column(name = "CCE_DH_REG_EVENTO")
    private OffsetDateTime cceDhRegEvento;

    // XML de retorno do evento (retEvento) — útil pra reconsulta/auditoria, mesmo padrão de
    // Nfe.cancXmlRetorno
    @Column(name = "CCE_XML_RETORNO")
    @Lob
    private String cceXmlRetorno;

    public Nfe getNfe() {
        return nfe;
    }

    public void setNfe(Nfe nfe) {
        this.nfe = nfe;
    }

    public Integer getNumeroSequencial() {
        return numeroSequencial;
    }

    public void setNumeroSequencial(Integer numeroSequencial) {
        this.numeroSequencial = numeroSequencial;
    }

    public String getTextoCorrecao() {
        return textoCorrecao;
    }

    public void setTextoCorrecao(String textoCorrecao) {
        this.textoCorrecao = textoCorrecao;
    }

    public OffsetDateTime getDataHoraEvento() {
        return dataHoraEvento;
    }

    public void setDataHoraEvento(OffsetDateTime dataHoraEvento) {
        this.dataHoraEvento = dataHoraEvento;
    }

    public Integer getCceCStat() {
        return cceCStat;
    }

    public void setCceCStat(Integer cceCStat) {
        this.cceCStat = cceCStat;
    }

    public String getCceXMotivo() {
        return cceXMotivo;
    }

    public void setCceXMotivo(String cceXMotivo) {
        this.cceXMotivo = cceXMotivo;
    }

    public String getCceNProt() {
        return cceNProt;
    }

    public void setCceNProt(String cceNProt) {
        this.cceNProt = cceNProt;
    }

    public OffsetDateTime getCceDhRegEvento() {
        return cceDhRegEvento;
    }

    public void setCceDhRegEvento(OffsetDateTime cceDhRegEvento) {
        this.cceDhRegEvento = cceDhRegEvento;
    }

    public String getCceXmlRetorno() {
        return cceXmlRetorno;
    }

    public void setCceXmlRetorno(String cceXmlRetorno) {
        this.cceXmlRetorno = cceXmlRetorno;
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
