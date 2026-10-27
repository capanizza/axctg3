package br.com.axialsoftware.axctg3.importacao;

import br.com.axialsoftware.axctg3.entity.User;
import br.com.axialsoftware.axctg3.entity.cadastros.Empresa;
import br.com.axialsoftware.axctg3.entity.cadastros.Parceiro;
import br.com.axialsoftware.axctg3.entity.enums.SituacaoLote;
import br.com.axialsoftware.axctg3.entity.enums.TipoLote;
import br.com.axialsoftware.axctg3.entity.importacao.ImpLote;
import br.com.axialsoftware.axctg3.entity.importacao.ImpParceiro;
import br.com.axialsoftware.axctg3.entity.tabelas.Municipio;
import br.com.axialsoftware.axctg3.service.importacao.ImportacaoParceirosService;
import br.com.axialsoftware.axctg3.service.importacao.ImportacaoPlanoContasService;
import br.com.axialsoftware.axctg3.test_support.AuthenticatedAsAdmin;
import io.jmix.core.DataManager;
import io.jmix.core.FetchPlan;
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
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Importação de um lote de parceiros do legado: na empresa do lote (não na da sessão), município
 * pelo código IBGE, número 0 = sem número, CNPJ repetido só avisa, e reimportação atualizando
 * só o que mudou.
 */
@SpringBootTest
@ExtendWith(AuthenticatedAsAdmin.class)
@ActiveProfiles("test")
class ImportacaoParceirosServiceTest {

    private static final int COD_EMPRESA = 9317;
    // código fora da faixa do IBGE, para não esbarrar num município de verdade
    private static final int COD_MUNICIPIO = 9931701;

    @Autowired
    DataManager dataManager;
    @Autowired
    ImportacaoParceirosService service;
    @Autowired
    CurrentAuthentication currentAuthentication;

    @BeforeEach
    void setUp() {
        limpar();
        Empresa empresa = dataManager.create(Empresa.class);
        empresa.setCodigo(COD_EMPRESA);
        empresa.setNome("Teste da importação de parceiros");
        empresa.setApelido("Imp parceiros");
        dataManager.save(empresa);
        Municipio municipio = dataManager.create(Municipio.class);
        municipio.setCodigo(COD_MUNICIPIO);
        municipio.setNome("Município de teste");
        municipio.setUf("SP");
        dataManager.save(municipio);
        // a sessão aponta para outra empresa: a importação não pode usá-la
        User admin = (User) currentAuthentication.getUser();
        admin.setCodEmpresa(COD_EMPRESA + 1);
    }

    @AfterEach
    void tearDown() {
        limpar();
    }

    @Test
    void test_criaComMunicipioEAvisaCnpjRepetido() {
        ImpLote lote = criarLote(
                linha(1L, "PADARIA BOA VISTA LTDA", "11222333000181", l -> {
                    l.setLogradouro("AMPERE");
                    l.setNumero(375);
                    l.setCep("12929570");
                    l.setMunicipio(COD_MUNICIPIO);
                    l.setEstado("SP");
                    l.setCliente("S");
                    l.setObservacao("cliente antigo");
                }),
                linha(2L, "MERCADO SEM NUMERO", "11222333000181", l -> {
                    l.setNumero(0);
                    l.setMunicipio(0);
                    l.setEstado("MG");
                }),
                linha(3L, "SECRETARIA DA RECEITA FEDERAL", "00000000000000", l -> l.setApelido(null)),
                linha(4L, "OUTRA SECRETARIA", "00000000000000", l -> l.setMunicipio(1234567)));

        ImportacaoPlanoContasService.Relatorio conferencia = service.conferir(lote.getId());
        assertThat(conferencia.getErros()).isEmpty();
        assertThat(parceiros()).isEmpty(); // conferir não grava

        ImportacaoPlanoContasService.Relatorio relatorio = service.importar(lote.getId());

        assertThat(relatorio.getErros()).isEmpty();
        assertThat(relatorio.getImportadas()).isEqualTo(4);
        assertThat(relatorio.getAvisos())
                .anyMatch(a -> a.equals("CNPJ/CPF 11222333000181 repetido nos parceiros [1, 2]."))
                .noneMatch(a -> a.contains("CNPJ/CPF 00000000000000"))
                .anyMatch(a -> a.equals("Parceiro 3: sem apelido no legado; usado \"SECRETARIA DA RECEIT\"."))
                .anyMatch(a -> a.startsWith("Parceiro 4: município 1234567 não existe"));

        Map<Long, Parceiro> parceiros = parceiros();
        assertThat(parceiros).containsOnlyKeys(1L, 2L, 3L, 4L);
        Parceiro padaria = parceiros.get(1L);
        assertThat(padaria.getNumero()).isEqualTo("375");
        assertThat(padaria.getMunicipio().getCodigo()).isEqualTo(COD_MUNICIPIO);
        assertThat(padaria.getCliente()).isTrue();
        assertThat(padaria.getFornecedor()).isFalse();
        assertThat(padaria.getObservacao()).isEqualTo("cliente antigo");
        assertThat(parceiros.get(2L).getNumero()).isNull();
        assertThat(parceiros.get(2L).getMunicipio()).isNull();
        assertThat(parceiros.get(2L).getEstado()).isEqualTo("MG");
        assertThat(dataManager.load(Parceiro.class)
                .query("select e from Parceiro e where e.codEmpresa = :cod")
                .parameter("cod", COD_EMPRESA + 1)
                .list()).isEmpty();

        ImpLote recarregado = dataManager.load(ImpLote.class).id(lote.getId()).one();
        assertThat(recarregado.getSituacao()).isEqualTo(SituacaoLote.IMPORTADO);
        assertThat(recarregado.getMensagem()).contains("4 criados, 0 alterados, 0 iguais");
    }

