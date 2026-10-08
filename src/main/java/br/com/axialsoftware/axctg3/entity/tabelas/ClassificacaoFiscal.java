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
// Desde 2026-10-08 é carregada da tabela NCM oficial do Siscomex
// (ClassificacaoFiscalImportService): codigo = o próprio NCM como número, descricao =
// a descrição montada com os níveis acima do NCM (posição > subposição > item).
@JmixEntity
@Table(name = "CLASSIFICACAO_FISCAL", indexes = {
        @Index(name = "IDX_CLASSIFICACAO_FISCAL_UNQ", columnList = "CODIGO", unique = true),
        @Index(name = "IDX_CLASSIFICACAO_FISCAL_COD_NCM", columnList = "COD_NCM")
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

    @Column(name = "DESCRICAO", length = 2000)
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
    @DependsOnProperties({"codNcm", "descricao"})
    public String getInstanceName(MetadataTools metadataTools) {
        return String.format("%s %s",
                metadataTools.format(codNcm),
                metadataTools.format(descricao));
    }
}
