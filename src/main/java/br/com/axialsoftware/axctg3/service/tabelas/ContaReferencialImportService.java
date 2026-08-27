package br.com.axialsoftware.axctg3.service.tabelas;

import br.com.axialsoftware.axctg3.entity.enums.CodNat;
import br.com.axialsoftware.axctg3.entity.enums.CodPlanRef;
import br.com.axialsoftware.axctg3.entity.tabelas.ContaReferencial;

import io.jmix.core.DataManager;
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
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Import do Plano de Contas Referencial da Receita Federal (SPED ECD/ECF) a partir do xlsx
 * oficial "Planos Referenciais" — um par de abas "Contas Patrimoniais" + "Contas de Resultado"
 * por {@link CodPlanRef} (identificados pela aba {@code INDICE} do próprio arquivo, que
 * documenta o que cada aba é). A tabela oficial muda todo ano (nova versão do leiaute), então em
 * vez de semear uma vez via changelog, este serviço faz upsert por ({@code codPlanRef},
 * {@code codigo}): reimportar o arquivo do ano seguinte atualiza o que mudou e cria o que é
 * novo, sem duplicar.
 *
 * <p>Lê o .xlsx "na unha" — {@code ZipInputStream} + {@code DocumentBuilder} puro do JDK, mesmo
 * estilo do {@link br.com.axialsoftware.axctg3.service.fiscal.NfeXmlParser} — em vez de trazer
 * Apache POI: o formato interno do xlsx é só um zip de XML, e só precisamos do
 * {@code xl/workbook.xml} (nome de aba → arquivo), {@code xl/sharedStrings.xml} e o
 * {@code sheetN.xml} de cada aba alvo. Não vale a dependência nova pra isso.
 *
 * <p>O cabeçalho de cada aba é lido por NOME de coluna (não por letra fixa), pra tolerar
 * reordenação/adição de coluna numa versão futura do arquivo oficial — só falha se uma das
 * colunas obrigatórias sumir. Exceção: a coluna de código sempre cai na coluna {@code A}
 * mesmo quando o cabeçalho não diz "CÓDIGO" (a aba {@code U100B} do arquivo 2026 vem com esse
 * cabeçalho em branco — anomalia real da fonte, não do parser).
 *
 * <p>Fica de fora {@code S100}/{@code S150} (PJ TEF — Tributação de Entidades do Futebol/SAF):
 * regime novo na tabela oficial, sem {@link CodPlanRef} correspondente ainda.
 */
@Service
public class ContaReferencialImportService {

    /** Abas do xlsx oficial que este import lê, e o plano referencial (RFB) a que pertencem —
     * um par "Patrimonial"/"Resultado" por plano, conforme a aba INDICE do arquivo oficial. */
    private static final Map<String, CodPlanRef> ABAS_PLANO = Map.ofEntries(
            Map.entry("L100A", CodPlanRef.PJ_em_geral_lucro_real),
            Map.entry("L300A", CodPlanRef.PJ_em_geral_lucro_real),
            Map.entry("L100B", CodPlanRef.Financeiras_lucro_real),
            Map.entry("L300B", CodPlanRef.Financeiras_lucro_real),
            Map.entry("L100C", CodPlanRef.Seguradoras_lucro_real),
            Map.entry("L300C", CodPlanRef.Seguradoras_lucro_real),
            Map.entry("P100A", CodPlanRef.PJ_em_geral_lucro_presumido),
            Map.entry("P150A", CodPlanRef.PJ_em_geral_lucro_presumido),
            Map.entry("P100B", CodPlanRef.Financeiras_lucro_presumido),
            Map.entry("P150B", CodPlanRef.Financeiras_lucro_presumido),
            Map.entry("U100A", CodPlanRef.Imunes_e_isentas_em_geral),
            Map.entry("U150A", CodPlanRef.Imunes_e_isentas_em_geral),
            Map.entry("U100B", CodPlanRef.Imunes_e_isentas_financeiras),
            Map.entry("U150B", CodPlanRef.Imunes_e_isentas_financeiras),
            Map.entry("U100C", CodPlanRef.Imunes_e_isentas_seguradoras),
            Map.entry("U150C", CodPlanRef.Imunes_e_isentas_seguradoras),
            Map.entry("U100D", CodPlanRef.Previdencia_complementar),
            Map.entry("U150D", CodPlanRef.Previdencia_complementar),
            Map.entry("U100E", CodPlanRef.Partidos_politicos),
            Map.entry("U150E", CodPlanRef.Partidos_politicos)
    );

