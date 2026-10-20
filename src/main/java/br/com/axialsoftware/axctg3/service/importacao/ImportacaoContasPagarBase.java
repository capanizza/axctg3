package br.com.axialsoftware.axctg3.service.importacao;

import br.com.axialsoftware.axctg3.entity.enums.SituacaoLote;
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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Importação de lotes de contas a pagar do legado — títulos a pagar (PAGAR/MOVPAGAR) e diversos a
 * pagar (DIVERSOS/MOVDIVERSOS), que têm a mesma forma no legado e no axctg3 e só mudam de tabela,
 * de tipo de lote e de sequence ({@link Config}). Cada um tem o seu serviço, que só escolhe a
 * configuração.
 * <p>
 * O mesmo desenho de {@link ImportacaoTitulosReceberService}: lote por ano ou por mês de emissão,
 * leitura e gravação por JDBC em lote (sem DataManager nem os listeners), tudo contabilizado,
 * emissão como item 1 (com o valor do título; emissão diferente vira divergência) e baixas pela
 * data, divergência quando a soma das baixas não bate com o PAGO do legado, e reimportação que
 * atualiza o título e troca os movimentos — mas nunca por cima de título criado no axctg3 ou de
 * movimento feito aqui.
 * <p>
 * Diferenças do receber: o título não tem banco, cada baixa tem o seu (banco 0 do legado = sem
 * banco, como nas emissões); o número é inteiro e vem de uma sequence por empresa, que é posta no
 * maior número da empresa depois da gravação, para o próximo título digitado no axctg3 não
 * repetir número.
 * <p>
 * Movimento sem histórico e sem valor (linha em branco digitada no legado) é ignorado, com aviso.
 */
public abstract class ImportacaoContasPagarBase {

    /** Marca em CREATED_BY dos títulos e itens gravados pela importação (a mesma do receber). */
    public static final String IMPORTACAO = ImportacaoTitulosReceberService.IMPORTACAO;

    private static final int LIMITE_LINHAS_SECAO = 300;
    private static final int TAMANHO_LOTE_JDBC = 1000;
    private static final int TAMANHO_DOCUMENTO = 20;

    /**
     * Onde gravar: tabelas do axctg3 e do lote, coluna do item que aponta o título, tipo de lote,
     * prefixo da sequence de numeração (+ empresa) e o nome do registro nas mensagens.
     */
    protected record Config(TipoLote tipo, String descricao, String nome, String tabela, String tabelaItem,
                            String colunaPai, String tabelaImp, String tabelaImpItem, String prefixoSequence) {
    }

    private final DataManager dataManager;
    private final JdbcTemplate jdbc;
    private final Sequences sequences;
    private final Config c;

    protected ImportacaoContasPagarBase(DataManager dataManager, DataSource dataSource, Sequences sequences,
                                        Config config) {
        this.dataManager = dataManager;
        this.jdbc = new JdbcTemplate(dataSource);
        this.sequences = sequences;
        this.c = config;
    }