    @Test
    void test_reimportacaoAtualizaSoOQueMudou() {
        service.importar(criarLote(
                linha(1L, "PADARIA BOA VISTA LTDA", "11222333000181", l -> l.setTelefone("1144445555")),
                linha(2L, "MERCADO", "44555666000172", l -> { })).getId());

        ImportacaoPlanoContasService.Relatorio relatorio = service.importar(criarLote(
                linha(1L, "PADARIA BOA VISTA LTDA", "11222333000181", l -> l.setTelefone("1144446666")),
                linha(2L, "MERCADO", "44555666000172", l -> { })).getId());

        assertThat(relatorio.getErros()).isEmpty();
        assertThat(relatorio.getImportadas()).isEqualTo(1);
        assertThat(relatorio.getAvisos())
                .anyMatch(a -> a.equals("Parceiro 1: telefone \"1144445555\" passa a \"1144446666\"."));
        assertThat(parceiros().get(1L).getTelefone()).isEqualTo("1144446666");
    }

    @Test
    void test_semRazaoImpedeAImportacao() {
        ImpLote lote = criarLote(linha(5L, " ", "11222333000181", l -> { }));

        ImportacaoPlanoContasService.Relatorio relatorio = service.importar(lote.getId());

        assertThat(relatorio.getErros()).anyMatch(e -> e.equals("Parceiro 5 sem razão social."));
        assertThat(parceiros()).isEmpty();
    }

    // na GB são favorecidos de contas a pagar (salário, FGTS, DAS...): ficam de fora, com aviso
    @Test
    void test_semCnpjFicaDeForaComAviso() {
        ImpLote lote = criarLote(
                linha(6L, "SALARIO FUNCIONARIO", "", l -> { }),
                linha(7L, "MERCADO", "44555666000172", l -> { }));

        ImportacaoPlanoContasService.Relatorio relatorio = service.importar(lote.getId());

        assertThat(relatorio.getErros()).isEmpty();
        assertThat(relatorio.getImportadas()).isEqualTo(1);
        assertThat(relatorio.getAvisos()).anyMatch(a -> a.startsWith("1 parceiro(s) sem CNPJ/CPF no legado não entram")
                && a.endsWith(": 6 SALARIO FUNCIONARIO."));
        assertThat(parceiros()).containsOnlyKeys(7L);
    }

    private ImpParceiro linha(Long codigo, String razao, String cnpj, Consumer<ImpParceiro> resto) {
        ImpParceiro l = dataManager.create(ImpParceiro.class);
        l.setCodigo(codigo);
        l.setRazao(razao);
        l.setApelido(razao.length() > 20 ? razao.substring(0, 20) : razao);
        l.setCnpj(cnpj);
        l.setCliente("N");
        l.setFornecedor("N");
        resto.accept(l);
        return l;
    }

    private ImpLote criarLote(ImpParceiro... linhas) {
        ImpLote lote = dataManager.create(ImpLote.class);
        lote.setTipo(TipoLote.PARCEIROS);
        lote.setSituacao(SituacaoLote.PRONTO);
        lote.setCodEmpresa(COD_EMPRESA);
        lote.setCodEmpresaLegado(1);
        lote = dataManager.save(lote);
        SaveContext ctx = new SaveContext();
        for (ImpParceiro l : linhas) {
            l.setLote(lote);
            ctx.saving(l);
        }
        dataManager.save(ctx);
        return lote;
    }

    private Map<Long, Parceiro> parceiros() {
        return dataManager.load(Parceiro.class)
                .query("select e from Parceiro e where e.codEmpresa = :cod")
                .parameter("cod", COD_EMPRESA)
                .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE).add("municipio", FetchPlan.BASE))
                .list()
                .stream()
                .collect(Collectors.toMap(Parceiro::getCodigo, p -> p));
    }

    private void limpar() {
        apagar(carregar(Parceiro.class, "select e from Parceiro e where e.codEmpresa = :cod", COD_EMPRESA));
        apagar(carregar(ImpParceiro.class, "select e from ImpParceiro e where e.lote.codEmpresa = :cod", COD_EMPRESA));
        apagar(carregar(ImpLote.class, "select e from ImpLote e where e.codEmpresa = :cod", COD_EMPRESA));
        apagar(carregar(Empresa.class, "select e from Empresa e where e.codigo = :cod", COD_EMPRESA));
        apagar(carregar(Municipio.class, "select e from Municipio e where e.codigo = :cod", COD_MUNICIPIO));
    }

    private <T> List<T> carregar(Class<T> classe, String jpql, int cod) {
        return dataManager.load(classe)
                .query(jpql)
                .parameter("cod", cod)
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
