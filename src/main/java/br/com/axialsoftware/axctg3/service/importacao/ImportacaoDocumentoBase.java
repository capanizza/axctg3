package br.com.axialsoftware.axctg3.service.importacao;

import br.com.axialsoftware.axctg3.entity.enums.TipoLote;
import br.com.axialsoftware.axctg3.entity.importacao.ImpLote;
import io.jmix.core.DataManager;
import io.jmix.core.UuidProvider;
import io.jmix.data.Sequence;
import io.jmix.data.Sequences;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Importação de documentos do legado com cabeçalho e itens — notas de saída e pedidos de venda —
 * para a empresa do lote. Um lote por ano (ou mês) da data do documento. Cada serviço só escolhe a
 * {@link Config} e converte a linha do legado ({@link #converter}); conferência, comparação e
 * gravação ficam aqui.
 * <p>
 * Os documentos são histórico: entram como estão no legado, sem passar pelos listeners (que
 * renumerariam o documento e recalculariam CST e ICMS dos itens) — por JDBC em lote, com
 * {@code CREATED_BY = }{@value ImportacaoTitulosReceberService#IMPORTACAO}. O documento é achado
 * pelo número (único na empresa, no legado). Exige parceiros, naturezas e produtos já importados.
 * <p>
 * Reimportação: documento que veio do legado recebe os dados de novo e os itens são trocados se
 * mudaram (soft delete dos antigos). Documento com o mesmo número criado no axctg3, ou importado
 * que ganhou item feito aqui, impede a importação. No fim, a numeração da empresa passa a
 * continuar do maior número ({@link #maiorNumero}).
 */
abstract class ImportacaoDocumentoBase {

    static final String IMPORTACAO = ImportacaoTitulosReceberService.IMPORTACAO;
    private static final int TAMANHO_LOTE_JDBC = 1000;

    /**
     * Tipo do lote; nome nas mensagens ("notas de saída") e do documento ("Nota"); tabelas do
     * axctg3 e do lote; coluna da data que delimita o período; colunas gravadas do cabeçalho e
     * do item, na ordem; colunas fixas da inclusão (ex.: FIN_NFE = 1); prefixo da sequence.
     */
    record Config(TipoLote tipo, String nome, String documento, String tabela, String tabelaItem, String colunaPai,
                  String tabelaImp, String tabelaImpItem, String colunaData, List<String> colunas,
                  List<String> colunasItem, Map<String, Object> fixasNaInclusao, String prefixoSequence) {
    }

    /** Documento já com os ids do axctg3 — o do lote ou o gravado; cabeçalho e itens por coluna. */
    static class Documento {
        final Integer numero;
        final Map<String, Object> cabecalho;
        final List<Map<String, Object>> itens;
        /** Para o resumo: valor do documento e se é cancelado (entra, mas não soma). */
        BigDecimal valor = BigDecimal.ZERO;
        boolean cancelado;
        UUID id;
        boolean trocarItens;

        Documento(Integer numero, Map<String, Object> cabecalho, List<Map<String, Object>> itens) {
            this.numero = numero;
            this.cabecalho = cabecalho;
            this.itens = itens;
        }
    }

    /**
     * O que a conversão de uma linha consulta: cadastros da empresa, o relatório e um estado da
     * execução, para o serviço juntar o que relata em {@link #concluirConversao}.
     */
    record Contexto(ImpLote lote, ImportacaoDocumentos.Cadastros cadastros, ImportacaoPlanoContasService.Relatorio relatorio,
                    Map<String, Object> estado) {
    }

    protected final DataManager dataManager;
    protected final JdbcTemplate jdbc;
    private final Sequences sequences;
    private final Config c;

    protected ImportacaoDocumentoBase(DataManager dataManager, DataSource dataSource, Sequences sequences, Config config) {
        this.dataManager = dataManager;
        this.jdbc = new JdbcTemplate(dataSource);
        this.sequences = sequences;
        this.c = config;
    }

    /**
     * Linha do lote (cabeçalho e itens crus, como {@link ImportacaoDocumentos#linha}) já traduzida;
     * null se o documento tiver erro (registrado no relatório) ou ficar de fora (com aviso).
     */
    protected abstract Documento converter(Map<String, Object> linha, List<Map<String, Object>> itens, Contexto ctx);

    /** Avisos que só fazem sentido depois de converter o lote inteiro. */
    protected void concluirConversao(Contexto ctx) {
    }

    /** Maior número já usado pela empresa, de onde a numeração continua. */
    protected Integer maiorNumero(Integer codEmpresa) {
        return jdbc.queryForObject("select max(NUMERO) from " + c.tabela() + " where COD_EMPRESA = ? "
                + "and DELETED_DATE is null", Integer.class, codEmpresa);
    }

    /** Confere o lote e o compara com os documentos da empresa. Só grava o relatório no lote. */
    @Transactional
    public ImportacaoPlanoContasService.Relatorio conferir(UUID loteId) {
        return executar(loteId, false);
    }

    /** Confere e, sem erro, cria os novos e atualiza os alterados. Tudo ou nada. */
    @Transactional
    public ImportacaoPlanoContasService.Relatorio importar(UUID loteId) {
        return executar(loteId, true);
    }

    private ImportacaoPlanoContasService.Relatorio executar(UUID loteId, boolean paraImportar) {
        ImpLote lote = dataManager.load(ImpLote.class).id(loteId).one();
        ImportacaoPlanoContasService.Relatorio relatorio = new ImportacaoPlanoContasService.Relatorio();
        ImportacaoDocumentos.validarLotePorPeriodo(jdbc, lote, c.tipo(), c.nome(), relatorio, paraImportar);
        Execucao execucao = null;
        if (relatorio.erros.isEmpty()) {
            execucao = new Execucao(lote, relatorio);
            execucao.conferir();
        }
        boolean importado = paraImportar && relatorio.erros.isEmpty();
        if (importado) {
            execucao.gravar();
            relatorio.importadas = execucao.novos.size() + execucao.alterados.size();
            ajustarSequence(lote, relatorio);
        }
        String resumo = "";
        if (execucao != null && relatorio.erros.isEmpty()) {
            resumo = execucao.novos.size() + (importado ? " criados, " : " a criar, ")
                    + execucao.alterados.size() + (importado ? " alterados, " : " a alterar, ")
                    + execucao.iguais + " iguais.\n"
                    + execucao.documentos + " " + c.nome() + " de " + lote.getPeriodo()
                    + (execucao.cancelados > 0 ? " (" + execucao.cancelados + " cancelados)" : "")
                    + ", " + execucao.itens + " itens, valor " + execucao.totalValor + ".\n";
        }
        ImportacaoDocumentos.gravarRelatorio(dataManager, lote, relatorio, resumo, importado);
        return relatorio;
    }

    private void ajustarSequence(ImpLote lote, ImportacaoPlanoContasService.Relatorio relatorio) {
        Integer maior = maiorNumero(lote.getCodEmpresa());
        if (maior == null || maior == 0) {
            return;
        }
        String nome = c.prefixoSequence() + lote.getCodEmpresa();
        try {
            sequences.setCurrentValue(Sequence.withName(nome), maior);
        } catch (RuntimeException e) {
            relatorio.avisos.add("Sequence " + nome + " não foi ajustada para " + maior
                    + " (ajuste em Administração > Sequência): " + e.getMessage());
        }
    }

    /** Conferência e gravação de um lote; guarda o que a conferência montou para a gravação. */
    private class Execucao {
        private final ImpLote lote;
        private final ImportacaoPlanoContasService.Relatorio relatorio;
        private final Integer codEmpresa;

        final List<Documento> novos = new ArrayList<>();
        final List<Documento> alterados = new ArrayList<>();
        int iguais;
        int documentos;
        int cancelados;
        int itens;
        BigDecimal totalValor = BigDecimal.ZERO;

        Execucao(ImpLote lote, ImportacaoPlanoContasService.Relatorio relatorio) {
            this.lote = lote;
            this.relatorio = relatorio;
            this.codEmpresa = lote.getCodEmpresa();
        }

        void conferir() {
            ImportacaoDocumentos.Cadastros cadastros = new ImportacaoDocumentos.Cadastros(jdbc, codEmpresa);
            cadastros.exigir(relatorio, c.nome());
            if (!relatorio.erros.isEmpty()) {
                return;
            }
            Map<Integer, List<Map<String, Object>>> itensPorPai = new HashMap<>();
            jdbc.query("select * from " + c.tabelaImpItem() + " where LOTE_ID = ? order by PAI, REGISTRO",
                    rs -> {
                        Map<String, Object> l = ImportacaoDocumentos.linha(rs);
                        itensPorPai.computeIfAbsent((Integer) l.get("PAI"), k -> new ArrayList<>()).add(l);
                    }, lote.getId());
            List<Map<String, Object>> linhas = jdbc.query("select * from " + c.tabelaImp()
                    + " where LOTE_ID = ? order by NUMERO", (rs, i) -> ImportacaoDocumentos.linha(rs), lote.getId());
            if (linhas.isEmpty()) {
                relatorio.erros.add("O lote não tem nenhum documento.");
                return;
            }
            Contexto ctx = new Contexto(lote, cadastros, relatorio, new HashMap<>());
            Map<Integer, Documento> doLote = new LinkedHashMap<>();
            for (Map<String, Object> l : linhas) {
                Documento d = converter(l, itensPorPai.getOrDefault((Integer) l.get("REGISTRO"), List.of()), ctx);
                if (d == null) {
                    continue;
                }
                if (doLote.containsKey(d.numero)) {
                    relatorio.erros.add(c.documento() + " " + d.numero + " repetido(a) no lote.");
                    continue;
                }
                doLote.put(d.numero, d);
                documentos++;
                itens += d.itens.size();
                if (d.cancelado) {
                    cancelados++;
                } else {
                    totalValor = totalValor.add(d.valor);
                }
            }
            cadastros.relatar(relatorio, c.nome());
            concluirConversao(ctx);
            if (!relatorio.erros.isEmpty()) {
                return;
            }
            comparar(doLote);
        }

        /** Separa novos, alterados e iguais; documento do axctg3 que não veio do legado impede. */
        private void comparar(Map<Integer, Documento> doLote) {
            // pela faixa de números do lote, filtrando em Java: o número no lote pode ser texto
            // (pedido do legado), e comparar com a coluna inteira do axctg3 falharia no Postgres
            int menor = doLote.keySet().stream().min(Integer::compare).orElse(0);
            int maior = doLote.keySet().stream().max(Integer::compare).orElse(0);
            String doLoteSql = "between ? and ?";
            Map<Integer, Documento> gravados = new HashMap<>();
            Map<UUID, Documento> porId = new HashMap<>();
            Map<Integer, String> criadoPor = new HashMap<>();
            jdbc.query("select ID, NUMERO, CREATED_BY, " + String.join(", ", c.colunas()) + " from " + c.tabela()
                            + " where COD_EMPRESA = ? and DELETED_DATE is null and NUMERO " + doLoteSql,
                    rs -> {
                        Map<String, Object> l = ImportacaoDocumentos.linha(rs);
                        Integer numero = (Integer) l.get("NUMERO");
                        if (!doLote.containsKey(numero)) {
                            return;
                        }
                        if (gravados.containsKey(numero)) {
                            relatorio.erros.add(c.documento() + " " + numero + " existe mais de uma vez no axctg3.");
                            return;
                        }
                        Map<String, Object> cab = new LinkedHashMap<>();
                        c.colunas().forEach(col -> cab.put(col, l.get(col)));
                        Documento d = new Documento(numero, cab, new ArrayList<>());
                        d.id = (UUID) l.get("ID");
                        gravados.put(numero, d);
                        porId.put(d.id, d);
                        criadoPor.put(numero, (String) l.get("CREATED_BY"));
                    }, codEmpresa, menor, maior);
            Set<UUID> comItemDoAxctg3 = new HashSet<>();
            jdbc.query("select it." + c.colunaPai() + ", it.CREATED_BY, it." + String.join(", it.", c.colunasItem())
                            + " from " + c.tabelaItem() + " it join " + c.tabela() + " d on d.ID = it." + c.colunaPai()
                            + " where d.COD_EMPRESA = ? and d.DELETED_DATE is null and it.DELETED_DATE is null "
                            + "and d.NUMERO " + doLoteSql + " order by it." + c.colunaPai() + ", it.ITEM",
                    rs -> {
                        Map<String, Object> l = ImportacaoDocumentos.linha(rs);
                        Documento d = porId.get((UUID) l.get(c.colunaPai()));
                        if (d == null) {
                            return;
                        }
                        Map<String, Object> item = new LinkedHashMap<>();
                        c.colunasItem().forEach(col -> item.put(col, l.get(col)));
                        d.itens.add(item);
                        if (!IMPORTACAO.equals(l.get("CREATED_BY"))) {
                            comItemDoAxctg3.add(d.id);
                        }
                    }, codEmpresa, menor, maior);

            List<String> alteracoes = new ArrayList<>();
            for (Documento d : doLote.values()) {
                Documento g = gravados.get(d.numero);
                if (g == null) {
                    novos.add(d);
                    continue;
                }
                if (!IMPORTACAO.equals(criadoPor.get(d.numero))) {
                    relatorio.erros.add(c.documento() + " " + d.numero
                            + " já existe no axctg3 e não veio da importação do legado.");
                    continue;
                }
                if (comItemDoAxctg3.contains(g.id)) {
                    relatorio.erros.add(c.documento() + " " + d.numero
                            + " tem item incluído no axctg3; a importação apagaria esse item.");
                    continue;
                }
                List<String> mudancas = ImportacaoDocumentos.diferencas(d.cabecalho, g.cabecalho);
                d.trocarItens = !ImportacaoDocumentos.mesmasLinhas(d.itens, g.itens);
                if (d.trocarItens) {
                    mudancas.add("itens substituídos (" + g.itens.size() + " → " + d.itens.size() + ")");
                }
                if (mudancas.isEmpty()) {
                    iguais++;
                } else {
                    d.id = g.id;
                    alteracoes.add(c.documento() + " " + d.numero + ": " + String.join("; ", mudancas) + ".");
                    alterados.add(d);
                }
            }
            relatorio.avisos.addAll(alteracoes);

            LocalDate inicio = LocalDate.of(lote.getAno(), lote.getMes() == null ? 1 : lote.getMes(), 1);
            LocalDate fim = lote.getMes() == null ? inicio.plusYears(1) : inicio.plusMonths(1);
            List<Integer> soNoAxctg3 = jdbc.queryForList("select d.NUMERO from " + c.tabela() + " d "
                            + "where d.COD_EMPRESA = ? and d.DELETED_DATE is null and d.CREATED_BY = ? "
                            + "and d." + c.colunaData() + " >= ? and d." + c.colunaData() + " < ? "
                            + "order by d.NUMERO", Integer.class,
                    codEmpresa, IMPORTACAO, inicio, fim).stream()
                    .filter(numero -> !doLote.containsKey(numero))
                    .toList();
            if (!soNoAxctg3.isEmpty()) {
                relatorio.avisos.add(soNoAxctg3.size() + " " + c.nome() + " de " + lote.getPeriodo()
                        + " importados antes não vieram neste lote (ficam como estão): "
                        + ImportacaoDocumentos.resumir(soNoAxctg3) + ".");
            }
        }

        void gravar() {
            OffsetDateTime agora = OffsetDateTime.now();
            List<Object[]> cabecalhosNovos = new ArrayList<>();
            List<Object[]> itensNovos = new ArrayList<>();
            for (Documento d : novos) {
                d.id = UuidProvider.createUuid();
                List<Object> v = new ArrayList<>(List.of(d.id, IMPORTACAO, agora, d.numero, codEmpresa));
                v.addAll(c.fixasNaInclusao().values());
                c.colunas().forEach(col -> v.add(d.cabecalho.get(col)));
                cabecalhosNovos.add(v.toArray());
                d.itens.forEach(i -> itensNovos.add(linhaItem(d.id, i, agora)));
            }
            List<Object[]> cabecalhosAlterados = new ArrayList<>();
            List<Object[]> itensApagados = new ArrayList<>();
            for (Documento d : alterados) {
                List<Object> v = new ArrayList<>();
                c.colunas().forEach(col -> v.add(d.cabecalho.get(col)));
                v.addAll(List.of(IMPORTACAO, agora, d.id));
                cabecalhosAlterados.add(v.toArray());
                if (d.trocarItens) {
                    itensApagados.add(new Object[]{IMPORTACAO, agora, d.id});
                    d.itens.forEach(i -> itensNovos.add(linhaItem(d.id, i, agora)));
                }
            }
            List<String> fixas = new ArrayList<>(c.fixasNaInclusao().keySet());
            emLotes("insert into " + c.tabela() + " (ID, VERSION, CREATED_BY, CREATED_DATE, NUMERO, COD_EMPRESA, "
                    + (fixas.isEmpty() ? "" : String.join(", ", fixas) + ", ") + String.join(", ", c.colunas())
                    + ") values (?, 1, ?, ?, ?, ?, " + marcadores(fixas.size() + c.colunas().size()) + ")",
                    cabecalhosNovos);
            emLotes("update " + c.tabela() + " set " + String.join(" = ?, ", c.colunas()) + " = ?, "
                    + "LAST_MODIFIED_BY = ?, LAST_MODIFIED_DATE = ?, VERSION = VERSION + 1 where ID = ?",
                    cabecalhosAlterados);
            emLotes("update " + c.tabelaItem() + " set DELETED_BY = ?, DELETED_DATE = ? "
                    + "where " + c.colunaPai() + " = ? and DELETED_DATE is null", itensApagados);
            emLotes("insert into " + c.tabelaItem() + " (ID, VERSION, CREATED_BY, CREATED_DATE, " + c.colunaPai() + ", "
                    + String.join(", ", c.colunasItem()) + ") values (?, 1, ?, ?, ?, "
                    + marcadores(c.colunasItem().size()) + ")", itensNovos);
        }

        private Object[] linhaItem(UUID pai, Map<String, Object> item, OffsetDateTime agora) {
            List<Object> v = new ArrayList<>(List.of(UuidProvider.createUuid(), IMPORTACAO, agora, pai));
            c.colunasItem().forEach(col -> v.add(item.get(col)));
            return v.toArray();
        }

        private void emLotes(String sql, List<Object[]> linhas) {
            for (int i = 0; i < linhas.size(); i += TAMANHO_LOTE_JDBC) {
                jdbc.batchUpdate(sql, linhas.subList(i, Math.min(i + TAMANHO_LOTE_JDBC, linhas.size())));
            }
        }
    }

    private static String marcadores(int quantidade) {
        return String.join(", ", Collections.nCopies(quantidade, "?"));
    }
}
