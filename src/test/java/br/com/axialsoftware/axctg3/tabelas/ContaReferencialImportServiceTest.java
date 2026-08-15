package br.com.axialsoftware.axctg3.tabelas;

import br.com.axialsoftware.axctg3.entity.enums.CodPlanRef;
import br.com.axialsoftware.axctg3.entity.tabelas.ContaReferencial;
import br.com.axialsoftware.axctg3.service.tabelas.ContaReferencialImportService;
import br.com.axialsoftware.axctg3.test_support.AuthenticatedAsAdmin;
import io.jmix.core.DataManager;
import io.jmix.core.SaveContext;
import io.jmix.data.PersistenceHints;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Cobre {@link ContaReferencialImportService} lendo um .xlsx sintético
 * ({@code plano_referencial_sample.xlsx}, gerado como zip+XML na mão, mesma estrutura mínima do
 * xlsx oficial da RFB) com as 20 abas (par Patrimonial/Resultado dos 10 {@link CodPlanRef}) — o
 * que o botão "Importar" da {@code ContaReferencial.list} exercita. Cada aba tem uma linha
 * sintética (raiz) e uma analítica, com o mesmo código "9"/"9.01" repetido em todas — não colide
 * porque o parse não grava nada (o upsert real é por código dentro de cada plano). A aba
 * "U100B" do fixture reproduz de propósito a anomalia real da fonte 2026 (cabeçalho da coluna
 * de código em branco em vez de "CÓDIGO"), pra cobrir o fallback pra coluna A.
 */
@SpringBootTest
@ExtendWith(AuthenticatedAsAdmin.class)
@ActiveProfiles("test")
class ContaReferencialImportServiceTest {

    @Autowired
    private DataManager dataManager;

    @Autowired
    private ContaReferencialImportService contaReferencialImportService;

    private byte[] xlsxAmostra() throws IOException {
        try (InputStream is = new ClassPathResource(
                "br/com/axialsoftware/axctg3/tabelas/plano_referencial_sample.xlsx").getInputStream()) {
            return is.readAllBytes();
        }
    }

    @Test
    void parseiaAs20AbasDosDezPlanosEMapeiaOsCampos() throws IOException {
        ContaReferencialImportService.ParseResult resultado = contaReferencialImportService.parsear(xlsxAmostra());

        assertThat(resultado.ignoradas()).isZero();
        assertThat(resultado.linhas()).hasSize(40); // 20 abas x 2 linhas (raiz + analítica)

        // 10 planos, 4 linhas cada (2 abas x 2 linhas)
        Map<CodPlanRef, Long> porPlano = resultado.linhas().stream()
                .collect(Collectors.groupingBy(ContaReferencialImportService.Linha::codPlanRef, Collectors.counting()));
        assertThat(porPlano).hasSize(10);
        assertThat(porPlano.values()).allMatch(qtd -> qtd == 4);

        ContaReferencialImportService.Linha raiz = linhaPorDescricao(resultado, "RAIZ L100A");
        assertThat(raiz.codigo()).isEqualTo("9");
        assertThat(raiz.analitica()).isFalse();
        assertThat(raiz.codContaSup()).isNull();
        assertThat(raiz.grau()).isEqualTo(1);
        assertThat(raiz.codNat()).isEqualTo(1);
        assertThat(raiz.dtIni()).isEqualTo(LocalDate.of(2015, 1, 1));
        assertThat(raiz.dtFim()).isNull();
        assertThat(raiz.orientacoes()).isNull();
        assertThat(raiz.codPlanRef()).isEqualTo(CodPlanRef.PJ_em_geral_lucro_real);

        ContaReferencialImportService.Linha filha = linhaPorDescricao(resultado, "FILHA L100A");
        assertThat(filha.codigo()).isEqualTo("901"); // "9.01" na fonte — armazenamos só os dígitos
        assertThat(filha.analitica()).isTrue();
        assertThat(filha.codContaSup()).isEqualTo("9");
        assertThat(filha.orientacoes()).isEqualTo("Orientação de teste");

        // aba sem coluna ORIENTAÇÕES no cabeçalho (a maioria das abas B/C/U1xx reais não tem)
        ContaReferencialImportService.Linha semOrientacoes = linhaPorDescricao(resultado, "FILHA L100B");
        assertThat(semOrientacoes.orientacoes()).isNull();
        assertThat(semOrientacoes.codPlanRef()).isEqualTo(CodPlanRef.Financeiras_lucro_real);

        // U100B: cabeçalho da coluna de código em branco na fonte real — cobre o fallback pra
        // coluna A (sem ele, codigo() viria null e a linha seria descartada). Imunes_e_isentas_
        // financeiras é um dos 3 planos "B" que mantêm o código pontuado (ver
        // PLANOS_COM_CODIGO_MISTO) — sem isso, código pontuado de nível raso colide com código já
        // liso de nível fundo na fonte real.
        ContaReferencialImportService.Linha comCabecalhoEmBranco = linhaPorDescricao(resultado, "FILHA U100B");
        assertThat(comCabecalhoEmBranco.codigo()).isEqualTo("9.01");
        assertThat(comCabecalhoEmBranco.codPlanRef()).isEqualTo(CodPlanRef.Imunes_e_isentas_financeiras);
    }

