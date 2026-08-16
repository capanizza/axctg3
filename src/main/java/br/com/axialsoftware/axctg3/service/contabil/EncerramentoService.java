package br.com.axialsoftware.axctg3.service.contabil;

import br.com.axialsoftware.axctg3.entity.cadastros.Empresa;
import br.com.axialsoftware.axctg3.entity.contabil.ContaContabil;
import br.com.axialsoftware.axctg3.entity.contabil.HistoricoContabil;
import br.com.axialsoftware.axctg3.entity.contabil.Lancamento;
import br.com.axialsoftware.axctg3.entity.contabil.SaldoConta;
import br.com.axialsoftware.axctg3.entity.enums.CodNat;
import br.com.axialsoftware.axctg3.service.UtilGeralService;
import io.jmix.core.DataManager;
import io.jmix.core.FetchPlan;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Fechamento de exercício: as três operações rodam nessa ordem (lançamentos de
 * encerramento → criar próximo exercício → saldos para o próximo exercício), mas cada uma
 * é independente e pode ser reexecutada — {@link #prepararCriarProximoExercicio} pula contas
 * já criadas, {@link #prepararTransferenciaSaldos} exige que o passo anterior já tenha rodado.
 */
@Service
public class EncerramentoService {

    /** Mês em que o exercício sempre fecha — o ano contábil aqui não é fiscal, é sempre 12 meses. */
    private static final int MES_FECHAMENTO = 12;

    private final DataManager dataManager;
    private final UtilGeralService utilGeralService;
    private final ContaContabilService contaContabilService;

    public EncerramentoService(DataManager dataManager, UtilGeralService utilGeralService,
                                ContaContabilService contaContabilService) {
        this.dataManager = dataManager;
        this.utilGeralService = utilGeralService;
        this.contaContabilService = contaContabilService;
    }

    // ===================== 1. Lançamentos de encerramento =====================

    /** Conta de resultado com saldo (mês 12) a zerar, e o saldo no momento do preparo. */
    public record ContaEncerramento(ContaContabil conta, BigDecimal saldo) {
    }

    /** Conta de destino dos lançamentos de encerramento, configurada em {@code Empresa.codContaEnc}. */
    public ContaContabil resolverContaEncerramento() {
        Empresa empresa = utilGeralService.getEmpresa();
        String codigo = empresa.getCodContaEnc();
        if (codigo == null || codigo.isBlank()) {
            return null;
        }
        return contaContabilService.contaContabilPeloCodigo(codigo);
    }

    /**
     * Contas de resultado (analíticas) do ano/empresa correntes com saldo diferente de zero
     * em dezembro. Não grava nada — {@link #lancarEncerramentoConta} é chamado item a item
     * pela BackgroundTask que mostra o progresso na tela.
     */
    public List<ContaEncerramento> prepararLancamentoEncerramento() {
        Integer codEmpresa = utilGeralService.getCodEmpresa();
        Integer ano = utilGeralService.getAnoContabil();
        List<ContaContabil> contas = dataManager.load(ContaContabil.class)
                .query("select e from ContaContabil e " +
                        "where e.codEmpresa = :codEmpresa " +
                        "and e.ano = :ano " +
                        "and e.analitica = true " +
                        "and e.codNat = :codNat " +
                        "order by e.codigo")
                .parameter("codEmpresa", codEmpresa)
                .parameter("ano", ano)
                .parameter("codNat", CodNat.CONTAS_DE_RESULTADO.getId())
                .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE).add("saldosConta", FetchPlan.BASE))
                .list();

        List<ContaEncerramento> resultado = new ArrayList<>();
        for (ContaContabil conta : contas) {
            BigDecimal saldo = conta.getSaldosConta().get(MES_FECHAMENTO - 1).getSaldoAtual();
            if (saldo.compareTo(BigDecimal.ZERO) != 0) {
                resultado.add(new ContaEncerramento(conta, saldo));
            }
        }
        return resultado;
    }

    /**
     * Zera uma conta de resultado contra a conta de encerramento da empresa e guarda o saldo
     * zerado em {@code saldoTransf} (usado depois no DRE e no Sped Contábil). Chamado item a
     * item pela BackgroundTask — o {@code save} do lançamento dispara o
     * {@code LancamentoEventListener} normalmente (numeração, atualização de saldos).
     */
    public void lancarEncerramentoConta(ContaEncerramento item, HistoricoContabil historico, ContaContabil contaEncerramento) {
        ContaContabil conta = item.conta();
        BigDecimal saldo = item.saldo();
        boolean devedor = saldo.compareTo(BigDecimal.ZERO) > 0;

        Lancamento lancamento = dataManager.create(Lancamento.class);
        // codEmpresa/ano/mes/numero/dataLancamento são carimbados pelo LancamentoEventListener
        // a partir do período contábil corrente (por isso o encerramento só roda em dezembro).
        lancamento.setValor(saldo.abs());
        if (devedor) {
            lancamento.setContaCredora(conta);
            lancamento.setContaDevedora(contaEncerramento);
        } else {
            lancamento.setContaDevedora(conta);
            lancamento.setContaCredora(contaEncerramento);
        }
        lancamento.setHistoricoContabil(historico);
        lancamento.setOrigem("enc");
        dataManager.save(lancamento);

        conta.setSaldoTransf(saldo);
        dataManager.saveWithoutReload(conta);
    }

    // ===================== 2. Criar próximo exercício =====================

    /**
     * Contas do ano corrente cujo correspondente (mesmo código) ainda não existe no ano
     * seguinte. Idempotente: rodar de novo só cria o que faltar.
     */
    public List<ContaContabil> prepararCriarProximoExercicio() {
        Integer codEmpresa = utilGeralService.getCodEmpresa();
        Integer ano = utilGeralService.getAnoContabil();

        List<ContaContabil> contasAnoAtual = dataManager.load(ContaContabil.class)
                .query("select e from ContaContabil e " +
                        "where e.codEmpresa = :codEmpresa " +
                        "and e.ano = :ano " +
                        "order by e.codigo")
                .parameter("codEmpresa", codEmpresa)
                .parameter("ano", ano)
                .list();

        java.util.Set<String> codigosAnoSeguinte = dataManager.loadValues(
                        "select e.codigo from ContaContabil e " +
                                "where e.codEmpresa = :codEmpresa " +
                                "and e.ano = :anoSeguinte")
                .property("codigo")
                .parameter("codEmpresa", codEmpresa)
                .parameter("anoSeguinte", ano + 1)
                .list()
                .stream()
                .map(kv -> (String) kv.getValue("codigo"))
                .collect(Collectors.toSet());

        return contasAnoAtual.stream()
                .filter(c -> !codigosAnoSeguinte.contains(c.getCodigo()))
                .toList();
    }

    /**
     * Copia uma conta do ano corrente pro ano seguinte (mesmo código/nome/grau/analítica/
     * natureza/conta superior/referencial — não copia {@code saldoTransf}, que é só do ano que
     * fechou). Não precisa criar os 12 {@code SaldoConta} explicitamente:
     * {@code ContaContabilEventListener} já semeia isso sozinho em qualquer conta nova. Chamado
     * item a item pela BackgroundTask.
     */
    public void criarContaProximoExercicio(ContaContabil contaOrigem) {
        ContaContabil nova = dataManager.create(ContaContabil.class);
        nova.setCodigo(contaOrigem.getCodigo());
        nova.setNome(contaOrigem.getNome());
        nova.setCodEmpresa(contaOrigem.getCodEmpresa());
        nova.setAno(contaOrigem.getAno() + 1);
        nova.setGrau(contaOrigem.getGrau());
        nova.setAnalitica(contaOrigem.getAnalitica());
        nova.setCodNat(contaOrigem.getCodNat());
        nova.setCodContaSup(contaOrigem.getCodContaSup());
        nova.setContaReferencial(contaOrigem.getContaReferencial());
        dataManager.save(nova);
    }

    // ===================== 3. Saldos para o próximo exercício =====================

    /** Par conta do ano corrente / conta correspondente (mesmo código) já criada no ano seguinte. */
    public record TransferenciaSaldo(ContaContabil contaOrigem, ContaContabil contaDestino) {
    }

    public record PreparoTransferenciaSaldos(List<TransferenciaSaldo> pendentes, List<String> contasFaltantes) {
    }

    /**
     * Casa, pelo código, as contas do ano corrente com as do ano seguinte (criadas pelo passo
     * 2). Se alguma conta do ano corrente não tiver correspondente no ano seguinte,
     * {@code contasFaltantes} vem preenchida e {@code pendentes} vem vazio — quem chama deve
     * checar isso antes de abrir a BackgroundTask, em vez de transferir parcialmente.
     */
    public PreparoTransferenciaSaldos prepararTransferenciaSaldos() {
        Integer codEmpresa = utilGeralService.getCodEmpresa();
        Integer ano = utilGeralService.getAnoContabil();

        List<ContaContabil> contasAnoAtual = dataManager.load(ContaContabil.class)
                .query("select e from ContaContabil e " +
                        "where e.codEmpresa = :codEmpresa " +
                        "and e.ano = :ano " +
                        "order by e.codigo")
                .parameter("codEmpresa", codEmpresa)
                .parameter("ano", ano)
                .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE).add("saldosConta", FetchPlan.BASE))
                .list();

        Map<String, ContaContabil> contasAnoSeguinte = dataManager.load(ContaContabil.class)
                .query("select e from ContaContabil e " +
                        "where e.codEmpresa = :codEmpresa " +
                        "and e.ano = :anoSeguinte")
                .parameter("codEmpresa", codEmpresa)
                .parameter("anoSeguinte", ano + 1)
                .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE).add("saldosConta", FetchPlan.BASE))
                .list()
                .stream()
                .collect(Collectors.toMap(ContaContabil::getCodigo, c -> c, (a, b) -> a));

        List<String> faltantes = contasAnoAtual.stream()
                .map(ContaContabil::getCodigo)
                .filter(codigo -> !contasAnoSeguinte.containsKey(codigo))
                .toList();
        if (!faltantes.isEmpty()) {
            return new PreparoTransferenciaSaldos(List.of(), faltantes);
        }

        List<TransferenciaSaldo> pendentes = contasAnoAtual.stream()
                .map(c -> new TransferenciaSaldo(c, contasAnoSeguinte.get(c.getCodigo())))
                .toList();
        return new PreparoTransferenciaSaldos(pendentes, List.of());
    }

    /**
     * Grava o saldo de dezembro da conta de origem como saldo anterior dos 12 meses da conta
     * de destino — cópia direta do balanço, sem lançamento (contas de resultado já vêm
     * zeradas pelo passo 1, então o valor copiado pra elas é zero). Chamado item a item pela
     * BackgroundTask.
     */
    public void transferirSaldoConta(TransferenciaSaldo pendente) {
        BigDecimal saldoDezembro = pendente.contaOrigem().getSaldosConta().get(MES_FECHAMENTO - 1).getSaldoAtual();
        List<SaldoConta> saldosDestino = pendente.contaDestino().getSaldosConta();
        for (SaldoConta saldoConta : saldosDestino) {
            saldoConta.setSaldoAnterior(saldoDezembro);
            dataManager.saveWithoutReload(saldoConta);
        }
    }
}
