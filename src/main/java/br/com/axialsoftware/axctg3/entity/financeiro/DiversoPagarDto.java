package br.com.axialsoftware.axctg3.entity.financeiro;

import io.jmix.core.entity.annotation.JmixGeneratedValue;
import io.jmix.core.entity.annotation.JmixId;
import io.jmix.core.metamodel.annotation.JmixEntity;

import java.math.BigDecimal;
import java.sql.Date;
import java.util.UUID;

/**
 * Linha do relatório "Entrada de diversos a pagar" (EntradaDiversoPagar.jasper). Ver
 * {@code DiversoPagarService.listarEntradaDiversosPagar}.
 */
@JmixEntity
public class DiversoPagarDto {

    @JmixGeneratedValue
    @JmixId
    private UUID id;

    private Integer numero;

    private String documento;

    private Date dataEmissao;

    private Date dataVencimento;

    private Long parceiro;

    private String nomeParceiro;

    private BigDecimal valor = BigDecimal.ZERO;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Integer getNumero() {
        return numero;
    }

    public void setNumero(Integer numero) {
        this.numero = numero;
    }

    public String getDocumento() {
        return documento;
    }

    public void setDocumento(String documento) {
        this.documento = documento;
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

    public BigDecimal getValor() {
        return valor;
    }

    public void setValor(BigDecimal valor) {
        this.valor = valor;
    }

}
