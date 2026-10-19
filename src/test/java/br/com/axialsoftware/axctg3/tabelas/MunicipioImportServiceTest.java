package br.com.axialsoftware.axctg3.tabelas;

import br.com.axialsoftware.axctg3.entity.tabelas.Municipio;
import br.com.axialsoftware.axctg3.service.tabelas.MunicipioImportService;
import br.com.axialsoftware.axctg3.test_support.AuthenticatedAsAdmin;
import io.jmix.core.DataManager;
import io.jmix.core.SaveContext;
import io.jmix.data.PersistenceHints;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Cobre {@link MunicipioImportService} com um .xlsx montado na hora, na mesma estrutura do
 * arquivo do Portal da NF-e (uma aba, colunas UF/Nome_UF/cMun/Nome_Município, tudo em shared
 * strings). Os códigos ficam na faixa 98xxxxx, que nenhum município real usa.
 */
@SpringBootTest
@ExtendWith(AuthenticatedAsAdmin.class)
@ActiveProfiles("test")
class MunicipioImportServiceTest {

    @Autowired
    DataManager dataManager;

    @Autowired
    MunicipioImportService importService;

    @AfterEach
    void tearDown() {
        // apaga de verdade: o índice único do HSQLDB de teste não exclui as linhas com
        // soft delete, e a próxima rodada recriaria os mesmos códigos
        List<Municipio> criados = dataManager.load(Municipio.class)
                .query("select e from Municipio e where e.codigo between 9800000 and 9899999")
                .hint(PersistenceHints.SOFT_DELETION, false)
                .list();
        if (!criados.isEmpty()) {
            dataManager.save(new SaveContext()
                    .setHint(PersistenceHints.SOFT_DELETION, false)
                    .removing(criados.toArray()));
        }
    }

    @Test
    void criaAtualizaENaoRegravaOQueEstaIgual() throws IOException {
        Municipio antigo = dataManager.create(Municipio.class);
        antigo.setCodigo(9800002);
        antigo.setNome("Nome Antigo");
        antigo.setUf("SP");
        dataManager.save(antigo);

        Municipio igual = dataManager.create(Municipio.class);
        igual.setCodigo(9800003);
        igual.setNome("São Teste");
        igual.setUf("RS");
        igual = dataManager.save(igual);

        byte[] xlsx = xlsx(List.of(
                new String[]{"UF", "Nome_UF", "cMun", "Nome_Município"},
                new String[]{"35", "São Paulo", "9800001", "Alta Floresta D'Oeste"},
                new String[]{"35", "São Paulo", "9800002", "Nome Novo"},
                new String[]{"43", "Rio Grande do Sul", "9800003", "São Teste"},
                new String[]{"99", "Inexistente", "9800004", "UF inválida"},
                new String[]{"35", "São Paulo", "abc", "Código inválido"}));

        MunicipioImportService.ImportResult resultado = importService.importar(xlsx);

        assertThat(resultado.criados()).isEqualTo(1);
        assertThat(resultado.atualizados()).isEqualTo(1);
        assertThat(resultado.inalterados()).isEqualTo(1);
        assertThat(resultado.ignorados()).isEqualTo(2);

        Municipio novo = carregar(9800001);
        assertThat(novo.getNome()).isEqualTo("Alta Floresta D'Oeste");
        assertThat(novo.getUf()).isEqualTo("SP");
        assertThat(carregar(9800002).getNome()).isEqualTo("Nome Novo");
        // o que já estava igual não foi gravado de novo
        assertThat(carregar(9800003).getVersion()).isEqualTo(igual.getVersion());
        assertThat(dataManager.load(Municipio.class)
                .query("select e from Municipio e where e.codigo = 9800004")
                .list()).isEmpty();
    }

    @Test
    void planilhaSemAsColunasEsperadasLancaExcecao() throws IOException {
        byte[] xlsx = xlsx(List.of(
                new String[]{"Código", "Nome"},
                new String[]{"9800001", "Teste"}));

        assertThatThrownBy(() -> importService.importar(xlsx))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Nome_Município");
    }

    @Test
    void arquivoQueNaoEhXlsxLancaExcecao() {
        assertThatThrownBy(() -> importService.importar("não é xlsx".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("xlsx");
    }

    private Municipio carregar(int codigo) {
        return dataManager.load(Municipio.class)
                .query("select e from Municipio e where e.codigo = :codigo")
                .parameter("codigo", codigo)
                .one();
    }

    /** xlsx mínimo: workbook com uma aba, cada célula como shared string (igual ao arquivo real). */
    private byte[] xlsx(List<String[]> linhas) throws IOException {
        List<String> strings = new ArrayList<>();
        StringBuilder sheet = new StringBuilder(
                "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>");
        for (int i = 0; i < linhas.size(); i++) {
            sheet.append("<row r=\"").append(i + 1).append("\">");
            String[] celulas = linhas.get(i);
            for (int j = 0; j < celulas.length; j++) {
                strings.add(celulas[j]);
                sheet.append("<c r=\"").append((char) ('A' + j)).append(i + 1).append("\" t=\"s\"><v>")
                        .append(strings.size() - 1).append("</v></c>");
            }
            sheet.append("</row>");
        }
        sheet.append("</sheetData></worksheet>");

        StringBuilder shared = new StringBuilder(
                "<sst xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">");
        for (String s : strings) {
            shared.append("<si><t>").append(s.replace("&", "&amp;").replace("<", "&lt;")).append("</t></si>");
        }
        shared.append("</sst>");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            entrada(zip, "xl/workbook.xml", "<workbook xmlns:r=\"http://schemas.openxmlformats.org/"
                    + "officeDocument/2006/relationships\"><sheets><sheet name=\"Mun\" sheetId=\"1\" r:id=\"rId1\"/>"
                    + "</sheets></workbook>");
            entrada(zip, "xl/_rels/workbook.xml.rels", "<Relationships><Relationship Id=\"rId1\" "
                    + "Target=\"worksheets/sheet1.xml\"/></Relationships>");
            entrada(zip, "xl/sharedStrings.xml", shared.toString());
            entrada(zip, "xl/worksheets/sheet1.xml", sheet.toString());
        }
        return out.toByteArray();
    }

    private void entrada(ZipOutputStream zip, String nome, String conteudo) throws IOException {
        zip.putNextEntry(new ZipEntry(nome));
        zip.write(conteudo.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