    private static final List<String> COLUNAS_OBRIGATORIAS = List.of(
            "CÓDIGO", "DESCRIÇÃO", "TIPO", "CONTA SUPERIOR", "NÍVEL", "NATUREZA");

    private static final DateTimeFormatter DATA_XLSX = DateTimeFormatter.ofPattern("ddMMyyyy", Locale.ROOT);

    private static final Pattern CELL_REF = Pattern.compile("([A-Z]+)(\\d+)");

    private final DataManager dataManager;

    public ContaReferencialImportService(DataManager dataManager) {
        this.dataManager = dataManager;
    }

    /** Uma linha já normalizada, pronta pra virar (ou atualizar) um {@link ContaReferencial}. */
    public record Linha(String codigo, String descricao, LocalDate dtIni, LocalDate dtFim,
                         Boolean analitica, String codContaSup, Integer grau, Integer codNat,
                         String orientacoes, CodPlanRef codPlanRef) {
    }

    public record ParseResult(List<Linha> linhas, int ignoradas) {
    }

    /**
     * Só parseia — não grava nada. A gravação é feita linha a linha por {@link #salvarLinha}, pra
     * o chamador poder rodar isso dentro de um {@code BackgroundTask} e publicar progresso a cada
     * save (é o save, não o parse, que demora com ~1100 linhas).
     */
    public ParseResult parsear(byte[] xlsxContent) {
        Map<String, byte[]> entradas = lerZip(xlsxContent);
        byte[] workbookXml = entradas.get("xl/workbook.xml");
        byte[] relsXml = entradas.get("xl/_rels/workbook.xml.rels");
        byte[] sharedStringsXml = entradas.get("xl/sharedStrings.xml");
        if (workbookXml == null || relsXml == null) {
            throw new IllegalArgumentException("Arquivo não é um .xlsx válido (workbook.xml ausente)");
        }

        List<String> sharedStrings = sharedStringsXml == null
                ? List.of() : lerSharedStrings(sharedStringsXml);
        Map<String, String> ridParaArquivo = lerRels(relsXml);
        Map<String, String> abaParaArquivo = lerAbas(workbookXml);

        List<Linha> linhas = new ArrayList<>();
        int ignoradas = 0;
        for (Map.Entry<String, CodPlanRef> aba : ABAS_PLANO.entrySet()) {
            String nomeAba = aba.getKey();
            CodPlanRef codPlanRef = aba.getValue();
            String rid = abaParaArquivo.get(nomeAba);
            String arquivo = rid == null ? null : ridParaArquivo.get(rid);
            if (arquivo == null) {
                throw new IllegalArgumentException("Aba \"" + nomeAba + "\" não encontrada no arquivo");
            }
            byte[] sheetXml = entradas.get("xl/" + arquivo);
            if (sheetXml == null) {
                throw new IllegalArgumentException("Aba \"" + nomeAba + "\" referenciada mas ausente no arquivo");
            }
            AbaLida lida = lerAba(sheetXml, sharedStrings, codPlanRef);
            linhas.addAll(lida.linhas());
            ignoradas += lida.ignoradas();
        }
        return new ParseResult(linhas, ignoradas);
    }

    /** Upsert por ({@code codPlanRef}, {@code codigo}). Retorna {@code true} se criou. */
    public boolean salvarLinha(Linha linha) {
        ContaReferencial cr = dataManager.load(ContaReferencial.class)
                .query("select e from ContaReferencial e where e.codPlanRef = :codPlanRef and e.codigo = :codigo")
                .parameter("codPlanRef", linha.codPlanRef().getId())
                .parameter("codigo", linha.codigo())
                .optional().orElse(null);

        boolean novo = cr == null;
        if (novo) {
            cr = dataManager.create(ContaReferencial.class);
            cr.setCodigo(linha.codigo());
            cr.setCodPlanRef(linha.codPlanRef());
        }
        cr.setDescricao(linha.descricao());
        cr.setDtIni(linha.dtIni());
        cr.setDtFim(linha.dtFim());
        cr.setAnalitica(linha.analitica());
        cr.setCodContaSup(linha.codContaSup());
        cr.setGrau(linha.grau());
        cr.setCodNat(CodNat.fromId(linha.codNat()));
        cr.setOrientacoes(linha.orientacoes());
        dataManager.saveWithoutReload(cr);
        return novo;
    }

    // ---- leitura do zip/xlsx ----

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

