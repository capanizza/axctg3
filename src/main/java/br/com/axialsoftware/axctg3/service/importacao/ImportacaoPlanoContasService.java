package br.com.axialsoftware.axctg3.service.importacao;

import br.com.axialsoftware.axctg3.entity.contabil.ContaContabil;
import br.com.axialsoftware.axctg3.entity.contabil.SaldoConta;
import br.com.axialsoftware.axctg3.entity.enums.CodNat;
import br.com.axialsoftware.axctg3.entity.enums.CodPlanRef;
import br.com.axialsoftware.axctg3.entity.enums.SituacaoLote;
import br.com.axialsoftware.axctg3.entity.enums.TipoLote;
import br.com.axialsoftware.axctg3.entity.importacao.ImpContaContabil;
import br.com.axialsoftware.axctg3.entity.importacao.ImpLote;
import br.com.axialsoftware.axctg3.entity.importacao.ImpSaldoConta;
import br.com.axialsoftware.axctg3.entity.tabelas.ContaReferencial;
import br.com.axialsoftware.axctg3.service.UtilGeralService;
import io.jmix.core.DataManager;
import io.jmix.core.FetchPlan;
import io.jmix.core.SaveContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Importa um {@link ImpLote} de plano de contas exportado do legado: contas e os 12 saldos
 * mensais de cada uma, sintéticas inclusive, copiados como estão — sem passar pelo
 * {@code LancamentoService.atualizarSaldos} (o motor de saldos já foi validado contra o legado
 * com o LancamentoTmp; daqui em diante o saldo do legado é a palavra final).
 * <p>
 * Por isso a <b>conferência</b> existe: antes de gravar (e também sozinha, pelo botão
 * "Conferir"), o plano é montado em memória e checado — continuidade mês a mês de cada conta
 * e cada sintética igual à soma das filhas diretas. Divergência não bloqueia: vira relatório
 * no lote, e o usuário decide. Só os <b>erros</b> estruturais (conta superior inexistente,
 * código repetido, plano já existente...) impedem a gravação.
 * <p>
 * As regras que o exportador antigo ({@code exportacao.ExportacaoPlanoContabil} no projeto
 * Axial) aplicava na hora de gravar vivem aqui agora: natureza pelo 1º dígito, conta superior
 * pela ordem do plano + grau, máscara da conta referencial pelo tamanho, conta coringa
 * {@code 000...} normalizada pra grau 1 / "DIVERSOS".
 */
@Service
public class ImportacaoPlanoContasService {

    private static final int TAMANHO_NOME = 50;

    private final DataManager dataManager;
    private final UtilGeralService utilGeralService;

    public ImportacaoPlanoContasService(DataManager dataManager, UtilGeralService utilGeralService) {
        this.dataManager = dataManager;
        this.utilGeralService = utilGeralService;
    }

    /**
     * Monta o plano em memória e confere, sem gravar nada além do relatório no lote. Se a
     * empresa já tem plano nesse ano, compara também o lote com o que está gravado — é assim
     * que se valida o exportador contra um plano já conferido.
     */
    @Transactional
    public Relatorio conferir(UUID loteId) {
        ImpLote lote = dataManager.load(ImpLote.class).id(loteId).one();
        Relatorio relatorio = new Relatorio();
        validarLote(lote, relatorio, false);
        if (relatorio.erros.isEmpty()) {
            Map<String, ContaLida> contas = montarPlano(lote, relatorio);
            if (relatorio.erros.isEmpty()) {
                conferirSaldos(contas, relatorio);
                compararComPlanoGravado(lote, contas, relatorio);
            }
        }
        gravarRelatorio(lote, relatorio, false);
        return relatorio;
    }

    /** Confere e, sem erro estrutural, grava as contas e os saldos. Tudo ou nada. */
    @Transactional
    public Relatorio importar(UUID loteId) {
        ImpLote lote = dataManager.load(ImpLote.class).id(loteId).one();
        Relatorio relatorio = new Relatorio();
        validarLote(lote, relatorio, true);
        Map<String, ContaLida> contas = null;
        if (relatorio.erros.isEmpty()) {
            contas = montarPlano(lote, relatorio);
            if (relatorio.erros.isEmpty()) {
                conferirSaldos(contas, relatorio);
            }
        }
        if (relatorio.erros.isEmpty()) {
            gravarPlano(lote, contas);
            relatorio.importadas = contas.size();
        }
        gravarRelatorio(lote, relatorio, relatorio.erros.isEmpty());
        return relatorio;
    }

