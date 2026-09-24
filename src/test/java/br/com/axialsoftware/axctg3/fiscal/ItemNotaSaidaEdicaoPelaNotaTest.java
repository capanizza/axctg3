package br.com.axialsoftware.axctg3.fiscal;

import br.com.axialsoftware.axctg3.entity.cadastros.Parceiro;
import br.com.axialsoftware.axctg3.entity.fiscal.ItemNotaSaida;
import br.com.axialsoftware.axctg3.entity.fiscal.NaturezaOperacao;
import br.com.axialsoftware.axctg3.entity.fiscal.NotaSaida;
import br.com.axialsoftware.axctg3.entity.fiscal.Produto;
import br.com.axialsoftware.axctg3.entity.tabelas.ClassTrib;
import br.com.axialsoftware.axctg3.test_support.AuthenticatedAsAdmin;
import io.jmix.core.DataManager;
import io.jmix.core.FetchPlan;
import io.jmix.core.SaveContext;
import io.jmix.data.PersistenceHints;
import org.junit.jupiter.api.AfterEach;
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
 * Editar um item JÁ GRAVADO pela tela da nota e salvar a nota: o item vem do fetch plan
 * de {@code nota-saida-detail-view.xml} (itens com {@code _base}, sem a referência de
 * volta {@code notaSaida}) e o {@code ItemNotaSaidaEventListener} lê
 * {@code item.getNotaSaida()} no EntitySavingEvent. Bug real 2026-09-24: trocar o
 * cClassTrib de um item de nota de doação e salvar a nota estourava
 * {@code ValidationException.instantiatingValueholderWithNullSession}.
 */
@SpringBootTest
@ExtendWith(AuthenticatedAsAdmin.class)
@ActiveProfiles("test")
class ItemNotaSaidaEdicaoPelaNotaTest {

    private static final int COD_EMPRESA = 9432;
    // Tabela global com índice único sem filtro de soft delete no HSQLDB — base variável.
    private final int classTribCodigoTeste = 9980000 + (int) (System.currentTimeMillis() % 9000);
    private final int classTribDoacaoTeste = 9960000 + (int) (System.currentTimeMillis() % 9000);

    @Autowired
    private DataManager dataManager;

    // Fetch plan antigo da tela (itens sem notaSaida) — cobre o fallback do listener.
    @Test
    void alterarItemCarregadoSemNotaSaidaESalvarANotaNaoEstoura() {
        alterarESalvar(false);
    }

    // Fetch plan atual de nota-saida-detail-view.xml (itens com notaSaida).
    @Test
    void alterarItemCarregadoComNotaSaidaESalvarANotaNaoEstoura() {
        alterarESalvar(true);
    }

