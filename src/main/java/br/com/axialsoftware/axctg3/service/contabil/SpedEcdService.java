package br.com.axialsoftware.axctg3.service.contabil;

import br.com.axialsoftware.axctg3.entity.cadastros.CentroCusto;
import br.com.axialsoftware.axctg3.entity.cadastros.Empresa;
import br.com.axialsoftware.axctg3.entity.contabil.ContaContabil;
import br.com.axialsoftware.axctg3.entity.contabil.HistoricoContabil;
import br.com.axialsoftware.axctg3.entity.contabil.Lancamento;
import br.com.axialsoftware.axctg3.entity.contabil.SaldoConta;
import io.jmix.core.DataManager;
import io.jmix.core.metamodel.datatype.EnumClass;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/**
 * Gera o arquivo texto do Sped ECD (Escrituração Contábil Digital) — v1: Bloco 0
 * (abertura/identificação) + Bloco I (lançamentos contábeis), leiaute 9 (jan/2026).
 *
 * <p>Não assina nem transmite nada — o arquivo gerado aqui é submetido pelo usuário ao
 * PVA do Sped Contábil (Programa Validador e Assinador, oficial da Receita Federal), que
 * faz a validação de conteúdo, assinatura digital e transmissão.
 *
 * <p>Fora do escopo desta versão: Bloco J (Balanço Patrimonial/DRE agregados), registro
 * 0007 (outras inscrições cadastrais — é pra inscrição em *outras* entidades, não a UF/IE
 * da própria empresa) e I052 (código de aglutinação — só alimenta o Bloco J).
 *
 * <p>Referências usadas na implementação: Manual de Orientação do Leiaute 9 da ECD
 * (RFB, jan/2026) e o gerador do legado Delphi (AxCtg, {@code F_SpedContabil.pas}, via
 * biblioteca ACBr) — a lógica de negócio replicada aqui vem de lá, mas sem recalcular a
 * hierarquia do plano de contas na unha: {@link ContaContabil#getCodContaSup()} já guarda
 * isso direto.
 */
@Service
public class SpedEcdService {

    private final DataManager dataManager;

    public SpedEcdService(DataManager dataManager) {
        this.dataManager = dataManager;
    }

    /**
     * Gera o arquivo do Sped ECD pro período informado — o manual exige que DT_INI e
     * DT_FIN estejam no mesmo ano-calendário; o plano de contas e os saldos mensais
     * (Bloco I) são carregados pelo {@code ano} correspondente (todo o exercício, mesmo
     * que o período pedido seja mais estreito — útil pra gerar só um mês em teste).
     *
     * @param versaoLeiaute valor gravado no campo COD_VER_LC do registro I010 —
     *                       configurável porque não é fixo entre exercícios/retificações
     *                       (ver {@code ConfigRel.versaoSpedEcd}).
     */
    public byte[] gerarArquivo(Integer codEmpresa, LocalDate dtIni, LocalDate dtFin, String versaoLeiaute) {
        if (dtIni.getYear() != dtFin.getYear()) {
            throw new IllegalArgumentException(
                    "Data inicial e final do Sped ECD devem estar no mesmo ano-calendário.");
        }

        Empresa empresa = dataManager.load(Empresa.class)
                .query("select e from Empresa e where e.codigo = :codEmpresa")
                .parameter("codEmpresa", codEmpresa)
                .one();

        int ano = dtIni.getYear();

        SpedTextWriter w = new SpedTextWriter();
        gravarBloco0(w, empresa, dtIni, dtFin);
        gravarBlocoI(w, empresa, codEmpresa, ano, dtIni, dtFin, versaoLeiaute);
        w.escreverBloco9("0");

        return w.finalizar();
    }

    private void gravarBloco0(SpedTextWriter w, Empresa empresa, LocalDate dtIni, LocalDate dtFin) {
        w.abrirBloco("0");

        String uf = empresa.getMunicipio() != null ? empresa.getMunicipio().getUf() : "";
        String codMun = empresa.getMunicipio() != null ? String.valueOf(empresa.getMunicipio().getCodigo()) : "";
        String codScp = (empresa.getCodScp() == null || empresa.getCodScp() == 0L)
                ? "" : String.format("%014d", empresa.getCodScp());

        w.registro("0000",
                "LECD",
                dtIni,
                dtFin,
                empresa.getNome(),
                SpedTextWriter.soDigitos(empresa.getCnpj()),
                uf,
                SpedTextWriter.soDigitos(empresa.getInscEst()),
                codMun,
                "", // IM — não modelado
                id(empresa.getIndSitEsp()),
                id(empresa.getIndSitIniPer()),
                id(empresa.getIndNire()),
                id(empresa.getIndFinEsc()),
                "", // COD_HASH_SUB — só pra escrituração substituta
                id(empresa.getIndGrandePorte()),
                id(empresa.getTipEcd()),
                codScp,
                boolSN(empresa.getIdentMf()),
                boolSN(empresa.getIndEscCons()),
                id(empresa.getIndCentralizada()),
                id(empresa.getIndMudancaPc()),
                id(empresa.getCodPlanRef())
        );

        w.registro("0001", 0);

        w.fecharBloco("0990");
    }

