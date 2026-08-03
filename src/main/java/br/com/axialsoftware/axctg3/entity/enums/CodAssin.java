package br.com.axialsoftware.axctg3.entity.enums;

import io.jmix.core.metamodel.datatype.EnumClass;

import org.jspecify.annotations.Nullable;


public enum CodAssin implements EnumClass<Integer> {

    Pessoa_juridica(1),
    Diretor(203),
    Conselheiro_de_administracao(204),
    Administrador(205),
    Administrador_do_grupo(206),
    Administrador_de_sociedade_filiada(207),
    Administrador_judicial_pessoa_fisica(220),
    Administrador_judicial_pessoa_juridica(222),
    Administrador_judicial_gestor(223),
    Gestor_judicial(226),
    Procurador(309),
    Inventariante(312),
    Liquidante(313),
    Interventor(315),
    Titular_pessoa_fisica(401),
    Empresario(801),
    Contador(900),
    Auditor_independente(940),
    Outros(999);

    private final Integer id;

    CodAssin(Integer id) {
        this.id = id;
    }

    public Integer getId() {
        return id;
    }

    @Nullable
    public static CodAssin fromId(Integer id) {
        for (CodAssin at : CodAssin.values()) {
            if (at.getId().equals(id)) {
                return at;
            }
        }
        return null;
    }
}