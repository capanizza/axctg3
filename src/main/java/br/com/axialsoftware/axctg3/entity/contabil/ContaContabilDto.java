package br.com.axialsoftware.axctg3.entity.contabil;

import io.jmix.core.entity.annotation.JmixGeneratedValue;
import io.jmix.core.entity.annotation.JmixId;
import io.jmix.core.metamodel.annotation.InstanceName;
import io.jmix.core.metamodel.annotation.JmixEntity;
import jakarta.persistence.Entity;

import java.util.UUID;

@JmixEntity
public class ContaContabilDto {
    @JmixGeneratedValue
    @JmixId
    private UUID id;

    @InstanceName
    private String codigo;

    private String nome;

    private Integer grau;

    private String analitica;

    private String analiticaAnterior;

    public String getAnaliticaAnterior() {
        return analiticaAnterior;
    }

    public void setAnaliticaAnterior(String analiticaAnterior) {
        this.analiticaAnterior = analiticaAnterior;
    }

    public String getAnalitica() {
        return analitica;
    }

    public void setAnalitica(String analitica) {
        this.analitica = analitica;
    }

    public Integer getGrau() {
        return grau;
    }

    public void setGrau(Integer grau) {
        this.grau = grau;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getCodigo() {
        return codigo;
    }

    public void setCodigo(String codigo) {
        this.codigo = codigo;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

}