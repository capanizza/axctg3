package br.com.axialsoftware.axctg3.entity.enums;

import io.jmix.core.metamodel.datatype.EnumClass;

import org.jspecify.annotations.Nullable;

/**
 * O que um {@code ImpLote} traz do sistema legado. O id é gravado pelo exportador do projeto
 * Axial direto no banco (JDBC) — nunca renumerar.
 */
public enum TipoLote implements EnumClass<Integer> {

    PLANO_CONTAS(1),
    LANCAMENTOS(2),
    HISTORICOS(3),
    BANCOS(4),
    HISTORICOS_FINANCEIROS(5),
    PARCEIROS(6),
    CENTROS_CUSTO(7),
    TITULOS_RECEBER(8),
    TITULOS_PAGAR(9),
    DIVERSOS_PAGAR(10),
    MOVIMENTO_BANCO(11),
    GRUPOS_PRODUTO(12),
    PRODUTOS(13),
    NATUREZAS_OPERACAO(14),
    CONDICOES_PAGAMENTO(15),
    VENDEDORES(16),
    TRANSPORTADORAS(17),
    NOTAS_SAIDA(18),
    PEDIDOS_VENDA(19),
    MENSAGENS(20);

    private final Integer id;

    TipoLote(Integer id) {
        this.id = id;
    }

    public Integer getId() {
        return id;
    }

    @Nullable
    public static TipoLote fromId(Integer id) {
        for (TipoLote at : TipoLote.values()) {
            if (at.getId().equals(id)) {
                return at;
            }
        }
        return null;
    }
}
