package br.com.axialsoftware.axctg3.service.importacao;

import br.com.axialsoftware.axctg3.entity.cadastros.CentroCusto;
import br.com.axialsoftware.axctg3.entity.contabil.ContaContabil;
import br.com.axialsoftware.axctg3.entity.contabil.HistoricoContabil;
import br.com.axialsoftware.axctg3.entity.contabil.Lancamento;
import br.com.axialsoftware.axctg3.entity.contabil.SaldoConta;
import br.com.axialsoftware.axctg3.entity.enums.CodNat;
import br.com.axialsoftware.axctg3.entity.enums.SituacaoLote;
import br.com.axialsoftware.axctg3.entity.enums.TipoLote;
import br.com.axialsoftware.axctg3.entity.importacao.ImpLancamento;
import br.com.axialsoftware.axctg3.entity.importacao.ImpLote;
import br.com.axialsoftware.axctg3.listener.contabil.LancamentoEventListener;
import io.jmix.core.DataManager;
import io.jmix.core.FetchPlan;
import io.jmix.core.SaveContext;
import io.jmix.data.Sequence;
import io.jmix.data.Sequences;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Importa um {@link ImpLote} de lançamentos do legado (MOVCONTA) de um ano inteiro — ou de um mês
 * só, quando o lote tem mês (empresa com milhares de lançamentos por mês) — para uma empresa cujo
 * plano de contas desse ano já foi importado com os saldos do legado. No lote de um mês, tudo
 * (a exigência de período vazio, a conferência dos saldos, a comparação com o gravado) vale só
 * para aquele mês; o saldo transferido do encerramento só é conferido no lote de dezembro ou do
 * ano inteiro.
 * <p>
 * Os lançamentos são gravados <b>sem</b> passar pelo {@code LancamentoService.atualizarSaldos}
 * (ver {@link LancamentoEventListener#gravarComoImportacao}): os saldos mensais vieram prontos
 * com o plano e contariam em dobro. Em troca, a <b>conferência</b> soma, mês a mês, o débito e
 * o crédito dos lançamentos de cada conta analítica e compara com os saldos do plano gravado;
 * o que não bate vai para o relatório do lote, nada é recalculado.
 * <p>
 * Número, ano, mês e dia vêm do legado (não da sessão nem da sequence), e a sequence
 * {@code lancamento_seq_<empresa>_<aaaamm>} de cada mês é posta no maior número importado, para
 * o próximo lançamento manual não repetir número. Lacuna de numeração não é apontada: o número
 * só serve para localizar o lançamento.
 * <p>
 * Lançamentos de encerramento de dezembro (conta de resultado com saldo transferido contra
 * conta do patrimônio líquido) são gravados com origem {@code "enc"}, a mesma do
 * {@code EncerramentoService}, para o SPED os marcar como "E".
 */
@Service
public class ImportacaoLancamentosService {

    static final String CONTA_CORINGA = "000000000";
    static final String ORIGEM_ENCERRAMENTO = "enc";
    private static final int MES_ENCERRAMENTO = 12;
    // um ano tem milhares de lançamentos: o relatório mostra o começo de cada seção e a contagem
    private static final int LIMITE_LINHAS_SECAO = 300;

    private final DataManager dataManager;
    private final Sequences sequences;

    public ImportacaoLancamentosService(DataManager dataManager, Sequences sequences) {
        this.dataManager = dataManager;
        this.sequences = sequences;
    }

    /**
     * Resolve os códigos, confere os lançamentos contra os saldos do plano e, se a empresa já
     * tem lançamentos nesse ano, compara o lote com eles. Só grava o relatório no lote.
     */
    public ImportacaoPlanoContasService.Relatorio conferir(UUID loteId) {
        ImpLote lote = dataManager.load(ImpLote.class).id(loteId).one();
        ImportacaoPlanoContasService.Relatorio relatorio = new ImportacaoPlanoContasService.Relatorio();
        validarLote(lote, relatorio, false);
        if (relatorio.erros.isEmpty()) {
            Leitura leitura = ler(lote, relatorio);
            if (relatorio.erros.isEmpty()) {
                conferirSaldos(leitura, relatorio);
                compararComGravados(lote, leitura, relatorio);
            }
        }
        gravarRelatorio(lote, relatorio, false);
        return relatorio;
    }

    /**
     * Confere e, sem erro, grava todos os lançamentos de uma vez (tudo ou nada) e ajusta as
     * sequences. Não pode ser chamado de dentro de uma transação: a gravação precisa fazer o
     * próprio commit enquanto o desvio do {@code LancamentoEventListener} está ligado.
     */
    public ImportacaoPlanoContasService.Relatorio importar(UUID loteId) {
        ImpLote lote = dataManager.load(ImpLote.class).id(loteId).one();
        ImportacaoPlanoContasService.Relatorio relatorio = new ImportacaoPlanoContasService.Relatorio();
        validarLote(lote, relatorio, true);
        Leitura leitura = null;
        if (relatorio.erros.isEmpty()) {
            leitura = ler(lote, relatorio);
            if (relatorio.erros.isEmpty()) {
                conferirSaldos(leitura, relatorio);
            }
        }
        boolean importado = false;
        if (relatorio.erros.isEmpty()) {
            try {
                gravar(lote, leitura);
                relatorio.importadas = leitura.lancamentos.size();
                importado = true;
            } catch (RuntimeException e) {
                relatorio.erros.add("Falha ao gravar, nada foi importado: " + e.getMessage());
            }
        }
        if (importado) {
            ajustarSequences(lote, leitura, relatorio);
        }
        gravarRelatorio(lote, relatorio, importado);
        return relatorio;
    }

    /** {@code paraImportar}: só a importação exige lote não importado e período sem lançamentos. */
    private void validarLote(ImpLote lote, ImportacaoPlanoContasService.Relatorio relatorio, boolean paraImportar) {
        if (lote.getTipo() != TipoLote.LANCAMENTOS) {
            relatorio.erros.add("O lote não é de lançamentos.");
            return;
        }
        if (lote.getSituacao() == SituacaoLote.EXPORTANDO) {
            relatorio.erros.add("A exportação deste lote não terminou (situação EXPORTANDO). Exporte de novo.");
            return;
        }
        if (lote.getAno() == null) {
            relatorio.erros.add("O lote não informa o ano dos lançamentos.");
            return;
        }
        if (lote.getMes() != null && (lote.getMes() < 1 || lote.getMes() > 12)) {
            relatorio.erros.add("O lote informa o mês " + lote.getMes() + ", inválido.");
            return;
        }
        // grava na empresa do lote, não na selecionada (mesma regra do plano de contas)
        Long empresas = dataManager.loadValue(
                        "select count(e) from Empresa e where e.codigo = :codEmpresa", Long.class)
                .parameter("codEmpresa", lote.getCodEmpresa())
                .one();
        if (empresas == 0) {
            relatorio.erros.add("A empresa " + lote.getCodEmpresa() + " não está cadastrada no axctg3.");
            return;
        }
        if (!paraImportar) {
            return;
        }
        if (lote.getSituacao() == SituacaoLote.IMPORTADO) {
            relatorio.erros.add("Este lote já foi importado.");
            return;
        }
        var consulta = dataManager.loadValue(
                        "select count(e) from Lancamento e where e.codEmpresa = :codEmpresa and e.ano = :ano"
                                + (lote.getMes() == null ? "" : " and e.mes = :mes"),
                        Long.class)
                .parameter("codEmpresa", lote.getCodEmpresa())
                .parameter("ano", lote.getAno());
        if (lote.getMes() != null) {
            consulta.parameter("mes", lote.getMes());
        }
        Long existentes = consulta.one();
        if (existentes > 0) {
            relatorio.erros.add("A empresa " + lote.getCodEmpresa() + " já tem " + existentes
                    + " lançamentos em " + lote.getPeriodo() + ". A importação só grava num período sem"
                    + " lançamentos (use Conferir para comparar o lote com eles).");
        }
    }

    /**
     * Lê as linhas do lote e resolve conta, histórico e centro de custo de cada uma. Código que
     * não existe no axctg3 é erro, agrupado por código com a quantidade de lançamentos.
     */
    Leitura ler(ImpLote lote, ImportacaoPlanoContasService.Relatorio relatorio) {
        Leitura leitura = new Leitura();
        leitura.mes = lote.getMes();
        int ano = lote.getAno();
        Integer codEmpresa = lote.getCodEmpresa();

        List<ContaContabil> contas = dataManager.load(ContaContabil.class)
                .query("select e from ContaContabil e where e.codEmpresa = :codEmpresa and e.ano = :ano")
                .parameter("codEmpresa", codEmpresa)
                .parameter("ano", ano)
                .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE).add("saldosConta", FetchPlan.BASE))
                .list();
        if (contas.isEmpty()) {
            relatorio.erros.add("A empresa " + codEmpresa + " não tem plano de contas em " + ano
                    + ". Importe antes o plano de contas com os saldos desse ano.");
            return leitura;
        }
        for (ContaContabil conta : contas) {
            leitura.contas.put(conta.getCodigo().trim(), conta);
        }
        Map<Integer, HistoricoContabil> historicos = new HashMap<>();
        dataManager.load(HistoricoContabil.class)
                .query("select e from HistoricoContabil e where e.codEmpresa = :codEmpresa")
                .parameter("codEmpresa", codEmpresa)
                .list()
                .forEach(h -> historicos.put(h.getCodigo(), h));
        Map<String, CentroCusto> centros = new HashMap<>();
        dataManager.load(CentroCusto.class)
                .query("select e from CentroCusto e where e.codEmpresa = :codEmpresa")
                .parameter("codEmpresa", codEmpresa)
                .list()
                .forEach(c -> centros.put(c.getCodigo().trim(), c));

        List<ImpLancamento> linhas = dataManager.load(ImpLancamento.class)
                .query("select e from ImpLancamento e where e.lote = :lote order by e.mes, e.numero")
                .parameter("lote", lote)
                .list();
        if (linhas.isEmpty()) {
            relatorio.erros.add("O lote não tem nenhum lançamento.");
            return leitura;
        }

        Map<String, Integer> contasInexistentes = new TreeMap<>();
        Map<String, Integer> contasSinteticas = new TreeMap<>();
        Map<Integer, Integer> historicosInexistentes = new TreeMap<>();
        Map<String, Integer> centrosInexistentes = new TreeMap<>();
        Set<String> chaves = new HashSet<>();
        int partidaSimples = 0;
        int valorZero = 0;

        for (ImpLancamento linha : linhas) {
            if (linha.getNumero() == null || linha.getMes() == null
                    || linha.getMes() < 1 || linha.getMes() > 12) {
                relatorio.erros.add("Linha sem número ou com mês inválido (número " + linha.getNumero()
                        + ", mês " + linha.getMes() + ").");
                continue;
            }
            String id = "Lançamento " + linha.getNumero() + " de " + linha.getMes() + "/" + ano;
            if (!Objects.equals(linha.getAno(), ano)) {
                relatorio.erros.add(id + ": ano " + linha.getAno() + " diferente do ano do lote.");
                continue;
            }
            if (lote.getMes() != null && !lote.getMes().equals(linha.getMes())) {
                relatorio.erros.add(id + ": fora do mês do lote (" + lote.getPeriodo() + ").");
                continue;
            }
            if (!chaves.add(linha.getMes() + "/" + linha.getNumero())) {
                relatorio.erros.add(id + " repetido no lote.");
                continue;
            }
            LocalDate data = linha.getDataLancamento();
            if (data == null) {
                data = dataPeloDia(ano, linha.getMes(), linha.getDia());
            } else if (data.getYear() != ano || data.getMonthValue() != linha.getMes()) {
                relatorio.erros.add(id + ": data " + data + " fora do mês do lançamento.");
                continue;
            }
            BigDecimal valor = linha.getValor();
            if (valor == null || valor.signum() < 0) {
                relatorio.erros.add(id + ": valor " + valor + " inválido.");
                continue;
            }
            if (valor.signum() == 0) {
                valorZero++;
            }

            LancamentoLido lido = new LancamentoLido();
            lido.numero = linha.getNumero();
            lido.mes = linha.getMes();
            lido.data = data;
            lido.valor = valor;
            lido.complemento = linha.getComplementoHistorico() == null ? "" : linha.getComplementoHistorico().trim();

            String codDev = vazioSeNulo(linha.getDevedora());
            String codCre = vazioSeNulo(linha.getCredora());
            // partida simples do legado (só débito ou só crédito): o lado em branco vai para a
            // coringa, que existe para isso
            if (codDev.isEmpty() || codCre.isEmpty()) {
                partidaSimples++;
                if (codDev.isEmpty()) {
                    codDev = CONTA_CORINGA;
                }
                if (codCre.isEmpty()) {
                    codCre = CONTA_CORINGA;
                }
            }
            lido.devedora = resolverConta(leitura, codDev, contasInexistentes, contasSinteticas);
            lido.credora = resolverConta(leitura, codCre, contasInexistentes, contasSinteticas);

            Integer codHist = linha.getHistorico();
            if (codHist != null) {
                lido.historico = historicos.get(codHist);
                if (lido.historico == null) {
                    historicosInexistentes.merge(codHist, 1, Integer::sum);
                }
            }
            String codCentro = vazioSeNulo(linha.getCentroCusto());
            if (!codCentro.isEmpty()) {
                lido.centroCusto = centros.get(codCentro);
                if (lido.centroCusto == null) {
                    centrosInexistentes.merge(codCentro, 1, Integer::sum);
                }
            }
            lido.encerramento = ehEncerramento(lido);
            leitura.lancamentos.add(lido);
        }

        contasInexistentes.forEach((codigo, qtd) -> relatorio.erros.add("Conta " + codigo
                + (CONTA_CORINGA.equals(codigo) ? " (coringa, usada na partida simples)" : "")
                + " não existe no plano de " + ano + " da empresa " + codEmpresa + " (" + qtd + " lançamento(s))."));
        historicosInexistentes.forEach((codigo, qtd) -> relatorio.erros.add("Histórico " + codigo
                + " não cadastrado na empresa " + codEmpresa + " (" + qtd + " lançamento(s))."));
        centrosInexistentes.forEach((codigo, qtd) -> relatorio.erros.add("Centro de custo " + codigo
                + " não cadastrado na empresa " + codEmpresa + " (" + qtd + " lançamento(s))."));
        contasSinteticas.forEach((codigo, qtd) -> relatorio.avisos.add("Conta " + codigo
                + " é sintética e recebe " + qtd + " lançamento(s)."));
        if (partidaSimples > 0) {
            relatorio.avisos.add(partidaSimples + " lançamento(s) de partida simples: o lado em branco foi para a conta "
                    + CONTA_CORINGA + ".");
        }
        if (valorZero > 0) {
            relatorio.avisos.add(valorZero + " lançamento(s) com valor zero.");
        }
        long encerramentos = leitura.lancamentos.stream().filter(l -> l.encerramento).count();
        if (encerramentos > 0) {
            relatorio.avisos.add(encerramentos + " lançamento(s) de dezembro reconhecidos como encerramento"
                    + " (resultado contra patrimônio líquido): gravados com origem \"" + ORIGEM_ENCERRAMENTO
                    + "\", que o SPED marca como E.");
        }
        return leitura;
    }

    private ContaContabil resolverConta(Leitura leitura, String codigo, Map<String, Integer> inexistentes,
                                       Map<String, Integer> sinteticas) {
        ContaContabil conta = leitura.contas.get(codigo);
        if (conta == null) {
            inexistentes.merge(codigo, 1, Integer::sum);
        } else if (!Boolean.TRUE.equals(conta.getAnalitica())) {
            sinteticas.merge(codigo, 1, Integer::sum);
        }
        return conta;
    }

    /**
     * Encerramento como o legado (e o {@code EncerramentoService}) faz: em dezembro, uma conta
     * analítica de resultado que tem saldo transferido, contra uma conta do patrimônio líquido.
     */
    private static boolean ehEncerramento(LancamentoLido lido) {
        if (lido.mes != MES_ENCERRAMENTO || lido.devedora == null || lido.credora == null) {
            return false;
        }
        return (resultadoComTransferencia(lido.devedora) && lido.credora.getCodNat() == CodNat.PATRIMONIO_LIQUIDO)
                || (resultadoComTransferencia(lido.credora) && lido.devedora.getCodNat() == CodNat.PATRIMONIO_LIQUIDO);
    }

    private static boolean resultadoComTransferencia(ContaContabil conta) {
        return Boolean.TRUE.equals(conta.getAnalitica())
                && conta.getCodNat() == CodNat.CONTAS_DE_RESULTADO
                && zeroSeNulo(conta.getSaldoTransf()).signum() != 0;
    }

    /**
     * Lançamentos × saldos do plano: em cada conta analítica e mês, a soma dos débitos e dos
     * créditos tem de dar o débito e o crédito do mês gravados no plano; e o encerramento de cada
     * conta de resultado tem de dar o saldo transferido dela. Só leitura.
     */
    void conferirSaldos(Leitura leitura, ImportacaoPlanoContasService.Relatorio relatorio) {
        Map<String, BigDecimal[]> debitos = new HashMap<>();
        Map<String, BigDecimal[]> creditos = new HashMap<>();
        Map<String, BigDecimal> encerrado = new HashMap<>();
        for (LancamentoLido lido : leitura.lancamentos) {
            somar(debitos, lido.devedora, lido.mes, lido.valor);
            somar(creditos, lido.credora, lido.mes, lido.valor);
            if (lido.encerramento) {
                // saldo transferido = saldo que a conta tinha: crédito do encerramento menos débito
                encerrado.merge(lido.devedora.getCodigo().trim(), lido.valor.negate(), BigDecimal::add);
                encerrado.merge(lido.credora.getCodigo().trim(), lido.valor, BigDecimal::add);
            }
        }
        for (ContaContabil conta : leitura.contas.values()) {
            if (!Boolean.TRUE.equals(conta.getAnalitica())) {
                continue;
            }
            String codigo = conta.getCodigo().trim();
            BigDecimal[] deb = debitos.get(codigo);
            BigDecimal[] cre = creditos.get(codigo);
            for (SaldoConta saldo : conta.getSaldosConta()) {
                int m = saldo.getMes() - 1;
                if (m < 0 || m > 11 || (leitura.mes != null && !leitura.mes.equals(saldo.getMes()))) {
                    continue;
                }
                compararMes(relatorio, codigo, saldo.getMes(), "débito", zeroSeNulo(saldo.getDebitoMes()),
                        deb == null ? BigDecimal.ZERO : deb[m]);
                compararMes(relatorio, codigo, saldo.getMes(), "crédito", zeroSeNulo(saldo.getCreditoMes()),
                        cre == null ? BigDecimal.ZERO : cre[m]);
            }
            if (conta.getCodNat() == CodNat.CONTAS_DE_RESULTADO
                    && (leitura.mes == null || leitura.mes == MES_ENCERRAMENTO)) {
                BigDecimal transf = zeroSeNulo(conta.getSaldoTransf());
                BigDecimal nosLancamentos = encerrado.getOrDefault(codigo, BigDecimal.ZERO);
                if (transf.compareTo(nosLancamentos) != 0) {
                    relatorio.divergencias.add("Conta " + codigo + ": saldo transferido " + transf
                            + " no plano, " + nosLancamentos + " nos lançamentos de encerramento.");
                }
            }
        }
    }

    private static void somar(Map<String, BigDecimal[]> somas, ContaContabil conta, int mes, BigDecimal valor) {
        BigDecimal[] meses = somas.computeIfAbsent(conta.getCodigo().trim(), k -> {
            BigDecimal[] v = new BigDecimal[12];
            java.util.Arrays.fill(v, BigDecimal.ZERO);
            return v;
        });
        meses[mes - 1] = meses[mes - 1].add(valor);
    }

    private static void compararMes(ImportacaoPlanoContasService.Relatorio relatorio, String codigo, int mes,
                                    String campo, BigDecimal noPlano, BigDecimal nosLancamentos) {
        if (noPlano.compareTo(nosLancamentos) != 0) {
            relatorio.divergencias.add("Conta " + codigo + ", mês " + mes + ": " + campo + " " + noPlano
                    + " no plano, " + nosLancamentos + " nos lançamentos.");
        }
    }

    /**
     * Compara o lote com os lançamentos já gravados da empresa/ano, se houver, por mês+número:
     * é assim que se valida o exportador contra um ano já importado do jeito antigo. Tudo vai
     * para as divergências, com o prefixo "Gravado:".
     */
    private void compararComGravados(ImpLote lote, Leitura leitura, ImportacaoPlanoContasService.Relatorio relatorio) {
        var consulta = dataManager.load(Lancamento.class)
                .query("select e from Lancamento e where e.codEmpresa = :codEmpresa and e.ano = :ano"
                        + (lote.getMes() == null ? "" : " and e.mes = :mes"))
                .parameter("codEmpresa", lote.getCodEmpresa())
                .parameter("ano", lote.getAno())
                .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE)
                        .add("contaDevedora", FetchPlan.BASE)
                        .add("contaCredora", FetchPlan.BASE)
                        .add("historicoContabil", FetchPlan.BASE)
                        .add("centroCusto", FetchPlan.BASE));
        if (lote.getMes() != null) {
            consulta.parameter("mes", lote.getMes());
        }
        List<Lancamento> gravados = consulta.list();
        if (gravados.isEmpty()) {
            return;
        }
        relatorio.avisos.add("A empresa " + lote.getCodEmpresa() + " já tem " + gravados.size()
                + " lançamentos gravados em " + lote.getPeriodo() + "; o lote foi comparado com eles.");
        Map<String, Lancamento> porChave = new HashMap<>();
        for (Lancamento gravado : gravados) {
            porChave.put(gravado.getMes() + "/" + gravado.getNumero(), gravado);
        }
        Set<String> doLote = new HashSet<>();
        for (LancamentoLido lido : leitura.lancamentos) {
            String chave = lido.mes + "/" + lido.numero;
            doLote.add(chave);
            Lancamento gravado = porChave.get(chave);
            String id = "Gravado: lançamento " + lido.numero + " de " + lido.mes + "/" + lote.getAno();
            if (gravado == null) {
                relatorio.divergencias.add(id + " está no lote mas não no axctg3.");
                continue;
            }
            compararCampo(relatorio, id, "data", lido.data, gravado.getDataLancamento());
            compararCampo(relatorio, id, "conta devedora", codigo(lido.devedora), codigo(gravado.getContaDevedora()));
            compararCampo(relatorio, id, "conta credora", codigo(lido.credora), codigo(gravado.getContaCredora()));
            if (lido.valor.compareTo(zeroSeNulo(gravado.getValor())) != 0) {
                compararCampo(relatorio, id, "valor", lido.valor, gravado.getValor());
            }
            compararCampo(relatorio, id, "histórico",
                    lido.historico == null ? null : lido.historico.getCodigo(),
                    gravado.getHistoricoContabil() == null ? null : gravado.getHistoricoContabil().getCodigo());
            compararCampo(relatorio, id, "centro de custo",
                    lido.centroCusto == null ? null : lido.centroCusto.getCodigo(),
                    gravado.getCentroCusto() == null ? null : gravado.getCentroCusto().getCodigo());
        }
        gravados.stream()
                .filter(g -> !doLote.contains(g.getMes() + "/" + g.getNumero()))
                .sorted((a, b) -> a.getMes().equals(b.getMes())
                        ? a.getNumero().compareTo(b.getNumero()) : a.getMes().compareTo(b.getMes()))
                .forEach(g -> relatorio.divergencias.add("Gravado: lançamento " + g.getNumero() + " de "
                        + g.getMes() + "/" + lote.getAno() + " está no axctg3 mas não no lote"
                        + (ORIGEM_ENCERRAMENTO.equals(g.getOrigem()) ? " (encerramento gerado pelo axctg3)." : ".")));
    }

    private static String codigo(ContaContabil conta) {
        return conta == null ? null : conta.getCodigo().trim();
    }

    private static void compararCampo(ImportacaoPlanoContasService.Relatorio relatorio, String id, String campo,
                                      Object doLote, Object gravado) {
        if (!Objects.equals(doLote, gravado)) {
            relatorio.divergencias.add(id + ": " + campo + " \"" + doLote + "\" no lote, \"" + gravado + "\" gravado.");
        }
    }

    private void gravar(ImpLote lote, Leitura leitura) {
        SaveContext saveContext = new SaveContext();
        for (LancamentoLido lido : leitura.lancamentos) {
            Lancamento lancamento = dataManager.create(Lancamento.class);
            lancamento.setCodEmpresa(lote.getCodEmpresa());
            lancamento.setAno(lote.getAno());
            lancamento.setMes(lido.mes);
            lancamento.setDia(lido.data.getDayOfMonth());
            lancamento.setDataLancamento(lido.data);
            lancamento.setNumero(lido.numero);
            lancamento.setContaDevedora(lido.devedora);
            lancamento.setContaCredora(lido.credora);
            lancamento.setHistoricoContabil(lido.historico);
            lancamento.setCentroCusto(lido.centroCusto);
            lancamento.setComplementoHistorico(lido.complemento);
            lancamento.setValor(lido.valor);
            lancamento.setOrigem(lido.encerramento ? ORIGEM_ENCERRAMENTO : null);
            saveContext.saving(lancamento);
        }
        LancamentoEventListener.gravarComoImportacao(() -> dataManager.save(saveContext));
    }

    /** Põe a sequence de cada mês no maior número importado; o próximo lançamento manual segue dali. */
    private void ajustarSequences(ImpLote lote, Leitura leitura, ImportacaoPlanoContasService.Relatorio relatorio) {
        Map<Integer, Integer> maiorPorMes = new TreeMap<>();
        for (LancamentoLido lido : leitura.lancamentos) {
            maiorPorMes.merge(lido.mes, lido.numero, Math::max);
        }
        maiorPorMes.forEach((mes, numero) -> {
            String nome = "lancamento_seq_" + lote.getCodEmpresa() + "_" + String.format("%4d%02d", lote.getAno(), mes);
            try {
                sequences.setCurrentValue(Sequence.withName(nome), numero);
            } catch (RuntimeException e) {
                relatorio.avisos.add("Sequence " + nome + " não foi ajustada para " + numero
                        + " (ajuste em Administração > Sequência): " + e.getMessage());
            }
        });
    }

    private static LocalDate dataPeloDia(int ano, int mes, Integer dia) {
        YearMonth anoMes = YearMonth.of(ano, mes);
        int d = dia == null || dia < 1 ? 1 : Math.min(dia, anoMes.lengthOfMonth());
        return anoMes.atDay(d);
    }

    private void gravarRelatorio(ImpLote lote, ImportacaoPlanoContasService.Relatorio relatorio, boolean importado) {
        StringBuilder sb = new StringBuilder();
        sb.append(importado ? "IMPORTADO em " : "Conferido em ").append(OffsetDateTime.now().withNano(0)).append('\n');
        if (importado) {
            sb.append(relatorio.importadas).append(" lançamentos gravados, sem recalcular os saldos do plano.\n");
        }
        secao(sb, "ERROS (impedem a importação)", relatorio.erros);
        secao(sb, "DIVERGÊNCIAS", relatorio.divergencias);
        secao(sb, "AVISOS", relatorio.avisos);
        if (relatorio.erros.isEmpty() && relatorio.divergencias.isEmpty()) {
            sb.append("\nLançamentos batendo com os saldos do plano em todas as contas analíticas")
                    .append(lote.getMes() == null ? "" : " no mês " + lote.getPeriodo()).append(".\n");
        }
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
        for (int i = 0; i < linhas.size() && i < LIMITE_LINHAS_SECAO; i++) {
            sb.append("  - ").append(linhas.get(i)).append('\n');
        }
        if (linhas.size() > LIMITE_LINHAS_SECAO) {
            sb.append("  ... e mais ").append(linhas.size() - LIMITE_LINHAS_SECAO).append(".\n");
        }
    }

    private static String vazioSeNulo(String texto) {
        return texto == null ? "" : texto.trim();
    }

    private static BigDecimal zeroSeNulo(BigDecimal valor) {
        return valor == null ? BigDecimal.ZERO : valor;
    }

    static class Leitura {
        // mês do lote; null = ano inteiro
        Integer mes;
        final Map<String, ContaContabil> contas = new HashMap<>();
        final List<LancamentoLido> lancamentos = new ArrayList<>();
    }

    static class LancamentoLido {
        int numero;
        int mes;
        LocalDate data;
        BigDecimal valor;
        String complemento;
        ContaContabil devedora;
        ContaContabil credora;
        HistoricoContabil historico;
        CentroCusto centroCusto;
        boolean encerramento;
    }
}
