package br.com.axialsoftware.axctg3.service.contabil;

import br.com.axialsoftware.axctg3.entity.cadastros.CentroCusto;
import br.com.axialsoftware.axctg3.entity.cadastros.Empresa;
import br.com.axialsoftware.axctg3.entity.contabil.ContaContabil;
import br.com.axialsoftware.axctg3.entity.contabil.HistoricoContabil;
import br.com.axialsoftware.axctg3.entity.contabil.Lancamento;
import br.com.axialsoftware.axctg3.entity.contabil.SaldoConta;
import br.com.axialsoftware.axctg3.entity.enums.CodAssin;
import br.com.axialsoftware.axctg3.entity.enums.CodNat;
import io.jmix.core.DataManager;
import io.jmix.core.metamodel.datatype.EnumClass;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Gera o arquivo texto do Sped ECD (Escrituração Contábil Digital) — v1: Bloco 0
 * (abertura/identificação) + Bloco I (lançamentos contábeis) + Bloco J (Balanço
 * Patrimonial/DRE), leiaute 9 (jan/2026).
 *
 * <p>Não assina nem transmite nada — o arquivo gerado aqui é submetido pelo usuário ao
 * PVA do Sped Contábil (Programa Validador e Assinador, oficial da Receita Federal), que
 * faz a validação de conteúdo, assinatura digital e transmissão.
 *
 * <p>Fora do escopo desta versão: J210/J215 (DLPA/DMPL — movimentação de Lucros/Prejuízos
 * Acumulados, registro facultativo) e Bloco K (Conglomerados Econômicos — só obrigatório
 * pra controladoras com demonstrações consolidadas). Blocos C e K saem só como marcador
 * vazio — ver {@link #gravarBlocoCVazio} pro motivo do Bloco C.
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
        gravarBlocoCVazio(w);
        Map<String, BigDecimal> saldoResultadoAtual = gravarBlocoI(w, empresa, codEmpresa, ano, dtIni, dtFin, versaoLeiaute);
        gravarBlocoJ(w, empresa, codEmpresa, ano, dtIni, dtFin, saldoResultadoAtual);
        gravarBlocoKVazio(w);
        w.escreverBloco9("0");

        return w.finalizar();
    }

    /**
     * Bloco C (Informações Recuperadas da Escrituração Contábil Anterior) — o manual é
     * explícito que "os registros do Bloco C não precisam ser importados, pois são
     * preenchidos pelo próprio PGE do Sped Contábil" quando o usuário usa o menu
     * Escrituração/Recuperar ECD anterior *dentro do PVA*. Não é papel do gerador
     * preencher isso — só o marcador de bloco (todo bloco é estruturalmente obrigatório,
     * mesmo sem dados, conforme cap. 3.1 do manual).
     */
    private void gravarBlocoCVazio(SpedTextWriter w) {
        w.abrirBloco("C");
        w.registro("C001", 1); // IND_DAD = 1 (bloco sem dados informados)
        w.fecharBloco("C990");
    }

    /**
     * Bloco K (Conglomerados Econômicos) — só obrigatório ter dados pra controladoras que
     * apresentam demonstrações consolidadas (Lei 6.404/76 / CPC 36); fora desse caso, é só
     * o marcador vazio, igual ao Bloco C.
     */
    private void gravarBlocoKVazio(SpedTextWriter w) {
        w.abrirBloco("K");
        w.registro("K001", 1); // IND_DAD = 1 (bloco sem dados informados)
        w.fecharBloco("K990");
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

        // COD_ENT_REF = UF da própria empresa (Secretaria da Fazenda estadual, tabela do
        // manual) — registro obrigatório (1:N) apesar do nome sugerir só inscrições em
        // *outras* entidades; confirmado contra amostra real e apontado pelo usuário via
        // erro do PVA. COD_INSCR fica em branco (opcional).
        w.registro("0007", uf, "");

        w.fecharBloco("0990");
    }

    /**
     * @return saldo de cada conta de resultado (analítica e sintética, chave = código)
     *         antes do encerramento — ver {@link #calcularSaldoResultadoAntesEncerramento}
     *         — reaproveitado pelo Bloco J (DRE, registro J150) pra não recalcular duas
     *         vezes a mesma coisa.
     */
    private Map<String, BigDecimal> gravarBlocoI(SpedTextWriter w, Empresa empresa, Integer codEmpresa, Integer ano,
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
        Map<String, BigDecimal> saldoResultadoAtual = gravarSaldoResultadoAntesEncerramento(w, codEmpresa, ano, dtFin);

        w.fecharBloco("I990");
        return saldoResultadoAtual;
    }

    /**
     * Conta "coringa" herdada do plano de contas do legado Delphi (grau 1, sem hierarquia,
     * nome genérico "Diversos") — não representa uma conta contábil real e não deve
     * aparecer no arquivo. Excluída aqui em vez de exigir soft-delete no cadastro porque a
     * herança se repete em todo plano de contas migrado do legado, não só numa empresa.
     */
    private static final String CONTA_DUMMY_LEGADO = "000000000";

    private void gravarPlanoDeContas(SpedTextWriter w, Empresa empresa, Integer codEmpresa, Integer ano) {
        boolean temPlanoReferencial = empresa.getCodPlanRef() != null;
        LocalDate dtAltPadrao = LocalDate.of(ano, 1, 1);

        List<ContaContabil> contas = dataManager.load(ContaContabil.class)
                .query("select c from ContaContabil c " +
                        "where c.codEmpresa = :codEmpresa and c.ano = :ano and c.codigo <> :contaDummy " +
                        "order by c.codigo")
                .parameter("codEmpresa", codEmpresa)
                .parameter("ano", ano)
                .parameter("contaDummy", CONTA_DUMMY_LEGADO)
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

            // Código de aglutinação (Bloco J): default = o próprio código da conta —
            // confirmado contra amostra real, empresa que não usa uma tabela de
            // aglutinação separada da própria hierarquia do plano de contas. Só pra
            // analíticas (REGRA_REGISTRO_PARA_CONTA_ANALITICA do manual).
            if (analitica) {
                w.registro("I052", "", conta.getCodigo());
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
                            "and s.contaContabil.codigo <> :contaDummy " +
                            "and (s.saldoAnterior <> 0 or s.debitoMes <> 0 or s.creditoMes <> 0) " +
                            "order by s.contaContabil.codigo")
                    .parameter("codEmpresa", codEmpresa)
                    .parameter("ano", ano)
                    .parameter("mes", mes)
                    .parameter("contaDummy", CONTA_DUMMY_LEGADO)
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
                    lancamento.getValor(), "D", "", codHistPad, hist,
                    ""); // COD_PART — código do participante, não modelado
            w.registro("I250", lancamento.getContaCredora().getCodigo(), "",
                    lancamento.getValor(), "C", "", codHistPad, hist,
                    ""); // COD_PART
        }
    }

    /**
     * Registros I350/I355 (saldo das contas de resultado antes do encerramento) — exigidos
     * pra dar suporte ao Bloco J: como o encerramento zera as contas de resultado (transfere
     * o resultado pra {@code Empresa.codContaEnc}), o saldo "puro" do exercício só existe
     * olhando os lançamentos sem a reversão do próprio encerramento — ver
     * {@link #calcularSaldoResultadoAntesEncerramento}. DT_RES = mesma data de
     * DT_EX_SOCIAL já usada no I030 (fim do período).
     */
    private Map<String, BigDecimal> gravarSaldoResultadoAntesEncerramento(SpedTextWriter w, Integer codEmpresa,
                                                                           Integer ano, LocalDate dtFin) {
        Map<String, BigDecimal> saldos = calcularSaldoResultadoAntesEncerramento(codEmpresa, ano);
        if (saldos.isEmpty()) {
            return saldos;
        }

        w.registro("I350", dtFin);

        List<ContaContabil> contasAnaliticas = dataManager.load(ContaContabil.class)
                .query("select c from ContaContabil c where c.codEmpresa = :codEmpresa and c.ano = :ano " +
                        "and c.codNat = :codNat and c.analitica = true order by c.codigo")
                .parameter("codEmpresa", codEmpresa)
                .parameter("ano", ano)
                .parameter("codNat", CodNat.CONTAS_DE_RESULTADO.getId())
                .list();
        for (ContaContabil conta : contasAnaliticas) {
            BigDecimal saldo = saldos.getOrDefault(conta.getCodigo(), BigDecimal.ZERO);
            if (saldo.signum() == 0) {
                continue;
            }
            w.registro("I355", conta.getCodigo(), "", saldo.abs(), indDC(saldo));
        }
        return saldos;
    }

    /**
     * Reconstrói o saldo de cada conta de resultado (analítica e sintética, subindo a
     * hierarquia igual {@code LancamentoService.atualizarSaldosGrupo}) como se o
     * encerramento daquele ano nunca tivesse sido lançado — soma todos os lançamentos do
     * ano exceto os de origem "enc" (gravados por {@code EncerramentoService}). Reaproveitado
     * pro ano corrente (I355/J150.VL_CTA_FIN) e pro ano anterior (J150.VL_CTA_INI,
     * comparativo — fica vazio se a empresa não tinha esse exercício no axctg3 ainda).
     */
    private Map<String, BigDecimal> calcularSaldoResultadoAntesEncerramento(Integer codEmpresa, Integer ano) {
        List<ContaContabil> contasResultado = dataManager.load(ContaContabil.class)
                .query("select c from ContaContabil c where c.codEmpresa = :codEmpresa and c.ano = :ano " +
                        "and c.codNat = :codNat")
                .parameter("codEmpresa", codEmpresa)
                .parameter("ano", ano)
                .parameter("codNat", CodNat.CONTAS_DE_RESULTADO.getId())
                .list();
        if (contasResultado.isEmpty()) {
            return Map.of();
        }

        Map<String, ContaContabil> contasPorCodigo = new HashMap<>();
        Map<String, BigDecimal> saldos = new LinkedHashMap<>();
        for (ContaContabil conta : contasResultado) {
            contasPorCodigo.put(conta.getCodigo(), conta);
            saldos.put(conta.getCodigo(), BigDecimal.ZERO);
        }

        List<Lancamento> lancamentosDoAno = dataManager.load(Lancamento.class)
                .query("select l from Lancamento l where l.codEmpresa = :codEmpresa and l.ano = :ano " +
                        "and (l.origem is null or l.origem <> 'enc')")
                .parameter("codEmpresa", codEmpresa)
                .parameter("ano", ano)
                .list();
        for (Lancamento lancamento : lancamentosDoAno) {
            aplicarNaHierarquiaResultado(contasPorCodigo, saldos, lancamento.getContaDevedora(), lancamento.getValor());
            aplicarNaHierarquiaResultado(contasPorCodigo, saldos, lancamento.getContaCredora(), lancamento.getValor().negate());
        }
        return saldos;
    }

    /** Débito soma, crédito subtrai (via sinal de {@code delta}) — mesma convenção de {@code atualizarSaldosGrupo}. */
    private void aplicarNaHierarquiaResultado(Map<String, ContaContabil> contasResultado,
                                               Map<String, BigDecimal> saldos, ContaContabil contaLancada, BigDecimal delta) {
        if (contaLancada.getCodNat() != CodNat.CONTAS_DE_RESULTADO) {
            return; // contrapartida (caixa, fornecedores etc.) não entra na DRE
        }
        ContaContabil atual = contaLancada;
        while (atual != null) {
            BigDecimal saldoAtual = saldos.get(atual.getCodigo());
            if (saldoAtual == null) {
                break; // segurança — não deveria acontecer (mapa já tem todas as contas de resultado do ano)
            }
            saldos.put(atual.getCodigo(), saldoAtual.add(delta));
            if (atual.getGrau() == 1) {
                break;
            }
            atual = contasResultado.get(atual.getCodContaSup().trim());
        }
    }

    /**
     * Bloco J (Demonstrações Contábeis): J100 (Balanço Patrimonial, contas de ativo e
     * passivo/PL) + J150 (DRE, contas de resultado) + termo de encerramento/signatários.
     * Código de aglutinação (COD_AGL) = próprio código da conta em ambos, mesma convenção
     * do I052. Fora do escopo: J210/J215 (DLPA/DMPL, facultativo).
     */
    private void gravarBlocoJ(SpedTextWriter w, Empresa empresa, Integer codEmpresa, Integer ano,
                               LocalDate dtIni, LocalDate dtFin, Map<String, BigDecimal> saldoResultadoAtual) {
        w.abrirBloco("J");
        w.registro("J001", 0);
        w.registro("J005", dtIni, dtFin, 1, "");

        List<ContaContabil> contasBalanco = dataManager.load(ContaContabil.class)
                .query("select c from ContaContabil c where c.codEmpresa = :codEmpresa and c.ano = :ano " +
                        "and c.codNat in :nats and c.codigo <> :contaDummy order by c.codigo")
                .parameter("codEmpresa", codEmpresa)
                .parameter("ano", ano)
                .parameter("nats", List.of(CodNat.CONTAS_DE_ATIVO.getId(), CodNat.CONTAS_DE_PASSIVO.getId(),
                        CodNat.PATRIMONIO_LIQUIDO.getId()))
                .parameter("contaDummy", CONTA_DUMMY_LEGADO)
                .list();

        int mesIni = dtIni.getMonthValue();
        int mesFin = dtFin.getMonthValue();
        for (ContaContabil conta : contasBalanco) {
            boolean analitica = Boolean.TRUE.equals(conta.getAnalitica());
            List<SaldoConta> saldosConta = conta.getSaldosConta();
            BigDecimal saldoIni = saldosConta.get(mesIni - 1).getSaldoAnterior();
            BigDecimal saldoFin = saldosConta.get(mesFin - 1).getSaldoAtual();
            String indGrpBal = conta.getCodNat() == CodNat.CONTAS_DE_ATIVO ? "A" : "P";

            w.registro("J100",
                    conta.getCodigo(),
                    analitica ? "D" : "T",
                    conta.getGrau(),
                    conta.getCodContaSup(),
                    indGrpBal,
                    conta.getNome(),
                    saldoIni.abs(), indDC(saldoIni),
                    saldoFin.abs(), indDC(saldoFin),
                    ""); // NOTA_EXP_REF — sem notas explicativas modeladas
        }

        List<ContaContabil> contasResultado = dataManager.load(ContaContabil.class)
                .query("select c from ContaContabil c where c.codEmpresa = :codEmpresa and c.ano = :ano " +
                        "and c.codNat = :codNat order by c.codigo")
                .parameter("codEmpresa", codEmpresa)
                .parameter("ano", ano)
                .parameter("codNat", CodNat.CONTAS_DE_RESULTADO.getId())
                .list();

        // Comparativo do período anterior — facultativo (campo "Não" obrigatório no
        // manual); fica em branco se a empresa não tinha esse exercício ainda no axctg3.
        Map<String, BigDecimal> saldoResultadoAnterior = calcularSaldoResultadoAntesEncerramento(codEmpresa, ano - 1);

        int numOrdem = 1;
        for (ContaContabil conta : contasResultado) {
            boolean analitica = Boolean.TRUE.equals(conta.getAnalitica());
            BigDecimal saldoAnt = saldoResultadoAnterior.get(conta.getCodigo());
            BigDecimal saldoFin = saldoResultadoAtual.getOrDefault(conta.getCodigo(), BigDecimal.ZERO);

            w.registro("J150",
                    numOrdem++,
                    conta.getCodigo(),
                    analitica ? "D" : "T",
                    conta.getGrau(),
                    conta.getCodContaSup(),
                    conta.getNome(),
                    saldoAnt == null ? "" : saldoAnt.abs(),
                    saldoAnt == null ? "" : indDC(saldoAnt),
                    saldoFin.abs(), indDC(saldoFin),
                    saldoFin.signum() < 0 ? "R" : "D", // natureza da linha — confirmado contra amostra real
                    ""); // NOTA_EXP_REF
        }

        gravarTermoEncerramentoJ(w, empresa, dtIni, dtFin);

        w.fecharBloco("J990");
    }

    /**
     * J900 (Termo de Encerramento, mesmos dados do I030/0000) + J930 (signatários — o
     * manual exige pelo menos um contador/contabilista e um que não seja contador; usa os
     * dados já cadastrados em {@code Empresa}, aba "Dados dos responsáveis").
     */
    private void gravarTermoEncerramentoJ(SpedTextWriter w, Empresa empresa, LocalDate dtIni, LocalDate dtFin) {
        w.registro("J900",
                "TERMO DE ENCERRAMENTO",
                empresa.getNumOrdemLivroEcd(),
                "DIARIO CONTABIL GERAL",
                empresa.getNome(),
                SpedTextWriter.QTD_LIN_ARQUIVO,
                dtIni,
                dtFin);

        if (empresa.getNmContabilista() != null && !empresa.getNmContabilista().isBlank()) {
            w.registro("J930",
                    empresa.getNmContabilista(),
                    SpedTextWriter.soDigitos(empresa.getCpfContabilista()),
                    qualif(empresa.getCodAssinContabilista()),
                    id(empresa.getCodAssinContabilista()),
                    empresa.getInscCrc(),
                    empresa.getEmailContabilista(),
                    SpedTextWriter.soDigitos(empresa.getFoneContabilista()),
                    empresa.getUfCrc(),
                    empresa.getNumSeqCrc(),
                    empresa.getDtCrc(),
                    "N");
        }
        if (empresa.getNmResponsavel() != null && !empresa.getNmResponsavel().isBlank()) {
            w.registro("J930",
                    empresa.getNmResponsavel(),
                    SpedTextWriter.soDigitos(empresa.getCpfResponsavel()),
                    qualif(empresa.getCodAssinResponsavel()),
                    id(empresa.getCodAssinResponsavel()),
                    "", // IND_CRC — só pra contador
                    "", // EMAIL — não modelado pro responsável legal
                    SpedTextWriter.soDigitos(empresa.getFoneResponsavel()),
                    "", "", "", // UF_CRC/NUM_SEQ_CRC/DT_CRC — só pra contador
                    boolSN(empresa.getIndRespLegal()));
        }
    }

    private static String qualif(CodAssin codAssin) {
        return codAssin == null ? "" : codAssin.name().replace('_', ' ');
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
