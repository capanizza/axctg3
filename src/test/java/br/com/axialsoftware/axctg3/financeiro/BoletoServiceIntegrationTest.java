package br.com.axialsoftware.axctg3.financeiro;

import br.com.axialsoftware.axctg3.entity.User;
import br.com.axialsoftware.axctg3.entity.cadastros.Empresa;
import br.com.axialsoftware.axctg3.entity.cadastros.Parceiro;
import br.com.axialsoftware.axctg3.entity.financeiro.Banco;
import br.com.axialsoftware.axctg3.entity.financeiro.HistoricoFinanceiro;
import br.com.axialsoftware.axctg3.entity.financeiro.TituloReceber;
import br.com.axialsoftware.axctg3.service.financeiro.BoletoService;
import br.com.axialsoftware.axctg3.test_support.AuthenticatedAsAdmin;
import io.jmix.core.DataManager;
import io.jmix.core.SaveContext;
import io.jmix.core.security.CurrentAuthentication;
import io.jmix.data.PersistenceHints;
import io.jmix.flowui.download.DownloadFormat;
import io.jmix.flowui.download.Downloader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

/**
 * Cobre {@link BoletoService} ponta a ponta contra o template real {@code boleto748.jasper}
 * — a composição do código de barras em si já é testada em
 * {@code SicrediCnab400HandlerTest}, e a formatação da linha digitável em
 * {@code BoletoServiceTest} (unitário, sem Spring). {@link Downloader} é substituído por
 * dublê porque não há UI Vaadin ativa no teste (mesmo padrão de {@code NfeDanfeServiceTest}/
 * {@code RemessaBancoServiceTest}) — prova que {@code JasperFillManager.fillReport} +
 * {@code JasperExportManager.exportReportToPdf} rodam sem exceção contra o
 * {@code boleto748.jrxml} de verdade, não só contra a fórmula do código de barras.
 */
@SpringBootTest
@ExtendWith(AuthenticatedAsAdmin.class)
@ActiveProfiles("test")
class BoletoServiceIntegrationTest {

    private static final int COD_EMPRESA = 9108;

    @Autowired
    DataManager dataManager;

    @Autowired
    CurrentAuthentication currentAuthentication;

    @Autowired
    BoletoService boletoService;

    @MockitoBean
    Downloader downloader;

    private Parceiro parceiro;
    private Banco bancoSicredi;

    @BeforeEach
    void setUp() {
        User admin = (User) currentAuthentication.getUser();
        admin.setCodEmpresa(COD_EMPRESA);

        limparDadosDaEmpresa();

        Empresa empresa = dataManager.create(Empresa.class);
        empresa.setCodigo(COD_EMPRESA);
        empresa.setNome("Radio Cultura de Teste Ltda");
        empresa.setApelido("Teste");
        empresa.setCnpj("45624871000114");
        empresa.setLogradouro("Rua Cel. Osorio");
        empresa.setNumero("84");
        empresa.setBairro("Centro");
        empresa.setCep("12900150");
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
        bancoSicredi.setCodCedente("59622");
        bancoSicredi.setAgencia("0738");
        bancoSicredi.setPosto("33");
        bancoSicredi.setByteGeracaoNossoNumero(2);
        bancoSicredi.setLocalPagamento("Cooperativas de crédito do Sicredi"); // LOCAL_PAGAMENTO length=50
        bancoSicredi.setMensagem("Não receber após 10 dias do vencimento.");
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

    @AfterEach
    void tearDown() {
        limparDadosDaEmpresa();
    }

    @Test
    void test_emitirBoletosGeraPdfRealComNomeDoTitulo() {
        TituloReceber titulo = criarTitulo("0000191", new BigDecimal("1200.00"));
        titulo.setDataVencimento(LocalDate.of(2026, 3, 10));
        titulo.setNumBanco("262000458"); // normalmente preenchido por RemessaBancoService.gerarRemessa
        titulo = dataManager.save(titulo);

        boletoService.emitirBoletos(List.of(titulo));

        ArgumentCaptor<byte[]> pdfCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(downloader).download(pdfCaptor.capture(), eq("Boleto 0000191.pdf"), eq(DownloadFormat.PDF));
        byte[] pdf = pdfCaptor.getValue();
        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");
    }

    @Test
    void test_emitirBoletosVariosTitulosMesmoBancoGeraUmUnicoPdf() {
        TituloReceber titulo1 = criarTitulo("0000191", new BigDecimal("1200.00"));
        titulo1.setNumBanco("262000458");
        titulo1 = dataManager.save(titulo1);
        TituloReceber titulo2 = criarTitulo("0000193", new BigDecimal("550.00"));
        titulo2.setNumBanco("262000024");
        titulo2 = dataManager.save(titulo2);

        boletoService.emitirBoletos(List.of(titulo1, titulo2));

        verify(downloader).download(any(byte[].class), eq("Boletos.pdf"), eq(DownloadFormat.PDF));
    }

    @Test
    void test_emitirBoletosSemNumBancoLancaExcecao() {
        TituloReceber titulo = criarTitulo("0000001", BigDecimal.TEN);
        titulo = dataManager.save(titulo);

        List<TituloReceber> titulos = List.of(titulo);
        assertThatThrownBy(() -> boletoService.emitirBoletos(titulos))
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
        titulo.setNumBanco("000000001");
        titulo = dataManager.save(titulo);

        List<TituloReceber> titulos = List.of(titulo);
        assertThatThrownBy(() -> boletoService.emitirBoletos(titulos))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private TituloReceber criarTitulo(String numero, BigDecimal valor) {
        TituloReceber tituloReceber = dataManager.create(TituloReceber.class);
        tituloReceber.setNumero(numero);
        tituloReceber.setDataEmissao(LocalDate.of(2026, 3, 2));
        tituloReceber.setDataVencimento(LocalDate.of(2026, 3, 10));
        tituloReceber.setParceiro(parceiro);
        tituloReceber.setBanco(bancoSicredi);
        tituloReceber.setValor(valor);
        return dataManager.save(tituloReceber);
    }

    private void limparDadosDaEmpresa() {
        apagar(carregar(TituloReceber.class, "select e from TituloReceber e where e.codEmpresa = :codEmpresa"));
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