    private void alterarESalvar(boolean comNotaSaida) {
        NotaSaida nota = criarNotaComItem();

        NotaSaida carregada = dataManager.load(NotaSaida.class)
                .id(nota.getId())
                .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE)
                        .add("itens", fi -> {
                            fi.addFetchPlan(FetchPlan.BASE).add("produto", FetchPlan.INSTANCE_NAME);
                            if (comNotaSaida) {
                                fi.add("notaSaida", FetchPlan.INSTANCE_NAME);
                            }
                        })
                        .add("parceiro", FetchPlan.INSTANCE_NAME)
                        .add("natureza", FetchPlan.INSTANCE_NAME))
                .one();
        ItemNotaSaida item = carregada.getItens().get(0);
        item.setCodClassTrib(410999);
        item.setQuantidade(new BigDecimal("2"));

        dataManager.save(new SaveContext().saving(carregada, item));

        ItemNotaSaida recarregado = dataManager.load(ItemNotaSaida.class).id(item.getId()).one();
        assertThat(recarregado.getCodClassTrib()).isEqualTo(410999);
        assertThat(recarregado.getQuantidade()).isEqualByComparingTo("2");
    }

    // Pedido do usuário 2026-09-24: cClassTrib do cabeçalho que não é o de tributação
    // integral (ex.: 410999 na doação) vence o do produto; CST 41 vem da natureza.
    @Test
    void itemNovoPegaCstDaNaturezaEClassTribDoCabecalho() {
        NotaSaida nota = criarNotaComItem();
        ClassTrib doacao = dataManager.create(ClassTrib.class);
        doacao.setCodigo(classTribDoacaoTeste);
        doacao.setCst(410);
        doacao.setDescricao("Doação de teste");
        doacao.setTipoAliquota("Sem alíquota");
        doacao.setNomenclatura("Teste");
        doacao.setDescricaoTratamentoTributario("Teste");
        dataManager.save(doacao);

        NaturezaOperacao natureza = dataManager.load(NaturezaOperacao.class).id(nota.getNatureza().getId()).one();
        natureza.setCst(dataManager.load(br.com.axialsoftware.axctg3.entity.tabelas.Cst.class)
                .query("select e from Cst e where e.codigo = '41'").one());
        natureza.setAliqIcms(new BigDecimal("7"));
        dataManager.save(natureza);
        NotaSaida carregada = dataManager.load(NotaSaida.class).id(nota.getId()).one();
        carregada.setClassTrib(doacao);
        carregada = dataManager.save(carregada);

        ItemNotaSaida item = dataManager.create(ItemNotaSaida.class);
        item.setNotaSaida(carregada);
        item.setItem(2);
        item.setProduto(dataManager.load(Produto.class)
                .query("select e from Produto e where e.codEmpresa = :c").parameter("c", COD_EMPRESA).one());
        item.setQuantidade(new BigDecimal("1"));
        item.setValorUnitario(new BigDecimal("50"));
        item = dataManager.save(item);

        assertThat(item.getCst()).isEqualTo("41");
        assertThat(item.getCodClassTrib()).isEqualTo(classTribDoacaoTeste);
        assertThat(item.getValorIcms()).isZero();
    }

    private NotaSaida criarNotaComItem() {
        Parceiro p = dataManager.create(Parceiro.class);
        p.setCodigo(1L);
        p.setCodEmpresa(COD_EMPRESA);
        p.setNome("Cliente de Teste");
        p.setApelido("Cliente Teste");
        p.setCnpj("12345678000190");
        dataManager.save(p);

        NaturezaOperacao n = dataManager.create(NaturezaOperacao.class);
        n.setCodigo(1);
        n.setCodEmpresa(COD_EMPRESA);
        n.setNome("Doação de teste");
        n.setCfop(5910);
        dataManager.save(n);

        ClassTrib classTrib = dataManager.create(ClassTrib.class);
        classTrib.setCodigo(classTribCodigoTeste);
        classTrib.setCst(1);
        classTrib.setDescricao("ClassTrib de teste");
        classTrib.setTipoAliquota("Padrão");
        classTrib.setNomenclatura("Teste");
        classTrib.setDescricaoTratamentoTributario("Teste");
        dataManager.save(classTrib);

        Produto produto = dataManager.create(Produto.class);
        produto.setCodigo(1);
        produto.setCodEmpresa(COD_EMPRESA);
        produto.setDescricao("Produto de teste");
        produto.setApelido("Teste");
        produto.setClassTrib(classTrib);
        dataManager.save(produto);

        NotaSaida nota = dataManager.create(NotaSaida.class);
        nota.setCodEmpresa(COD_EMPRESA);
        nota.setDataEmissao(LocalDate.now());
        nota.setDataSaida(LocalDate.now());
        nota.setEspecie("NF");
        nota.setSerie("1");
        nota.setParceiro(p);
        nota.setNatureza(n);
        nota = dataManager.save(nota);

        ItemNotaSaida item = dataManager.create(ItemNotaSaida.class);
        item.setNotaSaida(nota);
        item.setItem(1);
        item.setProduto(produto);
        item.setQuantidade(new BigDecimal("1"));
        item.setValorUnitario(new BigDecimal("10"));
        dataManager.save(item);
        return nota;
    }

    @AfterEach
    void tearDown() {
        apagar(dataManager.load(ItemNotaSaida.class)
                .query("select e from ItemNotaSaida e where e.notaSaida.codEmpresa = :c")
                .parameter("c", COD_EMPRESA).list());
        apagar(dataManager.load(NotaSaida.class)
                .query("select e from NotaSaida e where e.codEmpresa = :c").parameter("c", COD_EMPRESA).list());
        apagar(dataManager.load(Produto.class)
                .query("select e from Produto e where e.codEmpresa = :c").parameter("c", COD_EMPRESA).list());
        apagar(dataManager.load(NaturezaOperacao.class)
                .query("select e from NaturezaOperacao e where e.codEmpresa = :c").parameter("c", COD_EMPRESA).list());
        apagar(dataManager.load(Parceiro.class)
                .query("select e from Parceiro e where e.codEmpresa = :c").parameter("c", COD_EMPRESA).list());
        apagar(dataManager.load(ClassTrib.class)
                .query("select e from ClassTrib e where e.codigo in :c")
                .parameter("c", List.of(classTribCodigoTeste, classTribDoacaoTeste)).list());
    }

    private void apagar(List<?> entidades) {
        if (!entidades.isEmpty()) {
            dataManager.save(new SaveContext()
                    .setHint(PersistenceHints.SOFT_DELETION, false)
                    .removing(entidades.toArray()));
        }
    }
}
