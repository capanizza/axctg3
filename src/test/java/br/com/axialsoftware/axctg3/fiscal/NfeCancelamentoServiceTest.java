package br.com.axialsoftware.axctg3.fiscal;

import br.com.axialsoftware.axctg3.entity.fiscal.Nfe;
import br.com.axialsoftware.axctg3.service.fiscal.NfeCancelamentoService;
import br.com.axialsoftware.axctg3.test_support.AuthenticatedAsAdmin;
import io.jmix.core.DataManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cobre só a validação de {@link NfeCancelamentoService#cancelar} que roda ANTES de tocar
 * {@code Empresa}/certificado/SEFAZ (justificativa, {@code protCStat}, {@code protNProt}) —
 * mesmo limite dos outros testes de emissão própria de NFe (ver
 * {@code NfeDanfeServiceTest}, {@code NfeChaveServiceTest}): montagem de XML, assinatura
 * digital e transmissão SOAP não têm teste automatizado neste projeto, só validação contra
 * homologação de verdade (docs/EMISSAO-NFE.md) — reproduzir isso aqui exigiria um
 * certificado PKCS12 de teste que o projeto não tem.
 */
@SpringBootTest
@ExtendWith(AuthenticatedAsAdmin.class)
@ActiveProfiles("test")
class NfeCancelamentoServiceTest {

    private static final int COD_EMPRESA = 9108;
    private static final String CHAVE = "35240512345678000199550010000000021123456780";

    @Autowired
    private DataManager dataManager;

    @Autowired
    private NfeCancelamentoService nfeCancelamentoService;

    @AfterEach
    void tearDown() {
        dataManager.load(Nfe.class)
                .query("select e from Nfe e where e.codEmpresa = :codEmpresa")
                .parameter("codEmpresa", COD_EMPRESA)
                .list()
                .forEach(dataManager::remove);
    }

    private Nfe criarNfe(Integer protCStat, String protNProt) {
        Nfe nfe = dataManager.create(Nfe.class);
        nfe.setChave(CHAVE);
        nfe.setCodEmpresa(COD_EMPRESA);
        nfe.setProtCStat(protCStat);
        nfe.setProtNProt(protNProt);
        return dataManager.save(nfe);
    }

    @Test
    void justificativaCurtaDemaisNaoCancela() {
        Nfe nfe = criarNfe(100, "135260000000001");

        NfeCancelamentoService.ResultadoCancelamento resultado =
                nfeCancelamentoService.cancelar(nfe.getId(), "muito curta");

        assertThat(resultado.sucesso()).isFalse();
        assertThat(resultado.motivo()).contains("15 caracteres");
    }

    @Test
    void justificativaNulaNaoCancela() {
        Nfe nfe = criarNfe(100, "135260000000001");

        NfeCancelamentoService.ResultadoCancelamento resultado =
                nfeCancelamentoService.cancelar(nfe.getId(), null);

        assertThat(resultado.sucesso()).isFalse();
        assertThat(resultado.motivo()).contains("15 caracteres");
    }

    @Test
    void nfeNaoAutorizadaNaoCancela() {
        // protCStat=101 simula uma NFe já cancelada anteriormente — não pode cancelar de novo.
        Nfe nfe = criarNfe(101, "135260000000001");

        NfeCancelamentoService.ResultadoCancelamento resultado =
                nfeCancelamentoService.cancelar(nfe.getId(), "Erro de digitação no valor do produto");

        assertThat(resultado.sucesso()).isFalse();
        assertThat(resultado.motivo()).contains("cStat=101");
    }

    @Test
    void nfeSemProtCStatNaoCancela() {
        Nfe nfe = criarNfe(null, null);

        NfeCancelamentoService.ResultadoCancelamento resultado =
                nfeCancelamentoService.cancelar(nfe.getId(), "Erro de digitação no valor do produto");

        assertThat(resultado.sucesso()).isFalse();
        assertThat(resultado.motivo()).contains("cStat=vazio");
    }

    @Test
    void nfeSemProtocoloDeAutorizacaoNaoCancela() {
        Nfe nfe = criarNfe(100, null);

        NfeCancelamentoService.ResultadoCancelamento resultado =
                nfeCancelamentoService.cancelar(nfe.getId(), "Erro de digitação no valor do produto");

        assertThat(resultado.sucesso()).isFalse();
        assertThat(resultado.motivo()).contains("protocolo de autorização");
    }

    @Test
    void chaveInexistenteNaoEncontraNfe() {
        NfeCancelamentoService.ResultadoCancelamento resultado =
                nfeCancelamentoService.cancelarPorChave("00000000000000000000000000000000000000000000", "Erro de digitação no valor do produto");

        assertThat(resultado.sucesso()).isFalse();
        assertThat(resultado.motivo()).contains("Não foi encontrada");
    }

    @Test
    void nfeInexistentePorIdEstouraExcecaoDeCarregamento() {
        // cancelar(UUID) é usado por NfeListView, onde o id sempre vem de uma linha
        // selecionada na grid — diferente de cancelarPorChave, não tem fallback de "não
        // encontrada" porque nesse caminho a Nfe já existe por construção.
        UUID idInexistente = UUID.randomUUID();
        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
                () -> nfeCancelamentoService.cancelar(idInexistente, "Erro de digitação no valor do produto"));
    }
}
