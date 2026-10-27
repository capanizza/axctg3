package br.com.axialsoftware.axctg3.service.importacao;

import br.com.axialsoftware.axctg3.entity.enums.SituacaoLote;
import br.com.axialsoftware.axctg3.entity.enums.TipoLote;
import br.com.axialsoftware.axctg3.entity.importacao.ImpLote;
import io.jmix.core.DataManager;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.sql.Clob;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * O que a importação de notas de saída e de pedidos de venda do legado têm em comum: lote por
 * período, linhas lidas por JDBC como mapa coluna → valor (comparadas e gravadas coluna a coluna),
 * tradução dos códigos do legado para os ids dos cadastros da empresa e o relatório no lote.
 */
final class ImportacaoDocumentos {

    private static final int LIMITE_LINHAS_SECAO = 300;

    private ImportacaoDocumentos() {
    }

    /** Lote do tipo, com ano (e mês válido), de empresa cadastrada; importar exige lote não importado. */
    static void validarLotePorPeriodo(JdbcTemplate jdbc, ImpLote lote, TipoLote tipo, String nome,
                                      ImportacaoPlanoContasService.Relatorio relatorio, boolean paraImportar) {
        if (lote.getTipo() != tipo) {
            relatorio.erros.add("O lote não é de " + nome + ".");
            return;
        }
        if (lote.getSituacao() == SituacaoLote.EXPORTANDO) {
            relatorio.erros.add("A exportação deste lote não terminou (situação EXPORTANDO). Exporte de novo.");
            return;
        }
        if (lote.getAno() == null) {
            relatorio.erros.add("O lote não tem ano. Exporte de novo.");
            return;
        }
        if (lote.getMes() != null && (lote.getMes() < 1 || lote.getMes() > 12)) {
            relatorio.erros.add("O lote informa o mês " + lote.getMes() + ", inválido. Exporte de novo.");
            return;
        }
        Integer empresas = jdbc.queryForObject(
                "select count(*) from EMPRESA where CODIGO = ? and DELETED_DATE is null", Integer.class,
                lote.getCodEmpresa());
        if (empresas == null || empresas == 0) {
            relatorio.erros.add("A empresa " + lote.getCodEmpresa() + " não está cadastrada no axctg3.");
            return;
        }
        if (paraImportar && lote.getSituacao() == SituacaoLote.IMPORTADO) {
            relatorio.erros.add("Este lote já foi importado. Exporte de novo para trazer o que mudou no legado.");
        }
    }

    static void gravarRelatorio(DataManager dataManager, ImpLote lote, ImportacaoPlanoContasService.Relatorio relatorio,
                                String resumo, boolean importado) {
        StringBuilder sb = new StringBuilder();
        sb.append(importado ? "IMPORTADO em " : "Conferido em ").append(OffsetDateTime.now().withNano(0)).append('\n');
        sb.append(resumo);
        secao(sb, "ERROS (impedem a importação)", relatorio.erros);
        secao(sb, "DIVERGÊNCIAS (não impedem)", relatorio.divergencias);
        secao(sb, "AVISOS", relatorio.avisos);
        lote.setMensagem(sb.toString());
        if (importado) {
            lote.setSituacao(SituacaoLote.IMPORTADO);
            lote.setDataImportacao(OffsetDateTime.now());
        }
        dataManager.saveWithoutReload(lote);
    }

    private static void secao(StringBuilder sb, String titulo, List<String> linhas) {
        if (linhas.isEmpty()) {
            return;
        }
        sb.append('\n').append(titulo).append(" (").append(linhas.size()).append(")\n");
        linhas.stream().limit(LIMITE_LINHAS_SECAO).forEach(linha -> sb.append("  - ").append(linha).append('\n'));
        if (linhas.size() > LIMITE_LINHAS_SECAO) {
            sb.append("  ... e mais ").append(linhas.size() - LIMITE_LINHAS_SECAO).append('\n');
        }
    }

