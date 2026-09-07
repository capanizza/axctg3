package br.com.axialsoftware.axctg3.financeiro;

import br.com.axialsoftware.axctg3.entity.User;
import br.com.axialsoftware.axctg3.entity.cadastros.Empresa;
import br.com.axialsoftware.axctg3.entity.cadastros.Parceiro;
import br.com.axialsoftware.axctg3.entity.financeiro.Banco;
import br.com.axialsoftware.axctg3.entity.financeiro.HistoricoFinanceiro;
import br.com.axialsoftware.axctg3.entity.financeiro.RemessaBanco;
import br.com.axialsoftware.axctg3.entity.financeiro.TituloReceber;
import br.com.axialsoftware.axctg3.service.financeiro.RemessaBancoService;
import br.com.axialsoftware.axctg3.test_support.AuthenticatedAsAdmin;
import io.jmix.core.DataManager;
import io.jmix.core.SaveContext;
import io.jmix.core.security.CurrentAuthentication;
import io.jmix.data.PersistenceHints;
import io.jmix.flowui.download.Downloader;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Cobre a orquestração bank-agnostic de {@link RemessaBancoService} — a codificação CNAB
 * em si já é testada em {@code SicrediCnab400HandlerTest}. {@link Downloader} é
 * substituído por dublê porque não há UI Vaadin ativa no teste (mesmo padrão de
 * {@code NfeDanfeServiceTest}).
 */
@SpringBootTest
@ExtendWith(AuthenticatedAsAdmin.class)
@ActiveProfiles("test")
class RemessaBancoServiceTest {

    private static final int COD_EMPRESA = 9103;
    private static final int ANO = 2099;
    private static final int MES = 8;

    @Autowired
    DataManager dataManager;

    @Autowired
    CurrentAuthentication currentAuthentication;

    @Autowired
    RemessaBancoService remessaBancoService;

    @MockitoBean
    Downloader downloader;

    private Parceiro parceiro;
    private Banco bancoSicredi;

    @BeforeEach
    void setUp() {
        User admin = (User) currentAuthentication.getUser();
        admin.setCodEmpresa(COD_EMPRESA);
        admin.setAnoContabil(ANO);
        admin.setMesContabil(MES);

        limparDadosDaEmpresa();

        Empresa empresa = dataManager.create(Empresa.class);
        empresa.setCodigo(COD_EMPRESA);
        empresa.setNome("Empresa de teste");
        empresa.setApelido("Teste");
        empresa.setCnpj("00000000000191");
        dataManager.save(empresa);

        parceiro = dataManager.create(Parceiro.class);
        parceiro.setCodigo(9301L);
        parceiro.setCodEmpresa(COD_EMPRESA);
        parceiro.setNome("Cliente de teste");
        parceiro.setApelido("Cliente");
        parceiro.setCnpj("00000000000191");
        parceiro.setCliente(true);
        parceiro = dataManager.save(parceiro);

        bancoSicredi = dataManager.create(Banco.class);
        bancoSicredi.setCodigo(9301);
        bancoSicredi.setCodEmpresa(COD_EMPRESA);
        bancoSicredi.setNome("Sicredi");
        bancoSicredi.setCodGeral(748);
        bancoSicredi.setCodCedente("623");
        bancoSicredi.setAgencia("165");
        bancoSicredi.setPosto("2");
        bancoSicredi.setByteGeracaoNossoNumero(2);
        bancoSicredi = dataManager.save(bancoSicredi);

        // TituloReceberEventListener exige um HistoricoFinanceiro codigo=1 (emissão) pra
        // criar o item 1 automático de qualquer TituloReceber novo.
        HistoricoFinanceiro histEmissao = dataManager.create(HistoricoFinanceiro.class);
        histEmissao.setCodigo(1);
        histEmissao.setCodEmpresa(COD_EMPRESA);
        histEmissao.setNome("Emissão de teste");
        histEmissao.setEmissao(true);
        dataManager.save(histEmissao);
    }

