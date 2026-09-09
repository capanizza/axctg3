package br.com.axialsoftware.axctg3.service.financeiro;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Um registro de detalhe já interpretado de um arquivo de retorno bancário — saída
 * mecânica de {@link BancoCobrancaHandler#lerRetorno(byte[])}, sem regra de negócio
 * nenhuma (a leitura da tabela de ocorrências e o que fazer com cada uma fica em
 * {@code RetornoBancoService}). {@code seuNumero} é a chave de correlação com
 * {@code TituloReceber.numero} — {@code nossoNumero} é só informativo/conferência, já que
 * quem gera o Nosso Número é o axctg3 na remessa (boleto emitido pelo beneficiário).
 */
public record RetornoDetalheLido(
        String seuNumero,
        String nossoNumero,
        String ocorrencia,
        LocalDate dataOcorrencia,
        LocalDate dataVencimento,
        BigDecimal valorTitulo,
        BigDecimal valorPago,
        BigDecimal juros,
        BigDecimal desconto,
        BigDecimal abatimento,
        String motivos
) {
}
