package br.com.axialsoftware.axctg3.service.importacao;

import br.com.axialsoftware.axctg3.entity.enums.SituacaoLote;
import br.com.axialsoftware.axctg3.entity.enums.TipoLote;
import br.com.axialsoftware.axctg3.entity.importacao.ImpLote;
import io.jmix.core.DataManager;
import io.jmix.core.UuidProvider;
import io.jmix.data.Sequence;
import io.jmix.data.Sequences;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Importa um {@link ImpLote} de movimento bancário do legado (MOVBANCO) para a empresa do lote,
 * por ano ou por mês da data do movimento — no desenho dos títulos
 * ({@link ImportacaoTitulosReceberService}): JDBC em lote, sem DataManager nem o listener de
 * {@code MovimentoBanco} (que trocaria a empresa pela da sessão e daria outro número).
 * <p>
 * O número do lançamento é o REGISTRO do legado (como fazia {@code exportacao.ExportacaoMovimentoBanco})
 * e a sequence {@code movimento_banco_seq_<empresa>} vai para o maior número da empresa depois da
 * gravação. Cada movimento ganha o item 1 já lançado na contabilidade: o movimento importado é
 * histórico, a contabilidade do período vem da importação de lançamentos.
 * <p>
 * Reimportação: o que já veio do legado recebe os dados de novo; movimento com o mesmo número
 * criado no próprio axctg3 impede a importação. Movimento sem valor é ignorado, com aviso.
 */
@Service
public class ImportacaoMovimentoBancoService {

    /** Marca em CREATED_BY do que a importação grava (a mesma dos títulos). */
    public static final String IMPORTACAO = ImportacaoTitulosReceberService.IMPORTACAO;

    private static final int LIMITE_LINHAS_SECAO = 300;
    private static final int TAMANHO_LOTE_JDBC = 1000;

    private final DataManager dataManager;
    private final JdbcTemplate jdbc;
    private final Sequences sequences;

    public ImportacaoMovimentoBancoService(DataManager dataManager, DataSource dataSource, Sequences sequences) {
        this.dataManager = dataManager;
        this.jdbc = new JdbcTemplate(dataSource);
        this.sequences = sequences;
    }

