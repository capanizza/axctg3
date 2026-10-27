package br.com.axialsoftware.axctg3.service.importacao;

import br.com.axialsoftware.axctg3.entity.enums.TipoLote;
import io.jmix.core.DataManager;
import io.jmix.data.Sequences;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Importa um {@link br.com.axialsoftware.axctg3.entity.importacao.ImpLote} de pedidos de venda do
 * legado (PEDVENDA + ITEMPEDVENDA) para a empresa do lote — comportamento em
 * {@link ImportacaoDocumentoBase}. Não havia rotina antiga no projeto Axial.
 * <p>
 * A data do pedido (DATA; sem ela, DATAENTRADA) vai para a data de entrada. O desconto do pedido é
 * a soma de DESCONTO1 e DESCONTO2. As duas observações do legado: OBSERVACAO (interna) vai para o
 * complemento da mensagem e OBS1 (o texto que ia para a nota) para a mensagem da nota. A mensagem
 * padrão vem pelo código (importe antes as mensagens). FATURATOTAL marca o pedido como faturado,
 * e o axctg3 não emite nota a partir dele. Pedido cancelado no legado não entra (aviso). Os itens
 * levam CFOP e complemento; o CST fica vazio (o legado calculava na emissão da nota).
 */
@Service
public class ImportacaoPedidosVendaService extends ImportacaoDocumentoBase {

    private static final Config CONFIG = new Config(TipoLote.PEDIDOS_VENDA, "pedidos de venda", "Pedido",
            "PEDIDO_VENDA", "ITEM_PEDIDO_VENDA", "PEDIDO_VENDA_ID", "IMP_PEDIDO_VENDA", "IMP_ITEM_PEDIDO_VENDA",
            "DATA_ENTRADA",
            List.of("DATA_ENTRADA", "PARCEIRO_ID", "NATUREZA_ID", "CONDICAO_PAGAMENTO_ID", "BANCO_ID",
                    "TRANSPORTADORA_ID", "VENDEDOR_ID", "MOD_FRETE", "FRETE", "SEGURO", "DESPESAS", "DESCONTO",
                    "MENSAGEM_ID", "COMPLEMENTO_MENSAGEM", "MENSAGEM_NOTA", "FATURADO", "PESO_LIQUIDO", "PESO_BRUTO"),
            List.of("ITEM", "PRODUTO_ID", "QUANTIDADE", "VALOR_UNITARIO", "CFOP", "CST", "DESCRICAO_COMPLEMENTAR",
                    "FORA_DO_TOTAL"),
            Map.of(), "pedido_venda_seq_");

    public ImportacaoPedidosVendaService(DataManager dataManager, DataSource dataSource, Sequences sequences) {
        super(dataManager, dataSource, sequences, CONFIG);
    }

