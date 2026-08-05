package br.com.axialsoftware.axctg3.service.contabil;

import br.com.axialsoftware.axctg3.entity.cadastros.CentroCusto;
import br.com.axialsoftware.axctg3.entity.cadastros.ConfigRel;
import br.com.axialsoftware.axctg3.entity.contabil.*;
import br.com.axialsoftware.axctg3.service.RelatorioService;
import br.com.axialsoftware.axctg3.service.UtilGeralService;
import io.jmix.core.DataManager;
import io.jmix.core.Id;
import io.jmix.core.SaveContext;
import io.jmix.core.event.EntityChangedEvent;
import io.jmix.data.Sequence;
import io.jmix.data.Sequences;
import io.jmix.flowui.Dialogs;
import net.sf.jasperreports.engine.JRDataSource;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Date;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;

@Service
public class LancamentoService {

    private final UtilGeralService utilGeralService;
    private final DataManager dataManager;
    private final RelatorioService relatorioService;
    private final Dialogs dialogs;
    private final Sequences sequences;

    public LancamentoService(UtilGeralService utilGeralService, DataManager dataManager, RelatorioService relatorioService, Dialogs dialogs, Sequences sequences) {
        this.utilGeralService = utilGeralService;
        this.dataManager = dataManager;
        this.relatorioService = relatorioService;
        this.dialogs = dialogs;
        this.sequences = sequences;
    }

    private static final Logger log = LoggerFactory.getLogger(LancamentoService.class);

    public LocalDate calcularDataLancamento(Lancamento lancamento, Integer anoContabil, Integer mesContabil) {
        Integer dia;
        if (lancamento.getDia() != null) {
            dia = lancamento.getDia();
            if (lancamento.getDia() == 0) {
                dia = 1;
            } else {
                LocalDate ld = LocalDate.of(anoContabil, mesContabil, 1);
                ld = ld.with(TemporalAdjusters.lastDayOfMonth());
                Integer ultimoDia = ld.getDayOfMonth();
                if (dia > ultimoDia) {
                    dia = ultimoDia;
                }
            }
        } else {
            dia = 1;
        }
        return LocalDate.of(anoContabil, mesContabil, dia);
    }

    public void atualizarSaldos(Lancamento lancamento) {
        ContaContabil contaDevedora = lancamento.getContaDevedora();
        ContaContabil contaCredora = lancamento.getContaCredora();
        BigDecimal valor = lancamento.getValor();

        SaveContext saveContext = new SaveContext(); // vai até o fim

        List<SaldoConta> saldosDev = atualizarSaldosGrupo(
                contaDevedora,
                valor,
                true);
        for (SaldoConta sld : saldosDev) {
            saveContext.saving(sld);
        }

        List<SaldoConta> saldosCre = atualizarSaldosGrupo(
                contaCredora,
                valor,
                false);
        for (SaldoConta sld : saldosCre) {
            saveContext.saving(sld);
        }
    }

    private List<SaldoConta> atualizarSaldosGrupo(ContaContabil conta, BigDecimal valor, boolean debito) {
        Integer codEmpresa = utilGeralService.getCodEmpresa();
        Integer anoContabil = utilGeralService.getAnoContabil();
        int mesContabil = utilGeralService.getMesContabil();
        ContaContabil contaAux = conta;
        String codCtaAcima;
        List<SaldoConta> saldosConta;
        List<SaldoConta> resultado = new ArrayList<SaldoConta>();
        SaldoConta saldoConta, saldoConta2;
        BigDecimal saldo, saldo2;
        while (true) {
            saldosConta = contaAux.getSaldosConta();
            saldoConta = saldosConta.get(mesContabil - 1);
            if (debito) {
                saldo = saldoConta.getDebitoMes();
                saldo = saldo.add(valor);
                saldoConta.setDebitoMes(saldo);
                for (int i = mesContabil; i < 12; i++) {
                    saldoConta2 = saldosConta.get(i);
                    saldo2 = saldoConta2.getSaldoAnterior();
                    saldo2 = saldo2.add(valor);
                    saldoConta2.setSaldoAnterior(saldo2);
                }
            } else {
                saldo = saldoConta.getCreditoMes();
                saldo = saldo.add(valor);
                saldoConta.setCreditoMes(saldo);
                for (int i = mesContabil; i < 12; i++) {
                    saldoConta2 = saldosConta.get(i);
                    saldo2 = saldoConta2.getSaldoAnterior();
                    saldo2 = saldo2.subtract(valor);
                    saldoConta2.setSaldoAnterior(saldo2);
                }
            }
            resultado.add(saldoConta);
            if (contaAux.getGrau() == 1) {
                break;
            }
            codCtaAcima = contaAux.getCodContaSup().trim();
            try {
                contaAux = dataManager.load(ContaContabil.class)
                        .query("select p from ContaContabil p " +
                                "where p.codigo = :codigo " +
                                "and p.ano = :ano " +
                                "and p.codEmpresa = :codEmpresa")
                        .parameter("codigo", codCtaAcima)
                        .parameter("ano", anoContabil)
                        .parameter("codEmpresa", codEmpresa)
                        .one();
            } catch (Exception e) {
                log.info("Aqui");
            }
        }
        return resultado;
    }

