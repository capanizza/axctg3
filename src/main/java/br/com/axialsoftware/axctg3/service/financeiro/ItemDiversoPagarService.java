package br.com.axialsoftware.axctg3.service.financeiro;

import br.com.axialsoftware.axctg3.entity.cadastros.ConfigRel;
import br.com.axialsoftware.axctg3.entity.contabil.ContaContabil;
import br.com.axialsoftware.axctg3.entity.contabil.HistoricoContabil;
import br.com.axialsoftware.axctg3.entity.financeiro.DiversoPagar;
import br.com.axialsoftware.axctg3.entity.financeiro.HistoricoFinanceiro;
import br.com.axialsoftware.axctg3.entity.financeiro.ItemDiversoPagar;
import br.com.axialsoftware.axctg3.entity.financeiro.ItemDiversoPagarDto;
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
public class ItemDiversoPagarService {

    private final UtilGeralService utilGeralService;
    private final RelatorioService relatorioService;
    private final DataManager dataManager;
    private final UtilFinanceiroService utilFinanceiroService;

    public ItemDiversoPagarService(UtilGeralService utilGeralService, RelatorioService relatorioService, DataManager dataManager, UtilFinanceiroService utilFinanceiroService) {
        this.utilGeralService = utilGeralService;
        this.relatorioService = relatorioService;
        this.dataManager = dataManager;
        this.utilFinanceiroService = utilFinanceiroService;
    }

    public void listarBaixaDiversosPagar(ConfigRel configRel) {
        Integer codEmpresa = utilGeralService.getCodEmpresa();
        String tituloRelatorio = "Baixa de diversos a pagar";
        String nomeRelatorio = "BaixaDiversoPagar.jasper";
        String nomeSaida = "BaixaDiversoPagar.pdf";
        String nomeEmpresa = utilGeralService.getNomeEmpresa();
        SimpleDateFormat df = new SimpleDateFormat("dd/MM/yyyy");
        java.sql.Date dtInicial = utilGeralService.localDateToSqlDate(configRel.getDataBaixaDiversoInicialListagem());
        java.sql.Date dtFinal = utilGeralService.localDateToSqlDate(configRel.getDataBaixaDiversoFinalListagem());
        String periodoRelatorio = "Período: " + df.format(dtInicial) + " a " + df.format(dtFinal);

        var parametros = new HashMap<String, Object>();
        parametros.put("TITULO_RELATORIO", tituloRelatorio);
        parametros.put("NOME_EMPRESA", nomeEmpresa);
        parametros.put("PERIODO_RELATORIO", periodoRelatorio);

        List<ItemDiversoPagarDto> itemDiversoPagarsDto = prepararBaixaDto(codEmpresa, configRel);

        JRDataSource dataSource = new JRBeanCollectionDataSource(itemDiversoPagarsDto);

        relatorioService.emitirRelatorio(nomeRelatorio, dataSource, parametros, nomeSaida);
    }

