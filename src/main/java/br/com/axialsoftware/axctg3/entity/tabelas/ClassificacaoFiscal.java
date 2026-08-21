package br.com.axialsoftware.axctg3.entity.tabelas;

import io.jmix.core.MetadataTools;
import io.jmix.core.entity.annotation.JmixGeneratedValue;
import io.jmix.core.metamodel.annotation.DependsOnProperties;
import io.jmix.core.metamodel.annotation.InstanceName;
import io.jmix.core.metamodel.annotation.JmixEntity;
import io.jmix.core.metamodel.annotation.NumberFormat;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

// Tabela global de classificação fiscal (NCM), sem codEmpresa — mesmo padrão de
// Municipio/TipoLogradouro/ClassTrib nisso, MAS ao contrário deles, sem trilha de
// auditoria/soft delete (pedido explícito: tabela pequena, importada em bulk).
@JmixEntity
@Table(name = "CLASSIFICACAO_FISCAL", indexes = {
        @Index(name = "IDX_CLASSIFICACAO_FISCAL_UNQ", columnList = "CODIGO", unique = true)
})
@Entity
public class ClassificacaoFiscal {
    @JmixGeneratedValue
    @Column(name = "ID", nullable = false)
    @Id
    private UUID id;

    @Column(name = "CODIGO", nullable = false)
    @NotNull
    @NumberFormat(pattern = "###0")
    private Integer codigo;

    @Column(name = "COD_NCM", nullable = false, length = 10)
    @NotNull
    private String codNcm;

    @Column(name = "DESCRICAO", length = 30)
    private String descricao;

    public String getDescricao() {
        return descricao;
    }

    public void setDescricao(String descricao) {
        this.descricao = descricao;
    }

    public String getCodNcm() {
        return codNcm;
    }

    public void setCodNcm(String codNcm) {
        this.codNcm = codNcm;
    }

    public Integer getCodigo() {
        return codigo;
    }

    public void setCodigo(Integer codigo) {
        this.codigo = codigo;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    @InstanceName
    @DependsOnProperties({"codigo", "codNcm", "descricao"})
    public String getInstanceName(MetadataTools metadataTools) {
        return String.format("%d %s %s",
                codigo,
                metadataTools.format(codNcm),
                metadataTools.format(descricao));
    }
}
