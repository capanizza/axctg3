package br.com.axialsoftware.axctg3.entity.fiscal;

import io.jmix.core.annotation.DeletedBy;
import io.jmix.core.annotation.DeletedDate;
import io.jmix.core.entity.annotation.JmixGeneratedValue;
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

/**
 * Forma de pagamento ({@code pag/detPag}) de uma {@link Nfe}. Campos de cartão
 * ({@code tpIntegra}/{@code CNPJ} da credenciadora/bandeira/autorização) só fazem
 * sentido quando {@link #getTipoPag()} indica cartão de crédito/débito.
 */
@JmixEntity
@Table(name = "NFE_PAGAMENTO", indexes = {
        @Index(name = "IDX_NFE_PAGAMENTO_NFE", columnList = "NFE_ID")
})
@Entity
public class NfePagamento {
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

    // indPag: 0=à vista, 1=a prazo, 2=outros
    @Column(name = "IND_PAG")
    private Integer indPag;

    // tPag: 01=dinheiro, 03=cartão crédito, 04=cartão débito, 15=boleto, 17=PIX...
    @InstanceName
    @Column(name = "TIPO_PAG", length = 2)
    private String tipoPag;

    // xPag: descrição livre, obrigatória quando tPag=99 (outros)
    @Column(name = "DESCRICAO_PAG", length = 60)
    private String descricaoPag;

    // vPag
    @NumberFormat(pattern = "###,###,##0.00", decimalSeparator = ",", groupingSeparator = ".")
    @Column(name = "VALOR_PAG", precision = 19, scale = 2)
    private BigDecimal valorPag = BigDecimal.ZERO;

    // tpIntegra: 1=integrado (TEF), 2=não integrado
    @Column(name = "TIPO_INTEGRACAO_CARTAO")
    private Integer tipoIntegracaoCartao;

    @Column(name = "CNPJ_CREDENCIADORA", length = 14)
    private String cnpjCredenciadora;

    // tBand: 01=Visa, 02=Mastercard, 03=American Express...
    @Column(name = "BANDEIRA_CARTAO", length = 2)
    private String bandeiraCartao;

    @Column(name = "NUMERO_AUTORIZACAO_CARTAO", length = 20)
    private String numeroAutorizacaoCartao;

    public String getNumeroAutorizacaoCartao() {
        return numeroAutorizacaoCartao;
    }

    public void setNumeroAutorizacaoCartao(String numeroAutorizacaoCartao) {
        this.numeroAutorizacaoCartao = numeroAutorizacaoCartao;
    }

    public String getBandeiraCartao() {
        return bandeiraCartao;
    }

    public void setBandeiraCartao(String bandeiraCartao) {
        this.bandeiraCartao = bandeiraCartao;
    }

    public String getCnpjCredenciadora() {
        return cnpjCredenciadora;
    }

    public void setCnpjCredenciadora(String cnpjCredenciadora) {
        this.cnpjCredenciadora = cnpjCredenciadora;
    }

    public Integer getTipoIntegracaoCartao() {
        return tipoIntegracaoCartao;
    }

    public void setTipoIntegracaoCartao(Integer tipoIntegracaoCartao) {
        this.tipoIntegracaoCartao = tipoIntegracaoCartao;
    }

    public BigDecimal getValorPag() {
        return valorPag;
    }

    public void setValorPag(BigDecimal valorPag) {
        this.valorPag = valorPag;
    }

    public String getDescricaoPag() {
        return descricaoPag;
    }

    public void setDescricaoPag(String descricaoPag) {
        this.descricaoPag = descricaoPag;
    }

    public String getTipoPag() {
        return tipoPag;
    }

    public void setTipoPag(String tipoPag) {
        this.tipoPag = tipoPag;
    }

    public Integer getIndPag() {
        return indPag;
    }

    public void setIndPag(Integer indPag) {
        this.indPag = indPag;
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
