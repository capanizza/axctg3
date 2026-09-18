package br.com.axialsoftware.axctg3.fiscal;

import br.com.axialsoftware.axctg3.entity.User;
import br.com.axialsoftware.axctg3.entity.cadastros.Empresa;
import br.com.axialsoftware.axctg3.entity.cadastros.Parceiro;
import br.com.axialsoftware.axctg3.entity.enums.AmbienteNfe;
import br.com.axialsoftware.axctg3.entity.enums.CodRegimeTributario;
import br.com.axialsoftware.axctg3.entity.fiscal.ItemNotaSaida;
import br.com.axialsoftware.axctg3.entity.fiscal.NaturezaOperacao;
import br.com.axialsoftware.axctg3.entity.fiscal.NotaSaida;
import br.com.axialsoftware.axctg3.entity.fiscal.Produto;
import br.com.axialsoftware.axctg3.entity.tabelas.ClassTrib;
import br.com.axialsoftware.axctg3.entity.tabelas.Municipio;
import br.com.axialsoftware.axctg3.service.fiscal.NfeDanfeService;
import br.com.axialsoftware.axctg3.test_support.AuthenticatedAsAdmin;
import io.jmix.core.DataManager;
import io.jmix.core.security.CurrentAuthentication;
import io.jmix.flowui.download.DownloadFormat;
import io.jmix.flowui.download.Downloader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Cobre {@link NfeDanfeService#emitirPreDanfe} — a "pré-DANFE" pedida pelo usuário pra
 * listar uma {@link NotaSaida} ANTES de emitir/assinar/enviar a NFe (sem chave confirmada,
 * sem protocolo). Reaproveita {@code NfeXmlBuilder.construir}/{@code NfeXmlParser.parse}
 * pra montar um {@code Nfe} transitório com os mesmos valores calculados (rateio de frete,
 * ICMS por item) que a emissão de verdade usaria — mesma técnica de prova de
 * {@link NfeDanfeServiceTest} (mock do {@link Downloader}, confere bytes de PDF real).
 */
@SpringBootTest
@ExtendWith(AuthenticatedAsAdmin.class)
@ActiveProfiles("test")
class NfeDanfePreDanfeTest {

    private static final int COD_EMPRESA = 9430;

    // Municipio/ClassTrib são tabelas globais (sem codEmpresa) — código literal fixo colide
    // com uma linha soft-deleted de uma rodada anterior (índice único não filtra
    // deleted_date no HSQLDB de teste); base variável por execução evita a colisão.
    private final int codigoMunicipioTeste = 9000000 + (int) (System.currentTimeMillis() % 900000);
    private final int classTribCodigoTeste = 9990000 + (int) (System.currentTimeMillis() % 9000);

    @Autowired
    private DataManager dataManager;

    @Autowired
    private CurrentAuthentication currentAuthentication;

    @Autowired
    private NfeDanfeService nfeDanfeService;

    @MockitoBean
    private Downloader downloader;

    private Municipio municipio;
    private ClassTrib classTrib;
    private Empresa empresa;
    private Parceiro parceiro;
    private NaturezaOperacao natureza;
    private Produto produto;

    private void prepararFixtures() {
        // UtilGeralService.getNomeEmpresa()/getLogoEmpresa() leem o codEmpresa da SESSÃO
        // (User.codEmpresa), não do NotaSaida/Empresa criados no teste — sem isso,
        // AuthenticatedAsAdmin deixa null e NfeDanfeService.montarParametrosPreDanfe
        // estoura NoResultException (ver CLAUDE.md sobre esse gotcha).
        User admin = (User) currentAuthentication.getUser();
        admin.setCodEmpresa(COD_EMPRESA);

        municipio = dataManager.create(Municipio.class);
        municipio.setCodigo(codigoMunicipioTeste);
        municipio.setNome("São Paulo");
        municipio.setUf("SP");
        dataManager.save(municipio);

        classTrib = dataManager.create(ClassTrib.class);
        classTrib.setCodigo(classTribCodigoTeste);
        classTrib.setCst(1);
        classTrib.setDescricao("ClassTrib de teste");
        classTrib.setTipoAliquota("Padrão");
        classTrib.setNomenclatura("Teste");
        classTrib.setDescricaoTratamentoTributario("Teste");
        dataManager.save(classTrib);

        empresa = dataManager.create(Empresa.class);
        empresa.setCodigo(COD_EMPRESA);
        empresa.setNome("Empresa de Teste");
        empresa.setApelido("Teste");
        empresa.setCnpj("12345678000190");
        empresa.setInscEst("ISENTO");
        empresa.setCrt(CodRegimeTributario.REGIME_NORMAL);
        empresa.setAmbienteNfe(AmbienteNfe.HOMOLOGACAO);
        empresa.setMunicipio(municipio);
        dataManager.save(empresa);

        parceiro = dataManager.create(Parceiro.class);
        parceiro.setCodigo(1L);
        parceiro.setCodEmpresa(COD_EMPRESA);
        parceiro.setNome("Cliente de Teste");
        parceiro.setApelido("Cliente Teste");
        parceiro.setCnpj("98765432000155");
        parceiro.setCliente(true);
        dataManager.save(parceiro);

        natureza = dataManager.create(NaturezaOperacao.class);
        natureza.setCodigo(1);
        natureza.setCodEmpresa(COD_EMPRESA);
        natureza.setNome("Venda de teste");
        natureza.setCfop(5102);
        dataManager.save(natureza);

        produto = dataManager.create(Produto.class);
        produto.setCodigo(1);
        produto.setCodEmpresa(COD_EMPRESA);
        produto.setDescricao("Produto de Teste");
        produto.setApelido("Teste");
        produto.setClassTrib(classTrib);
        dataManager.save(produto);
    }

    private NotaSaida criarNotaSaidaComItem() {
        NotaSaida notaSaida = dataManager.create(NotaSaida.class);
        notaSaida.setCodEmpresa(COD_EMPRESA);
        notaSaida.setDataEmissao(LocalDate.now());
        notaSaida.setDataSaida(LocalDate.now());
        notaSaida.setEspecie("NF");
        notaSaida.setSerie("1");
        notaSaida.setParceiro(parceiro);
        notaSaida.setNatureza(natureza);
        notaSaida = dataManager.save(notaSaida);

        ItemNotaSaida item = dataManager.create(ItemNotaSaida.class);
        item.setNotaSaida(notaSaida);
        item.setProduto(produto);
        item.setQuantidade(new BigDecimal("3"));
        item.setValorUnitario(new BigDecimal("10.50"));
        dataManager.save(item);

        return dataManager.load(NotaSaida.class).id(notaSaida.getId()).one();
    }

    @Test
    void emitirPreDanfeGeraPdfSemGravarChaveNemTitulo() {
        prepararFixtures();
        NotaSaida notaSaida = criarNotaSaidaComItem();

        NfeDanfeService.ResultadoPreDanfe resultado = nfeDanfeService.emitirPreDanfe(notaSaida.getId());

        assertThat(resultado.sucesso()).isTrue();
        ArgumentCaptor<byte[]> pdfCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(downloader).download(pdfCaptor.capture(), eq("PreDanfe_" + notaSaida.getNumero() + ".pdf"), eq(DownloadFormat.PDF));
        byte[] pdf = pdfCaptor.getValue();
        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");

        // "sem gravar, apenas listar" — a prévia não deve deixar rastro nenhum na nota.
        NotaSaida recarregada = dataManager.load(NotaSaida.class).id(notaSaida.getId()).one();
        assertThat(recarregada.getChave()).isNull();
        assertThat(recarregada.getChaveTentativa()).isNull();
    }

    @Test
    void emitirPreDanfeSemItensFalhaSemGerarPdf() {
        prepararFixtures();
        NotaSaida notaSaida = dataManager.create(NotaSaida.class);
        notaSaida.setCodEmpresa(COD_EMPRESA);
        notaSaida.setDataEmissao(LocalDate.now());
        notaSaida.setDataSaida(LocalDate.now());
        notaSaida.setEspecie("NF");
        notaSaida.setSerie("1");
        notaSaida.setParceiro(parceiro);
        notaSaida.setNatureza(natureza);
        notaSaida = dataManager.save(notaSaida);

        NfeDanfeService.ResultadoPreDanfe resultado = nfeDanfeService.emitirPreDanfe(notaSaida.getId());

        assertThat(resultado.sucesso()).isFalse();
        assertThat(resultado.motivo()).contains("sem itens");
        verify(downloader, never()).download(any(byte[].class), anyString(), eq(DownloadFormat.PDF));
    }

    /**
     * Hard delete em tudo, na ordem de dependência (filho antes do pai) — Municipio/
     * ClassTrib são tabelas globais sem índice único soft-delete-aware (mesmo raciocínio de
     * PedidoVendaUiTest.apagar/BemUiTest): um soft delete deixaria a FK viva (Empresa→
     * Municipio, Produto→ClassTrib) e o hard delete do pai estouraria violação de FK.
     */
    @AfterEach
    void tearDown() {
        apagarDeVerdade(dataManager.load(ItemNotaSaida.class)
                .query("select e from ItemNotaSaida e where e.notaSaida.codEmpresa = :codEmpresa")
                .parameter("codEmpresa", COD_EMPRESA)
                .list());
        apagarDeVerdade(dataManager.load(NotaSaida.class)
                .query("select e from NotaSaida e where e.codEmpresa = :codEmpresa")
                .parameter("codEmpresa", COD_EMPRESA)
                .list());
        apagarDeVerdade(dataManager.load(Produto.class)
                .query("select e from Produto e where e.codEmpresa = :codEmpresa")
                .parameter("codEmpresa", COD_EMPRESA)
                .list());
        apagarDeVerdade(dataManager.load(NaturezaOperacao.class)
                .query("select e from NaturezaOperacao e where e.codEmpresa = :codEmpresa")
                .parameter("codEmpresa", COD_EMPRESA)
                .list());
        apagarDeVerdade(dataManager.load(Parceiro.class)
                .query("select e from Parceiro e where e.codEmpresa = :codEmpresa")
                .parameter("codEmpresa", COD_EMPRESA)
                .list());
        apagarDeVerdade(dataManager.load(Empresa.class)
                .query("select e from Empresa e where e.codigo = :codigo")
                .parameter("codigo", COD_EMPRESA)
                .list());
        apagarDeVerdade(dataManager.load(Municipio.class)
                .query("select e from Municipio e where e.codigo = :codigo")
                .parameter("codigo", codigoMunicipioTeste)
                .list());
        apagarDeVerdade(dataManager.load(ClassTrib.class)
                .query("select e from ClassTrib e where e.codigo = :codigo")
                .parameter("codigo", classTribCodigoTeste)
                .list());
    }

    private void apagarDeVerdade(java.util.List<?> entidades) {
        if (entidades.isEmpty()) {
            return;
        }
        dataManager.save(new io.jmix.core.SaveContext()
                .setHint(io.jmix.data.PersistenceHints.SOFT_DELETION, false)
                .removing(entidades.toArray()));
    }
}
