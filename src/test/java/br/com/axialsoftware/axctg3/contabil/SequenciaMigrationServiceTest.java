package br.com.axialsoftware.axctg3.contabil;

import br.com.axialsoftware.axctg3.entity.User;
import br.com.axialsoftware.axctg3.entity.cadastros.Parceiro;
import br.com.axialsoftware.axctg3.entity.contabil.ContaContabil;
import br.com.axialsoftware.axctg3.entity.contabil.Lancamento;
import br.com.axialsoftware.axctg3.entity.enums.CodNat;
import br.com.axialsoftware.axctg3.entity.financeiro.HistoricoFinanceiro;
import br.com.axialsoftware.axctg3.entity.financeiro.TituloPagar;
import br.com.axialsoftware.axctg3.service.SequenciaMigrationService;
import br.com.axialsoftware.axctg3.test_support.AuthenticatedAsAdmin;
import io.jmix.core.DataManager;
import io.jmix.core.SaveContext;
import io.jmix.core.security.CurrentAuthentication;
import io.jmix.data.PersistenceHints;
import io.jmix.data.Sequence;
import io.jmix.data.Sequences;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code SequenciaMigrationService} — cobre a migração pontual (2026-08-24) que semeia
 * as sequences novas (nome com codEmpresa) a partir do {@code max(numero)} já gravado,
 * pra não colidir com dado existente na primeira gravação depois da correção. Usa
 * {@code gravarLancamento}-style (numero setado direto, sem passar pelo listener) pra
 * simular dado que já existia antes da migração — mesmo padrão de
 * {@code LancamentoService.gravarLancamento} usado no import de lançamentos legados.
 */
@SpringBootTest
@ExtendWith(AuthenticatedAsAdmin.class)
@ActiveProfiles("test")
class SequenciaMigrationServiceTest {

    private static final int COD_EMPRESA = 9103;
    private static final int ANO = 2097;
    private static final int MES = 6;

    @Autowired
    DataManager dataManager;
    @Autowired
    Sequences sequences;
    @Autowired
    SequenciaMigrationService sequenciaMigrationService;
    @Autowired
    CurrentAuthentication currentAuthentication;

    @BeforeEach
    void setUp() {
        // LancamentoEventListener carimba ano/mes a partir da sessão mesmo quando numero
        // já vem setado (só a geração via sequence é pulada) — sem isso, NPE.
        User admin = (User) currentAuthentication.getUser();
        admin.setCodEmpresa(COD_EMPRESA);
        admin.setAnoContabil(ANO);
        admin.setMesContabil(MES);

        limpar();
    }

    @AfterEach
    void tearDown() {
        limpar();
    }

    @Test
    void migrarLancamentoSemeiaSequenceComOMaxJaGravado() {
        ContaContabil devedora = criarConta("9001", "Caixa de teste");
        ContaContabil credora = criarConta("9002", "Receita de teste");

        // simula dado que já existia antes da correção — numero setado direto, sem
        // passar pelo listener (mesmo caminho de um import de lançamentos legados)
        gravarLancamentoComNumero(devedora, credora, 3);
        gravarLancamentoComNumero(devedora, credora, 7);
        gravarLancamentoComNumero(devedora, credora, 5);

        sequenciaMigrationService.migrarLancamento();

        String nome = "lancamento_seq_" + COD_EMPRESA + "_" + String.format("%4d%02d", ANO, MES);
        long proximo = sequences.createNextValue(Sequence.withName(nome));

        // faixa, não valor exato: setCurrentValue depende do store (setval vs alter
        // sequence restart with) — mesma diferença de ImportarLancamentosTest.
        assertThat(proximo).isBetween(7L, 8L);
    }

    @Test
    void migrarTituloPagarSemeiaSequenceComOMaxJaGravado() {
        // TituloPagarEventListener cria um ItemPagar de emissão no CREATE, buscando um
        // HistoricoFinanceiro código 1 da empresa — sem ele o save nem completa.
        HistoricoFinanceiro historico = dataManager.create(HistoricoFinanceiro.class);
        historico.setCodigo(1);
        historico.setCodEmpresa(COD_EMPRESA);
        historico.setNome("Histórico de teste");
        dataManager.save(historico);

        Parceiro parceiro = criarParceiro();
        gravarTituloPagarComNumero(parceiro, 10);
        gravarTituloPagarComNumero(parceiro, 42);

        sequenciaMigrationService.migrarTituloPagar();

        long proximo = sequences.createNextValue(Sequence.withName("titulo_pagar_seq_" + COD_EMPRESA));
        assertThat(proximo).isBetween(42L, 43L);
    }

