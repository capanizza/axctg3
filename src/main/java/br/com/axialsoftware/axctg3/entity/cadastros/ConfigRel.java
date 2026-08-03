package br.com.axialsoftware.axctg3.entity.cadastros;

import br.com.axialsoftware.axctg3.entity.User;
import io.jmix.core.entity.annotation.JmixGeneratedValue;
import io.jmix.core.metamodel.annotation.InstanceName;
import io.jmix.core.metamodel.annotation.JmixEntity;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.util.UUID;

@JmixEntity
@Table(name = "CONFIG_REL")
@Entity
public class ConfigRel {
    @JmixGeneratedValue
    @Column(name = "ID", nullable = false)
    @Id
    private UUID id;

    @InstanceName
    @JoinColumn(name = "USER_ID", nullable = false)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private User user;

    @Column(name = "lancamento_inicial")
    private Integer lancamentoInicial = 0;

    @Column(name = "lancamento_final")
    private Integer lancamentoFinal = 0;

    @Column(name = "data_inicial")
    private LocalDate dataInicial;

    @Column(name = "data_final")
    private LocalDate dataFinal;

    @Column(name = "conta_devedora")
    private String contaDevedora = "";

    @Column(name = "conta_credora")
    private String contaCredora = "";

    @Column(name = "grau_fechamento")
    private Integer grauFechamento = 0;

    @Column(name = "conta_inicial")
    private String contaInicial;

    @Column(name = "conta_final")
    private String contaFinal;

    @Column(name = "listagem_sintetica")
    private Boolean listagemSintetica = false;

    @Column(name = "data_depreciacao")
    private LocalDate dataDepreciacao;

    @Column(name = "historico_depreciacao")
    private Integer historicoDepreciacao = 0;

    @Column(name = "origem")
    private String origem;

    @Column(name = "data_lancamento_inicial")
    private LocalDate dataLancamentoInicial;

    @Column(name = "data_lancamento_final")
    private LocalDate dataLancamentoFinal;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Integer getLancamentoInicial() {
        return lancamentoInicial;
    }

    public void setLancamentoInicial(Integer lancamentoInicial) {
        this.lancamentoInicial = lancamentoInicial;
    }

    public Integer getLancamentoFinal() {
        return lancamentoFinal;
    }

    public void setLancamentoFinal(Integer lancamentoFinal) {
        this.lancamentoFinal = lancamentoFinal;
    }

    public LocalDate getDataInicial() {
        return dataInicial;
    }

    public void setDataInicial(LocalDate dataInicial) {
        this.dataInicial = dataInicial;
    }

    public LocalDate getDataFinal() {
        return dataFinal;
    }

    public void setDataFinal(LocalDate dataFinal) {
        this.dataFinal = dataFinal;
    }

    public String getContaDevedora() {
        return contaDevedora;
    }

    public void setContaDevedora(String contaDevedora) {
        this.contaDevedora = contaDevedora;
    }

    public String getContaCredora() {
        return contaCredora;
    }

    public void setContaCredora(String contaCredora) {
        this.contaCredora = contaCredora;
    }

    public Integer getGrauFechamento() {
        return grauFechamento;
    }

    public void setGrauFechamento(Integer grauFechamento) {
        this.grauFechamento = grauFechamento;
    }

    public String getContaInicial() {
        return contaInicial;
    }

    public void setContaInicial(String contaInicial) {
        this.contaInicial = contaInicial;
    }

    public String getContaFinal() {
        return contaFinal;
    }

    public void setContaFinal(String contaFinal) {
        this.contaFinal = contaFinal;
    }

    public Boolean getListagemSintetica() {
        return listagemSintetica;
    }

    public void setListagemSintetica(Boolean listagemSintetica) {
        this.listagemSintetica = listagemSintetica;
    }

    public LocalDate getDataDepreciacao() {
        return dataDepreciacao;
    }

    public void setDataDepreciacao(LocalDate dataDepreciacao) {
        this.dataDepreciacao = dataDepreciacao;
    }

    public Integer getHistoricoDepreciacao() {
        return historicoDepreciacao;
    }

    public void setHistoricoDepreciacao(Integer historicoDepreciacao) {
        this.historicoDepreciacao = historicoDepreciacao;
    }

    public String getOrigem() {
        return origem;
    }

    public void setOrigem(String origem) {
        this.origem = origem;
    }

    public LocalDate getDataLancamentoInicial() {
        return dataLancamentoInicial;
    }

    public void setDataLancamentoInicial(LocalDate dataLancamentoInicial) {
        this.dataLancamentoInicial = dataLancamentoInicial;
    }

    public LocalDate getDataLancamentoFinal() {
        return dataLancamentoFinal;
    }

    public void setDataLancamentoFinal(LocalDate dataLancamentoFinal) {
        this.dataLancamentoFinal = dataLancamentoFinal;
    }
}