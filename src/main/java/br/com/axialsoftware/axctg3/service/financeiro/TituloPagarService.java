package br.com.axialsoftware.axctg3.service.financeiro;

import br.com.axialsoftware.axctg3.entity.cadastros.ConfigRel;
import br.com.axialsoftware.axctg3.entity.contabil.ContaContabil;
import br.com.axialsoftware.axctg3.entity.contabil.HistoricoContabil;
import br.com.axialsoftware.axctg3.entity.financeiro.HistoricoFinanceiro;
import br.com.axialsoftware.axctg3.entity.financeiro.ItemPagar;
import br.com.axialsoftware.axctg3.entity.financeiro.TituloPagar;
import br.com.axialsoftware.axctg3.entity.financeiro.TituloPagarDto;
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
public class TituloPagarService {

    private final DataManager dataManager;
    private final UtilGeralService utilGeralService;
    private final RelatorioService relatorioService;
    private final UtilFinanceiroService utilFinanceiroService;

    public TituloPagarService(DataManager dataManager, UtilGeralService utilGeralService, RelatorioService relatorioService, UtilFinanceiroService utilFinanceiroService) {
        this.dataManager = dataManager;
        this.utilGeralService = utilGeralService;
        this.relatorioService = relatorioService;
        this.utilFinanceiroService = utilFinanceiroService;
    }

    /** Soma dos itens de baixa (item 2+) já lançados para este título. */
    public BigDecimal valorRecebidoTitulo(TituloPagar tituloPagar) {
        BigDecimal valor = dataManager.loadValue(
                        "select sum(e.valor) from ItemPagar e " +
                                "where e.tituloPagar = :tituloPagar " +
                                "and e.historicoFinanceiro.baixa = true",
                        BigDecimal.class)
                .parameter("tituloPagar", tituloPagar)
                .one();
        return valor == null ? BigDecimal.ZERO : valor;
    }

    public Boolean aberto(TituloPagar tituloPagar) {
        BigDecimal valorBaixa = valorRecebidoTitulo(tituloPagar);
        return valorBaixa.compareTo(tituloPagar.getValor()) < 0;
    }

    public BigDecimal valorRecebidoTituloAteData(TituloPagar tituloPagar, LocalDate dt) {
        BigDecimal valor = dataManager.loadValue(
                        "select sum(e.valor) from ItemPagar e " +
                                "where e.tituloPagar = :tituloPagar " +
                                "and e.historicoFinanceiro.baixa = true " +
                                "and e.data <= :dt",
                        BigDecimal.class)
                .parameter("tituloPagar", tituloPagar)
                .parameter("dt", dt)
                .one();
        return valor == null ? BigDecimal.ZERO : valor;
    }

    public Boolean abertoAteData(TituloPagar tituloPagar, LocalDate dt) {
        BigDecimal valorBaixa = valorRecebidoTituloAteData(tituloPagar, dt);
        return valorBaixa.compareTo(tituloPagar.getValor()) < 0;
    }

    public void listarEntradaTitulosPagar(ConfigRel configRel) {
        Integer codEmpresa = utilGeralService.getCodEmpresa();
        String tituloRelatorio = "Entrada de títulos a pagar";
        String nomeRelatorio = "EntradaTituloPagar.jasper";
        String nomeSaida = "EntradaTituloPagar.pdf";
        String nomeEmpresa = utilGeralService.getNomeEmpresa();
        SimpleDateFormat df = new SimpleDateFormat("dd/MM/yyyy");
        java.sql.Date dtInicial = utilGeralService.localDateToSqlDate(configRel.getDataEmissaoPagarInicialListagem());
        java.sql.Date dtFinal = utilGeralService.localDateToSqlDate(configRel.getDataEmissaoPagarFinalListagem());
        String periodoRelatorio = "Período: " + df.format(dtInicial) + " a " + df.format(dtFinal);

        var parametros = new HashMap<String, Object>();
        parametros.put("TITULO_RELATORIO", tituloRelatorio);
        parametros.put("NOME_EMPRESA", nomeEmpresa);
        parametros.put("PERIODO_RELATORIO", periodoRelatorio);

        List<TituloPagarDto> tituloPagarsDto = prepararEmissaoDto(codEmpresa, configRel);

        JRDataSource dataSource = new JRBeanCollectionDataSource(tituloPagarsDto);

        relatorioService.emitirRelatorio(nomeRelatorio, dataSource, parametros, nomeSaida);
    }

