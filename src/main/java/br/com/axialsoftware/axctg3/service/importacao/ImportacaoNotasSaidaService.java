package br.com.axialsoftware.axctg3.service.importacao;

import br.com.axialsoftware.axctg3.entity.enums.TipoLote;
import io.jmix.core.DataManager;
import io.jmix.data.Sequences;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Importa um {@link br.com.axialsoftware.axctg3.entity.importacao.ImpLote} de notas de saída do
 * legado (NOTASAIDA + ITEMNOTASAIDA) para a empresa do lote — comportamento em
 * {@link ImportacaoDocumentoBase}. Faz o papel de {@code exportacao.ExportacaoNotaSaida} do
 * projeto Axial, que gravava direto e pulava o número que já existia.
 * <p>
 * Condição, banco, transportadora, vendedor e mensagem que não existem ficam vazios, com aviso. Espécie
 * fora de "NF"/"NFe" (há lixo no legado) vira a espécie da empresa; série vazia vira "1". O ST da
 * nota soma a GNRE, como na rotina antiga; a observação vai para o complemento da mensagem. A
 * nota cancelada entra marcada como cancelada. A CST do item só existe nos bancos com NFESPED.
 * <p>
 * A numeração de notas continua do maior número entre as notas de saída e as NFe emitidas pelo
 * próprio CNPJ — NFe digitada não passa por {@code NOTA_SAIDA}. Conferência: a soma dos itens
 * (quantidade × valor unitário) tem de bater com o valor das mercadorias; diferença vira
 * divergência, não impede.
 */
@Service
public class ImportacaoNotasSaidaService extends ImportacaoDocumentoBase {

    private static final Config CONFIG = new Config(TipoLote.NOTAS_SAIDA, "notas de saída", "Nota", "NOTA_SAIDA",
            "ITEM_NOTA_SAIDA", "NOTA_SAIDA_ID", "IMP_NOTA_SAIDA", "IMP_ITEM_NOTA_SAIDA", "DATA_EMISSAO",
            List.of("DATA_EMISSAO", "DATA_SAIDA", "ESPECIE", "SERIE", "PARCEIRO_ID", "NATUREZA_ID",
                    "CONDICAO_PAGAMENTO_ID", "BANCO_ID", "TRANSPORTADORA_ID", "VENDEDOR_ID", "MOD_FRETE", "VALOR",
                    "VALOR_MERCADORIA", "BASE_ICMS", "VALOR_ICMS", "BASE_ST", "VALOR_ST", "BASE_IPI", "VALOR_IPI",
                    "FRETE", "SEGURO", "DESPESAS", "DESCONTO", "CANCELADA", "CHAVE", "MENSAGEM_ID",
                    "COMPLEMENTO_MENSAGEM", "PESO_LIQUIDO", "PESO_BRUTO"),
            List.of("ITEM", "PRODUTO_ID", "QUANTIDADE", "VALOR_UNITARIO", "VALOR_DESCONTO", "PORC_DESCONTO",
                    "BASE_ICMS", "ALIQ_ICMS", "VALOR_ICMS", "BASE_IPI", "ALIQ_IPI", "VALOR_IPI", "BASE_ST", "VALOR_ST",
                    "FRETE", "SEGURO", "DESPESAS", "CFOP", "CST", "PESO_LIQUIDO", "PESO_BRUTO",
                    "DESCRICAO_COMPLEMENTAR", "FORA_DO_TOTAL"),
            Map.of("FIN_NFE", 1), "nota_saida_seq_");

    public ImportacaoNotasSaidaService(DataManager dataManager, DataSource dataSource, Sequences sequences) {
        super(dataManager, dataSource, sequences, CONFIG);
    }

    @Override
    protected Integer maiorNumero(Integer codEmpresa) {
        Integer maiorNota = super.maiorNumero(codEmpresa);
        Integer maiorNfe = jdbc.queryForObject("select max(n.N_NF) from NFE n join EMPRESA e on e.CODIGO = n.COD_EMPRESA "
                + "where n.COD_EMPRESA = ? and n.DELETED_DATE is null and e.DELETED_DATE is null "
                + "and n.EMIT_CNPJ = e.CNPJ", Integer.class, codEmpresa);
        return Math.max(maiorNota == null ? 0 : maiorNota, maiorNfe == null ? 0 : maiorNfe);
    }