    /** Confere o lote e o compara com o movimento da empresa. Só grava o relatório no lote. */
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
        Execucao execucao = null;
        validarLote(lote, relatorio, paraImportar);
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
        gravarRelatorio(lote, relatorio, execucao, importado);
        return relatorio;
    }

    private void validarLote(ImpLote lote, ImportacaoPlanoContasService.Relatorio relatorio, boolean paraImportar) {
        if (lote.getTipo() != TipoLote.MOVIMENTO_BANCO) {
            relatorio.erros.add("O lote não é de movimento bancário.");
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

    /** Põe a sequence de numeração da empresa no maior número de movimento que ela tem. */
    private void ajustarSequence(ImpLote lote, ImportacaoPlanoContasService.Relatorio relatorio) {
        Integer maior = jdbc.queryForObject("select max(LANCAMENTO) from MOVIMENTO_BANCO where COD_EMPRESA = ?",
                Integer.class, lote.getCodEmpresa());
        if (maior == null) {
            return;
        }
        String nome = "movimento_banco_seq_" + lote.getCodEmpresa();
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

        private final Map<Integer, UUID> bancos = new HashMap<>();
        private final Map<Integer, UUID> historicos = new HashMap<>();
        private final Map<String, UUID> contas = new HashMap<>();
        private Integer anoPlano;

        final List<Movimento> novos = new ArrayList<>();
        final List<Movimento> alterados = new ArrayList<>();
        int iguais;
        int movimentos;
        BigDecimal entradas = BigDecimal.ZERO;
        BigDecimal saidas = BigDecimal.ZERO;

        Execucao(ImpLote lote, ImportacaoPlanoContasService.Relatorio relatorio) {
            this.lote = lote;
            this.relatorio = relatorio;
            this.codEmpresa = lote.getCodEmpresa();
        }

        void conferir() {
            jdbc.query("select CODIGO, ID from BANCO where COD_EMPRESA = ? and DELETED_DATE is null",
                    rs -> { bancos.put(rs.getInt(1), uuid(rs, 2)); }, codEmpresa);
            jdbc.query("select CODIGO, ID from HISTORICO_FINANCEIRO where COD_EMPRESA = ? and DELETED_DATE is null",
                    rs -> { historicos.put(rs.getInt(1), uuid(rs, 2)); }, codEmpresa);
            Integer comPlano = jdbc.queryForObject("select count(*) from CONTA_CONTABIL "
                            + "where COD_EMPRESA = ? and ANO = ? and DELETED_DATE is null", Integer.class,
                    codEmpresa, lote.getAno());
            anoPlano = comPlano != null && comPlano > 0 ? lote.getAno()
                    : jdbc.queryForObject("select max(ANO) from CONTA_CONTABIL "
                    + "where COD_EMPRESA = ? and DELETED_DATE is null", Integer.class, codEmpresa);
            if (anoPlano != null) {
                jdbc.query("select CODIGO, ID from CONTA_CONTABIL where COD_EMPRESA = ? and ANO = ? and DELETED_DATE is null",
                        rs -> { contas.put(rs.getString(1), uuid(rs, 2)); }, codEmpresa, anoPlano);
            }
            if (bancos.isEmpty()) {
                relatorio.erros.add("A empresa " + codEmpresa + " não tem bancos. Importe antes os bancos.");
                return;
            }

            List<ImpMovimento> linhas = jdbc.query("select REGISTRO, DATA, HISTORICO, BANCO, DOCUMENTO, DESCRICAO, "
                            + "VALOR, CHEQUE, ENTRADA, TIPO_SALDO, COD_CONTABIL "
                            + "from IMP_MOVIMENTO_BANCO where LOTE_ID = ? order by REGISTRO",
                    (rs, i) -> new ImpMovimento(inteiro(rs, 1), data(rs, 2), inteiro(rs, 3), inteiro(rs, 4),
                            rs.getString(5), rs.getString(6), rs.getBigDecimal(7), inteiro(rs, 8), rs.getString(9),
                            rs.getString(10), rs.getString(11)),
                    lote.getId());
            if (linhas.isEmpty()) {
                relatorio.erros.add("O lote não tem nenhum movimento.");
                return;
            }

            Map<Integer, Movimento> doLote = new LinkedHashMap<>();
            Map<Integer, Integer> bancosNaoAchados = new TreeMap<>();
            Map<Integer, Integer> historicosNaoAchados = new TreeMap<>();
            Map<String, Integer> contasNaoAchadas = new TreeMap<>();
            int semValor = 0;
            for (ImpMovimento l : linhas) {
                String id = "Movimento " + (l.registro() == null ? "(sem registro)" : l.registro());
                if (l.registro() == null) {
                    relatorio.erros.add(id + ".");
                    continue;
                }
                if (doLote.containsKey(l.registro())) {
                    relatorio.erros.add(id + " repetido no lote.");
                    continue;
                }
                if (l.valor() == null) {
                    semValor++;
                    continue;
                }
                if (l.data() == null) {
                    relatorio.erros.add(id + " sem data.");
                    continue;
                }
                UUID banco = l.banco() == null ? null : bancos.get(l.banco());
                if (banco == null) {
                    bancosNaoAchados.merge(l.banco(), 1, Integer::sum);
                    continue;
                }
                UUID historico = null;
                if (l.historico() != null && l.historico() != 0) {
                    historico = historicos.get(l.historico());
                    if (historico == null) {
                        historicosNaoAchados.merge(l.historico(), 1, Integer::sum);
                        continue;
                    }
                }
                String codContabil = texto(l.codContabil());
                UUID conta = codContabil == null ? null : contas.get(codContabil);
                if (codContabil != null && conta == null) {
                    contasNaoAchadas.merge(codContabil, 1, Integer::sum);
                }
                if (l.data().getYear() < 1990 || l.data().getYear() > lote.getAno() + 10) {
                    relatorio.avisos.add(id + ": data " + l.data() + " (digitada errada no legado?).");
                }
                boolean entrada = sim(l.entrada());
                Movimento m = new Movimento(l.registro(), l.data(), banco, historico, l.valor(),
                        cortar(texto(l.documento()), 20, id, "documento"), cortar(texto(l.descricao()), 30, id, "descrição"),
                        l.cheque() == null || l.cheque() == 0 ? null : l.cheque(), conta, entrada, sim(l.tipoSaldo()));
                doLote.put(l.registro(), m);
                movimentos++;
                if (!m.tipoSaldo) {
                    if (entrada) {
                        entradas = entradas.add(l.valor());
                    } else {
                        saidas = saidas.add(l.valor());
                    }
                }
            }
            bancosNaoAchados.forEach((codigo, qtd) -> relatorio.erros.add("Banco " + codigo
                    + " não existe na empresa (" + qtd + " movimentos). Importe antes os bancos."));
            historicosNaoAchados.forEach((codigo, qtd) -> relatorio.erros.add("Histórico financeiro " + codigo
                    + " não existe na empresa (" + qtd + " movimentos). Importe antes os históricos financeiros."));
            contasNaoAchadas.forEach((codigo, qtd) -> relatorio.avisos.add("Conta contábil " + codigo
                    + " não existe no plano de " + anoPlano + " (" + qtd + " movimentos): ficam sem conta."));
            if (semValor > 0) {
                relatorio.avisos.add(semValor + " movimento(s) sem valor no legado ignorado(s).");
            }
            if (!relatorio.erros.isEmpty()) {
                return;
            }
            comparar(doLote);
        }

        private void comparar(Map<Integer, Movimento> doLote) {
            Map<Integer, Gravado> gravados = new HashMap<>();
            jdbc.query("select m.ID, m.LANCAMENTO, m.CREATED_BY, m.DATA, m.BANCO_ID, m.HISTORICO_FINANCEIRO_ID, "
                            + "m.VALOR, m.DOCUMENTO, m.DESCRICAO, m.CHEQUE, m.CONTA_CONTABIL_ID, m.ENTRADA, m.TIPO_SALDO "
                            + "from MOVIMENTO_BANCO m where m.COD_EMPRESA = ? and m.DELETED_DATE is null "
                            + "and m.LANCAMENTO in (select i.REGISTRO from IMP_MOVIMENTO_BANCO i where i.LOTE_ID = ?)",
                    rs -> {
                        Movimento m = new Movimento(rs.getInt(2), data(rs, 4), uuid(rs, 5), uuid(rs, 6),
                                rs.getBigDecimal(7), texto(rs.getString(8)), texto(rs.getString(9)), inteiro(rs, 10),
                                uuid(rs, 11), rs.getBoolean(12), rs.getBoolean(13));
                        gravados.put(m.lancamento, new Gravado(uuid(rs, 1), rs.getString(3), m));
                    },
                    codEmpresa, lote.getId());

            for (Movimento m : doLote.values()) {
                Gravado g = gravados.get(m.lancamento);
                if (g == null) {
                    novos.add(m);
                    continue;
                }
                if (!IMPORTACAO.equals(g.criadoPor())) {
                    relatorio.erros.add("Movimento " + m.lancamento
                            + " já existe no axctg3 e não veio da importação do legado.");
                    continue;
                }
                List<String> mudancas = m.diferencas(g.movimento());
                if (mudancas.isEmpty()) {
                    iguais++;
                } else {
                    m.id = g.id();
                    relatorio.avisos.add("Movimento " + m.lancamento + ": " + String.join("; ", mudancas) + ".");
                    alterados.add(m);
                }
            }

            LocalDate inicio = LocalDate.of(lote.getAno(), lote.getMes() == null ? 1 : lote.getMes(), 1);
            LocalDate fim = lote.getMes() == null ? inicio.plusYears(1) : inicio.plusMonths(1);
            List<Integer> soNoAxctg3 = jdbc.queryForList("select m.LANCAMENTO from MOVIMENTO_BANCO m "
                            + "where m.COD_EMPRESA = ? and m.DELETED_DATE is null and m.DATA >= ? and m.DATA < ? "
                            + "and m.LANCAMENTO not in (select i.REGISTRO from IMP_MOVIMENTO_BANCO i where i.LOTE_ID = ?) "
                            + "order by m.LANCAMENTO", Integer.class,
                    codEmpresa, inicio, fim, lote.getId());
            if (!soNoAxctg3.isEmpty()) {
                List<String> numeros = soNoAxctg3.subList(0, Math.min(50, soNoAxctg3.size())).stream()
                        .map(String::valueOf).toList();
                relatorio.avisos.add(soNoAxctg3.size() + " movimento(s) de " + lote.getPeriodo()
                        + " existem no axctg3 e não vieram no lote (ficam como estão): "
                        + String.join(", ", numeros) + (soNoAxctg3.size() > 50 ? "..." : "") + ".");
            }
        }

        void gravar() {
            OffsetDateTime agora = OffsetDateTime.now();
            List<Object[]> inserir = new ArrayList<>();
            List<Object[]> itens = new ArrayList<>();
            for (Movimento m : novos) {
                m.id = UuidProvider.createUuid();
                inserir.add(new Object[]{m.id, IMPORTACAO, agora, m.lancamento, codEmpresa, m.data, m.banco,
                        m.historico, m.valor, m.documento, m.descricao, m.cheque, m.conta, m.entrada, m.tipoSaldo});
                itens.add(new Object[]{UuidProvider.createUuid(), IMPORTACAO, agora, m.id, Boolean.TRUE});
            }
            List<Object[]> atualizar = new ArrayList<>();
            for (Movimento m : alterados) {
                atualizar.add(new Object[]{m.data, m.banco, m.historico, m.valor, m.documento, m.descricao, m.cheque,
                        m.conta, m.entrada, m.tipoSaldo, IMPORTACAO, agora, m.id});
            }
            emLotes("insert into MOVIMENTO_BANCO (ID, VERSION, CREATED_BY, CREATED_DATE, LANCAMENTO, COD_EMPRESA, "
                    + "DATA, BANCO_ID, HISTORICO_FINANCEIRO_ID, VALOR, DOCUMENTO, DESCRICAO, CHEQUE, CONTA_CONTABIL_ID, "
                    + "ENTRADA, TIPO_SALDO) values (?, 1, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", inserir);
            emLotes("insert into ITEM_MOVIMENTO_BANCO (ID, VERSION, CREATED_BY, CREATED_DATE, MOVIMENTO_BANCO_ID, "
                    + "ITEM, LANCADO) values (?, 1, ?, ?, ?, 1, ?)", itens);
            emLotes("update MOVIMENTO_BANCO set DATA = ?, BANCO_ID = ?, HISTORICO_FINANCEIRO_ID = ?, VALOR = ?, "
                    + "DOCUMENTO = ?, DESCRICAO = ?, CHEQUE = ?, CONTA_CONTABIL_ID = ?, ENTRADA = ?, TIPO_SALDO = ?, "
                    + "LAST_MODIFIED_BY = ?, LAST_MODIFIED_DATE = ?, VERSION = VERSION + 1 where ID = ?", atualizar);
        }

        private void emLotes(String sql, List<Object[]> linhas) {
            for (int i = 0; i < linhas.size(); i += TAMANHO_LOTE_JDBC) {
                jdbc.batchUpdate(sql, linhas.subList(i, Math.min(i + TAMANHO_LOTE_JDBC, linhas.size())));
            }
        }

        private String cortar(String valor, int tamanho, String id, String campo) {
            if (valor == null || valor.length() <= tamanho) {
                return valor;
            }
            relatorio.avisos.add(id + ": " + campo + " cortado em " + tamanho + " caracteres (" + valor + ").");
            return valor.substring(0, tamanho).trim();
        }
    }

    private void gravarRelatorio(ImpLote lote, ImportacaoPlanoContasService.Relatorio relatorio, Execucao e,
                                 boolean importado) {
        StringBuilder sb = new StringBuilder();
        sb.append(importado ? "IMPORTADO em " : "Conferido em ").append(OffsetDateTime.now().withNano(0)).append('\n');
        if (e != null && relatorio.erros.isEmpty()) {
            sb.append(e.novos.size()).append(importado ? " criados, " : " a criar, ")
                    .append(e.alterados.size()).append(importado ? " alterados, " : " a alterar, ")
                    .append(e.iguais).append(" iguais.\n");
            sb.append(e.movimentos).append(" movimentos de ").append(lote.getPeriodo()).append(": entradas ")
                    .append(e.entradas).append(", saídas ").append(e.saidas)
                    .append(" (sem os registros de saldo).\n");
        }
        secao(sb, "ERROS (impedem a importação)", relatorio.erros);
        secao(sb, "AVISOS", relatorio.avisos);
        lote.setMensagem(sb.toString());
        if (importado) {
            lote.setSituacao(SituacaoLote.IMPORTADO);
            lote.setDataImportacao(OffsetDateTime.now());
        }
        dataManager.saveWithoutReload(lote);
    }

    private void secao(StringBuilder sb, String titulo, List<String> linhas) {
        if (linhas.isEmpty()) {
            return;
        }
        sb.append('\n').append(titulo).append(" (").append(linhas.size()).append(")\n");
        linhas.stream().limit(LIMITE_LINHAS_SECAO).forEach(linha -> sb.append("  - ").append(linha).append('\n'));
        if (linhas.size() > LIMITE_LINHAS_SECAO) {
            sb.append("  ... e mais ").append(linhas.size() - LIMITE_LINHAS_SECAO).append('\n');
        }
    }

    private static String texto(String valor) {
        if (valor == null) {
            return null;
        }
        String t = valor.trim();
        return t.isEmpty() ? null : t;
    }

    private static boolean sim(String marca) {
        return "S".equalsIgnoreCase(texto(marca));
    }

    private static Integer inteiro(ResultSet rs, int coluna) throws SQLException {
        int v = rs.getInt(coluna);
        return rs.wasNull() ? null : v;
    }

    private static LocalDate data(ResultSet rs, int coluna) throws SQLException {
        java.sql.Date d = rs.getDate(coluna);
        return d == null ? null : d.toLocalDate();
    }

    /** UUID lido do banco: o driver do Postgres devolve UUID, o HSQLDB dos testes pode devolver outro tipo. */
    private static UUID uuid(ResultSet rs, int coluna) throws SQLException {
        Object v = rs.getObject(coluna);
        if (v == null || v instanceof UUID) {
            return (UUID) v;
        }
        if (v instanceof byte[] b && b.length == 16) {
            java.nio.ByteBuffer bb = java.nio.ByteBuffer.wrap(b);
            return new UUID(bb.getLong(), bb.getLong());
        }
        return UUID.fromString(v.toString());
    }

    private record ImpMovimento(Integer registro, LocalDate data, Integer historico, Integer banco, String documento,
                                String descricao, BigDecimal valor, Integer cheque, String entrada, String tipoSaldo,
                                String codContabil) {
    }

    private record Gravado(UUID id, String criadoPor, Movimento movimento) {
    }

    /** Movimento já com os ids do axctg3 — o do lote ou o gravado. */
    private static class Movimento {
        final Integer lancamento;
        final LocalDate data;
        final UUID banco;
        final UUID historico;
        final BigDecimal valor;
        final String documento;
        final String descricao;
        final Integer cheque;
        final UUID conta;
        final boolean entrada;
        final boolean tipoSaldo;
        UUID id;

        Movimento(Integer lancamento, LocalDate data, UUID banco, UUID historico, BigDecimal valor, String documento,
                  String descricao, Integer cheque, UUID conta, boolean entrada, boolean tipoSaldo) {
            this.lancamento = lancamento;
            this.data = data;
            this.banco = banco;
            this.historico = historico;
            this.valor = valor;
            this.documento = documento;
            this.descricao = descricao;
            this.cheque = cheque;
            this.conta = conta;
            this.entrada = entrada;
            this.tipoSaldo = tipoSaldo;
        }

        List<String> diferencas(Movimento g) {
            List<String> d = new ArrayList<>();
            if (!Objects.equals(data, g.data)) {
                d.add("data " + g.data + " passa a " + data);
            }
            if (!Objects.equals(banco, g.banco)) {
                d.add("banco trocado");
            }
            if (!Objects.equals(historico, g.historico)) {
                d.add("histórico trocado");
            }
            if (g.valor == null || valor.compareTo(g.valor) != 0) {
                d.add("valor " + g.valor + " passa a " + valor);
            }
            if (!Objects.equals(documento, g.documento)) {
                d.add("documento " + g.documento + " passa a " + documento);
            }
            if (!Objects.equals(descricao, g.descricao)) {
                d.add("descrição \"" + g.descricao + "\" passa a \"" + descricao + "\"");
            }
            if (!Objects.equals(cheque, g.cheque)) {
                d.add("cheque " + g.cheque + " passa a " + cheque);
            }
            if (!Objects.equals(conta, g.conta)) {
                d.add("conta contábil trocada");
            }
            if (entrada != g.entrada) {
                d.add(entrada ? "passa a entrada" : "passa a saída");
            }
            if (tipoSaldo != g.tipoSaldo) {
                d.add(tipoSaldo ? "passa a registro de saldo" : "deixa de ser registro de saldo");
            }
            return d;
        }
    }
}
