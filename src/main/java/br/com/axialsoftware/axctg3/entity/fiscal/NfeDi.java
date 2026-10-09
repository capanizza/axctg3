package br.com.axialsoftware.axctg3.entity.fiscal;

import br.com.axialsoftware.axctg3.entity.enums.FormaImportacao;
import br.com.axialsoftware.axctg3.entity.enums.ViaTransporteInternacional;
import io.jmix.core.DeletePolicy;
import io.jmix.core.annotation.DeletedBy;
import io.jmix.core.annotation.DeletedDate;
import io.jmix.core.entity.annotation.JmixGeneratedValue;
import io.jmix.core.entity.annotation.OnDelete;
import io.jmix.core.metamodel.annotation.Composition;
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
import java.util.List;
import java.util.UUID;

/**
 * Declaração de Importação ({@code det/prod/DI}) de um {@link NfeItem} — obrigatória em
 * item com CFOP de entrada do exterior (3xxx). Um item pode ter várias (o leiaute aceita
 * até 100), e cada uma tem uma ou mais {@link NfeDiAdicao} (grupo {@code adi}). Snapshot
 * do que foi declarado na nota, sem ligação com cadastro nenhum — mesma lógica de
 * {@link Nfe}/{@link NfeItem}. As tags originais ficam nos comentários de cada campo.
 */
@JmixEntity
@Table(name = "NFE_DI", indexes = {
        @Index(name = "IDX_NFE_DI_NFE_ITEM", columnList = "NFE_ITEM_ID")
})
@Entity
public class NfeDi {
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

    @JoinColumn(name = "NFE_ITEM_ID", nullable = false)
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private NfeItem nfeItem;

    // nDI — número da DI/DSI/DIRE/DUIMP (até 15 caracteres)
    @InstanceName
    @Column(name = "NUMERO_DI", length = 15)
    private String numeroDi;

    // dDI — data de registro do documento
    @Column(name = "DATA_DI")
    private LocalDate dataDi;

    // xLocDesemb — local do desembaraço aduaneiro
    @Column(name = "LOCAL_DESEMBARACO", length = 60)
    private String localDesembaraco;

    // UFDesemb
    @Column(name = "UF_DESEMBARACO", length = 2)
    private String ufDesembaraco;

    // dDesemb
    @Column(name = "DATA_DESEMBARACO")
    private LocalDate dataDesembaraco;

    // tpViaTransp — ver ViaTransporteInternacional
    @Column(name = "VIA_TRANSPORTE")
    private Integer viaTransporte;

    // vAFRMM — Adicional ao Frete para Renovação da Marinha Mercante (só via marítima)
    @NumberFormat(pattern = "###,###,##0.00", decimalSeparator = ",", groupingSeparator = ".")
    @Column(name = "VALOR_AFRMM", precision = 19, scale = 2)
    private BigDecimal valorAfrmm = BigDecimal.ZERO;

    // tpIntermedio — ver FormaImportacao
    @Column(name = "FORMA_IMPORTACAO")
    private Integer formaImportacao;

    // CNPJ ou CPF do adquirente/encomendante (por conta e ordem ou encomenda) — o XML
    // escolhe a tag pelo tamanho, mesmo critério do destinatário em NfeXmlBuilder
    @Column(name = "CNPJ_CPF_ADQUIRENTE", length = 14)
    private String cnpjCpfAdquirente;

    // UFTerceiro
    @Column(name = "UF_TERCEIRO", length = 2)
    private String ufTerceiro;

    // cExportador
    @Column(name = "COD_EXPORTADOR", length = 60)
    private String codExportador;

    @OnDelete(DeletePolicy.CASCADE)
    @Composition
    @OrderBy("sequencial")
    @OneToMany(mappedBy = "nfeDi")
    private List<NfeDiAdicao> adicoes;

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

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public OffsetDateTime getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(OffsetDateTime createdDate) {
        this.createdDate = createdDate;
    }

    public String getLastModifiedBy() {
        return lastModifiedBy;
    }

    public void setLastModifiedBy(String lastModifiedBy) {
        this.lastModifiedBy = lastModifiedBy;
    }

    public OffsetDateTime getLastModifiedDate() {
        return lastModifiedDate;
    }

    public void setLastModifiedDate(OffsetDateTime lastModifiedDate) {
        this.lastModifiedDate = lastModifiedDate;
    }

    public String getDeletedBy() {
        return deletedBy;
    }

    public void setDeletedBy(String deletedBy) {
        this.deletedBy = deletedBy;
    }

    public OffsetDateTime getDeletedDate() {
        return deletedDate;
    }

    public void setDeletedDate(OffsetDateTime deletedDate) {
        this.deletedDate = deletedDate;
    }

    public NfeItem getNfeItem() {
        return nfeItem;
    }

    public void setNfeItem(NfeItem nfeItem) {
        this.nfeItem = nfeItem;
    }

    public String getNumeroDi() {
        return numeroDi;
    }

    public void setNumeroDi(String numeroDi) {
        this.numeroDi = numeroDi;
    }

    public LocalDate getDataDi() {
        return dataDi;
    }

    public void setDataDi(LocalDate dataDi) {
        this.dataDi = dataDi;
    }

    public String getLocalDesembaraco() {
        return localDesembaraco;
    }

    public void setLocalDesembaraco(String localDesembaraco) {
        this.localDesembaraco = localDesembaraco;
    }

    public String getUfDesembaraco() {
        return ufDesembaraco;
    }

    public void setUfDesembaraco(String ufDesembaraco) {
        this.ufDesembaraco = ufDesembaraco;
    }

    public LocalDate getDataDesembaraco() {
        return dataDesembaraco;
    }

    public void setDataDesembaraco(LocalDate dataDesembaraco) {
        this.dataDesembaraco = dataDesembaraco;
    }

    public BigDecimal getValorAfrmm() {
        return valorAfrmm;
    }

    public void setValorAfrmm(BigDecimal valorAfrmm) {
        this.valorAfrmm = valorAfrmm;
    }

    public String getCnpjCpfAdquirente() {
        return cnpjCpfAdquirente;
    }

    public void setCnpjCpfAdquirente(String cnpjCpfAdquirente) {
        this.cnpjCpfAdquirente = cnpjCpfAdquirente;
    }

    public String getUfTerceiro() {
        return ufTerceiro;
    }

    public void setUfTerceiro(String ufTerceiro) {
        this.ufTerceiro = ufTerceiro;
    }

    public String getCodExportador() {
        return codExportador;
    }

    public void setCodExportador(String codExportador) {
        this.codExportador = codExportador;
    }

    public List<NfeDiAdicao> getAdicoes() {
        return adicoes;
    }

    public void setAdicoes(List<NfeDiAdicao> adicoes) {
        this.adicoes = adicoes;
    }

    public ViaTransporteInternacional getViaTransporte() {
        return viaTransporte == null ? null : ViaTransporteInternacional.fromId(viaTransporte);
    }

    public void setViaTransporte(ViaTransporteInternacional viaTransporte) {
        this.viaTransporte = viaTransporte == null ? null : viaTransporte.getId();
    }

    public FormaImportacao getFormaImportacao() {
        return formaImportacao == null ? null : FormaImportacao.fromId(formaImportacao);
    }

    public void setFormaImportacao(FormaImportacao formaImportacao) {
        this.formaImportacao = formaImportacao == null ? null : formaImportacao.getId();
    }
}
