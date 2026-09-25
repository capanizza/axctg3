package br.com.axialsoftware.axctg3.fiscal;

import br.com.axialsoftware.axctg3.Axctg3Application;
import br.com.axialsoftware.axctg3.entity.User;
import br.com.axialsoftware.axctg3.entity.cadastros.Empresa;
import br.com.axialsoftware.axctg3.entity.fiscal.Nfe;
import br.com.axialsoftware.axctg3.service.fiscal.NfeImportService;
import br.com.axialsoftware.axctg3.test_support.AdminUiTestAuthenticator;
import br.com.axialsoftware.axctg3.view.fiscal.nfe.NfeListView;
import io.jmix.core.DataManager;
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
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * "Baixar XML" de NfeListView: entrega o nfeProc gravado em {@code Nfe.xmlRetorno} como
 * {@code <chave>-nfe.xml}; NFe sem XML gravado (importada) não chama o Downloader.
 * Downloader mockado, mesmo padrão de {@link NfeDanfeServiceTest}.
 */
@UiTest(authenticator = AdminUiTestAuthenticator.class)
@SpringBootTest(classes = {Axctg3Application.class, FlowuiTestAssistConfiguration.class})
@ActiveProfiles("test")
class NfeListViewBaixarXmlUiTest {

    private static final int COD_EMPRESA = 9430;
    private static final String CHAVE = "35240512345678000199550010000000011123456789";

    @Autowired
    private DataManager dataManager;
    @Autowired
    private ViewNavigators viewNavigators;
    @Autowired
    private CurrentAuthentication currentAuthentication;
    @Autowired
    private NfeImportService nfeImportService;

    @MockitoBean
    private Downloader downloader;

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
    }

    @AfterEach
    void tearDown() {
        limparDados();
    }

    @Test
    void baixaONfeProcGravadoComNomeDaChave() throws IOException {
        byte[] xml = xmlAmostra();
        Nfe nfe = importar(xml);
        nfe.setXmlRetorno(new String(xml, StandardCharsets.UTF_8));
        dataManager.save(nfe);

        NfeListView view = abrirListaESelecionar();
        view.onNfesDataGridBaixarXmlAction(null);

        ArgumentCaptor<byte[]> captor = ArgumentCaptor.forClass(byte[].class);
        verify(downloader).download(captor.capture(), eq(CHAVE + "-nfe.xml"), eq(DownloadFormat.XML));
        assertThat(new String(captor.getValue(), StandardCharsets.UTF_8)).contains("<nfeProc");
    }

    @Test
    void nfeSemXmlGravadoNaoBaixa() throws IOException {
        importar(xmlAmostra()); // importada: xmlRetorno fica vazio

        NfeListView view = abrirListaESelecionar();
        view.onNfesDataGridBaixarXmlAction(null);

        verify(downloader, never()).download(any(byte[].class), any(String.class), any(DownloadFormat.class));
    }

    private NfeListView abrirListaESelecionar() {
        viewNavigators.view(UiTestUtils.getCurrentView(), NfeListView.class).navigate();
        NfeListView view = UiTestUtils.getCurrentView();
        DataGrid<Nfe> grid = UiTestUtils.getComponent(view, "nfesDataGrid");
        Nfe linha = grid.getItems().getItems().stream()
                .filter(n -> CHAVE.equals(n.getChave()))
                .findFirst()
                .orElseThrow();
        grid.select(linha);
        return view;
    }

    private Nfe importar(byte[] xml) {
        nfeImportService.importar("nfe_import_sample.xml", xml);
        return dataManager.load(Nfe.class)
                .query("select e from Nfe e where e.chave = :chave")
                .parameter("chave", CHAVE)
                .one();
    }

    private void limparDados() {
        dataManager.load(Nfe.class)
                .query("select e from Nfe e where e.chave = :chave")
                .parameter("chave", CHAVE)
                .list()
                .forEach(dataManager::remove);
        dataManager.load(Empresa.class)
                .query("select e from Empresa e where e.codigo = :codEmpresa")
                .parameter("codEmpresa", COD_EMPRESA)
                .list()
                .forEach(dataManager::remove);
    }

    private byte[] xmlAmostra() throws IOException {
        try (InputStream is = new ClassPathResource(
                "br/com/axialsoftware/axctg3/fiscal/nfe_import_sample.xml").getInputStream()) {
            return is.readAllBytes();
        }
    }
}
