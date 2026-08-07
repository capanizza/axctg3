package br.com.axialsoftware.axctg3.service.financeiro;

import br.com.axialsoftware.axctg3.entity.cadastros.ConfigRel;
import br.com.axialsoftware.axctg3.entity.financeiro.TituloReceber;
import br.com.axialsoftware.axctg3.entity.financeiro.TituloReceberDto;
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

/**
 * {@code lancamentosEmissao} do axctg-flow não foi portado — ver o Javadoc de
 * {@link TituloReceber}. O que segue (cálculo de aberto/valor baixado e os relatórios)
 * não depende disso.
 */
@Service
public class TituloReceberService {

    private final DataManager dataManager;
    private final UtilGeralService utilGeralService;
    private final RelatorioService relatorioService;

    public TituloReceberService(DataManager dataManager, UtilGeralService utilGeralService, RelatorioService relatorioService) {
        this.dataManager = dataManager;
        this.utilGeralService = utilGeralService;
        this.relatorioService = relatorioService;
    }

    /** Soma dos itens de baixa (item 2+) já lançados para este título. */
    public BigDecimal valorRecebidoTitulo(TituloReceber tituloReceber) {
        BigDecimal valor = dataManager.loadValue(
                        "select sum(e.valor) from ItemReceber e " +
                                "where e.tituloReceber = :tituloReceber " +
                                "and e.historicoFinanceiro.baixa = true",
                        BigDecimal.class)
                .parameter("tituloReceber", tituloReceber)
                .one();
        return valor == null ? BigDecimal.ZERO : valor;
    }

    public Boolean aberto(TituloReceber tituloReceber) {
        BigDecimal valorBaixa = valorRecebidoTitulo(tituloReceber);
        return valorBaixa.compareTo(tituloReceber.getValor()) < 0;
    }

    public BigDecimal valorRecebidoTituloAteData(TituloReceber tituloReceber, LocalDate dt) {
        BigDecimal valor = dataManager.loadValue(
                        "select sum(e.valor) from ItemReceber e " +
                                "where e.tituloReceber = :tituloReceber " +
                                "and e.historicoFinanceiro.baixa = true " +
                                "and e.data <= :dt",
                        BigDecimal.class)
                .parameter("tituloReceber", tituloReceber)
                .parameter("dt", dt)
                .one();
        return valor == null ? BigDecimal.ZERO : valor;
    }

    public Boolean abertoAteData(TituloReceber tituloReceber, LocalDate dt) {
        BigDecimal valorBaixa = valorRecebidoTituloAteData(tituloReceber, dt);
        return valorBaixa.compareTo(tituloReceber.getValor()) < 0;
    }

    public void listarEntradaTitulosReceber(ConfigRel configRel) {
        Integer codEmpresa = utilGeralService.getCodEmpresa();
        String tituloRelatorio = "Entrada de títulos a receber";
        String nomeRelatorio = "EntradaTituloReceber.jasper";
        String nomeSaida = "EntradaTituloReceber.pdf";
        String nomeEmpresa = utilGeralService.getNomeEmpresa();
        SimpleDateFormat df = new SimpleDateFormat("dd/MM/yyyy");
        java.sql.Date dtInicial = utilGeralService.localDateToSqlDate(configRel.getDataEmissaoReceberInicialListagem());
        java.sql.Date dtFinal = utilGeralService.localDateToSqlDate(configRel.getDataEmissaoReceberFinalListagem());
        String periodoRelatorio = "Período: " + df.format(dtInicial) + " a " + df.format(dtFinal);

        var parametros = new HashMap<String, Object>();
        parametros.put("TITULO_RELATORIO", tituloRelatorio);
        parametros.put("NOME_EMPRESA", nomeEmpresa);
        parametros.put("PERIODO_RELATORIO", periodoRelatorio);

        List<TituloReceberDto> tituloRecebersDto = prepararEmissaoDto(codEmpresa, configRel);

        JRDataSource dataSource = new JRBeanCollectionDataSource(tituloRecebersDto);

        relatorioService.emitirRelatorio(nomeRelatorio, dataSource, parametros, nomeSaida);
    }

    private List<TituloReceberDto> prepararEmissaoDto(Integer codEmpresa, ConfigRel configRel) {
        Integer banco = configRel.getBancoInicial();
        String listarTodos = banco == null ? "sim" : "nao";

        List<TituloReceberDto> tituloRecebersDto = new ArrayList<>();
        List<TituloReceber> tituloRecebers = dataManager.load(TituloReceber.class)
                .query("select e from TituloReceber e " +
                        "where e.dataEmissao between :dataEmissaoInicial and :dataEmissaoFinal " +
                        "and (:listarTodos = 'sim' or e.banco.codigo = :banco) " +
                        "and e.codEmpresa = :codEmpresa " +
                        "order by e.dataEmissao, e.numero")
                .parameter("codEmpresa", codEmpresa)
                .parameter("dataEmissaoInicial", configRel.getDataEmissaoReceberInicialListagem())
                .parameter("dataEmissaoFinal", configRel.getDataEmissaoReceberFinalListagem())
                .parameter("banco", banco)
                .parameter("listarTodos", listarTodos)
                .list();
        for (TituloReceber tituloReceber : tituloRecebers) {
            TituloReceberDto tituloReceberDto = dataManager.create(TituloReceberDto.class);
            tituloReceberDto.setNumero(tituloReceber.getNumero());
            tituloReceberDto.setDataEmissao(utilGeralService.localDateToSqlDate(tituloReceber.getDataEmissao()));
            tituloReceberDto.setDataVencimento(utilGeralService.localDateToSqlDate(tituloReceber.getDataVencimento()));
            tituloReceberDto.setParceiro(tituloReceber.getParceiro().getCodigo());
            tituloReceberDto.setNomeParceiro(String.format("%d %s", tituloReceber.getParceiro().getCodigo(), tituloReceber.getParceiro().getNome()));
            tituloReceberDto.setBanco(tituloReceber.getBanco().getCodigo());
            tituloReceberDto.setNomeBanco(String.format("%d %s", tituloReceber.getBanco().getCodigo(), tituloReceber.getBanco().getNome()));
            tituloReceberDto.setValor(tituloReceber.getValor());
            tituloRecebersDto.add(tituloReceberDto);
        }
        return tituloRecebersDto;
    }

