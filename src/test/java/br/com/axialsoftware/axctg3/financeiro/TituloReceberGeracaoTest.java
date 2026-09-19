package br.com.axialsoftware.axctg3.financeiro;

import br.com.axialsoftware.axctg3.entity.User;
import br.com.axialsoftware.axctg3.entity.cadastros.CondicaoPagamento;
import br.com.axialsoftware.axctg3.entity.cadastros.Empresa;
import br.com.axialsoftware.axctg3.entity.cadastros.Parceiro;
import br.com.axialsoftware.axctg3.entity.fiscal.ItemNotaSaida;
import br.com.axialsoftware.axctg3.entity.fiscal.NaturezaOperacao;
import br.com.axialsoftware.axctg3.entity.fiscal.NotaSaida;
import br.com.axialsoftware.axctg3.entity.fiscal.Produto;
import br.com.axialsoftware.axctg3.entity.financeiro.Banco;
import br.com.axialsoftware.axctg3.entity.financeiro.HistoricoFinanceiro;
import br.com.axialsoftware.axctg3.entity.financeiro.ItemReceber;
import br.com.axialsoftware.axctg3.entity.financeiro.TituloReceber;
import br.com.axialsoftware.axctg3.entity.tabelas.ClassTrib;
import br.com.axialsoftware.axctg3.service.financeiro.TituloReceberService;
import br.com.axialsoftware.axctg3.test_support.AuthenticatedAsAdmin;
import io.jmix.core.DataManager;
import io.jmix.core.FetchPlan;
import io.jmix.core.security.CurrentAuthentication;
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
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cobre {@code TituloReceberService.gerarTitulosDaEmissao}, decidido com o usuário
 * 2026-09-14: títulos calculados a partir de {@code NotaSaida.condicaoPagamento} (número
 * de parcelas, dias pro primeiro vencimento, dias entre parcelas) ANTES de
 * {@code NfeEmissaoService} montar o XML — o grupo {@code cobr}/{@code dup} do XML
 * (montagem de XML sem teste automatizado neste projeto, ver {@code NfeEmissaoServiceTest})
 * depende dos títulos já existirem. Testado direto no serviço, sem passar pela emissão de
 * verdade.
 *
 * <p>Cada teste usa seu PRÓPRIO {@code codEmpresa} (contador estático, base variável por
 * execução — ver comentário do campo) em vez de uma constante fixa compartilhada: o
 * HSQLDB de teste é um arquivo persistente entre rodadas de {@code clean test}, e seu
 * índice único não exclui linha soft-deletada — reaproveitar o mesmo código de empresa
 * entre execuções bateria em {@code UniqueConstraintViolationException} numa rodada
 * seguinte. {@code TituloReceberEventListener.onTituloReceberChangedBeforeCommit} também
 * exige um {@code HistoricoFinanceiro} codigo=1 já existente pra essa empresa.
 */
@SpringBootTest
@ExtendWith(AuthenticatedAsAdmin.class)
@ActiveProfiles("test")
class TituloReceberGeracaoTest {

    // Base variável por execução (não um literal fixo tipo 9120): o HSQLDB de teste é um
    // arquivo persistente entre rodadas (ver CLAUDE.md), e seu índice único não exclui
    // linha soft-deletada — reusar o MESMO codEmpresa entre uma rodada de "clean test" e a
    // próxima esbarraria na constraint única de Produto/NaturezaOperacao/etc mesmo depois
    // do tearDown ter "removido" (soft delete) as linhas da rodada anterior.
    private static final AtomicInteger COD_EMPRESA_SEQ =
            new AtomicInteger(800_000 + (int) (System.currentTimeMillis() % 100_000));
    private static final int CLASS_TRIB_CODIGO_TESTE = 9990004;

    @Autowired
    private DataManager dataManager;
    @Autowired
    private TituloReceberService tituloReceberService;
    @Autowired
    private CurrentAuthentication currentAuthentication;
    @Autowired
    private Sequences sequences;

    private int codEmpresa;