    /** {@code paraImportar}: só a importação exige empresa da sessão, plano vazio e lote não importado. */
    private void validarLote(ImpLote lote, Relatorio relatorio, boolean paraImportar) {
        if (lote.getTipo() != TipoLote.PLANO_CONTAS) {
            relatorio.erros.add("O lote não é de plano de contas.");
            return;
        }
        if (lote.getSituacao() == SituacaoLote.EXPORTANDO) {
            relatorio.erros.add("A exportação deste lote não terminou (situação EXPORTANDO). Exporte de novo.");
            return;
        }
        if (lote.getAno() == null) {
            relatorio.erros.add("O lote não informa o ano do plano de contas.");
            return;
        }
        if (!paraImportar) {
            return;
        }
        if (lote.getSituacao() == SituacaoLote.IMPORTADO) {
            relatorio.erros.add("Este lote já foi importado.");
            return;
        }
        Integer codEmpresa = utilGeralService.getCodEmpresa();
        if (!lote.getCodEmpresa().equals(codEmpresa)) {
            relatorio.erros.add("O lote é da empresa " + lote.getCodEmpresa() + ", mas a empresa selecionada é a "
                    + codEmpresa + ". Selecione a empresa " + lote.getCodEmpresa() + " antes de importar.");
            return;
        }
        Long existentes = dataManager.loadValue(
                        "select count(e) from ContaContabil e where e.codEmpresa = :codEmpresa and e.ano = :ano",
                        Long.class)
                .parameter("codEmpresa", lote.getCodEmpresa())
                .parameter("ano", lote.getAno())
                .one();
        if (existentes > 0) {
            relatorio.erros.add("A empresa " + lote.getCodEmpresa() + " já tem " + existentes
                    + " contas no plano de " + lote.getAno() + ". A importação só grava um plano vazio.");
        }
    }

    /**
     * Lê as linhas do lote e devolve o plano, em ordem de código, com conta superior, natureza
     * e saldos resolvidos. Problemas estruturais vão para {@code relatorio.erros}.
     */
    Map<String, ContaLida> montarPlano(ImpLote lote, Relatorio relatorio) {
        List<ImpContaContabil> linhas = dataManager.load(ImpContaContabil.class)
                .query("select e from ImpContaContabil e where e.lote = :lote order by e.codigo")
                .parameter("lote", lote)
                .list();
        Map<String, ContaLida> contas = new LinkedHashMap<>();
        if (linhas.isEmpty()) {
            relatorio.erros.add("O lote não tem nenhuma conta.");
            return contas;
        }

        String[] contaSup = new String[30];
        for (ImpContaContabil linha : linhas) {
            ContaLida conta = new ContaLida();
            conta.codigo = linha.getCodigo().trim();
            conta.nome = linha.getNome() == null ? "" : linha.getNome().trim();
            conta.grau = linha.getGrau() == null ? 0 : linha.getGrau();
            conta.analitica = "S".equalsIgnoreCase(linha.getAnalitica());
            conta.contaReferencial = linha.getContaReferencial();

            if (contas.containsKey(conta.codigo)) {
                relatorio.erros.add("Conta " + conta.codigo + " repetida no lote.");
                continue;
            }
            // coringa do legado (partida simples): chegava com grau 0 e nome vazio, o que
            // quebraria a subida de saldos (atualizarSaldos só para no grau 1)
            if (conta.codigo.startsWith("000")) {
                if (conta.grau != 1 || !"DIVERSOS".equals(conta.nome)) {
                    relatorio.avisos.add("Conta coringa " + conta.codigo + " normalizada para grau 1, nome DIVERSOS.");
                }
                conta.grau = 1;
                conta.nome = "DIVERSOS";
            }
            if (conta.nome.length() > TAMANHO_NOME) {
                relatorio.avisos.add("Conta " + conta.codigo + ": nome cortado em " + TAMANHO_NOME
                        + " caracteres (" + conta.nome + ").");
                conta.nome = conta.nome.substring(0, TAMANHO_NOME);
            }
            if (conta.nome.isEmpty()) {
                relatorio.erros.add("Conta " + conta.codigo + " sem nome.");
            }
            if (conta.grau < 1 || conta.grau >= contaSup.length) {
                relatorio.erros.add("Conta " + conta.codigo + " com grau inválido (" + conta.grau + ").");
                continue;
            }

            // mesma regra do exportador antigo: o plano vem em ordem de código, então a
            // superior de uma conta de grau g é a última sintética de grau g-1 vista antes dela
            if (conta.grau == 1) {
                conta.codContaSup = "";
            } else {
                conta.codContaSup = contaSup[conta.grau - 1];
                if (conta.codContaSup == null || conta.codContaSup.isEmpty()) {
                    relatorio.erros.add("Conta " + conta.codigo + " (grau " + conta.grau
                            + ") não tem conta sintética de grau " + (conta.grau - 1) + " acima dela.");
                }
            }
            if (!conta.analitica) {
                contaSup[conta.grau] = conta.codigo;
                // uma sintética nova encerra os ramos mais profundos da anterior
                for (int g = conta.grau + 1; g < contaSup.length; g++) {
                    contaSup[g] = null;
                }
            }
            conta.codNat = naturezaPeloCodigo(conta.codigo);
            contas.put(conta.codigo, conta);
        }

        lerSaldos(lote, contas, relatorio);
        return contas;
    }

