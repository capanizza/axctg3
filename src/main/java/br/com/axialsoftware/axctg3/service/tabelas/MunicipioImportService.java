package br.com.axialsoftware.axctg3.service.tabelas;

import br.com.axialsoftware.axctg3.entity.tabelas.Municipio;
import br.com.axialsoftware.axctg3.service.fiscal.UfIbge;
import io.jmix.core.DataManager;
import io.jmix.core.SaveContext;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Import da tabela de municípios do IBGE a partir do xlsx do Portal da NF-e ("Diversos" →
 * "Tabela de Municípios", ex. {@code 250210_Tab_Municipios.xlsx}): uma aba só, com as colunas
 * {@code UF} (código IBGE da UF), {@code Nome_UF}, {@code cMun} (código de 7 dígitos) e
 * {@code Nome_Município}. Lê a primeira aba do arquivo, com o cabeçalho por nome de coluna.
 *
 * <p>Upsert por {@code codigo}: cria o que falta e atualiza nome/UF do que mudou; o que já está
 * igual não é gravado de novo. Nada é apagado — um município fora do arquivo (o 9999999
 * "Exterior", que a tabela do IBGE não traz, ou um código que o IBGE extinguiu mas que algum
 * cadastro ainda usa) continua como está.
 *
 * <p>Lê o .xlsx com {@code ZipInputStream} + {@code DocumentBuilder} do JDK, mesmo estilo do
 * {@link ContaReferencialImportService}, sem Apache POI.
 */
@Service
public class MunicipioImportService {

    private static final String COLUNA_UF = "UF";
    private static final String COLUNA_CODIGO = "CMUN";
    private static final String COLUNA_NOME = "NOME_MUNICÍPIO";

    private static final int LOTE = 1000;

    private static final Pattern CELL_REF = Pattern.compile("([A-Z]+)(\\d+)");

    private final DataManager dataManager;

    public MunicipioImportService(DataManager dataManager) {
        this.dataManager = dataManager;
    }

    public record ImportResult(int criados, int atualizados, int inalterados, int ignorados) {
    }

    private record Linha(Integer codigo, String nome, String uf) {
    }

    public ImportResult importar(byte[] xlsxContent) {
        List<Linha> linhas = new ArrayList<>();
        int ignorados = lerPlanilha(xlsxContent, linhas);
        if (linhas.isEmpty()) {
            throw new IllegalArgumentException("Nenhum município encontrado no arquivo");
        }

        Map<Integer, Municipio> porCodigo = new HashMap<>();
        for (Municipio m : dataManager.load(Municipio.class).all().list()) {
            porCodigo.put(m.getCodigo(), m);
        }

        int criados = 0;
        int atualizados = 0;
        int inalterados = 0;
        List<Municipio> gravar = new ArrayList<>();
        for (Linha linha : linhas) {
            Municipio m = porCodigo.get(linha.codigo());
            if (m == null) {
                m = dataManager.create(Municipio.class);
                m.setCodigo(linha.codigo());
                porCodigo.put(linha.codigo(), m);
                criados++;
            } else if (Objects.equals(m.getNome(), linha.nome()) && Objects.equals(m.getUf(), linha.uf())) {
                inalterados++;
                continue;
            } else {
                atualizados++;
            }
            m.setNome(linha.nome());
            m.setUf(linha.uf());
            gravar.add(m);
        }
        for (int i = 0; i < gravar.size(); i += LOTE) {
            SaveContext ctx = new SaveContext().setDiscardSaved(true);
            gravar.subList(i, Math.min(i + LOTE, gravar.size())).forEach(ctx::saving);
            dataManager.save(ctx);
        }
        return new ImportResult(criados, atualizados, inalterados, ignorados);
    }

