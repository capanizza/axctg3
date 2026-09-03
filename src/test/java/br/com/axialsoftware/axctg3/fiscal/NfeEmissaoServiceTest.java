package br.com.axialsoftware.axctg3.fiscal;

import br.com.axialsoftware.axctg3.entity.cadastros.Parceiro;
import br.com.axialsoftware.axctg3.entity.enums.FinNfe;
import br.com.axialsoftware.axctg3.entity.fiscal.NaturezaOperacao;
import br.com.axialsoftware.axctg3.entity.fiscal.NotaSaida;
import br.com.axialsoftware.axctg3.service.fiscal.NfeEmissaoService;
import br.com.axialsoftware.axctg3.test_support.AuthenticatedAsAdmin;
import io.jmix.core.DataManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cobre só a validação de finalidade ({@link NfeEmissaoService#emitir}) que roda ANTES de
 * tocar {@code Empresa}/certificado/SEFAZ — mesmo limite dos outros testes de emissão
 * própria de NFe (ver {@code NfeCancelamentoServiceTest}, {@code NfeCartaCorrecaoServiceTest}):
 * montagem de XML, assinatura digital e transmissão SOAP não têm teste automatizado neste
 * projeto, só validação contra homologação de verdade (docs/EMISSAO-NFE.md).
 */
@SpringBootTest
@ExtendWith(AuthenticatedAsAdmin.class)
@ActiveProfiles("test")
class NfeEmissaoServiceTest {

    private static final int COD_EMPRESA = 9110;
    private static final String CHAVE_ORIGINAL_VALIDA = "35240512345678000199550010000000041123456782";

    @Autowired
    private DataManager dataManager;

    @Autowired
    private NfeEmissaoService nfeEmissaoService;

    @AfterEach
    void tearDown() {
        dataManager.load(NotaSaida.class)
                .query("select e from NotaSaida e where e.codEmpresa = :codEmpresa")
                .parameter("codEmpresa", COD_EMPRESA)
                .list()
                .forEach(dataManager::remove);
        dataManager.load(NaturezaOperacao.class)
                .query("select e from NaturezaOperacao e where e.codEmpresa = :codEmpresa")
                .parameter("codEmpresa", COD_EMPRESA)
                .list()
                .forEach(dataManager::remove);
        dataManager.load(Parceiro.class)
                .query("select e from Parceiro e where e.codEmpresa = :codEmpresa")
                .parameter("codEmpresa", COD_EMPRESA)
                .list()
                .forEach(dataManager::remove);
    }

    private Parceiro criarParceiro() {
        Parceiro parceiro = dataManager.create(Parceiro.class);
        parceiro.setCodigo(1L);
        parceiro.setCodEmpresa(COD_EMPRESA);
        parceiro.setNome("Cliente de Teste");
        parceiro.setApelido("Cliente Teste");
        parceiro.setCnpj("12345678000190");
        return dataManager.save(parceiro);
    }

    private NaturezaOperacao criarNatureza() {
        NaturezaOperacao natureza = dataManager.create(NaturezaOperacao.class);
        natureza.setCodigo(1);
        natureza.setCodEmpresa(COD_EMPRESA);
        natureza.setNome("Venda de teste");
        natureza.setCfop(5102);
        return dataManager.save(natureza);
    }

    private NotaSaida criarNotaSaida(FinNfe finNfe, String chaveNotaOriginal) {
        NotaSaida notaSaida = dataManager.create(NotaSaida.class);
        notaSaida.setCodEmpresa(COD_EMPRESA);
        notaSaida.setDataEmissao(LocalDate.now());
        notaSaida.setDataSaida(LocalDate.now());
        notaSaida.setEspecie("NF");
        notaSaida.setSerie("1");
        notaSaida.setParceiro(criarParceiro());
        notaSaida.setNatureza(criarNatureza());
        if (finNfe != null) {
            notaSaida.setFinNfe(finNfe);
        }
        notaSaida.setChaveNotaOriginal(chaveNotaOriginal);
        return dataManager.save(notaSaida);
    }

    @Test
    void finalidadeComplementarSemChaveOriginalNaoEmite() {
        NotaSaida notaSaida = criarNotaSaida(FinNfe.COMPLEMENTAR, null);

        NfeEmissaoService.ResultadoEmissao resultado = nfeEmissaoService.emitir(notaSaida.getId());

        assertThat(resultado.sucesso()).isFalse();
        assertThat(resultado.motivo()).contains("complementar precisa referenciar");
    }

    @Test
    void finalidadeComplementarComChaveTamanhoErradoNaoEmite() {
        NotaSaida notaSaida = criarNotaSaida(FinNfe.COMPLEMENTAR, "12345");

        NfeEmissaoService.ResultadoEmissao resultado = nfeEmissaoService.emitir(notaSaida.getId());

        assertThat(resultado.sucesso()).isFalse();
        assertThat(resultado.motivo()).contains("complementar precisa referenciar");
    }

    @Test
    void finalidadeNormalComChaveOriginalPreenchidaNaoEmite() {
        NotaSaida notaSaida = criarNotaSaida(FinNfe.NORMAL, CHAVE_ORIGINAL_VALIDA);

        NfeEmissaoService.ResultadoEmissao resultado = nfeEmissaoService.emitir(notaSaida.getId());

        assertThat(resultado.sucesso()).isFalse();
        assertThat(resultado.motivo()).contains("mude a finalidade");
    }

    /**
     * Finalidade complementar com chave válida passa da validação e chega até a checagem
     * de Empresa (que falha por falta de cadastro de teste) — mesma técnica de
     * {@code NfeCancelamentoServiceTest}: a mensagem diferente confirma que o guard de
     * finalidade não bloqueou o fluxo.
     */
    @Test
    void finalidadeComplementarComChaveValidaPassaDaValidacao() {
        NotaSaida notaSaida = criarNotaSaida(FinNfe.COMPLEMENTAR, CHAVE_ORIGINAL_VALIDA);

        NfeEmissaoService.ResultadoEmissao resultado = nfeEmissaoService.emitir(notaSaida.getId());

        assertThat(resultado.sucesso()).isFalse();
        assertThat(resultado.motivo()).isEqualTo("Empresa não encontrada");
    }

    @Test
    void finalidadeNormalSemChaveOriginalPassaDaValidacao() {
        NotaSaida notaSaida = criarNotaSaida(null, null);

        NfeEmissaoService.ResultadoEmissao resultado = nfeEmissaoService.emitir(notaSaida.getId());

        assertThat(resultado.sucesso()).isFalse();
        assertThat(resultado.motivo()).isEqualTo("Empresa não encontrada");
    }
}
