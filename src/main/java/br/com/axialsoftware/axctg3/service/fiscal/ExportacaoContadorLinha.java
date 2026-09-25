package br.com.axialsoftware.axctg3.service.fiscal;

import java.math.BigDecimal;
import java.util.Date;

/**
 * Uma linha do relatório de conferência (ExportacaoContador.jrxml) que acompanha o zip de
 * XMLs pro contador — bean simples pro {@code JRBeanCollectionDataSource}, não é entidade
 * nem é exibido em tela. {@code secao} agrupa o relatório (NFe emitidas, canceladas, cartas
 * de correção, inutilizações); {@code valorSoma} é o que entra no total da seção (zero pra
 * NFe emitida e depois cancelada, que continua listada mas não soma).
 */
public class ExportacaoContadorLinha {

    private final Integer ordemSecao;
    private final String secao;
    private final String numero;
    private final String serie;
    private final Date data;
    private final String descricao;
    private final String detalhe;
    private final BigDecimal valor;
    private final BigDecimal valorSoma;

    public ExportacaoContadorLinha(Integer ordemSecao, String secao, String numero, String serie, Date data,
                                   String descricao, String detalhe, BigDecimal valor, BigDecimal valorSoma) {
        this.ordemSecao = ordemSecao;
        this.secao = secao;
        this.numero = numero;
        this.serie = serie;
        this.data = data;
        this.descricao = descricao;
        this.detalhe = detalhe;
        this.valor = valor;
        this.valorSoma = valorSoma;
    }

    public Integer getOrdemSecao() {
        return ordemSecao;
    }

    public String getSecao() {
        return secao;
    }

    public String getNumero() {
        return numero;
    }

    public String getSerie() {
        return serie;
    }

    public Date getData() {
        return data;
    }

    public String getDescricao() {
        return descricao;
    }

    public String getDetalhe() {
        return detalhe;
    }

    public BigDecimal getValor() {
        return valor;
    }

    public BigDecimal getValorSoma() {
        return valorSoma;
    }
}
