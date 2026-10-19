package br.com.axialsoftware.axctg3.importacao;

import br.com.axialsoftware.axctg3.entity.User;
import br.com.axialsoftware.axctg3.entity.cadastros.Empresa;
import br.com.axialsoftware.axctg3.entity.contabil.HistoricoContabil;
import br.com.axialsoftware.axctg3.entity.enums.SituacaoLote;
import br.com.axialsoftware.axctg3.entity.enums.TipoLote;
import br.com.axialsoftware.axctg3.entity.importacao.ImpHistoricoContabil;
import br.com.axialsoftware.axctg3.entity.importacao.ImpLote;
import br.com.axialsoftware.axctg3.service.importacao.ImportacaoHistoricosService;
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
 * Importação de um lote de históricos contábeis do legado: na empresa do lote (não na da
 * sessão), cria os novos, troca a descrição dos que mudaram, não regrava os iguais e deixa
 * intocado o que só existe no axctg3.
 */
@SpringBootTest
@ExtendWith(AuthenticatedAsAdmin.class)
@ActiveProfiles("test")
class ImportacaoHistoricosServiceTest {

    private static final int COD_EMPRESA = 9309;

    @Autowired
    DataManager dataManager;
    @Autowired
    ImportacaoHistoricosService service;
    @Autowired
    CurrentAuthentication currentAuthentication;

    @BeforeEach
    void setUp() {
        limpar();
        Empresa empresa = dataManager.create(Empresa.class);
        empresa.setCodigo(COD_EMPRESA);
        empresa.setNome("Empresa de teste da importação de históricos");
        empresa.setApelido("Imp hist");
        dataManager.save(empresa);
        historico(1, "VENDA A VISTA");
        historico(2, "COMPRA A PRAZ");
        historico(50, "CRIADO NO AXCTG3");
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
        ImpLote lote = criarLote(new Object[][]{
                {0, "."},
                {1, "VENDA A VISTA"},
                {2, "COMPRA A PRAZO"},
                {3, "PAGAMENTO DE FORNECEDOR REFERENTE A DUPLICATA DE COMPRA"}});

        ImportacaoPlanoContasService.Relatorio conferencia = service.conferir(lote.getId());
        assertThat(conferencia.getErros()).isEmpty();
        assertThat(descricoes()).containsEntry(2, "COMPRA A PRAZ"); // conferir não grava

        ImportacaoPlanoContasService.Relatorio relatorio = service.importar(lote.getId());

        assertThat(relatorio.getErros()).isEmpty();
        assertThat(relatorio.getImportadas()).isEqualTo(3); // 0 e 3 criados, 2 alterado
        assertThat(relatorio.getAvisos())
                .anyMatch(a -> a.startsWith("Histórico 3: descrição cortada em 50"))
                .anyMatch(a -> a.startsWith("Histórico 2: \"COMPRA A PRAZ\" passa a \"COMPRA A PRAZO\""))
                .anyMatch(a -> a.startsWith("Histórico 50 (\"CRIADO NO AXCTG3\") existe no axctg3"));

        Map<Integer, String> descricoes = descricoes();
        assertThat(descricoes).hasSize(5)
                .containsEntry(0, ".")
                .containsEntry(1, "VENDA A VISTA")
                .containsEntry(2, "COMPRA A PRAZO")
                .containsEntry(3, "PAGAMENTO DE FORNECEDOR REFERENTE A DUPLICATA DE C")
                .containsEntry(50, "CRIADO NO AXCTG3");
        assertThat(dataManager.load(HistoricoContabil.class)
                .query("select e from HistoricoContabil e where e.codEmpresa = :cod")
                .parameter("cod", COD_EMPRESA + 1)
                .list()).isEmpty();

        ImpLote recarregado = dataManager.load(ImpLote.class).id(lote.getId()).one();
        assertThat(recarregado.getSituacao()).isEqualTo(SituacaoLote.IMPORTADO);
        assertThat(recarregado.getMensagem()).contains("2 criados, 1 alterados, 1 iguais");
        assertThat(service.importar(lote.getId()).getErros()).anyMatch(e -> e.contains("já foi importado"));
    }

    @Test
    void test_codigoRepetidoOuSemDescricaoImpedeAImportacao() {
        ImpLote lote = criarLote(new Object[][]{
                {4, "PRIMEIRO"},
                {4, "REPETIDO"},
                {5, "  "}});

        ImportacaoPlanoContasService.Relatorio relatorio = service.importar(lote.getId());

        assertThat(relatorio.getErros())
                .anyMatch(e -> e.equals("Histórico 4 repetido no lote."))
                .anyMatch(e -> e.equals("Histórico 5 sem descrição."));
        assertThat(descricoes()).doesNotContainKeys(4, 5);
        assertThat(dataManager.load(ImpLote.class).id(lote.getId()).one().getSituacao())
                .isEqualTo(SituacaoLote.PRONTO);
    }

    private void historico(int codigo, String descricao) {
        HistoricoContabil h = dataManager.create(HistoricoContabil.class);
        h.setCodigo(codigo);
        h.setCodEmpresa(COD_EMPRESA);
        h.setDescricao(descricao);
        dataManager.save(h);
    }

    private ImpLote criarLote(Object[][] linhas) {
        ImpLote lote = dataManager.create(ImpLote.class);
        lote.setTipo(TipoLote.HISTORICOS);
        lote.setSituacao(SituacaoLote.PRONTO);
        lote.setCodEmpresa(COD_EMPRESA);
        lote.setCodEmpresaLegado(4);
        lote = dataManager.save(lote);
        SaveContext ctx = new SaveContext();
        for (Object[] l : linhas) {
            ImpHistoricoContabil h = dataManager.create(ImpHistoricoContabil.class);
            h.setLote(lote);
            h.setCodigo((Integer) l[0]);
            h.setDescricao((String) l[1]);
            ctx.saving(h);
        }
        dataManager.save(ctx);
        return lote;
    }

    private Map<Integer, String> descricoes() {
        return dataManager.load(HistoricoContabil.class)
                .query("select e from HistoricoContabil e where e.codEmpresa = :cod")
                .parameter("cod", COD_EMPRESA)
                .list()
                .stream()
                .collect(Collectors.toMap(HistoricoContabil::getCodigo, HistoricoContabil::getDescricao));
    }

    private void limpar() {
        apagar(carregar(HistoricoContabil.class, "select e from HistoricoContabil e where e.codEmpresa = :cod"));
        apagar(carregar(ImpHistoricoContabil.class, "select e from ImpHistoricoContabil e where e.lote.codEmpresa = :cod"));
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
                .removing(entidades.toArray()));
    }
}
