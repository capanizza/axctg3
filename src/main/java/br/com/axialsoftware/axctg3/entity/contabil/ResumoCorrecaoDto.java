package br.com.axialsoftware.axctg3.entity.contabil;

import io.jmix.core.entity.annotation.JmixGeneratedValue;
import io.jmix.core.entity.annotation.JmixId;
import io.jmix.core.metamodel.annotation.InstanceName;
import io.jmix.core.metamodel.annotation.JmixEntity;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Bean de relatório para {@code DepreciacaoService.resumoCorrecaoMonetaria()} — não é
 * entidade persistente, só agrupa a depreciação acumulada por conta contábil de
 * depreciação para o Jasper {@code ResumoCorrecao2.jasper}. Ver docs/MIGRACAO.md.
 */
@JmixEntity
public class ResumoCorrecaoDto {
    @JmixGeneratedValue
    @JmixId
    private UUID id;

    @InstanceName
    private String codConta;

    private String nmConta;

    private BigDecimal valorAtual;

    public BigDecimal getValorAtual() {
        return valorAtual;
    }

    public void setValorAtual(BigDecimal valorAtual) {
        this.valorAtual = valorAtual;
    }

    public String getNmConta() {
        return nmConta;
    }

    public void setNmConta(String nmConta) {
        this.nmConta = nmConta;
    }

    public String getCodConta() {
        return codConta;
    }

    public void setCodConta(String codConta) {
        this.codConta = codConta;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }
}
