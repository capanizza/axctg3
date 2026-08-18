package br.com.axialsoftware.axctg3.fiscal;

import br.com.axialsoftware.axctg3.service.fiscal.NfeChaveService;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cobre {@link NfeChaveService} — cálculo puro (sem I/O, sem Spring), então roda como teste
 * unitário simples, não {@code @SpringBootTest}. O dígito verificador de {@link
 * NfeChaveService#calcularDv(String)} é conferido contra um valor calculado à mão pra não
 * depender só de a implementação "concordar consigo mesma": string de 43 dígitos '1' — pesos
 * cíclicos 2..9 da direita pra esquerda somam 229 (5 ciclos completos de 44 + 9 dos 3 dígitos
 * finais), resto = 229 % 11 = 9, DV = 11 - 9 = 2.
 */
class NfeChaveServiceTest {

    private final NfeChaveService service = new NfeChaveService();

    @Test
    void test_calcularDv_contraValorCalculadoAMao() {
        String chave43 = "1".repeat(43);

        assertThat(service.calcularDv(chave43)).isEqualTo(2);
    }

    @Test
    void test_calcularDv_ehDeterministico() {
        String chave43 = "3521500123456000123455500010000000011234567";

        int dv1 = service.calcularDv(chave43);
        int dv2 = service.calcularDv(chave43);

        assertThat(dv1).isEqualTo(dv2);
        assertThat(dv1).isBetween(0, 9);
    }

    @Test
    void test_gerarChave_montaOsCamposNasPosicoesCorretas() {
        LocalDate dataEmissao = LocalDate.of(2026, 8, 17);
        String chave = service.gerarChave(
                35, dataEmissao, "12.345.678/0001-95", 55, 1, 123, 1, 12345678);

        assertThat(chave).hasSize(44);
        assertThat(chave).matches("\\d{44}");
        assertThat(chave.substring(0, 2)).isEqualTo("35");
        assertThat(chave.substring(2, 6)).isEqualTo("2608");
        assertThat(chave.substring(6, 20)).isEqualTo("12345678000195");
        assertThat(chave.substring(20, 22)).isEqualTo("55");
        assertThat(chave.substring(22, 25)).isEqualTo("001");
        assertThat(chave.substring(25, 34)).isEqualTo("000000123");
        assertThat(chave.substring(34, 35)).isEqualTo("1");
        assertThat(chave.substring(35, 43)).isEqualTo("12345678");

        int dvEsperado = service.calcularDv(chave.substring(0, 43));
        assertThat(chave.substring(43)).isEqualTo(String.valueOf(dvEsperado));
    }

    @Test
    void test_gerarCNf_geraOitoDigitos() {
        Integer cNf = service.gerarCNf();

        assertThat(cNf).isBetween(0, 99_999_999);
    }
}