    private void subtrairSaldos(ContaContabil conta, BigDecimal val, boolean debito) {
        BigDecimal valor = val.multiply(new BigDecimal(-1));

        SaveContext saveContext = new SaveContext();

        List<SaldoConta> saldos = atualizarSaldosGrupo(
                conta,
                valor,
                debito);

        for (SaldoConta sld : saldos) {
            saveContext.saving(sld);
        }
    }

    public void excluirSaldosAnteriores(EntityChangedEvent<Lancamento> event) {
        ContaContabil contaDevedora, contaCredora;
        Id<ContaContabil> contaId = event.getChanges().getOldReferenceId("contaDevedora");
        if (contaId == null) {  // é alteração
            Id<Lancamento> lancamentoId = event.getEntityId();
            Lancamento lancamento = dataManager.load(lancamentoId).one();
            contaDevedora = lancamento.getContaDevedora();
        } else { // é exclusão
            contaDevedora = dataManager.load(contaId).one();
        }

        contaId = event.getChanges().getOldReferenceId("contaCredora");
        if (contaId == null) {
            Id<Lancamento> lancamentoId = event.getEntityId();
            Lancamento lancamento = dataManager.load(lancamentoId).one();
            contaCredora = lancamento.getContaCredora();
        } else {
            contaCredora = dataManager.load(contaId).one();
        }
        BigDecimal valor = event.getChanges().getOldValue("valor");
        if (valor == null) {
            Id<Lancamento> lancamentoId = event.getEntityId();
            Lancamento lancamento = dataManager.load(lancamentoId).one();
            valor = lancamento.getValor();
        }

        subtrairSaldos(contaDevedora, valor, true);
        subtrairSaldos(contaCredora, valor, false);
    }

    public void listarLancamentos(ConfigRel configRel) {
        Integer codEmpresa = utilGeralService.getCodEmpresa();
        String tituloRelatorio = "Listagem de lançamentos";
        String nomeRelatorio = "Lancamento.jasper";
        String nomeSaida = "Lancamento.pdf";
        String nomeEmpresa = utilGeralService.getNomeEmpresa();
        SimpleDateFormat df = new SimpleDateFormat("dd/MM/yyyy");
        Date dtInicial = Date.valueOf(configRel.getDataInicial());
        Date dtFinal = Date.valueOf(configRel.getDataFinal());
        String periodoRelatorio, origem;
        if (configRel.getOrigem() == null) {
            origem = "";
        } else {
            origem = configRel.getOrigem();
        }
        if (origem.isEmpty()) {
            periodoRelatorio = "Período: " + df.format(dtInicial) + " a " + df.format(dtFinal);
        } else {
            periodoRelatorio = "Período: " + df.format(dtInicial) + " a " + df.format(dtFinal) + " Origem: " + origem;
        }

        var parametros = new HashMap<String, Object>();
        parametros.put("TITULO_RELATORIO", tituloRelatorio);
        parametros.put("NOME_EMPRESA", nomeEmpresa);
        parametros.put("GRAU_FECHAMENTO", configRel.getGrauFechamento());
        parametros.put("PERIODO_RELATORIO", periodoRelatorio);
        // parametros.put("LOGO", System.getProperty("user.dir").replace('\\','/') + "/logo.bmp");
        parametros.put("LOGO", utilGeralService.getLogoEmpresa());

        List<LancamentoDto> lancamentosDto = prepararLancamentosDto(codEmpresa, configRel);

        JRDataSource dataSource = new JRBeanCollectionDataSource(lancamentosDto);

        relatorioService.emitirRelatorio(nomeRelatorio, dataSource, parametros, nomeSaida);
    }