    private List<ItemDiversoPagarDto> prepararBaixaDto(Integer codEmpresa, ConfigRel configRel) {
        Integer banco = configRel.getBancoInicial();
        String listarTodos = banco == null ? "sim" : "nao";

        List<ItemDiversoPagarDto> itemDiversoPagarsDto = new ArrayList<>();
        List<ItemDiversoPagar> itemDiversoPagars = dataManager.load(ItemDiversoPagar.class)
                .query("select e from ItemDiversoPagar e " +
                        "where e.data between :dataBaixaInicial and :dataBaixaFinal " +
                        "and e.historicoFinanceiro.baixa = true " +
                        "and (:listarTodos = 'sim' or e.banco.codigo = :banco) " +
                        "and e.diversoPagar.codEmpresa = :codEmpresa " +
                        "order by e.data, e.diversoPagar.numero")
                .parameter("codEmpresa", codEmpresa)
                .parameter("dataBaixaInicial", configRel.getDataBaixaDiversoInicialListagem())
                .parameter("dataBaixaFinal", configRel.getDataBaixaDiversoFinalListagem())
                .parameter("banco", banco)
                .parameter("listarTodos", listarTodos)
                .list();
        for (ItemDiversoPagar itemDiversoPagar : itemDiversoPagars) {
            DiversoPagar diversoPagar = itemDiversoPagar.getDiversoPagar();
            ItemDiversoPagarDto itemDiversoPagarDto = dataManager.create(ItemDiversoPagarDto.class);
            itemDiversoPagarDto.setNumero(diversoPagar.getNumero());
            itemDiversoPagarDto.setDocumento(diversoPagar.getDocumento());
            itemDiversoPagarDto.setDataBaixa(utilGeralService.localDateToSqlDate(itemDiversoPagar.getData()));
            itemDiversoPagarDto.setDataEmissao(utilGeralService.localDateToSqlDate(diversoPagar.getDataEmissao()));
            itemDiversoPagarDto.setDataVencimento(utilGeralService.localDateToSqlDate(diversoPagar.getDataVencimento()));
            itemDiversoPagarDto.setParceiro(diversoPagar.getParceiro().getCodigo());
            itemDiversoPagarDto.setNomeParceiro(String.format("%d %s", diversoPagar.getParceiro().getCodigo(), diversoPagar.getParceiro().getNome()));
            itemDiversoPagarDto.setBanco(itemDiversoPagar.getBanco().getCodigo());
            itemDiversoPagarDto.setNomeBanco(String.format("%d %s", itemDiversoPagar.getBanco().getCodigo(), itemDiversoPagar.getBanco().getNome()));
            itemDiversoPagarDto.setValor(itemDiversoPagar.getValor());
            itemDiversoPagarDto.setJuros(itemDiversoPagar.getJuros());
            itemDiversoPagarDto.setDesconto(itemDiversoPagar.getDesconto());
            itemDiversoPagarsDto.add(itemDiversoPagarDto);
        }
        return itemDiversoPagarsDto;
    }

    /** Lança contabilmente os itens de baixa (item 2+) ainda não contabilizados de um diverso. */
    public void lancamentosBaixa(DiversoPagar diversoPagar) {
        for (ItemDiversoPagar itemDiversoPagar : diversoPagar.getItens()) {
            HistoricoFinanceiro historicoFinanceiro = itemDiversoPagar.getHistoricoFinanceiro();
            if (historicoFinanceiro.getEmissao()) {
                continue;
            }
            ContaContabil contaParceiroEntrada = utilGeralService.getEmpresa().getContaParceiroEntrada(); // conta devedora
            ContaContabil contaBancoEntrada = itemDiversoPagar.getBanco().getContaContabil(); // conta credora
            HistoricoContabil historicoEntrada = utilGeralService.getEmpresa().getHistoricoBaixaPagar();
            String complementoHistorico = historicoEntrada == null ? "Pagamento " : "";
            complementoHistorico = complementoHistorico +
                    "diverso " +
                    diversoPagar.getNumero() +
                    " fornecedor: " +
                    diversoPagar.getParceiro().getApelido();
            LocalDate data = itemDiversoPagar.getData();
            int dia = data.getDayOfMonth();
            BigDecimal valor = itemDiversoPagar.getValor();

            utilFinanceiroService.gerarLancamento(itemDiversoPagar,
                    dia,
                    contaParceiroEntrada,
                    contaBancoEntrada,
                    valor,
                    historicoEntrada,
                    complementoHistorico);

            if (historicoFinanceiro.getJuros() && itemDiversoPagar.getJuros().compareTo(BigDecimal.ZERO) > 0) {
                String complementoJuros = "Juros diverso " + diversoPagar.getNumero() + " fornecedor: " + diversoPagar.getParceiro().getApelido();
                utilFinanceiroService.gerarLancamento(itemDiversoPagar,
                        dia,
                        contaBancoEntrada,
                        historicoFinanceiro.getContaContabilPagar(),
                        itemDiversoPagar.getJuros(),
                        historicoFinanceiro.getHistoricoContabilPagar(),
                        complementoJuros);
            }

            if (historicoFinanceiro.getDesconto() && itemDiversoPagar.getDesconto().compareTo(BigDecimal.ZERO) > 0) {
                String complementoDesconto = "Desconto diverso " + diversoPagar.getNumero() + " fornecedor: " + diversoPagar.getParceiro().getApelido();
                utilFinanceiroService.gerarLancamento(itemDiversoPagar,
                        dia,
                        historicoFinanceiro.getContaContabilPagar(),
                        contaBancoEntrada,
                        itemDiversoPagar.getDesconto(),
                        historicoFinanceiro.getHistoricoContabilPagar(),
                        complementoDesconto);
            }

            itemDiversoPagar.setContabilizado(true);
            dataManager.save(itemDiversoPagar);
        }
    }

}
