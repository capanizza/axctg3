package br.com.axialsoftware.axctg3.service.contabil;

import br.com.axialsoftware.axctg3.entity.cadastros.ConfigRel;
import br.com.axialsoftware.axctg3.entity.contabil.BalanceteDto;
import br.com.axialsoftware.axctg3.entity.contabil.ContaContabil;
import br.com.axialsoftware.axctg3.entity.contabil.ContaContabilDto;
import br.com.axialsoftware.axctg3.entity.contabil.SaldoConta;
import br.com.axialsoftware.axctg3.entity.contabil.VerificacaoDto;
import br.com.axialsoftware.axctg3.entity.tabelas.ContaReferencial;
import br.com.axialsoftware.axctg3.service.RelatorioService;
import br.com.axialsoftware.axctg3.service.UtilGeralService;
import io.jmix.core.DataManager;
import io.jmix.core.FetchPlan;
import io.jmix.core.SaveContext;
import net.sf.jasperreports.engine.JRDataSource;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class ContaContabilService {

    private static final Logger log = LoggerFactory.getLogger(ContaContabilService.class);

    private final DataManager dataManager;
    private final UtilGeralService utilGeralService;
    private final RelatorioService relatorioService;

    public ContaContabilService(DataManager dataManager, UtilGeralService utilGeralService, RelatorioService relatorioService) {
        this.dataManager = dataManager;
        this.utilGeralService = utilGeralService;
        this.relatorioService = relatorioService;
    }

    public ContaContabil contaContabilPeloCodigo(String codigo) {
        try {
            return dataManager.load(ContaContabil.class)
                    .query("select e from ContaContabil e " +
                            "where e.codigo = :codigo " +
                            "and e.codEmpresa = :codEmpresa " +
                            "and e.ano = :ano")
                    .parameter("codigo", codigo)
                    .parameter("codEmpresa", utilGeralService.getCodEmpresa())
                    .parameter("ano", utilGeralService.getAnoContabil())
                    .one();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Associa {@code contaReferencial} a todas as contas contábeis analíticas da
     * empresa/ano da sessão cujo código esteja entre {@code codigoInicial} e
     * {@code codigoFinal} (inclusive), mesma comparação por string de
     * {@link #prepararBalancetesDto} (contaInicial/contaFinal). Retorna a quantidade de
     * contas atualizadas.
     */
    public int associarContaReferencialEmGrupo(String codigoInicial, String codigoFinal, ContaReferencial contaReferencial) {
        Integer codEmpresa = utilGeralService.getCodEmpresa();
        Integer ano = utilGeralService.getAnoContabil();
        log.info("associarContaReferencialEmGrupo codEmpresa={} ano={} codigoInicial={} codigoFinal={} contaReferencial={}",
                codEmpresa, ano, codigoInicial, codigoFinal,
                contaReferencial == null ? null : contaReferencial.getCodigo());
        List<ContaContabil> contas = dataManager.load(ContaContabil.class)
                .query("select e from ContaContabil e " +
                        "where e.codEmpresa = :codEmpresa " +
                        "and e.ano = :ano " +
                        "and e.analitica = true " +
                        "and e.codigo >= :codigoInicial " +
                        "and e.codigo <= :codigoFinal " +
                        "order by e.codigo")
                .parameter("codEmpresa", codEmpresa)
                .parameter("ano", ano)
                .parameter("codigoInicial", codigoInicial)
                .parameter("codigoFinal", codigoFinal)
                .list();
        log.info("associarContaReferencialEmGrupo encontrou {} conta(s)", contas.size());
        if (contas.isEmpty()) {
            return 0;
        }
        SaveContext saveContext = new SaveContext();
        for (ContaContabil conta : contas) {
            conta.setContaReferencial(contaReferencial);
            saveContext.saving(conta);
        }
        dataManager.save(saveContext);
        return contas.size();
    }

    /**
     * Par conta contábil (ano atual, sem contaReferencial) / contaReferencial que ela herdaria
     * do ano anterior, calculado por {@link #prepararCopiaAssociacaoAnoAnterior}.
     */
    public record CopiaAssociacao(ContaContabil contaContabil, ContaReferencial contaReferencial) {
    }

    /**
     * Localiza, pelo mesmo código, as contas contábeis analíticas do ano atual ainda sem
     * {@code contaReferencial} cujo correspondente do ano anterior já tem uma associação —
     * candidatas ao "Copiar do ano anterior". Não grava nada; {@link #salvarCopiaAssociacao}
     * é chamado item a item pela BackgroundTask que mostra o progresso na tela.
     */
    public List<CopiaAssociacao> prepararCopiaAssociacaoAnoAnterior(Integer codEmpresa, Integer ano) {
        Map<String, ContaReferencial> referenciasAnoAnterior = dataManager.load(ContaContabil.class)
                .query("select e from ContaContabil e " +
                        "where e.codEmpresa = :codEmpresa " +
                        "and e.ano = :anoAnterior " +
                        "and e.analitica = true " +
                        "and e.contaReferencial is not null")
                .parameter("codEmpresa", codEmpresa)
                .parameter("anoAnterior", ano - 1)
                .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE).add("contaReferencial", FetchPlan.BASE))
                .list()
                .stream()
                .collect(java.util.stream.Collectors.toMap(
                        ContaContabil::getCodigo, ContaContabil::getContaReferencial, (a, b) -> a));
        if (referenciasAnoAnterior.isEmpty()) {
            return List.of();
        }

        List<ContaContabil> contasSemReferencial = dataManager.load(ContaContabil.class)
                .query("select e from ContaContabil e " +
                        "where e.codEmpresa = :codEmpresa " +
                        "and e.ano = :ano " +
                        "and e.analitica = true " +
                        "and e.contaReferencial is null " +
                        "order by e.codigo")
                .parameter("codEmpresa", codEmpresa)
                .parameter("ano", ano)
                .list();

        List<CopiaAssociacao> copias = new ArrayList<>();
        for (ContaContabil conta : contasSemReferencial) {
            ContaReferencial contaReferencial = referenciasAnoAnterior.get(conta.getCodigo());
            if (contaReferencial != null) {
                copias.add(new CopiaAssociacao(conta, contaReferencial));
            }
        }
        return copias;
    }

    /** Grava uma {@link CopiaAssociacao} — chamado item a item pela BackgroundTask da tela. */
    public void salvarCopiaAssociacao(CopiaAssociacao copia) {
        ContaContabil contaContabil = copia.contaContabil();
        contaContabil.setContaReferencial(copia.contaReferencial());
        dataManager.save(contaContabil);
    }

    public void listarContasContabeis () {
        String tituloRelatorio = "Listagem das contas contábeis";
        String nomeRelatorio = "ContaContabil.jasper";
        String nomeEmpresa = utilGeralService.getNomeEmpresa();
        String nomeSaida = "ContaContabil.pdf";

        var parametros = new HashMap<String, Object>();
        parametros.put("TITULO_RELATORIO", tituloRelatorio);
        parametros.put("NOME_EMPRESA", nomeEmpresa);
        parametros.put("LOGO", utilGeralService.getLogoEmpresa());

        List<ContaContabilDto> contasDto = prepararContas();

        JRDataSource dataSource = new JRBeanCollectionDataSource(contasDto);

        relatorioService.emitirRelatorio(nomeRelatorio, dataSource, parametros, nomeSaida);
    }

    private List<ContaContabilDto> prepararContas() {
        String analiticaAnterior = "N";
        String simNao;
        Integer codEmpresa = utilGeralService.getCodEmpresa();
        List<ContaContabilDto> contasDto = new ArrayList<>();
        List<ContaContabil> contas = dataManager.load(ContaContabil.class)
                .query("select p from ContaContabil p " +
                        "where p.codEmpresa = :codEmpresa " +
                        "and p.codigo > '1' " +
                        "order by p.codigo")
                .parameter("codEmpresa", codEmpresa)
                .list();
        for (ContaContabil conta: contas) {
            ContaContabilDto contaDto = dataManager.create(ContaContabilDto.class);
            contaDto.setCodigo(conta.getCodigo());
            contaDto.setNome(conta.getNome());
            contaDto.setGrau(conta.getGrau());
            simNao = conta.getAnalitica() ? "S" : "N";
            contaDto.setAnalitica(simNao);
            contaDto.setAnaliticaAnterior(analiticaAnterior);
            analiticaAnterior = contaDto.getAnalitica();
            contasDto.add(contaDto);
        }
        return contasDto;
    }

    public void listarBalancete(int tipo, ConfigRel configRel) {
        Integer codEmpresa = utilGeralService.getCodEmpresa();
        String tituloRelatorio;
        String nomeRelatorio;
        String nomeSaida;

        if (tipo == 1) {
            tituloRelatorio = "Balancete de " + utilGeralService.extensoPeriodoContabil();
            nomeRelatorio = "Balancete.jasper";
            nomeSaida = "Balancete.pdf";
        } else {
            tituloRelatorio = "Balanço de " + utilGeralService.extensoPeriodoContabil();
            nomeRelatorio = "Balanco.jasper";
            nomeSaida = "Balanco.pdf";
        }
        String nomeEmpresa = utilGeralService.getNomeEmpresa();

        var parametros = new HashMap<String, Object>();
        parametros.put("TITULO_RELATORIO", tituloRelatorio);
        parametros.put("NOME_EMPRESA", nomeEmpresa);
        parametros.put("GRAU_FECHAMENTO", configRel.getGrauFechamento());
        parametros.put("LOGO", utilGeralService.getLogoEmpresa());

        List<BalanceteDto> balancetesDto = prepararBalancetesDto(codEmpresa, configRel, tipo);

        JRDataSource dataSource = new JRBeanCollectionDataSource(balancetesDto);

        relatorioService.emitirRelatorio(nomeRelatorio, dataSource, parametros, nomeSaida);
    }

    private List<BalanceteDto> prepararBalancetesDto(Integer codEmpresa, ConfigRel configRel, int tipo) {
        int anoContabil = utilGeralService.getAnoContabil();
        int mesContabil = utilGeralService.getMesContabil();
        List<BalanceteDto> balancetesDto = new ArrayList<>();
        List<ContaContabil> contas = dataManager.load(ContaContabil.class)
                .query("select p from ContaContabil p " +
                        "where p.codigo >= :contaInicial " +
                        "and p.codigo <= :contaFinal " +
                        "and p.ano = :anoContabil " +
                        "and p.codEmpresa = :codEmpresa " +
                        "and p.grau <= :grauFechamento " +
                        "order by p.codigo")
                .parameter("contaInicial", configRel.getContaInicial())
                .parameter("contaFinal", configRel.getContaFinal())
                .parameter("anoContabil", anoContabil)
                .parameter("codEmpresa", codEmpresa)
                .parameter("grauFechamento", configRel.getGrauFechamento())
                .list();
//        List<BalanceteDto> balancetesDtoBusca = new ArrayList<BalanceteDto>();
        BigDecimal sldAnt, sldAntTotal = BigDecimal.ZERO, anterior = BigDecimal.ZERO, anteriorTotal = BigDecimal.ZERO
                , debMes , debMesTotal = BigDecimal.ZERO, debito = BigDecimal.ZERO
                , credMes, credMesTotal = BigDecimal.ZERO, credito = BigDecimal.ZERO
                , sldAtual, sldAtualTotal = BigDecimal.ZERO, atual = BigDecimal.ZERO, atualTotal = BigDecimal.ZERO;
        Integer grau;
        int gr, grauAnterior = 0, diferenca, posicao = 0, tam;
        String codCta, nomeCta, contaAnterior = "0";
        BalanceteDto balanceteDto
                , balanceteDtoAnterior = dataManager.create(BalanceteDto.class)
                , balanceteDtoTotal
                , balanceteDtoBusca;
        for (ContaContabil conta: contas) {
            List<SaldoConta> saldos = dataManager.load(SaldoConta.class)
                    .query("select s from SaldoConta s " +
                            "where s.contaContabil = :conta " +
                            "order by s.mes")
                    .parameter("conta", conta)
                    .list();
            sldAnt = saldos.get(mesContabil - 1).getSaldoAnterior();
            if (sldAnt == null) { sldAnt = BigDecimal.ZERO; }
            debMes = saldos.get(mesContabil - 1).getDebitoMes();
            if (debMes == null) { debMes = BigDecimal.ZERO; }
            credMes = saldos.get(mesContabil - 1).getCreditoMes();
            if (credMes == null) { credMes = BigDecimal.ZERO; }
//            sldAtual = saldos.get(mesContabil - 1).getSldAtual();
            sldAtual = sldAnt.add(debMes).subtract(credMes);
            if (sldAnt.compareTo(BigDecimal.ZERO) == 0
                    && debMes.compareTo(BigDecimal.ZERO) == 0
                    && credMes.compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }
            grau = conta.getGrau();
            if (grau > configRel.getGrauFechamento()) {
                continue;
            }
            if (grau.equals(configRel.getGrauFechamento())) {
                anteriorTotal = anteriorTotal.add(sldAnt);
                atualTotal = atualTotal.add(sldAtual);
            }
            if (grauAnterior == 0) {
                grauAnterior = grau;
            }
            if (grau > 3 && tipo == 1) {
                gr = 3;
            } else {
                gr = grau;
            }
            codCta = conta.getCodigo(); // formatacaoService.formatacaoMascContabil(conta.getCodCta());
            tam = codCta.length() + gr - 1;
            codCta = String.format("%" + tam + "s", codCta);

            nomeCta = conta.getNome();
            if (tipo == 2 && grau > 1) {
                tam = nomeCta.length() + grau - 1;
                nomeCta = String.format("%" + tam + "s", nomeCta);
            }

            balanceteDto = dataManager.create(BalanceteDto.class);
            balanceteDto.setConta(codCta);
            balanceteDto.setNome(nomeCta);
            balanceteDto.setGrau(conta.getGrau());
            balanceteDto.setGrauAnterior(grauAnterior);
            balanceteDto.setAnalitica(conta.getAnalitica().toString().equals("Sim"));
            balanceteDto.setSldAnt(sldAnt);
            balanceteDto.setSaldoAnteriorComLetra(utilGeralService.valorComLetra(sldAnt));
            balanceteDto.setDebMes(debMes);
            balanceteDto.setCredMes(credMes);
            balanceteDto.setSldAtual(sldAtual);
            balanceteDto.setSaldoAtualComLetra(utilGeralService.valorComLetra(sldAtual));
            balanceteDto.setTotalizar(false);
            if (grau.equals(configRel.getGrauFechamento())) {
                sldAntTotal = sldAntTotal.add(sldAnt);
                sldAtualTotal = sldAtualTotal.add(sldAtual);
                debMesTotal = debMesTotal.add(debMes);
                credMesTotal = credMesTotal.add(credMes);
                if (conta.getCodigo().substring(0, 1).compareTo("2") > 0) {  // resultado do exercício
                    anterior = anterior.add(sldAnt);
                    debito = debito.add(debMes);
                    credito = credito.add(credMes);
                    atual = atual.add(sldAtual);
                }
            }
            if (grau < grauAnterior) {
                diferenca = grauAnterior - grau;
                while (diferenca > 0) {
                    balanceteDtoTotal = buscarContaAnterior(balancetesDto, balanceteDtoAnterior);
                    balanceteDtoBusca = dataManager.create(BalanceteDto.class);
                    balanceteDtoBusca.setConta(balanceteDtoTotal.getConta());
                    balanceteDtoBusca.setNome(balanceteDtoTotal.getNome());
                    balanceteDtoBusca.setGrau(balanceteDtoTotal.getGrau());
                    balanceteDtoBusca.setAnalitica(balanceteDtoTotal.getAnalitica());
                    balanceteDtoBusca.setSldAnt(balanceteDtoTotal.getSldAnt());
                    balanceteDtoBusca.setSaldoAnteriorComLetra(utilGeralService.valorComLetra(balanceteDtoTotal.getSldAnt()));
                    balanceteDtoBusca.setDebMes(balanceteDtoTotal.getDebMes());
                    balanceteDtoBusca.setCredMes(balanceteDtoTotal.getCredMes());
                    balanceteDtoBusca.setSldAtual(balanceteDtoTotal.getSldAtual());
//                    balanceteDtoBusca.setSaldoAtualComLetra(formatacaoService.valorComLetra(sldAtualTotal));
                    balanceteDtoBusca.setSaldoAtualComLetra(utilGeralService.valorComLetra(balanceteDtoTotal.getSldAtual()));
                    balanceteDtoBusca.setTotalizar(true);
                    balanceteDtoBusca.setPosicao(posicao++);
                    balancetesDto.add(balanceteDtoBusca);
                    balanceteDtoAnterior = balanceteDtoBusca;
                    diferenca--;
                }
            }
            balanceteDto.setPosicao(posicao++);
            balancetesDto.add(balanceteDto);

            balanceteDtoAnterior = balanceteDto;
            grauAnterior = grau;
            contaAnterior = codCta;
        }

        if (!balancetesDto.isEmpty()) {
            tam = balancetesDto.size() - 1;
            grauAnterior = configRel.getGrauFechamento() - 1;
            for (int i = tam; i > 0; i--) {
                if (i == tam) {
                    balanceteDto = buscarContaAnterior(balancetesDto, balanceteDtoAnterior);
                } else {
                    balanceteDto = balancetesDto.get(i);
                    grau = balanceteDto.getGrau();
                    if (grau >= grauAnterior) {
                        continue;
                    }
                }
                balanceteDtoBusca = dataManager.create(BalanceteDto.class);
                balanceteDtoBusca.setConta(balanceteDto.getConta());
                balanceteDtoBusca.setNome(balanceteDto.getNome());
                balanceteDtoBusca.setGrau(balanceteDto.getGrau());
                balanceteDtoBusca.setAnalitica(balanceteDto.getAnalitica());
                balanceteDtoBusca.setSldAnt(balanceteDto.getSldAnt());
                balanceteDtoBusca.setSaldoAnteriorComLetra(utilGeralService.valorComLetra(balanceteDto.getSldAnt()));
                balanceteDtoBusca.setDebMes(balanceteDto.getDebMes());
                balanceteDtoBusca.setCredMes(balanceteDto.getCredMes());
                balanceteDtoBusca.setSldAtual(balanceteDto.getSldAtual());
                balanceteDtoBusca.setSaldoAtualComLetra(utilGeralService.valorComLetra(balanceteDto.getSldAtual()));
                balanceteDtoBusca.setTotalizar(true);
                balanceteDtoBusca.setPosicao(posicao++);
                balancetesDto.add(balanceteDtoBusca);
                grauAnterior = balanceteDtoBusca.getGrau();
                if (grauAnterior == 1) {
                    break;
                }
            }
        }

        if (sldAntTotal.compareTo(BigDecimal.ZERO) != 0 ||
                debMesTotal.compareTo(BigDecimal.ZERO) != 0 ||
                credMesTotal.compareTo(BigDecimal.ZERO) != 0 ||
                sldAtualTotal.compareTo(BigDecimal.ZERO) != 0) {
            balanceteDtoTotal = dataManager.create(BalanceteDto.class);
            balanceteDtoTotal.setConta(contaAnterior);
            balanceteDtoTotal.setNome("Total");
            balanceteDtoTotal.setGrau(99);
            balanceteDtoTotal.setGrauAnterior(99);
            balanceteDtoTotal.setAnalitica(true);
            balanceteDtoTotal.setSldAnt(sldAntTotal);
            balanceteDtoTotal.setDebMes(debMesTotal);
            balanceteDtoTotal.setCredMes(credMesTotal);
            balanceteDtoTotal.setSldAtual(sldAtualTotal);
            balanceteDtoTotal.setAnterior(anterior);
            balanceteDtoTotal.setAnteriorComLetra(utilGeralService.valorComLetra(anterior));
            balanceteDtoTotal.setAnteriorTotalComLetra(utilGeralService.valorComLetra(anteriorTotal));
            balanceteDtoTotal.setCredito(credito);
            balanceteDtoTotal.setDebito(debito);
            balanceteDtoTotal.setAtual(atual);
            balanceteDtoTotal.setAtualComLetra(utilGeralService.valorComLetra(atual));
            balanceteDtoTotal.setAtualTotalComLetra(utilGeralService.valorComLetra(atualTotal));
            balanceteDtoTotal.setTotalizar(true);
            balancetesDto.add(balanceteDtoTotal);
        }

        return balancetesDto;
    }

    private BalanceteDto buscarContaAnterior(List<BalanceteDto> list, BalanceteDto balanceteDto) {
        BalanceteDto balanceteDtoBusca = dataManager.create(BalanceteDto.class);
        Integer posicao = balanceteDto.getPosicao();
        for (int i = posicao - 1; i >= 0; i--) {
            balanceteDtoBusca = list.get(i);
            if (balanceteDtoBusca.getGrau() < balanceteDto.getGrau()) {
                break;
            }
        }
        return balanceteDtoBusca;
    }

    public void verificarContas() {
        String tituloRelatorio = "Verificação das contas contábeis";
        String nomeRelatorio = "VerificacaoContas.jasper";
        String nomeEmpresa = utilGeralService.getNomeEmpresa();
        String nomeSaida = "VerificacaoContas.pdf";

        var parametros = new HashMap<String, Object>();
        parametros.put("TITULO_RELATORIO", tituloRelatorio);
        parametros.put("NOME_EMPRESA", nomeEmpresa);
        parametros.put("LOGO", utilGeralService.getLogoEmpresa());

        List<VerificacaoDto> contasDto = verificarContasContabeis();

        JRDataSource dataSource = new JRBeanCollectionDataSource(contasDto);

        relatorioService.emitirRelatorio(nomeRelatorio, dataSource, parametros, nomeSaida);
    }

    private List<VerificacaoDto> verificarContasContabeis() {
        Integer codEmpresa = utilGeralService.getCodEmpresa();
        Integer anoContabil = utilGeralService.getAnoContabil();
        List<ContaContabil> contas = dataManager.load(ContaContabil.class)
                .query("select e from ContaContabil e " +
                        "where e.codigo > '1' " +
                        "and e.ano = :ano " +
                        "and e.codEmpresa = :codEmpresa " +
                        "order by e.codigo")
                .parameter("ano", anoContabil)
                .parameter("codEmpresa", codEmpresa)
                .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE).add("saldosConta", FetchPlan.BASE))
                .list();
        List<VerificacaoDto> verificacaoDtos = new ArrayList<>();

        for (ContaContabil contaContabil : contas) { // se há 12 saldos
            if (contaContabil.getSaldosConta().size() != 12) {
                VerificacaoDto verificacaoDto = dataManager.create(VerificacaoDto.class);
                verificacaoDto.setTipo(0);
                verificacaoDto.setTexto("Verificação das colunas dos saldos");
                verificacaoDto.setCodigo(contaContabil.getCodigo());
                verificacaoDto.setNome(contaContabil.getNome());
                verificacaoDtos.add(verificacaoDto);
            }
        }

        for (ContaContabil contaContabil : contas) {
            if (!Boolean.TRUE.equals(contaContabil.getAnalitica())) {
                continue;
            }
            List<SaldoConta> saldoContas = new ArrayList<>(contaContabil.getSaldosConta());
            saldoContas.sort(Comparator.comparing(SaldoConta::getMes)); // ordena pelo mês
            for (SaldoConta saldoConta : saldoContas) {
                int mes = saldoConta.getMes();
                LocalDate dataInicial = LocalDate.of(anoContabil, mes, 1);
                LocalDate dataFinal = dataInicial.with(TemporalAdjusters.lastDayOfMonth());
                BigDecimal saldoDevedor = Optional.ofNullable(saldoConta.getDebitoMes()).orElse(BigDecimal.ZERO);
                BigDecimal movimentoDevedor = valorMovDevedor(contaContabil, dataInicial, dataFinal);
                BigDecimal saldoCredor = Optional.ofNullable(saldoConta.getCreditoMes()).orElse(BigDecimal.ZERO);
                BigDecimal movimentoCredor = valorMovCredor(contaContabil, dataInicial, dataFinal);
                if (saldoDevedor.compareTo(movimentoDevedor) != 0 || saldoCredor.compareTo(movimentoCredor) != 0) {
                    VerificacaoDto verificacaoDto = dataManager.create(VerificacaoDto.class);
                    verificacaoDto.setTipo(1);
                    verificacaoDto.setTexto("Verificação dos saldos e lançamentos");
                    verificacaoDto.setCodigo(contaContabil.getCodigo());
                    verificacaoDto.setNome(contaContabil.getNome());
                    verificacaoDto.setMes(mes);
                    verificacaoDto.setSaldoDevedor(saldoDevedor);
                    verificacaoDto.setMovimentoDevedor(movimentoDevedor);
                    verificacaoDto.setSaldoCredor(saldoCredor);
                    verificacaoDto.setMovimentoCredor(movimentoCredor);
                    verificacaoDtos.add(verificacaoDto);
                }
            }
        }

        return verificacaoDtos;
    }

    private BigDecimal valorMovCredor(ContaContabil conta, LocalDate dataInicial, LocalDate dataFinal) {
        return dataManager.loadValue(
                        "select sum(e.valor) from Lancamento e " +
                                "where e.dataLancamento between :dataInicial and :dataFinal " +
                                "and e.contaCredora = :conta " +
                                "and e.codEmpresa = :codEmpresa",
                        BigDecimal.class)
                .parameter("dataInicial", dataInicial)
                .parameter("dataFinal", dataFinal)
                .parameter("conta", conta)
                .parameter("codEmpresa", utilGeralService.getCodEmpresa())
                .optional()
                .orElse(BigDecimal.ZERO);
    }

    private BigDecimal valorMovDevedor(ContaContabil conta, LocalDate dataInicial, LocalDate dataFinal) {
        return dataManager.loadValue(
                        "select sum(e.valor) from Lancamento e " +
                                "where e.dataLancamento between :dataInicial and :dataFinal " +
                                "and e.contaDevedora = :conta " +
                                "and e.codEmpresa = :codEmpresa",
                        BigDecimal.class)
                .parameter("dataInicial", dataInicial)
                .parameter("dataFinal", dataFinal)
                .parameter("conta", conta)
                .parameter("codEmpresa", utilGeralService.getCodEmpresa())
                .optional()
                .orElse(BigDecimal.ZERO);
    }

}