    private void gravarLancamentoComNumero(ContaContabil devedora, ContaContabil credora, int numero) {
        Lancamento lancamento = dataManager.create(Lancamento.class);
        lancamento.setNumero(numero);
        lancamento.setAno(ANO);
        lancamento.setMes(MES);
        lancamento.setDia(1);
        lancamento.setDataLancamento(LocalDate.of(ANO, MES, 1));
        lancamento.setCodEmpresa(COD_EMPRESA);
        lancamento.setContaDevedora(devedora);
        lancamento.setContaCredora(credora);
        lancamento.setValor(BigDecimal.TEN);
        dataManager.save(lancamento);
    }

    private void gravarTituloPagarComNumero(Parceiro parceiro, int numero) {
        TituloPagar tituloPagar = dataManager.create(TituloPagar.class);
        tituloPagar.setNumero(numero);
        tituloPagar.setCodEmpresa(COD_EMPRESA);
        tituloPagar.setParceiro(parceiro);
        tituloPagar.setValor(BigDecimal.TEN);
        tituloPagar.setDataEmissao(LocalDate.of(ANO, MES, 1));
        tituloPagar.setDataVencimento(LocalDate.of(ANO, MES, 10));
        dataManager.save(tituloPagar);
    }

    private ContaContabil criarConta(String codigo, String nome) {
        ContaContabil conta = dataManager.create(ContaContabil.class);
        conta.setCodigo(codigo);
        conta.setNome(nome);
        conta.setAno(ANO);
        conta.setCodEmpresa(COD_EMPRESA);
        conta.setGrau(1);
        conta.setCodContaSup(codigo);
        conta.setAnalitica(true);
        conta.setCodNat(CodNat.CONTAS_DE_ATIVO);
        return dataManager.save(conta);
    }

    private Parceiro criarParceiro() {
        Parceiro parceiro = dataManager.create(Parceiro.class);
        parceiro.setCodigo(1L);
        parceiro.setCodEmpresa(COD_EMPRESA);
        parceiro.setNome("Fornecedor de teste");
        parceiro.setApelido("Fornecedor de teste");
        parceiro.setCnpj("00000000000000");
        return dataManager.save(parceiro);
    }

    private void limpar() {
        apagar(dataManager.load(Lancamento.class)
                .query("select e from Lancamento e where e.codEmpresa = :codEmpresa")
                .parameter("codEmpresa", COD_EMPRESA)
                .hint(PersistenceHints.SOFT_DELETION, false)
                .list());
        // ItemPagar filho de TituloPagar tem @OnDelete(CASCADE), então apagar TituloPagar
        // primeiro já leva os itens junto — mesma ordem de outros testes financeiros.
        apagar(dataManager.load(TituloPagar.class)
                .query("select e from TituloPagar e where e.codEmpresa = :codEmpresa")
                .parameter("codEmpresa", COD_EMPRESA)
                .hint(PersistenceHints.SOFT_DELETION, false)
                .list());
        apagar(dataManager.load(HistoricoFinanceiro.class)
                .query("select e from HistoricoFinanceiro e where e.codEmpresa = :codEmpresa")
                .parameter("codEmpresa", COD_EMPRESA)
                .hint(PersistenceHints.SOFT_DELETION, false)
                .list());
        apagar(dataManager.load(ContaContabil.class)
                .query("select e from ContaContabil e where e.codEmpresa = :codEmpresa")
                .parameter("codEmpresa", COD_EMPRESA)
                .hint(PersistenceHints.SOFT_DELETION, false)
                .list());
        apagar(dataManager.load(Parceiro.class)
                .query("select e from Parceiro e where e.codEmpresa = :codEmpresa")
                .parameter("codEmpresa", COD_EMPRESA)
                .hint(PersistenceHints.SOFT_DELETION, false)
                .list());
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
