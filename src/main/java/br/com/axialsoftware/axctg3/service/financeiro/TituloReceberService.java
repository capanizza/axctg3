package br.com.axialsoftware.axctg3.service.financeiro;

import br.com.axialsoftware.axctg3.entity.cadastros.CondicaoPagamento;
import br.com.axialsoftware.axctg3.entity.cadastros.ConfigRel;
import br.com.axialsoftware.axctg3.entity.contabil.ContaContabil;
import br.com.axialsoftware.axctg3.entity.contabil.HistoricoContabil;
import br.com.axialsoftware.axctg3.entity.financeiro.Banco;
import br.com.axialsoftware.axctg3.entity.financeiro.HistoricoFinanceiro;
import br.com.axialsoftware.axctg3.entity.financeiro.ItemReceber;
import br.com.axialsoftware.axctg3.entity.financeiro.TituloReceber;
import br.com.axialsoftware.axctg3.entity.financeiro.TituloReceberDto;
import br.com.axialsoftware.axctg3.entity.fiscal.NotaSaida;
import br.com.axialsoftware.axctg3.service.RelatorioService;
import br.com.axialsoftware.axctg3.service.UtilGeralService;
import io.jmix.core.DataManager;
import net.sf.jasperreports.engine.JRDataSource;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * {@code lancamentosEmissao} portado a partir de {@code TituloPagarService.lancamentosEmissao}
 * usando {@code TituloReceber.contaContabil} — ver o Javadoc de {@link TituloReceber} para o
 * porquê da versão simplificada. O restante (cálculo de aberto/valor baixado e os
 * relatórios) não depende disso.
 */
@Service
public class TituloReceberService {

    private final DataManager dataManager;
    private final UtilGeralService utilGeralService;
    private final RelatorioService relatorioService;
    private final UtilFinanceiroService utilFinanceiroService;

    public TituloReceberService(DataManager dataManager, UtilGeralService utilGeralService, RelatorioService relatorioService, UtilFinanceiroService utilFinanceiroService) {
        this.dataManager = dataManager;
        this.utilGeralService = utilGeralService;
        this.relatorioService = relatorioService;
        this.utilFinanceiroService = utilFinanceiroService;
    }