    private List<LancamentoDto> prepararLancamentosDto(Integer codEmpresa, ConfigRel configRel) {
        List<LancamentoDto> lancamentosDto = new ArrayList<LancamentoDto>();
        List<Lancamento> lancamentos = dataManager.load(Lancamento.class)
                .query("select l from Lancamento l " +
                        "where l.dataLancamento >= :dataInicial " +
                        "and l.dataLancamento <= :dataFinal " +
                        "and l.numero >= :lancamentoInicial " +
                        "and l.numero <= :lancamentoFinal " +
                        "and (:codCtaDev = '' or l.contaDevedora.codigo = :codCtaDev) " +
                        "and (:codCtaCre = '' or l.contaCredora.codigo = :codCtaCre) " +
                        "and (:origem is null or :origem = '' or l.origem = :origem) " +
                        "and l.codEmpresa = :codEmpresa " +
                        "order by l.numero")
                .parameter("codEmpresa", codEmpresa)
                .parameter("lancamentoInicial", configRel.getLancamentoInicial())
                .parameter("lancamentoFinal", configRel.getLancamentoFinal())
                .parameter("dataInicial", configRel.getDataInicial())
                .parameter("dataFinal", configRel.getDataFinal())
                .parameter("codCtaDev", configRel.getContaDevedora())
                .parameter("codCtaCre", configRel.getContaCredora())
                .parameter("origem", configRel.getOrigem())
                .list();
        for (Lancamento lancamento: lancamentos) {
            LancamentoDto lancamentoDto = dataManager.create(LancamentoDto.class);
            lancamentoDto.setNumero(lancamento.getNumero());
            lancamentoDto.setDataLancamento(utilGeralService.localDateToSqlDate(lancamento.getDataLancamento()));
            lancamentoDto.setOrigem(lancamento.getOrigem());
            lancamentoDto.setCodCtDev(lancamento.getContaDevedora().getCodigo());
            lancamentoDto.setDscCtDev(lancamento.getContaDevedora().getNome());
            lancamentoDto.setCodCtCre(lancamento.getContaCredora().getCodigo());
            lancamentoDto.setDscCtCre(lancamento.getContaCredora().getNome());
            if (lancamento.getHistoricoContabil() != null) {
                lancamentoDto.setCodHistCont(lancamento.getHistoricoContabil().getCodigo());
                lancamentoDto.setDscHistCont(lancamento.getHistoricoContabil().getDescricao());
            }
            lancamentoDto.setComplHist(lancamento.getComplementoHistorico());
            lancamentoDto.setValor(lancamento.getValor());
            lancamentosDto.add(lancamentoDto);
        }
        return lancamentosDto;
    }

    public void listarRazao(ConfigRel configRel) {
        SimpleDateFormat df = new SimpleDateFormat("dd/MM/yyyy");
        Date dtInicial = Date.valueOf(configRel.getDataInicial());
        Date dtFinal = Date.valueOf(configRel.getDataFinal());

        int codEmpresa = utilGeralService.getCodEmpresa();

        String tituloRelatorio = "Razão contábil";
        String nomeRelatorio = "Razao.jasper";
        String nomeEmpresa = utilGeralService.getNomeEmpresa();
        String nomeSaida = "Razao.pdf";
        String periodoRelatorio = "Período: " + df.format(dtInicial) + " a " + df.format(dtFinal);

        var parametros = new HashMap<String, Object>();
        parametros.put("TITULO_RELATORIO", tituloRelatorio);
        parametros.put("NOME_EMPRESA", nomeEmpresa);
        parametros.put("PERIODO_RELATORIO", periodoRelatorio);
        parametros.put("LOGO", utilGeralService.getLogoEmpresa());

        List<RazaoDto> list = prepararRazoesDto(codEmpresa, configRel);
        JRDataSource dataSource = new JRBeanCollectionDataSource(list);

        relatorioService.emitirRelatorio(nomeRelatorio, dataSource, parametros, nomeSaida);
    }

