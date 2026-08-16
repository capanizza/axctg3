package br.com.axialsoftware.axctg3.contabil;

import br.com.axialsoftware.axctg3.entity.User;
import br.com.axialsoftware.axctg3.entity.cadastros.Empresa;
import br.com.axialsoftware.axctg3.entity.contabil.ContaContabil;
import br.com.axialsoftware.axctg3.entity.contabil.Lancamento;
import br.com.axialsoftware.axctg3.entity.contabil.SaldoConta;
import br.com.axialsoftware.axctg3.entity.enums.CodNat;
import br.com.axialsoftware.axctg3.service.contabil.EncerramentoService;
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

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cobre as três funções de {@link EncerramentoService} de ponta a ponta: lança o
 * encerramento de uma conta de resultado, cria o exercício seguinte e transfere os saldos —
 * nessa ordem, porque é a única ordem em que as três fazem sentido (ver AGENTS.md/CLAUDE.md,
 * seção "Accounting posting and balance rollup").
 */
@SpringBootTest
@ExtendWith(AuthenticatedAsAdmin.class)
@ActiveProfiles("test")
class EncerramentoServiceTest {

    private static final int COD_EMPRESA = 9305;
    private static final int ANO = 2098;

    @Autowired
    DataManager dataManager;
    @Autowired
    EncerramentoService encerramentoService;
    @Autowired
    CurrentAuthentication currentAuthentication;

    private ContaContabil caixa;
    private ContaContabil receita;
    private ContaContabil contaEncerramento;

    @BeforeEach
    void setUp() {
        limparDadosDaEmpresa();

        Empresa empresa = dataManager.create(Empresa.class);
        empresa.setCodigo(COD_EMPRESA);
        empresa.setNome("Empresa de teste");
        empresa.setApelido("Teste");
        empresa.setCodContaEnc("9600");
        dataManager.save(empresa);

        User admin = (User) currentAuthentication.getUser();
        admin.setCodEmpresa(COD_EMPRESA);
        admin.setAnoContabil(ANO);
        admin.setMesContabil(12);

        caixa = criarConta("9501", "Caixa", CodNat.CONTAS_DE_ATIVO);
        receita = criarConta("9502", "Receita de vendas", CodNat.CONTAS_DE_RESULTADO);
        contaEncerramento = criarConta("9600", "Lucros/prejuízos acumulados", CodNat.PATRIMONIO_LIQUIDO);

        // simula uma venda à vista: débito em caixa, crédito em receita — deixa a receita com
        // saldo credor (negativo, pela convenção débito soma/crédito subtrai)
        Lancamento venda = dataManager.create(Lancamento.class);
        venda.setContaDevedora(caixa);
        venda.setContaCredora(receita);
        venda.setValor(new BigDecimal("1000.00"));
        dataManager.save(venda);
    }

    @Test
    void test_lancarEncerramentoZeraContaDeResultadoEGuardaSaldoTransf() {
        List<EncerramentoService.ContaEncerramento> itens = encerramentoService.prepararLancamentoEncerramento();

        assertThat(itens).hasSize(1);
        EncerramentoService.ContaEncerramento item = itens.get(0);
        assertThat(item.conta().getId()).isEqualTo(receita.getId());
        assertThat(item.saldo()).isEqualByComparingTo("-1000.00");

        encerramentoService.lancarEncerramentoConta(item, null, contaEncerramento);

        assertThat(saldoAtualDezembro(receita)).isEqualByComparingTo("0.00");
        assertThat(recarregar(receita).getSaldoTransf()).isEqualByComparingTo("-1000.00");
        assertThat(saldoAtualDezembro(contaEncerramento)).isEqualByComparingTo("-1000.00");

        // já sem saldo a encerrar — não reaparece numa segunda rodada
        assertThat(encerramentoService.prepararLancamentoEncerramento()).isEmpty();
    }

