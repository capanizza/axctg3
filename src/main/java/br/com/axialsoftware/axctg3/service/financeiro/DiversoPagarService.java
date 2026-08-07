package br.com.axialsoftware.axctg3.service.financeiro;

import br.com.axialsoftware.axctg3.entity.cadastros.ConfigRel;
import br.com.axialsoftware.axctg3.entity.contabil.ContaContabil;
import br.com.axialsoftware.axctg3.entity.contabil.HistoricoContabil;
import br.com.axialsoftware.axctg3.entity.financeiro.DiversoPagar;
import br.com.axialsoftware.axctg3.entity.financeiro.DiversoPagarDto;
import br.com.axialsoftware.axctg3.entity.financeiro.HistoricoFinanceiro;
import br.com.axialsoftware.axctg3.entity.financeiro.ItemDiversoPagar;
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
public class DiversoPagarService {

    private final DataManager dataManager;
    private final UtilGeralService utilGeralService;
    private final RelatorioService relatorioService;
    private final UtilFinanceiroService utilFinanceiroService;

    public DiversoPagarService(DataManager dataManager, UtilGeralService utilGeralService, RelatorioService relatorioService, UtilFinanceiroService utilFinanceiroService) {
        this.dataManager = dataManager;
        this.utilGeralService = utilGeralService;
        this.relatorioService = relatorioService;
        this.utilFinanceiroService = utilFinanceiroService;
    }

    /** Soma dos itens de baixa (item 2+) já lançados para este diverso. */
    public BigDecimal valorRecebidoDiverso(DiversoPagar diversoPagar) {
        BigDecimal valor = dataManager.loadValue(
                        "select sum(e.valor) from ItemDiversoPagar e " +
                                "where e.diversoPagar = :diversoPagar " +
                                "and e.historicoFinanceiro.baixa = true",
                        BigDecimal.class)
                .parameter("diversoPagar", diversoPagar)
                .one();
        return valor == null ? BigDecimal.ZERO : valor;
    }

    public Boolean aberto(DiversoPagar diversoPagar) {
        BigDecimal valorBaixa = valorRecebidoDiverso(diversoPagar);
        return valorBaixa.compareTo(diversoPagar.getValor()) < 0;
    }

    public void listarEntradaDiversosPagar(ConfigRel configRel) {
        Integer codEmpresa = utilGeralService.getCodEmpresa();
        String tituloRelatorio = "Entrada de diversos a pagar";
        String nomeRelatorio = "EntradaDiversoPagar.jasper";
        String nomeSaida = "EntradaDiversoPagar.pdf";
        String nomeEmpresa = utilGeralService.getNomeEmpresa();
        SimpleDateFormat df = new SimpleDateFormat("dd/MM/yyyy");
        java.sql.Date dtInicial = utilGeralService.localDateToSqlDate(configRel.getDataEmissaoDiversoInicialListagem());
        java.sql.Date dtFinal = utilGeralService.localDateToSqlDate(configRel.getDataEmissaoDiversoFinalListagem());
        String periodoRelatorio = "Período: " + df.format(dtInicial) + " a " + df.format(dtFinal);

        var parametros = new HashMap<String, Object>();
        parametros.put("TITULO_RELATORIO", tituloRelatorio);
        parametros.put("NOME_EMPRESA", nomeEmpresa);
        parametros.put("PERIODO_RELATORIO", periodoRelatorio);

        List<DiversoPagarDto> diversoPagarsDto = prepararEmissaoDto(codEmpresa, configRel);

        JRDataSource dataSource = new JRBeanCollectionDataSource(diversoPagarsDto);

        relatorioService.emitirRelatorio(nomeRelatorio, dataSource, parametros, nomeSaida);
    }

    private List<DiversoPagarDto> prepararEmissaoDto(Integer codEmpresa, ConfigRel configRel) {
        List<DiversoPagarDto> diversoPagarsDto = new ArrayList<>();
        List<DiversoPagar> diversoPagars = dataManager.load(DiversoPagar.class)
                .query("select e from DiversoPagar e " +
                        "where e.dataEmissao between :dataEmissaoInicial and :dataEmissaoFinal " +
                        "and e.codEmpresa = :codEmpresa " +
                        "order by e.dataEmissao, e.numero")
                .parameter("codEmpresa", codEmpresa)
                .parameter("dataEmissaoInicial", configRel.getDataEmissaoDiversoInicialListagem())
                .parameter("dataEmissaoFinal", configRel.getDataEmissaoDiversoFinalListagem())
                .list();
        for (DiversoPagar diversoPagar : diversoPagars) {
            DiversoPagarDto diversoPagarDto = dataManager.create(DiversoPagarDto.class);
            diversoPagarDto.setNumero(diversoPagar.getNumero());
            diversoPagarDto.setDocumento(diversoPagar.getDocumento());
            diversoPagarDto.setDataEmissao(utilGeralService.localDateToSqlDate(diversoPagar.getDataEmissao()));
            diversoPagarDto.setDataVencimento(utilGeralService.localDateToSqlDate(diversoPagar.getDataVencimento()));
            diversoPagarDto.setParceiro(diversoPagar.getParceiro().getCodigo());
            diversoPagarDto.setNomeParceiro(String.format("%d %s", diversoPagar.getParceiro().getCodigo(), diversoPagar.getParceiro().getNome()));
            diversoPagarDto.setValor(diversoPagar.getValor());
            diversoPagarsDto.add(diversoPagarDto);
        }
        return diversoPagarsDto;
    }

    /** Lança contabilmente o item de emissão (item 1) de cada diverso ainda não contabilizado. */
    public void lancamentosEmissao(DiversoPagar diversoPagar) {
        for (ItemDiversoPagar itemDiverso : diversoPagar.getItens()) {
            if (itemDiverso.getHistoricoFinanceiro().getBaixa()) {
                continue;
            }
            ContaContabil contaParceiroEntrada = utilGeralService.getEmpresa().getContaParceiroEntrada(); // conta credora
            ContaContabil contaEntrada = diversoPagar.getContaContabil(); // conta devedora
            HistoricoFinanceiro historicoFinanceiro = itemDiverso.getHistoricoFinanceiro();
            HistoricoContabil historicoEntrada = historicoFinanceiro.getHistoricoContabilPagar();
            String complementoHistorico = diversoPagar.getNumero() + " fornecedor: " + diversoPagar.getParceiro().getApelido();
            LocalDate dataEmissao = itemDiverso.getData();
            int dia = dataEmissao.getDayOfMonth();
            BigDecimal valor = itemDiverso.getValor();

            utilFinanceiroService.gerarLancamento(diversoPagar,
                    dia,
                    contaEntrada,
                    contaParceiroEntrada,
                    valor,
                    historicoEntrada,
                    complementoHistorico);

            itemDiverso.setContabilizado(true);
            dataManager.save(itemDiverso);
        }
    }

}
