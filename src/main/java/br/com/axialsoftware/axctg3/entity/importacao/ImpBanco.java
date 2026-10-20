package br.com.axialsoftware.axctg3.entity.importacao;

import io.jmix.core.annotation.DeletedBy;
import io.jmix.core.annotation.DeletedDate;
import io.jmix.core.entity.annotation.JmixGeneratedValue;
import io.jmix.core.metamodel.annotation.JmixEntity;
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
 * Banco do legado (BANCO, com a configuração de boleto de CONFBOLETO) dentro de um
 * {@link ImpLote} — ver {@code ImpLote}. Cru, como no Firebird; o tamanho dos campos do
 * {@code Banco} é conferido na importação.
 */
@JmixEntity
@Table(name = "IMP_BANCO", indexes = {
        @Index(name = "IDX_IMP_BANCO_LOTE", columnList = "LOTE_ID")
})
@Entity
public class ImpBanco {
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

    @JoinColumn(name = "LOTE_ID", nullable = false)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @NotNull
    private ImpLote lote;

    // BANCO + CONFBOLETO (pela coluna PAI), como no legado; sem CONFBOLETO os campos do boleto vêm vazios
    @Column(name = "CODIGO")
    private Integer codigo;

    @Column(name = "COD_GERAL")
    private Integer codGeral;

    @Column(name = "NOME")
    private String nome;

    @Column(name = "COD_CONTABIL")
    private String codContabil;

    @Column(name = "AGENCIA")
    private String agencia;

    @Column(name = "DIG_AGENCIA")
    private String digAgencia;

    @Column(name = "CONTA")
    private String conta;

    @Column(name = "DIG_CONTA")
    private String digConta;

    @Column(name = "COD_CEDENTE")
    private String codCedente;

    @Column(name = "DIG_CEDENTE")
    private String digCedente;

    @Column(name = "NOME_CEDENTE")
    private String nomeCedente;

    @Column(name = "LOCAL_PAGAMENTO")
    private String localPagamento;

    @Column(name = "ESPECIE")
    private String especie;

    @Column(name = "CARTEIRA")
    private String carteira;

    @Column(name = "CONVENIO")
    private String convenio;

    @Column(name = "NOSSO_NUM_INICIAL")
    private String nossoNumInicial;

    @Column(name = "NOSSO_NUM_FINAL")
    private String nossoNumFinal;

    @Column(name = "NOSSO_NUM_ATUAL")
    private String nossoNumAtual;

    @Column(name = "NUM_REMESSA")
    private Integer numRemessa;

    @Column(name = "DIAS_PROTESTO")
    private Integer diasProtesto;

    @Column(name = "MULTA", precision = 19, scale = 2)
    private BigDecimal multa;

    @Column(name = "MULTA_DIA", precision = 19, scale = 2)
    private BigDecimal multaDia;

    @Column(name = "MENSAGEM", length = 1000)
    private String mensagem;

    @Column(name = "BAIXA_NORMAL")
    private Integer baixaNormal;

    @Column(name = "BAIXA_JUROS")
    private Integer baixaJuros;

    @Column(name = "BAIXA_DESCONTO")
    private Integer baixaDesconto;

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

    public ImpLote getLote() {
        return lote;
    }

    public void setLote(ImpLote lote) {
        this.lote = lote;
    }

    public Integer getCodigo() {
        return codigo;
    }

    public void setCodigo(Integer codigo) {
        this.codigo = codigo;
    }

    public Integer getCodGeral() {
        return codGeral;
    }

    public void setCodGeral(Integer codGeral) {
        this.codGeral = codGeral;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getCodContabil() {
        return codContabil;
    }

    public void setCodContabil(String codContabil) {
        this.codContabil = codContabil;
    }

    public String getAgencia() {
        return agencia;
    }

    public void setAgencia(String agencia) {
        this.agencia = agencia;
    }

    public String getDigAgencia() {
        return digAgencia;
    }

    public void setDigAgencia(String digAgencia) {
        this.digAgencia = digAgencia;
    }

    public String getConta() {
        return conta;
    }

    public void setConta(String conta) {
        this.conta = conta;
    }

    public String getDigConta() {
        return digConta;
    }

    public void setDigConta(String digConta) {
        this.digConta = digConta;
    }

    public String getCodCedente() {
        return codCedente;
    }

    public void setCodCedente(String codCedente) {
        this.codCedente = codCedente;
    }

    public String getDigCedente() {
        return digCedente;
    }

    public void setDigCedente(String digCedente) {
        this.digCedente = digCedente;
    }

    public String getNomeCedente() {
        return nomeCedente;
    }

    public void setNomeCedente(String nomeCedente) {
        this.nomeCedente = nomeCedente;
    }

    public String getLocalPagamento() {
        return localPagamento;
    }

    public void setLocalPagamento(String localPagamento) {
        this.localPagamento = localPagamento;
    }

    public String getEspecie() {
        return especie;
    }

    public void setEspecie(String especie) {
        this.especie = especie;
    }

    public String getCarteira() {
        return carteira;
    }

    public void setCarteira(String carteira) {
        this.carteira = carteira;
    }

    public String getConvenio() {
        return convenio;
    }

    public void setConvenio(String convenio) {
        this.convenio = convenio;
    }

    public String getNossoNumInicial() {
        return nossoNumInicial;
    }

    public void setNossoNumInicial(String nossoNumInicial) {
        this.nossoNumInicial = nossoNumInicial;
    }

    public String getNossoNumFinal() {
        return nossoNumFinal;
    }

    public void setNossoNumFinal(String nossoNumFinal) {
        this.nossoNumFinal = nossoNumFinal;
    }

    public String getNossoNumAtual() {
        return nossoNumAtual;
    }

    public void setNossoNumAtual(String nossoNumAtual) {
        this.nossoNumAtual = nossoNumAtual;
    }

    public Integer getNumRemessa() {
        return numRemessa;
    }

    public void setNumRemessa(Integer numRemessa) {
        this.numRemessa = numRemessa;
    }

    public Integer getDiasProtesto() {
        return diasProtesto;
    }

    public void setDiasProtesto(Integer diasProtesto) {
        this.diasProtesto = diasProtesto;
    }

    public BigDecimal getMulta() {
        return multa;
    }

    public void setMulta(BigDecimal multa) {
        this.multa = multa;
    }

    public BigDecimal getMultaDia() {
        return multaDia;
    }

    public void setMultaDia(BigDecimal multaDia) {
        this.multaDia = multaDia;
    }

    public String getMensagem() {
        return mensagem;
    }

    public void setMensagem(String mensagem) {
        this.mensagem = mensagem;
    }

    public Integer getBaixaNormal() {
        return baixaNormal;
    }

    public void setBaixaNormal(Integer baixaNormal) {
        this.baixaNormal = baixaNormal;
    }

    public Integer getBaixaJuros() {
        return baixaJuros;
    }

    public void setBaixaJuros(Integer baixaJuros) {
        this.baixaJuros = baixaJuros;
    }

    public Integer getBaixaDesconto() {
        return baixaDesconto;
    }

    public void setBaixaDesconto(Integer baixaDesconto) {
        this.baixaDesconto = baixaDesconto;
    }
}