    @Override
    protected Documento converter(Map<String, Object> l, List<Map<String, Object>> itensLegado, Contexto ctx) {
        Integer numero = (Integer) l.get("NUMERO");
        if (numero == null) {
            ctx.relatorio().erros.add("Pedido sem número no lote (registro " + l.get("REGISTRO") + ").");
            return null;
        }
        String titulo = "Pedido " + numero;
        if ("S".equalsIgnoreCase(ImportacaoDocumentos.texto((String) l.get("CANCELADO")))) {
            contar(ctx, "cancelados").add(numero);
            return null;
        }
        boolean faturado = "S".equalsIgnoreCase(ImportacaoDocumentos.texto((String) l.get("FATURA_TOTAL")));
        ImportacaoDocumentos.Cadastros cad = ctx.cadastros();
        LocalDate data = (LocalDate) (l.get("DATA") != null ? l.get("DATA") : l.get("DATA_ENTRADA"));
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("DATA_ENTRADA", data);
        c.put("PARCEIRO_ID", cad.parceiro(l.get("PARCEIRO")));
        c.put("NATUREZA_ID", cad.natureza(l.get("NATUREZA")));
        c.put("CONDICAO_PAGAMENTO_ID", cad.condicao(l.get("CONDICAO")));
        c.put("BANCO_ID", cad.banco(l.get("BANCO")));
        c.put("TRANSPORTADORA_ID", cad.transportadora(l.get("TRANSPO")));
        c.put("VENDEDOR_ID", cad.vendedor(l.get("VENDEDOR")));
        c.put("MOD_FRETE", ImportacaoDocumentos.modFrete((String) l.get("TIPO_FRETE")));
        c.put("FRETE", ImportacaoDocumentos.zero(l.get("FRETE")));
        c.put("SEGURO", ImportacaoDocumentos.zero(l.get("SEGURO")));
        c.put("DESPESAS", BigDecimal.ZERO);
        c.put("DESCONTO", ImportacaoDocumentos.zero(l.get("DESCONTO_1")).add(ImportacaoDocumentos.zero(l.get("DESCONTO_2"))));
        c.put("MENSAGEM_ID", cad.mensagem(l.get("MENSAGEM")));
        c.put("COMPLEMENTO_MENSAGEM", ImportacaoDocumentos.texto((String) l.get("OBSERVACAO")));
        c.put("MENSAGEM_NOTA", ImportacaoDocumentos.texto((String) l.get("OBS1")));
        c.put("FATURADO", faturado);
        c.put("PESO_LIQUIDO", ImportacaoDocumentos.zero(l.get("PESO_LIQUIDO")));
        c.put("PESO_BRUTO", ImportacaoDocumentos.zero(l.get("PESO_BRUTO")));
        boolean comErro = false;
        if (data == null) {
            ctx.relatorio().erros.add(titulo + " sem data.");
            comErro = true;
        }

        List<Map<String, Object>> itens = new ArrayList<>();
        BigDecimal valor = BigDecimal.ZERO;
        for (Map<String, Object> li : itensLegado) {
            Map<String, Object> i = new LinkedHashMap<>();
            i.put("ITEM", itens.size() + 1);
            i.put("PRODUTO_ID", cad.produto(li.get("PRODUTO")));
            BigDecimal quantidade = ImportacaoDocumentos.zero(li.get("QUANTIDADE"));
            BigDecimal unitario = ImportacaoDocumentos.zero(li.get("VALOR_UNITARIO"));
            i.put("QUANTIDADE", quantidade);
            i.put("VALOR_UNITARIO", unitario);
            i.put("CFOP", li.get("CFO"));
            i.put("CST", null);
            i.put("DESCRICAO_COMPLEMENTAR",
                    ImportacaoDocumentos.cortar(ImportacaoDocumentos.texto((String) li.get("COMPL_PRODUTO")), 255));
            i.put("FORA_DO_TOTAL", Boolean.FALSE);
            itens.add(i);
            valor = valor.add(quantidade.multiply(unitario));
        }
        if (comErro || c.get("PARCEIRO_ID") == null || c.get("NATUREZA_ID") == null
                || itens.stream().anyMatch(i -> i.get("PRODUTO_ID") == null)) {
            return null;
        }
        if (itens.isEmpty()) {
            ctx.relatorio().avisos.add(titulo + " sem itens no legado.");
        }
        Documento d = new Documento(numero, c, itens);
        d.valor = valor.setScale(2, java.math.RoundingMode.HALF_UP);
        return d;
    }

    @Override
    protected void concluirConversao(Contexto ctx) {
        List<Integer> cancelados = contar(ctx, "cancelados");
        if (!cancelados.isEmpty()) {
            ctx.relatorio().avisos.add(cancelados.size() + " pedido(s) cancelado(s) no legado não entram: "
                    + ImportacaoDocumentos.resumir(cancelados) + ".");
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Integer> contar(Contexto ctx, String chave) {
        return (List<Integer>) ctx.estado().computeIfAbsent(chave, k -> new ArrayList<Integer>());
    }
}
