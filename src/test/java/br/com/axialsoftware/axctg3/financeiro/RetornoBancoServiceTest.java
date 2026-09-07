package br.com.axialsoftware.axctg3.financeiro;

import br.com.axialsoftware.axctg3.entity.User;
import br.com.axialsoftware.axctg3.entity.cadastros.Empresa;
import br.com.axialsoftware.axctg3.entity.cadastros.Parceiro;
import br.com.axialsoftware.axctg3.entity.financeiro.Banco;
import br.com.axialsoftware.axctg3.entity.financeiro.HistoricoFinanceiro;
import br.com.axialsoftware.axctg3.entity.financeiro.ItemReceber;
import br.com.axialsoftware.axctg3.entity.financeiro.RetornoBanco;
import br.com.axialsoftware.axctg3.entity.financeiro.TituloReceber;
import br.com.axialsoftware.axctg3.service.financeiro.BancoCobrancaHandler;
import br.com.axialsoftware.axctg3.service.financeiro.RetornoBancoService;
import br.com.axialsoftware.axctg3.service.financeiro.RetornoDetalheLido;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Cobre a orquestração bank-agnostic de {@link RetornoBancoService} (regra de negócio por
 * ocorrência, idempotência) — a leitura do CNAB em si já é testada em
 * {@code SicrediCnab400HandlerTest}. Aqui o {@link BancoCobrancaHandler} é um dublê que
 * devolve {@link RetornoDetalheLido} controlados, sem precisar montar bytes CNAB reais.
 */
@SpringBootTest
@ExtendWith(AuthenticatedAsAdmin.class)
@ActiveProfiles("test")
class RetornoBancoServiceTest {

    private static final int COD_EMPRESA = 9104;
    private static final int ANO = 2099;
    private static final int MES = 8;

    @Autowired
    DataManager dataManager;

    @Autowired
    CurrentAuthentication currentAuthentication;

    @Autowired
    RetornoBancoService retornoBancoService;

    @MockitoBean
    BancoCobrancaHandler bancoCobrancaHandler;

    private Parceiro parceiro;
    private Banco bancoSicredi;

    @BeforeEach
    void setUp() {
        when(bancoCobrancaHandler.getCodGeralSuportado()).thenReturn(748);

        User admin = (User) currentAuthentication.getUser();
        admin.setCodEmpresa(COD_EMPRESA);
        admin.setAnoContabil(ANO);
        admin.setMesContabil(MES);

        limparDadosDaEmpresa();

        HistoricoFinanceiro histEmissao = criarHistoricoFinanceiro(1, "Emissão de teste", true);
        HistoricoFinanceiro histBaixa = criarHistoricoFinanceiro(2, "Baixa de teste", false);

        Empresa empresa = dataManager.create(Empresa.class);
        empresa.setCodigo(COD_EMPRESA);
        empresa.setNome("Empresa de teste");
        empresa.setApelido("Teste");
        empresa.setCnpj("00000000000191");
        empresa.setHistFinBaixaReceber(histBaixa);
        dataManager.save(empresa);

        parceiro = dataManager.create(Parceiro.class);
        parceiro.setCodigo(9401L);
        parceiro.setCodEmpresa(COD_EMPRESA);
        parceiro.setNome("Cliente de teste");
        parceiro.setApelido("Cliente");
        parceiro.setCnpj("00000000000191");
        parceiro.setCliente(true);
        parceiro = dataManager.save(parceiro);

        bancoSicredi = dataManager.create(Banco.class);
        bancoSicredi.setCodigo(9401);
        bancoSicredi.setCodEmpresa(COD_EMPRESA);
        bancoSicredi.setNome("Sicredi");
        bancoSicredi.setCodGeral(748);
        bancoSicredi = dataManager.save(bancoSicredi);
    }

