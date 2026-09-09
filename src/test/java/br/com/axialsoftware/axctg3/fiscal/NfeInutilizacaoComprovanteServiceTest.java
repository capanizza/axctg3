package br.com.axialsoftware.axctg3.fiscal;

import br.com.axialsoftware.axctg3.entity.User;
import br.com.axialsoftware.axctg3.entity.cadastros.Empresa;
import br.com.axialsoftware.axctg3.entity.enums.AmbienteNfe;
import br.com.axialsoftware.axctg3.entity.fiscal.NfeInutilizacao;
import br.com.axialsoftware.axctg3.service.fiscal.NfeInutilizacaoComprovanteService;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

/**
 * Cobre {@link NfeInutilizacaoComprovanteService} — mesmo raciocínio de prova de
 * {@link NfeDanfeServiceTest}: {@code RelatorioService.emitirRelatorio} engole qualquer
 * exceção em {@code log.error} (nenhuma sobe pra fora), então só "não lançou exceção" não
 * provaria que o {@code ComprovanteInutilizacao.jrxml} rodou de verdade — o {@link Downloader}
 * é mockado e o teste verifica que foi chamado com bytes de PDF reais.
 */
@SpringBootTest
@ExtendWith(AuthenticatedAsAdmin.class)
@ActiveProfiles("test")
class NfeInutilizacaoComprovanteServiceTest {

    private static final int COD_EMPRESA = 9107;

    @Autowired
    private DataManager dataManager;

    @Autowired
    private CurrentAuthentication currentAuthentication;

    @Autowired
    private NfeInutilizacaoComprovanteService nfeInutilizacaoComprovanteService;

    @MockitoBean
    private Downloader downloader;

    @BeforeEach
    void setUp() {
        User admin = (User) currentAuthentication.getUser();
        admin.setCodEmpresa(COD_EMPRESA);
        limparDadosDeTeste();

        Empresa empresa = dataManager.create(Empresa.class);
        empresa.setCodigo(COD_EMPRESA);
        empresa.setNome("Empresa de teste");
        empresa.setApelido("Teste");
        empresa.setCnpj("12345678000199");
        empresa.setAmbienteNfe(AmbienteNfe.HOMOLOGACAO);
        dataManager.save(empresa);
    }

    @AfterEach
    void tearDown() {
        limparDadosDeTeste();
    }

    private void limparDadosDeTeste() {
        dataManager.load(NfeInutilizacao.class)
                .query("select e from NfeInutilizacao e where e.codEmpresa = :codEmpresa")
                .parameter("codEmpresa", COD_EMPRESA)
                .list()
                .forEach(dataManager::remove);
        dataManager.load(Empresa.class)
                .query("select e from Empresa e where e.codigo = :codEmpresa")
                .parameter("codEmpresa", COD_EMPRESA)
                .list()
                .forEach(dataManager::remove);
    }

    private NfeInutilizacao criarInutilizacaoHomologada() {
        NfeInutilizacao inut = dataManager.create(NfeInutilizacao.class);
        inut.setCodEmpresa(COD_EMPRESA);
        inut.setAno(2026);
        inut.setModelo(55);
        inut.setSerie(1);
        inut.setNumeroInicial(100);
        inut.setNumeroFinal(105);
        inut.setJustificativa("Erro de sequência na numeração das notas emitidas.");
        inut.setRetCStat(102);
        inut.setRetXMotivo("Inutilização de número homologada");
        inut.setRetNProt("135260000123456");
        inut.setDhRecbto(OffsetDateTime.now());
        return dataManager.save(inut);
    }

    @Test
    void imprimirGeraPdfReal() {
        NfeInutilizacao inut = criarInutilizacaoHomologada();

        nfeInutilizacaoComprovanteService.imprimir(inut.getId());

        ArgumentCaptor<byte[]> pdfCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(downloader).download(pdfCaptor.capture(), eq("Inutilizacao_2026_1_100_105.pdf"), eq(DownloadFormat.PDF));
        byte[] pdf = pdfCaptor.getValue();
        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");
    }

    /**
     * Pedido rejeitado (nunca chegou a ser homologado) — sem protocolo/data de recebimento,
     * ainda assim tem que gerar o comprovante (registra a tentativa, não só o sucesso).
     */
    @Test
    void imprimirInutilizacaoRejeitadaGeraPdfReal() {
        NfeInutilizacao inut = dataManager.create(NfeInutilizacao.class);
        inut.setCodEmpresa(COD_EMPRESA);
        inut.setAno(2026);
        inut.setModelo(55);
        inut.setSerie(2);
        inut.setNumeroInicial(10);
        inut.setNumeroFinal(20);
        inut.setJustificativa("Erro de sequência na numeração das notas emitidas.");
        inut.setRetCStat(563);
        inut.setRetXMotivo("Rejeição: CNPJ do emitente não confere");
        dataManager.save(inut);

        nfeInutilizacaoComprovanteService.imprimir(inut.getId());

        ArgumentCaptor<byte[]> pdfCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(downloader).download(pdfCaptor.capture(), eq("Inutilizacao_2026_2_10_20.pdf"), eq(DownloadFormat.PDF));
        assertThat(new String(pdfCaptor.getValue(), 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");
    }
}
