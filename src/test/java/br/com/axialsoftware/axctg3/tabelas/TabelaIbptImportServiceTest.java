package br.com.axialsoftware.axctg3.tabelas;

import br.com.axialsoftware.axctg3.entity.tabelas.TabelaIbpt;
import br.com.axialsoftware.axctg3.service.tabelas.TabelaIbptImportService;
import br.com.axialsoftware.axctg3.test_support.AuthenticatedAsAdmin;
import io.jmix.core.DataManager;
import io.jmix.core.SaveContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Cobre o {@link TabelaIbptImportService} — o botão "Importar" da {@code TabelaIbpt.list}.
 * UF sintética "ZZ" (não existe) pra não mexer em linhas de UF real no HSQLDB de teste
 * compartilhado. A segunda importação do mesmo arquivo prova que apagar e regravar as
 * mesmas chaves (UF, código, ex, tipo) numa transação só não estoura o índice único.
 */
@SpringBootTest
@ExtendWith(AuthenticatedAsAdmin.class)
@ActiveProfiles("test")
class TabelaIbptImportServiceTest {

    // Arquivo real baixado pelo usuário (2026-09-24) — o teste que o usa é pulado em
    // máquina onde ele não existe.
    private static final Path CSV_REAL = Path.of("C:/ibpt/TabelaIBPTaxSP26.2.B.csv");

    @Autowired
    private DataManager dataManager;

    @Autowired
    private TabelaIbptImportService tabelaIbptImportService;

    private boolean importouSpReal;

    private byte[] csvAmostra() {
        String csv = """
                codigo;ex;tipo;descricao;nacionalfederal;importadosfederal;estadual;municipal;vigenciainicio;vigenciafim;chave;versao;fonte
                01012100;;0;"Cavalos reprodutores,de raca pura";13.45;15.45;18.00;0.00;20/09/2026;31/10/2026;C44399;26.2.B;IBPT/empresometro.com.br
                22030000;01;0;"Cervejas; em garrafa de vidro — ação";20.10;22.10;25.00;0.00;20/09/2026;31/10/2026;C44399;26.2.B;IBPT/empresometro.com.br
                010101000;;1;"Serviço de teste";4.20;4.20;0.00;2.00;20/09/2026;31/10/2026;C44399;26.2.B;IBPT/empresometro.com.br
                linha;quebrada
                """;
        return csv.getBytes(Charset.forName("windows-1252"));
    }

    @Test
    void importaSubstituiUfInteiraEIgnoraLinhaInvalida() {
        TabelaIbptImportService.ImportResult r1 = tabelaIbptImportService.importar(csvAmostra(), "TabelaIBPTaxZZ26.2.B.csv");
        assertThat(r1.uf()).isEqualTo("ZZ");
        assertThat(r1.versao()).isEqualTo("26.2.B");
        assertThat(r1.importados()).isEqualTo(3);
        assertThat(r1.removidos()).isZero();
        assertThat(r1.ignorados()).isEqualTo(1);

        TabelaIbptImportService.ImportResult r2 = tabelaIbptImportService.importar(csvAmostra(), "TabelaIBPTaxZZ26.2.B.csv");
        assertThat(r2.importados()).isEqualTo(3);
        assertThat(r2.removidos()).isEqualTo(3);
        assertThat(linhas("ZZ")).hasSize(3);

        TabelaIbpt cerveja = linhas("ZZ").stream().filter(t -> t.getCodigo().equals("22030000")).findFirst().orElseThrow();
        assertThat(cerveja.getEx()).isEqualTo("01");
        assertThat(cerveja.getTipo()).isZero();
        assertThat(cerveja.getDescricao()).isEqualTo("Cervejas; em garrafa de vidro — ação");
        assertThat(cerveja.getAliqNacionalFederal()).isEqualByComparingTo(new BigDecimal("20.10"));
        assertThat(cerveja.getAliqImportadosFederal()).isEqualByComparingTo(new BigDecimal("22.10"));
        assertThat(cerveja.getAliqEstadual()).isEqualByComparingTo(new BigDecimal("25.00"));
        assertThat(cerveja.getVigenciaInicio()).isEqualTo(LocalDate.of(2026, 9, 20));
        assertThat(cerveja.getVigenciaFim()).isEqualTo(LocalDate.of(2026, 10, 31));
        assertThat(cerveja.getChave()).isEqualTo("C44399");

        TabelaIbpt cavalo = linhas("ZZ").stream().filter(t -> t.getCodigo().equals("01012100")).findFirst().orElseThrow();
        assertThat(cavalo.getEx()).isNull();
    }

    @Test
    void nomeDeArquivoSemUfEhRecusado() {
        assertThatThrownBy(() -> tabelaIbptImportService.importar(csvAmostra(), "tabela.csv"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("UF");
        assertThat(linhas("ZZ")).isEmpty();
    }

    @Test
    void importaArquivoRealDoIbpt() throws IOException {
        assumeTrue(Files.exists(CSV_REAL), "CSV real do IBPT não disponível nesta máquina");
        assumeTrue(linhas("SP").isEmpty(), "já há linhas de SP no banco de teste");
        importouSpReal = true;

        TabelaIbptImportService.ImportResult r = tabelaIbptImportService.importar(
                Files.readAllBytes(CSV_REAL), CSV_REAL.getFileName().toString());

        assertThat(r.uf()).isEqualTo("SP");
        assertThat(r.importados()).isEqualTo(12162);
        assertThat(r.ignorados()).isZero();
    }

    private List<TabelaIbpt> linhas(String uf) {
        return dataManager.load(TabelaIbpt.class)
                .query("select e from TabelaIbpt e where e.uf = :uf")
                .parameter("uf", uf)
                .list();
    }

    @AfterEach
    void tearDown() {
        apagar(linhas("ZZ"));
        if (importouSpReal) {
            apagar(linhas("SP"));
        }
    }

    private void apagar(List<TabelaIbpt> linhas) {
        if (!linhas.isEmpty()) {
            dataManager.save(new SaveContext().removing(linhas.toArray()));
        }
    }
}
