package br.com.axialsoftware.axctg3.fiscal;

import br.com.axialsoftware.axctg3.entity.fiscal.Produto;
import br.com.axialsoftware.axctg3.entity.tabelas.ClassTrib;
import br.com.axialsoftware.axctg3.entity.tabelas.ClassificacaoFiscal;
import br.com.axialsoftware.axctg3.service.tabelas.ClassificacaoFiscalImportService;
import br.com.axialsoftware.axctg3.test_support.AuthenticatedAsAdmin;
import io.jmix.core.DataManager;
import io.jmix.core.SaveContext;
import io.jmix.data.PersistenceHints;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Cobre {@link ClassificacaoFiscalImportService} com um JSON no formato do Portal Único
 * Siscomex, num capítulo fictício (98) para não colidir com dados de outros testes:
 * descrição montada com os níveis acima, upsert pelo NCM, remoção do que saiu da tabela e
 * dos repetidos, e o que um Produto usa ficando intocado.
 */
@SpringBootTest
@ExtendWith(AuthenticatedAsAdmin.class)
@ActiveProfiles("test")
class ClassificacaoFiscalImportServiceTest {

    private static final int COD_EMPRESA = 9501;
    private static final int CLASS_TRIB_CODIGO_TESTE = 9990009;

    private static final String JSON = """
            {"Data_Ultima_Atualizacao_NCM":"Vigente em 01/01/2099","Ato":"Teste","Nomenclaturas":[
             {"Codigo":"98","Descricao":"Capítulo de teste."},
             {"Codigo":"98.01","Descricao":"Farelos <i>de teste</i>."},
             {"Codigo":"9801.1","Descricao":"- De trigo:"},
             {"Codigo":"9801.10.10","Descricao":"-- Farelo"},
             {"Codigo":"9801.10.90","Descricao":"-- Outros"},
             {"Codigo":"9801.20.00","Descricao":"- De arroz"}
            ]}
            """;

    @Autowired
    DataManager dataManager;

    @Autowired
    ClassificacaoFiscalImportService importService;

    @AfterEach
    void tearDown() {
        List<Produto> produtos = dataManager.load(Produto.class)
                .query("select e from Produto e where e.codEmpresa = :codEmpresa")
                .parameter("codEmpresa", COD_EMPRESA)
                .hint(PersistenceHints.SOFT_DELETION, false)
                .list();
        if (!produtos.isEmpty()) {
            dataManager.save(new SaveContext()
                    .setHint(PersistenceHints.SOFT_DELETION, false)
                    .removing(produtos.toArray()));
        }
        dataManager.load(ClassificacaoFiscal.class)
                .query("select e from ClassificacaoFiscal e where e.codNcm like '98%' "
                        + "or e.codigo between 990001 and 990009")
                .list()
                .forEach(dataManager::remove);
        dataManager.load(ClassTrib.class)
                .query("select e from ClassTrib e where e.codigo = :codigo")
                .parameter("codigo", CLASS_TRIB_CODIGO_TESTE)
                .list()
                .forEach(dataManager::remove);
    }

    @Test
    void importa_monta_descricao_e_trata_repetidos_obsoletos_e_em_uso() {
        ClassificacaoFiscal repetidoLivre = criar(990001, "98011010", "velho livre");
        ClassificacaoFiscal repetidoEmUso = criar(990002, "98011010", "velho em uso");
        ClassificacaoFiscal obsoletoLivre = criar(990003, "98999999", "extinto");
        ClassificacaoFiscal obsoletoEmUso = criar(990004, "98888888", "extinto em uso");
        ClassificacaoFiscal semMercadoria = criar(990005, "00000000", "FRETES");
        ClassTrib classTrib = criarClassTrib();
        criarProduto(1, repetidoEmUso, classTrib);
        criarProduto(2, obsoletoEmUso, classTrib);

        ClassificacaoFiscalImportService.ImportResult r =
                importService.importar(JSON.getBytes(StandardCharsets.UTF_8));

        assertThat(r.vigencia()).isEqualTo("Vigente em 01/01/2099");
        assertThat(r.criados()).isEqualTo(2);
        assertThat(r.atualizados()).isEqualTo(1);
        assertThat(r.removidos()).isGreaterThanOrEqualTo(2);
        assertThat(r.mantidosEmUso()).contains("98888888");

        // o repetido em uso foi o escolhido: mesmo id, código = NCM, descrição montada
        ClassificacaoFiscal farelo = porNcm("98011010").orElseThrow();
        assertThat(farelo.getId()).isEqualTo(repetidoEmUso.getId());
        assertThat(farelo.getCodigo()).isEqualTo(98011010);
        assertThat(farelo.getDescricao()).isEqualTo("Farelos de teste. > De trigo > Farelo");
        assertThat(porNcm("98011090").orElseThrow().getDescricao())
                .isEqualTo("Farelos de teste. > De trigo > Outros");
        assertThat(porNcm("98012000").orElseThrow().getDescricao())
                .isEqualTo("Farelos de teste. > De arroz");

        assertThat(existe(repetidoLivre)).isFalse();
        assertThat(existe(obsoletoLivre)).isFalse();
        assertThat(existe(obsoletoEmUso)).isTrue();
        assertThat(existe(semMercadoria)).isTrue();

        // reimportar o mesmo arquivo não muda nada
        ClassificacaoFiscalImportService.ImportResult r2 =
                importService.importar(JSON.getBytes(StandardCharsets.UTF_8));
        assertThat(r2.criados()).isZero();
        assertThat(r2.atualizados()).isEqualTo(3);
        assertThat(r2.removidos()).isZero();
    }

    @Test
    void json_fora_do_formato_e_recusado() {
        assertThatThrownBy(() -> importService.importar("[]".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Nomenclaturas");
        assertThatThrownBy(() -> importService.importar("não é json".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private ClassificacaoFiscal criar(int codigo, String ncm, String descricao) {
        ClassificacaoFiscal cf = dataManager.create(ClassificacaoFiscal.class);
        cf.setCodigo(codigo);
        cf.setCodNcm(ncm);
        cf.setDescricao(descricao);
        return dataManager.save(cf);
    }

    private ClassTrib criarClassTrib() {
        ClassTrib classTrib = dataManager.create(ClassTrib.class);
        classTrib.setCodigo(CLASS_TRIB_CODIGO_TESTE);
        classTrib.setCst(1);
        classTrib.setDescricao("ClassTrib de teste");
        classTrib.setTipoAliquota("Padrão");
        classTrib.setNomenclatura("Teste");
        classTrib.setDescricaoTratamentoTributario("Teste");
        return dataManager.save(classTrib);
    }

    private void criarProduto(int codigo, ClassificacaoFiscal classificacao, ClassTrib classTrib) {
        Produto produto = dataManager.create(Produto.class);
        produto.setCodigo(codigo);
        produto.setCodEmpresa(COD_EMPRESA);
        produto.setDescricao("Produto de teste " + codigo);
        produto.setApelido("Teste" + codigo);
        produto.setClassTrib(classTrib);
        produto.setClassificacaoFiscal(classificacao);
        dataManager.save(produto);
    }

    private Optional<ClassificacaoFiscal> porNcm(String ncm) {
        return dataManager.load(ClassificacaoFiscal.class)
                .query("select e from ClassificacaoFiscal e where e.codNcm = :ncm")
                .parameter("ncm", ncm)
                .optional();
    }

    private boolean existe(ClassificacaoFiscal cf) {
        return dataManager.load(ClassificacaoFiscal.class).id(cf.getId()).optional().isPresent();
    }
}