    /** Nome de aba -> id de relacionamento (rIdN). */
    private Map<String, String> lerAbas(byte[] workbookXml) {
        Document doc = parseDocument(workbookXml);
        Map<String, String> result = new HashMap<>();
        NodeList sheetList = doc.getElementsByTagName("sheet");
        for (int i = 0; i < sheetList.getLength(); i++) {
            Element sheet = (Element) sheetList.item(i);
            result.put(sheet.getAttribute("name"), sheet.getAttribute("r:id"));
        }
        return result;
    }

    private record AbaLida(List<Linha> linhas, int ignoradas) {
    }

    private AbaLida lerAba(byte[] sheetXml, List<String> sharedStrings, CodPlanRef codPlanRef) {
        Document doc = parseDocument(sheetXml);
        NodeList rowList = doc.getElementsByTagName("row");

        Map<String, String> colunaPorNome = null;
        List<Linha> linhas = new ArrayList<>();
        int ignoradas = 0;

        for (int i = 0; i < rowList.getLength(); i++) {
            Element row = (Element) rowList.item(i);
            Map<String, String> celulas = lerCelulas(row, sharedStrings);

            if (colunaPorNome == null) {
                colunaPorNome = new LinkedHashMap<>();
                for (Map.Entry<String, String> e : celulas.entrySet()) {
                    if (e.getValue() != null) {
                        colunaPorNome.put(e.getValue().trim().toUpperCase(Locale.ROOT), e.getKey());
                    }
                }
                // a coluna de código é sempre a A, mesmo quando o cabeçalho vem em branco em vez
                // de "CÓDIGO" (caso real da aba U100B no arquivo de 2026) — sem esse fallback a
                // aba importaria silenciosamente zero linhas
                colunaPorNome.putIfAbsent("CÓDIGO", "A");

                for (String obrigatoria : COLUNAS_OBRIGATORIAS) {
                    if (!colunaPorNome.containsKey(obrigatoria)) {
                        throw new IllegalArgumentException(
                                "Aba não tem o formato esperado: coluna \"" + obrigatoria + "\" não encontrada");
                    }
                }
                continue;
            }

            String codigo = valor(celulas, colunaPorNome, "CÓDIGO");
            if (codigo == null || codigo.isBlank()) {
                continue; // linha em branco/rodapé (ex.: "Voltar Índice")
            }

            try {
                String nivel = valor(celulas, colunaPorNome, "NÍVEL");
                String natureza = valor(celulas, colunaPorNome, "NATUREZA");
                // Código mantido igual à fonte, com ponto — o PVA do Sped exige o código
                // referencial pontuado no registro I051 (ex. "1.01.01.01.01"), confirmado
                // rodando o arquivo real no validador. Uma leva anterior tirava o ponto pra
                // "só dígitos" (exceto nas 3 abas "B", que já misturam pontuado/concatenado
                // na mesma coluna e colidiam se normalizadas) — decisão revertida.
                String codigoNormalizado = codigo.trim();
                String codContaSup = blankToNull(valor(celulas, colunaPorNome, "CONTA SUPERIOR"));
                linhas.add(new Linha(
                        codigoNormalizado,
                        valor(celulas, colunaPorNome, "DESCRIÇÃO"),
                        parseData(valor(celulas, colunaPorNome, "DT_INI")),
                        parseData(valor(celulas, colunaPorNome, "DT_FIM")),
                        "A".equals(valor(celulas, colunaPorNome, "TIPO")),
                        codContaSup,
                        Integer.valueOf(nivel.trim()),
                        Integer.valueOf(natureza.trim()),
                        blankToNull(valor(celulas, colunaPorNome, "ORIENTAÇÕES")),
                        codPlanRef
                ));
            } catch (NumberFormatException | NullPointerException | DateTimeParseException e) {
                ignoradas++;
            }
        }
        return new AbaLida(linhas, ignoradas);
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

    private String valor(Map<String, String> celulas, Map<String, String> colunaPorNome, String nomeColuna) {
        String coluna = colunaPorNome.get(nomeColuna);
        return coluna == null ? null : celulas.get(coluna);
    }

    private LocalDate parseData(String ddMMyyyy) {
        if (ddMMyyyy == null || ddMMyyyy.isBlank()) {
            return null;
        }
        return LocalDate.parse(ddMMyyyy.trim(), DATA_XLSX);
    }

    private String blankToNull(String v) {
        return (v == null || v.isBlank()) ? null : v;
    }
}
