package br.com.axialsoftware.axctg3.service.financeiro;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Teste puro (sem Spring) da formatação da linha digitável — padrão Febraban, comum a
 * qualquer banco, conferido na mão (módulo 10) contra os 2 boletos reais Sicredi em
 * {@code c:/remessa/748/202603/pdf}.
 */
class BoletoServiceTest {

    @Test
    void test_formatarLinhaDigitavel_boletoReal_000191() {
        String codigoBarras = "748" + "9" + "9" + "1381" + "0000120000" + "1126200045807383359622100";

        String linhaDigitavel = BoletoService.formatarLinhaDigitavel(codigoBarras);

        assertThat(linhaDigitavel).isEqualTo("74891.12628 00045.807385 33596.221003 9 13810000120000");
    }

    @Test
    void test_formatarLinhaDigitavel_boletoReal_000193() {
        String codigoBarras = "748" + "9" + "1" + "1381" + "0000055000" + "1126200002407383359622100";

        String linhaDigitavel = BoletoService.formatarLinhaDigitavel(codigoBarras);

        assertThat(linhaDigitavel).isEqualTo("74891.12628 00002.407385 33596.221003 1 13810000055000");
    }
}
