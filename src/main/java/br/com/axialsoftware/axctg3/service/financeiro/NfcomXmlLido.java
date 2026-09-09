package br.com.axialsoftware.axctg3.service.financeiro;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Resultado do parse de um XML de NFCom (nota autorizada ou evento de cancelamento) pelo
 * {@link NfcomXmlParser} — despachado por tipo em {@link NfcomImportService} via
 * {@code instanceof} (mesmo padrão de pattern matching já usado no projeto, Java 21).
 */
public sealed interface NfcomXmlLido permits NfcomXmlLido.Nota, NfcomXmlLido.Cancelamento {

    /**
     * Nota autorizada. {@code numero} já vem formatado com zero à esquerda (6 dígitos, igual
     * ao {@code TituloReceber.numero}) — ver {@link NfcomXmlParser}.
     */
    record Nota(String numero, LocalDate dataEmissao, LocalDate dataVencimento, BigDecimal valor,
                Destinatario destinatario) implements NfcomXmlLido {

        /** Dados do {@code dest} da NFCom, usados pra casar/criar o {@code Parceiro}. */
        public record Destinatario(String cnpjCpf, String nome, String inscricaoEstadual, String logradouro,
                                    String numero, String bairro, Integer codMunicipio, String cep, String uf,
                                    String telefone) {
        }
    }

    /**
     * Evento de cancelamento (tpEvento 110111). {@code numero} é derivado da chave de acesso
     * (últimos 6 dígitos do campo nNF, posições 29-34 — ver {@link NfcomXmlParser}), já que o
     * evento não traz um grupo {@code ide} com {@code nNF} isolado.
     */
    record Cancelamento(String numero) implements NfcomXmlLido {
    }
}