    @Override
    protected Documento converter(Map<String, Object> l, List<Map<String, Object>> itensLegado, Contexto ctx) {
        Integer numero = (Integer) l.get("NUMERO");
        if (numero == null) {
            ctx.relatorio().erros.add("Nota sem número no lote (registro " + l.get("REGISTRO") + ").");
            return null;
        }
        String especiePadrao = (String) ctx.estado().computeIfAbsent("especiePadrao", k -> jdbc.queryForObject(
                "select coalesce(ESPECIE_NFE, 'NF') from EMPRESA where CODIGO = ? and DELETED_DATE is null",
                String.class, ctx.lote().getCodEmpresa()));
        String titulo = "Nota " + numero;
        ImportacaoDocumentos.Cadastros cad = ctx.cadastros();
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("DATA_EMISSAO", l.get("DATA_EMISSAO"));
        c.put("DATA_SAIDA", l.get("DATA_SAIDA"));
        String especie = ImportacaoDocumentos.texto((String) l.get("ESPECIE"));
        if ("NF".equalsIgnoreCase(especie) || "NFE".equalsIgnoreCase(especie)) {
            especie = "NF".equalsIgnoreCase(especie) ? "NF" : "NFe";
        } else {
            especiesTrocadas(ctx).merge(especie == null ? "" : especie, 1, Integer::sum);
            especie = especiePadrao;
        }
        c.put("ESPECIE", especie);
        String serie = ImportacaoDocumentos.texto((String) l.get("SERIE"));
        c.put("SERIE", serie == null ? "1" : ImportacaoDocumentos.cortar(serie, 2));
        c.put("PARCEIRO_ID", cad.parceiro(l.get("PARCEIRO")));
        c.put("NATUREZA_ID", cad.natureza(l.get("NATUREZA")));
        c.put("CONDICAO_PAGAMENTO_ID", cad.condicao(l.get("CONDICAO")));
        c.put("BANCO_ID", cad.banco(l.get("BANCO")));
        c.put("TRANSPORTADORA_ID", cad.transportadora(l.get("TRANSPO")));
        c.put("VENDEDOR_ID", cad.vendedor(l.get("VENDEDOR")));
        c.put("MOD_FRETE", ImportacaoDocumentos.modFrete((String) l.get("TIPO_FRETE")));
        for (String col : List.of("VALOR", "VALOR_MERCADORIA", "BASE_ICMS", "VALOR_ICMS")) {
            c.put(col, ImportacaoDocumentos.zero(l.get(col)));
        }
        c.put("BASE_ST", ImportacaoDocumentos.zero(l.get("BASE_SUBSTITUICAO")));
        c.put("VALOR_ST", ImportacaoDocumentos.zero(l.get("VALOR_SUBSTITUICAO"))
                .add(ImportacaoDocumentos.zero(l.get("VALOR_GNRE"))));
        for (String col : List.of("BASE_IPI", "VALOR_IPI", "FRETE", "SEGURO", "DESPESAS", "DESCONTO")) {
            c.put(col, ImportacaoDocumentos.zero(l.get(col)));
        }
        boolean cancelada = "S".equalsIgnoreCase(ImportacaoDocumentos.texto((String) l.get("CANCELADA")));
        c.put("CANCELADA", cancelada);
        c.put("CHAVE", ImportacaoDocumentos.cortar(ImportacaoDocumentos.texto((String) l.get("CHAVE_NFE")), 50));
        c.put("MENSAGEM_ID", cad.mensagem(l.get("MENSAGEM")));
        c.put("COMPLEMENTO_MENSAGEM", ImportacaoDocumentos.texto((String) l.get("OBSERVACAO")));
        c.put("PESO_LIQUIDO", ImportacaoDocumentos.zero(l.get("PESO_LIQUIDO")));
        c.put("PESO_BRUTO", ImportacaoDocumentos.zero(l.get("PESO_BRUTO")));
        boolean comErro = false;
        if (l.get("DATA_EMISSAO") == null) {
            ctx.relatorio().erros.add(titulo + " sem data de emissão.");
            comErro = true;
        }

        List<Map<String, Object>> itens = new ArrayList<>();
        BigDecimal somaItens = BigDecimal.ZERO;
        java.util.Set<Object> registros = new java.util.HashSet<>();
        for (Map<String, Object> li : itensLegado) {
            // o join com a NFESPED repete o item quando ela tem duas linhas para ele (raro)
            if (!registros.add(li.get("REGISTRO"))) {
                continue;
            }
            Map<String, Object> i = new LinkedHashMap<>();
            i.put("ITEM", itens.size() + 1);
            i.put("PRODUTO_ID", cad.produto(li.get("PRODUTO")));
            BigDecimal quantidade = ImportacaoDocumentos.zero(li.get("QUANTIDADE"));
            BigDecimal unitario = ImportacaoDocumentos.zero(li.get("VALOR_UNITARIO"));
            i.put("QUANTIDADE", quantidade);
            i.put("VALOR_UNITARIO", unitario);
            i.put("VALOR_DESCONTO", ImportacaoDocumentos.zero(li.get("DESCONTO")));
            i.put("PORC_DESCONTO", ImportacaoDocumentos.zero(li.get("PORC_DESCONTO")));
            for (String col : List.of("BASE_ICMS", "ALIQ_ICMS", "VALOR_ICMS", "BASE_IPI", "ALIQ_IPI", "VALOR_IPI")) {
                i.put(col, ImportacaoDocumentos.zero(li.get(col)));
            }
            i.put("BASE_ST", ImportacaoDocumentos.zero(li.get("BASE_SUBSTITUICAO")));
            i.put("VALOR_ST", ImportacaoDocumentos.zero(li.get("VALOR_SUBSTITUICAO")));
            i.put("FRETE", ImportacaoDocumentos.zero(li.get("FRETE")));
            i.put("SEGURO", ImportacaoDocumentos.zero(li.get("SEGURO")));
            i.put("DESPESAS", BigDecimal.ZERO);
            i.put("CFOP", li.get("CFOP"));
            i.put("CST", ImportacaoDocumentos.cortar(ImportacaoDocumentos.texto((String) li.get("CST")), 4));
            i.put("PESO_LIQUIDO", ImportacaoDocumentos.zero(li.get("PESO_LIQUIDO")));
            i.put("PESO_BRUTO", ImportacaoDocumentos.zero(li.get("PESO_BRUTO")));
            i.put("DESCRICAO_COMPLEMENTAR",
                    ImportacaoDocumentos.cortar(ImportacaoDocumentos.texto((String) li.get("COMPL_PRODUTO")), 255));
            i.put("FORA_DO_TOTAL", Boolean.FALSE);
            itens.add(i);
            somaItens = somaItens.add(quantidade.multiply(unitario));
        }
        if (comErro || c.get("PARCEIRO_ID") == null || c.get("NATUREZA_ID") == null
                || itens.stream().anyMatch(i -> i.get("PRODUTO_ID") == null)) {
            return null;
        }
        BigDecimal mercadorias = ((BigDecimal) c.get("VALOR_MERCADORIA")).setScale(2, RoundingMode.HALF_UP);
        if (itens.isEmpty()) {
            ctx.relatorio().avisos.add(titulo + " sem itens no legado.");
        } else if (somaItens.setScale(2, RoundingMode.HALF_UP).compareTo(mercadorias) != 0) {
            ctx.relatorio().divergencias.add(titulo + ": itens somam " + somaItens.setScale(2, RoundingMode.HALF_UP)
                    + " e o valor das mercadorias é " + mercadorias + ".");
        }
        Documento d = new Documento(numero, c, itens);
        d.valor = (BigDecimal) c.get("VALOR");
        d.cancelado = cancelada;
        return d;
    }

    @Override
    protected void concluirConversao(Contexto ctx) {
        especiesTrocadas(ctx).forEach((especie, qtd) -> ctx.relatorio().avisos.add(qtd + " nota(s) com espécie \""
                + especie + "\" no legado: gravadas como \"" + ctx.estado().get("especiePadrao") + "\"."));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Integer> especiesTrocadas(Contexto ctx) {
        return (Map<String, Integer>) ctx.estado().computeIfAbsent("especiesTrocadas", k -> new TreeMap<String, Integer>());
    }
}