    private List<TituloPagarDto> prepararEmissaoDto(Integer codEmpresa, ConfigRel configRel) {
        List<TituloPagarDto> tituloPagarsDto = new ArrayList<>();
        List<TituloPagar> tituloPagars = dataManager.load(TituloPagar.class)
                .query("select e from TituloPagar e " +
                        "where e.dataEmissao between :dataEmissaoInicial and :dataEmissaoFinal " +
                        "and e.codEmpresa = :codEmpresa " +
                        "order by e.dataEmissao, e.numero")
                .parameter("codEmpresa", codEmpresa)
                .parameter("dataEmissaoInicial", configRel.getDataEmissaoPagarInicialListagem())
                .parameter("dataEmissaoFinal", configRel.getDataEmissaoPagarFinalListagem())
                .list();
        for (TituloPagar tituloPagar : tituloPagars) {
            TituloPagarDto tituloPagarDto = dataManager.create(TituloPagarDto.class);
            tituloPagarDto.setNumero(tituloPagar.getNumero());
            tituloPagarDto.setDocumento(tituloPagar.getDocumento());
            tituloPagarDto.setDataEmissao(utilGeralService.localDateToSqlDate(tituloPagar.getDataEmissao()));
            tituloPagarDto.setDataVencimento(utilGeralService.localDateToSqlDate(tituloPagar.getDataVencimento()));
            tituloPagarDto.setParceiro(tituloPagar.getParceiro().getCodigo());
            tituloPagarDto.setNomeParceiro(String.format("%d %s", tituloPagar.getParceiro().getCodigo(), tituloPagar.getParceiro().getNome()));
            tituloPagarDto.setValor(tituloPagar.getValor());
            tituloPagarsDto.add(tituloPagarDto);
        }
        return tituloPagarsDto;
    }

    /** Lança contabilmente o item de emissão (item 1) de cada título ainda não contabilizado. */
    public void lancamentosEmissao(TituloPagar tituloPagar) {
        for (ItemPagar itemPagar : tituloPagar.getItens()) {
            if (itemPagar.getHistoricoFinanceiro().getBaixa()) {
                continue;
            }
            ContaContabil contaParceiroEntrada = utilGeralService.getEmpresa().getContaParceiroEntrada(); // conta credora
            ContaContabil contaEntrada = tituloPagar.getContaContabil(); // conta devedora
            HistoricoFinanceiro historicoFinanceiro = itemPagar.getHistoricoFinanceiro();
            HistoricoContabil historicoEntrada = historicoFinanceiro.getHistoricoContabilPagar();
            String complementoHistorico = tituloPagar.getNumero() + " fornecedor: " + tituloPagar.getParceiro().getApelido();
            LocalDate dataEmissao = itemPagar.getData();
            int dia = dataEmissao.getDayOfMonth();
            BigDecimal valor = itemPagar.getValor();

            utilFinanceiroService.gerarLancamento(tituloPagar,
                    dia,
                    contaEntrada,
                    contaParceiroEntrada,
                    valor,
                    historicoEntrada,
                    complementoHistorico);

            itemPagar.setContabilizado(true);
            dataManager.save(itemPagar);
        }
    }

