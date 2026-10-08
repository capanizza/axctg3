package br.com.axialsoftware.axctg3.service.tabelas;

import br.com.axialsoftware.axctg3.entity.tabelas.ClassificacaoFiscal;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jmix.core.DataManager;
import io.jmix.core.SaveContext;
import io.jmix.core.UnconstrainedDataManager;
import io.jmix.data.PersistenceHints;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Import da tabela NCM vigente para {@link ClassificacaoFiscal}, a partir do JSON oficial
 * do Portal Único Siscomex ({@code portalunico.siscomex.gov.br/classif/api/publico/
 * nomenclatura/download/json}) — objeto com {@code Data_Ultima_Atualizacao_NCM} e o array
 * {@code Nomenclaturas} ({@code Codigo} com pontos, {@code Descricao}, ...), trazendo a
 * hierarquia inteira: capítulo (2 dígitos), posição (4), subposições (5 e 6), item (7) e
 * o NCM propriamente dito (8). Só os de 8 dígitos viram ClassificacaoFiscal.
 * <p>
 * A descrição de um NCM sozinha costuma ser só "-- Outros"; por isso a gravada é montada
 * com os níveis acima (posição > subposição > item > NCM), sem os travessões de nível nem
 * as tags HTML ({@code <i>}, {@code <sup>}, {@code <sub>}) que o Siscomex põe no texto.
 * <p>
 * Faz upsert pelo NCM, com {@code codigo} = o NCM como número. O que não está mais no
 * arquivo é apagado (a tabela não tem soft delete), assim como os NCMs repetidos, exceto o
 * NCM {@code 00000000} — aceito na NFe para item que não é mercadoria — e os que algum
 * Produto ainda usa, que ficam como estão e voltam em {@code mantidosEmUso}, para corrigir o
 * produto antes. Tudo numa só transação.
 */
@Service
public class ClassificacaoFiscalImportService {

    static final String NCM_SEM_MERCADORIA = "00000000";

    private static final int LOTE = 1000;
    private static final int TAMANHO_DESCRICAO = 2000;
    private static final Pattern TAG_HTML = Pattern.compile("<[^>]+>");
    private static final Pattern PREFIXO_NIVEL = Pattern.compile("^[-–\\s]+");
    private static final Pattern NCM = Pattern.compile("\\d{8}");

    // ObjectMapper próprio, mesmo motivo do ClassTribImportService: não há bean
    // ObjectMapper autoconfigurado neste projeto.
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final DataManager dataManager;
    private final UnconstrainedDataManager unconstrainedDataManager;

    public ClassificacaoFiscalImportService(DataManager dataManager,
                                            UnconstrainedDataManager unconstrainedDataManager) {
        this.dataManager = dataManager;
        this.unconstrainedDataManager = unconstrainedDataManager;
    }

    public record ImportResult(String vigencia, int criados, int atualizados, int removidos,
                               List<String> mantidosEmUso) {
    }

