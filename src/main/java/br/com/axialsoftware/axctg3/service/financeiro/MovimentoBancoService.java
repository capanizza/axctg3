package br.com.axialsoftware.axctg3.service.financeiro;

import br.com.axialsoftware.axctg3.entity.cadastros.ConfigRel;
import br.com.axialsoftware.axctg3.entity.contabil.ContaContabil;
import br.com.axialsoftware.axctg3.entity.financeiro.ItemMovimentoBanco;
import br.com.axialsoftware.axctg3.entity.financeiro.MovimentoBanco;
import br.com.axialsoftware.axctg3.entity.financeiro.MovimentoBancoDto;
import br.com.axialsoftware.axctg3.service.RelatorioService;
import br.com.axialsoftware.axctg3.service.UtilGeralService;
import io.jmix.core.DataManager;
import net.sf.jasperreports.engine.JRDataSource;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

@Service
public class MovimentoBancoService {

    private final UtilGeralService utilGeralService;
    private final RelatorioService relatorioService;
    private final DataManager dataManager;
    private final UtilFinanceiroService utilFinanceiroService;

    public MovimentoBancoService(UtilGeralService utilGeralService, RelatorioService relatorioService, DataManager dataManager, UtilFinanceiroService utilFinanceiroService) {
        this.utilGeralService = utilGeralService;
        this.relatorioService = relatorioService;
        this.dataManager = dataManager;
        this.utilFinanceiroService = utilFinanceiroService;
    }

    public void listarMovimentoBanco(ConfigRel configRel) {
        Integer codEmpresa = utilGeralService.getCodEmpresa();
        String tituloRelatorio = "Movimento banco";
        String nomeRelatorio = "MovimentoBanco.jasper";
        String nomeSaida = "MovimentoBanco.pdf";
        String nomeEmpresa = utilGeralService.getNomeEmpresa();
        SimpleDateFormat df = new SimpleDateFormat("dd/MM/yyyy");
        java.sql.Date dtInicial = utilGeralService.localDateToSqlDate(configRel.getDataMovimentoBancoInicialListagem());
        java.sql.Date dtFinal = utilGeralService.localDateToSqlDate(configRel.getDataMovimentoBancoFinalListagem());
        String periodoRelatorio = "Período: " + df.format(dtInicial) + " a " + df.format(dtFinal);

        var parametros = new HashMap<String, Object>();
        parametros.put("TITULO_RELATORIO", tituloRelatorio);
        parametros.put("NOME_EMPRESA", nomeEmpresa);
        parametros.put("PERIODO_RELATORIO", periodoRelatorio);

        List<MovimentoBancoDto> movimentoBancoDto = prepararMovimentoBancoDto(codEmpresa, configRel);

        JRDataSource dataSource = new JRBeanCollectionDataSource(movimentoBancoDto);

        relatorioService.emitirRelatorio(nomeRelatorio, dataSource, parametros, nomeSaida);
    }

    public void listarMovimentoFinanceiro(ConfigRel configRel) {
        Integer codEmpresa = utilGeralService.getCodEmpresa();
        String tituloRelatorio = "Movimento financeiro";
        String nomeRelatorio = "MovimentoFinanceiro.jasper";
        String nomeSaida = "MovimentoFinanceiro.pdf";
        String nomeEmpresa = utilGeralService.getNomeEmpresa();
        SimpleDateFormat df = new SimpleDateFormat("dd/MM/yyyy");
        java.sql.Date dtInicial = utilGeralService.localDateToSqlDate(configRel.getDataMovimentoBancoInicialListagem());
        java.sql.Date dtFinal = utilGeralService.localDateToSqlDate(configRel.getDataMovimentoBancoFinalListagem());
        String periodoRelatorio = "Período: " + df.format(dtInicial) + " a " + df.format(dtFinal);
        Integer banco = configRel.getBancoMovimentoBanco();

        var parametros = new HashMap<String, Object>();
        parametros.put("TITULO_RELATORIO", tituloRelatorio);
        parametros.put("NOME_EMPRESA", nomeEmpresa);
        parametros.put("PERIODO_RELATORIO", periodoRelatorio);
        parametros.put("BANCO", "Banco: " + utilFinanceiroService.getNomeBanco(banco));

        List<MovimentoBancoDto> movimentoBancoDto = prepararMovimentoBancoDto(codEmpresa, configRel);

        JRDataSource dataSource = new JRBeanCollectionDataSource(movimentoBancoDto);

        relatorioService.emitirRelatorio(nomeRelatorio, dataSource, parametros, nomeSaida);
    }

