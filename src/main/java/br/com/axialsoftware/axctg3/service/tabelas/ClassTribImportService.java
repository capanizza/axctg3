package br.com.axialsoftware.axctg3.service.tabelas;

import br.com.axialsoftware.axctg3.entity.tabelas.ClassTrib;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jmix.core.DataManager;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Import de código de classificação tributária (ClassTrib) a partir do JSON oficial no
 * formato distribuído pela NFe.io — um array de objetos com {@code codigo}, {@code
 * descricao} e os demais campos do grupo gIBSCBS (RT 2025.002/LC 214/2025). É o mesmo
 * formato do arquivo que a fonte disponibiliza a cada nova versão do Informe Técnico,
 * então uma atualização futura é só baixar de novo e subir aqui — sem conversão manual.
 * Faz upsert por {@code codigo}: atualiza se já existir, cria se não existir. Itens sem
 * {@code codigo} ou {@code descricao} válidos são ignorados.
 */
@Service
public class ClassTribImportService {

    // ObjectMapper próprio, não injetado: este projeto não traz spring-boot-starter-json
    // (não há controller REST/MVC), então não existe bean ObjectMapper autoconfigurado
    // no contexto — confirmado por NoSuchBeanDefinitionException ao tentar injetar.
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final DataManager dataManager;

    public ClassTribImportService(DataManager dataManager) {
        this.dataManager = dataManager;
    }

    public record ImportResult(int criados, int atualizados, int ignorados) {
    }

    public ImportResult importar(byte[] jsonContent) {
        JsonNode root;
        try {
            root = objectMapper.readTree(jsonContent);
        } catch (IOException e) {
            throw new IllegalArgumentException("Arquivo não é um JSON válido: " + e.getMessage(), e);
        }

        if (root == null || !root.isArray()) {
            throw new IllegalArgumentException("O JSON deve ser uma lista de classificações tributárias");
        }

        int criados = 0;
        int atualizados = 0;
        int ignorados = 0;

        for (JsonNode node : root) {
            Integer codigo = parseCodigo(node.path("codigo"));
            String descricao = node.path("descricao").asText(null);
            if (codigo == null || descricao == null || descricao.isBlank()) {
                ignorados++;
                continue;
            }

            ClassTrib classTrib = dataManager.load(ClassTrib.class)
                    .query("select e from ClassTrib e where e.codigo = :codigo")
                    .parameter("codigo", codigo)
                    .optional()
                    .orElse(null);

            boolean novo = classTrib == null;
            if (novo) {
                classTrib = dataManager.create(ClassTrib.class);
                classTrib.setCodigo(codigo);
            }

            classTrib.setCst(codigo / 1000);
            classTrib.setDescricao(descricao);
            classTrib.setTipoAliquota(node.path("tipoAliquota").asText(null));
            classTrib.setNomenclatura(node.path("nomenclatura").asText(null));
            classTrib.setDescricaoTratamentoTributario(node.path("descricaoTratamentoTributario").asText(null));
            classTrib.setIncompativelComSuspensao(node.path("incompativelComSuspensao").asBoolean(false));
            classTrib.setExigeGrupoDesoneracao(node.path("exigeGrupoDesoneracao").asBoolean(false));
            classTrib.setPossuiPercentualReducao(node.path("possuiPercentualReducao").asBoolean(false));
            classTrib.setIndicaApropriacaoCreditoAdquirenteCbs(
                    node.path("indicaApropriacaoCreditoAdquirenteCbs").asBoolean(false));
            classTrib.setIndicaApropriacaoCreditoAdquirenteIbs(
                    node.path("indicaApropriacaoCreditoAdquirenteIbs").asBoolean(false));
            classTrib.setIndicaCreditoPresumidoFornecedor(
                    node.path("indicaCreditoPresumidoFornecedor").asBoolean(false));
            classTrib.setIndicaCreditoPresumidoAdquirente(
                    node.path("indicaCreditoPresumidoAdquirente").asBoolean(false));
            classTrib.setCreditoOperacaoAntecedente(node.path("creditoOperacaoAntecedente").asText(null));
            classTrib.setPercentualReducaoCbs(node.path("percentualReducaoCbs").decimalValue());
            classTrib.setPercentualReducaoIbsUf(node.path("percentualReducaoIbsUf").decimalValue());
            classTrib.setPercentualReducaoIbsMun(node.path("percentualReducaoIbsMun").decimalValue());

            List<String> siglas = new ArrayList<>();
            boolean aplicavelNfe = false;
            for (JsonNode tipoDfe : node.path("tiposDfeClassificacao")) {
                String sigla = tipoDfe.path("sigla").asText(null);
                if (sigla != null && !sigla.isBlank()) {
                    siglas.add(sigla);
                    if ("NFe".equals(sigla)) {
                        aplicavelNfe = true;
                    }
                }
            }
            classTrib.setAplicavelNfe(aplicavelNfe);
            classTrib.setSiglasDfe(String.join(",", siglas));

            String dataAtualizacao = node.path("dataAtualizacao").asText(null);
            classTrib.setDataAtualizacao(dataAtualizacao == null || dataAtualizacao.isBlank()
                    ? null : LocalDate.parse(dataAtualizacao));

            dataManager.saveWithoutReload(classTrib);

            if (novo) {
                criados++;
            } else {
                atualizados++;
            }
        }

        return new ImportResult(criados, atualizados, ignorados);
    }

    private Integer parseCodigo(JsonNode codigoNode) {
        String texto = codigoNode.asText(null);
        if (texto == null || texto.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(texto);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