    /**
     * Linha do resultado como coluna (maiúscula) → valor, com os tipos normalizados para comparar
     * o lote com o gravado: data vira LocalDate, CLOB vira texto, inteiro vira Integer e coluna de
     * id vira UUID (o HSQLDB dos testes não devolve UUID).
     */
    static Map<String, Object> linha(ResultSet rs) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        Map<String, Object> l = new LinkedHashMap<>();
        for (int i = 1; i <= meta.getColumnCount(); i++) {
            String coluna = meta.getColumnLabel(i).toUpperCase();
            Object v = rs.getObject(i);
            if (v instanceof java.sql.Date d) {
                v = d.toLocalDate();
            } else if (v instanceof Clob c) {
                v = c.getSubString(1, (int) c.length());
            } else if (v instanceof Short || v instanceof Long && !coluna.equals("ID")) {
                v = ((Number) v).intValue();
            }
            if (v != null && (coluna.equals("ID") || coluna.endsWith("_ID"))) {
                v = uuid(v);
            }
            l.put(coluna, v);
        }
        return l;
    }

    static UUID uuid(Object v) {
        if (v == null || v instanceof UUID) {
            return (UUID) v;
        }
        if (v instanceof byte[] b && b.length == 16) {
            java.nio.ByteBuffer bb = java.nio.ByteBuffer.wrap(b);
            return new UUID(bb.getLong(), bb.getLong());
        }
        return UUID.fromString(v.toString());
    }

    /** Colunas com valor diferente entre o do lote e o gravado ("valor 10 passa a 12"). */
    static List<String> diferencas(Map<String, Object> novo, Map<String, Object> gravado) {
        List<String> d = new ArrayList<>();
        novo.forEach((coluna, valor) -> {
            Object antes = gravado.get(coluna);
            if (!iguais(valor, antes)) {
                String nome = coluna.toLowerCase().replace('_', ' ');
                d.add(coluna.endsWith("_ID") ? nome.substring(0, nome.length() - 3) + " trocado"
                        : nome + " " + mostrar(antes) + " passa a " + mostrar(valor));
            }
        });
        return d;
    }

    static boolean mesmasLinhas(List<Map<String, Object>> novas, List<Map<String, Object>> gravadas) {
        if (novas.size() != gravadas.size()) {
            return false;
        }
        for (int i = 0; i < novas.size(); i++) {
            Map<String, Object> g = gravadas.get(i);
            for (Map.Entry<String, Object> e : novas.get(i).entrySet()) {
                if (!iguais(e.getValue(), g.get(e.getKey()))) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean iguais(Object a, Object b) {
        if (a instanceof String || b instanceof String) {
            return Objects.equals(texto((String) a), texto((String) b));
        }
        if (a instanceof BigDecimal || b instanceof BigDecimal) {
            return zero(a).compareTo(zero(b)) == 0;
        }
        if (a instanceof Boolean || b instanceof Boolean) {
            return Boolean.TRUE.equals(a) == Boolean.TRUE.equals(b);
        }
        return Objects.equals(a, b);
    }

    private static String mostrar(Object valor) {
        return valor == null ? "(vazio)" : "\"" + valor + "\"";
    }

    static String texto(String valor) {
        if (valor == null) {
            return null;
        }
        String t = valor.trim();
        return t.isEmpty() ? null : t;
    }

    static String cortar(String valor, int tamanho) {
        return valor == null || valor.length() <= tamanho ? valor : valor.substring(0, tamanho).trim();
    }

    static BigDecimal zero(Object valor) {
        return valor == null ? BigDecimal.ZERO : (BigDecimal) valor;
    }

    /** TIPOFRETE do legado como o f_nfe.pas lia: 1 = por conta do emitente, 2 = do destinatário. */
    static Integer modFrete(String tipoFrete) {
        String t = texto(tipoFrete);
        if ("1".equals(t)) {
            return 0;
        }
        if ("2".equals(t)) {
            return 1;
        }
        return null;
    }

    static String resumir(List<?> numeros) {
        return numeros.stream().limit(50).map(String::valueOf).collect(Collectors.joining(", "))
                + (numeros.size() > 50 ? "..." : "");
    }

    /**
     * Ids dos cadastros da empresa pelo código do legado. Parceiro, natureza e produto são
     * obrigatórios (código que não existe vira erro); condição, banco, transportadora, vendedor e
     * mensagem ficam vazios (aviso). Código 0 ou vazio é "sem". O que falta é contado por código e
     * relatado uma vez só.
     */
    static class Cadastros {
        private final Map<String, Map<Long, UUID>> ids = new HashMap<>();
        private final Map<String, Map<Long, Integer>> faltando = new LinkedHashMap<>();

        Cadastros(JdbcTemplate jdbc, Integer codEmpresa) {
            carregar(jdbc, codEmpresa, "parceiro", "PARCEIRO");
            carregar(jdbc, codEmpresa, "natureza de operação", "NATUREZA_OPERACAO");
            carregar(jdbc, codEmpresa, "produto", "PRODUTO");
            carregar(jdbc, codEmpresa, "condição de pagamento", "CONDICAO_PAGAMENTO");
            carregar(jdbc, codEmpresa, "banco", "BANCO");
            carregar(jdbc, codEmpresa, "transportadora", "TRANSPORTADORA");
            carregar(jdbc, codEmpresa, "vendedor", "VENDEDOR");
            carregar(jdbc, codEmpresa, "mensagem", "MENSAGEM");
        }

        private void carregar(JdbcTemplate jdbc, Integer codEmpresa, String nome, String tabela) {
            Map<Long, UUID> m = new HashMap<>();
            jdbc.query("select CODIGO, ID from " + tabela + " where COD_EMPRESA = ? and DELETED_DATE is null",
                    rs -> { m.put(rs.getLong(1), uuid(rs.getObject(2))); }, codEmpresa);
            ids.put(nome, m);
        }

        /** Sem parceiros, naturezas ou produtos na empresa, os documentos não têm como entrar. */
        void exigir(ImportacaoPlanoContasService.Relatorio relatorio, String documentos) {
            for (String nome : List.of("parceiro", "natureza de operação", "produto")) {
                if (ids.get(nome).isEmpty()) {
                    relatorio.erros.add("A empresa não tem nenhum(a) " + nome + ". Importe antes os cadastros: as "
                            + documentos + " dependem deles.");
                }
            }
        }

        UUID parceiro(Object codigo) {
            return achar("parceiro", codigo, true);
        }

        UUID natureza(Object codigo) {
            return achar("natureza de operação", codigo, true);
        }

        UUID produto(Object codigo) {
            return achar("produto", codigo, true);
        }

        UUID condicao(Object codigo) {
            return achar("condição de pagamento", codigo, false);
        }

        UUID banco(Object codigo) {
            return achar("banco", codigo, false);
        }

        UUID transportadora(Object codigo) {
            return achar("transportadora", codigo, false);
        }

        UUID vendedor(Object codigo) {
            return achar("vendedor", codigo, false);
        }

        UUID mensagem(Object codigo) {
            return achar("mensagem", codigo, false);
        }

        private UUID achar(String nome, Object codigo, boolean obrigatorio) {
            long c = codigo == null ? 0 : ((Number) codigo).longValue();
            if (c == 0 && !obrigatorio) {
                return null;
            }
            UUID id = ids.get(nome).get(c);
            if (id == null) {
                faltando.computeIfAbsent(nome, k -> new TreeMap<>()).merge(c, 1, Integer::sum);
            }
            return id;
        }

        /** Relata o que faltou: obrigatório vira erro, opcional vira aviso. */
        void relatar(ImportacaoPlanoContasService.Relatorio relatorio, String documentos) {
            faltando.forEach((nome, codigos) -> {
                boolean obrigatorio = List.of("parceiro", "natureza de operação", "produto").contains(nome);
                String lista = codigos.entrySet().stream().limit(30)
                        .map(e -> e.getKey() + " (" + e.getValue() + ")")
                        .collect(Collectors.joining(", ")) + (codigos.size() > 30 ? "..." : "");
                if (obrigatorio) {
                    relatorio.erros.add(capitalizar(nome) + " que não existe no axctg3 — código (quantas " + documentos
                            + "/itens): " + lista + ". Importe ou corrija o cadastro antes.");
                } else {
                    relatorio.avisos.add(capitalizar(nome) + " que não existe no axctg3 fica vazio(a) — código (quantas "
                            + documentos + "): " + lista + ".");
                }
            });
        }

        private static String capitalizar(String s) {
            return Character.toUpperCase(s.charAt(0)) + s.substring(1);
        }
    }
}
