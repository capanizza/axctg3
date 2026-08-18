package br.com.axialsoftware.axctg3.service.fiscal;

import java.util.Map;

/**
 * Código IBGE numérico de cada UF (campo {@code cUF} do leiaute NFe) — tabela fixa,
 * nunca muda, por isso não é entidade/tabela de banco (mesmo raciocínio de
 * {@code NfeWebserviceEndpoints}). Usada tanto na chave de acesso ({@code
 * NfeChaveService}) quanto no XML ({@code NfeXmlBuilder}).
 */
public final class UfIbge {

    private static final Map<String, Integer> CODIGO_POR_UF = Map.ofEntries(
            Map.entry("RO", 11), Map.entry("AC", 12), Map.entry("AM", 13), Map.entry("RR", 14),
            Map.entry("PA", 15), Map.entry("AP", 16), Map.entry("TO", 17), Map.entry("MA", 21),
            Map.entry("PI", 22), Map.entry("CE", 23), Map.entry("RN", 24), Map.entry("PB", 25),
            Map.entry("PE", 26), Map.entry("AL", 27), Map.entry("SE", 28), Map.entry("BA", 29),
            Map.entry("MG", 31), Map.entry("ES", 32), Map.entry("RJ", 33), Map.entry("SP", 35),
            Map.entry("PR", 41), Map.entry("SC", 42), Map.entry("RS", 43), Map.entry("MS", 50),
            Map.entry("MT", 51), Map.entry("GO", 52), Map.entry("DF", 53)
    );

    private UfIbge() {
    }

    /** @throws IllegalArgumentException se a sigla não for uma UF brasileira válida */
    public static Integer codigo(String uf) {
        Integer codigo = uf == null ? null : CODIGO_POR_UF.get(uf.trim().toUpperCase());
        if (codigo == null) {
            throw new IllegalArgumentException("UF inválida: " + uf);
        }
        return codigo;
    }
}