    @BeforeEach
    void setUp() {
        codEmpresa = COD_EMPRESA_SEQ.getAndIncrement();

        // TituloReceberEventListener.onTituloReceberSaving sobrescreve codEmpresa com
        // utilGeralService.getCodEmpresa() (lido do usuário logado, não do valor setado no
        // código) — sem isso a entidade tenta persistir com codEmpresa nulo e quebra
        // @NotNull. onTituloReceberChangedBeforeCommit também cria o item 1 de emissão
        // automaticamente, buscando um HistoricoFinanceiro codigo=1 da empresa — sem essa
        // linha, o save de qualquer TituloReceber novo quebra.
        User admin = (User) currentAuthentication.getUser();
        admin.setCodEmpresa(codEmpresa);

        HistoricoFinanceiro historicoEmissao = dataManager.create(HistoricoFinanceiro.class);
        historicoEmissao.setCodigo(1);
        historicoEmissao.setCodEmpresa(codEmpresa);
        historicoEmissao.setNome("Emissão de teste");
        historicoEmissao.setEmissao(true);
        historicoEmissao.setBaixa(false);
        historicoEmissao.setJuros(false);
        historicoEmissao.setDesconto(false);
        dataManager.save(historicoEmissao);
    }

    @AfterEach
    void tearDown() {
        dataManager.load(Empresa.class)
                .query("select e from Empresa e where e.codigo = :codEmpresa")
                .parameter("codEmpresa", codEmpresa)
                .list()
                .forEach(dataManager::remove);
        dataManager.load(ItemReceber.class)
                .query("select e from ItemReceber e where e.tituloReceber.codEmpresa = :codEmpresa")
                .parameter("codEmpresa", codEmpresa)
                .list()
                .forEach(dataManager::remove);
        dataManager.load(TituloReceber.class)
                .query("select e from TituloReceber e where e.codEmpresa = :codEmpresa")
                .parameter("codEmpresa", codEmpresa)
                .list()
                .forEach(dataManager::remove);
        dataManager.load(NotaSaida.class)
                .query("select e from NotaSaida e where e.codEmpresa = :codEmpresa")
                .parameter("codEmpresa", codEmpresa)
                .list()
                .forEach(dataManager::remove);
        dataManager.load(NaturezaOperacao.class)
                .query("select e from NaturezaOperacao e where e.codEmpresa = :codEmpresa")
                .parameter("codEmpresa", codEmpresa)
                .list()
                .forEach(dataManager::remove);
        dataManager.load(Parceiro.class)
                .query("select e from Parceiro e where e.codEmpresa = :codEmpresa")
                .parameter("codEmpresa", codEmpresa)
                .list()
                .forEach(dataManager::remove);
        dataManager.load(Produto.class)
                .query("select e from Produto e where e.codEmpresa = :codEmpresa")
                .parameter("codEmpresa", codEmpresa)
                .list()
                .forEach(dataManager::remove);
        dataManager.load(Banco.class)
                .query("select e from Banco e where e.codEmpresa = :codEmpresa")
                .parameter("codEmpresa", codEmpresa)
                .list()
                .forEach(dataManager::remove);
        dataManager.load(CondicaoPagamento.class)
                .query("select e from CondicaoPagamento e where e.codEmpresa = :codEmpresa")
                .parameter("codEmpresa", codEmpresa)
                .list()
                .forEach(dataManager::remove);
        dataManager.load(HistoricoFinanceiro.class)
                .query("select e from HistoricoFinanceiro e where e.codEmpresa = :codEmpresa")
                .parameter("codEmpresa", codEmpresa)
                .list()
                .forEach(dataManager::remove);
        // ClassTrib (tabela global, sem codEmpresa) fica de propósito — soft delete +
        // índice único (codigo) sem exclusão de linha soft-deletada no HSQLDB faria
        // qualquer teste seguinte que recriasse o mesmo código esbarrar na constraint.
        // criarClassTrib() já é find-or-create, então não duplica entre execuções.
    }

    private ClassTrib criarClassTrib() {
        return dataManager.load(ClassTrib.class)
                .query("select e from ClassTrib e where e.codigo = :codigo")
                .parameter("codigo", CLASS_TRIB_CODIGO_TESTE)
                .optional()
                .orElseGet(() -> {
                    ClassTrib classTrib = dataManager.create(ClassTrib.class);
                    classTrib.setCodigo(CLASS_TRIB_CODIGO_TESTE);
                    classTrib.setCst(1);
                    classTrib.setDescricao("ClassTrib de teste");
                    classTrib.setTipoAliquota("Padrão");
                    classTrib.setNomenclatura("Teste");
                    classTrib.setDescricaoTratamentoTributario("Teste");
                    return dataManager.save(classTrib);
                });
    }

    private Parceiro criarParceiro() {
        Parceiro parceiro = dataManager.create(Parceiro.class);
        parceiro.setCodigo(1L);
        parceiro.setCodEmpresa(codEmpresa);
        parceiro.setNome("Cliente de Teste");
        parceiro.setApelido("Cliente Teste");
        parceiro.setCnpj("12345678000190");
        return dataManager.save(parceiro);
    }

