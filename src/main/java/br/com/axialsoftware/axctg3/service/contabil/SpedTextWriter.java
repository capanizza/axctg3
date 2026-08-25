package br.com.axialsoftware.axctg3.service.contabil;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;

/**
 * Serializador genérico do layout de texto do Sped ECD (e, no futuro, de outros Speds
 * pipe-delimitados) — mecânico, sem regra de negócio contábil nenhuma. Ver Manual de
 * Orientação do Leiaute 9 da ECD, cap. 2.2/2.3, e docs/ecd/... (referências trazidas pelo
 * usuário na sessão que criou esta classe).
 *
 * <p>Regras de formatação fixas pelo manual: arquivo em ISO-8859-1, fim de linha CRLF,
 * campos separados por {@code |}, campo vazio é só {@code ||}, decimal com vírgula (sem
 * separador de milhar), data {@code ddMMyyyy} sem separador.
 *
 * <p>Uso: {@link #abrirBloco(String)} no início de cada bloco, {@link #registro} pra cada
 * linha de dado, {@link #fecharBloco(String)} no registro de encerramento do bloco
 * (0990/I990/J990/...), {@link #escreverBloco9(String)} pro bloco de controle, e
 * {@link #finalizar()} no fim — devolve os bytes prontos pro {@code Downloader}.
 */
public class SpedTextWriter {

    /**
     * Marcador pro campo QTD_LIN do registro I030 — "quantidade total de linhas do
     * arquivo digital" (Registro I030, campo 05), um valor que só existe depois que o
     * arquivo inteiro (inclusive o bloco 9900/9990/9999) já foi escrito, mas o campo fica
     * perto do topo do arquivo. Resolvido em {@link #finalizar()} por substituição de
     * texto, depois que a contagem final está fechada.
     */
    public static final String QTD_LIN_ARQUIVO = "@@QTD_LIN_ARQUIVO@@";

    private static final DateTimeFormatter DATA_SPED = DateTimeFormatter.ofPattern("ddMMyyyy");

    private final StringBuilder buffer = new StringBuilder();
    private final Map<String, Integer> contagemGlobal = new LinkedHashMap<>();
    private int linhasBlocoAtual = 0;

    /** Marca o início de um bloco novo — zera o contador de linhas do bloco atual. */
    public void abrirBloco(String nomeBloco) {
        linhasBlocoAtual = 0;
    }

    /** Escreve uma linha de registro: {@code |REG|campo1|campo2|...|}. */
    public void registro(String reg, Object... campos) {
        StringBuilder linha = new StringBuilder("|").append(reg);
        for (Object campo : campos) {
            linha.append('|').append(formatarCampo(campo));
        }
        linha.append('|');
        buffer.append(linha).append("\r\n");
        contagemGlobal.merge(reg, 1, Integer::sum);
        linhasBlocoAtual++;
    }

    /**
     * Escreve o registro de encerramento de um bloco (ex. 0990/I990), contando também a
     * própria linha de encerramento — mesma convenção do manual pros registros 099X/X990.
     */
    public void fecharBloco(String registroFechamento) {
        registro(registroFechamento, linhasBlocoAtual + 1);
    }

    /**
     * Escreve o bloco 9 inteiro (controle e encerramento do arquivo): 9001, um 9900 por
     * tipo de registro existente no arquivo inteiro (incluindo os do próprio bloco 9,
     * conforme manual), 9990 (linhas do bloco 9) e 9999 (linhas do arquivo inteiro).
     * Deve ser a última coisa escrita.
     */
    public void escreverBloco9(String indDadBloco9) {
        abrirBloco("9");
        registro("9001", indDadBloco9);

        LinkedHashSet<String> tipos = new LinkedHashSet<>(contagemGlobal.keySet());
        tipos.add("9900");
        tipos.add("9990");
        tipos.add("9999");
        int totalTipos = tipos.size();
        for (String tipo : tipos) {
            int qtd;
            switch (tipo) {
                case "9900" -> qtd = totalTipos;
                case "9990", "9999" -> qtd = 1;
                default -> qtd = contagemGlobal.get(tipo);
            }
            registro("9900", tipo, qtd);
        }

        fecharBloco("9990");

        long totalArquivo = contagemGlobal.values().stream().mapToLong(Integer::intValue).sum() + 1;
        registro("9999", totalArquivo);
    }

    /**
     * Devolve o arquivo pronto, em ISO-8859-1 (exigido pelo leiaute). Chamar só depois de
     * {@link #escreverBloco9(String)} — é quando a contagem final de linhas fecha, usada
     * pra resolver o marcador {@link #QTD_LIN_ARQUIVO}.
     */
    public byte[] finalizar() {
        long totalLinhas = contagemGlobal.values().stream().mapToLong(Integer::intValue).sum();
        String texto = buffer.toString().replace(QTD_LIN_ARQUIVO, String.valueOf(totalLinhas));
        return texto.getBytes(StandardCharsets.ISO_8859_1);
    }

    /** Formata um {@link BigDecimal} monetário: vírgula decimal, 2 casas, sem milhar. */
    public static String valor(BigDecimal v) {
        if (v == null) {
            return "";
        }
        return v.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString().replace('.', ',');
    }

    /** Formata uma data no padrão {@code ddMMyyyy} do Sped. */
    public static String data(LocalDate d) {
        return d == null ? "" : d.format(DATA_SPED);
    }

    /** Mantém só dígitos — uso em CPF/CNPJ/CEP/NIRE/IE, que o leiaute exige sem máscara. */
    public static String soDigitos(String s) {
        return s == null ? "" : s.replaceAll("\\D", "");
    }

    private String formatarCampo(Object campo) {
        if (campo == null) {
            return "";
        }
        String texto;
        if (campo instanceof BigDecimal bd) {
            texto = valor(bd);
        } else if (campo instanceof LocalDate ld) {
            texto = data(ld);
        } else {
            texto = campo.toString();
        }
        // "|" nunca pode fazer parte do conteúdo de um campo (regra do manual, cap. 2.2)
        return texto.replace("|", "");
    }
}
