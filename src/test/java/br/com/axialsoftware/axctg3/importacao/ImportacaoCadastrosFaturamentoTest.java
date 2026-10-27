package br.com.axialsoftware.axctg3.importacao;

import br.com.axialsoftware.axctg3.entity.User;
import br.com.axialsoftware.axctg3.entity.cadastros.CondicaoPagamento;
import br.com.axialsoftware.axctg3.entity.cadastros.Empresa;
import br.com.axialsoftware.axctg3.entity.cadastros.Mensagem;
import br.com.axialsoftware.axctg3.entity.cadastros.Transportadora;
import br.com.axialsoftware.axctg3.entity.cadastros.Vendedor;
import br.com.axialsoftware.axctg3.entity.enums.CodRegimeTributario;
import br.com.axialsoftware.axctg3.entity.enums.SituacaoLote;
import br.com.axialsoftware.axctg3.entity.enums.TipoLote;
import br.com.axialsoftware.axctg3.entity.enums.TipoProduto;
import br.com.axialsoftware.axctg3.entity.fiscal.GrupoProduto;
import br.com.axialsoftware.axctg3.entity.fiscal.NaturezaOperacao;
import br.com.axialsoftware.axctg3.entity.fiscal.Produto;
import br.com.axialsoftware.axctg3.entity.importacao.ImpCondicaoPagamento;
import br.com.axialsoftware.axctg3.entity.importacao.ImpGrupoProduto;
import br.com.axialsoftware.axctg3.entity.importacao.ImpLote;
import br.com.axialsoftware.axctg3.entity.importacao.ImpMensagem;
import br.com.axialsoftware.axctg3.entity.importacao.ImpNaturezaOperacao;
import br.com.axialsoftware.axctg3.entity.importacao.ImpProduto;
import br.com.axialsoftware.axctg3.entity.importacao.ImpTransportadora;
import br.com.axialsoftware.axctg3.entity.importacao.ImpVendedor;
import br.com.axialsoftware.axctg3.entity.tabelas.ClassificacaoFiscal;
import br.com.axialsoftware.axctg3.service.importacao.ImportacaoCondicoesPagamentoService;
import br.com.axialsoftware.axctg3.service.importacao.ImportacaoGruposProdutoService;
import br.com.axialsoftware.axctg3.service.importacao.ImportacaoMensagensService;
import br.com.axialsoftware.axctg3.service.importacao.ImportacaoNaturezasOperacaoService;
import br.com.axialsoftware.axctg3.service.importacao.ImportacaoPlanoContasService;
import br.com.axialsoftware.axctg3.service.importacao.ImportacaoProdutosService;
import br.com.axialsoftware.axctg3.service.importacao.ImportacaoTransportadorasService;
import br.com.axialsoftware.axctg3.service.importacao.ImportacaoVendedoresService;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Importação dos cadastros de faturamento do legado: grupos de produtos, produtos, naturezas de
 * operação, condições de pagamento, vendedores e transportadoras — na empresa do lote (não na da
 * sessão), criando e atualizando pelo código.
 */
@SpringBootTest
@ExtendWith(AuthenticatedAsAdmin.class)
@ActiveProfiles("test")
class ImportacaoCadastrosFaturamentoTest {

    private static final int COD_EMPRESA = 9327;
    // NCM fictício (capítulo 99 não existe na NCM) — não colide com a tabela real
    private static final String NCM_TESTE = "99990327";

    // ClassificacaoFiscal é global, com índice único sem filtro de soft delete no HSQLDB
    private final int classificacaoCodigoTeste = 800000 + (int) (System.currentTimeMillis() % 90000);

    @Autowired
    DataManager dataManager;
    @Autowired
    CurrentAuthentication currentAuthentication;
    @Autowired
    ImportacaoGruposProdutoService gruposService;
    @Autowired
    ImportacaoProdutosService produtosService;
    @Autowired
    ImportacaoNaturezasOperacaoService naturezasService;
    @Autowired
    ImportacaoCondicoesPagamentoService condicoesService;
    @Autowired
    ImportacaoVendedoresService vendedoresService;
    @Autowired
    ImportacaoTransportadorasService transportadorasService;
    @Autowired
    ImportacaoMensagensService mensagensService;