    private Banco criarBanco() {
        Banco banco = dataManager.create(Banco.class);
        banco.setCodigo(1);
        banco.setCodEmpresa(codEmpresa);
        banco.setNome("Banco de Teste");
        return dataManager.save(banco);
    }

    private CondicaoPagamento criarCondicaoPagamento(int parcelas, int primeira, int diferenca) {
        CondicaoPagamento condicaoPagamento = dataManager.create(CondicaoPagamento.class);
        condicaoPagamento.setCodigo(1);
        condicaoPagamento.setCodEmpresa(codEmpresa);
        condicaoPagamento.setNome(parcelas + "x");
        condicaoPagamento.setParcelas(parcelas);
        condicaoPagamento.setPrimeira(primeira);
        condicaoPagamento.setDiferenca(diferenca);
        return dataManager.save(condicaoPagamento);
    }

    private NotaSaida criarNotaSaidaComItem(CondicaoPagamento condicaoPagamento, Banco banco,
                                             Parceiro parceiro, BigDecimal quantidade, BigDecimal valorUnitario) {
        NaturezaOperacao natureza = dataManager.create(NaturezaOperacao.class);
        natureza.setCodigo(1);
        natureza.setCodEmpresa(codEmpresa);
        natureza.setNome("Venda de teste");
        natureza.setCfop(5102);
        natureza = dataManager.save(natureza);

        Produto produto = dataManager.create(Produto.class);
        produto.setCodigo(1);
        produto.setCodEmpresa(codEmpresa);
        produto.setDescricao("Produto de teste");
        produto.setApelido("Teste");
        produto.setClassTrib(criarClassTrib());
        produto = dataManager.save(produto);

        NotaSaida notaSaida = dataManager.create(NotaSaida.class);
        notaSaida.setCodEmpresa(codEmpresa);
        notaSaida.setDataEmissao(LocalDate.of(2026, 9, 14));
        notaSaida.setDataSaida(LocalDate.of(2026, 9, 14));
        notaSaida.setEspecie("NF");
        notaSaida.setSerie("1");
        notaSaida.setParceiro(parceiro);
        notaSaida.setNatureza(natureza);
        notaSaida.setCondicaoPagamento(condicaoPagamento);
        notaSaida.setBanco(banco);
        notaSaida = dataManager.save(notaSaida);

        ItemNotaSaida item = dataManager.create(ItemNotaSaida.class);
        item.setNotaSaida(notaSaida);
        item.setItem(1);
        item.setProduto(produto);
        item.setQuantidade(quantidade);
        item.setValorUnitario(valorUnitario);
        dataManager.save(item);

        return dataManager.load(NotaSaida.class).id(notaSaida.getId()).one();
    }

