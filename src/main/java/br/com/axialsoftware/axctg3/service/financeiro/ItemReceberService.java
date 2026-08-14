package br.com.axialsoftware.axctg3.service.financeiro;

import br.com.axialsoftware.axctg3.entity.cadastros.ConfigRel;
import br.com.axialsoftware.axctg3.entity.contabil.ContaContabil;
import br.com.axialsoftware.axctg3.entity.contabil.HistoricoContabil;
import br.com.axialsoftware.axctg3.entity.financeiro.HistoricoFinanceiro;
import br.com.axialsoftware.axctg3.entity.financeiro.ItemReceber;
import br.com.axialsoftware.axctg3.entity.financeiro.ItemReceberDto;
import br.com.axialsoftware.axctg3.entity.financeiro.TituloReceber;
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
public class ItemReceberService {

    private final DataManager dataManager;
    private final UtilGeralService utilGeralService;
    private final RelatorioService relatorioService;
    private final UtilFinanceiroService utilFinanceiroService;

    public ItemReceberService(DataManager dataManager, UtilGeralService utilGeralService, RelatorioService relatorioService, UtilFinanceiroService utilFinanceiroService) {
        this.dataManager = dataManager;
        this.utilGeralService = utilGeralService;
        this.relatorioService = relatorioService;
        this.utilFinanceiroService = utilFinanceiroService;
    }

    /** Lança contabilmente os itens de baixa (item 2+) ainda não contabilizados de um título. */
    public void lancamentosBaixa(TituloReceber tituloReceber) {
        for (ItemReceber itemReceber : tituloReceber.getItens()) {
            HistoricoFinanceiro historicoFinanceiro = itemReceber.getHistoricoFinanceiro();
            if (historicoFinanceiro.getEmissao()) {
                continue;
            }
            ContaContabil contaBancoSaida = tituloReceber.getBanco().getContaContabil(); // conta devedora
            ContaContabil contaParceiroSaida = utilGeralService.getEmpresa().getContaParceiroSaida(); // conta credora
            HistoricoContabil historicoSaida = utilGeralService.getEmpresa().getHistoricoBaixaReceber();
            String complementoHistorico = historicoSaida == null ? "Recebimento " : "";
            complementoHistorico = complementoHistorico +
                    "título " +
                    tituloReceber.getNumero() +
                    " cliente: " +
                    tituloReceber.getParceiro().getApelido();
            LocalDate data = itemReceber.getData();
            int dia = data.getDayOfMonth();
            BigDecimal valor = itemReceber.getValor();

            utilFinanceiroService.gerarLancamento(itemReceber,
                    dia,
                    contaBancoSaida,
                    contaParceiroSaida,
                    valor,
                    historicoSaida,
                    complementoHistorico);

            if (historicoFinanceiro.getJuros() && itemReceber.getJuros().compareTo(BigDecimal.ZERO) > 0) {
                String complementoJuros = "Juros título " + tituloReceber.getNumero() + " cliente: " + tituloReceber.getParceiro().getApelido();
                utilFinanceiroService.gerarLancamento(itemReceber,
                        dia,
                        contaBancoSaida,
                        historicoFinanceiro.getContaContabilReceber(),
                        itemReceber.getJuros(),
                        historicoFinanceiro.getHistoricoContabilReceber(),
                        complementoJuros);
            }

            if (historicoFinanceiro.getDesconto() && itemReceber.getDesconto().compareTo(BigDecimal.ZERO) > 0) {
                String complementoDesconto = "Desconto título " + tituloReceber.getNumero() + " cliente: " + tituloReceber.getParceiro().getApelido();
                utilFinanceiroService.gerarLancamento(itemReceber,
                        dia,
                        historicoFinanceiro.getContaContabilReceber(),
                        contaBancoSaida,
                        itemReceber.getDesconto(),
                        historicoFinanceiro.getHistoricoContabilReceber(),
                        complementoDesconto);
            }

            itemReceber.setContabilizado(true);
            dataManager.save(itemReceber);
        }
    }

