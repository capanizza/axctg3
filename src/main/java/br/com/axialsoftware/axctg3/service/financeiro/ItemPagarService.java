package br.com.axialsoftware.axctg3.service.financeiro;

import br.com.axialsoftware.axctg3.entity.cadastros.ConfigRel;
import br.com.axialsoftware.axctg3.entity.contabil.ContaContabil;
import br.com.axialsoftware.axctg3.entity.contabil.HistoricoContabil;
import br.com.axialsoftware.axctg3.entity.financeiro.HistoricoFinanceiro;
import br.com.axialsoftware.axctg3.entity.financeiro.ItemPagar;
import br.com.axialsoftware.axctg3.entity.financeiro.ItemPagarDto;
import br.com.axialsoftware.axctg3.entity.financeiro.TituloPagar;
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
public class ItemPagarService {

    private final DataManager dataManager;
    private final UtilGeralService utilGeralService;
    private final RelatorioService relatorioService;
    private final UtilFinanceiroService utilFinanceiroService;

    public ItemPagarService(DataManager dataManager, UtilGeralService utilGeralService, RelatorioService relatorioService, UtilFinanceiroService utilFinanceiroService) {
        this.dataManager = dataManager;
        this.utilGeralService = utilGeralService;
        this.relatorioService = relatorioService;
        this.utilFinanceiroService = utilFinanceiroService;
    }

    public void listarBaixaTitulosPagar(ConfigRel configRel) {
        Integer codEmpresa = utilGeralService.getCodEmpresa();
        String tituloRelatorio = "Baixa de títulos a pagar";
        String nomeRelatorio = "BaixaTituloPagar.jasper";
        String nomeSaida = "BaixaTituloPagar.pdf";
        String nomeEmpresa = utilGeralService.getNomeEmpresa();
        SimpleDateFormat df = new SimpleDateFormat("dd/MM/yyyy");
        java.sql.Date dtInicial = utilGeralService.localDateToSqlDate(configRel.getDataBaixaPagarInicialListagem());
        java.sql.Date dtFinal = utilGeralService.localDateToSqlDate(configRel.getDataBaixaPagarFinalListagem());
        String periodoRelatorio = "Período: " + df.format(dtInicial) + " a " + df.format(dtFinal);

        var parametros = new HashMap<String, Object>();
        parametros.put("TITULO_RELATORIO", tituloRelatorio);
        parametros.put("NOME_EMPRESA", nomeEmpresa);
        parametros.put("PERIODO_RELATORIO", periodoRelatorio);

        List<ItemPagarDto> itemPagarsDto = prepararBaixaDto(codEmpresa, configRel);

        JRDataSource dataSource = new JRBeanCollectionDataSource(itemPagarsDto);

        relatorioService.emitirRelatorio(nomeRelatorio, dataSource, parametros, nomeSaida);
    }

