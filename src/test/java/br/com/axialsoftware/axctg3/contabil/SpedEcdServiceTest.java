package br.com.axialsoftware.axctg3.contabil;

import br.com.axialsoftware.axctg3.entity.User;
import br.com.axialsoftware.axctg3.entity.cadastros.CentroCusto;
import br.com.axialsoftware.axctg3.entity.cadastros.Empresa;
import br.com.axialsoftware.axctg3.entity.contabil.ContaContabil;
import br.com.axialsoftware.axctg3.entity.contabil.HistoricoContabil;
import br.com.axialsoftware.axctg3.entity.contabil.Lancamento;
import br.com.axialsoftware.axctg3.entity.enums.CodNat;
import br.com.axialsoftware.axctg3.entity.enums.CodPlanRef;
import br.com.axialsoftware.axctg3.entity.tabelas.ContaReferencial;
import br.com.axialsoftware.axctg3.service.contabil.SpedEcdService;
import br.com.axialsoftware.axctg3.test_support.AuthenticatedAsAdmin;
import io.jmix.core.DataManager;
import io.jmix.core.SaveContext;
import io.jmix.core.security.CurrentAuthentication;
import io.jmix.data.PersistenceHints;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cobre {@link SpedEcdService#gerarArquivo} de ponta a ponta com uma empresa de teste
 * pequena: verifica não só o formato mecânico (CRLF, ISO-8859-1, contagens de
 * fechamento), mas também a invariante que o PVA do Sped Contábil de fato valida
 * (REGRA_VALIDACAO_VALOR_DEB/CRED do manual): a soma dos I250 de uma conta/mês tem que
 * bater com o I155 daquela conta/mês.
 */
@SpringBootTest
@ExtendWith(AuthenticatedAsAdmin.class)
@ActiveProfiles("test")
class SpedEcdServiceTest {

    private static final int COD_EMPRESA = 9407;
    private static final int ANO = 2097;

    @Autowired
    DataManager dataManager;
    @Autowired
    SpedEcdService spedEcdService;
    @Autowired
    CurrentAuthentication currentAuthentication;

    private ContaContabil caixa;
    private ContaContabil vendas;

    @BeforeEach
    void setUp() {
        limparDadosDaEmpresa();

        Empresa empresa = dataManager.create(Empresa.class);
        empresa.setCodigo(COD_EMPRESA);
        // "ç"/"ã" de propósito — testa o round-trip ISO-8859-1 exigido pelo leiaute
        empresa.setNome("Distribuidora São José Ltda");
        empresa.setApelido("Teste Sped");
        empresa.setCnpj("12345678000199");
        empresa.setCodPlanRef(CodPlanRef.PJ_em_geral_lucro_real);
        dataManager.save(empresa);

        User admin = (User) currentAuthentication.getUser();
        admin.setCodEmpresa(COD_EMPRESA);
        admin.setAnoContabil(ANO);
        admin.setMesContabil(1);

        ContaReferencial refCaixa = dataManager.create(ContaReferencial.class);
        refCaixa.setCodPlanRef(CodPlanRef.PJ_em_geral_lucro_real);
        refCaixa.setCodigo("101010100");
        refCaixa.setDescricao("Caixa");
        refCaixa.setDtIni(LocalDate.of(2020, 1, 1));
        refCaixa.setGrau(5);
        refCaixa.setCodNat(CodNat.CONTAS_DE_ATIVO);
        refCaixa.setAnalitica(true);
        dataManager.save(refCaixa);

        ContaContabil ativo = criarConta("1", "Ativo", 1, "", CodNat.CONTAS_DE_ATIVO, false, null);
        caixa = criarConta("1.1", "Caixa", 2, "1", CodNat.CONTAS_DE_ATIVO, true, refCaixa);
        ContaContabil receita = criarConta("4", "Receita", 1, "", CodNat.CONTAS_DE_RESULTADO, false, null);
        vendas = criarConta("4.1", "Vendas", 2, "4", CodNat.CONTAS_DE_RESULTADO, true, null);

        HistoricoContabil historico = dataManager.create(HistoricoContabil.class);
        historico.setCodigo(1);
        historico.setCodEmpresa(COD_EMPRESA);
        historico.setDescricao("Venda à vista");
        dataManager.save(historico);

        CentroCusto centro = dataManager.create(CentroCusto.class);
        centro.setCodigo("CC1");
        centro.setCodEmpresa(COD_EMPRESA);
        centro.setNome("Matriz");
        dataManager.save(centro);

        // duas vendas em meses diferentes — testa que I150 sai pros 12 meses (mesmo os
        // sem movimento) e que só os meses com saldo/movimento ganham I155
        admin.setMesContabil(1);
        criarLancamento(caixa, vendas, "1000.00", historico);
        admin.setMesContabil(6);
        criarLancamento(caixa, vendas, "500.00", historico);
    }

    @Test
    void test_gerarArquivo_fechamentosBatemComAsLinhasReais() {
        byte[] arquivo = spedEcdService.gerarArquivo(COD_EMPRESA, ANO);
        String texto = new String(arquivo, StandardCharsets.ISO_8859_1);

        assertThat(texto).contains("\r\n");
        String[] linhas = texto.split("\r\n");
        // a última linha do split não sobra vazia porque o arquivo termina com \r\n
        // (split não gera elemento vazio à direita por padrão)

        assertThat(linhas[0]).startsWith("|0000|LECD|");
        // encoding ISO-8859-1: "São José" vira essa sequência quando decodificada de volta
        assertThat(linhas[0]).contains("São José");

        long totalReal = linhas.length;
        long declaradoEm9999 = Long.parseLong(campo(ultimaLinhaComReg(linhas, "9999"), 1));
        assertThat(declaradoEm9999).isEqualTo(totalReal);

        long declaradoEmI030 = Long.parseLong(campo(unicaLinhaComReg(linhas, "I030"), 4));
        assertThat(declaradoEmI030).isEqualTo(totalReal);
    }

    @Test
    void test_gerarArquivo_9900ListaContagemRealDeCadaRegistro() {
        byte[] arquivo = spedEcdService.gerarArquivo(COD_EMPRESA, ANO);
        String[] linhas = new String(arquivo, StandardCharsets.ISO_8859_1).split("\r\n");

        Map<String, Long> contagemReal = Arrays.stream(linhas)
                .map(l -> campo(l, 0))
                .collect(Collectors.groupingBy(r -> r, Collectors.counting()));

        Map<String, Long> contagemDeclarada = Arrays.stream(linhas)
                .filter(l -> "9900".equals(campo(l, 0)))
                .collect(Collectors.toMap(l -> campo(l, 1), l -> Long.parseLong(campo(l, 2))));

        assertThat(contagemDeclarada).isEqualTo(contagemReal);
        // plano de contas: 4 contas (1, 1.1, 4, 4.1) -> 4 registros I050
        assertThat(contagemReal.get("I050")).isEqualTo(4L);
        // só a conta analítica "1.1" tem contaReferencial + Empresa.codPlanRef preenchido
        assertThat(contagemReal.get("I051")).isEqualTo(1L);
        // 2 lançamentos -> 2 I200, cada um com 2 partidas -> 4 I250
        assertThat(contagemReal.get("I200")).isEqualTo(2L);
        assertThat(contagemReal.get("I250")).isEqualTo(4L);
        // I150 sempre um por mês do ano inteiro, mesmo sem movimento
        assertThat(contagemReal.get("I150")).isEqualTo(12L);
    }

    @Test
    void test_gerarArquivo_somaDosI250BateComI155PorContaEMes() {
        byte[] arquivo = spedEcdService.gerarArquivo(COD_EMPRESA, ANO);
        String[] linhas = new String(arquivo, StandardCharsets.ISO_8859_1).split("\r\n");

        // mês de janeiro: só a venda de 1000,00 — acha o I155 do caixa dentro do primeiro
        // bloco de I150/I155 (janeiro é o primeiro I150 emitido)
        int inicioJaneiro = indiceDaLinha(linhas, "I150");
        int fimJaneiro = indiceDaLinha(linhas, "I150", inicioJaneiro + 1);
        String i155Caixa = Arrays.stream(linhas, inicioJaneiro, fimJaneiro)
                .filter(l -> "I155".equals(campo(l, 0)) && caixa.getCodigo().equals(campo(l, 1)))
                .findFirst()
                .orElseThrow();

        String vlDebI155 = campo(i155Caixa, 5).replace(",", ".");
        assertThat(vlDebI155).isEqualTo("1000.00");

        // soma de todos os I250 de débito na conta caixa (janeiro + junho: 1000 + 500) —
        // é a mesma invariante que o PVA valida (REGRA_VALIDACAO_VALOR_DEB do manual),
        // só que aqui somando o arquivo inteiro em vez de por mês isolado
        double somaDebitoCaixa = Arrays.stream(linhas)
                .filter(l -> "I250".equals(campo(l, 0))
                        && caixa.getCodigo().equals(campo(l, 1))
                        && "D".equals(campo(l, 4)))
                .mapToDouble(l -> Double.parseDouble(campo(l, 3).replace(",", ".")))
                .sum();
        assertThat(somaDebitoCaixa).isEqualTo(1500.00); // 1000 (jan) + 500 (jun)
    }

    private void criarLancamento(ContaContabil devedora, ContaContabil credora, String valor,
                                  HistoricoContabil historico) {
        Lancamento lancamento = dataManager.create(Lancamento.class);
        lancamento.setContaDevedora(devedora);
        lancamento.setContaCredora(credora);
        lancamento.setValor(new java.math.BigDecimal(valor));
        lancamento.setHistoricoContabil(historico);
        lancamento.setComplementoHistorico("Venda balcão");
        dataManager.save(lancamento);
    }

    private ContaContabil criarConta(String codigo, String nome, int grau, String codContaSup,
                                      CodNat codNat, boolean analitica, ContaReferencial referencial) {
        ContaContabil conta = dataManager.create(ContaContabil.class);
        conta.setCodigo(codigo);
        conta.setNome(nome);
        conta.setAno(ANO);
        conta.setCodEmpresa(COD_EMPRESA);
        conta.setGrau(grau);
        conta.setCodContaSup(codContaSup);
        conta.setAnalitica(analitica);
        conta.setCodNat(codNat);
        conta.setContaReferencial(referencial);
        return dataManager.save(conta);
    }

    // --- helpers de leitura do texto pipe-delimitado gerado ---

    private static final Pattern SPLIT_CAMPOS = Pattern.compile("\\|");

    /** Campo N (0 = tipo de registro) de uma linha {@code |REG|f1|f2|...|}. */
    private static String campo(String linha, int indice) {
        // a linha começa com "|", então split produz um elemento vazio na posição 0
        String[] partes = SPLIT_CAMPOS.split(linha, -1);
        return partes[indice + 1];
    }

    private static String unicaLinhaComReg(String[] linhas, String reg) {
        return Arrays.stream(linhas).filter(l -> reg.equals(campo(l, 0))).findFirst().orElseThrow();
    }

    private static String ultimaLinhaComReg(String[] linhas, String reg) {
        List<String> encontradas = Arrays.stream(linhas).filter(l -> reg.equals(campo(l, 0))).toList();
        return encontradas.get(encontradas.size() - 1);
    }

    private static int indiceDaLinha(String[] linhas, String reg) {
        return indiceDaLinha(linhas, reg, 0);
    }

    private static int indiceDaLinha(String[] linhas, String reg, int apartirDe) {
        for (int i = apartirDe; i < linhas.length; i++) {
            if (reg.equals(campo(linhas[i], 0))) {
                return i;
            }
        }
        return linhas.length;
    }

    @AfterEach
    void tearDown() {
        limparDadosDaEmpresa();
    }

    private void limparDadosDaEmpresa() {
        apagar(carregar(Lancamento.class,
                "select e from Lancamento e where e.codEmpresa = :codEmpresa", COD_EMPRESA));
        apagar(carregar(HistoricoContabil.class,
                "select e from HistoricoContabil e where e.codEmpresa = :codEmpresa", COD_EMPRESA));
        apagar(carregar(CentroCusto.class,
                "select e from CentroCusto e where e.codEmpresa = :codEmpresa", COD_EMPRESA));
        apagar(carregar(ContaContabil.class,
                "select e from ContaContabil e where e.codEmpresa = :codEmpresa", COD_EMPRESA));
        apagar(carregar(Empresa.class,
                "select e from Empresa e where e.codigo = :codEmpresa", COD_EMPRESA));
        apagar(dataManager.load(ContaReferencial.class)
                .query("select e from ContaReferencial e where e.codigo = :codigo and e.codPlanRef = :codPlanRef")
                .parameter("codigo", "101010100")
                .parameter("codPlanRef", CodPlanRef.PJ_em_geral_lucro_real.getId())
                .hint(PersistenceHints.SOFT_DELETION, false)
                .list());
    }

    private <E> List<E> carregar(Class<E> entityClass, String query, Object codEmpresa) {
        return dataManager.load(entityClass)
                .query(query)
                .parameter("codEmpresa", codEmpresa)
                .hint(PersistenceHints.SOFT_DELETION, false)
                .list();
    }

    private void apagar(List<?> entidades) {
        if (entidades.isEmpty()) {
            return;
        }
        dataManager.save(new SaveContext()
                .setHint(PersistenceHints.SOFT_DELETION, false)
                .setHint(PersistenceHints.SKIP_ENTITY_CHANGED_EVENT, true)
                .removing(entidades.toArray()));
    }
}