    public void listarBaixaTitulosReceber(ConfigRel configRel) {
        Integer codEmpresa = utilGeralService.getCodEmpresa();
        String tituloRelatorio = "Baixa de títulos a receber";
        String nomeRelatorio = "BaixaTituloReceber.jasper";
        String nomeSaida = "BaixaTituloReceber.pdf";
        String nomeEmpresa = utilGeralService.getNomeEmpresa();
        SimpleDateFormat df = new SimpleDateFormat("dd/MM/yyyy");
        java.sql.Date dtInicial = utilGeralService.localDateToSqlDate(configRel.getDataBaixaReceberInicialListagem());
        java.sql.Date dtFinal = utilGeralService.localDateToSqlDate(configRel.getDataBaixaReceberFinalListagem());
        String periodoRelatorio = "Período: " + df.format(dtInicial) + " a " + df.format(dtFinal);

        var parametros = new HashMap<String, Object>();
        parametros.put("TITULO_RELATORIO", tituloRelatorio);
        parametros.put("NOME_EMPRESA", nomeEmpresa);
        parametros.put("PERIODO_RELATORIO", periodoRelatorio);
        parametros.put("LOGO", utilGeralService.getLogoEmpresa());

        List<ItemReceberDto> itemRecebersDto = prepararBaixaDto(codEmpresa, configRel);

        JRDataSource dataSource = new JRBeanCollectionDataSource(itemRecebersDto);

        relatorioService.emitirRelatorio(nomeRelatorio, dataSource, parametros, nomeSaida);
    }

    private List<ItemReceberDto> prepararBaixaDto(Integer codEmpresa, ConfigRel configRel) {
        Integer banco = configRel.getBancoInicial();
        String listarTodos = banco == null ? "sim" : "nao";

        List<ItemReceberDto> itemRecebersDto = new ArrayList<>();
        List<ItemReceber> itemRecebers = dataManager.load(ItemReceber.class)
                .query("select e from ItemReceber e " +
                        "where e.data between :dataBaixaInicial and :dataBaixaFinal " +
                        "and e.historicoFinanceiro.baixa = true " +
                        "and (:listarTodos = 'sim' or e.tituloReceber.banco.codigo = :banco) " +
                        "and e.tituloReceber.codEmpresa = :codEmpresa " +
                        "order by e.data, e.tituloReceber.numero")
                .parameter("codEmpresa", codEmpresa)
                .parameter("dataBaixaInicial", configRel.getDataBaixaReceberInicialListagem())
                .parameter("dataBaixaFinal", configRel.getDataBaixaReceberFinalListagem())
                .parameter("banco", banco)
                .parameter("listarTodos", listarTodos)
                .list();
        for (ItemReceber itemReceber : itemRecebers) {
            TituloReceber tituloReceber = itemReceber.getTituloReceber();
            ItemReceberDto itemReceberDto = dataManager.create(ItemReceberDto.class);
            itemReceberDto.setNumero(tituloReceber.getNumero());
            itemReceberDto.setDataBaixa(utilGeralService.localDateToSqlDate(itemReceber.getData()));
            itemReceberDto.setDataEmissao(utilGeralService.localDateToSqlDate(tituloReceber.getDataEmissao()));
            itemReceberDto.setDataVencimento(utilGeralService.localDateToSqlDate(tituloReceber.getDataVencimento()));
            itemReceberDto.setParceiro(tituloReceber.getParceiro().getCodigo());
            itemReceberDto.setNomeParceiro(String.format("%d %s", tituloReceber.getParceiro().getCodigo(), tituloReceber.getParceiro().getNome()));
            itemReceberDto.setBanco(tituloReceber.getBanco().getCodigo());
            itemReceberDto.setNomeBanco(String.format("%d %s", tituloReceber.getBanco().getCodigo(), tituloReceber.getBanco().getNome()));
            itemReceberDto.setValor(itemReceber.getValor());
            itemReceberDto.setJuros(itemReceber.getJuros());
            itemReceberDto.setDesconto(itemReceber.getDesconto());
            itemRecebersDto.add(itemReceberDto);
        }
        return itemRecebersDto;
    }

}