    private void lerSaldos(ImpLote lote, Map<String, ContaLida> contas, Relatorio relatorio) {
        List<ImpSaldoConta> saldos = dataManager.load(ImpSaldoConta.class)
                .query("select e from ImpSaldoConta e where e.lote = :lote order by e.conta, e.mes")
                .parameter("lote", lote)
                .list();
        for (ImpSaldoConta saldo : saldos) {
            String codigo = saldo.getConta().trim();
            ContaLida conta = contas.get(codigo);
            if (conta == null) {
                relatorio.erros.add("Saldo da conta " + codigo + " (mês " + saldo.getMes()
                        + "), que não está no plano do lote.");
                continue;
            }
            int mes = saldo.getMes();
            if (mes < 1 || mes > 12) {
                relatorio.erros.add("Conta " + codigo + ": saldo com mês inválido (" + mes + ").");
                continue;
            }
            if (conta.temSaldo[mes - 1]) {
                relatorio.erros.add("Conta " + codigo + ": mais de um saldo para o mês " + mes + ".");
                continue;
            }
            conta.temSaldo[mes - 1] = true;
            conta.saldoAnterior[mes - 1] = zeroSeNulo(saldo.getSaldoAnterior());
            conta.debito[mes - 1] = zeroSeNulo(saldo.getDebitoMes());
            conta.credito[mes - 1] = zeroSeNulo(saldo.getCreditoMes());
            BigDecimal transf = zeroSeNulo(saldo.getSaldoTransf());
            if (transf.signum() != 0) {
                conta.mesesComTransf++;
                conta.saldoTransf = conta.saldoTransf.add(transf);
            }
        }
        for (ContaLida conta : contas.values()) {
            List<Integer> faltando = new ArrayList<>();
            for (int m = 0; m < 12; m++) {
                if (!conta.temSaldo[m]) {
                    faltando.add(m + 1);
                }
            }
            if (!faltando.isEmpty() && faltando.size() < 12) {
                relatorio.avisos.add("Conta " + conta.codigo + ": sem saldo nos meses " + faltando + " (gravados zerados).");
            } else if (faltando.size() == 12) {
                relatorio.avisos.add("Conta " + conta.codigo + ": nenhum saldo no legado (gravada zerada).");
            }
            if (conta.mesesComTransf > 1) {
                relatorio.avisos.add("Conta " + conta.codigo + ": saldo transferido em " + conta.mesesComTransf
                        + " meses no legado; gravada a soma " + conta.saldoTransf + ".");
            }
        }
    }