    @BeforeEach
    void setUp() {
        limpar();
        Empresa empresa = dataManager.create(Empresa.class);
        empresa.setCodigo(COD_EMPRESA);
        empresa.setNome("Teste da importação de cadastros de faturamento");
        empresa.setApelido("Imp faturamento");
        empresa.setCrt(CodRegimeTributario.SIMPLES_NACIONAL);
        dataManager.save(empresa);
        ClassificacaoFiscal cf = dataManager.create(ClassificacaoFiscal.class);
        cf.setCodigo(classificacaoCodigoTeste);
        cf.setCodNcm(NCM_TESTE);
        cf.setDescricao("NCM de teste");
        dataManager.save(cf);
        // a sessão aponta para outra empresa: a importação não pode usá-la
        User admin = (User) currentAuthentication.getUser();
        admin.setCodEmpresa(COD_EMPRESA + 1);
    }

    @AfterEach
    void tearDown() {
        limpar();
    }

    @Test
    void test_produtoSemGruposNaEmpresaPedeOsGruposAntes() {
        ImpLote lote = lote(TipoLote.PRODUTOS, ImpProduto.class, p -> {
            p.setCodigo(1);
            p.setDescricao("PERFIL U");
            p.setApelido("PERFIL U (PC)");
            p.setGrupoProd(1);
        });

        assertThat(produtosService.importar(lote.getId()).getErros())
                .contains("A empresa " + COD_EMPRESA + " não tem grupos de produtos. Importe antes os grupos de produtos.");
    }

    @Test
    void test_gruposEProdutos() {
        ImpLote grupos = lote(TipoLote.GRUPOS_PRODUTO, ImpGrupoProduto.class,
                g -> {
                    g.setCodigo(1);
                    g.setNome("CORTE E DOBRA");
                },
                g -> {
                    g.setCodigo(2);
                    g.setNome("MATERIA PRIMA");
                    g.setTipoProduto(1);
                });
        ImportacaoPlanoContasService.Relatorio rg = gruposService.importar(grupos.getId());
        assertThat(rg.getErros()).isEmpty();
        assertThat(rg.getAvisos()).contains("Grupo de produtos 1: sem tipo de produto no legado; gravado como \"Outras\".");
        assertThat(grupo(1).getTipoProduto()).isEqualTo(TipoProduto.OUTRAS);
        assertThat(grupo(2).getTipoProduto()).isEqualTo(TipoProduto.MATERIA_PRIMA);

        ImpLote produtos = lote(TipoLote.PRODUTOS, ImpProduto.class,
                p -> {
                    p.setCodigo(1);
                    p.setDescricao("CHAPA A/C");
                    p.setApelido("CHAPA");
                    p.setUnidade("KG");
                    p.setGrupoProd(2);
                    p.setNcm(NCM_TESTE);
                    p.setPesoLiquido(new BigDecimal("1.50000"));
                    p.setInativo("N");
                },
                p -> {
                    p.setCodigo(2);
                    p.setDescricao("TUBO SEM APELIDO MUITO COMPRIDO");
                    p.setGrupoProd(7);    // grupo que não existe
                    p.setNcm("1406");     // NCM inválido
                });
        ImportacaoPlanoContasService.Relatorio rp = produtosService.importar(produtos.getId());

        assertThat(rp.getErros()).isEmpty();
        assertThat(rp.getImportadas()).isEqualTo(2);
        assertThat(rp.getAvisos())
                .contains("Grupo de produtos 7 não existe no axctg3; fica o grupo que já havia em produto 2.")
                .contains("NCM 1406 não existe na tabela NCM; fica a classificação fiscal que já havia em produto 2.")
                .contains("Produto 2: sem apelido no legado; usado \"TUBO SEM APELIDO MUI\".");
        Produto chapa = produto(1);
        assertThat(chapa.getGrupoProduto().getCodigo()).isEqualTo(2);
        assertThat(chapa.getTipoProduto()).isEqualTo(TipoProduto.MATERIA_PRIMA);
        assertThat(chapa.getClassificacaoFiscal().getCodNcm()).isEqualTo(NCM_TESTE);
        assertThat(chapa.getClassTrib().getCodigo()).isEqualTo(1);
        assertThat(chapa.getPesoLiquido()).isEqualByComparingTo("1.5");
        assertThat(chapa.getInativo()).isFalse();
        Produto tubo = produto(2);
        assertThat(tubo.getGrupoProduto()).isNull();
        assertThat(tubo.getClassificacaoFiscal()).isNull();
        assertThat(dataManager.load(ImpLote.class).id(produtos.getId()).one().getSituacao())
                .isEqualTo(SituacaoLote.IMPORTADO);
    }

