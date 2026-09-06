package br.com.axialsoftware.axctg3.fiscal;

import br.com.axialsoftware.axctg3.entity.cadastros.Empresa;
import br.com.axialsoftware.axctg3.entity.cadastros.Parceiro;
import br.com.axialsoftware.axctg3.entity.enums.FinNfe;
import br.com.axialsoftware.axctg3.entity.fiscal.ItemNotaSaida;
import br.com.axialsoftware.axctg3.entity.fiscal.NaturezaOperacao;
import br.com.axialsoftware.axctg3.entity.fiscal.NotaSaida;
import br.com.axialsoftware.axctg3.entity.fiscal.Produto;
import br.com.axialsoftware.axctg3.entity.tabelas.ClassTrib;
import br.com.axialsoftware.axctg3.service.fiscal.NfeEmissaoService;
import br.com.axialsoftware.axctg3.test_support.AuthenticatedAsAdmin;
import io.jmix.core.DataManager;
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
 * Cobre só a validação de finalidade ({@link NfeEmissaoService#emitir}) e a geração do
 * "pseudo item" de uma NFe Complementar sem itens lançados manualmente
 * ({@link NfeEmissaoService} — método privado {@code gerarItemComplementar}, testado
 * indiretamente via o resultado persistido) que rodam ANTES de tocar certificado/SEFAZ —
 * mesmo limite dos outros testes de emissão própria de NFe (ver
 * {@code NfeCancelamentoServiceTest}, {@code NfeCartaCorrecaoServiceTest}): montagem de
 * XML, assinatura digital e transmissão SOAP não têm teste automatizado neste projeto, só
 * validação contra homologação de verdade (docs/EMISSAO-NFE.md).
 */
@SpringBootTest
@ExtendWith(AuthenticatedAsAdmin.class)
@ActiveProfiles("test")
class NfeEmissaoServiceTest {

    private static final int COD_EMPRESA = 9110;
    private static final String CHAVE_ORIGINAL_VALIDA = "35240512345678000199550010000000041123456782";
    private static final int CLASS_TRIB_CODIGO_TESTE = 9990001;

    @Autowired
    private DataManager dataManager;

    @Autowired
    private NfeEmissaoService nfeEmissaoService;

    @AfterEach
    void tearDown() {
        dataManager.load(NotaSaida.class)
                .query("select e from NotaSaida e where e.codEmpresa = :codEmpresa")
                .parameter("codEmpresa", COD_EMPRESA)
                .list()
                .forEach(dataManager::remove);
        dataManager.load(NaturezaOperacao.class)
                .query("select e from NaturezaOperacao e where e.codEmpresa = :codEmpresa")
                .parameter("codEmpresa", COD_EMPRESA)
                .list()
                .forEach(dataManager::remove);
        dataManager.load(Parceiro.class)
                .query("select e from Parceiro e where e.codEmpresa = :codEmpresa")
                .parameter("codEmpresa", COD_EMPRESA)
                .list()
                .forEach(dataManager::remove);
        dataManager.load(Produto.class)
                .query("select e from Produto e where e.codEmpresa = :codEmpresa")
                .parameter("codEmpresa", COD_EMPRESA)
                .list()
                .forEach(dataManager::remove);
        dataManager.load(Empresa.class)
                .query("select e from Empresa e where e.codigo = :codigo")
                .parameter("codigo", COD_EMPRESA)
                .list()
                .forEach(dataManager::remove);
        // ClassTrib é tabela global (sem codEmpresa) — limpa só o código reservado de teste.
        dataManager.load(ClassTrib.class)
                .query("select e from ClassTrib e where e.codigo = :codigo")
                .parameter("codigo", CLASS_TRIB_CODIGO_TESTE)
                .list()
                .forEach(dataManager::remove);
    }

    private Parceiro criarParceiro() {
        Parceiro parceiro = dataManager.create(Parceiro.class);
        parceiro.setCodigo(1L);
        parceiro.setCodEmpresa(COD_EMPRESA);
        parceiro.setNome("Cliente de Teste");
        parceiro.setApelido("Cliente Teste");
        parceiro.setCnpj("12345678000190");
        return dataManager.save(parceiro);
    }

    private NaturezaOperacao criarNatureza() {
        NaturezaOperacao natureza = dataManager.create(NaturezaOperacao.class);
        natureza.setCodigo(1);
        natureza.setCodEmpresa(COD_EMPRESA);
        natureza.setNome("Venda de teste");
        natureza.setCfop(5102);
        return dataManager.save(natureza);
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

    private Produto criarProdutoPlaceholder() {
        Produto produto = dataManager.create(Produto.class);
        produto.setCodigo(1);
        produto.setCodEmpresa(COD_EMPRESA);
        produto.setDescricao("Complemento de NFe");
        produto.setApelido("Complemento");
        produto.setClassTrib(criarClassTrib());
        return dataManager.save(produto);
    }

    private Empresa criarEmpresa(Produto produtoNfeComplementar) {
        Empresa empresa = dataManager.create(Empresa.class);
        empresa.setCodigo(COD_EMPRESA);
        empresa.setNome("Empresa de Teste");
        empresa.setApelido("Teste");
        empresa.setProdutoNfeComplementar(produtoNfeComplementar);
        return dataManager.save(empresa);
    }

    private NotaSaida criarNotaSaida(FinNfe finNfe, String chaveNotaOriginal) {
        return criarNotaSaida(finNfe, chaveNotaOriginal, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    private NotaSaida criarNotaSaida(FinNfe finNfe, String chaveNotaOriginal,
                                      BigDecimal valorMercadoria, BigDecimal baseIcms, BigDecimal valorIcms) {
        NotaSaida notaSaida = dataManager.create(NotaSaida.class);
        notaSaida.setCodEmpresa(COD_EMPRESA);
        notaSaida.setDataEmissao(LocalDate.now());
        notaSaida.setDataSaida(LocalDate.now());
        notaSaida.setEspecie("NF");
        notaSaida.setSerie("1");
        notaSaida.setParceiro(criarParceiro());
        notaSaida.setNatureza(criarNatureza());
        if (finNfe != null) {
            notaSaida.setFinNfe(finNfe);
        }
        notaSaida.setChaveNotaOriginal(chaveNotaOriginal);
        notaSaida.setValorMercadoria(valorMercadoria);
        notaSaida.setBaseIcms(baseIcms);
        notaSaida.setValorIcms(valorIcms);
        return dataManager.save(notaSaida);
    }

    private List<ItemNotaSaida> itensDaNota(NotaSaida notaSaida) {
        return dataManager.load(ItemNotaSaida.class)
                .query("select e from ItemNotaSaida e where e.notaSaida = :notaSaida")
                .parameter("notaSaida", notaSaida)
                .list();
    }

    @Test
    void finalidadeComplementarSemChaveOriginalNaoEmite() {
        NotaSaida notaSaida = criarNotaSaida(FinNfe.COMPLEMENTAR, null);

        NfeEmissaoService.ResultadoEmissao resultado = nfeEmissaoService.emitir(notaSaida.getId());

        assertThat(resultado.sucesso()).isFalse();
        assertThat(resultado.motivo()).contains("complementar precisa referenciar");
    }

    @Test
    void finalidadeComplementarComChaveTamanhoErradoNaoEmite() {
        NotaSaida notaSaida = criarNotaSaida(FinNfe.COMPLEMENTAR, "12345");

        NfeEmissaoService.ResultadoEmissao resultado = nfeEmissaoService.emitir(notaSaida.getId());

        assertThat(resultado.sucesso()).isFalse();
        assertThat(resultado.motivo()).contains("complementar precisa referenciar");
    }

    @Test
    void finalidadeNormalComChaveOriginalPreenchidaNaoEmite() {
        NotaSaida notaSaida = criarNotaSaida(FinNfe.NORMAL, CHAVE_ORIGINAL_VALIDA);

        NfeEmissaoService.ResultadoEmissao resultado = nfeEmissaoService.emitir(notaSaida.getId());

        assertThat(resultado.sucesso()).isFalse();
        assertThat(resultado.motivo()).contains("mude a finalidade");
    }

    /**
     * Finalidade complementar com chave válida passa da validação e chega até a checagem
     * de Empresa (que falha por falta de cadastro de teste) — mesma técnica de
     * {@code NfeCancelamentoServiceTest}: a mensagem diferente confirma que o guard de
     * finalidade não bloqueou o fluxo.
     */
    @Test
    void finalidadeComplementarComChaveValidaPassaDaValidacao() {
        NotaSaida notaSaida = criarNotaSaida(FinNfe.COMPLEMENTAR, CHAVE_ORIGINAL_VALIDA);

        NfeEmissaoService.ResultadoEmissao resultado = nfeEmissaoService.emitir(notaSaida.getId());

        assertThat(resultado.sucesso()).isFalse();
        assertThat(resultado.motivo()).isEqualTo("Empresa não encontrada");
    }

    @Test
    void finalidadeNormalSemChaveOriginalPassaDaValidacao() {
        NotaSaida notaSaida = criarNotaSaida(null, null);

        NfeEmissaoService.ResultadoEmissao resultado = nfeEmissaoService.emitir(notaSaida.getId());

        assertThat(resultado.sucesso()).isFalse();
        assertThat(resultado.motivo()).isEqualTo("Empresa não encontrada");
    }

    @Test
    void complementarSemItensESemProdutoConfiguradoNaoGeraItem() {
        criarEmpresa(null); // sem produtoNfeComplementar
        NotaSaida notaSaida = criarNotaSaida(FinNfe.COMPLEMENTAR, CHAVE_ORIGINAL_VALIDA,
                BigDecimal.ZERO, new BigDecimal("100.00"), new BigDecimal("18.00"));

        NfeEmissaoService.ResultadoEmissao resultado = nfeEmissaoService.emitir(notaSaida.getId());

        assertThat(resultado.sucesso()).isFalse();
        assertThat(resultado.motivo()).contains("configure um Produto padrão");
        assertThat(itensDaNota(notaSaida)).isEmpty();
    }

    /** Complemento só de imposto (valorMercadoria=0) — item nasce com quantidade=1/valor
     * zerado, nunca quantidade=0 (achado 2026-09-06: SEFAZ rejeita qCom=0 como "Falha no
     * Schema XML", mesmo com vUnCom/vProd zerados — ver [[axctg3-nfe-complementar-cstat225]]). */
    @Test
    void complementarSoDeIcmsGeraItemComQuantidadeUmEValorZerado() {
        Produto placeholder = criarProdutoPlaceholder();
        criarEmpresa(placeholder);
        NotaSaida notaSaida = criarNotaSaida(FinNfe.COMPLEMENTAR, CHAVE_ORIGINAL_VALIDA,
                BigDecimal.ZERO, new BigDecimal("100.00"), new BigDecimal("18.00"));

        nfeEmissaoService.emitir(notaSaida.getId());

        List<ItemNotaSaida> itens = itensDaNota(notaSaida);
        assertThat(itens).hasSize(1);
        ItemNotaSaida item = itens.get(0);
        assertThat(item.getProduto().getId()).isEqualTo(placeholder.getId());
        assertThat(item.getCfop()).isEqualTo(5102);
        assertThat(item.getQuantidade()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(item.getValorUnitario()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(item.getBaseIcms()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(item.getValorIcms()).isEqualByComparingTo(new BigDecimal("18.00"));
        assertThat(item.getAliqIcms()).isEqualByComparingTo(new BigDecimal("18.00"));
        assertThat(item.getCst()).isEqualTo("00");
        // 410029 "Operações acobertadas somente pelo ICMS" (CST 410, "Sem alíquota") — não
        // o classTrib "Padrão" genérico, que exige o grupo gIBSCBS preenchido com valores
        // reais (ver comentário em gerarItemComplementar).
        assertThat(item.getCodClassTrib()).isEqualTo(410029);
    }

    /** Complemento de ICMS-ST (baseSt/valorSt preenchidos no cabeçalho) — item nasce com
     * CST 10, não 00 (achado 2026-09-06, ver [[axctg3-nfe-complementar-cstat225]]). */
    @Test
    void complementarComBaseStGeraItemComCst10() {
        Produto placeholder = criarProdutoPlaceholder();
        criarEmpresa(placeholder);
        NotaSaida notaSaida = criarNotaSaida(FinNfe.COMPLEMENTAR, CHAVE_ORIGINAL_VALIDA,
                BigDecimal.ZERO, new BigDecimal("100.00"), new BigDecimal("18.00"));
        notaSaida.setBaseSt(new BigDecimal("50.00"));
        notaSaida.setValorSt(new BigDecimal("9.00"));
        dataManager.save(notaSaida);

        nfeEmissaoService.emitir(notaSaida.getId());

        List<ItemNotaSaida> itens = itensDaNota(notaSaida);
        assertThat(itens).hasSize(1);
        ItemNotaSaida item = itens.get(0);
        assertThat(item.getCst()).isEqualTo("10");
        assertThat(item.getBaseSt()).isEqualByComparingTo(new BigDecimal("50.00"));
        assertThat(item.getValorSt()).isEqualByComparingTo(new BigDecimal("9.00"));
        assertThat(item.getBaseIcms()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(item.getValorIcms()).isEqualByComparingTo(new BigDecimal("18.00"));
    }

    /** Complemento de preço (valorMercadoria != 0) — item nasce com quantidade=1/valorUnitario=diferença. */
    @Test
    void complementarComDiferencaDePrecoGeraItemComQuantidadeUm() {
        Produto placeholder = criarProdutoPlaceholder();
        criarEmpresa(placeholder);
        NotaSaida notaSaida = criarNotaSaida(FinNfe.COMPLEMENTAR, CHAVE_ORIGINAL_VALIDA,
                new BigDecimal("50.00"), BigDecimal.ZERO, BigDecimal.ZERO);

        nfeEmissaoService.emitir(notaSaida.getId());

        List<ItemNotaSaida> itens = itensDaNota(notaSaida);
        assertThat(itens).hasSize(1);
        ItemNotaSaida item = itens.get(0);
        assertThat(item.getQuantidade()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(item.getValorUnitario()).isEqualByComparingTo(new BigDecimal("50.00"));
    }

    @Test
    void nfeInexistentePorIdEstouraExcecaoDeCarregamento() {
        java.util.UUID idInexistente = java.util.UUID.randomUUID();
        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
                () -> nfeEmissaoService.emitir(idInexistente));
    }
}