    /**
     * As duas checagens de valor combinadas com o usuário. Só leitura: o que diverge vai para
     * {@code relatorio.divergencias}, nada é corrigido.
     */
    void conferirSaldos(Map<String, ContaLida> contas, Relatorio relatorio) {
        // 1) continuidade: saldo anterior de um mês = saldo anterior + débito - crédito do mês antes
        for (ContaLida conta : contas.values()) {
            for (int m = 1; m < 12; m++) {
                BigDecimal esperado = conta.saldoAnterior[m - 1].add(conta.debito[m - 1]).subtract(conta.credito[m - 1]);
                if (esperado.compareTo(conta.saldoAnterior[m]) != 0) {
                    relatorio.divergencias.add("Conta " + conta.codigo + ", mês " + (m + 1)
                            + ": saldo anterior " + conta.saldoAnterior[m]
                            + ", mas o mês " + m + " fecha em " + esperado + ".");
                }
            }
        }

        // 2) cada sintética = soma das filhas diretas, mês a mês
        Map<String, List<ContaLida>> filhas = new HashMap<>();
        for (ContaLida conta : contas.values()) {
            if (conta.codContaSup != null && !conta.codContaSup.isEmpty()) {
                filhas.computeIfAbsent(conta.codContaSup, k -> new ArrayList<>()).add(conta);
            }
        }
        for (ContaLida conta : contas.values()) {
            List<ContaLida> lista = filhas.get(conta.codigo);
            if (conta.analitica || lista == null) {
                continue;
            }
            for (int m = 0; m < 12; m++) {
                BigDecimal ant = BigDecimal.ZERO, deb = BigDecimal.ZERO, cre = BigDecimal.ZERO;
                for (ContaLida filha : lista) {
                    ant = ant.add(filha.saldoAnterior[m]);
                    deb = deb.add(filha.debito[m]);
                    cre = cre.add(filha.credito[m]);
                }
                compararSoma(relatorio, conta, m + 1, "saldo anterior", conta.saldoAnterior[m], ant);
                compararSoma(relatorio, conta, m + 1, "débito", conta.debito[m], deb);
                compararSoma(relatorio, conta, m + 1, "crédito", conta.credito[m], cre);
            }
        }
    }

    private void compararSoma(Relatorio relatorio, ContaLida conta, int mes, String campo,
                              BigDecimal daConta, BigDecimal dasFilhas) {
        if (daConta.compareTo(dasFilhas) != 0) {
            relatorio.divergencias.add("Sintética " + conta.codigo + ", mês " + mes + ": " + campo
                    + " " + daConta + ", soma das filhas " + dasFilhas + ".");
        }
    }