    @Test
    void test_criarProximoExercicioECopiarSaldosDeFechamento() {
        // fecha o resultado primeiro, senão a receita "vazaria" resultado pro ano seguinte
        List<EncerramentoService.ContaEncerramento> itens = encerramentoService.prepararLancamentoEncerramento();
        encerramentoService.lancarEncerramentoConta(itens.get(0), null, contaEncerramento);

        List<ContaContabil> aCriar = encerramentoService.prepararCriarProximoExercicio();
        assertThat(aCriar).extracting(ContaContabil::getCodigo)
                .containsExactlyInAnyOrder("9501", "9502", "9600");

        aCriar.forEach(encerramentoService::criarContaProximoExercicio);

        // idempotente: rodar de novo não acha mais nada pra criar
        assertThat(encerramentoService.prepararCriarProximoExercicio()).isEmpty();

        ContaContabil caixaProximoAno = contaNoAno("9501", ANO + 1);
        assertThat(caixaProximoAno.getNome()).isEqualTo("Caixa");
        assertThat(caixaProximoAno.getCodNat()).isEqualTo(CodNat.CONTAS_DE_ATIVO);
        assertThat(caixaProximoAno.getSaldosConta()).hasSize(12);
        assertThat(caixaProximoAno.getSaldosConta().get(0).getSaldoAtual()).isEqualByComparingTo("0.00");

        EncerramentoService.PreparoTransferenciaSaldos preparo = encerramentoService.prepararTransferenciaSaldos();
        assertThat(preparo.contasFaltantes()).isEmpty();
        assertThat(preparo.pendentes()).hasSize(3);

        preparo.pendentes().forEach(encerramentoService::transferirSaldoConta);

        ContaContabil caixaDepoisTransf = contaNoAno("9501", ANO + 1);
        ContaContabil receitaDepoisTransf = contaNoAno("9502", ANO + 1);
        // caixa foi debitado em 1000 na venda — vira saldo de abertura do ano seguinte
        for (SaldoConta saldo : caixaDepoisTransf.getSaldosConta()) {
            assertThat(saldo.getSaldoAtual()).isEqualByComparingTo("1000.00");
        }
        // receita já veio zerada do encerramento — abre o ano seguinte zerada também
        for (SaldoConta saldo : receitaDepoisTransf.getSaldosConta()) {
            assertThat(saldo.getSaldoAtual()).isEqualByComparingTo("0.00");
        }
    }

    @Test
    void test_transferirSaldosExigeQueProximoExercicioJaExista() {
        EncerramentoService.PreparoTransferenciaSaldos preparo = encerramentoService.prepararTransferenciaSaldos();

        assertThat(preparo.pendentes()).isEmpty();
        assertThat(preparo.contasFaltantes()).containsExactlyInAnyOrder("9501", "9502", "9600");
    }

    private BigDecimal saldoAtualDezembro(ContaContabil conta) {
        return recarregar(conta).getSaldosConta().get(11).getSaldoAtual();
    }

    private ContaContabil recarregar(ContaContabil conta) {
        return dataManager.load(ContaContabil.class).id(conta.getId()).one();
    }

    private ContaContabil contaNoAno(String codigo, int ano) {
        return dataManager.load(ContaContabil.class)
                .query("select e from ContaContabil e where e.codigo = :codigo and e.codEmpresa = :codEmpresa and e.ano = :ano")
                .parameter("codigo", codigo)
                .parameter("codEmpresa", COD_EMPRESA)
                .parameter("ano", ano)
                .one();
    }

    private ContaContabil criarConta(String codigo, String nome, CodNat codNat) {
        ContaContabil conta = dataManager.create(ContaContabil.class);
        conta.setCodigo(codigo);
        conta.setNome(nome);
        conta.setAno(ANO);
        conta.setCodEmpresa(COD_EMPRESA);
        conta.setGrau(1);
        conta.setCodContaSup(codigo);
        conta.setAnalitica(true);
        conta.setCodNat(codNat);
        return dataManager.save(conta);
    }

    @AfterEach
    void tearDown() {
        limparDadosDaEmpresa();
    }

    private void limparDadosDaEmpresa() {
        apagar(carregar(Lancamento.class,
                "select e from Lancamento e where e.codEmpresa = :codEmpresa", COD_EMPRESA));
        apagar(carregar(ContaContabil.class,
                "select e from ContaContabil e where e.codEmpresa = :codEmpresa", COD_EMPRESA));
        apagar(carregar(Empresa.class,
                "select e from Empresa e where e.codigo = :codEmpresa", COD_EMPRESA));
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