    @Test
    void test_gerarRemessaIncrementaNumeroEGravaNosTitulos() {
        TituloReceber titulo1 = criarTitulo("0000001", new BigDecimal("500.00"));
        TituloReceber titulo2 = criarTitulo("0000002", new BigDecimal("300.50"));

        RemessaBanco remessaBanco = remessaBancoService.gerarRemessa(List.of(titulo1, titulo2));

        assertThat(remessaBanco.getNumRemessa()).isEqualTo(1);
        assertThat(remessaBanco.getQuantidadeTitulos()).isEqualTo(2);
        assertThat(remessaBanco.getValorTotal()).isEqualByComparingTo("800.50");

        TituloReceber titulo1Recarregado = recarregar(titulo1);
        assertThat(titulo1Recarregado.getNumRemessa()).isEqualTo(1);
        assertThat(titulo1Recarregado.getNumBanco()).isNotBlank();

        Banco bancoRecarregado = dataManager.load(Banco.class).id(bancoSicredi.getId()).one();
        assertThat(bancoRecarregado.getNumRemessa()).isEqualTo(1);
    }

    @Test
    void test_segundaRemessaIncrementaNumero() {
        remessaBancoService.gerarRemessa(List.of(criarTitulo("0000001", BigDecimal.TEN)));
        RemessaBanco segunda = remessaBancoService.gerarRemessa(List.of(criarTitulo("0000002", BigDecimal.ONE)));

        assertThat(segunda.getNumRemessa()).isEqualTo(2);
    }

    @Test
    void test_titulosDeBancosDiferentesLancaExcecao() {
        Banco outroBanco = dataManager.create(Banco.class);
        outroBanco.setCodigo(9302);
        outroBanco.setCodEmpresa(COD_EMPRESA);
        outroBanco.setNome("Outro banco");
        outroBanco.setCodGeral(341);
        outroBanco = dataManager.save(outroBanco);

        TituloReceber titulo1 = criarTitulo("0000001", BigDecimal.TEN);
        TituloReceber titulo2 = criarTitulo("0000002", BigDecimal.ONE);
        titulo2.setBanco(outroBanco);
        titulo2 = dataManager.save(titulo2);

        List<TituloReceber> titulos = List.of(titulo1, titulo2);
        assertThatThrownBy(() -> remessaBancoService.gerarRemessa(titulos))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void test_bancoSemHandlerLancaExcecao() {
        Banco bancoNaoSuportado = dataManager.create(Banco.class);
        bancoNaoSuportado.setCodigo(9303);
        bancoNaoSuportado.setCodEmpresa(COD_EMPRESA);
        bancoNaoSuportado.setNome("Banco não suportado");
        bancoNaoSuportado.setCodGeral(1); // Banco do Brasil — sem handler nesta fase
        bancoNaoSuportado = dataManager.save(bancoNaoSuportado);

        TituloReceber titulo = criarTitulo("0000001", BigDecimal.TEN);
        titulo.setBanco(bancoNaoSuportado);
        titulo = dataManager.save(titulo);

        List<TituloReceber> titulos = List.of(titulo);
        assertThatThrownBy(() -> remessaBancoService.gerarRemessa(titulos))
                .isInstanceOf(IllegalArgumentException.class);
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

    private TituloReceber recarregar(TituloReceber tituloReceber) {
        return dataManager.load(TituloReceber.class).id(tituloReceber.getId()).one();
    }

    private void limparDadosDaEmpresa() {
        apagar(carregar(TituloReceber.class, "select e from TituloReceber e where e.codEmpresa = :codEmpresa"));
        apagar(carregar(RemessaBanco.class, "select e from RemessaBanco e where e.codEmpresa = :codEmpresa"));
        apagar(carregar(Banco.class, "select e from Banco e where e.codEmpresa = :codEmpresa"));
        apagar(carregar(HistoricoFinanceiro.class, "select e from HistoricoFinanceiro e where e.codEmpresa = :codEmpresa"));
        apagar(carregar(Parceiro.class, "select e from Parceiro e where e.codEmpresa = :codEmpresa"));
        apagar(carregar(Empresa.class, "select e from Empresa e where e.codigo = :codEmpresa"));
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