    @Test
    void test_ocorrenciaLiquidacaoCriaBaixaQuandoTituloAberto() {
        TituloReceber titulo = criarTitulo("0000001", new BigDecimal("500.00"));
        RetornoDetalheLido liquidacao = new RetornoDetalheLido("0000001", "250225200001234", "06",
                LocalDate.of(ANO, MES, 15), titulo.getDataVencimento(), new BigDecimal("500.00"),
                new BigDecimal("500.00"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, "");
        when(bancoCobrancaHandler.lerRetorno(any())).thenReturn(List.of(liquidacao));

        RetornoBanco retornoBanco = retornoBancoService.processarRetorno(bancoSicredi, new byte[0], "retorno.txt");

        assertThat(retornoBanco.getQuantidadeBaixados()).isEqualTo(1);
        List<ItemReceber> itens = carregarItens(titulo);
        assertThat(itens).hasSize(2); // item 1 (emissão) + item 2 (baixa)
        ItemReceber baixa = itens.get(1);
        assertThat(baixa.getItem()).isEqualTo(2);
        assertThat(baixa.getValor()).isEqualByComparingTo("500.00");
        assertThat(baixa.getContabilizado()).isFalse();
    }

    @Test
    void test_reprocessarMesmoRetornoNaoDuplicaBaixa() {
        TituloReceber titulo = criarTitulo("0000002", new BigDecimal("500.00"));
        RetornoDetalheLido liquidacao = new RetornoDetalheLido("0000002", "250225200001235", "06",
                LocalDate.of(ANO, MES, 15), titulo.getDataVencimento(), new BigDecimal("500.00"),
                new BigDecimal("500.00"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, "");
        when(bancoCobrancaHandler.lerRetorno(any())).thenReturn(List.of(liquidacao));

        retornoBancoService.processarRetorno(bancoSicredi, new byte[0], "retorno1.txt");
        RetornoBanco segundoProcessamento = retornoBancoService.processarRetorno(bancoSicredi, new byte[0], "retorno2.txt");

        assertThat(segundoProcessamento.getQuantidadeBaixados()).isEqualTo(0);
        assertThat(carregarItens(titulo)).hasSize(2); // não duplicou
    }

    @Test
    void test_ocorrenciaConfirmacaoGravaNumBancoSeAindaNaoGravado() {
        TituloReceber titulo = criarTitulo("0000003", new BigDecimal("500.00"));
        RetornoDetalheLido confirmacao = new RetornoDetalheLido("0000003", "250225200001236", "02",
                LocalDate.of(ANO, MES, 11), titulo.getDataVencimento(), new BigDecimal("500.00"),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, "");
        when(bancoCobrancaHandler.lerRetorno(any())).thenReturn(List.of(confirmacao));

        RetornoBanco retornoBanco = retornoBancoService.processarRetorno(bancoSicredi, new byte[0], "retorno.txt");

        assertThat(retornoBanco.getQuantidadeConfirmados()).isEqualTo(1);
        TituloReceber recarregado = dataManager.load(TituloReceber.class).id(titulo.getId()).one();
        assertThat(recarregado.getNumBanco()).isEqualTo("250225200001236");
    }

    @Test
    void test_seuNumeroNaoEncontradoIncrementaNaoEncontrados() {
        RetornoDetalheLido semTitulo = new RetornoDetalheLido("9999999", "250225200001237", "02",
                LocalDate.of(ANO, MES, 11), null, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, "");
        when(bancoCobrancaHandler.lerRetorno(any())).thenReturn(List.of(semTitulo));

        RetornoBanco retornoBanco = retornoBancoService.processarRetorno(bancoSicredi, new byte[0], "retorno.txt");

        assertThat(retornoBanco.getQuantidadeNaoEncontrados()).isEqualTo(1);
        assertThat(retornoBanco.getQuantidadeConfirmados()).isEqualTo(0);
    }

    @Test
    void test_ocorrenciaRejeicaoIncrementaRejeitados() {
        TituloReceber titulo = criarTitulo("0000004", new BigDecimal("500.00"));
        RetornoDetalheLido rejeicao = new RetornoDetalheLido("0000004", "", "03",
                LocalDate.of(ANO, MES, 11), titulo.getDataVencimento(), new BigDecimal("500.00"),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, "48");
        when(bancoCobrancaHandler.lerRetorno(any())).thenReturn(List.of(rejeicao));

        RetornoBanco retornoBanco = retornoBancoService.processarRetorno(bancoSicredi, new byte[0], "retorno.txt");

        assertThat(retornoBanco.getQuantidadeRejeitados()).isEqualTo(1);
        assertThat(carregarItens(titulo)).hasSize(1); // só o item de emissão, sem baixa
    }

    @AfterEach
    void tearDown() {
        limparDadosDaEmpresa();
    }

    private TituloReceber criarTitulo(String numero, BigDecimal valor) {
        TituloReceber tituloReceber = dataManager.create(TituloReceber.class);
        tituloReceber.setNumero(numero);
        tituloReceber.setDataEmissao(LocalDate.of(ANO, MES, 10));
        tituloReceber.setDataVencimento(LocalDate.of(ANO, MES, 20));
        tituloReceber.setParceiro(parceiro);
        tituloReceber.setBanco(bancoSicredi);
        tituloReceber.setValor(valor);
        return dataManager.save(tituloReceber);
    }

    private List<ItemReceber> carregarItens(TituloReceber tituloReceber) {
        return dataManager.load(ItemReceber.class)
                .query("select e from ItemReceber e where e.tituloReceber = :tituloReceber order by e.item")
                .parameter("tituloReceber", tituloReceber)
                .list();
    }

    private HistoricoFinanceiro criarHistoricoFinanceiro(int codigo, String nome, boolean emissao) {
        HistoricoFinanceiro historico = dataManager.create(HistoricoFinanceiro.class);
        historico.setCodigo(codigo);
        historico.setCodEmpresa(COD_EMPRESA);
        historico.setNome(nome);
        historico.setEmissao(emissao);
        historico.setBaixa(!emissao);
        return dataManager.save(historico);
    }

    private void limparDadosDaEmpresa() {
        apagar(carregar(ItemReceber.class, "select e from ItemReceber e where e.tituloReceber.codEmpresa = :codEmpresa"));
        apagar(carregar(TituloReceber.class, "select e from TituloReceber e where e.codEmpresa = :codEmpresa"));
        apagar(carregar(RetornoBanco.class, "select e from RetornoBanco e where e.codEmpresa = :codEmpresa"));
        apagar(carregar(Banco.class, "select e from Banco e where e.codEmpresa = :codEmpresa"));
        apagar(carregar(Empresa.class, "select e from Empresa e where e.codigo = :codEmpresa"));
        apagar(carregar(HistoricoFinanceiro.class, "select e from HistoricoFinanceiro e where e.codEmpresa = :codEmpresa"));
        apagar(carregar(Parceiro.class, "select e from Parceiro e where e.codEmpresa = :codEmpresa"));
    }

    private <E> List<E> carregar(Class<E> entityClass, String query) {
        return dataManager.load(entityClass)
                .query(query)
                .parameter("codEmpresa", COD_EMPRESA)
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
