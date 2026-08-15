package br.com.axialsoftware.axctg3.contabil;

import br.com.axialsoftware.axctg3.entity.User;
import br.com.axialsoftware.axctg3.entity.cadastros.Empresa;
import br.com.axialsoftware.axctg3.entity.contabil.ContaContabil;
import br.com.axialsoftware.axctg3.entity.contabil.Lancamento;
import br.com.axialsoftware.axctg3.entity.enums.CodNat;
import br.com.axialsoftware.axctg3.entity.enums.CodPlanRef;
import br.com.axialsoftware.axctg3.entity.tabelas.ContaReferencial;
import br.com.axialsoftware.axctg3.service.contabil.ContaContabilService;
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

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cobre {@code ContaContabilService.verificarContas()} — relatório de verificação
 * portado do axctg-flow (ver docs/MIGRACAO.md). {@code emitirRelatorio()} engole qualquer
 * exceção da emissão do PDF (log.info), e a lista de {@code VerificacaoDto} montada é
 * privada ao serviço — não dá pra inspecionar o resultado por fora, então o teste (mesmo
 * padrão de {@code DepreciacaoServiceTest.test_listarDepreciacoesNaoLancaExcecao}) garante
 * só que a query/fetchPlan de {@code saldosConta} e a agregação de
 * {@code Lancamento.contaDevedora}/{@code contaCredora} não quebram em runtime.
 */
@SpringBootTest
@ExtendWith(AuthenticatedAsAdmin.class)
@ActiveProfiles("test")
class ContaContabilServiceTest {

    private static final int COD_EMPRESA = 9304;
    private static final int ANO = 2097;

    @Autowired
    DataManager dataManager;
    @Autowired
    ContaContabilService contaContabilService;
    @Autowired
    CurrentAuthentication currentAuthentication;

    @BeforeEach
    void setUp() {
        User admin = (User) currentAuthentication.getUser();
        admin.setCodEmpresa(COD_EMPRESA);
        admin.setAnoContabil(ANO);
        admin.setMesContabil(3);

        limparDadosDaEmpresa();

        // UtilGeralService.getNomeEmpresa()/getLogoEmpresa() fazem .one() em Empresa pelo
        // codEmpresa da sessão — sem essa linha dá NoResultException antes do relatório rodar.
        Empresa empresa = dataManager.create(Empresa.class);
        empresa.setCodigo(COD_EMPRESA);
        empresa.setNome("Empresa de teste");
        empresa.setApelido("Teste");
        dataManager.save(empresa);
    }

    @Test
    void test_verificarContasNaoLancaExcecao() {
        // ContaContabilEventListener seeda os 12 SaldoConta no create — cobre o caminho
        // "12 saldos ok"; a conta analítica sem lançamento no período cobre o caminho de
        // divergência saldo x movimento (saldo zerado vs. soma de Lancamento também zero,
        // portanto sem divergência real, mas a query de agregação é exercida).
        criarConta("9501", "Caixa", true);
        criarConta("9502", "Bancos", false);

        contaContabilService.verificarContas();
    }

    @Test
    void test_associarContaReferencialEmGrupoAtualizaSoAsContasAnaliticasNoIntervalo() {
        criarConta("9500", "Grupo caixa e bancos", false);
        ContaContabil dentro1 = criarConta("9501", "Caixa", true);
        ContaContabil dentro2 = criarConta("9502", "Bancos", true);
        ContaContabil fora = criarConta("9503", "Aplicações", true);

        ContaReferencial contaReferencial = dataManager.create(ContaReferencial.class);
        contaReferencial.setCodigo("1.01.01");
        contaReferencial.setDescricao("Caixa e equivalentes");
        contaReferencial.setDtIni(LocalDate.of(2015, 1, 1));
        contaReferencial.setGrau(3);
        contaReferencial.setAnalitica(true);
        contaReferencial.setCodNat(CodNat.CONTAS_DE_ATIVO);
        contaReferencial.setCodPlanRef(CodPlanRef.PJ_em_geral_lucro_real);
        contaReferencial = dataManager.save(contaReferencial);

        int atualizadas = contaContabilService.associarContaReferencialEmGrupo("9501", "9502", contaReferencial);

        assertThat(atualizadas).isEqualTo(2);
        assertThat(recarregar(dentro1).getContaReferencial()).isEqualTo(contaReferencial);
        assertThat(recarregar(dentro2).getContaReferencial()).isEqualTo(contaReferencial);
        assertThat(recarregar(fora).getContaReferencial()).isNull();
    }

    @Test
    void test_associarContaReferencialEmGrupoSemContasNoIntervaloRetornaZero() {
        criarConta("9501", "Caixa", true);

        ContaReferencial contaReferencial = dataManager.create(ContaReferencial.class);
        contaReferencial.setCodigo("1.01.01");
        contaReferencial.setDescricao("Caixa e equivalentes");
        contaReferencial.setDtIni(LocalDate.of(2015, 1, 1));
        contaReferencial.setGrau(3);
        contaReferencial.setAnalitica(true);
        contaReferencial.setCodNat(CodNat.CONTAS_DE_ATIVO);
        contaReferencial.setCodPlanRef(CodPlanRef.PJ_em_geral_lucro_real);
        contaReferencial = dataManager.save(contaReferencial);

        int atualizadas = contaContabilService.associarContaReferencialEmGrupo("9601", "9602", contaReferencial);

        assertThat(atualizadas).isEqualTo(0);
    }

    private ContaContabil recarregar(ContaContabil conta) {
        return dataManager.load(ContaContabil.class).id(conta.getId()).one();
    }

    private ContaContabil criarConta(String codigo, String nome, boolean analitica) {
        ContaContabil conta = dataManager.create(ContaContabil.class);
        conta.setCodigo(codigo);
        conta.setNome(nome);
        conta.setAno(ANO);
        conta.setCodEmpresa(COD_EMPRESA);
        conta.setGrau(1);
        conta.setCodContaSup(codigo);
        conta.setAnalitica(analitica);
        conta.setCodNat(CodNat.CONTAS_DE_ATIVO);
        return dataManager.save(conta);
    }

    @AfterEach
    void tearDown() {
        limparDadosDaEmpresa();
    }

    private void limparDadosDaEmpresa() {
        apagar(carregar(Lancamento.class,
                "select e from Lancamento e where e.codEmpresa = :codEmpresa", "codEmpresa", COD_EMPRESA));
        apagar(carregar(ContaContabil.class,
                "select e from ContaContabil e where e.codEmpresa = :codEmpresa", "codEmpresa", COD_EMPRESA));
        apagar(carregar(Empresa.class,
                "select e from Empresa e where e.codigo = :codEmpresa", "codEmpresa", COD_EMPRESA));
        apagar(carregar(ContaReferencial.class,
                "select e from ContaReferencial e where e.codigo = :codigo", "codigo", "1.01.01"));
    }

    private <E> List<E> carregar(Class<E> entityClass, String query, String paramName, Object paramValue) {
        return dataManager.load(entityClass)
                .query(query)
                .parameter(paramName, paramValue)
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