    private List<RazaoDto> prepararRazoesDto(Integer codEmpresa, ConfigRel configRel) {
        List<RazaoDto> lancamentosDto = new ArrayList<RazaoDto>();
        List<Lancamento> lancamentos = dataManager.load(Lancamento.class)
                .query("select l from Lancamento l " +
                        "where l.dataLancamento >= :dataInicial " +
                        "and l.dataLancamento <= :dataFinal " +
                        "and (l.contaDevedora.codigo >= :codCtaInicial) " +
                        "and (l.contaDevedora.codigo <= :codCtaFinal) " +
                        "and l.codEmpresa = :codEmpresa " +
                        "order by l.contaDevedora.codigo, l.dataLancamento, l.numero")
                .parameter("codEmpresa", codEmpresa)
                .parameter("dataInicial", configRel.getDataInicial())
                .parameter("dataFinal", configRel.getDataFinal())
                .parameter("codCtaInicial", configRel.getContaInicial())
                .parameter("codCtaFinal", configRel.getContaFinal())
                .list();
        BigDecimal saldoAnterior = BigDecimal.ZERO;
        BigDecimal saldoAux = BigDecimal.ZERO;
        String contaAux = "";
        for (Lancamento lancamento: lancamentos) {
            if (contaAux.compareTo(lancamento.getContaDevedora().getCodigo()) != 0) {
                saldoAnterior = calcularSaldoAnterior(lancamento, configRel, true);
                saldoAux = saldoAnterior;
                contaAux = lancamento.getContaDevedora().getCodigo();
            }
            RazaoDto razaoDto = dataManager.create(RazaoDto.class);
            razaoDto.setNumero(lancamento.getNumero());
            razaoDto.setDataLancamento(utilGeralService.localDateToSqlDate(lancamento.getDataLancamento()));
            razaoDto.setCodConta(lancamento.getContaDevedora().getCodigo());
            razaoDto.setNmConta(lancamento.getContaDevedora().getNome());
            razaoDto.setCodContraPartida(lancamento.getContaCredora().getCodigo());
            razaoDto.setNmContraPartida(lancamento.getContaCredora().getNome());
            String st = "";
            if (lancamento.getHistoricoContabil() != null) {
                st = lancamento.getHistoricoContabil().getCodigo().toString() + " ";
                st = lancamento.getHistoricoContabil().getDescricao() + " ";
            }
            st = st + lancamento.getComplementoHistorico();
            razaoDto.setHistorico(st);
            razaoDto.setSaldoAnterior(saldoAnterior);
            razaoDto.setSaldoAnteriorComLetra(utilGeralService.valorComLetra(saldoAnterior));
            saldoAux = saldoAux.add(lancamento.getValor());
            razaoDto.setSaldoAtual(saldoAux);
            razaoDto.setSaldoAtualComLetra(utilGeralService.valorComLetra(saldoAux));
            razaoDto.setValorDebito(lancamento.getValor());
            razaoDto.setValorCredito(BigDecimal.ZERO);
            lancamentosDto.add(razaoDto);
        }

        lancamentos = dataManager.load(Lancamento.class)
                .query("select l from Lancamento l " +
                        "where l.dataLancamento >= :dataInicial " +
                        "and l.dataLancamento <= :dataFinal " +
                        "and (l.contaCredora.codigo >= :codCtaInicial) " +
                        "and (l.contaCredora.codigo <= :codCtaFinal) " +
                        "and l.codEmpresa = :codEmpresa " +
                        "order by l.contaCredora.codigo, l.dataLancamento, l.numero")
                .parameter("codEmpresa", codEmpresa)
                .parameter("dataInicial", configRel.getDataInicial())
                .parameter("dataFinal", configRel.getDataFinal())
                .parameter("codCtaInicial", configRel.getContaInicial())
                .parameter("codCtaFinal", configRel.getContaFinal())
                .list();
        contaAux = "";
        // saldoAux = BigDecimal.ZERO;
        // saldoAnterior = BigDecimal.ZERO;
        for (Lancamento lancamento: lancamentos) {
            if (contaAux.compareTo(lancamento.getContaCredora().getCodigo()) != 0) {
                saldoAnterior = calcularSaldoAnterior(lancamento, configRel, false);
                saldoAux = saldoAnterior;
                contaAux = lancamento.getContaCredora().getCodigo();
            }
            RazaoDto razaoDto = dataManager.create(RazaoDto.class);
            razaoDto.setNumero(lancamento.getNumero());
            razaoDto.setDataLancamento(utilGeralService.localDateToSqlDate(lancamento.getDataLancamento()));
            razaoDto.setCodConta(lancamento.getContaCredora().getCodigo());
            razaoDto.setNmConta(lancamento.getContaCredora().getNome());
            razaoDto.setCodContraPartida(lancamento.getContaDevedora().getCodigo());
            razaoDto.setNmContraPartida(lancamento.getContaDevedora().getNome());
            String st = "";
            if (lancamento.getHistoricoContabil() != null) {
                st = lancamento.getHistoricoContabil().getCodigo().toString() + " ";
                st = st + lancamento.getHistoricoContabil().getDescricao() + " ";
            }
            st = st + lancamento.getComplementoHistorico();
            razaoDto.setHistorico(st);
            razaoDto.setSaldoAnterior(saldoAnterior);
            razaoDto.setSaldoAnteriorComLetra(utilGeralService.valorComLetra(saldoAnterior));
            saldoAux = saldoAux.subtract(lancamento.getValor());
            razaoDto.setSaldoAtual(saldoAux);
            razaoDto.setSaldoAtualComLetra(utilGeralService.valorComLetra(saldoAux));
            razaoDto.setValorDebito(BigDecimal.ZERO);
            razaoDto.setValorCredito(lancamento.getValor());
            lancamentosDto.add(razaoDto);
        }
        return lancamentosDto;
    }