    private List<ItemPagarDto> prepararBaixaDto(Integer codEmpresa, ConfigRel configRel) {
        Integer banco = configRel.getBancoInicial();
        String listarTodos = banco == null ? "sim" : "nao";

        List<ItemPagarDto> itemPagarsDto = new ArrayList<>();
        List<ItemPagar> itemPagars = dataManager.load(ItemPagar.class)
                .query("select e from ItemPagar e " +
                        "where e.data between :dataBaixaInicial and :dataBaixaFinal " +
                        "and e.historicoFinanceiro.baixa = true " +
                        "and (:listarTodos = 'sim' or e.banco.codigo = :banco) " +
                        "and e.tituloPagar.codEmpresa = :codEmpresa " +
                        "order by e.data, e.tituloPagar.numero")
                .parameter("codEmpresa", codEmpresa)
                .parameter("dataBaixaInicial", configRel.getDataBaixaPagarInicialListagem())
                .parameter("dataBaixaFinal", configRel.getDataBaixaPagarFinalListagem())
                .parameter("banco", banco)
                .parameter("listarTodos", listarTodos)
                .list();
        for (ItemPagar itemPagar : itemPagars) {
            TituloPagar tituloPagar = itemPagar.getTituloPagar();
            ItemPagarDto itemPagarDto = dataManager.create(ItemPagarDto.class);
            itemPagarDto.setNumero(tituloPagar.getNumero());
            itemPagarDto.setDocumento(tituloPagar.getDocumento());
            itemPagarDto.setDataBaixa(utilGeralService.localDateToSqlDate(itemPagar.getData()));
            itemPagarDto.setDataEmissao(utilGeralService.localDateToSqlDate(tituloPagar.getDataEmissao()));
            itemPagarDto.setDataVencimento(utilGeralService.localDateToSqlDate(tituloPagar.getDataVencimento()));
            itemPagarDto.setParceiro(tituloPagar.getParceiro().getCodigo());
            itemPagarDto.setNomeParceiro(String.format("%d %s", tituloPagar.getParceiro().getCodigo(), tituloPagar.getParceiro().getNome()));
            itemPagarDto.setBanco(itemPagar.getBanco().getCodigo());
            itemPagarDto.setNomeBanco(String.format("%d %s", itemPagar.getBanco().getCodigo(), itemPagar.getBanco().getNome()));
            itemPagarDto.setValor(itemPagar.getValor());
            itemPagarDto.setJuros(itemPagar.getJuros());
            itemPagarDto.setDesconto(itemPagar.getDesconto());
            itemPagarsDto.add(itemPagarDto);
        }
        return itemPagarsDto;
    }

    /** Lança contabilmente os itens de baixa (item 2+) ainda não contabilizados de um título. */
    public void lancamentosBaixa(TituloPagar tituloPagar) {
        for (ItemPagar itemPagar : tituloPagar.getItens()) {
            HistoricoFinanceiro historicoFinanceiro = itemPagar.getHistoricoFinanceiro();
            if (historicoFinanceiro.getEmissao()) {
                continue;
            }
            ContaContabil contaParceiroEntrada = utilGeralService.getEmpresa().getContaParceiroEntrada(); // conta devedora
            ContaContabil contaBancoEntrada = itemPagar.getBanco().getContaContabil(); // conta credora
            HistoricoContabil historicoEntrada = utilGeralService.getEmpresa().getHistoricoBaixaPagar();
            String complementoHistorico = historicoEntrada == null ? "Pagamento " : "";
            complementoHistorico = complementoHistorico +
                    "título " +
                    tituloPagar.getNumero() +
                    " fornecedor: " +
                    tituloPagar.getParceiro().getApelido();
            LocalDate data = itemPagar.getData();
            int dia = data.getDayOfMonth();
            BigDecimal valor = itemPagar.getValor();

            utilFinanceiroService.gerarLancamento(itemPagar,
                    dia,
                    contaParceiroEntrada,
                    contaBancoEntrada,
                    valor,
                    historicoEntrada,
                    complementoHistorico);

            if (historicoFinanceiro.getJuros() && itemPagar.getJuros().compareTo(BigDecimal.ZERO) > 0) {
                String complementoJuros = "Juros título " + tituloPagar.getNumero() + " fornecedor: " + tituloPagar.getParceiro().getApelido();
                utilFinanceiroService.gerarLancamento(itemPagar,
                        dia,
                        contaBancoEntrada,
                        historicoFinanceiro.getContaContabilPagar(),
                        itemPagar.getJuros(),
                        historicoFinanceiro.getHistoricoContabilPagar(),
                        complementoJuros);
            }

            if (historicoFinanceiro.getDesconto() && itemPagar.getDesconto().compareTo(BigDecimal.ZERO) > 0) {
                String complementoDesconto = "Desconto título " + tituloPagar.getNumero() + " fornecedor: " + tituloPagar.getParceiro().getApelido();
                utilFinanceiroService.gerarLancamento(itemPagar,
                        dia,
                        historicoFinanceiro.getContaContabilPagar(),
                        contaBancoEntrada,
                        itemPagar.getDesconto(),
                        historicoFinanceiro.getHistoricoContabilPagar(),
                        complementoDesconto);
            }

            itemPagar.setContabilizado(true);
            dataManager.save(itemPagar);
        }
    }

}