    public void tituloPagarVencimento(ConfigRel configRel) {
        Integer codEmpresa = utilGeralService.getCodEmpresa();
        String tituloRelatorio = "Títulos a pagar por vencimento";
        String nomeRelatorio = "VencimentoTituloPagar.jasper";
        String nomeSaida = "VencimentoTituloPagar.pdf";
        String nomeEmpresa = utilGeralService.getNomeEmpresa();
        SimpleDateFormat df = new SimpleDateFormat("dd/MM/yyyy");
        java.sql.Date dtEmissaoInicial = utilGeralService.localDateToSqlDate(configRel.getDataEmissaoPagarInicialListagem());
        java.sql.Date dtEmissaoFinal = utilGeralService.localDateToSqlDate(configRel.getDataEmissaoPagarFinalListagem());
        java.sql.Date dtVencimentoInicial = utilGeralService.localDateToSqlDate(configRel.getDataVencimentoPagarInicialListagem());
        java.sql.Date dtVencimentoFinal = utilGeralService.localDateToSqlDate(configRel.getDataVencimentoPagarFinalListagem());
        String periodoEmissao = "Emissão: " + df.format(dtEmissaoInicial) + " a " + df.format(dtEmissaoFinal);
        String periodoVencimento = "Vencimento: " + df.format(dtVencimentoInicial) + " a " + df.format(dtVencimentoFinal);

        var parametros = new HashMap<String, Object>();
        parametros.put("TITULO_RELATORIO", tituloRelatorio);
        parametros.put("NOME_EMPRESA", nomeEmpresa);
        parametros.put("PERIODO_EMISSAO", periodoEmissao);
        parametros.put("PERIODO_VENCIMENTO", periodoVencimento);

        List<TituloPagarDto> tituloPagarsDto = prepararVencimentoDto(codEmpresa, configRel);

        JRDataSource dataSource = new JRBeanCollectionDataSource(tituloPagarsDto);

        relatorioService.emitirRelatorio(nomeRelatorio, dataSource, parametros, nomeSaida);
    }

    private List<TituloPagarDto> prepararVencimentoDto(Integer codEmpresa, ConfigRel configRel) {
        List<TituloPagarDto> tituloPagarsDto = new ArrayList<>();
        List<TituloPagar> tituloPagars = dataManager.load(TituloPagar.class)
                .query("select e from TituloPagar e " +
                        "where e.dataVencimento between :dataVencimentoInicial and :dataVencimentoFinal " +
                        "and e.dataEmissao between :dataEmissaoInicial and :dataEmissaoFinal " +
                        "and e.codEmpresa = :codEmpresa " +
                        "order by e.dataVencimento, e.numero")
                .parameter("dataVencimentoInicial", configRel.getDataVencimentoPagarInicialListagem())
                .parameter("dataVencimentoFinal", configRel.getDataVencimentoPagarFinalListagem())
                .parameter("dataEmissaoInicial", configRel.getDataEmissaoPagarInicialListagem())
                .parameter("dataEmissaoFinal", configRel.getDataEmissaoPagarFinalListagem())
                .parameter("codEmpresa", codEmpresa)
                .list();
        for (TituloPagar tituloPagar : tituloPagars) {
            if (!abertoAteData(tituloPagar, configRel.getDataEmissaoPagarFinalListagem())) {
                continue;
            }
            TituloPagarDto tituloPagarDto = dataManager.create(TituloPagarDto.class);
            tituloPagarDto.setNumero(tituloPagar.getNumero());
            tituloPagarDto.setDataEmissao(utilGeralService.localDateToSqlDate(tituloPagar.getDataEmissao()));
            tituloPagarDto.setDataVencimento(utilGeralService.localDateToSqlDate(tituloPagar.getDataVencimento()));
            tituloPagarDto.setParceiro(tituloPagar.getParceiro().getCodigo());
            tituloPagarDto.setNomeParceiro(String.format("%d %s", tituloPagar.getParceiro().getCodigo(), tituloPagar.getParceiro().getApelido()));
            tituloPagarDto.setValor(tituloPagar.getValor().subtract(valorRecebidoTituloAteData(tituloPagar, configRel.getDataEmissaoPagarFinalListagem())));
            tituloPagarsDto.add(tituloPagarDto);
        }
        return tituloPagarsDto;
    }

}