    public BigDecimal calcularSaldoAnterior(Lancamento lancamento, ConfigRel configRel, boolean debito) {
        int ano = configRel.getDataInicial().getYear();
        int mes = configRel.getDataInicial().getMonthValue() - 1;

        Calendar cal = Calendar.getInstance();
        cal.set(ano, mes, 1);
        LocalDate dtAnterior = new Date(cal.getTime().getTime()).toLocalDate();

        ContaContabil conta;
        if (debito) {
            conta = lancamento.getContaDevedora();
        } else {
            conta = lancamento.getContaCredora();
        }

        List<SaldoConta> saldos = dataManager.load(SaldoConta.class)
                .query("select s from SaldoConta s " +
                        "where s.contaContabil = :conta " +
                        "order by s.mes")
                .parameter("conta", conta)
                .list();
        BigDecimal saldoAnterior = saldos.get(mes).getSaldoAnterior(); // configRel.getDataInicial().getMonthValue() -1).getSldAnt();

        List<Lancamento> lancs = (dataManager.load(Lancamento.class)
                .query("select l from Lancamento l " +
                        "where l.dataLancamento >= :dataAnterior " +
                        "and l.dataLancamento < :dataInicial " +
                        "and l.contaDevedora = :conta")
                .parameter("dataAnterior", dtAnterior)
                .parameter("dataInicial", configRel.getDataInicial())
                .parameter("conta", conta)
                .list());
        BigDecimal debitoAnterior = BigDecimal.ZERO;
        for (Lancamento lanc: lancs) {
            debitoAnterior = debitoAnterior.add(lanc.getValor());
        }

        List<Lancamento> lancs2 = (dataManager.load(Lancamento.class)
                .query("select l from Lancamento l " +
                        "where l.dataLancamento >= :dataAnterior " +
                        "and l.dataLancamento < :dataInicial " +
                        "and l.contaCredora = :conta")
                .parameter("dataAnterior", dtAnterior)
                .parameter("dataInicial", configRel.getDataInicial())
                .parameter("conta", conta)
                .list());
        BigDecimal creditoAnterior = BigDecimal.ZERO;
        for (Lancamento lanc: lancs2) {
            creditoAnterior = creditoAnterior.add(lanc.getValor());
        }

        return saldoAnterior.add(debitoAnterior).subtract(creditoAnterior);
    }

