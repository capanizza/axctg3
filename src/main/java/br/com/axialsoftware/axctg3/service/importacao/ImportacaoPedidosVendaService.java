package br.com.axialsoftware.axctg3.service.importacao;

import br.com.axialsoftware.axctg3.entity.enums.TipoLote;
import br.com.axialsoftware.axctg3.service.fiscal.ItemNotaSaidaTributacaoService;
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
 * e o axctg3 não emite nota a partir dele. Pedido cancelado, ou sem natureza ou parceiro
 * (começado e abandonado), não entra (aviso). Os itens
 * levam CFOP e complemento; o CST fica vazio (o legado calculava na emissão da nota). O retorno do
 * material do cliente (5902...) sai do total como na tela do item: {@link
 * ItemNotaSaidaTributacaoService#cfopForaDoTotal}, só no Simples.
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
        // número é texto no legado ("002677")
        String numeroLegado = ImportacaoDocumentos.texto((String) l.get("NUMERO"));
        if (numeroLegado == null) {
            ctx.relatorio().erros.add("Pedido sem número no lote (registro " + l.get("REGISTRO") + ").");
            return null;
        }
        if (!numeroLegado.matches("\\d{1,9}")) {
            ctx.relatorio().erros.add("Pedido com número \"" + numeroLegado + "\" no legado: o axctg3 só aceita número.");
            return null;
        }
        Integer numero = Integer.valueOf(numeroLegado);
        String titulo = "Pedido " + numero;
        if ("S".equalsIgnoreCase(ImportacaoDocumentos.texto((String) l.get("CANCELADO")))) {
            contar(ctx, "cancelados").add(numero);
            return null;
        }
        // natureza 0 / sem parceiro: pedido começado e abandonado no legado (na GB, quase todos
        // sem itens) — fica de fora, como o cancelado
        if (semCodigo(l.get("NATUREZA")) || semCodigo(l.get("PARCEIRO"))) {
            contar(ctx, "incompletos").add(numero);
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

        // devolução simbólica fora do total, pela mesma regra da tela do item (o legado mandava
        // tudo no total; um pedido importado e emitido no axctg3 saiu com o retorno no vNF — NF
        // 12787 da GB)
        boolean simples = (Boolean) ctx.estado().computeIfAbsent("simples", k -> jdbc.queryForObject(
                "select count(*) from EMPRESA where CODIGO = ? and DELETED_DATE is null and CRT in (1, 2)",
                Integer.class, ctx.lote().getCodEmpresa()) > 0);
        Integer cfopNatureza = cfopDaNatureza(ctx, l.get("NATUREZA"));
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
            i.put("FORA_DO_TOTAL", simples
                    && ItemNotaSaidaTributacaoService.cfopForaDoTotal((Integer) li.get("CFO"), cfopNatureza));
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

    /** Pedido faturado no axctg3 (nota emitida aqui) continua faturado: reimportar não o reabre. */
    @Override
    protected void ajustarComGravado(Documento doLote, Documento gravado) {
        if (Boolean.TRUE.equals(gravado.cabecalho.get("FATURADO"))) {
            doLote.cabecalho.put("FATURADO", Boolean.TRUE);
        }
    }

    @Override
    protected void concluirConversao(Contexto ctx) {
        List<Integer> cancelados = contar(ctx, "cancelados");
        if (!cancelados.isEmpty()) {
            ctx.relatorio().avisos.add(cancelados.size() + " pedido(s) cancelado(s) no legado não entram: "
                    + ImportacaoDocumentos.resumir(cancelados) + ".");
        }
        List<Integer> incompletos = contar(ctx, "incompletos");
        if (!incompletos.isEmpty()) {
            ctx.relatorio().avisos.add(incompletos.size() + " pedido(s) sem natureza ou sem parceiro no legado "
                    + "(incompletos) não entram: " + ImportacaoDocumentos.resumir(incompletos) + ".");
        }
    }

    @SuppressWarnings("unchecked")
    private Integer cfopDaNatureza(Contexto ctx, Object codigo) {
        Map<Long, Integer> cfops = (Map<Long, Integer>) ctx.estado().computeIfAbsent("cfops", k -> {
            Map<Long, Integer> m = new java.util.HashMap<>();
            jdbc.query("select CODIGO, CFOP from NATUREZA_OPERACAO where COD_EMPRESA = ? and DELETED_DATE is null",
                    rs -> { m.put(rs.getLong(1), rs.getInt(2)); }, ctx.lote().getCodEmpresa());
            return m;
        });
        return codigo == null ? null : cfops.get(((Number) codigo).longValue());
    }

    private static boolean semCodigo(Object codigo) {
        return codigo == null || ((Number) codigo).longValue() == 0;
    }

    @SuppressWarnings("unchecked")
    private static List<Integer> contar(Contexto ctx, String chave) {
        return (List<Integer>) ctx.estado().computeIfAbsent(chave, k -> new ArrayList<Integer>());
    }
}
