package br.com.axialsoftware.axctg3.entity.enums;

import io.jmix.core.metamodel.datatype.EnumClass;

import org.jspecify.annotations.Nullable;


public enum TipoProduto implements EnumClass<Integer> {

/*
    MERCADORIA_PARA_REVENDA(0),
    MATERIA_PRIMA(1),
    EMBALAGEM(2),
    PRODUTO_EM_PROCESSO(3),
    PRODUTO_ACABADO(4),
    SUBPRODUTO(5),
    PRODUTO_INTERMEDIARIO(6),
    MATERIAL_DE_USO_E_CONSUMO(7),
    ATIVO_IMOBILIZADO(8),
    SERVICOS(9),
    OUTROS_INSUMOS(10),
    OUTRAS(99);
*/
    REVENDA(0),
    MATERIA_PRIMA(1),
    EMBALAGEM(2),
    EM_PROCESSO(3),
    ACABADO(4),
    SUBPRODUTO(5),
    INTERMEDIARIO(6),
    USO_E_CONSUMO(7),
    ATIVO(8),
    SERVICOS(9),
    OUTROS_INSUMOS(10),
    OUTRAS(99);

    private final Integer id;

    TipoProduto(Integer id) {
        this.id = id;
    }

    public Integer getId() {
        return id;
    }

    @Nullable
    public static TipoProduto fromId(Integer id) {
        for (TipoProduto at : TipoProduto.values()) {
            if (at.getId().equals(id)) {
                return at;
            }
        }
        return null;
    }
}