    @Test
    void arquivoSemWorkbookLancaExcecaoComMensagemClara() {
        byte[] naoEhXlsx = "isto não é um xlsx".getBytes();

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> contaReferencialImportService.parsear(naoEhXlsx));
        assertThat(e).hasMessageContaining("xlsx");
    }

    @Test
    void salvarLinhaCriaDepoisAtualiza() {
        ContaReferencialImportService.Linha linhaOriginal = new ContaReferencialImportService.Linha(
                "9.99", "Descrição original", LocalDate.of(2015, 1, 1), null,
                true, null, 1, 1, null, CodPlanRef.PJ_em_geral_lucro_real);

        boolean criouNaPrimeira = contaReferencialImportService.salvarLinha(linhaOriginal);
        assertThat(criouNaPrimeira).isTrue();

        ContaReferencialImportService.Linha linhaAtualizada = new ContaReferencialImportService.Linha(
                "9.99", "Descrição atualizada", LocalDate.of(2015, 1, 1), null,
                true, null, 1, 1, "nova orientação", CodPlanRef.PJ_em_geral_lucro_real);

        boolean criouNaSegunda = contaReferencialImportService.salvarLinha(linhaAtualizada);
        assertThat(criouNaSegunda).isFalse();

        ContaReferencial salva = carregar("9.99");
        assertThat(salva.getDescricao()).isEqualTo("Descrição atualizada");
        assertThat(salva.getOrientacoes()).isEqualTo("nova orientação");
    }

    private ContaReferencialImportService.Linha linhaPorDescricao(ContaReferencialImportService.ParseResult resultado,
                                                                    String descricao) {
        return resultado.linhas().stream()
                .filter(l -> l.descricao().equals(descricao))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Linha \"" + descricao + "\" não encontrada"));
    }

    private ContaReferencial carregar(String codigo) {
        return dataManager.load(ContaReferencial.class)
                .query("select e from ContaReferencial e where e.codigo = :codigo")
                .parameter("codigo", codigo)
                .one();
    }

    // Hard delete: o índice único de ContaReferencial no HSQLDB não exclui registros com soft
    // delete (ao contrário do Postgres) — mesmo padrão de ContaContabilListViewUiTest.limparDados.
    @AfterEach
    void tearDown() {
        List<ContaReferencial> criadas = dataManager.load(ContaReferencial.class)
                .query("select e from ContaReferencial e where e.codigo like '9%'")
                .hint(PersistenceHints.SOFT_DELETION, false)
                .list();
        if (!criadas.isEmpty()) {
            dataManager.save(new SaveContext()
                    .setHint(PersistenceHints.SOFT_DELETION, false)
                    .setHint(PersistenceHints.SKIP_ENTITY_CHANGED_EVENT, true)
                    .removing(criadas.toArray()));
        }
    }
}
