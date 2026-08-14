package br.com.axialsoftware.axctg3.service.contabil;

import br.com.axialsoftware.axctg3.entity.cadastros.ConfigRel;
import br.com.axialsoftware.axctg3.entity.contabil.Bem;
import br.com.axialsoftware.axctg3.entity.contabil.BemDto;
import br.com.axialsoftware.axctg3.entity.contabil.Depreciacao;
import br.com.axialsoftware.axctg3.entity.contabil.HistoricoContabil;
import br.com.axialsoftware.axctg3.entity.contabil.Lancamento;
import br.com.axialsoftware.axctg3.entity.contabil.LancamentoDto;
import br.com.axialsoftware.axctg3.entity.contabil.ResumoCorrecaoDto;
import br.com.axialsoftware.axctg3.service.RelatorioService;
import br.com.axialsoftware.axctg3.service.UtilGeralService;
import io.jmix.core.DataManager;
import io.jmix.core.FetchPlan;
import net.sf.jasperreports.engine.JRDataSource;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

@Service
public class DepreciacaoService {

    private final UtilGeralService utilGeralService;
    private final DataManager dataManager;
    private final RelatorioService relatorioService;

    public DepreciacaoService(UtilGeralService utilGeralService, DataManager dataManager, RelatorioService relatorioService) {
        this.utilGeralService = utilGeralService;
        this.dataManager = dataManager;
        this.relatorioService = relatorioService;
    }