    @Test
    void test_naturezaNoSimplesGanhaOCsosnDaCondicaoDoLegado() {
        ImpLote lote = lote(TipoLote.NATUREZAS_OPERACAO, ImpNaturezaOperacao.class,
                n -> natureza(n, 1, 5101, "VENDA PRODUCAO", "4.00", "1", "S"),
                n -> natureza(n, 3, 5124, "INDUST. POR ENCOM", "0.00", "2", "S"),
                n -> natureza(n, 2, 5101, "VENDA SEM CREDITO", "4.00", "3", "S"));

        ImportacaoPlanoContasService.Relatorio r = naturezasService.importar(lote.getId());

        assertThat(r.getErros()).isEmpty();
        assertThat(r.getAvisos()).anyMatch(a -> a.contains("não tem plano de contas"));
        assertThat(cstDaNatureza(1)).isEqualTo("101");
        assertThat(cstDaNatureza(3)).isEqualTo("400");
        assertThat(cstDaNatureza(2)).isEqualTo("102");
        NaturezaOperacao venda = dataManager.load(NaturezaOperacao.class)
                .query("select e from NaturezaOperacao e where e.codEmpresa = :cod and e.codigo = 1")
                .parameter("cod", COD_EMPRESA).one();
        assertThat(venda.getAliqIcms()).isEqualByComparingTo("4");
        assertThat(venda.getVenda()).isTrue();
        assertThat(venda.getCfop()).isEqualTo(5101);
    }

    @Test
    void test_condicoesVendedoresETransportadoras() {
        ImpLote condicoes = lote(TipoLote.CONDICOES_PAGAMENTO, ImpCondicaoPagamento.class, c -> {
            c.setCodigo(3);
            c.setNome("28/35/42 D.D.");
            c.setParcelas(3);
            c.setPrimeira(28);
            c.setDiferenca(7);
        });
        assertThat(condicoesService.importar(condicoes.getId()).getErros()).isEmpty();
        CondicaoPagamento condicao = dataManager.load(CondicaoPagamento.class)
                .query("select e from CondicaoPagamento e where e.codEmpresa = :cod").parameter("cod", COD_EMPRESA).one();
        assertThat(condicao.getParcelas()).isEqualTo(3);
        assertThat(condicao.getPrimeira()).isEqualTo(28);
        assertThat(condicao.getDiferenca()).isEqualTo(7);

        ImpLote vendedores = lote(TipoLote.VENDEDORES, ImpVendedor.class, v -> {
            v.setCodigo(3);
            v.setNome("RAFAEL");
            v.setCnpj("24689778000118");
            v.setComissao(new BigDecimal("2.1250"));
        });
        ImportacaoPlanoContasService.Relatorio rv = vendedoresService.importar(vendedores.getId());
        assertThat(rv.getErros()).isEmpty();
        assertThat(rv.getAvisos()).contains("Vendedor 3: comissão 2.125% arredondada para 2.13%.");
        assertThat(dataManager.load(Vendedor.class)
                .query("select e from Vendedor e where e.codEmpresa = :cod").parameter("cod", COD_EMPRESA).one()
                .getComissao()).isEqualByComparingTo("2.13");

        ImpLote transportadoras = lote(TipoLote.TRANSPORTADORAS, ImpTransportadora.class, t -> {
            t.setCodigo(1);
            t.setNome("TRANSPORTADORA FRANK");
            t.setCnpj("13192884000175");
            t.setCidade("VARZEA PAULISTA");
            t.setEstado("SP");
        });
        assertThat(transportadorasService.importar(transportadoras.getId()).getErros()).isEmpty();
        Transportadora transportadora = dataManager.load(Transportadora.class)
                .query("select e from Transportadora e where e.codEmpresa = :cod").parameter("cod", COD_EMPRESA).one();
        assertThat(transportadora.getCidade()).isEqualTo("VARZEA PAULISTA");

        // reimportar com o nome mudado atualiza, e a sessão (outra empresa) não recebe nada
        ImpLote denovo = lote(TipoLote.TRANSPORTADORAS, ImpTransportadora.class, t -> {
            t.setCodigo(1);
            t.setNome("FRANK GUINDASTES");
        });
        assertThat(transportadorasService.importar(denovo.getId()).getAvisos())
                .anyMatch(a -> a.startsWith("Transportadora 1: nome \"TRANSPORTADORA FRANK\" passa a \"FRANK GUINDASTES\""));
        assertThat(dataManager.load(Transportadora.class)
                .query("select e from Transportadora e where e.codEmpresa = :cod").parameter("cod", COD_EMPRESA + 1)
                .list()).isEmpty();
    }