    /** Preenche {@code linhas} com a primeira aba do xlsx; retorna quantas linhas foram ignoradas. */
    private int lerPlanilha(byte[] xlsxContent, List<Linha> linhas) {
        Map<String, byte[]> entradas = lerZip(xlsxContent);
        byte[] workbookXml = entradas.get("xl/workbook.xml");
        byte[] relsXml = entradas.get("xl/_rels/workbook.xml.rels");
        if (workbookXml == null || relsXml == null) {
            throw new IllegalArgumentException("Arquivo não é um .xlsx válido (workbook.xml ausente)");
        }
        byte[] sharedStringsXml = entradas.get("xl/sharedStrings.xml");
        List<String> sharedStrings = sharedStringsXml == null ? List.of() : lerSharedStrings(sharedStringsXml);

        NodeList sheets = parseDocument(workbookXml).getElementsByTagName("sheet");
        if (sheets.getLength() == 0) {
            throw new IllegalArgumentException("O arquivo não tem nenhuma aba");
        }
        String rid = ((Element) sheets.item(0)).getAttribute("r:id");
        String arquivo = lerRels(relsXml).get(rid);
        byte[] sheetXml = arquivo == null ? null : entradas.get("xl/" + arquivo);
        if (sheetXml == null) {
            throw new IllegalArgumentException("Aba referenciada mas ausente no arquivo");
        }

        NodeList rowList = parseDocument(sheetXml).getElementsByTagName("row");
        Map<String, String> colunaPorNome = null;
        int ignorados = 0;
        for (int i = 0; i < rowList.getLength(); i++) {
            Map<String, String> celulas = lerCelulas((Element) rowList.item(i), sharedStrings);

            if (colunaPorNome == null) {
                colunaPorNome = new HashMap<>();
                for (Map.Entry<String, String> e : celulas.entrySet()) {
                    if (e.getValue() != null) {
                        colunaPorNome.put(e.getValue().trim().toUpperCase(Locale.ROOT), e.getKey());
                    }
                }
                for (String obrigatoria : List.of(COLUNA_UF, COLUNA_CODIGO, COLUNA_NOME)) {
                    if (!colunaPorNome.containsKey(obrigatoria)) {
                        throw new IllegalArgumentException("O arquivo não tem o formato da tabela de municípios "
                                + "do Portal da NF-e (colunas UF, cMun e Nome_Município)");
                    }
                }
                continue;
            }

            String codigo = celulas.get(colunaPorNome.get(COLUNA_CODIGO));
            String nome = celulas.get(colunaPorNome.get(COLUNA_NOME));
            String codigoUf = celulas.get(colunaPorNome.get(COLUNA_UF));
            if ((codigo == null || codigo.isBlank()) && (nome == null || nome.isBlank())) {
                continue; // linha em branco
            }
            try {
                Integer codigoMunicipio = Integer.valueOf(codigo.trim());
                String uf = UfIbge.sigla(Integer.valueOf(codigoUf.trim()));
                if (codigoMunicipio < 1000000 || codigoMunicipio > 9999999 || uf == null || nome.isBlank()) {
                    ignorados++;
                    continue;
                }
                linhas.add(new Linha(codigoMunicipio, nome.trim(), uf));
            } catch (NumberFormatException | NullPointerException e) {
                ignorados++;
            }
        }
        if (colunaPorNome == null) {
            throw new IllegalArgumentException("A planilha está vazia");
        }
        return ignorados;
    }

    // ---- leitura do zip/xlsx (mesmo código do ContaReferencialImportService) ----

    private Map<String, byte[]> lerZip(byte[] xlsxContent) {
        Map<String, byte[]> entradas = new HashMap<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(xlsxContent))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    entradas.put(entry.getName(), zis.readAllBytes());
                }
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Arquivo não é um .xlsx válido: " + e.getMessage(), e);
        }
        return entradas;
    }

    private Document parseDocument(byte[] xmlContent) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            return builder.parse(new ByteArrayInputStream(xmlContent));
        } catch (ParserConfigurationException | SAXException | IOException e) {
            throw new IllegalArgumentException("XML interno do .xlsx inválido: " + e.getMessage(), e);
        }
    }

    private List<String> lerSharedStrings(byte[] xml) {
        Document doc = parseDocument(xml);
        List<String> result = new ArrayList<>();
        NodeList siList = doc.getElementsByTagName("si");
        for (int i = 0; i < siList.getLength(); i++) {
            Element si = (Element) siList.item(i);
            NodeList tList = si.getElementsByTagName("t");
            StringBuilder texto = new StringBuilder();
            for (int j = 0; j < tList.getLength(); j++) {
                texto.append(tList.item(j).getTextContent());
            }
            result.add(texto.toString());
        }
        return result;
    }

    /** Id de relacionamento (rIdN) -> caminho do arquivo, relativo a {@code xl/}. */
    private Map<String, String> lerRels(byte[] xml) {
        Document doc = parseDocument(xml);
        Map<String, String> result = new HashMap<>();
        NodeList relList = doc.getElementsByTagName("Relationship");
        for (int i = 0; i < relList.getLength(); i++) {
            Element rel = (Element) relList.item(i);
            result.put(rel.getAttribute("Id"), rel.getAttribute("Target"));
        }
        return result;
    }

    private Map<String, String> lerCelulas(Element row, List<String> sharedStrings) {
        Map<String, String> celulas = new LinkedHashMap<>();
        NodeList cList = row.getElementsByTagName("c");
        for (int i = 0; i < cList.getLength(); i++) {
            Element c = (Element) cList.item(i);
            Matcher m = CELL_REF.matcher(c.getAttribute("r"));
            if (!m.matches()) {
                continue;
            }
            String coluna = m.group(1);
            String tipo = c.getAttribute("t");

            NodeList isList = c.getElementsByTagName("is");
            NodeList vList = c.getElementsByTagName("v");
            String valor = null;
            if (isList.getLength() > 0) {
                NodeList tList = ((Element) isList.item(0)).getElementsByTagName("t");
                StringBuilder texto = new StringBuilder();
                for (int j = 0; j < tList.getLength(); j++) {
                    texto.append(tList.item(j).getTextContent());
                }
                valor = texto.toString();
            } else if (vList.getLength() > 0) {
                valor = vList.item(0).getTextContent();
                if ("s".equals(tipo)) {
                    int idx = Integer.parseInt(valor.trim());
                    valor = idx < sharedStrings.size() ? sharedStrings.get(idx) : "";
                }
            }
            celulas.put(coluna, valor);
        }
        return celulas;
    }
}
