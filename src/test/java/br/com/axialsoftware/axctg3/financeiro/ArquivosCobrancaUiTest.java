package br.com.axialsoftware.axctg3.financeiro;

import br.com.axialsoftware.axctg3.Axctg3Application;
import br.com.axialsoftware.axctg3.entity.User;
import br.com.axialsoftware.axctg3.entity.cadastros.Empresa;
import br.com.axialsoftware.axctg3.entity.financeiro.Banco;
import br.com.axialsoftware.axctg3.entity.financeiro.RemessaBanco;
import br.com.axialsoftware.axctg3.test_support.AdminUiTestAuthenticator;
import br.com.axialsoftware.axctg3.view.financeiro.banco.BancoDetailView;
import br.com.axialsoftware.axctg3.view.financeiro.tituloreceber.RemessaBancoListView;
import br.com.axialsoftware.axctg3.view.financeiro.tituloreceber.TituloReceberListView;
import io.jmix.core.DataManager;
import io.jmix.core.FileRef;
import io.jmix.core.FileStorage;
import io.jmix.core.security.CurrentAuthentication;
import io.jmix.flowui.ViewNavigators;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.download.DownloadFormat;
import io.jmix.flowui.download.Downloader;
import io.jmix.flowui.testassist.FlowuiTestAssistConfiguration;
import io.jmix.flowui.testassist.UiTest;
import io.jmix.flowui.testassist.UiTestUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Telas mexidas quando remessa/boleto/SPED ECD deixaram de gravar em pasta do servidor:
 * "Baixar arquivo" de RemessaBanco.list entrega a cópia do FileStorage (e não chama o
 * Downloader pra remessa antiga, sem cópia); TituloReceber.list e Banco.detail abrem sem os
 * campos de caminho/pasta removidos. Downloader mockado, mesmo padrão de
 * {@code NfeListViewBaixarXmlUiTest}.
 */
@UiTest(authenticator = AdminUiTestAuthenticator.class)
@SpringBootTest(classes = {Axctg3Application.class, FlowuiTestAssistConfiguration.class})
@ActiveProfiles("test")
class ArquivosCobrancaUiTest {

    private static final int COD_EMPRESA = 9431;

    @Autowired
    private DataManager dataManager;
    @Autowired
    private ViewNavigators viewNavigators;
    @Autowired
    private CurrentAuthentication currentAuthentication;
    @Autowired
    private FileStorage fileStorage;

    @MockitoBean
    private Downloader downloader;

    private Banco banco;

    @BeforeEach
    void setUp() {
        User admin = (User) currentAuthentication.getUser();
        admin.setCodEmpresa(COD_EMPRESA);
        limparDados();

        Empresa empresa = dataManager.create(Empresa.class);
        empresa.setCodigo(COD_EMPRESA);
        empresa.setNome("Empresa de teste");
        empresa.setApelido("Teste");
        dataManager.save(empresa);

        banco = dataManager.create(Banco.class);
        banco.setCodigo(9431);
        banco.setCodEmpresa(COD_EMPRESA);
        banco.setNome("Sicredi");
        banco.setCodGeral(748);
        banco = dataManager.save(banco);
    }

    @AfterEach
    void tearDown() {
        limparDados();
    }

    @Test
    void baixarArquivoEntregaACopiaDoFileStorage() {
        FileRef arquivo = fileStorage.saveStream("00623A28.001",
                new ByteArrayInputStream("01REMESSA".getBytes(StandardCharsets.ISO_8859_1)));
        criarRemessa(1, arquivo);

        RemessaBancoListView view = abrirListaESelecionar(1);
        view.onRemessaBancosDataGridBaixarArquivoAction(null);

        verify(downloader).download(eq(arquivo), eq(DownloadFormat.OCTET_STREAM));
    }

    @Test
    void remessaSemCopiaNaoBaixa() {
        criarRemessa(2, null);

        RemessaBancoListView view = abrirListaESelecionar(2);
        view.onRemessaBancosDataGridBaixarArquivoAction(null);

        verify(downloader, never()).download(any(FileRef.class), any(DownloadFormat.class));
    }

    @Test
    void listaDeTitulosEDetalheDoBancoAbrem() {
        viewNavigators.view(UiTestUtils.getCurrentView(), TituloReceberListView.class).navigate();
        assertThat((Object) UiTestUtils.getCurrentView()).isInstanceOf(TituloReceberListView.class);

        viewNavigators.detailView(UiTestUtils.getCurrentView(), Banco.class)
                .editEntity(banco)
                .withViewClass(BancoDetailView.class)
                .navigate();
        assertThat((Object) UiTestUtils.getCurrentView()).isInstanceOf(BancoDetailView.class);
    }

    private void criarRemessa(int numero, FileRef arquivo) {
        RemessaBanco remessaBanco = dataManager.create(RemessaBanco.class);
        remessaBanco.setCodEmpresa(COD_EMPRESA);
        remessaBanco.setBanco(banco);
        remessaBanco.setDataGeracao(LocalDate.now());
        remessaBanco.setNumRemessa(numero);
        remessaBanco.setQuantidadeTitulos(1);
        remessaBanco.setValorTotal(BigDecimal.TEN);
        remessaBanco.setArquivo(arquivo);
        dataManager.save(remessaBanco);
    }

    private RemessaBancoListView abrirListaESelecionar(int numero) {
        viewNavigators.view(UiTestUtils.getCurrentView(), RemessaBancoListView.class).navigate();
        RemessaBancoListView view = UiTestUtils.getCurrentView();
        DataGrid<RemessaBanco> grid = UiTestUtils.getComponent(view, "remessaBancosDataGrid");
        RemessaBanco linha = grid.getItems().getItems().stream()
                .filter(r -> r.getNumRemessa() == numero)
                .findFirst()
                .orElseThrow();
        grid.select(linha);
        return view;
    }

    private void limparDados() {
        dataManager.load(RemessaBanco.class)
                .query("select e from RemessaBanco e where e.codEmpresa = :codEmpresa")
                .parameter("codEmpresa", COD_EMPRESA)
                .list()
                .forEach(remessa -> {
                    if (remessa.getArquivo() != null && fileStorage.fileExists(remessa.getArquivo())) {
                        fileStorage.removeFile(remessa.getArquivo());
                    }
                    dataManager.remove(remessa);
                });
        dataManager.load(Banco.class)
                .query("select e from Banco e where e.codEmpresa = :codEmpresa")
                .parameter("codEmpresa", COD_EMPRESA)
                .list()
                .forEach(dataManager::remove);
        dataManager.load(Empresa.class)
                .query("select e from Empresa e where e.codigo = :codEmpresa")
                .parameter("codEmpresa", COD_EMPRESA)
                .list()
                .forEach(dataManager::remove);
    }
}