    public void calcularDepreciacoes(ConfigRel configRel) {
        Integer codEmpresa = utilGeralService.getCodEmpresa();
        String tituloRelatorio = "Depreciação de bens";
        String nomeRelatorio = "Depreciacao.jasper";
        String nomeEmpresa = utilGeralService.getNomeEmpresa();
        String nomeSaida = "Depreciacao.pdf";

        LocalDate dataDepreciacao = configRel.getDataDepreciacao();

        var parametros = new HashMap<String, Object>();
        parametros.put("TITULO_RELATORIO", tituloRelatorio);
        parametros.put("NOME_EMPRESA", nomeEmpresa);
        parametros.put("DATA_DEPRECIACAO", utilGeralService.localDateToSqlDate(dataDepreciacao));
        parametros.put("LOGO", utilGeralService.getLogoEmpresa());

        List<Bem> bens = dataManager.load(Bem.class)
                .query("select b from Bem b " +
                        "where b.codigo > 0 " +
                        "and b.taxaDepr is not null " +
                        "and b.taxaDepr > 0 " +
                        "and b.valorCompra > 0 " +
                        "and b.deprAcum < b.valorCompra " +
                        "and b.codEmpresa = :codEmpresa " +
                        "and (b.dataDepr is null or b.dataDepr < :dataDepreciacao) " + // depreciação anterior
                        "and b.dataBaixa is null " +
                        "order by b.codigo")
                .parameter("codEmpresa", codEmpresa)
                .parameter("dataDepreciacao", dataDepreciacao)
                .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE)
                        .add("contaContabilDepr", FetchPlan.BASE)
                        .add("depreciacaos", FetchPlan.BASE))
                .list();

        List<BemDto> bensDto = new ArrayList<>();
        for (Bem bem : bens) {
            if (bem.getDataDepr() == null && bem.getDataIniDepr() != null && bem.getDataIniDepr().isAfter(dataDepreciacao)) {
                continue;
            }

            BigDecimal deprPeriodo = getDeprPeriodo(bem);

            LocalDate dataInicial;
            if (bem.getDataIniDepr() == null) {
                dataInicial = bem.getDataCompra();
            } else {
                dataInicial = bem.getDataIniDepr();
            }

            BigDecimal deprAnterior = BigDecimal.ZERO;
            if (bem.getValorIniDepr() != null && bem.getValorIniDepr().compareTo(BigDecimal.ZERO) > 0) {
                deprAnterior = bem.getValorIniDepr();
            } else {
                for (Depreciacao depr : bem.getDepreciacaos()) {
                    deprAnterior = deprAnterior.add(depr.getValorDepr());
                }
            }

            BigDecimal deprAtual = deprAnterior.add(deprPeriodo);
            if (bem.getValorCompra().compareTo(deprAtual) < 0) {
                deprPeriodo = deprPeriodo.subtract(deprAtual.subtract(bem.getValorCompra()));
                deprAtual = bem.getValorCompra();
            }

            Depreciacao depreciacao = dataManager.create(Depreciacao.class);
            depreciacao.setBem(bem);
            depreciacao.setDataDepr(dataDepreciacao);
            depreciacao.setValorDepr(deprPeriodo);

            bem.setDeprAcum(deprAtual);
            bem.setDataDepr(dataDepreciacao);

            dataManager.saveWithoutReload(depreciacao, bem);

            BemDto bemDto = dataManager.create(BemDto.class);
            bemDto.setCodigoConta(bem.getContaContabilDepr().getCodigo());
            bemDto.setNomeConta(bem.getContaContabilDepr().getNome());
            bemDto.setCodigo(bem.getCodigo());
            bemDto.setDescricao(bem.getDescricao());
            bemDto.setResumida(bem.getResumida());
            bemDto.setDataCompra(utilGeralService.localDateToSqlDate(dataInicial));
            bemDto.setValorCompra(bem.getValorCompra());
            bemDto.setTaxaDepr(bem.getTaxaDepr());
            bemDto.setDeprPeriodo(deprPeriodo);
            bemDto.setDeprAnterior(deprAnterior);
            bemDto.setDeprAtual(deprAtual);
            bensDto.add(bemDto);
        }

        JRDataSource dataSource = new JRBeanCollectionDataSource(bensDto);
        relatorioService.emitirRelatorio(nomeRelatorio, dataSource, parametros, nomeSaida);
    }

    public void lancarDepreciacoes(ConfigRel configRel) {
        Integer codEmpresa = utilGeralService.getCodEmpresa();

        String tituloRelatorio = "Lançamento das depreciações";
        String nomeRelatorio = "Lancamento.jasper";
        String nomeEmpresa = utilGeralService.getNomeEmpresa();
        String nomeSaida = "LancamentoDepreciacao.pdf";

        LocalDate dataDepreciacao = configRel.getDataDepreciacao();
        Integer codHistorico = configRel.getHistoricoDepreciacao();

        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");

        var parametros = new HashMap<String, Object>();
        parametros.put("TITULO_RELATORIO", tituloRelatorio);
        parametros.put("NOME_EMPRESA", nomeEmpresa);
        parametros.put("PERIODO_RELATORIO", "Data depreciação: " + sdf.format(utilGeralService.localDateToSqlDate(dataDepreciacao)));
        parametros.put("LOGO", utilGeralService.getLogoEmpresa());

        List<Depreciacao> depreciacoes = dataManager.load(Depreciacao.class)
                .query("select d from Depreciacao d " +
                        "where d.dataDepr = :dataDepreciacao " +
                        "and d.bem.codEmpresa = :codEmpresa")
                .parameter("codEmpresa", codEmpresa)
                .parameter("dataDepreciacao", dataDepreciacao)
                .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE)
                        .add("bem.contaContabilDepr", FetchPlan.BASE)
                        .add("bem.contaContabilDespDepr", FetchPlan.BASE))
                .list();

        HistoricoContabil historicoContabil = dataManager.load(HistoricoContabil.class)
                .query("select h from HistoricoContabil h " +
                        "where h.codigo = :codHistorico " +
                        "and h.codEmpresa = :codEmpresa")
                .parameter("codHistorico", codHistorico)
                .parameter("codEmpresa", codEmpresa)
                .one();

        List<LancamentoDto> lancamentosDto = new ArrayList<>();
        for (Depreciacao depreciacao : depreciacoes) {
            if (depreciacao.getLancamento() == null) {
                Lancamento lancamento = dataManager.create(Lancamento.class);
                // codEmpresa/ano/mes/numero/dataLancamento são carimbados pelo
                // LancamentoEventListener a partir do período contábil corrente — só o
                // dia é definido aqui (senão o listener assume dia 1).
                lancamento.setDia(depreciacao.getDataDepr().getDayOfMonth());
                lancamento.setValor(depreciacao.getValorDepr());
                lancamento.setContaDevedora(depreciacao.getBem().getContaContabilDespDepr());
                lancamento.setContaCredora(depreciacao.getBem().getContaContabilDepr());
                lancamento.setHistoricoContabil(historicoContabil);
                lancamento.setOrigem("depr");
                Lancamento lanc = dataManager.save(lancamento);
                depreciacao.setLancamento(lanc.getNumero().longValue());
                dataManager.saveWithoutReload(depreciacao);
            }

            LancamentoDto lancamentoDto = dataManager.create(LancamentoDto.class);
            lancamentoDto.setNumero(depreciacao.getLancamento().intValue());
            lancamentoDto.setDataLancamento(utilGeralService.localDateToSqlDate(depreciacao.getDataDepr()));
            lancamentoDto.setCodCtDev(depreciacao.getBem().getContaContabilDespDepr().getCodigo());
            lancamentoDto.setDscCtDev(depreciacao.getBem().getContaContabilDespDepr().getNome());
            lancamentoDto.setCodCtCre(depreciacao.getBem().getContaContabilDepr().getCodigo());
            lancamentoDto.setDscCtCre(depreciacao.getBem().getContaContabilDepr().getNome());
            lancamentoDto.setCodHistCont(historicoContabil.getCodigo());
            lancamentoDto.setDscHistCont(historicoContabil.getDescricao());
            lancamentoDto.setValor(depreciacao.getValorDepr());
            lancamentoDto.setOrigem("depr");
            lancamentosDto.add(lancamentoDto);
        }

        lancamentosDto.sort(Comparator.comparing(LancamentoDto::getNumero));

        JRDataSource dataSource = new JRBeanCollectionDataSource(lancamentosDto);
        relatorioService.emitirRelatorio(nomeRelatorio, dataSource, parametros, nomeSaida);
    }

    public void listarDepreciacoes(ConfigRel configRel, Boolean listagemCompleta) {
        Integer codEmpresa = utilGeralService.getCodEmpresa();
        String tituloRelatorio = "Listagem das depreciações";
        String nomeRelatorio = "Depreciacao.jasper";
        String nomeEmpresa = utilGeralService.getNomeEmpresa();
        String nomeSaida = "Depreciacao.pdf";

        LocalDate dataDepr = configRel.getDataDepreciacao();

        var parametros = new HashMap<String, Object>();
        parametros.put("TITULO_RELATORIO", tituloRelatorio);
        parametros.put("NOME_EMPRESA", nomeEmpresa);
        parametros.put("DATA_DEPRECIACAO", utilGeralService.localDateToSqlDate(dataDepr));
        parametros.put("LOGO", utilGeralService.getLogoEmpresa());

        List<Bem> bens = dataManager.load(Bem.class)
                .query("select b from Bem b " +
                        "where b.codigo > 0 " +
                        "and b.taxaDepr is not null " +
                        "and b.taxaDepr > 0 " +
                        "and b.valorCompra > 0 " +
                        "and b.dataDepr <= :dataDepreciacao " +
                        "and b.codEmpresa = :codEmpresa " +
                        "and b.dataBaixa is null " +
                        "order by b.codigo")
                .parameter("codEmpresa", codEmpresa)
                .parameter("dataDepreciacao", dataDepr)
                .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE)
                        .add("contaContabilDepr", FetchPlan.BASE)
                        .add("depreciacaos", FetchPlan.BASE))
                .list();

        List<BemDto> bensDto = new ArrayList<>();
        for (Bem bem : bens) {
            if (!listagemCompleta && bem.getDataDepr().isBefore(dataDepr)) {
                continue;
            }
            LocalDate dataInicial;
            if (bem.getDataIniDepr() == null) {
                dataInicial = bem.getDataCompra();
            } else {
                dataInicial = bem.getDataIniDepr();
            }

            BigDecimal deprPeriodo = getDeprPeriodo(bem);

            BigDecimal deprAnterior = BigDecimal.ZERO;
            for (Depreciacao depr : bem.getDepreciacaos()) {
                if (depr.getDataDepr().isBefore(dataDepr)) {
                    deprAnterior = deprAnterior.add(depr.getValorDepr());
                }
            }

            BigDecimal deprAtual = deprAnterior.add(deprPeriodo);
            if (bem.getValorCompra().compareTo(deprAtual) < 0) {
                deprPeriodo = deprPeriodo.subtract(deprAtual.subtract(bem.getValorCompra()));
                deprAtual = bem.getValorCompra();
            }
            BemDto bemDto = dataManager.create(BemDto.class);
            bemDto.setCodigoConta(bem.getContaContabilDepr().getCodigo());
            bemDto.setNomeConta(bem.getContaContabilDepr().getNome());
            bemDto.setCodigo(bem.getCodigo());
            bemDto.setDescricao(bem.getDescricao());
            bemDto.setResumida(bem.getResumida());
            bemDto.setDataCompra(utilGeralService.localDateToSqlDate(dataInicial));
            bemDto.setValorCompra(bem.getValorCompra());
            bemDto.setTaxaDepr(bem.getTaxaDepr());
            bemDto.setDeprPeriodo(deprPeriodo);
            bemDto.setDeprAnterior(deprAnterior);
            bemDto.setDeprAtual(deprAtual);
            bensDto.add(bemDto);
        }

        JRDataSource dataSource = new JRBeanCollectionDataSource(bensDto);
        relatorioService.emitirRelatorio(nomeRelatorio, dataSource, parametros, nomeSaida);
    }

    /**
     * Porta apenas a variante 2 do legado (bean datasource via emitirRelatorio()), não a
     * variante 1 (resumoCorrecaoMonetaria original, que abria JDBC direto pro axialdb via
     * emitirRelatorio2 — caminho legado marcado para não seguir, ver docs/MIGRACAO.md).
     */
    public void resumoCorrecaoMonetaria() {
        String tituloRelatorio = "Resumo correção monetária";
        String nomeRelatorio = "ResumoCorrecao2.jasper";
        String nomeEmpresa = utilGeralService.getNomeEmpresa();
        String nomeSaida = "ResumoCorrecao.pdf";
        String periodoRelatorio = utilGeralService.extensoPeriodoContabil();

        var parametros = new HashMap<String, Object>();
        parametros.put("TITULO_RELATORIO", tituloRelatorio);
        parametros.put("NOME_EMPRESA", nomeEmpresa);
        parametros.put("PERIODO_RELATORIO", periodoRelatorio);
        parametros.put("LOGO", utilGeralService.getLogoEmpresa());

        List<ResumoCorrecaoDto> resumoDto = prepararResumoCorrecaoDto();

        JRDataSource dataSource = new JRBeanCollectionDataSource(resumoDto);
        relatorioService.emitirRelatorio(nomeRelatorio, dataSource, parametros, nomeSaida);
    }

    private List<ResumoCorrecaoDto> prepararResumoCorrecaoDto() {
        Integer codEmpresa = utilGeralService.getCodEmpresa();
        List<ResumoCorrecaoDto> resumoDto = new ArrayList<>();
        List<Bem> bens = dataManager.load(Bem.class)
                .query("select b from Bem b " +
                        "where b.codEmpresa = :codEmpresa " +
                        "and b.taxaDepr > 0 " +
                        // valorBaixa só é preenchido quando o bem é baixado (sem default no
                        // BemEventListener) — o legado comparava direto contra valorCompra,
                        // o que excluiria todo bem ainda ativo (valorBaixa null).
                        "and (b.valorBaixa is null or b.valorCompra > b.valorBaixa) " +
                        "order by b.contaContabilDepr.codigo, b.codigo")
                .parameter("codEmpresa", codEmpresa)
                .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE)
                        .add("contaContabilDepr", FetchPlan.BASE))
                .list();
        for (Bem bem : bens) {
            ResumoCorrecaoDto resumoCorrecaoDto = dataManager.create(ResumoCorrecaoDto.class);
            resumoCorrecaoDto.setCodConta(bem.getContaContabilDepr().getCodigo());
            resumoCorrecaoDto.setNmConta(bem.getContaContabilDepr().getNome());
            resumoCorrecaoDto.setValorAtual(bem.getDeprAcum());
            resumoDto.add(resumoCorrecaoDto);
        }
        return resumoDto;
    }

    private static BigDecimal getDeprPeriodo(Bem bem) {
        return bem.getValorCompra()
                .multiply(bem.getTaxaDepr())
                .divide(new BigDecimal(100), RoundingMode.HALF_UP)
                .divide(new BigDecimal(12), RoundingMode.HALF_UP)
                .setScale(2, RoundingMode.HALF_UP);
    }
}