    /** Confere o lote e o compara com os títulos da empresa. Só grava o relatório no lote. */
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
        if (lote.getTipo() != c.tipo()) {
            relatorio.erros.add("O lote não é de " + c.descricao() + ".");
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

    /**
     * Põe a sequence de numeração da empresa no maior número de título a pagar que ela tem
     * (importado ou não). No Postgres o próximo número sai depois dele.
     */
    private void ajustarSequence(ImpLote lote, ImportacaoPlanoContasService.Relatorio relatorio) {
        Integer maior = jdbc.queryForObject("select max(NUMERO) from " + c.tabela() + " where COD_EMPRESA = ?",
                Integer.class, lote.getCodEmpresa());
        if (maior == null) {
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

        private final Map<Long, UUID> parceiros = new HashMap<>();
        private final Map<Integer, UUID> bancos = new HashMap<>();
        private final Map<Integer, Historico> historicos = new HashMap<>();
        private final Map<String, UUID> contas = new HashMap<>();
        private Integer anoPlano;

        final List<Titulo> novos = new ArrayList<>();
        final List<Titulo> alterados = new ArrayList<>();
        int iguais;
        int titulos;
        BigDecimal totalValor = BigDecimal.ZERO;
        BigDecimal totalBaixado = BigDecimal.ZERO;
        int abertos;
        BigDecimal totalAberto = BigDecimal.ZERO;

        Execucao(ImpLote lote, ImportacaoPlanoContasService.Relatorio relatorio) {
            this.lote = lote;
            this.relatorio = relatorio;
            this.codEmpresa = lote.getCodEmpresa();
        }

        void conferir() {
            carregarCadastros();
            if (!relatorio.erros.isEmpty()) {
                return;
            }
            List<ImpTitulo> linhas = jdbc.query("select REGISTRO, NUMERO, DOCUMENTO, PARCEIRO, DATA_EMISSAO, "
                            + "DATA_VENCIMENTO, VALOR, PAGO, COD_CONTABIL, OBSERVACAO "
                            + "from " + c.tabelaImp() + " where LOTE_ID = ? order by NUMERO",
                    (rs, i) -> new ImpTitulo(inteiro(rs, 1), inteiro(rs, 2), rs.getString(3), inteiro(rs, 4),
                            data(rs, 5), data(rs, 6), rs.getBigDecimal(7), rs.getBigDecimal(8), rs.getString(9),
                            rs.getString(10)),
                    lote.getId());
            if (linhas.isEmpty()) {
                relatorio.erros.add("O lote não tem nenhum registro.");
                return;
            }
            Map<Integer, List<ImpMovimento>> movimentos = new HashMap<>();
            jdbc.query("select REGISTRO, PAI, DATA, HISTORICO, VALOR, JUROS, DESCONTO, BANCO "
                            + "from " + c.tabelaImpItem() + " where LOTE_ID = ?",
                    rs -> {
                        ImpMovimento m = new ImpMovimento(inteiro(rs, 1), inteiro(rs, 2), data(rs, 3), inteiro(rs, 4),
                                rs.getBigDecimal(5), rs.getBigDecimal(6), rs.getBigDecimal(7), inteiro(rs, 8));
                        movimentos.computeIfAbsent(m.pai(), k -> new ArrayList<>()).add(m);
                    },
                    lote.getId());

            Map<Integer, Titulo> doLote = new LinkedHashMap<>();
            Map<String, Integer> contasNaoAchadas = new TreeMap<>();
            Map<Integer, Integer> historicosNaoAchados = new TreeMap<>();
            Map<Integer, Integer> bancosNaoAchados = new TreeMap<>();
            int vazios = 0;
            for (ImpTitulo linha : linhas) {
                Titulo t = converter(linha, movimentos.getOrDefault(linha.registro(), List.of()),
                        contasNaoAchadas, historicosNaoAchados, bancosNaoAchados);
                if (t == null) {
                    continue;
                }
                vazios += t.movimentosVazios;
                if (doLote.containsKey(t.numero)) {
                    relatorio.erros.add(c.nome() + " " + t.numero + " repetido no lote.");
                    continue;
                }
                doLote.put(t.numero, t);
            }
            historicosNaoAchados.forEach((codigo, qtd) -> relatorio.erros.add("Histórico financeiro " + codigo
                    + " não existe na empresa (" + qtd + " movimentos). Importe antes os históricos financeiros."));
            bancosNaoAchados.forEach((codigo, qtd) -> relatorio.erros.add("Banco " + codigo
                    + " não existe na empresa (" + qtd + " movimentos). Importe antes os bancos."));
            contasNaoAchadas.forEach((codigo, qtd) -> relatorio.avisos.add("Conta contábil " + codigo
                    + " não existe no plano de " + anoPlano + " (" + qtd + " registros): ficam sem conta."));
            if (vazios > 0) {
                relatorio.avisos.add(vazios + " movimento(s) sem histórico e sem valor (linha em branco no legado) "
                        + "ignorado(s).");
            }
            if (!relatorio.erros.isEmpty()) {
                return;
            }
            comparar(doLote);
        }

        private void carregarCadastros() {
            jdbc.query("select CODIGO, ID from PARCEIRO where COD_EMPRESA = ? and DELETED_DATE is null",
                    rs -> { parceiros.put(rs.getLong(1), uuid(rs, 2)); }, codEmpresa);
            jdbc.query("select CODIGO, ID from BANCO where COD_EMPRESA = ? and DELETED_DATE is null",
                    rs -> { bancos.put(rs.getInt(1), uuid(rs, 2)); }, codEmpresa);
            jdbc.query("select CODIGO, ID, EMISSAO, BAIXA from HISTORICO_FINANCEIRO "
                            + "where COD_EMPRESA = ? and DELETED_DATE is null",
                    rs -> {
                        historicos.put(rs.getInt(1), new Historico(rs.getInt(1), uuid(rs, 2),
                                rs.getBoolean(3), rs.getBoolean(4)));
                    }, codEmpresa);
            // conta do plano do ano do lote; sem plano nesse ano, do mais recente
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
            if (parceiros.isEmpty()) {
                relatorio.erros.add("A empresa " + codEmpresa + " não tem parceiros. Importe antes os parceiros.");
            }
            if (historicos.isEmpty()) {
                relatorio.erros.add("A empresa " + codEmpresa
                        + " não tem históricos financeiros. Importe antes os históricos financeiros.");
            }
        }

        /** Título do legado já traduzido para os ids do axctg3; null se tiver erro. */
        private Titulo converter(ImpTitulo l, List<ImpMovimento> movimentos, Map<String, Integer> contasNaoAchadas,
                                 Map<Integer, Integer> historicosNaoAchados, Map<Integer, Integer> bancosNaoAchados) {
            String titulo = c.nome() + " " + (l.numero() == null ? "(registro " + l.registro() + ")" : l.numero());
            int erros = relatorio.erros.size();
            if (l.numero() == null) {
                relatorio.erros.add(titulo + " sem número.");
            }
            UUID parceiro = l.parceiro() == null ? null : parceiros.get(l.parceiro().longValue());
            if (parceiro == null) {
                relatorio.erros.add(titulo + ": parceiro " + l.parceiro() + " não existe na empresa.");
            }
            if (l.dataEmissao() == null || l.dataVencimento() == null) {
                relatorio.erros.add(titulo + " sem data de emissão ou de vencimento.");
            }
            if (l.valor() == null) {
                relatorio.erros.add(titulo + " sem valor.");
            }
            String documento = texto(l.documento());
            if (documento != null && documento.length() > TAMANHO_DOCUMENTO) {
                relatorio.avisos.add(titulo + ": documento cortado em " + TAMANHO_DOCUMENTO + " caracteres ("
                        + documento + ").");
                documento = documento.substring(0, TAMANHO_DOCUMENTO).trim();
            }
            String codContabil = texto(l.codContabil());
            UUID conta = codContabil == null ? null : contas.get(codContabil);
            if (codContabil != null && conta == null) {
                contasNaoAchadas.merge(codContabil, 1, Integer::sum);
            }

            List<ImpMovimento> ordenados = new ArrayList<>(movimentos);
            ordenados.sort(Comparator.comparing(ImpMovimento::data, Comparator.nullsFirst(Comparator.naturalOrder()))
                    .thenComparing(ImpMovimento::registro, Comparator.nullsFirst(Comparator.naturalOrder())));
            List<Item> itens = new ArrayList<>();
            Item emissao = null;
            boolean emissaoSemValor = false;
            int emissoes = 0;
            int vazios = 0;
            BigDecimal baixado = BigDecimal.ZERO;
            for (ImpMovimento m : ordenados) {
                if (m.historico() == null && (m.valor() == null || m.valor().signum() == 0)) {
                    vazios++;
                    continue;
                }
                Historico h = m.historico() == null ? null : historicos.get(m.historico());
                if (h == null) {
                    historicosNaoAchados.merge(m.historico(), 1, Integer::sum);
                    continue;
                }
                // emissão sem valor no legado: entra com o valor do título (ver abaixo)
                if (m.data() == null || (m.valor() == null && !h.emissao())) {
                    relatorio.erros.add(titulo + ": movimento sem data ou sem valor (histórico " + h.codigo() + ").");
                    continue;
                }
                UUID banco = null;
                if (m.banco() != null && m.banco() != 0) {
                    banco = bancos.get(m.banco());
                    if (banco == null) {
                        bancosNaoAchados.merge(m.banco(), 1, Integer::sum);
                        continue;
                    }
                }
                Item item = new Item(0, m.data(), h.id(), banco, zero(m.valor()), zero(m.juros()), zero(m.desconto()));
                if (h.emissao()) {
                    emissoes++;
                    emissao = item;
                    emissaoSemValor = m.valor() == null;
                    continue;
                }
                itens.add(item);
                if (h.baixa()) {
                    baixado = baixado.add(m.valor());
                }
            }
            if (emissoes > 1) {
                relatorio.erros.add(titulo + ": mais de um movimento de emissão no legado.");
            }
            if (relatorio.erros.size() > erros) {
                return null;
            }
            if (emissao == null) {
                Historico h = historicoEmissao();
                if (h == null) {
                    relatorio.erros.add(titulo + ": sem movimento de emissão no legado e a empresa não tem "
                            + "histórico financeiro de emissão.");
                    return null;
                }
                relatorio.avisos.add(titulo + ": sem movimento de emissão no legado; criado com a data e o valor do título.");
                emissao = new Item(0, l.dataEmissao(), h.id(), null, l.valor(), BigDecimal.ZERO, BigDecimal.ZERO);
            } else if (emissaoSemValor) {
                relatorio.divergencias.add(titulo + ": emissão sem valor no movimento; importada com o valor do título ("
                        + l.valor() + ").");
                emissao = emissao.comValor(l.valor());
            } else if (emissao.valor().compareTo(l.valor()) != 0) {
                // o item 1 acompanha o valor do título, como no axctg3 ao editar um título
                relatorio.divergencias.add(titulo + ": emissão de " + emissao.valor() + " no movimento e "
                        + l.valor() + " no título; importada com o valor do título.");
                emissao = emissao.comValor(l.valor());
            }
            BigDecimal pago = zero(l.pago());
            if (baixado.compareTo(pago) != 0) {
                relatorio.divergencias.add(titulo + ": baixas somam " + baixado + " e o legado diz pago " + pago + ".");
            }
            List<Item> numerados = new ArrayList<>();
            numerados.add(emissao.comItem(1));
            for (int i = 0; i < itens.size(); i++) {
                numerados.add(itens.get(i).comItem(i + 2));
            }
            for (Item item : numerados) {
                if (item.data().getYear() < 1990 || item.data().getYear() > lote.getAno() + 10) {
                    relatorio.avisos.add(titulo + ": movimento com data " + item.data() + " (digitada errada no legado?).");
                }
            }

            Titulo t = new Titulo(l.numero(), documento, l.dataEmissao(), l.dataVencimento(), parceiro, l.valor(),
                    conta, texto(l.observacao()), numerados);
            t.movimentosVazios = vazios;
            titulos++;
            totalValor = totalValor.add(l.valor());
            totalBaixado = totalBaixado.add(baixado);
            if (baixado.compareTo(l.valor()) < 0) {
                abertos++;
                totalAberto = totalAberto.add(l.valor().subtract(baixado));
            }
            return t;
        }

        private Historico historicoEmissao() {
            Historico h = historicos.get(1);
            if (h != null && h.emissao()) {
                return h;
            }
            return historicos.values().stream().filter(Historico::emissao)
                    .min(Comparator.comparing(Historico::codigo)).orElse(null);
        }

        /** Separa novos, alterados e iguais; título do axctg3 que não veio do legado impede. */
        private void comparar(Map<Integer, Titulo> doLote) {
            Map<Integer, Gravado> gravados = new HashMap<>();
            jdbc.query("select t.ID, t.NUMERO, t.CREATED_BY, t.DOCUMENTO, t.DATA_EMISSAO, t.DATA_VENCIMENTO, "
                            + "t.PARCEIRO_ID, t.VALOR, t.CONTA_CONTABIL_ID, t.OBSERVACAO "
                            + "from " + c.tabela() + " t where t.COD_EMPRESA = ? and t.DELETED_DATE is null "
                            + "and t.NUMERO in (select i.NUMERO from " + c.tabelaImp() + " i where i.LOTE_ID = ?)",
                    rs -> {
                        Gravado g = new Gravado(uuid(rs, 1), rs.getInt(2), rs.getString(3),
                                new Titulo(rs.getInt(2), rs.getString(4), data(rs, 5), data(rs, 6), uuid(rs, 7),
                                        rs.getBigDecimal(8), uuid(rs, 9), rs.getString(10), new ArrayList<>()));
                        gravados.put(g.numero(), g);
                    },
                    codEmpresa, lote.getId());
            Map<UUID, Gravado> porId = new HashMap<>();
            gravados.values().forEach(g -> porId.put(g.id(), g));
            Set<UUID> comItemDoAxctg3 = new HashSet<>();
            jdbc.query("select ip." + c.colunaPai() + ", ip.ITEM, ip.DATA, ip.HISTORICO_FINANCEIRO_ID, ip.BANCO_ID, "
                            + "ip.VALOR, ip.JUROS, ip.DESCONTO, ip.CREATED_BY "
                            + "from " + c.tabelaItem() + " ip join " + c.tabela() + " t on t.ID = ip." + c.colunaPai() + " "
                            + "where t.COD_EMPRESA = ? and t.DELETED_DATE is null and ip.DELETED_DATE is null "
                            + "and t.NUMERO in (select i.NUMERO from " + c.tabelaImp() + " i where i.LOTE_ID = ?)",
                    rs -> {
                        Gravado g = porId.get(uuid(rs, 1));
                        if (g == null) {
                            return;
                        }
                        g.titulo().itens.add(new Item(rs.getInt(2), data(rs, 3), uuid(rs, 4), uuid(rs, 5),
                                rs.getBigDecimal(6), rs.getBigDecimal(7), rs.getBigDecimal(8)));
                        if (!IMPORTACAO.equals(rs.getString(9))) {
                            comItemDoAxctg3.add(g.id());
                        }
                    },
                    codEmpresa, lote.getId());

            List<String> alteracoes = new ArrayList<>();
            for (Titulo t : doLote.values()) {
                Gravado g = gravados.get(t.numero);
                if (g == null) {
                    novos.add(t);
                    continue;
                }
                if (!IMPORTACAO.equals(g.criadoPor())) {
                    relatorio.erros.add(c.nome() + " " + t.numero + " já existe no axctg3 e não veio da importação do legado.");
                    continue;
                }
                if (comItemDoAxctg3.contains(g.id())) {
                    relatorio.erros.add(c.nome() + " " + t.numero + " tem movimento feito no axctg3; a importação "
                            + "apagaria esse movimento.");
                    continue;
                }
                List<String> mudancas = t.diferencas(g.titulo());
                if (mudancas.isEmpty()) {
                    iguais++;
                } else {
                    t.id = g.id();
                    t.trocarItens = !t.mesmosItens(g.titulo());
                    alteracoes.add(c.nome() + " " + t.numero + ": " + String.join("; ", mudancas) + ".");
                    alterados.add(t);
                }
            }
            relatorio.avisos.addAll(alteracoes);

            LocalDate inicio = LocalDate.of(lote.getAno(), lote.getMes() == null ? 1 : lote.getMes(), 1);
            LocalDate fim = lote.getMes() == null ? inicio.plusYears(1) : inicio.plusMonths(1);
            List<Integer> soNoAxctg3 = jdbc.queryForList("select t.NUMERO from " + c.tabela() + " t "
                            + "where t.COD_EMPRESA = ? and t.DELETED_DATE is null "
                            + "and t.DATA_EMISSAO >= ? and t.DATA_EMISSAO < ? "
                            + "and t.NUMERO not in (select i.NUMERO from " + c.tabelaImp() + " i where i.LOTE_ID = ?) "
                            + "order by t.NUMERO", Integer.class,
                    codEmpresa, inicio, fim, lote.getId());
            if (!soNoAxctg3.isEmpty()) {
                List<String> numeros = soNoAxctg3.subList(0, Math.min(50, soNoAxctg3.size())).stream()
                        .map(String::valueOf).toList();
                relatorio.avisos.add(soNoAxctg3.size() + " registro(s) de " + lote.getPeriodo()
                        + " existem no axctg3 e não vieram no lote (ficam como estão): "
                        + String.join(", ", numeros) + (soNoAxctg3.size() > 50 ? "..." : "") + ".");
            }
        }

        void gravar() {
            OffsetDateTime agora = OffsetDateTime.now();
            List<Object[]> titulosNovos = new ArrayList<>();
            List<Object[]> itensNovos = new ArrayList<>();
            for (Titulo t : novos) {
                t.id = UuidProvider.createUuid();
                titulosNovos.add(new Object[]{t.id, IMPORTACAO, agora, t.numero, codEmpresa, t.documento,
                        t.dataEmissao, t.dataVencimento, t.parceiro, t.valor, t.conta, t.observacao});
                t.itens.forEach(item -> itensNovos.add(linhaItem(t.id, item, agora)));
            }
            List<Object[]> titulosAlterados = new ArrayList<>();
            List<Object[]> itensApagados = new ArrayList<>();
            for (Titulo t : alterados) {
                titulosAlterados.add(new Object[]{t.documento, t.dataEmissao, t.dataVencimento, t.parceiro, t.valor,
                        t.conta, t.observacao, IMPORTACAO, agora, t.id});
                if (t.trocarItens) {
                    itensApagados.add(new Object[]{IMPORTACAO, agora, t.id});
                    t.itens.forEach(item -> itensNovos.add(linhaItem(t.id, item, agora)));
                }
            }
            emLotes("insert into " + c.tabela() + " (ID, VERSION, CREATED_BY, CREATED_DATE, NUMERO, COD_EMPRESA, "
                    + "DOCUMENTO, DATA_EMISSAO, DATA_VENCIMENTO, PARCEIRO_ID, VALOR, CONTA_CONTABIL_ID, OBSERVACAO) "
                    + "values (?, 1, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", titulosNovos);
            emLotes("update " + c.tabela() + " set DOCUMENTO = ?, DATA_EMISSAO = ?, DATA_VENCIMENTO = ?, PARCEIRO_ID = ?, "
                    + "VALOR = ?, CONTA_CONTABIL_ID = ?, OBSERVACAO = ?, LAST_MODIFIED_BY = ?, "
                    + "LAST_MODIFIED_DATE = ?, VERSION = VERSION + 1 where ID = ?", titulosAlterados);
            emLotes("update " + c.tabelaItem() + " set DELETED_BY = ?, DELETED_DATE = ? "
                    + "where " + c.colunaPai() + " = ? and DELETED_DATE is null", itensApagados);
            emLotes("insert into " + c.tabelaItem() + " (ID, VERSION, CREATED_BY, CREATED_DATE, " + c.colunaPai() + ", ITEM, DATA, "
                    + "HISTORICO_FINANCEIRO_ID, BANCO_ID, VALOR, JUROS, DESCONTO, CONTABILIZADO) "
                    + "values (?, 1, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", itensNovos);
        }

        private Object[] linhaItem(UUID titulo, Item item, OffsetDateTime agora) {
            return new Object[]{UuidProvider.createUuid(), IMPORTACAO, agora, titulo, item.item(), item.data(),
                    item.historico(), item.banco(), item.valor(), item.juros(), item.desconto(), Boolean.TRUE};
        }

        private void emLotes(String sql, List<Object[]> linhas) {
            for (int i = 0; i < linhas.size(); i += TAMANHO_LOTE_JDBC) {
                jdbc.batchUpdate(sql, linhas.subList(i, Math.min(i + TAMANHO_LOTE_JDBC, linhas.size())));
            }
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
            sb.append(e.titulos).append(" registros de ").append(lote.getPeriodo()).append(": valor ").append(e.totalValor)
                    .append(", baixado ").append(e.totalBaixado).append(", em aberto ").append(e.totalAberto)
                    .append(" (").append(e.abertos).append(" em aberto).\n");
        }
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

    private static BigDecimal zero(BigDecimal valor) {
        return valor == null ? BigDecimal.ZERO : valor;
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

    private record ImpTitulo(Integer registro, Integer numero, String documento, Integer parceiro,
                             LocalDate dataEmissao, LocalDate dataVencimento, BigDecimal valor, BigDecimal pago,
                             String codContabil, String observacao) {
    }

    private record ImpMovimento(Integer registro, Integer pai, LocalDate data, Integer historico, BigDecimal valor,
                                BigDecimal juros, BigDecimal desconto, Integer banco) {
    }

    private record Historico(int codigo, UUID id, boolean emissao, boolean baixa) {
    }

    private record Item(int item, LocalDate data, UUID historico, UUID banco, BigDecimal valor, BigDecimal juros,
                        BigDecimal desconto) {
        Item comItem(int numero) {
            return new Item(numero, data, historico, banco, valor, juros, desconto);
        }

        Item comValor(BigDecimal novo) {
            return new Item(item, data, historico, banco, novo, juros, desconto);
        }

        boolean mesmo(Item o) {
            return item == o.item && Objects.equals(data, o.data) && Objects.equals(historico, o.historico)
                    && Objects.equals(banco, o.banco)
                    && igual(valor, o.valor) && igual(juros, o.juros) && igual(desconto, o.desconto);
        }
    }

    private record Gravado(UUID id, Integer numero, String criadoPor, Titulo titulo) {
    }

    /** Título já com os ids do axctg3 — o do lote ou o gravado. */
    private static class Titulo {
        final Integer numero;
        final String documento;
        final LocalDate dataEmissao;
        final LocalDate dataVencimento;
        final UUID parceiro;
        final BigDecimal valor;
        final UUID conta;
        final String observacao;
        final List<Item> itens;
        UUID id;
        boolean trocarItens;
        int movimentosVazios;

        Titulo(Integer numero, String documento, LocalDate dataEmissao, LocalDate dataVencimento, UUID parceiro,
               BigDecimal valor, UUID conta, String observacao, List<Item> itens) {
            this.numero = numero;
            this.documento = documento;
            this.dataEmissao = dataEmissao;
            this.dataVencimento = dataVencimento;
            this.parceiro = parceiro;
            this.valor = valor;
            this.conta = conta;
            this.observacao = observacao;
            this.itens = itens;
        }

        List<String> diferencas(Titulo gravado) {
            List<String> d = new ArrayList<>();
            if (!Objects.equals(documento, texto(gravado.documento))) {
                d.add("documento " + gravado.documento + " passa a " + documento);
            }
            if (!Objects.equals(dataEmissao, gravado.dataEmissao)) {
                d.add("emissão " + gravado.dataEmissao + " passa a " + dataEmissao);
            }
            if (!Objects.equals(dataVencimento, gravado.dataVencimento)) {
                d.add("vencimento " + gravado.dataVencimento + " passa a " + dataVencimento);
            }
            if (!Objects.equals(parceiro, gravado.parceiro)) {
                d.add("parceiro trocado");
            }
            if (!igual(valor, gravado.valor)) {
                d.add("valor " + gravado.valor + " passa a " + valor);
            }
            if (!Objects.equals(conta, gravado.conta)) {
                d.add("conta contábil trocada");
            }
            if (!Objects.equals(observacao, texto(gravado.observacao))) {
                d.add("observação trocada");
            }
            if (!mesmosItens(gravado)) {
                d.add("movimentos substituídos (" + gravado.itens.size() + " → " + itens.size() + ")");
            }
            return d;
        }

        boolean mesmosItens(Titulo gravado) {
            if (itens.size() != gravado.itens.size()) {
                return false;
            }
            List<Item> outros = new ArrayList<>(gravado.itens);
            outros.sort(Comparator.comparingInt(Item::item));
            for (int i = 0; i < itens.size(); i++) {
                if (!itens.get(i).mesmo(outros.get(i))) {
                    return false;
                }
            }
            return true;
        }
    }

    private static boolean igual(BigDecimal a, BigDecimal b) {
        if (a == null || b == null) {
            return a == b;
        }
        return a.compareTo(b) == 0;
    }
}
