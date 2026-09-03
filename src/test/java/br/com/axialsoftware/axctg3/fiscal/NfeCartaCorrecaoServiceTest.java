package br.com.axialsoftware.axctg3.fiscal;

import br.com.axialsoftware.axctg3.entity.fiscal.Nfe;
import br.com.axialsoftware.axctg3.entity.fiscal.NfeCartaCorrecao;
import br.com.axialsoftware.axctg3.service.fiscal.NfeCartaCorrecaoService;
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
 * Cobre só a validação de {@link NfeCartaCorrecaoService#corrigir} que roda ANTES de tocar
 * {@code Empresa}/certificado/SEFAZ (texto, {@code protCStat}, sequencial, limite de 20) —
 * mesmo limite de {@link NfeCancelamentoServiceTest}: montagem de XML, assinatura digital e
 * transmissão SOAP não têm teste automatizado neste projeto, só validação contra homologação
 * de verdade (docs/EMISSAO-NFE.md).
 */
@SpringBootTest
@ExtendWith(AuthenticatedAsAdmin.class)
@ActiveProfiles("test")
class NfeCartaCorrecaoServiceTest {

    private static final int COD_EMPRESA = 9109;
    private static final String CHAVE = "35240512345678000199550010000000031123456781";
    private static final String TEXTO_VALIDO = "Correção do bairro do destinatário na emissão original";

    @Autowired
    private DataManager dataManager;

    @Autowired
    private NfeCartaCorrecaoService nfeCartaCorrecaoService;

    @AfterEach
    void tearDown() {
        // NfeCartaCorrecao é composição de Nfe (@OnDelete CASCADE) — remover a Nfe já leva
        // as cartas de correção junto, mesmo padrão de NfeCancelamentoServiceTest.
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

    private void criarCartasCorrecao(Nfe nfe, int quantidade) {
        for (int i = 1; i <= quantidade; i++) {
            NfeCartaCorrecao cartaCorrecao = dataManager.create(NfeCartaCorrecao.class);
            cartaCorrecao.setNfe(nfe);
            cartaCorrecao.setNumeroSequencial(i);
            cartaCorrecao.setTextoCorrecao("Correção número " + i + " já registrada anteriormente");
            dataManager.save(cartaCorrecao);
        }
    }

    @Test
    void textoCurtoDemaisNaoCorrige() {
        Nfe nfe = criarNfe(100, "135260000000001");

        NfeCartaCorrecaoService.ResultadoCorrecao resultado =
                nfeCartaCorrecaoService.corrigir(nfe.getId(), "muito curto");

        assertThat(resultado.sucesso()).isFalse();
        assertThat(resultado.motivo()).contains("15 caracteres");
    }

    @Test
    void textoEmBrancoNaoCorrige() {
        Nfe nfe = criarNfe(100, "135260000000001");

        NfeCartaCorrecaoService.ResultadoCorrecao resultado =
                nfeCartaCorrecaoService.corrigir(nfe.getId(), "   ");

        assertThat(resultado.sucesso()).isFalse();
        assertThat(resultado.motivo()).contains("15 caracteres");
    }

    @Test
    void nfeNaoAutorizadaNaoCorrige() {
        // protCStat=101 simula uma NFe já cancelada — não pode receber CC-e.
        Nfe nfe = criarNfe(101, "135260000000001");

        NfeCartaCorrecaoService.ResultadoCorrecao resultado =
                nfeCartaCorrecaoService.corrigir(nfe.getId(), TEXTO_VALIDO);

        assertThat(resultado.sucesso()).isFalse();
        assertThat(resultado.motivo()).contains("cStat=101");
    }

    @Test
    void nfeSemProtCStatNaoCorrige() {
        Nfe nfe = criarNfe(null, null);

        NfeCartaCorrecaoService.ResultadoCorrecao resultado =
                nfeCartaCorrecaoService.corrigir(nfe.getId(), TEXTO_VALIDO);

        assertThat(resultado.sucesso()).isFalse();
        assertThat(resultado.motivo()).contains("cStat=vazio");
    }

    @Test
    void semEmpresaCadastradaNaoCorrige() {
        Nfe nfe = criarNfe(100, "135260000000001");

        NfeCartaCorrecaoService.ResultadoCorrecao resultado =
                nfeCartaCorrecaoService.corrigir(nfe.getId(), TEXTO_VALIDO);

        assertThat(resultado.sucesso()).isFalse();
        assertThat(resultado.motivo()).isEqualTo("Empresa não encontrada");
    }

    /**
     * Cria 5 CC-e's anteriores (sequencial 1 a 5, bem abaixo do limite de 20) e confirma que
     * o cálculo do próximo sequencial não trava o fluxo — chega até a checagem de Empresa
     * normalmente, mesma asserção de {@link #semEmpresaCadastradaNaoCorrige}.
     */
    @Test
    void sequencialContinuaNormalmenteComCorrecoesAnteriores() {
        Nfe nfe = criarNfe(100, "135260000000001");
        criarCartasCorrecao(nfe, 5);

        NfeCartaCorrecaoService.ResultadoCorrecao resultado =
                nfeCartaCorrecaoService.corrigir(nfe.getId(), TEXTO_VALIDO);

        assertThat(resultado.sucesso()).isFalse();
        assertThat(resultado.motivo()).isEqualTo("Empresa não encontrada");
    }

    /**
     * Cria as 20 CC-e's permitidas (sequencial 1 a 20) — a 21ª é barrada ANTES de checar
     * Empresa/SEFAZ. Se o cálculo do sequencial estivesse errado (ex.: sempre voltando 1), o
     * limite nunca dispararia e cairíamos em "Empresa não encontrada" — é essa diferença de
     * mensagem que confirma que o sequencial foi calculado corretamente.
     */
    @Test
    void limiteDeVinteCartasPorNfeBloqueiaSemChamarSefaz() {
        Nfe nfe = criarNfe(100, "135260000000001");
        criarCartasCorrecao(nfe, 20);

        NfeCartaCorrecaoService.ResultadoCorrecao resultado =
                nfeCartaCorrecaoService.corrigir(nfe.getId(), TEXTO_VALIDO);

        assertThat(resultado.sucesso()).isFalse();
        assertThat(resultado.motivo()).contains("Limite de 20");
    }

    @Test
    void chaveInexistenteNaoEncontraNfe() {
        NfeCartaCorrecaoService.ResultadoCorrecao resultado =
                nfeCartaCorrecaoService.corrigirPorChave("00000000000000000000000000000000000000000000", TEXTO_VALIDO);

        assertThat(resultado.sucesso()).isFalse();
        assertThat(resultado.motivo()).contains("Não foi encontrada");
    }

    @Test
    void nfeInexistentePorIdEstouraExcecaoDeCarregamento() {
        UUID idInexistente = UUID.randomUUID();
        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
                () -> nfeCartaCorrecaoService.corrigir(idInexistente, TEXTO_VALIDO));
    }
}
