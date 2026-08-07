package br.com.axialsoftware.axctg3.entity.financeiro;

import io.jmix.core.entity.annotation.JmixGeneratedValue;
import io.jmix.core.entity.annotation.JmixId;
import io.jmix.core.metamodel.annotation.JmixEntity;

import java.math.BigDecimal;
import java.sql.Date;
import java.util.UUID;

/**
 * Linha dos relatórios "Entrada de títulos a receber" (EntradaTituloReceber.jasper) e
 * "Títulos a receber por vencimento" (VencimentoTituloReceber.jasper). Ver
 * {@code TituloReceberService}.
 */
@JmixEntity
public class TituloReceberDto {

    @JmixGeneratedValue
    @JmixId
    private UUID id;

    private String numero;

    private Date dataEmissao;

    private Date dataVencimento;

    private Long parceiro;

    private String nomeParceiro;

    private Integer banco;

    private String nomeBanco;

    private BigDecimal valor = BigDecimal.ZERO;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getNumero() {
        return numero;
    }

    public void setNumero(String numero) {
        this.numero = numero;
    }

    public Date getDataEmissao() {
        return dataEmissao;
    }

    public void setDataEmissao(Date dataEmissao) {
        this.dataEmissao = dataEmissao;
    }

    public Date getDataVencimento() {
        return dataVencimento;
    }

    public void setDataVencimento(Date dataVencimento) {
        this.dataVencimento = dataVencimento;
    }

    public Long getParceiro() {
        return parceiro;
    }

    public void setParceiro(Long parceiro) {
        this.parceiro = parceiro;
    }

    public String getNomeParceiro() {
        return nomeParceiro;
    }

    public void setNomeParceiro(String nomeParceiro) {
        this.nomeParceiro = nomeParceiro;
    }

    public Integer getBanco() {
        return banco;
    }

    public void setBanco(Integer banco) {
        this.banco = banco;
    }

    public String getNomeBanco() {
        return nomeBanco;
    }

    public void setNomeBanco(String nomeBanco) {
        this.nomeBanco = nomeBanco;
    }

    public BigDecimal getValor() {
        return valor;
    }

    public void setValor(BigDecimal valor) {
        this.valor = valor;
    }

}