    // notaSaida precisa vir fetched (mesmo que rasa) — ItemNotaSaidaEventListener.
    // onItemNotaSaidaSaving lê item.getNotaSaida() logo no início; sem isso, salvar o item
    // recarregado por uma query crua (sem fetchPlan) estoura "Cannot get unfetched
    // attribute" num objeto detached.
    private ItemNotaSaida carregarItemDaNota(NotaSaida notaSaida) {
        return dataManager.load(ItemNotaSaida.class)
                .query("select e from ItemNotaSaida e where e.notaSaida = :notaSaida")
                .parameter("notaSaida", notaSaida)
                .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE)
                        .add("notaSaida", fpNota -> fpNota.addFetchPlan(FetchPlan.BASE)
                                .add("natureza", FetchPlan.BASE))
                        .add("produto", FetchPlan.BASE))
                .one();
    }

    private List<TituloReceber> titulosDaNota(NotaSaida notaSaida) {
        return dataManager.load(TituloReceber.class)
                .query("select e from TituloReceber e where e.notaSaida = :notaSaida order by e.dataVencimento")
                .parameter("notaSaida", notaSaida)
                .list();
    }

    @Test
    void parcelaUnicaNaoLevaLetraNoNumero() {
        Parceiro parceiro = criarParceiro();
        Banco banco = criarBanco();
        CondicaoPagamento condicaoPagamento = criarCondicaoPagamento(1, 30, 0);
        NotaSaida notaSaida = criarNotaSaidaComItem(condicaoPagamento, banco, parceiro,
                new BigDecimal("10"), new BigDecimal("10")); // valor 100.00

        String erro = tituloReceberService.gerarTitulosDaEmissao(notaSaida);
        assertThat(erro).isNull();

        List<TituloReceber> titulos = titulosDaNota(notaSaida);
        assertThat(titulos).hasSize(1);
        TituloReceber titulo = titulos.get(0);
        assertThat(titulo.getNumero()).isEqualTo(String.format("%06d", notaSaida.getNumero()));
        assertThat(titulo.getValor()).isEqualByComparingTo("100.00");
        assertThat(titulo.getDataVencimento()).isEqualTo(LocalDate.of(2026, 10, 14)); // +30 dias
        assertThat(titulo.getBanco()).isEqualTo(banco);
        assertThat(titulo.getParceiro()).isEqualTo(parceiro);
    }

    @Test
    void multiplasParcelasLevamLetraEDatasEspacadas() {
        Parceiro parceiro = criarParceiro();
        Banco banco = criarBanco();
        CondicaoPagamento condicaoPagamento = criarCondicaoPagamento(3, 30, 30);
        NotaSaida notaSaida = criarNotaSaidaComItem(condicaoPagamento, banco, parceiro,
                new BigDecimal("10"), new BigDecimal("10")); // valor 100.00

        String erro = tituloReceberService.gerarTitulosDaEmissao(notaSaida);
        assertThat(erro).isNull();

        List<TituloReceber> titulos = titulosDaNota(notaSaida);
        assertThat(titulos).hasSize(3);
        String base = String.format("%06d", notaSaida.getNumero());

        // 100.00 / 3 = 33.33 (arredondado) por parcela — sobra vai pra primeira: 33.34
        assertThat(titulos.get(0).getNumero()).isEqualTo(base + "/A");
        assertThat(titulos.get(0).getValor()).isEqualByComparingTo("33.34");
        assertThat(titulos.get(0).getDataVencimento()).isEqualTo(LocalDate.of(2026, 10, 14));

        assertThat(titulos.get(1).getNumero()).isEqualTo(base + "/B");
        assertThat(titulos.get(1).getValor()).isEqualByComparingTo("33.33");
        assertThat(titulos.get(1).getDataVencimento()).isEqualTo(LocalDate.of(2026, 11, 13));

        assertThat(titulos.get(2).getNumero()).isEqualTo(base + "/C");
        assertThat(titulos.get(2).getValor()).isEqualByComparingTo("33.33");
        assertThat(titulos.get(2).getDataVencimento()).isEqualTo(LocalDate.of(2026, 12, 13));

        BigDecimal soma = titulos.stream().map(TituloReceber::getValor).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(soma).isEqualByComparingTo(notaSaida.getValor());
    }

    @Test
    void naoGeraDeNovoSeNotaJaTemTitulo() {
        Parceiro parceiro = criarParceiro();
        Banco banco = criarBanco();
        CondicaoPagamento condicaoPagamento = criarCondicaoPagamento(1, 10, 0);
        NotaSaida notaSaida = criarNotaSaidaComItem(condicaoPagamento, banco, parceiro,
                new BigDecimal("10"), new BigDecimal("10"));

        tituloReceberService.gerarTitulosDaEmissao(notaSaida);
        tituloReceberService.gerarTitulosDaEmissao(notaSaida); // reemissão — não duplica

        assertThat(titulosDaNota(notaSaida)).hasSize(1);
    }

    @Test
    void notaEditadaDepoisRegeneraTitulos() {
        Parceiro parceiro = criarParceiro();
        Banco banco = criarBanco();
        CondicaoPagamento condicaoPagamento = criarCondicaoPagamento(1, 10, 0);
        NotaSaida notaSaida = criarNotaSaidaComItem(condicaoPagamento, banco, parceiro,
                new BigDecimal("10"), new BigDecimal("10")); // valor 100.00

        tituloReceberService.gerarTitulosDaEmissao(notaSaida);
        assertThat(titulosDaNota(notaSaida)).hasSize(1);
        assertThat(titulosDaNota(notaSaida).get(0).getValor()).isEqualByComparingTo("100.00");

        // edita o item pra dobrar o valor da nota — mesmo cenário real que gerou
        // cStat=851 (soma dos títulos divergindo de NotaSaida.valor)
        ItemNotaSaida item = carregarItemDaNota(notaSaida);
        item.setValorUnitario(new BigDecimal("20"));
        dataManager.save(item);
        NotaSaida notaAtualizada = dataManager.load(NotaSaida.class).id(notaSaida.getId()).one();
        assertThat(notaAtualizada.getValor()).isEqualByComparingTo("200.00");

        String erro = tituloReceberService.gerarTitulosDaEmissao(notaAtualizada);

        assertThat(erro).isNull();
        List<TituloReceber> titulos = titulosDaNota(notaAtualizada);
        assertThat(titulos).hasSize(1);
        assertThat(titulos.get(0).getValor()).isEqualByComparingTo("200.00");
    }

    @Test
    void naoRegeneraQuandoItemJaContabilizado() {
        Parceiro parceiro = criarParceiro();
        Banco banco = criarBanco();
        CondicaoPagamento condicaoPagamento = criarCondicaoPagamento(1, 10, 0);
        NotaSaida notaSaida = criarNotaSaidaComItem(condicaoPagamento, banco, parceiro,
                new BigDecimal("10"), new BigDecimal("10")); // valor 100.00
        tituloReceberService.gerarTitulosDaEmissao(notaSaida);

        TituloReceber titulo = titulosDaNota(notaSaida).get(0);
        ItemReceber itemReceber = dataManager.load(ItemReceber.class)
                .query("select e from ItemReceber e where e.tituloReceber = :titulo")
                .parameter("titulo", titulo)
                .one();
        itemReceber.setContabilizado(true);
        dataManager.save(itemReceber);

        ItemNotaSaida item = carregarItemDaNota(notaSaida);
        item.setValorUnitario(new BigDecimal("20"));
        dataManager.save(item);
        NotaSaida notaAtualizada = dataManager.load(NotaSaida.class).id(notaSaida.getId()).one();

        String erro = tituloReceberService.gerarTitulosDaEmissao(notaAtualizada);

        assertThat(erro).isNotNull();
        List<TituloReceber> titulos = titulosDaNota(notaAtualizada);
        assertThat(titulos).hasSize(1);
        assertThat(titulos.get(0).getValor()).isEqualByComparingTo("100.00"); // não mexeu
    }

    @Test
    void semCondicaoPagamentoNaoGeraTituloNemDaErro() {
        Parceiro parceiro = criarParceiro();
        Banco banco = criarBanco();
        NotaSaida notaSaida = criarNotaSaidaComItem(null, banco, parceiro,
                new BigDecimal("10"), new BigDecimal("10"));

        String erro = tituloReceberService.gerarTitulosDaEmissao(notaSaida);

        assertThat(erro).isNull();
        assertThat(titulosDaNota(notaSaida)).isEmpty();
    }

    @Test
    void comCondicaoPagamentoMasSemBancoRetornaErro() {
        Parceiro parceiro = criarParceiro();
        CondicaoPagamento condicaoPagamento = criarCondicaoPagamento(1, 10, 0);
        NotaSaida notaSaida = criarNotaSaidaComItem(condicaoPagamento, null, parceiro,
                new BigDecimal("10"), new BigDecimal("10"));

        String erro = tituloReceberService.gerarTitulosDaEmissao(notaSaida);

        assertThat(erro).isNotNull();
        assertThat(titulosDaNota(notaSaida)).isEmpty();
    }

    @Test
    void empresaComNumTitAltUsaSequenciaPropriaEmVezDoNumeroDaNota() {
        Empresa empresa = dataManager.create(Empresa.class);
        empresa.setCodigo(codEmpresa);
        empresa.setNome("Empresa de teste");
        empresa.setApelido("Teste");
        empresa.setNumTitAlt(true);
        dataManager.save(empresa);

        // consome o primeiro valor da sequence "numerotitulo<codEmpresa>" antes de gerar o
        // título — sem isso, ambas as sequences (a da nota e a alternativa) partiriam de 1
        // pra uma empresa nova neste teste e o número coincidiria por acaso, mascarando um
        // bug em que gerarTitulosDaEmissao ignorasse Empresa.numTitAlt.
        sequences.createNextValue(Sequence.withName("numerotitulo" + codEmpresa));

        Parceiro parceiro = criarParceiro();
        Banco banco = criarBanco();
        CondicaoPagamento condicaoPagamento = criarCondicaoPagamento(1, 10, 0);
        NotaSaida notaSaida = criarNotaSaidaComItem(condicaoPagamento, banco, parceiro,
                new BigDecimal("10"), new BigDecimal("10"));

        String erro = tituloReceberService.gerarTitulosDaEmissao(notaSaida);
        assertThat(erro).isNull();

        TituloReceber titulo = titulosDaNota(notaSaida).get(0);
        assertThat(titulo.getNumero()).isEqualTo("000002");
        assertThat(titulo.getNumero()).isNotEqualTo(String.format("%06d", notaSaida.getNumero()));
    }
}
