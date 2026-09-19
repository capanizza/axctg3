package br.com.axialsoftware.axctg3.service.financeiro;

import br.com.axialsoftware.axctg3.entity.cadastros.CondicaoPagamento;
import br.com.axialsoftware.axctg3.entity.cadastros.ConfigRel;
import br.com.axialsoftware.axctg3.entity.cadastros.Empresa;
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
import io.jmix.core.FetchPlan;
import io.jmix.core.SaveContext;
import io.jmix.data.PersistenceHints;
import io.jmix.data.Sequence;
import io.jmix.data.Sequences;
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
    private final Sequences sequences;

    public TituloReceberService(DataManager dataManager, UtilGeralService utilGeralService, RelatorioService relatorioService, UtilFinanceiroService utilFinanceiroService, Sequences sequences) {
        this.dataManager = dataManager;
        this.utilGeralService = utilGeralService;
        this.relatorioService = relatorioService;
        this.utilFinanceiroService = utilFinanceiroService;
        this.sequences = sequences;
    }

    /**
     * Gera os títulos a receber de uma {@link NotaSaida} a partir de
     * {@code NotaSaida.condicaoPagamento} — chamado por {@code NfeEmissaoService.emitir}
     * ANTES de montar o XML (o grupo {@code cobr}/{@code dup} e {@code pag} dependem dos
     * títulos já existirem nesse momento, ver {@code NfeXmlBuilder.buscarTitulos}).
     * Idempotente: não gera de novo se a soma dos títulos já existentes ainda bate com
     * {@code NotaSaida.valor} — cobre reemissão depois de um erro anterior sem duplicar
     * título. Se a nota foi editada depois dos títulos gerados (soma não bate mais),
     * regenera do zero, desde que nenhum item já tenha sido contabilizado.
     *
     * <p>Cada parcela vence {@code condicaoPagamento.primeira} dias após a emissão, e as
     * seguintes a cada {@code condicaoPagamento.diferenca} dias. Valor de cada parcela =
     * {@code NotaSaida.valor} ÷ número de parcelas, arredondado; a diferença de
     * arredondamento (se a soma das parcelas não bater com o valor da nota) é absorvida
     * pela PRIMEIRA parcela, não pela última — pedido explícito do usuário 2026-09-14.
     *
     * <p>Numeração do título: por padrão, número da nota com 6 dígitos (zeros à
     * esquerda). Quando {@code Empresa.numTitAlt} está marcado, a base sai de uma
     * {@link Sequence} própria por empresa ({@code "numerotitulo" + codEmpresa}) em vez do
     * número da nota — independente e nunca reaproveitada entre notas, ao contrário do
     * número da nota que pode repetir entre séries/espécies diferentes. Nos dois casos,
     * parcela única não leva sufixo; mais de uma parcela leva "/" + uma letra maiúscula,
     * começando em "A" pra primeira parcela (não é {@link CondicaoPagamento#getCodigo()}
     * nem nada específico da condição, só a base + posição da parcela). O
     * {@code nDup} do XML/DANFE é outra coisa — não usa esse número, ver
     * {@code NfeXmlBuilder.construirCobr}.
     *
     * @return mensagem de erro (sem gerar nada) quando falta um pré-requisito; {@code null}
     * em caso de sucesso ou quando não há nada a fazer (nota já tem título, ou não tem
     * condição de pagamento configurada — mesmo comportamento de hoje, sem duplicata no XML)
     */
    @Transactional
    public String gerarTitulosDaEmissao(NotaSaida notaSaida) {
        List<TituloReceber> titulosExistentes = dataManager.load(TituloReceber.class)
                .query("select e from TituloReceber e where e.notaSaida = :notaSaida")
                .parameter("notaSaida", notaSaida)
                .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE).add("itens", FetchPlan.BASE))
                .list();
        if (!titulosExistentes.isEmpty()) {
            BigDecimal somaExistente = titulosExistentes.stream()
                    .map(TituloReceber::getValor)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal valorAtualDaNota = notaSaida.getValor() == null ? BigDecimal.ZERO : notaSaida.getValor();
            if (somaExistente.compareTo(valorAtualDaNota) == 0) {
                return null;
            }
            // Nota editada (itens alterados) depois que os títulos já tinham sido gerados —
            // a soma antiga não bate mais com NotaSaida.valor atual (achado ao vivo
            // 2026-09-17: XML saía com vLiq/dup divergentes, SEFAZ rejeitava cStat=851
            // "Soma do valor das parcelas difere do Valor Líquido da Fatura"). Só
            // regenera quando nenhum item já foi contabilizado — reverter lançamento
            // contábil já postado é decisão do usuário, não automática.
            boolean algumJaContabilizado = titulosExistentes.stream()
                    .flatMap(t -> t.getItens().stream())
                    .anyMatch(item -> Boolean.TRUE.equals(item.getContabilizado()));
            if (algumJaContabilizado) {
                return "Os títulos desta nota estão desatualizados (valor mudou depois de gerados), "
                        + "mas já têm lançamento contábil — ajuste manualmente antes de emitir";
            }
            // Hard delete, não soft delete: o número do título reusa nota+letra (ex.
            // "458750/A"), e o novo título recriado abaixo tem o MESMO número — um soft
            // delete deixaria uma linha morta ocupando esse número pra sempre no índice
            // único (NUMERO, COD_EMPRESA) do HSQLDB, que não é parcial como o do Postgres
            // (ver jmix-create-liquibase-changelog). Título nunca contabilizado não tem
            // valor de auditoria em manter. Os itens (composição, @OnDelete(CASCADE)) são
            // removidos automaticamente — removê-los antes à mão faz o Jmix tentar
            // cascatear de novo em cima de uma linha já apagada (OptimisticLockException).
            dataManager.save(new SaveContext()
                    .setHint(PersistenceHints.SOFT_DELETION, false)
                    .removing(titulosExistentes.toArray()));
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

        String numeroBase = numeroBaseTitulo(notaSaida);
        LocalDate dataVencimento = notaSaida.getDataEmissao().plusDays(condicaoPagamento.getPrimeira());

        for (int i = 0; i < parcelas; i++) {
            TituloReceber titulo = dataManager.create(TituloReceber.class);
            titulo.setNotaSaida(notaSaida);
            titulo.setNumero(parcelas == 1 ? numeroBase : numeroBase + "/" + (char) ('A' + i));
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

    /**
     * Base do número do título (sem a letra da parcela) — ver o Javadoc de
     * {@link #gerarTitulosDaEmissao}. Sem {@code Empresa} cadastrada pro
     * {@code notaSaida.getCodEmpresa()} (não deveria acontecer fora de teste), cai no
     * comportamento padrão em vez de estourar exceção.
     */
    private String numeroBaseTitulo(NotaSaida notaSaida) {
        boolean numeracaoAlternativa = dataManager.load(Empresa.class)
                .query("select e from Empresa e where e.codigo = :codEmpresa")
                .parameter("codEmpresa", notaSaida.getCodEmpresa())
                .optional()
                .map(Empresa::getNumTitAlt)
                .orElse(Boolean.FALSE);
        if (numeracaoAlternativa) {
            long numero = sequences.createNextValue(Sequence.withName("numerotitulo" + notaSaida.getCodEmpresa()));
            return String.format("%06d", numero);
        }
        return String.format("%06d", notaSaida.getNumero());
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