    /**
     * Compara o lote com o plano da empresa/ano já gravado no axctg3, se houver: contas que só
     * existem de um lado, diferenças de cadastro e de saldo, mês a mês. Tudo vai para as
     * divergências, com o prefixo "Gravado:".
     */
    private void compararComPlanoGravado(ImpLote lote, Map<String, ContaLida> contas, Relatorio relatorio) {
        List<ContaContabil> gravadas = dataManager.load(ContaContabil.class)
                .query("select e from ContaContabil e where e.codEmpresa = :codEmpresa and e.ano = :ano")
                .parameter("codEmpresa", lote.getCodEmpresa())
                .parameter("ano", lote.getAno())
                .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE).add("saldosConta", FetchPlan.BASE))
                .list();
        if (gravadas.isEmpty()) {
            return;
        }
        relatorio.avisos.add("A empresa " + lote.getCodEmpresa() + " já tem " + gravadas.size()
                + " contas gravadas em " + lote.getAno() + "; o lote foi comparado com elas.");
        Map<String, ContaContabil> porCodigo = new HashMap<>();
        for (ContaContabil gravada : gravadas) {
            porCodigo.put(gravada.getCodigo(), gravada);
            if (!contas.containsKey(gravada.getCodigo())) {
                relatorio.divergencias.add("Gravado: conta " + gravada.getCodigo() + " não está no lote.");
            }
        }
        for (ContaLida lida : contas.values()) {
            ContaContabil gravada = porCodigo.get(lida.codigo);
            if (gravada == null) {
                relatorio.divergencias.add("Gravado: conta " + lida.codigo + " do lote não existe no plano gravado.");
                continue;
            }
            compararCampo(relatorio, lida.codigo, "nome", lida.nome, gravada.getNome());
            compararCampo(relatorio, lida.codigo, "grau", lida.grau, gravada.getGrau());
            compararCampo(relatorio, lida.codigo, "analítica", lida.analitica, Boolean.TRUE.equals(gravada.getAnalitica()));
            compararCampo(relatorio, lida.codigo, "conta superior", lida.codContaSup,
                    gravada.getCodContaSup() == null ? "" : gravada.getCodContaSup().trim());
            compararCampo(relatorio, lida.codigo, "natureza", lida.codNat,
                    gravada.getCodNat() == null ? null : gravada.getCodNat().getId());
            if (lida.saldoTransf.compareTo(zeroSeNulo(gravada.getSaldoTransf())) != 0) {
                relatorio.divergencias.add("Gravado: conta " + lida.codigo + ", saldo transferido " + lida.saldoTransf
                        + " no lote, " + zeroSeNulo(gravada.getSaldoTransf()) + " gravado.");
            }
            for (SaldoConta saldo : gravada.getSaldosConta()) {
                int m = saldo.getMes() - 1;
                if (m < 0 || m > 11) {
                    continue;
                }
                compararValor(relatorio, lida.codigo, saldo.getMes(), "saldo anterior", lida.saldoAnterior[m], saldo.getSaldoAnterior());
                compararValor(relatorio, lida.codigo, saldo.getMes(), "débito", lida.debito[m], saldo.getDebitoMes());
                compararValor(relatorio, lida.codigo, saldo.getMes(), "crédito", lida.credito[m], saldo.getCreditoMes());
            }
        }
    }

    private void compararCampo(Relatorio relatorio, String codigo, String campo, Object doLote, Object gravado) {
        if (doLote == null ? gravado != null : !doLote.equals(gravado)) {
            relatorio.divergencias.add("Gravado: conta " + codigo + ", " + campo + " \"" + doLote
                    + "\" no lote, \"" + gravado + "\" gravado.");
        }
    }

    private void compararValor(Relatorio relatorio, String codigo, int mes, String campo,
                               BigDecimal doLote, BigDecimal gravado) {
        if (doLote.compareTo(zeroSeNulo(gravado)) != 0) {
            relatorio.divergencias.add("Gravado: conta " + codigo + ", mês " + mes + ", " + campo + " "
                    + doLote + " no lote, " + zeroSeNulo(gravado) + " gravado.");
        }
    }

    private void gravarPlano(ImpLote lote, Map<String, ContaLida> contas) {
        Map<String, ContaReferencial> referenciais = new HashMap<>();
        SaveContext saveContext = new SaveContext();
        for (ContaLida lida : contas.values()) {
            ContaContabil conta = dataManager.create(ContaContabil.class);
            conta.setCodEmpresa(lote.getCodEmpresa());
            conta.setAno(lote.getAno());
            conta.setCodigo(lida.codigo);
            conta.setNome(lida.nome);
            conta.setGrau(lida.grau);
            conta.setAnalitica(lida.analitica);
            conta.setCodNat(CodNat.fromId(lida.codNat));
            conta.setCodContaSup(lida.codContaSup);
            conta.setSaldoTransf(lida.saldoTransf);
            conta.setContaReferencial(buscarReferencial(lida.contaReferencial, referenciais));

            List<SaldoConta> saldos = new ArrayList<>();
            for (int m = 0; m < 12; m++) {
                SaldoConta saldo = dataManager.create(SaldoConta.class);
                saldo.setContaContabil(conta);
                saldo.setMes(m + 1);
                saldo.setSaldoAnterior(lida.saldoAnterior[m]);
                saldo.setDebitoMes(lida.debito[m]);
                saldo.setCreditoMes(lida.credito[m]);
                saldos.add(saldo);
                saveContext.saving(saldo);
            }
            // com os 12 saldos já na coleção, o ContaContabilEventListener não cria os zerados
            conta.setSaldosConta(saldos);
            saveContext.saving(conta);
        }
        dataManager.save(saveContext);
    }

    private ContaReferencial buscarReferencial(String codigoLegado, Map<String, ContaReferencial> cache) {
        if (codigoLegado == null || codigoLegado.isBlank()) {
            return null;
        }
        String codigo = mascararReferencial(codigoLegado.trim());
        if (cache.containsKey(codigo)) {
            return cache.get(codigo);
        }
        ContaReferencial referencial = dataManager.load(ContaReferencial.class)
                .query("select e from ContaReferencial e where e.codigo = :codigo and e.codPlanRef = :codPlanRef")
                .parameter("codigo", codigo)
                .parameter("codPlanRef", CodPlanRef.PJ_em_geral_lucro_real.getId())
                .optional()
                .orElse(null);
        cache.put(codigo, referencial);
        return referencial;
    }

    /** O legado guarda a conta referencial sem pontos; a RFB usa {@code #.##.##...}. */
    public static String mascararReferencial(String semPontos) {
        if (semPontos.contains(".") || semPontos.length() < 3 || semPontos.length() % 2 == 0) {
            return semPontos;
        }
        StringBuilder sb = new StringBuilder(semPontos.substring(0, 1));
        for (int i = 1; i < semPontos.length(); i += 2) {
            sb.append('.').append(semPontos, i, i + 2);
        }
        return sb.toString();
    }

    /** Mesma regra do exportador antigo: 0/1 ativo, 24 PL, 2 passivo, 9 outras, resto resultado. */
    static int naturezaPeloCodigo(String codigo) {
        switch (codigo.charAt(0)) {
            case '0':
            case '1':
                return 1;
            case '2':
                return codigo.startsWith("24") ? 3 : 2;
            case '9':
                return 9;
            default:
                return 4;
        }
    }

    private void gravarRelatorio(ImpLote lote, Relatorio relatorio, boolean importado) {
        StringBuilder sb = new StringBuilder();
        sb.append(importado ? "IMPORTADO em " : "Conferido em ").append(OffsetDateTime.now().withNano(0)).append('\n');
        if (importado) {
            sb.append(relatorio.importadas).append(" contas gravadas, com 12 saldos mensais cada.\n");
        }
        secao(sb, "ERROS (impedem a importação)", relatorio.erros);
        secao(sb, "DIVERGÊNCIAS DE SALDO", relatorio.divergencias);
        secao(sb, "AVISOS", relatorio.avisos);
        if (relatorio.erros.isEmpty() && relatorio.divergencias.isEmpty() && relatorio.avisos.isEmpty()) {
            sb.append("\nNenhum problema encontrado: saldos contínuos e sintéticas batendo com as filhas.\n");
        }
        lote.setMensagem(sb.toString());
        if (importado) {
            lote.setSituacao(SituacaoLote.IMPORTADO);
            lote.setDataImportacao(OffsetDateTime.now());
        }
        dataManager.saveWithoutReload(lote);
    }

    private void secao(StringBuilder sb, String titulo, List<String> linhas) {
        if (linhas.isEmpty()) {
            return;
        }
        sb.append('\n').append(titulo).append(" (").append(linhas.size()).append(")\n");
        for (String linha : linhas) {
            sb.append("  - ").append(linha).append('\n');
        }
    }

    private static BigDecimal zeroSeNulo(BigDecimal valor) {
        return valor == null ? BigDecimal.ZERO : valor;
    }

    static class ContaLida {
        String codigo;
        String nome;
        int grau;
        boolean analitica;
        int codNat;
        String codContaSup;
        String contaReferencial;
        final BigDecimal[] saldoAnterior = zeros();
        final BigDecimal[] debito = zeros();
        final BigDecimal[] credito = zeros();
        final boolean[] temSaldo = new boolean[12];
        BigDecimal saldoTransf = BigDecimal.ZERO;
        int mesesComTransf;

        private static BigDecimal[] zeros() {
            BigDecimal[] v = new BigDecimal[12];
            java.util.Arrays.fill(v, BigDecimal.ZERO);
            return v;
        }
    }

    public static class Relatorio {
        final List<String> erros = new ArrayList<>();
        final List<String> divergencias = new ArrayList<>();
        final List<String> avisos = new ArrayList<>();
        int importadas;

        public List<String> getErros() {
            return erros;
        }

        public List<String> getDivergencias() {
            return divergencias;
        }

        public List<String> getAvisos() {
            return avisos;
        }

        public int getImportadas() {
            return importadas;
        }
    }
}
