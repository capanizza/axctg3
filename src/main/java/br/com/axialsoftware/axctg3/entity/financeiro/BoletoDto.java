package br.com.axialsoftware.axctg3.entity.financeiro;

import io.jmix.core.entity.annotation.JmixGeneratedValue;
import io.jmix.core.entity.annotation.JmixId;
import io.jmix.core.metamodel.annotation.InstanceName;
import io.jmix.core.metamodel.annotation.JmixEntity;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Um boleto impresso (Recibo do Pagador + Ficha de Compensação) — DTO não persistente
 * {@code @JmixEntity}, mesmo molde de {@link br.com.axialsoftware.axctg3.entity.fiscal.DanfeItemDto},
 * usado só como bean do {@code JRBeanCollectionDataSource} passado pro template
 * {@code boleto<codGeral>.jasper} (ver {@code BoletoService}). Uma instância por
 * {@link TituloReceber} selecionado; template é por banco porque o leiaute impresso e a
 * composição do código de barras variam de banco pra banco (Febraban só padroniza a
 * estrutura geral).
 *
 * <p>Sem linhas de desconto/mora/multa/juros — {@link TituloReceber}/{@link ItemReceber}
 * carregam esses valores só na baixa, não na emissão (ver Javadoc de {@code TituloReceber});
 * o boleto legado (referência: PDFs reais em {@code c:/remessa/748/202603/pdf}) também sai
 * com essas linhas em branco.
 */
@JmixEntity
public class BoletoDto {
    @JmixGeneratedValue
    @JmixId
    private UUID id;

    @InstanceName
    private String numeroDocumento;

    // Já formatadas "dd/MM/yyyy" pelo BoletoService — o atributo pattern do JasperReports
    // não formata campo java.time.LocalDate como esperado nesse projeto (sai em ISO
    // "2026-09-02"), mesmo gotcha documentado em DanfeDuplicataDto.
    private String dataDocumento;

    private String dataVencimento;

    private String dataProcessamento;

    private BigDecimal valorDocumento;

    private String especieDoc;

    private String aceite;

    /** Código do banco + dígito verificador Febraban, ex.: {@code "748-X"}. */
    private String codigoBancoComDv;

    private String localPagamento;

    private String nossoNumeroFormatado;

    /** Ex.: {@code "0738.33.59622"} (agência.posto.código do cedente). */
    private String agenciaCodigoBeneficiario;

    private String nomeBeneficiario;

    private String cnpjBeneficiario;

    private String enderecoBeneficiario;

    private String nomePagador;

    private String cnpjCpfPagador;

    private String enderecoPagador;

    /** Texto de instruções de responsabilidade do beneficiário (ver {@link Banco#getMensagem()}). */
    private String instrucoes;

    /** 44 dígitos, sem formatação — vira {@code codeExpression} do componente Interleaved2Of5. */
    private String codigoBarras;

    /** Código de barras formatado nos 5 campos padrão Febraban, com espaços. */
    private String linhaDigitavel;

    public String getNumeroDocumento() {
        return numeroDocumento;
    }

    public void setNumeroDocumento(String numeroDocumento) {
        this.numeroDocumento = numeroDocumento;
    }

    public String getDataDocumento() {
        return dataDocumento;
    }

    public void setDataDocumento(String dataDocumento) {
        this.dataDocumento = dataDocumento;
    }

    public String getDataVencimento() {
        return dataVencimento;
    }

    public void setDataVencimento(String dataVencimento) {
        this.dataVencimento = dataVencimento;
    }

    public String getDataProcessamento() {
        return dataProcessamento;
    }

    public void setDataProcessamento(String dataProcessamento) {
        this.dataProcessamento = dataProcessamento;
    }

    public BigDecimal getValorDocumento() {
        return valorDocumento;
    }

    public void setValorDocumento(BigDecimal valorDocumento) {
        this.valorDocumento = valorDocumento;
    }

    public String getEspecieDoc() {
        return especieDoc;
    }

    public void setEspecieDoc(String especieDoc) {
        this.especieDoc = especieDoc;
    }

    public String getAceite() {
        return aceite;
    }

    public void setAceite(String aceite) {
        this.aceite = aceite;
    }

    public String getCodigoBancoComDv() {
        return codigoBancoComDv;
    }

    public void setCodigoBancoComDv(String codigoBancoComDv) {
        this.codigoBancoComDv = codigoBancoComDv;
    }

    public String getLocalPagamento() {
        return localPagamento;
    }

    public void setLocalPagamento(String localPagamento) {
        this.localPagamento = localPagamento;
    }

    public String getNossoNumeroFormatado() {
        return nossoNumeroFormatado;
    }

    public void setNossoNumeroFormatado(String nossoNumeroFormatado) {
        this.nossoNumeroFormatado = nossoNumeroFormatado;
    }

    public String getAgenciaCodigoBeneficiario() {
        return agenciaCodigoBeneficiario;
    }

    public void setAgenciaCodigoBeneficiario(String agenciaCodigoBeneficiario) {
        this.agenciaCodigoBeneficiario = agenciaCodigoBeneficiario;
    }

    public String getNomeBeneficiario() {
        return nomeBeneficiario;
    }

    public void setNomeBeneficiario(String nomeBeneficiario) {
        this.nomeBeneficiario = nomeBeneficiario;
    }

    public String getCnpjBeneficiario() {
        return cnpjBeneficiario;
    }

    public void setCnpjBeneficiario(String cnpjBeneficiario) {
        this.cnpjBeneficiario = cnpjBeneficiario;
    }

    public String getEnderecoBeneficiario() {
        return enderecoBeneficiario;
    }

    public void setEnderecoBeneficiario(String enderecoBeneficiario) {
        this.enderecoBeneficiario = enderecoBeneficiario;
    }

    public String getNomePagador() {
        return nomePagador;
    }

    public void setNomePagador(String nomePagador) {
        this.nomePagador = nomePagador;
    }

    public String getCnpjCpfPagador() {
        return cnpjCpfPagador;
    }

    public void setCnpjCpfPagador(String cnpjCpfPagador) {
        this.cnpjCpfPagador = cnpjCpfPagador;
    }

    public String getEnderecoPagador() {
        return enderecoPagador;
    }

    public void setEnderecoPagador(String enderecoPagador) {
        this.enderecoPagador = enderecoPagador;
    }

    public String getInstrucoes() {
        return instrucoes;
    }

    public void setInstrucoes(String instrucoes) {
        this.instrucoes = instrucoes;
    }

    public String getCodigoBarras() {
        return codigoBarras;
    }

    public void setCodigoBarras(String codigoBarras) {
        this.codigoBarras = codigoBarras;
    }

    public String getLinhaDigitavel() {
        return linhaDigitavel;
    }

    public void setLinhaDigitavel(String linhaDigitavel) {
        this.linhaDigitavel = linhaDigitavel;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }
}
