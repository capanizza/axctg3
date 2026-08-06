package br.com.axialsoftware.axctg3.entity.financeiro;

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
@Table(name = "BANCO", indexes = {
        @Index(name = "IDX_BANCO_UNQ_CODIGO_COD_EMPRESA", columnList = "CODIGO, COD_EMPRESA", unique = true)
})
@Entity
public class Banco {
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
    @NumberFormat(pattern = "###0")
    @NotNull
    private Integer codigo;

    @Column(name = "COD_EMPRESA", nullable = false)
    @NumberFormat(pattern = "##0")
    @NotNull
    private Integer codEmpresa;

    @Column(name = "COD_GERAL")
    @NumberFormat(pattern = "###0")
    private Integer codGeral;

    @Column(name = "NOME", nullable = false, length = 30)
    @NotNull
    private String nome;

    @JoinColumn(name = "CONTA_CONTABIL_ID")
    @ManyToOne(fetch = FetchType.LAZY)
    private ContaContabil contaContabil;

    @Column(name = "COD_CONTABIL", length = 25)
    private String codContabil;

    @Column(name = "AGENCIA", length = 10)
    private String agencia;

    @Column(name = "DIG_AGENCIA", length = 3)
    private String digAgencia;

    @Column(name = "CONTA", length = 10)
    private String conta;

    @Column(name = "DIG_CONTA", length = 3)
    private String digConta;

    @Column(name = "COD_CEDENTE", length = 10)
    private String codCedente;

    @Column(name = "DIG_CEDENTE", length = 2)
    private String digCedente;

    @Column(name = "NOME_CEDENTE", length = 50)
    private String nomeCedente;

    @Column(name = "LOCAL_PAGAMENTO", length = 50)
    private String localPagamento;

    @Column(name = "BAIXA_NORMAL")
    private Integer baixaNormal;

    @Column(name = "BAIXA_JUROS")
    private Integer baixaJuros;

    @Column(name = "BAIXA_DESCONTO")
    private Integer baixaDesconto;

    @Column(name = "MULTA_ATRASO")
    private BigDecimal multaAtraso;

    @Column(name = "MULTA_DIA")
    private BigDecimal multaDia;

    @Column(name = "DIAS_PROTESTO")
    private Integer diasProtesto;

    @Column(name = "CARTEIRA", length = 10)
    private String carteira;

    @Column(name = "CONVENIO", length = 10)
    private String convenio;

    @Column(name = "NOSSO_NUM_INICIAL", length = 10)
    private String nossoNumInicial;

    @Column(name = "NOSSO_NUM_FINAL", length = 10)
    private String nossoNumFinal;

    @Column(name = "NOSSO_NUM_ATUAL", length = 10)
    private String nossoNumAtual;

    @Column(name = "PASTA_REMESSA",  length = 50)
    private String pastaRemessa;

    @Column(name = "PASTA_RETORNO",  length = 50)
    private String pastaRetorno;

    @Column(name = "NUM_REMESSA")
    private Integer numRemessa;

    @Column(name = "ESPECIE", length = 10)
    private String especie;

    @Column(name = "MENSAGEM")
    @Lob
    private String mensagem;

    public String getMensagem() {
        return mensagem;
    }

    public void setMensagem(String mensagem) {
        this.mensagem = mensagem;
    }

    public String getEspecie() {
        return especie;
    }

    public void setEspecie(String especie) {
        this.especie = especie;
    }

    public Integer getNumRemessa() {
        return numRemessa;
    }

    public void setNumRemessa(Integer numRemessa) {
        this.numRemessa = numRemessa;
    }

    public String getPastaRetorno() {
        return pastaRetorno;
    }

    public void setPastaRetorno(String pastaRetorno) {
        this.pastaRetorno = pastaRetorno;
    }

    public String getPastaRemessa() {
        return pastaRemessa;
    }

    public void setPastaRemessa(String pastaRemessa) {
        this.pastaRemessa = pastaRemessa;
    }

    public String getNossoNumAtual() {
        return nossoNumAtual;
    }

    public void setNossoNumAtual(String nossoNumAtual) {
        this.nossoNumAtual = nossoNumAtual;
    }

    public String getNossoNumFinal() {
        return nossoNumFinal;
    }

    public void setNossoNumFinal(String nossoNumFinal) {
        this.nossoNumFinal = nossoNumFinal;
    }

    public String getNossoNumInicial() {
        return nossoNumInicial;
    }

    public void setNossoNumInicial(String nossoNumInicial) {
        this.nossoNumInicial = nossoNumInicial;
    }

    public String getConvenio() {
        return convenio;
    }

    public void setConvenio(String convenio) {
        this.convenio = convenio;
    }

    public String getCarteira() {
        return carteira;
    }

    public void setCarteira(String carteira) {
        this.carteira = carteira;
    }

    public Integer getDiasProtesto() {
        return diasProtesto;
    }

    public void setDiasProtesto(Integer diasProtesto) {
        this.diasProtesto = diasProtesto;
    }

    public BigDecimal getMultaDia() {
        return multaDia;
    }

    public void setMultaDia(BigDecimal multaDia) {
        this.multaDia = multaDia;
    }

    public BigDecimal getMultaAtraso() {
        return multaAtraso;
    }

    public void setMultaAtraso(BigDecimal multaAtraso) {
        this.multaAtraso = multaAtraso;
    }

    public Integer getBaixaDesconto() {
        return baixaDesconto;
    }

    public void setBaixaDesconto(Integer baixaDesconto) {
        this.baixaDesconto = baixaDesconto;
    }

    public Integer getBaixaJuros() {
        return baixaJuros;
    }

    public void setBaixaJuros(Integer baixaJuros) {
        this.baixaJuros = baixaJuros;
    }

    public Integer getBaixaNormal() {
        return baixaNormal;
    }

    public void setBaixaNormal(Integer baixaNormal) {
        this.baixaNormal = baixaNormal;
    }

    public String getLocalPagamento() {
        return localPagamento;
    }

    public void setLocalPagamento(String localPagamento) {
        this.localPagamento = localPagamento;
    }

    public String getNomeCedente() {
        return nomeCedente;
    }

    public void setNomeCedente(String nomeCedente) {
        this.nomeCedente = nomeCedente;
    }

    public String getDigCedente() {
        return digCedente;
    }

    public void setDigCedente(String digCedente) {
        this.digCedente = digCedente;
    }

    public String getCodCedente() {
        return codCedente;
    }

    public void setCodCedente(String codCedente) {
        this.codCedente = codCedente;
    }

    public String getDigConta() {
        return digConta;
    }

    public Integer getCodigo() {
        return codigo;
    }

    public void setCodigo(Integer codigo) {
        this.codigo = codigo;
    }

    public Integer getCodEmpresa() {
        return codEmpresa;
    }

    public void setCodEmpresa(Integer codEmpresa) {
        this.codEmpresa = codEmpresa;
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

    public ContaContabil getContaContabil() {
        return contaContabil;
    }

    public void setContaContabil(ContaContabil contaContabil) {
        this.contaContabil = contaContabil;
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

    public void setDigConta(String digConta) {
        this.digConta = digConta;
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
    @DependsOnProperties({"codigo", "codGeral", "nome"})
    public String getInstanceName(MetadataTools metadataTools) {
        return String.format("%d %d %s",
                codigo,
                codGeral,
                metadataTools.format(nome));
    }
}