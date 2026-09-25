package br.com.axialsoftware.axctg3.fiscal;

import br.com.axialsoftware.axctg3.entity.User;
import br.com.axialsoftware.axctg3.entity.cadastros.Empresa;
import br.com.axialsoftware.axctg3.entity.fiscal.Nfe;
import br.com.axialsoftware.axctg3.entity.fiscal.NfeCartaCorrecao;
import br.com.axialsoftware.axctg3.entity.fiscal.NfeInutilizacao;
import br.com.axialsoftware.axctg3.entity.fiscal.NfeItem;
import br.com.axialsoftware.axctg3.service.fiscal.NfeExportacaoContadorService;
import br.com.axialsoftware.axctg3.test_support.AuthenticatedAsAdmin;
import io.jmix.core.DataManager;
import io.jmix.core.security.CurrentAuthentication;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Zip de XMLs pro contador: só produção, só NFe emitidas pelo sistema (com nfeProc), cada
 * evento pela própria data, e o PDF de conferência gerado de verdade pelo Jasper.
 */
@SpringBootTest
@ExtendWith(AuthenticatedAsAdmin.class)
@ActiveProfiles("test")
class NfeExportacaoContadorServiceTest {

    private static final int COD_EMPRESA = 9440;
    private static final LocalDate INICIO = LocalDate.of(2026, 9, 1);
    private static final LocalDate FIM = LocalDate.of(2026, 9, 30);

    @Autowired
    private DataManager dataManager;
    @Autowired
    private CurrentAuthentication currentAuthentication;
    @Autowired
    private NfeExportacaoContadorService service;

    /** Chaves variáveis por execução: o índice único de CHAVE no HSQLDB de teste não ignora soft delete. */
    private long baseChave;

    @BeforeEach
    void setUp() {
        User admin = (User) currentAuthentication.getUser();
        admin.setCodEmpresa(COD_EMPRESA);
        limparDados();
        baseChave = System.currentTimeMillis() * 10;

        Empresa empresa = dataManager.create(Empresa.class);
        empresa.setCodigo(COD_EMPRESA);
        empresa.setNome("Empresa de teste");
        empresa.setApelido("Teste");
        empresa.setCnpj("12345678000199");
        dataManager.save(empresa);
    }

    @AfterEach
    void tearDown() {
        limparDados();
    }

    @Test
    void montaZipComSubpastasEFiltraAmbienteEPeriodo() throws IOException {
        Nfe autorizada = criarNfe(1, 1, 100, "2026-09-10T10:00:00-03:00", "<nfeProc>autorizada</nfeProc>");
        criarItem(autorizada, 5102);
        criarItem(autorizada, 5405);

        Nfe cancelada = criarNfe(2, 1, 101, "2026-09-10T11:00:00-03:00", "<nfeProc>cancelada</nfeProc>");
        cancelada.setCancCStat(135);
        cancelada.setCancDhRegEvento(OffsetDateTime.parse("2026-09-11T09:00:00-03:00"));
        cancelada.setCancXJust("Erro na digitação do valor");
        cancelada.setCancXmlRetorno("<procEventoNFe>cancelamento</procEventoNFe>");
        dataManager.save(cancelada);

        criarNfe(3, 2, 100, "2026-09-12T10:00:00-03:00", "<nfeProc>homologacao</nfeProc>"); // fora: homologação
        criarNfe(4, 1, 100, "2026-09-12T10:00:00-03:00", null);                            // fora: importada
        // fora pela data da nota (31/08 23:30 em Brasília = 01/09 em UTC), mas a CC-e é de setembro
        Nfe deAgosto = criarNfe(5, 1, 100, "2026-08-31T23:30:00-03:00", "<nfeProc>agosto</nfeProc>");
        criarCartaCorrecao(deAgosto, "2026-09-02T08:00:00-03:00");

        criarInutilizacao(1, 10, 12, "2026-09-05T10:00:00-03:00");
        criarInutilizacao(2, 20, 21, "2026-09-05T10:00:00-03:00"); // fora: homologação

        NfeExportacaoContadorService.Resultado resultado = service.exportar(INICIO, FIM, false);

        assertThat(resultado.nomeArquivo()).isEqualTo("NFe_12345678000199_2026-09.zip");
        assertThat(resultado.nfes()).isEqualTo(2);
        assertThat(resultado.canceladas()).isEqualTo(1);
        assertThat(resultado.cartasCorrecao()).isEqualTo(1);
        assertThat(resultado.inutilizacoes()).isEqualTo(1);

        Map<String, byte[]> entradas = lerZip(resultado.zip());
        assertThat(entradas.keySet()).containsExactlyInAnyOrder(
                "nfe/" + chave(1) + "-nfe.xml",
                "nfe/" + chave(2) + "-nfe.xml",
                "canceladas/" + chave(2) + "-can.xml",
                "cartas-correcao/" + chave(5) + "-cce-1.xml",
                "inutilizacoes/inut-26-1-10-12.xml",
                "Conferencia_2026-09.pdf");
        assertThat(new String(entradas.get("canceladas/" + chave(2) + "-can.xml"), StandardCharsets.UTF_8))
                .isEqualTo("<procEventoNFe>cancelamento</procEventoNFe>");
        assertThat(new String(entradas.get("Conferencia_2026-09.pdf"), 0, 4, StandardCharsets.US_ASCII))
                .isEqualTo("%PDF");
    }

