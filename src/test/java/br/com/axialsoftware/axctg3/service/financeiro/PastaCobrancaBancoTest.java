package br.com.axialsoftware.axctg3.service.financeiro;

import br.com.axialsoftware.axctg3.entity.financeiro.Banco;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Teste puro (sem Spring) de {@link PastaCobrancaBanco} — mesma pasta usada por
 * {@code RemessaBancoService} (arquivo de remessa) e {@code BoletoService} (subpasta
 * {@code pdf} dos boletos).
 */
class PastaCobrancaBancoTest {

    @Test
    void test_resolver_raizCodGeralComTresDigitosEAaaamm() {
        Banco banco = new Banco();
        banco.setPastaRemessa("c:/remessa");
        banco.setCodGeral(748);

        Path pasta = PastaCobrancaBanco.resolver(banco, LocalDate.of(2026, 3, 5));

        assertThat(pasta).isEqualTo(Path.of("c:/remessa", "748", "202603"));
    }

    @Test
    void test_resolver_pastaRemessaEmBrancoLancaExcecao() {
        Banco banco = new Banco();
        banco.setNome("Sicredi");
        banco.setCodGeral(748);

        assertThatThrownBy(() -> PastaCobrancaBanco.resolver(banco, LocalDate.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Sicredi");
    }
}