    public void tituloReceberVencimento(ConfigRel configRel) {
        Integer codEmpresa = utilGeralService.getCodEmpresa();
        String tituloRelatorio = "Títulos a receber por vencimento";
        String nomeRelatorio = "VencimentoTituloReceber.jasper";
        String nomeSaida = "VencimentoTituloReceber.pdf";
        String nomeEmpresa = utilGeralService.getNomeEmpresa();
        SimpleDateFormat df = new SimpleDateFormat("dd/MM/yyyy");
        java.sql.Date dtEmissaoInicial = utilGeralService.localDateToSqlDate(configRel.getDataEmissaoReceberInicialListagem());
        java.sql.Date dtEmissaoFinal = utilGeralService.localDateToSqlDate(configRel.getDataEmissaoReceberFinalListagem());
        java.sql.Date dtVencimentoInicial = utilGeralService.localDateToSqlDate(configRel.getDataVencimentoReceberInicialListagem());
        java.sql.Date dtVencimentoFinal = utilGeralService.localDateToSqlDate(configRel.getDataVencimentoReceberFinalListagem());
        String periodoEmissao = "Emissão: " + df.format(dtEmissaoInicial) + " a " + df.format(dtEmissaoFinal);
        String periodoVencimento = "Vencimento: " + df.format(dtVencimentoInicial) + " a " + df.format(dtVencimentoFinal);

        var parametros = new HashMap<String, Object>();
        parametros.put("TITULO_RELATORIO", tituloRelatorio);
        parametros.put("NOME_EMPRESA", nomeEmpresa);
        parametros.put("PERIODO_EMISSAO", periodoEmissao);
        parametros.put("PERIODO_VENCIMENTO", periodoVencimento);

        List<TituloReceberDto> tituloRecebersDto = prepararVencimentoDto(codEmpresa, configRel);

        JRDataSource dataSource = new JRBeanCollectionDataSource(tituloRecebersDto);

        relatorioService.emitirRelatorio(nomeRelatorio, dataSource, parametros, nomeSaida);
    }

    private List<TituloReceberDto> prepararVencimentoDto(Integer codEmpresa, ConfigRel configRel) {
        List<TituloReceberDto> tituloRecebersDto = new ArrayList<>();
        List<TituloReceber> tituloRecebers = dataManager.load(TituloReceber.class)
                .query("select e from TituloReceber e " +
                        "where e.dataVencimento between :dataVencimentoInicial and :dataVencimentoFinal " +
                        "and e.dataEmissao between :dataEmissaoInicial and :dataEmissaoFinal " +
                        "and e.codEmpresa = :codEmpresa " +
                        "order by e.dataVencimento, e.numero")
                .parameter("dataVencimentoInicial", configRel.getDataVencimentoReceberInicialListagem())
                .parameter("dataVencimentoFinal", configRel.getDataVencimentoReceberFinalListagem())
                .parameter("dataEmissaoInicial", configRel.getDataEmissaoReceberInicialListagem())
                .parameter("dataEmissaoFinal", configRel.getDataEmissaoReceberFinalListagem())
                .parameter("codEmpresa", codEmpresa)
                .list();
        for (TituloReceber tituloReceber : tituloRecebers) {
            if (!abertoAteData(tituloReceber, configRel.getDataEmissaoReceberFinalListagem())) {
                continue;
            }
            TituloReceberDto tituloReceberDto = dataManager.create(TituloReceberDto.class);
            tituloReceberDto.setNumero(tituloReceber.getNumero());
            tituloReceberDto.setDataEmissao(utilGeralService.localDateToSqlDate(tituloReceber.getDataEmissao()));
            tituloReceberDto.setDataVencimento(utilGeralService.localDateToSqlDate(tituloReceber.getDataVencimento()));
            tituloReceberDto.setParceiro(tituloReceber.getParceiro().getCodigo());
            tituloReceberDto.setNomeParceiro(String.format("%d %s", tituloReceber.getParceiro().getCodigo(), tituloReceber.getParceiro().getApelido()));
            tituloReceberDto.setBanco(tituloReceber.getBanco().getCodigo());
            tituloReceberDto.setNomeBanco(String.format("%d %s", tituloReceber.getBanco().getCodigo(), tituloReceber.getBanco().getNome()));
            tituloReceberDto.setValor(tituloReceber.getValor().subtract(valorRecebidoTituloAteData(tituloReceber, configRel.getDataEmissaoReceberFinalListagem())));
            tituloRecebersDto.add(tituloReceberDto);
        }
        return tituloRecebersDto;
    }

}