    @Transactional
    public ImportResult importar(byte[] jsonContent) {
        JsonNode root;
        try {
            root = objectMapper.readTree(jsonContent);
        } catch (IOException e) {
            throw new IllegalArgumentException("Arquivo não é um JSON válido: " + e.getMessage(), e);
        }
        JsonNode nomenclaturas = root == null ? null : root.get("Nomenclaturas");
        if (nomenclaturas == null || !nomenclaturas.isArray()) {
            throw new IllegalArgumentException(
                    "O JSON não está no formato da tabela NCM do Siscomex (falta a lista \"Nomenclaturas\")");
        }

        // código sem pontos -> descrição já limpa, para todos os níveis
        Map<String, String> descricoes = new HashMap<>();
        for (JsonNode node : nomenclaturas) {
            String codigo = node.path("Codigo").asText("").replace(".", "").trim();
            String descricao = limpar(node.path("Descricao").asText(""));
            if (!codigo.isEmpty() && !descricao.isEmpty()) {
                descricoes.put(codigo, descricao);
            }
        }
        Map<String, String> vigentes = new LinkedHashMap<>();
        descricoes.keySet().stream()
                .filter(c -> NCM.matcher(c).matches())
                .sorted()
                .forEach(c -> vigentes.put(c, descricaoCompleta(c, descricoes)));
        if (vigentes.isEmpty()) {
            throw new IllegalArgumentException("Nenhum NCM de 8 dígitos no arquivo");
        }

        List<ClassificacaoFiscal> existentes = dataManager.load(ClassificacaoFiscal.class)
                .all()
                .list();
        Set<UUID> emUso = new HashSet<>(unconstrainedDataManager.loadValue(
                        "select distinct p.classificacaoFiscal.id from Produto p "
                                + "where p.classificacaoFiscal is not null", UUID.class)
                .hint(PersistenceHints.SOFT_DELETION, false)
                .list());

        // um registro por NCM. Os que saíram da tabela e os repetidos (o legado tinha o
        // mesmo NCM em vários códigos) são apagados — menos os que um Produto usa, que
        // ficam intocados e vão para a lista de mantidos
        Map<String, List<ClassificacaoFiscal>> porNcmVigente = new LinkedHashMap<>();
        List<ClassificacaoFiscal> remover = new ArrayList<>();
        List<String> mantidosEmUso = new ArrayList<>();
        for (ClassificacaoFiscal cf : existentes) {
            String ncm = cf.getCodNcm();
            if (NCM_SEM_MERCADORIA.equals(ncm)) {
                continue;
            }
            if (vigentes.containsKey(ncm)) {
                porNcmVigente.computeIfAbsent(ncm, k -> new ArrayList<>()).add(cf);
            } else if (emUso.contains(cf.getId())) {
                mantidosEmUso.add(ncm);
            } else {
                remover.add(cf);
            }
        }
        Map<String, ClassificacaoFiscal> porNcm = new HashMap<>();
        porNcmVigente.forEach((ncm, lista) -> {
            ClassificacaoFiscal escolhido = lista.stream()
                    .filter(cf -> emUso.contains(cf.getId()))
                    .findFirst()
                    .orElse(lista.get(0));
            porNcm.put(ncm, escolhido);
            for (ClassificacaoFiscal cf : lista) {
                if (cf == escolhido) {
                    continue;
                }
                if (emUso.contains(cf.getId())) {
                    mantidosEmUso.add(ncm);
                } else {
                    remover.add(cf);
                }
            }
        });

        for (int i = 0; i < remover.size(); i += LOTE) {
            SaveContext ctx = new SaveContext().setDiscardSaved(true);
            remover.subList(i, Math.min(i + LOTE, remover.size())).forEach(ctx::removing);
            dataManager.save(ctx);
        }

        int criados = 0;
        int atualizados = 0;
        List<ClassificacaoFiscal> gravar = new ArrayList<>();
        for (Map.Entry<String, String> e : vigentes.entrySet()) {
            ClassificacaoFiscal cf = porNcm.get(e.getKey());
            if (cf == null) {
                cf = dataManager.create(ClassificacaoFiscal.class);
                cf.setCodNcm(e.getKey());
                criados++;
            } else {
                atualizados++;
            }
            cf.setCodigo(Integer.valueOf(e.getKey()));
            cf.setDescricao(e.getValue());
            gravar.add(cf);
        }
        for (int i = 0; i < gravar.size(); i += LOTE) {
            SaveContext ctx = new SaveContext().setDiscardSaved(true);
            gravar.subList(i, Math.min(i + LOTE, gravar.size())).forEach(ctx::saving);
            dataManager.save(ctx);
        }

        mantidosEmUso.sort(null);
        return new ImportResult(root.path("Data_Ultima_Atualizacao_NCM").asText(""),
                criados, atualizados, remover.size(), mantidosEmUso);
    }

    // posição (4) > subposição (5) > subposição (6) > item (7) > NCM (8), pulando os níveis
    // que não existem no arquivo; o capítulo (2) fica de fora, é genérico demais
    private String descricaoCompleta(String ncm, Map<String, String> descricoes) {
        List<String> partes = new ArrayList<>();
        for (int tamanho : new int[]{4, 5, 6, 7, 8}) {
            String descricao = descricoes.get(ncm.substring(0, tamanho));
            if (descricao != null) {
                partes.add(descricao);
            }
        }
        String completa = String.join(" > ", partes);
        return completa.length() > TAMANHO_DESCRICAO ? completa.substring(0, TAMANHO_DESCRICAO) : completa;
    }

    private String limpar(String texto) {
        String t = TAG_HTML.matcher(texto).replaceAll("");
        t = PREFIXO_NIVEL.matcher(t).replaceFirst("").trim();
        if (t.endsWith(":")) {
            t = t.substring(0, t.length() - 1).trim();
        }
        return t;
    }
}