    @Test
    void periodoSemNadaDevolveVazioSemZip() {
        criarNfe(1, 2, 100, "2026-09-10T10:00:00-03:00", "<nfeProc>homologacao</nfeProc>");

        NfeExportacaoContadorService.Resultado resultado = service.exportar(INICIO, FIM, false);

        assertThat(resultado.vazio()).isTrue();
        assertThat(resultado.zip()).isNull();
    }

    @Test
    void incluirHomologacaoTrazAsNotasEInutilizacoesDeTeste() {
        criarNfe(1, 2, 100, "2026-09-10T10:00:00-03:00", "<nfeProc>homologacao</nfeProc>");
        criarInutilizacao(2, 20, 21, "2026-09-05T10:00:00-03:00");

        NfeExportacaoContadorService.Resultado resultado = service.exportar(INICIO, FIM, true);

        assertThat(resultado.nfes()).isEqualTo(1);
        assertThat(resultado.inutilizacoes()).isEqualTo(1);
    }

    private String chave(int n) {
        return String.format("%044d", baseChave + n);
    }

    private Nfe criarNfe(int n, int tpAmb, int cStat, String dhEmi, String xmlRetorno) {
        Nfe nfe = dataManager.create(Nfe.class);
        nfe.setChave(chave(n));
        nfe.setCodEmpresa(COD_EMPRESA);
        nfe.setNumeroNf(1000 + n);
        nfe.setSerie(1);
        nfe.setDhEmi(OffsetDateTime.parse(dhEmi));
        nfe.setDestXNome("Cliente " + n);
        nfe.setValorNf(new BigDecimal("100.00"));
        nfe.setProtTpAmb(tpAmb);
        nfe.setProtCStat(cStat);
        nfe.setXmlRetorno(xmlRetorno);
        return dataManager.save(nfe);
    }

    private void criarItem(Nfe nfe, int cfop) {
        NfeItem item = dataManager.create(NfeItem.class);
        item.setNfe(nfe);
        item.setCfop(cfop);
        dataManager.save(item);
    }

    private void criarCartaCorrecao(Nfe nfe, String dhRegEvento) {
        NfeCartaCorrecao cce = dataManager.create(NfeCartaCorrecao.class);
        cce.setNfe(nfe);
        cce.setNumeroSequencial(1);
        cce.setTextoCorrecao("Corrige o endereço de entrega");
        cce.setCceCStat(135);
        cce.setCceDhRegEvento(OffsetDateTime.parse(dhRegEvento));
        cce.setCceXmlRetorno("<retEvento>cce</retEvento>");
        dataManager.save(cce);
    }

    private void criarInutilizacao(int tpAmb, int inicial, int fim, String dhRecbto) {
        NfeInutilizacao inut = dataManager.create(NfeInutilizacao.class);
        inut.setCodEmpresa(COD_EMPRESA);
        inut.setAno(26);
        inut.setSerie(1);
        inut.setNumeroInicial(inicial);
        inut.setNumeroFinal(fim);
        inut.setJustificativa("Numeração pulada por falha");
        inut.setRetCStat(102);
        inut.setRetNProt("135260000012345");
        inut.setDhRecbto(OffsetDateTime.parse(dhRecbto));
        inut.setXmlRetorno("<retInutNFe><infInut><tpAmb>" + tpAmb + "</tpAmb></infInut></retInutNFe>");
        dataManager.save(inut);
    }

    private static Map<String, byte[]> lerZip(byte[] zip) throws IOException {
        Map<String, byte[]> entradas = new LinkedHashMap<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip), StandardCharsets.UTF_8)) {
            ZipEntry entrada;
            while ((entrada = in.getNextEntry()) != null) {
                entradas.put(entrada.getName(), in.readAllBytes());
            }
        }
        return entradas;
    }

    private void limparDados() {
        dataManager.load(NfeCartaCorrecao.class)
                .query("select e from NfeCartaCorrecao e where e.nfe.codEmpresa = :codEmpresa")
                .parameter("codEmpresa", COD_EMPRESA)
                .list()
                .forEach(dataManager::remove);
        dataManager.load(Nfe.class)
                .query("select e from Nfe e where e.codEmpresa = :codEmpresa")
                .parameter("codEmpresa", COD_EMPRESA)
                .list()
                .forEach(dataManager::remove);
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
}
