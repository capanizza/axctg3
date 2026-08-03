package br.com.axialsoftware.axctg3.entity.contabil;

import br.com.axialsoftware.axctg3.entity.enums.CodNat;
import io.jmix.core.DeletePolicy;
import io.jmix.core.MetadataTools;
import io.jmix.core.annotation.DeletedBy;
import io.jmix.core.annotation.DeletedDate;
import io.jmix.core.entity.annotation.JmixGeneratedValue;
import io.jmix.core.entity.annotation.OnDelete;
import io.jmix.core.metamodel.annotation.*;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@JmixEntity
@Table(name = "CONTA_CONTABIL", indexes = {
        @Index(name = "IDX_CONTA_CONTABIL_UNQ", columnList = "CODIGO, COD_EMPRESA, ANO", unique = true)
})
@Entity
public class ContaContabil {
    @JmixGeneratedValue
    @Column(name = "ID", nullable = false)
    @Id
    private UUID id;

    @CreatedBy
    @Column(name = "CREATED_BY")
    private String createdBy;

    @CreatedDate
    @Column(name = "CREATED_DATE")
    private OffsetDateTime createdDate;

    @Column(name = "VERSION", nullable = false)
    @Version
    private Integer version;

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

    @Column(name = "CODIGO", nullable = false, length = 25)
    @NotNull
    private String codigo;

    @Column(name = "ANO", nullable = false)
    @NotNull
    private Integer ano;

    @Column(name = "COD_EMPRESA", nullable = false)
    @NotNull
    private Integer codEmpresa;

    @Column(name = "NOME", nullable = false, length = 50)
    @NotNull
    private String nome;

    @NumberFormat(pattern = "#0")
    @Column(name = "GRAU", nullable = false)
    @NotNull
    private Integer grau;

    @Column(name = "ANALITICA", nullable = false)
    @NotNull
    private Boolean analitica = false;

    @Column(name = "COD_NAT", nullable = false)
    @NotNull
    private Integer codNat;

    @Column(name = "COD_CONTA_ENC", length = 25)
    private String codContaEnc;

    @Column(name = "COD_CONTA_REF", length = 25)
    private String codContaRef;

    @NotNull
    @Column(name = "COD_CONTA_SUP", nullable = false, length = 25)
    private String codContaSup;

    @NumberFormat(pattern = "###,###,##0.00", decimalSeparator = ",", groupingSeparator = ".")
    @Column(name = "SALDO_TRANSF", precision = 19, scale = 2)
    private BigDecimal saldoTransf;

    @OnDelete(DeletePolicy.CASCADE)
    @Composition
    @OneToMany(mappedBy = "contaContabil")
    @OrderBy("mes")
    private List<SaldoConta> saldosConta;

    public List<SaldoConta> getSaldosConta() {
        return saldosConta;
    }

    public void setSaldosConta(List<SaldoConta> saldosConta) {
        this.saldosConta = saldosConta;
    }

    public BigDecimal getSaldoTransf() {
        return saldoTransf;
    }

    public void setSaldoTransf(BigDecimal saldoTransf) {
        this.saldoTransf = saldoTransf;
    }

    public String getCodContaSup() {
        return codContaSup;
    }

    public void setCodContaSup(String codContaSup) {
        this.codContaSup = codContaSup;
    }

    public String getCodContaRef() {
        return codContaRef;
    }

    public void setCodContaRef(String codContaRef) {
        this.codContaRef = codContaRef;
    }

    public String getCodContaEnc() {
        return codContaEnc;
    }

    public void setCodContaEnc(String codContaEnc) {
        this.codContaEnc = codContaEnc;
    }

    public CodNat getCodNat() {
        return codNat == null ? null : CodNat.fromId(codNat);
    }

    public void setCodNat(CodNat codNat) {
        this.codNat = codNat == null ? null : codNat.getId();
    }

    public Boolean getAnalitica() {
        return analitica;
    }

    public void setAnalitica(Boolean analitica) {
        this.analitica = analitica;
    }

    public Integer getGrau() {
        return grau;
    }

    public void setGrau(Integer grau) {
        this.grau = grau;
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

    public Integer getAno() {
        return ano;
    }

    public void setAno(Integer ano) {
        this.ano = ano;
    }

    public String getCodigo() {
        return codigo;
    }

    public void setCodigo(String codigo) {
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

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
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

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    @InstanceName
    @DependsOnProperties({"codigo", "nome"})
    public String getInstanceName(MetadataTools metadataTools) {
        return String.format("%s %s",
                metadataTools.format(codigo),
                metadataTools.format(nome));
    }
}