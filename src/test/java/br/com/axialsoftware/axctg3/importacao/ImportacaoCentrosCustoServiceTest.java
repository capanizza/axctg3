package br.com.axialsoftware.axctg3.importacao;

import br.com.axialsoftware.axctg3.entity.User;
import br.com.axialsoftware.axctg3.entity.cadastros.CentroCusto;
import br.com.axialsoftware.axctg3.entity.cadastros.Empresa;
import br.com.axialsoftware.axctg3.entity.enums.SituacaoLote;
import br.com.axialsoftware.axctg3.entity.enums.TipoLote;
import br.com.axialsoftware.axctg3.entity.importacao.ImpCentroCusto;
import br.com.axialsoftware.axctg3.entity.importacao.ImpLote;
import br.com.axialsoftware.axctg3.service.importacao.ImportacaoCentrosCustoService;
import br.com.axialsoftware.axctg3.service.importacao.ImportacaoPlanoContasService;
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

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Importação de um lote de centros de custo do legado: na empresa do lote (não na da sessão),
 * código texto, cria os novos, troca o nome dos que mudaram e deixa intocado o que só existe
 * no axctg3.
 */
@SpringBootTest
@ExtendWith(AuthenticatedAsAdmin.class)
@ActiveProfiles("test")
class ImportacaoCentrosCustoServiceTest {

    private static final int COD_EMPRESA = 9319;

    @Autowired
    DataManager dataManager;
    @Autowired
    ImportacaoCentrosCustoService service;
    @Autowired
    CurrentAuthentication currentAuthentication;

    @BeforeEach
    void setUp() {
        limpar();
        Empresa empresa = dataManager.create(Empresa.class);
        empresa.setCodigo(COD_EMPRESA);
        empresa.setNome("Teste da importação de centros de custo");
        empresa.setApelido("Imp centros");
        dataManager.save(empresa);
        centro("1.01", "ADMINISTRATIV");
        centro("9.99", "CRIADO NO AXCTG3");
        // a sessão aponta para outra empresa: a importação não pode usá-la
        User admin = (User) currentAuthentication.getUser();
        admin.setCodEmpresa(COD_EMPRESA + 1);
    }

    @AfterEach
    void tearDown() {
        limpar();
    }

    @Test
    void test_criaAlteraENaoMexeNoQueSoExisteNoAxctg3() {
        ImpLote lote = criarLote(new String[][]{
                {"1.01", "ADMINISTRATIVO"},
                {"1.02", "PRODUCAO"}});

        assertThat(service.conferir(lote.getId()).getErros()).isEmpty();
        assertThat(nomes()).containsEntry("1.01", "ADMINISTRATIV"); // conferir não grava

        ImportacaoPlanoContasService.Relatorio relatorio = service.importar(lote.getId());

        assertThat(relatorio.getErros()).isEmpty();
        assertThat(relatorio.getImportadas()).isEqualTo(2);
        assertThat(relatorio.getAvisos())
                .anyMatch(a -> a.equals("Centro de custo 1.01: nome \"ADMINISTRATIV\" passa a \"ADMINISTRATIVO\"."))
                .anyMatch(a -> a.startsWith("Centro de custo 9.99 (\"CRIADO NO AXCTG3\") existe no axctg3"));
        assertThat(nomes()).hasSize(3)
                .containsEntry("1.01", "ADMINISTRATIVO")
                .containsEntry("1.02", "PRODUCAO")
                .containsEntry("9.99", "CRIADO NO AXCTG3");
        assertThat(dataManager.load(CentroCusto.class)
                .query("select e from CentroCusto e where e.codEmpresa = :cod")
                .parameter("cod", COD_EMPRESA + 1)
                .list()).isEmpty();
        assertThat(dataManager.load(ImpLote.class).id(lote.getId()).one().getSituacao())
                .isEqualTo(SituacaoLote.IMPORTADO);
    }

    @Test
    void test_codigoRepetidoOuSemNomeImpedeAImportacao() {
        ImpLote lote = criarLote(new String[][]{
                {"2.01", "PRIMEIRO"},
                {"2.01", "REPETIDO"},
                {"2.02", " "}});

        ImportacaoPlanoContasService.Relatorio relatorio = service.importar(lote.getId());

        assertThat(relatorio.getErros())
                .anyMatch(e -> e.equals("Centro de custo 2.01 repetido no lote."))
                .anyMatch(e -> e.equals("Centro de custo 2.02 sem nome."));
        assertThat(nomes()).doesNotContainKeys("2.01", "2.02");
    }

    private void centro(String codigo, String nome) {
        CentroCusto c = dataManager.create(CentroCusto.class);
        c.setCodigo(codigo);
        c.setCodEmpresa(COD_EMPRESA);
        c.setNome(nome);
        dataManager.save(c);
    }

    private ImpLote criarLote(String[][] linhas) {
        ImpLote lote = dataManager.create(ImpLote.class);
        lote.setTipo(TipoLote.CENTROS_CUSTO);
        lote.setSituacao(SituacaoLote.PRONTO);
        lote.setCodEmpresa(COD_EMPRESA);
        lote.setCodEmpresaLegado(2);
        lote = dataManager.save(lote);
        SaveContext ctx = new SaveContext();
        for (String[] l : linhas) {
            ImpCentroCusto c = dataManager.create(ImpCentroCusto.class);
            c.setLote(lote);
            c.setCodigo(l[0]);
            c.setNome(l[1]);
            ctx.saving(c);
        }
        dataManager.save(ctx);
        return lote;
    }

    private Map<String, String> nomes() {
        return dataManager.load(CentroCusto.class)
                .query("select e from CentroCusto e where e.codEmpresa = :cod")
                .parameter("cod", COD_EMPRESA)
                .list()
                .stream()
                .collect(Collectors.toMap(CentroCusto::getCodigo, CentroCusto::getNome));
    }

    private void limpar() {
        apagar(carregar(CentroCusto.class, "select e from CentroCusto e where e.codEmpresa = :cod"));
        apagar(carregar(ImpCentroCusto.class, "select e from ImpCentroCusto e where e.lote.codEmpresa = :cod"));
        apagar(carregar(ImpLote.class, "select e from ImpLote e where e.codEmpresa = :cod"));
        apagar(carregar(Empresa.class, "select e from Empresa e where e.codigo = :cod"));
    }

    private <T> List<T> carregar(Class<T> classe, String jpql) {
        return dataManager.load(classe)
                .query(jpql)
                .parameter("cod", COD_EMPRESA)
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
