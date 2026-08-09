package br.com.axialsoftware.axctg3.tabelas;

import br.com.axialsoftware.axctg3.entity.tabelas.ClassTrib;
import br.com.axialsoftware.axctg3.service.tabelas.ClassTribImportService;
import br.com.axialsoftware.axctg3.test_support.AuthenticatedAsAdmin;
import io.jmix.core.DataManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Cobre o {@link ClassTribImportService} lendo o formato JSON oficial (NFe.io) — o que o
 * botão "Importar" da {@code ClassTrib.list} exercita. Usa códigos sintéticos 999001-999003
 * (fora da faixa real, que vai até 830001) para não colidir com o seed de 161 registros já
 * carregado pelo changelog no HSQLDB de teste, que é um arquivo compartilhado entre execuções.
 */
@SpringBootTest
@ExtendWith(AuthenticatedAsAdmin.class)
@ActiveProfiles("test")
class ClassTribImportServiceTest {

    private static final List<Integer> CODIGOS_TESTE = List.of(999001, 999002, 999003);

    @Autowired
    private DataManager dataManager;

    @Autowired
    private ClassTribImportService classTribImportService;

    private byte[] jsonAmostra() throws IOException {
        try (InputStream is = new ClassPathResource(
                "br/com/axialsoftware/axctg3/tabelas/class_trib_import_sample.json").getInputStream()) {
            return is.readAllBytes();
        }
    }

    @Test
    void importaCriaAtualizaEIgnoraLinhaInvalida() throws IOException {
        byte[] json = jsonAmostra();

        ClassTribImportService.ImportResult primeiraImportacao = classTribImportService.importar(json);
        assertThat(primeiraImportacao.criados()).isEqualTo(3);
        assertThat(primeiraImportacao.atualizados()).isEqualTo(0);
        assertThat(primeiraImportacao.ignorados()).isEqualTo(1);

        ClassTrib aplicavelNfe = carregar(999001);
        assertThat(aplicavelNfe.getCst()).isEqualTo(999);
        assertThat(aplicavelNfe.getTipoAliquota()).isEqualTo("Padrão");
        assertThat(aplicavelNfe.getNomenclatura()).isEqualTo("NBS ou NCM");
        assertThat(aplicavelNfe.getCreditoOperacaoAntecedente()).isEqualTo("Manutenção");
        assertThat(aplicavelNfe.getPercentualReducaoCbs()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(aplicavelNfe.getAplicavelNfe()).isTrue();
        assertThat(aplicavelNfe.getSiglasDfe()).isEqualTo("NFe,NFCe");
        assertThat(aplicavelNfe.getDataAtualizacao()).isEqualTo(LocalDate.of(2025, 12, 15));

        ClassTrib comReducao = carregar(999002);
        assertThat(comReducao.getIncompativelComSuspensao()).isTrue();
        assertThat(comReducao.getPossuiPercentualReducao()).isTrue();
        assertThat(comReducao.getIndicaCreditoPresumidoFornecedor()).isTrue();
        assertThat(comReducao.getCreditoOperacaoAntecedente()).isEqualTo("Anulação");
        assertThat(comReducao.getPercentualReducaoCbs()).isEqualByComparingTo(new BigDecimal("60"));
        assertThat(comReducao.getPercentualReducaoIbsUf()).isEqualByComparingTo(new BigDecimal("60"));
        assertThat(comReducao.getPercentualReducaoIbsMun()).isEqualByComparingTo(new BigDecimal("60"));
        assertThat(comReducao.getAplicavelNfe()).isFalse();
        assertThat(comReducao.getSiglasDfe()).isEqualTo("NFSe");

        ClassTrib semCreditoAntecedente = carregar(999003);
        assertThat(semCreditoAntecedente.getCreditoOperacaoAntecedente()).isNull();
        assertThat(semCreditoAntecedente.getAplicavelNfe()).isFalse();
        assertThat(semCreditoAntecedente.getSiglasDfe()).isEmpty();

        // reimportar o mesmo JSON é idempotente: nada de novo criado, os 3 são atualizados
        ClassTribImportService.ImportResult segundaImportacao = classTribImportService.importar(json);
        assertThat(segundaImportacao.criados()).isEqualTo(0);
        assertThat(segundaImportacao.atualizados()).isEqualTo(3);
        assertThat(segundaImportacao.ignorados()).isEqualTo(1);
    }

    @Test
    void jsonInvalidoLancaExcecaoComMensagemClara() {
        byte[] jsonInvalido = "{\"não\": \"é uma lista\"}".getBytes();

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> classTribImportService.importar(jsonInvalido));
        assertThat(e).hasMessageContaining("lista");
    }

    private ClassTrib carregar(int codigo) {
        return dataManager.load(ClassTrib.class)
                .query("select e from ClassTrib e where e.codigo = :codigo")
                .parameter("codigo", codigo)
                .one();
    }

    @AfterEach
    void tearDown() {
        List<ClassTrib> criados = dataManager.load(ClassTrib.class)
                .query("select e from ClassTrib e where e.codigo in :codigos")
                .parameter("codigos", CODIGOS_TESTE)
                .list();
        criados.forEach(dataManager::remove);
    }
}
