package br.com.axialsoftware.axctg3.fiscal;

import br.com.axialsoftware.axctg3.entity.User;
import br.com.axialsoftware.axctg3.entity.cadastros.Empresa;
import br.com.axialsoftware.axctg3.entity.fiscal.Nfe;
import br.com.axialsoftware.axctg3.service.fiscal.NfeDanfeService;
import br.com.axialsoftware.axctg3.service.fiscal.NfeImportService;
import br.com.axialsoftware.axctg3.test_support.AuthenticatedAsAdmin;
import io.jmix.core.DataManager;
import io.jmix.core.security.CurrentAuthentication;
import io.jmix.flowui.download.DownloadFormat;
import io.jmix.flowui.download.Downloader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

/**
 * Cobre {@link NfeDanfeService} — emissão do DANFE a partir de uma {@link Nfe} já persistida
 * (aqui, importada via {@link NfeImportService} com o mesmo XML de amostra usado por
 * {@link NfeImportServiceTest}). Como {@link br.com.axialsoftware.axctg3.service.RelatorioService#emitirRelatorio}
 * engole qualquer exceção da emissão do PDF em {@code log.info} (nenhuma sobe pra fora), um
 * teste que só checasse "não lançou exceção" passaria mesmo se o {@code Danfe.jrxml} nunca
 * rodasse de fato — por isso o {@link Downloader} é substituído por um mock e o teste verifica
 * que ele foi chamado com bytes de PDF reais: é a prova de que {@code JasperFillManager.fillReport}
 * + {@code JasperExportManager.exportReportToPdf} rodaram sem exceção contra o template.
 */
@SpringBootTest
@ExtendWith(AuthenticatedAsAdmin.class)
@ActiveProfiles("test")
class NfeDanfeServiceTest {

    private static final int COD_EMPRESA = 9106;
    private static final String CHAVE = "35240512345678000199550010000000011123456789";

    @Autowired
    private DataManager dataManager;

    @Autowired
    private CurrentAuthentication currentAuthentication;

    @Autowired
    private NfeImportService nfeImportService;

    @Autowired
    private NfeDanfeService nfeDanfeService;

    @MockitoBean
    private Downloader downloader;

    @BeforeEach
    void setUp() {
        User admin = (User) currentAuthentication.getUser();
        admin.setCodEmpresa(COD_EMPRESA);
        limparDadosDeTeste();

        // UtilGeralService.getNomeEmpresa()/getLogoEmpresa() fazem .one() em Empresa pelo
        // codEmpresa da sessão — sem essa linha dá NoResultException antes do relatório rodar.
        Empresa empresa = dataManager.create(Empresa.class);
        empresa.setCodigo(COD_EMPRESA);
        empresa.setNome("Empresa de teste");
        empresa.setApelido("Teste");
        dataManager.save(empresa);
    }

    @AfterEach
    void tearDown() {
        limparDadosDeTeste();
    }

    private void limparDadosDeTeste() {
        dataManager.load(Nfe.class)
                .query("select e from Nfe e where e.codEmpresa = :codEmpresa")
                .parameter("codEmpresa", COD_EMPRESA)
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

    @Test
    void emitirDanfeGeraPdfRealAPartirDoId() throws IOException {
        nfeImportService.importar("nfe_import_sample.xml", xmlAmostra());
        Nfe nfe = dataManager.load(Nfe.class)
                .query("select e from Nfe e where e.chave = :chave")
                .parameter("chave", CHAVE)
                .one();

        nfeDanfeService.emitirDanfe(nfe.getId());

        ArgumentCaptor<byte[]> pdfCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(downloader).download(pdfCaptor.capture(), eq("Danfe_" + CHAVE + ".pdf"), eq(DownloadFormat.PDF));
        byte[] pdf = pdfCaptor.getValue();
        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");
    }

    @Test
    void emitirDanfePorChaveGeraPdfReal() throws IOException {
        nfeImportService.importar("nfe_import_sample.xml", xmlAmostra());

        nfeDanfeService.emitirDanfePorChave(CHAVE);

        ArgumentCaptor<byte[]> pdfCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(downloader).download(pdfCaptor.capture(), eq("Danfe_" + CHAVE + ".pdf"), eq(DownloadFormat.PDF));
        assertThat(new String(pdfCaptor.getValue(), 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");
    }
}
