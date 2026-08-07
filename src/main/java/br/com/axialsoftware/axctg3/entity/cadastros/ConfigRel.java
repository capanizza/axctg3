package br.com.axialsoftware.axctg3.entity.cadastros;

import br.com.axialsoftware.axctg3.entity.User;
import br.com.axialsoftware.axctg3.entity.financeiro.Banco;
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

    @Column(name = "data_emissao_diverso_inicial")
    private LocalDate dataEmissaoDiversoInicial;

    @Column(name = "data_emissao_diverso_final")
    private LocalDate dataEmissaoDiversoFinal;

    @Column(name = "data_vencimento_diverso_inicial")
    private LocalDate dataVencimentoDiversoInicial;

    @Column(name = "data_vencimento_diverso_final")
    private LocalDate dataVencimentoDiversoFinal;

    @Column(name = "data_emissao_diverso_inicial_listagem")
    private LocalDate dataEmissaoDiversoInicialListagem;

    @Column(name = "data_emissao_diverso_final_listagem")
    private LocalDate dataEmissaoDiversoFinalListagem;

    @Column(name = "data_baixa_diverso_inicial_listagem")
    private LocalDate dataBaixaDiversoInicialListagem;

    @Column(name = "data_baixa_diverso_final_listagem")
    private LocalDate dataBaixaDiversoFinalListagem;

    @Column(name = "data_baixa_pagar")
    private LocalDate dataBaixaPagar;

    @Column(name = "banco_inicial")
    private Integer bancoInicial;

    @JoinColumn(name = "banco_id")
    @ManyToOne(fetch = FetchType.LAZY)
    private Banco banco;

    @Column(name = "data_baixa_receber")
    private LocalDate dataBaixaReceber;

    @Column(name = "data_emissao_receber_inicial")
    private LocalDate dataEmissaoReceberInicial;

    @Column(name = "data_emissao_receber_final")
    private LocalDate dataEmissaoReceberFinal;

    @Column(name = "data_vencimento_receber_inicial")
    private LocalDate dataVencimentoReceberInicial;

    @Column(name = "data_vencimento_receber_final")
    private LocalDate dataVencimentoReceberFinal;

    @Column(name = "data_vencimento_receber_inicial_listagem")
    private LocalDate dataVencimentoReceberInicialListagem;

    @Column(name = "data_vencimento_receber_final_listagem")
    private LocalDate dataVencimentoReceberFinalListagem;

    @Column(name = "data_emissao_receber_inicial_listagem")
    private LocalDate dataEmissaoReceberInicialListagem;

    @Column(name = "data_emissao_receber_final_listagem")
    private LocalDate dataEmissaoReceberFinalListagem;

    @Column(name = "data_baixa_receber_inicial_listagem")
    private LocalDate dataBaixaReceberInicialListagem;

    @Column(name = "data_baixa_receber_final_listagem")
    private LocalDate dataBaixaReceberFinalListagem;

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

    public LocalDate getDataEmissaoDiversoInicial() {
        return dataEmissaoDiversoInicial;
    }

    public void setDataEmissaoDiversoInicial(LocalDate dataEmissaoDiversoInicial) {
        this.dataEmissaoDiversoInicial = dataEmissaoDiversoInicial;
    }

    public LocalDate getDataEmissaoDiversoFinal() {
        return dataEmissaoDiversoFinal;
    }

    public void setDataEmissaoDiversoFinal(LocalDate dataEmissaoDiversoFinal) {
        this.dataEmissaoDiversoFinal = dataEmissaoDiversoFinal;
    }

    public LocalDate getDataVencimentoDiversoInicial() {
        return dataVencimentoDiversoInicial;
    }

    public void setDataVencimentoDiversoInicial(LocalDate dataVencimentoDiversoInicial) {
        this.dataVencimentoDiversoInicial = dataVencimentoDiversoInicial;
    }

    public LocalDate getDataVencimentoDiversoFinal() {
        return dataVencimentoDiversoFinal;
    }

    public void setDataVencimentoDiversoFinal(LocalDate dataVencimentoDiversoFinal) {
        this.dataVencimentoDiversoFinal = dataVencimentoDiversoFinal;
    }

    public LocalDate getDataEmissaoDiversoInicialListagem() {
        return dataEmissaoDiversoInicialListagem;
    }

    public void setDataEmissaoDiversoInicialListagem(LocalDate dataEmissaoDiversoInicialListagem) {
        this.dataEmissaoDiversoInicialListagem = dataEmissaoDiversoInicialListagem;
    }

    public LocalDate getDataEmissaoDiversoFinalListagem() {
        return dataEmissaoDiversoFinalListagem;
    }

    public void setDataEmissaoDiversoFinalListagem(LocalDate dataEmissaoDiversoFinalListagem) {
        this.dataEmissaoDiversoFinalListagem = dataEmissaoDiversoFinalListagem;
    }

    public LocalDate getDataBaixaDiversoInicialListagem() {
        return dataBaixaDiversoInicialListagem;
    }

    public void setDataBaixaDiversoInicialListagem(LocalDate dataBaixaDiversoInicialListagem) {
        this.dataBaixaDiversoInicialListagem = dataBaixaDiversoInicialListagem;
    }

    public LocalDate getDataBaixaDiversoFinalListagem() {
        return dataBaixaDiversoFinalListagem;
    }

    public void setDataBaixaDiversoFinalListagem(LocalDate dataBaixaDiversoFinalListagem) {
        this.dataBaixaDiversoFinalListagem = dataBaixaDiversoFinalListagem;
    }

    public LocalDate getDataBaixaPagar() {
        return dataBaixaPagar;
    }

    public void setDataBaixaPagar(LocalDate dataBaixaPagar) {
        this.dataBaixaPagar = dataBaixaPagar;
    }

    public Integer getBancoInicial() {
        return bancoInicial;
    }

    public void setBancoInicial(Integer bancoInicial) {
        this.bancoInicial = bancoInicial;
    }

    public Banco getBanco() {
        return banco;
    }

    public void setBanco(Banco banco) {
        this.banco = banco;
    }

    public LocalDate getDataBaixaReceber() {
        return dataBaixaReceber;
    }

    public void setDataBaixaReceber(LocalDate dataBaixaReceber) {
        this.dataBaixaReceber = dataBaixaReceber;
    }

    public LocalDate getDataEmissaoReceberInicial() {
        return dataEmissaoReceberInicial;
    }

    public void setDataEmissaoReceberInicial(LocalDate dataEmissaoReceberInicial) {
        this.dataEmissaoReceberInicial = dataEmissaoReceberInicial;
    }

    public LocalDate getDataEmissaoReceberFinal() {
        return dataEmissaoReceberFinal;
    }

    public void setDataEmissaoReceberFinal(LocalDate dataEmissaoReceberFinal) {
        this.dataEmissaoReceberFinal = dataEmissaoReceberFinal;
    }

    public LocalDate getDataVencimentoReceberInicial() {
        return dataVencimentoReceberInicial;
    }

    public void setDataVencimentoReceberInicial(LocalDate dataVencimentoReceberInicial) {
        this.dataVencimentoReceberInicial = dataVencimentoReceberInicial;
    }

    public LocalDate getDataVencimentoReceberFinal() {
        return dataVencimentoReceberFinal;
    }

    public void setDataVencimentoReceberFinal(LocalDate dataVencimentoReceberFinal) {
        this.dataVencimentoReceberFinal = dataVencimentoReceberFinal;
    }

    public LocalDate getDataVencimentoReceberInicialListagem() {
        return dataVencimentoReceberInicialListagem;
    }

    public void setDataVencimentoReceberInicialListagem(LocalDate dataVencimentoReceberInicialListagem) {
        this.dataVencimentoReceberInicialListagem = dataVencimentoReceberInicialListagem;
    }

    public LocalDate getDataVencimentoReceberFinalListagem() {
        return dataVencimentoReceberFinalListagem;
    }

    public void setDataVencimentoReceberFinalListagem(LocalDate dataVencimentoReceberFinalListagem) {
        this.dataVencimentoReceberFinalListagem = dataVencimentoReceberFinalListagem;
    }

    public LocalDate getDataEmissaoReceberInicialListagem() {
        return dataEmissaoReceberInicialListagem;
    }

    public void setDataEmissaoReceberInicialListagem(LocalDate dataEmissaoReceberInicialListagem) {
        this.dataEmissaoReceberInicialListagem = dataEmissaoReceberInicialListagem;
    }

    public LocalDate getDataEmissaoReceberFinalListagem() {
        return dataEmissaoReceberFinalListagem;
    }

    public void setDataEmissaoReceberFinalListagem(LocalDate dataEmissaoReceberFinalListagem) {
        this.dataEmissaoReceberFinalListagem = dataEmissaoReceberFinalListagem;
    }

    public LocalDate getDataBaixaReceberInicialListagem() {
        return dataBaixaReceberInicialListagem;
    }

    public void setDataBaixaReceberInicialListagem(LocalDate dataBaixaReceberInicialListagem) {
        this.dataBaixaReceberInicialListagem = dataBaixaReceberInicialListagem;
    }

    public LocalDate getDataBaixaReceberFinalListagem() {
        return dataBaixaReceberFinalListagem;
    }

    public void setDataBaixaReceberFinalListagem(LocalDate dataBaixaReceberFinalListagem) {
        this.dataBaixaReceberFinalListagem = dataBaixaReceberFinalListagem;
    }
}