    public void importarLancamentos() {
        ConfigRel configRel = utilGeralService.prepararConfigRel();
        Integer codEmpresa = utilGeralService.getCodEmpresa();

        LocalDate dataLancamentoInicial = configRel.getDataLancamentoInicial() == null ?
                LocalDate.now() :
                configRel.getDataLancamentoInicial();
        LocalDate dataLancamentoFinal = configRel.getDataLancamentoFinal() == null ?
                LocalDate.now() :
                configRel.getDataLancamentoFinal();

        LocalDate dataInicial = LocalDate.of(utilGeralService.getAnoContabil(),
                utilGeralService.getMesContabil(),
                1);
        LocalDate dataFinal = dataInicial.with(TemporalAdjusters.lastDayOfMonth());
        int ano = dataInicial.getYear(), mes = dataInicial.getMonthValue();

        if (dataLancamentoInicial.getYear() != utilGeralService.getAnoContabil() ||
                dataLancamentoInicial.getMonthValue() != utilGeralService.getMesContabil() ||
                dataLancamentoFinal.getYear() != utilGeralService.getAnoContabil() ||
                dataLancamentoFinal.getMonthValue() != utilGeralService.getMesContabil()) {
            dialogs.createMessageDialog()
                    .withHeader("Importação de lançamentos")
                    .withText("Intervalo de datas fora do mês contábil")
                    .open();
            return;
        }

        List<LancamentoTmp> lancamentoTmps = dataManager.load(LancamentoTmp.class)
                .query("select e from LancamentoTmp e " +
                        "where e.dataLancamento between :dataInicial and :dataFinal " +
                        "and e.codEmpresa = :codEmpresa " +
                        "order by e.numero")
                .parameter("dataInicial", dataLancamentoInicial)
                .parameter("dataFinal", dataLancamentoFinal)
                .parameter("codEmpresa", codEmpresa)
                .list();
        long lancamentoFinal = 0L;
        List<Lancamento> lancamentos = new ArrayList<>();
        for (LancamentoTmp lanc: lancamentoTmps) {
            Lancamento lancamento = dataManager.create(Lancamento.class);
            lancamento.setId(lanc.getId());
            lancamento.setVersion(lanc.getVersion());
            lancamento.setNumero(lanc.getNumero());
            lancamento.setCreatedBy(lanc.getCreatedBy());
            lancamento.setCreatedDate(lanc.getCreatedDate());
            lancamento.setLastModifiedBy(lanc.getLastModifiedBy());
            lancamento.setLastModifiedDate(lanc.getLastModifiedDate());
            lancamento.setComplementoHistorico(lanc.getComplementoHistorico());
//            lancamento.setContaCredora(lanc.getContaCredora());
//            lancamento.setContaDevedora(lanc.getContaDevedora());
//            lancamento.setHistoricoContabil(lanc.getHistoricoContabil());
//            lancamento.setCentroCusto(lanc.getCentroCusto());
            ContaContabil conta = lerContaContabil(lanc.getDevedora(), codEmpresa);
            lancamento.setContaDevedora(conta);
            conta = lerContaContabil(lanc.getCredora(), codEmpresa);
            lancamento.setContaCredora(conta);
            HistoricoContabil historico = lerHistoricoContabil(lanc.getHist(), codEmpresa);
            lancamento.setHistoricoContabil(historico);
            CentroCusto centro = lerCentroCusto(lanc.getcCusto(), codEmpresa);
            lancamento.setCentroCusto(centro);
            lancamento.setAno(lanc.getAno());
            lancamento.setMes(lanc.getMes());
            lancamento.setDia(lanc.getDia());
            lancamento.setValor(lanc.getValor());
            lancamento.setCodEmpresa(codEmpresa);
            lancamento.setDataLancamento(lanc.getDataLancamento());
            lancamento.setOrigem(lanc.getOrigem());
            ano = lancamento.getAno();
            mes = lancamento.getMes();
            lancamentos.add(lancamento);
            lancamentoFinal++;
        }
        if (lancamentos.isEmpty()) {
            dialogs.createMessageDialog()
                    .withHeader("Êrro na importação de lançamentos")
                    .withText("Importação não efetuada")
                    .open();
        }
        else {
            SaveContext saveContext = new SaveContext();
            for (Lancamento lancamento : lancamentos) {
                saveContext.saving(lancamento);
            }
            dataManager.save(saveContext);
            String st = "lancamento_seq_" + String.format("%4d%02d", ano, mes);
            sequences.setCurrentValue(Sequence.withName(st), lancamentoFinal);

            dialogs.createMessageDialog()
                    .withHeader("Importação de lançamentos")
                    .withText("Importação concluída com sucesso!")
                    .open();
        }
    }