    private void gravarBlocoI(SpedTextWriter w, Empresa empresa, Integer codEmpresa, Integer ano,
                               LocalDate dtIni, LocalDate dtFin, String versaoLeiaute) {
        w.abrirBloco("I");

        w.registro("I001", 0);
        w.registro("I010", "G", versaoLeiaute);

        String desMun = empresa.getMunicipio() != null ? empresa.getMunicipio().getNome() : "";
        String nireDigitos = SpedTextWriter.soDigitos(empresa.getNire());
        String nire = nireDigitos.isEmpty() ? "" : String.format("%011d", Long.parseLong(nireDigitos));
        // Campo 05 (QTD_LIN) é a quantidade total de linhas do arquivo inteiro — só se
        // sabe depois de escrever tudo, inclusive o bloco 9; resolvido em finalizar().
        w.registro("I030",
                "TERMO DE ABERTURA", // DNRC_ABERT, texto fixo (17 chars)
                empresa.getNumOrdemLivroEcd(),
                "DIARIO CONTABIL GERAL", // NAT_LIVR
                SpedTextWriter.QTD_LIN_ARQUIVO, // QTD_LIN
                empresa.getNome(),
                nire,
                SpedTextWriter.soDigitos(empresa.getCnpj()),
                "", // DT_ARQ — data de arquivamento dos atos constitutivos, não modelada
                "", // DT_ARQ_CONV — conversão de sociedade simples em empresária, raro
                desMun,
                dtFin); // DT_EX_SOCIAL

        gravarPlanoDeContas(w, empresa, codEmpresa, ano);
        gravarHistoricos(w, codEmpresa);
        gravarCentrosCusto(w, codEmpresa);
        gravarSaldosPeriodicos(w, codEmpresa, ano, dtIni, dtFin);
        gravarLancamentos(w, codEmpresa, dtIni, dtFin);

        w.fecharBloco("I990");
    }

    private void gravarPlanoDeContas(SpedTextWriter w, Empresa empresa, Integer codEmpresa, Integer ano) {
        boolean temPlanoReferencial = empresa.getCodPlanRef() != null;
        LocalDate dtAltPadrao = LocalDate.of(ano, 1, 1);

        List<ContaContabil> contas = dataManager.load(ContaContabil.class)
                .query("select c from ContaContabil c " +
                        "where c.codEmpresa = :codEmpresa and c.ano = :ano " +
                        "order by c.codigo")
                .parameter("codEmpresa", codEmpresa)
                .parameter("ano", ano)
                .list();

        for (ContaContabil conta : contas) {
            boolean analitica = Boolean.TRUE.equals(conta.getAnalitica());
            LocalDate dtAlt = conta.getCreatedDate() != null ? conta.getCreatedDate().toLocalDate() : dtAltPadrao;

            w.registro("I050",
                    dtAlt,
                    conta.getCodNat() == null ? "" : String.format("%02d", conta.getCodNat().getId()),
                    analitica ? "A" : "S",
                    conta.getGrau(),
                    conta.getCodigo(),
                    conta.getCodContaSup(),
                    conta.getNome());

            if (temPlanoReferencial && analitica && conta.getContaReferencial() != null) {
                w.registro("I051", "", conta.getContaReferencial().getCodigo());
            }
        }
    }

    private void gravarHistoricos(SpedTextWriter w, Integer codEmpresa) {
        List<HistoricoContabil> historicos = dataManager.load(HistoricoContabil.class)
                .query("select h from HistoricoContabil h where h.codEmpresa = :codEmpresa order by h.codigo")
                .parameter("codEmpresa", codEmpresa)
                .list();

        for (HistoricoContabil historico : historicos) {
            if (historico.getDescricao() == null || historico.getDescricao().isBlank()) {
                continue;
            }
            w.registro("I075", codHist(historico.getCodigo()), historico.getDescricao());
        }
    }

    private void gravarCentrosCusto(SpedTextWriter w, Integer codEmpresa) {
        List<CentroCusto> centros = dataManager.load(CentroCusto.class)
                .query("select c from CentroCusto c where c.codEmpresa = :codEmpresa order by c.codigo")
                .parameter("codEmpresa", codEmpresa)
                .list();

        for (CentroCusto centro : centros) {
            LocalDate dtAlt = centro.getCreatedDate() != null ? centro.getCreatedDate().toLocalDate() : null;
            w.registro("I100", dtAlt, centro.getCodigo(), centro.getNome());
        }
    }