    private List<MovimentoBancoDto> prepararMovimentoBancoDto(Integer codEmpresa, ConfigRel configRel) {
        Integer banco = configRel.getBancoMovimentoBanco();
        String listarTodos = banco == null ? "sim" : "nao";
        Boolean tipoSaldo = false;

        BigDecimal saldoAnterior = saldoAnteriorBanco(configRel);

        List<MovimentoBancoDto> movimentoBancosDto = new ArrayList<>();
        List<MovimentoBanco> movimentoBancos = dataManager.load(MovimentoBanco.class)
                .query("select e from MovimentoBanco e " +
                        "where e.data between :dataInicial and :dataFinal " +
                        "and (:listarTodos = 'sim' or e.banco.codigo = :banco) " +
                        "and e.tipoSaldo = :tipoSaldo " +
                        "and e.codEmpresa = :codEmpresa " +
                        "order by e.data, e.lancamento")
                .parameter("codEmpresa", codEmpresa)
                .parameter("dataInicial", configRel.getDataMovimentoBancoInicialListagem())
                .parameter("dataFinal", configRel.getDataMovimentoBancoFinalListagem())
                .parameter("banco", banco)
                .parameter("listarTodos", listarTodos)
                .parameter("tipoSaldo", tipoSaldo)
                .list();
        for (MovimentoBanco movimentoBanco : movimentoBancos) {
            MovimentoBancoDto movimentoBancoDto = dataManager.create(MovimentoBancoDto.class);
            movimentoBancoDto.setTipo(0); // movimento bancário
            movimentoBancoDto.setLancamento(movimentoBanco.getLancamento());
            movimentoBancoDto.setData(utilGeralService.localDateToSqlDate(movimentoBanco.getData()));
            movimentoBancoDto.setNomeBanco(String.format("%d %s", movimentoBanco.getBanco().getCodigo(), movimentoBanco.getBanco().getNome()));
            movimentoBancoDto.setNomeHistoricoFinanceiro(String.format("%d %s", movimentoBanco.getHistoricoFinanceiro().getCodigo(), movimentoBanco.getHistoricoFinanceiro().getNome()));
            movimentoBancoDto.setValor(movimentoBanco.getValor());
            if (movimentoBanco.getEntrada()) {
                movimentoBancoDto.setValorEntrada(movimentoBanco.getValor());
                movimentoBancoDto.setValorSaida(BigDecimal.ZERO);
            } else {
                movimentoBancoDto.setValorEntrada(BigDecimal.ZERO);
                movimentoBancoDto.setValorSaida(movimentoBanco.getValor());
            }
            movimentoBancoDto.setDocumento(utilGeralService.semNull(movimentoBanco.getDocumento()));
            movimentoBancoDto.setDescricao(utilGeralService.semNull(movimentoBanco.getDescricao()));
            if (movimentoBanco.getContaContabil() == null) {
                movimentoBancoDto.setNomeContaContabil("");
            } else {
                movimentoBancoDto.setNomeContaContabil(String.format("%s %s", movimentoBanco.getContaContabil().getCodigo(), movimentoBanco.getContaContabil().getNome()));
            }
            movimentoBancoDto.setSaldo(saldoAnterior.add(movimentoBancoDto.getValorEntrada().subtract(movimentoBancoDto.getValorSaida())));
            saldoAnterior = movimentoBancoDto.getSaldo();
            movimentoBancoDto.setEntrada(movimentoBanco.getEntrada());
            movimentoBancosDto.add(movimentoBancoDto);
        }
        return movimentoBancosDto;
    }

    private BigDecimal saldoAnteriorBanco(ConfigRel configRel) {
        Integer codEmpresa = utilGeralService.getCodEmpresa();
        LocalDate dataInicial = configRel.getDataMovimentoBancoInicialListagem();
        Boolean tipoSaldo = true;
        BigDecimal saldoAnterior;
        List<MovimentoBanco> movimentoBancos = dataManager.load(MovimentoBanco.class)
                .query("select e from MovimentoBanco e " +
                        "where e.data < :dataInicial " +
                        "and e.tipoSaldo = :tipoSaldo " +
                        "and e.codEmpresa = :codEmpresa " +
                        "order by e.data desc")
                .parameter("dataInicial", dataInicial)
                .parameter("tipoSaldo", tipoSaldo)
                .parameter("codEmpresa", codEmpresa)
                .list();
        if (movimentoBancos.isEmpty()) {
            saldoAnterior = BigDecimal.ZERO;
        } else {
            MovimentoBanco movimentoBanco = movimentoBancos.getFirst();
            BigDecimal saldo = movimentoBanco.getValor();
            if (movimentoBanco.getEntrada()) {
                saldoAnterior = saldo;
            } else {
                saldoAnterior = saldo.multiply(BigDecimal.valueOf(-1));
            }
        }
        return saldoAnterior;
    }

    /** Lança contabilmente os itens ainda não contabilizados de um movimento bancário. */
    public void lancamentos(MovimentoBanco movimentoBanco) {
        for (ItemMovimentoBanco itemMovimentoBanco : movimentoBanco.getItens()) {
            ContaContabil contaMovimentoBancario = utilGeralService.getEmpresa().getContaMovimentoBancario();
            ContaContabil contaContabil = movimentoBanco.getContaContabil();
            ContaContabil contaBanco = movimentoBanco.getBanco().getContaContabil();
            ContaContabil contaDevedora;
            ContaContabil contaCredora;
            if (contaContabil == null) {
                if (movimentoBanco.getEntrada()) {
                    contaDevedora = contaBanco;
                    contaCredora = contaMovimentoBancario;
                } else {
                    contaDevedora = contaMovimentoBancario;
                    contaCredora = contaBanco;
                }
            } else {
                if (movimentoBanco.getEntrada()) {
                    contaDevedora = contaBanco;
                    contaCredora = contaContabil;
                } else {
                    contaDevedora = contaContabil;
                    contaCredora = contaBanco;
                }
            }
            String complementoHistorico = movimentoBanco.getLancamento() + " " + movimentoBanco.getDescricao();
            LocalDate data = movimentoBanco.getData();
            int dia = data.getDayOfMonth();
            BigDecimal valor = movimentoBanco.getValor();

            utilFinanceiroService.gerarLancamento(movimentoBanco,
                    dia,
                    contaDevedora,
                    contaCredora,
                    valor,
                    null,
                    complementoHistorico);

            itemMovimentoBanco.setLancado(true);
            dataManager.save(itemMovimentoBanco);
        }
    }

}
