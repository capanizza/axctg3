package br.com.axialsoftware.axctg3.entity.tabelas;

import br.com.axialsoftware.axctg3.entity.enums.CodNat;
import br.com.axialsoftware.axctg3.entity.enums.CodPlanRef;
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

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Plano de Contas Referencial da Receita Federal (SPED ECD) — o "de-para" oficial entre o plano
 * de contas próprio de cada empresa e um plano de contas padronizado, um por {@link CodPlanRef}
 * (10 planos: PJ em geral, financeiras, seguradoras, imunes/isentas etc.). Tabela global, sem
 * {@code codEmpresa} (como {@code ClassTrib}/{@code Municipio}) — o plano referencial é o mesmo
 * pra todas as empresas que usam aquele {@link CodPlanRef}.
 *
 * <p>Cada {@code ContaContabil} analítica referencia uma linha desta tabela
 * ({@code ContaContabil.contaReferencial}), filtrada pelo {@code CodPlanRef} da {@code Empresa}.
 *
 * <p>Populada e atualizada pela tela via import do xlsx oficial "Planos Referenciais" da
 * RFB/Sped ECD (troca todo ano, numa nova versão do leiaute) — ver
 * {@link br.com.axialsoftware.axctg3.service.tabelas.ContaReferencialImportService}. Faz upsert
 * por ({@code codPlanRef}, {@code codigo}), então reimportar o arquivo do ano seguinte atualiza o
 * que mudou sem duplicar.
 */
@JmixEntity
@Table(name = "CONTA_REFERENCIAL", indexes = {
        @Index(name = "IDX_CONTA_REFERENCIAL_UNQ", columnList = "COD_PLAN_REF, CODIGO", unique = true)
})
@Entity
public class ContaReferencial {
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

    // qual dos 10 planos referenciais da RFB esta linha pertence
    @Column(name = "COD_PLAN_REF", nullable = false)
    @NotNull
    private Integer codPlanRef;

    @Column(name = "CODIGO", nullable = false, length = 25)
    @NotNull
    private String codigo;

    @Column(name = "DESCRICAO", nullable = false, length = 200)
    @NotNull
    private String descricao;

    @Column(name = "DT_INI", nullable = false)
    @NotNull
    private LocalDate dtIni;

    // não populado na fonte atual, mas o layout oficial prevê revogação de conta
    @Column(name = "DT_FIM")
    private LocalDate dtFim;

    // sem nullable=false/@NotNull: um Boolean obrigatório faz o Jmix rejeitar desmarcar o
    // Checkbox, já que `false` é o valor "vazio" do Vaadin Checkbox (mesmo motivo do
    // ContaContabil.analitica). TIPO=A -> true (recebe lançamento/mapeamento), TIPO=S -> false
    // (linha de agrupamento).
    @Column(name = "ANALITICA")
    private Boolean analitica = false;

    // auto-referência por código, mesmo padrão não-FK do ContaContabil.codContaSup — raízes
    // (ex.: "1 ATIVO") não têm pai
    @Column(name = "COD_CONTA_SUP", length = 25)
    private String codContaSup;

    @NumberFormat(pattern = "#0")
    @Column(name = "GRAU", nullable = false)
    @NotNull
    private Integer grau;

    @Column(name = "COD_NAT", nullable = false)
    @NotNull
    private Integer codNat;

    @Lob
    @Column(name = "ORIENTACOES")
    private String orientacoes;

    public CodPlanRef getCodPlanRef() {
        return codPlanRef == null ? null : CodPlanRef.fromId(codPlanRef);
    }

    public void setCodPlanRef(CodPlanRef codPlanRef) {
        this.codPlanRef = codPlanRef == null ? null : codPlanRef.getId();
    }

    public String getCodigo() {
        return codigo;
    }

    public void setCodigo(String codigo) {
        this.codigo = codigo;
    }

    public String getDescricao() {
        return descricao;
    }

    public void setDescricao(String descricao) {
        this.descricao = descricao;
    }

    public LocalDate getDtIni() {
        return dtIni;
    }

    public void setDtIni(LocalDate dtIni) {
        this.dtIni = dtIni;
    }

    public LocalDate getDtFim() {
        return dtFim;
    }

    public void setDtFim(LocalDate dtFim) {
        this.dtFim = dtFim;
    }

    public Boolean getAnalitica() {
        return analitica;
    }

    public void setAnalitica(Boolean analitica) {
        this.analitica = analitica;
    }

    public String getCodContaSup() {
        return codContaSup;
    }

    public void setCodContaSup(String codContaSup) {
        this.codContaSup = codContaSup;
    }

    public Integer getGrau() {
        return grau;
    }

    public void setGrau(Integer grau) {
        this.grau = grau;
    }

    public CodNat getCodNat() {
        return codNat == null ? null : CodNat.fromId(codNat);
    }

    public void setCodNat(CodNat codNat) {
        this.codNat = codNat == null ? null : codNat.getId();
    }

    public String getOrientacoes() {
        return orientacoes;
    }

    public void setOrientacoes(String orientacoes) {
        this.orientacoes = orientacoes;
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
        return String.format("%s %s",
                metadataTools.format(codigo),
                metadataTools.format(descricao));
    }
}