    @Test
    void test_mensagens() {
        ImpLote lote = lote(TipoLote.MENSAGENS, ImpMensagem.class,
                m -> {
                    m.setCodigo(6);
                    m.setTexto("ICMS suspenso - Decreto n. 45.490/2000, art. 402.");
                },
                m -> m.setCodigo(7));
        ImportacaoPlanoContasService.Relatorio r = mensagensService.importar(lote.getId());
        assertThat(r.getErros()).isEmpty();
        assertThat(r.getImportadas()).isEqualTo(2);
        assertThat(r.getAvisos()).contains("Mensagem 7 sem texto no legado.");
        assertThat(dataManager.load(Mensagem.class)
                .query("select e from Mensagem e where e.codEmpresa = :cod and e.codigo = 6").parameter("cod", COD_EMPRESA)
                .one().getTexto()).isEqualTo("ICMS suspenso - Decreto n. 45.490/2000, art. 402.");
    }

    private void natureza(ImpNaturezaOperacao n, int codigo, int cfop, String nome, String aliq, String condicao,
                          String venda) {
        n.setCodigo(codigo);
        n.setCfop(cfop);
        n.setNome(nome);
        n.setAliqIcms(new BigDecimal(aliq));
        n.setCondicaoIcm(condicao);
        n.setVenda(venda);
    }

    private String cstDaNatureza(int codigo) {
        return dataManager.load(NaturezaOperacao.class)
                .query("select e from NaturezaOperacao e where e.codEmpresa = :cod and e.codigo = :codigo")
                .parameter("cod", COD_EMPRESA)
                .parameter("codigo", codigo)
                .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE).add("cst", FetchPlan.BASE))
                .one()
                .getCst()
                .getCodigo();
    }

    private GrupoProduto grupo(int codigo) {
        return dataManager.load(GrupoProduto.class)
                .query("select e from GrupoProduto e where e.codEmpresa = :cod and e.codigo = :codigo")
                .parameter("cod", COD_EMPRESA)
                .parameter("codigo", codigo)
                .one();
    }

    private Produto produto(int codigo) {
        return dataManager.load(Produto.class)
                .query("select e from Produto e where e.codEmpresa = :cod and e.codigo = :codigo")
                .parameter("cod", COD_EMPRESA)
                .parameter("codigo", codigo)
                .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE)
                        .add("grupoProduto", FetchPlan.BASE)
                        .add("classificacaoFiscal", FetchPlan.BASE)
                        .add("classTrib", FetchPlan.BASE))
                .one();
    }

    @SafeVarargs
    private <T> ImpLote lote(TipoLote tipo, Class<T> classe, Consumer<T>... linhas) {
        ImpLote lote = dataManager.create(ImpLote.class);
        lote.setTipo(tipo);
        lote.setSituacao(SituacaoLote.PRONTO);
        lote.setCodEmpresa(COD_EMPRESA);
        lote.setCodEmpresaLegado(1);
        lote = dataManager.save(lote);
        SaveContext ctx = new SaveContext();
        for (Consumer<T> preencher : linhas) {
            T linha = dataManager.create(classe);
            try {
                classe.getMethod("setLote", ImpLote.class).invoke(linha, lote);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
            preencher.accept(linha);
            ctx.saving(linha);
        }
        dataManager.save(ctx);
        return lote;
    }

    private void limpar() {
        apagar(carregar(Produto.class, "select e from Produto e where e.codEmpresa = :cod"));
        apagar(carregar(GrupoProduto.class, "select e from GrupoProduto e where e.codEmpresa = :cod"));
        apagar(carregar(NaturezaOperacao.class, "select e from NaturezaOperacao e where e.codEmpresa = :cod"));
        apagar(carregar(CondicaoPagamento.class, "select e from CondicaoPagamento e where e.codEmpresa = :cod"));
        apagar(carregar(Vendedor.class, "select e from Vendedor e where e.codEmpresa = :cod"));
        apagar(carregar(Transportadora.class, "select e from Transportadora e where e.codEmpresa = :cod"));
        apagar(carregar(Mensagem.class, "select e from Mensagem e where e.codEmpresa = :cod"));
        for (Class<?> imp : List.of(ImpProduto.class, ImpGrupoProduto.class, ImpNaturezaOperacao.class,
                ImpCondicaoPagamento.class, ImpVendedor.class, ImpTransportadora.class, ImpMensagem.class)) {
            apagar(carregar(imp, "select e from " + imp.getSimpleName() + " e where e.lote.codEmpresa = :cod"));
        }
        apagar(carregar(ImpLote.class, "select e from ImpLote e where e.codEmpresa = :cod"));
        apagar(carregar(Empresa.class, "select e from Empresa e where e.codigo = :cod"));
        apagar(dataManager.load(ClassificacaoFiscal.class)
                .query("select e from ClassificacaoFiscal e where e.codNcm = :ncm")
                .parameter("ncm", NCM_TESTE)
                .hint(PersistenceHints.SOFT_DELETION, false)
                .list());
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