    /**
     * Gera os títulos a receber de uma {@link NotaSaida} a partir de
     * {@code NotaSaida.condicaoPagamento} — chamado por {@code NfeEmissaoService.emitir}
     * ANTES de montar o XML (o grupo {@code cobr}/{@code dup} e {@code pag} dependem dos
     * títulos já existirem nesse momento, ver {@code NfeXmlBuilder.buscarTitulos}).
     * Idempotente: não gera de novo se a nota já tem título — cobre reemissão depois de
     * um erro anterior sem duplicar título.
     *
     * <p>Cada parcela vence {@code condicaoPagamento.primeira} dias após a emissão, e as
     * seguintes a cada {@code condicaoPagamento.diferenca} dias. Valor de cada parcela =
     * {@code NotaSaida.valor} ÷ número de parcelas, arredondado; a diferença de
     * arredondamento (se a soma das parcelas não bater com o valor da nota) é absorvida
     * pela PRIMEIRA parcela, não pela última — pedido explícito do usuário 2026-09-14.
     *
     * <p>Numeração do título: número da nota com 6 dígitos (zeros à esquerda). Parcela
     * única não leva sufixo; mais de uma parcela leva uma letra maiúscula colada em
     * seguida (sem espaço), começando em "A" pra primeira parcela — mesma convenção do
     * {@code nDup} de duplicata usado no mercado (não é {@link CondicaoPagamento#getCodigo()}
     * nem nada específico da condição, só o número da nota + posição da parcela).
     *
     * @return mensagem de erro (sem gerar nada) quando falta um pré-requisito; {@code null}
     * em caso de sucesso ou quando não há nada a fazer (nota já tem título, ou não tem
     * condição de pagamento configurada — mesmo comportamento de hoje, sem duplicata no XML)
     */
    @Transactional
    public String gerarTitulosDaEmissao(NotaSaida notaSaida) {
        boolean jaTemTitulo = !dataManager.load(TituloReceber.class)
                .query("select e from TituloReceber e where e.notaSaida = :notaSaida")
                .parameter("notaSaida", notaSaida)
                .list()
                .isEmpty();
        if (jaTemTitulo) {
            return null;
        }
        CondicaoPagamento condicaoPagamento = notaSaida.getCondicaoPagamento();
        if (condicaoPagamento == null) {
            return null;
        }
        Banco banco = notaSaida.getBanco();
        if (banco == null) {
            return "Nota tem condição de pagamento mas não tem banco configurado — "
                    + "obrigatório pra gerar os títulos a receber";
        }
        int parcelas = condicaoPagamento.getParcelas();
        if (parcelas < 1) {
            return "Condição de pagamento com número de parcelas inválido (" + parcelas + ")";
        }
        if (parcelas > 26) {
            return "Condição de pagamento com mais de 26 parcelas — a letra de identificação "
                    + "do título esgota o alfabeto";
        }

        BigDecimal valorTotal = notaSaida.getValor() == null ? BigDecimal.ZERO : notaSaida.getValor();
        BigDecimal valorParcela = valorTotal.divide(BigDecimal.valueOf(parcelas), 2, RoundingMode.HALF_UP);
        BigDecimal valorDemaisParcelas = valorParcela.multiply(BigDecimal.valueOf(parcelas - 1L));
        BigDecimal valorPrimeiraParcela = valorTotal.subtract(valorDemaisParcelas);

        String numeroBase = String.format("%06d", notaSaida.getNumero());
        LocalDate dataVencimento = notaSaida.getDataEmissao().plusDays(condicaoPagamento.getPrimeira());

        for (int i = 0; i < parcelas; i++) {
            TituloReceber titulo = dataManager.create(TituloReceber.class);
            titulo.setNotaSaida(notaSaida);
            titulo.setNumero(parcelas == 1 ? numeroBase : numeroBase + (char) ('A' + i));
            titulo.setCodEmpresa(notaSaida.getCodEmpresa());
            titulo.setDataEmissao(notaSaida.getDataEmissao());
            titulo.setDataVencimento(dataVencimento);
            titulo.setParceiro(notaSaida.getParceiro());
            titulo.setBanco(banco);
            titulo.setValor(i == 0 ? valorPrimeiraParcela : valorParcela);
            dataManager.save(titulo);

            dataVencimento = dataVencimento.plusDays(condicaoPagamento.getDiferenca());
        }
        return null;
    }

    /** Lança contabilmente o item de emissão (item 1) de cada título ainda não contabilizado. */
    public void lancamentosEmissao(TituloReceber tituloReceber) {
        for (ItemReceber itemReceber : tituloReceber.getItens()) {
            if (itemReceber.getHistoricoFinanceiro().getBaixa()) {
                continue;
            }
            ContaContabil contaParceiroSaida = utilGeralService.getEmpresa().getContaParceiroSaida(); // conta devedora
            ContaContabil contaSaida = tituloReceber.getContaContabil(); // conta credora
            HistoricoFinanceiro historicoFinanceiro = itemReceber.getHistoricoFinanceiro();
            HistoricoContabil historicoSaida = historicoFinanceiro.getHistoricoContabilReceber();
            String complementoHistorico = tituloReceber.getNumero() + " cliente: " + tituloReceber.getParceiro().getApelido();
            LocalDate dataEmissao = itemReceber.getData();
            int dia = dataEmissao.getDayOfMonth();
            BigDecimal valor = itemReceber.getValor();

            utilFinanceiroService.gerarLancamento(tituloReceber,
                    dia,
                    contaParceiroSaida,
                    contaSaida,
                    valor,
                    historicoSaida,
                    complementoHistorico);

            itemReceber.setContabilizado(true);
            dataManager.save(itemReceber);
        }
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
        parametros.put("LOGO", utilGeralService.getLogoEmpresa());

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
        parametros.put("LOGO", utilGeralService.getLogoEmpresa());

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