    private CentroCusto lerCentroCusto(String centro, Integer codEmpresa) {
        if (centro == null) {
            return null;
        }
        return dataManager.load(CentroCusto.class)
                .query("select e from CentroCusto e " +
                        "where e.codigo = :codigo " +
                        "and e.codEmpresa = :codEmpresa")
                .parameter("codigo", centro)
                .parameter("codEmpresa", codEmpresa)
                .optional()
                .orElse(null);
    }

    private HistoricoContabil lerHistoricoContabil(Integer hist, Integer codEmpresa) {
        return dataManager.load(HistoricoContabil.class)
                .query("select e from HistoricoContabil e " +
                        "where e.codigo = :codigo " +
                        "and e.codEmpresa = :codEmpresa")
                .parameter("codigo", hist)
                .parameter("codEmpresa", codEmpresa)
                .optional()
                .orElse(null);
    }

    private ContaContabil lerContaContabil(String conta, Integer codEmpresa) {
        return dataManager.load(ContaContabil.class)
                .query("select e from ContaContabil e " +
                        "where e.codigo = :conta " +
                        "and e.codEmpresa = :codEmpresa")
                .parameter("conta", conta)
                .parameter("codEmpresa", codEmpresa)
                .optional()
                .orElse(null);
    }

    public List<LancamentoTmp> getLancamentosTmp() {
        return dataManager.load(LancamentoTmp.class)
                .query("select e from LancamentoTmp e " +
                        "order by e.numero")
                .list();
    }

    public void gravarLancamento(LancamentoTmp lancamentoTmp) {
        Lancamento lancamento = dataManager.create(Lancamento.class);
        int codEmpresa = utilGeralService.getCodEmpresa();

        lancamento.setId(lancamentoTmp.getId());
        lancamento.setVersion(lancamentoTmp.getVersion());
        lancamento.setNumero(lancamentoTmp.getNumero());
        lancamento.setCreatedBy(lancamentoTmp.getCreatedBy());
        lancamento.setCreatedDate(lancamentoTmp.getCreatedDate());
        lancamento.setLastModifiedBy(lancamentoTmp.getLastModifiedBy());
        lancamento.setLastModifiedDate(lancamentoTmp.getLastModifiedDate());
        lancamento.setComplementoHistorico(lancamentoTmp.getComplementoHistorico());
//            lancamento.setContaCredora(lancamentoTmp.getContaCredora());
//            lancamento.setContaDevedora(lancamentoTmp.getContaDevedora());
//            lancamento.setHistoricoContabil(lancamentoTmp.getHistoricoContabil());
//            lancamento.setCentroCusto(lancamentoTmp.getCentroCusto());
        ContaContabil conta = lerContaContabil(lancamentoTmp.getDevedora(), codEmpresa);
        lancamento.setContaDevedora(conta);
        conta = lerContaContabil(lancamentoTmp.getCredora(), codEmpresa);
        lancamento.setContaCredora(conta);
        HistoricoContabil historico = lerHistoricoContabil(lancamentoTmp.getHist(), codEmpresa);
        lancamento.setHistoricoContabil(historico);
        CentroCusto centro = lerCentroCusto(lancamentoTmp.getcCusto(), codEmpresa);
        lancamento.setCentroCusto(centro);
        lancamento.setAno(lancamentoTmp.getAno());
        lancamento.setMes(lancamentoTmp.getMes());
        lancamento.setDia(lancamentoTmp.getDia());
        lancamento.setValor(lancamentoTmp.getValor());
        lancamento.setCodEmpresa(codEmpresa);
        lancamento.setDataLancamento(lancamentoTmp.getDataLancamento());
        lancamento.setOrigem(lancamentoTmp.getOrigem());
        dataManager.saveWithoutReload(lancamento);
    }
}