    private void gravarSaldosPeriodicos(SpedTextWriter w, Integer codEmpresa, Integer ano,
                                         LocalDate dtIni, LocalDate dtFin) {
        // I150 um por mês do período DECLARADO (dtIni-dtFin), não do ano inteiro — regra
        // REGRA_CONTINUIDADE_SALDOS_PERIODICOS do manual exige continuidade sem buracos
        // dentro do período da escrituração, que é o intervalo pedido, não necessariamente
        // o exercício inteiro (confirmado contra amostra real gerada pro legado com um
        // período de um mês só: só saiu 1 I150, não 12).
        YearMonth mesIni = YearMonth.from(dtIni);
        YearMonth mesFin = YearMonth.from(dtFin);
        for (YearMonth ym = mesIni; !ym.isAfter(mesFin); ym = ym.plusMonths(1)) {
            int mes = ym.getMonthValue();
            LocalDate inicioMes = ym.atDay(1);
            LocalDate fimMes = ym.atEndOfMonth();
            w.registro("I150", inicioMes, fimMes);

            // Só contas analíticas — contas sintéticas (somatório) não entram no I155: o
            // saldo delas é obtido por agregação da hierarquia (COD_CTA_SUP no I050) pelo
            // próprio PVA, não declarado aqui. Confirmado contra amostra real: o legado só
            // emite I155 pras contas analíticas.
            List<SaldoConta> saldos = dataManager.load(SaldoConta.class)
                    .query("select s from SaldoConta s " +
                            "where s.contaContabil.codEmpresa = :codEmpresa " +
                            "and s.contaContabil.ano = :ano and s.mes = :mes " +
                            "and s.contaContabil.analitica = true " +
                            "and (s.saldoAnterior <> 0 or s.debitoMes <> 0 or s.creditoMes <> 0) " +
                            "order by s.contaContabil.codigo")
                    .parameter("codEmpresa", codEmpresa)
                    .parameter("ano", ano)
                    .parameter("mes", mes)
                    .list();

            for (SaldoConta saldo : saldos) {
                BigDecimal saldoIni = saldo.getSaldoAnterior();
                BigDecimal saldoFin = saldo.getSaldoAtual();
                w.registro("I155",
                        saldo.getContaContabil().getCodigo(),
                        "", // COD_CCUS — saldo não é segregado por centro de custo neste modelo
                        saldoIni.abs(),
                        indDC(saldoIni),
                        saldo.getDebitoMes(),
                        saldo.getCreditoMes(),
                        saldoFin.abs(),
                        indDC(saldoFin));
            }
        }
    }

    private void gravarLancamentos(SpedTextWriter w, Integer codEmpresa, LocalDate dtIni, LocalDate dtFin) {
        List<Lancamento> lancamentos = dataManager.load(Lancamento.class)
                .query("select l from Lancamento l where l.codEmpresa = :codEmpresa " +
                        "and l.dataLancamento >= :dtIni and l.dataLancamento <= :dtFin " +
                        "order by l.dataLancamento, l.numero")
                .parameter("codEmpresa", codEmpresa)
                .parameter("dtIni", dtIni)
                .parameter("dtFin", dtFin)
                .list();

        for (Lancamento lancamento : lancamentos) {
            String numLcto = String.format("%02d%06d", lancamento.getMes(), lancamento.getNumero());
            // "enc" é o valor gravado por EncerramentoService pros lançamentos de
            // encerramento de contas de resultado — ver EncerramentoService.java
            String indLcto = "enc".equals(lancamento.getOrigem()) ? "E" : "N";

            w.registro("I200", numLcto, lancamento.getDataLancamento(), lancamento.getValor(), indLcto,
                    ""); // DT_LCTO_EXT — data no sistema externo de origem, não modelada

            String codHistPad = lancamento.getHistoricoContabil() != null
                    ? codHist(lancamento.getHistoricoContabil().getCodigo()) : "";
            String hist = lancamento.getComplementoHistorico();

            w.registro("I250", lancamento.getContaDevedora().getCodigo(), "",
                    lancamento.getValor(), "D", "", codHistPad, hist);
            w.registro("I250", lancamento.getContaCredora().getCodigo(), "",
                    lancamento.getValor(), "C", "", codHistPad, hist);
        }
    }

    private static String codHist(Integer codigo) {
        return codigo == null ? "" : String.format("%03d", codigo);
    }

    private static String indDC(BigDecimal valor) {
        return valor.signum() < 0 ? "C" : "D";
    }

    private static String id(EnumClass<Integer> enumValue) {
        return enumValue == null ? "" : String.valueOf(enumValue.getId());
    }

    private static String boolSN(Boolean valor) {
        return Boolean.TRUE.equals(valor) ? "S" : "N";
    }
}
