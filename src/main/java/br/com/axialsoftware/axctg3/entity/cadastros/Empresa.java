package br.com.axialsoftware.axctg3.entity.cadastros;

import br.com.axialsoftware.axctg3.entity.contabil.ContaContabil;
import br.com.axialsoftware.axctg3.entity.contabil.HistoricoContabil;
import br.com.axialsoftware.axctg3.entity.enums.*;
import br.com.axialsoftware.axctg3.entity.financeiro.HistoricoFinanceiro;
import br.com.axialsoftware.axctg3.entity.tabelas.Municipio;
import br.com.axialsoftware.axctg3.entity.tabelas.TipoLogradouro;
import io.jmix.core.FileRef;
import io.jmix.core.MetadataTools;
import io.jmix.core.annotation.DeletedBy;
import io.jmix.core.annotation.DeletedDate;
import io.jmix.core.entity.annotation.JmixGeneratedValue;
import io.jmix.core.metamodel.annotation.DependsOnProperties;
import io.jmix.core.metamodel.annotation.InstanceName;
import io.jmix.core.metamodel.annotation.JmixEntity;
import io.jmix.core.metamodel.annotation.NumberFormat;
import io.jmix.core.metamodel.datatype.DatatypeFormatter;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@JmixEntity
@Table(name = "EMPRESA")
@Entity
public class Empresa {
    @JmixGeneratedValue
    @Column(name = "ID", nullable = false)
    @Id
    private UUID id;

    @Column(name = "VERSION", nullable = false)
    @Version
    private Integer version;

    @CreatedBy
    @Column(name = "CREATED_BY")
    private String createdBy;

    @CreatedDate
    @Column(name = "CREATED_DATE")
    private OffsetDateTime createdDate;

    @LastModifiedBy
    @Column(name = "LAST_MODIFIED_BY")
    private String lastModifiedBy;

    @LastModifiedDate
    @Column(name = "LAST_MODIFIED_DATE")
    private OffsetDateTime lastModifiedDate;

    @DeletedBy
    @Column(name = "DELETED_BY")
    private String deletedBy;

    @DeletedDate
    @Column(name = "DELETED_DATE")
    private OffsetDateTime deletedDate;

    @Column(name = "CODIGO", nullable = false)
    @NotNull
    @NumberFormat(pattern = "##0")
    private Integer codigo = 0;

    @Column(name = "NOME", nullable = false, length = 50)
    @NotNull
    private String nome;

    @Column(name = "APELIDO", nullable = false, length = 20)
    @NotNull
    private String apelido;

    @JoinColumn(name = "TIPO_LOGRADOURO_ID")
    @ManyToOne(fetch = FetchType.LAZY)
    private TipoLogradouro tipoLogradouro;

    @Column(name = "LOGRADOURO", length = 50)
    private String logradouro;

    @Column(name = "NUMERO", length = 10)
    private String numero;

    @Column(name = "COMPLEMENTO", length = 20)
    private String complemento;

    @Column(name = "BAIRRO", length = 20)
    private String bairro;

    @JoinColumn(name = "MUNICIPIO_ID")
    @ManyToOne(fetch = FetchType.LAZY)
    private Municipio municipio;

    @Column(name = "CEP", length = 8)
    private String cep;

    @Column(name = "CNPJ", length = 14)
    private String cnpj;

    @Column(name = "INSC_EST", length = 20)
    private String inscEst;

    @Column(name = "INSC_MUN", length = 20)
    private String inscMun;

    @Column(name = "NIRE", length = 11)
    private String nire;

    @Column(name = "IND_SIT_ESP")
    private Integer indSitEsp;

    @Column(name = "IND_SIT_INI_PER")
    private Integer indSitIniPer;

    @Column(name = "IND_NIRE")
    private Integer indNire;

    @Column(name = "IND_FIN_ESC")
    private Integer indFinEsc;

    @Column(name = "IND_GRANDE_PORTE")
    private Integer indGrandePorte;

    @Column(name = "TIP_ECD")
    private Integer tipEcd;

    @Column(name = "COD_SCP")
    private Long codScp = 0L;

    @Column(name = "IDENT_MF")
    private Boolean identMf = false;

    @Column(name = "IND_ESC_CONS")
    private Boolean indEscCons = false;

    @Column(name = "IND_CENTRALIZADA")
    private Integer indCentralizada;

    @Column(name = "IND_MUDANCA_PC")
    private Integer indMudancaPc;

    @Column(name = "COD_PLAN_REF")
    private Integer codPlanRef;

    // número de ordem do livro digital (Registro I030.NUM_ORD do Sped ECD) — não é uma
    // Sequence do Jmix: regerar o mesmo livro (ex. corrigindo dado antes da transmissão
    // final) precisa manter o mesmo número, e Sequences só expõe createNextValue (sempre
    // incrementa, sem "peek"). O contador confirma/ajusta esse valor na mão, igual faz
    // com NIRE/CRC — sem valor padrão automático.
    @Column(name = "NUM_ORDEM_LIVRO_ECD")
    private Integer numOrdemLivroEcd;

    // data de arquivamento dos atos constitutivos na junta comercial — Registro
    // I030.DT_ARQ do Sped ECD (advertência do PVA quando em branco).
    @Column(name = "DT_ARQ")
    private LocalDate dtArq;

    @Column(name = "NM_RESPONSAVEL", length = 50)
    private String nmResponsavel;

    @Column(name = "CPF_RESPONSAVEL", length = 11)
    private String cpfResponsavel;

    @Column(name = "COD_ASSIN_RESPONSAVEL")
    private Integer codAssinResponsavel;

    @Column(name = "EMAIL_RESPONSAVEL", length = 60)
    private String emailResponsavel;

    @Column(name = "FONE_RESPONSAVEL", length = 14)
    private String foneResponsavel;

    @Column(name = "IND_RESP_LEGAL")
    private Boolean indRespLegal = false;

    @Column(name = "NM_CONTABILISTA", length = 50)
    private String nmContabilista;

    @Column(name = "CPF_CONTABILISTA", length = 11)
    private String cpfContabilista;

    @Column(name = "COD_ASSIN_CONTABILISTA")
    private Integer codAssinContabilista;

    @Column(name = "EMAIL_CONTABILISTA", length = 60)
    private String emailContabilista;

    @Column(name = "FONE_CONTABILISTA", length = 14)
    private String foneContabilista;

    @Column(name = "INSC_CRC", length = 20)
    private String inscCrc;

    @Column(name = "NUM_SEQ_CRC", length = 20)
    private String numSeqCrc;

    @Column(name = "DT_CRC")
    private LocalDate dtCrc;

    @Column(name = "UF_CRC", length = 2)
    private String ufCrc;

    @Column(name = "MASC_CONTABIL", length = 25)
    private String mascContabil;

    // código da conta contábil de encerramento (não FK: o plano de contas é versionado por
    // ano — ContaContabil tem chave única CODIGO+COD_EMPRESA+ANO — então a conta de destino
    // é resolvida em tempo de uso por este código junto com o ano contábil corrente, não
    // fixada num ID de uma linha de um ano específico)
    @Column(name = "COD_CONTA_ENC", length = 25)
    private String codContaEnc;

    @JoinColumn(name = "CONTA_PARCEIRO_SAIDA_ID")
    @ManyToOne(fetch = FetchType.LAZY)
    private ContaContabil contaParceiroSaida;

    @JoinColumn(name = "CONTA_PARCEIRO_ENTRADA_ID")
    @ManyToOne(fetch = FetchType.LAZY)
    private ContaContabil contaParceiroEntrada;

    @JoinColumn(name = "CONTA_IPI_SAIDA_ID")
    @ManyToOne(fetch = FetchType.LAZY)
    private ContaContabil contaIpiSaida;

    @JoinColumn(name = "CONTA_GNRE_SAIDA_ID")
    @ManyToOne(fetch = FetchType.LAZY)
    private ContaContabil contaGnreSaida;

    @JoinColumn(name = "CONTA_SUBSTITUICAO_SAIDA_ID")
    @ManyToOne(fetch = FetchType.LAZY)
    private ContaContabil contaSubstituicaoSaida;

    @JoinColumn(name = "CONTA_REVENDA_ID")
    @ManyToOne(fetch = FetchType.LAZY)
    private ContaContabil contaRevenda;

    @JoinColumn(name = "CONTA_CAIXA_ID")
    @ManyToOne(fetch = FetchType.LAZY)
    private ContaContabil contaCaixa;

    @JoinColumn(name = "CONTA_MOVIMENTO_BANCARIO_ID")
    @ManyToOne(fetch = FetchType.LAZY)
    private ContaContabil contaMovimentoBancario;

    @JoinColumn(name = "HISTORICO_GNRE_SAIDA_ID")
    @ManyToOne(fetch = FetchType.LAZY)
    private HistoricoContabil historicoGnreSaida;

    @JoinColumn(name = "HISTORICO_SUBSTITUICAO_SAIDA_ID")
    @ManyToOne(fetch = FetchType.LAZY)
    private HistoricoContabil historicoSubstituicaoSaida;

    @JoinColumn(name = "HISTORICO_REVENDA_ID")
    @ManyToOne(fetch = FetchType.LAZY)
    private HistoricoContabil historicoRevenda;

    @JoinColumn(name = "HISTORICO_IPI_ID")
    @ManyToOne(fetch = FetchType.LAZY)
    private HistoricoContabil historicoIpi;

    @JoinColumn(name = "HISTORICO_BAIXA_RECEBER_ID")
    @ManyToOne(fetch = FetchType.LAZY)
    private HistoricoContabil historicoBaixaReceber;

    @JoinColumn(name = "HISTORICO_BAIXA_PAGAR_ID")
    @ManyToOne(fetch = FetchType.LAZY)
    private HistoricoContabil historicoBaixaPagar;

    @JoinColumn(name = "HIST_FIN_BAIXA_RECEBER_ID")
    @ManyToOne(fetch = FetchType.LAZY)
    private HistoricoFinanceiro histFinBaixaReceber;

    @JoinColumn(name = "HIST_FIN_BAIXA_JUROS_RECEBER_ID")
    @ManyToOne(fetch = FetchType.LAZY)
    private HistoricoFinanceiro histFinBaixaJurosReceber;

    @JoinColumn(name = "HIST_FIN_BAIXA_DESCONTO_RECEBER_ID")
    @ManyToOne(fetch = FetchType.LAZY)
    private HistoricoFinanceiro histFinBaixaDescontoReceber;

    @JoinColumn(name = "HIST_FIN_BAIXA_PAGAR_ID")
    @ManyToOne(fetch = FetchType.LAZY)
    private HistoricoFinanceiro histFinBaixaPagar;

    @JoinColumn(name = "HIST_FIN_BAIXA_JUROS_PAGAR_ID")
    @ManyToOne(fetch = FetchType.LAZY)
    private HistoricoFinanceiro histFinBaixaJurosPagar;

    @JoinColumn(name = "HIST_FIN_BAIXA_DESCONTO_PAGAR_ID")
    @ManyToOne(fetch = FetchType.LAZY)
    private HistoricoFinanceiro histFinBaixaDescontoPagar;

    @Column(name = "SELECIONADA")
    private Boolean selecionada = false;

    // FileRef (io.jmix.core.FileRef, serializado como String pelo Jmix) — mesmo padrão de
    // certificadoArquivo abaixo. Era um caminho de texto solto até 2026-08-25 (ver
    // changelog 25-...-empresa-logo-fileref); dados antigos não são FileRefs válidos e
    // foram zerados na migração — cada empresa precisa reenviar o logo pela tela.
    @Column(name = "LOGO")
    private FileRef logo;

    // Código de Regime Tributário (CRT do leiaute NFe) — decide se a emissão própria usa
    // CST (regime normal) ou CSOSN (Simples) no bloco de ICMS de cada item. Nullable:
    // empresa existente precisa configurar antes de emitir. Ver docs/EMISSAO-NFE.md.
    @Column(name = "CRT")
    private Integer crt;

    // Ambiente de emissão (tpAmb do leiaute) — decide qual endpoint SEFAZ é chamado.
    // Sem valor padrão fixado no código de propósito: a tela sugere Homologação, mas o
    // campo em si fica nullable igual aos outros dessa seção, pra não emitir em produção
    // por acidente antes de configurar.
    @Column(name = "AMBIENTE_NFE")
    private Integer ambienteNfe;

    // Certificado A1 (.pfx/.p12) usado pra assinar e autenticar (mTLS) a emissão própria
    // de NFe — arquivo de verdade via FileRef/jmix-localfs (primeiro uso de FileRef no
    // projeto; Empresa.logo acima é só um caminho em texto, padrão diferente e mais
    // antigo). Sem criptografia na senha nesta rodada — mesmo nível de risco já aceito
    // em application.properties (ver "Known rough edges" do CLAUDE.md).
    @Column(name = "CERTIFICADO_ARQUIVO")
    private FileRef certificadoArquivo;

    @Column(name = "CERTIFICADO_SENHA", length = 100)
    private String certificadoSenha;

    public String getCertificadoSenha() {
        return certificadoSenha;
    }

    public void setCertificadoSenha(String certificadoSenha) {
        this.certificadoSenha = certificadoSenha;
    }

    public FileRef getCertificadoArquivo() {
        return certificadoArquivo;
    }

    public void setCertificadoArquivo(FileRef certificadoArquivo) {
        this.certificadoArquivo = certificadoArquivo;
    }

    public AmbienteNfe getAmbienteNfe() {
        return ambienteNfe == null ? null : AmbienteNfe.fromId(ambienteNfe);
    }

    public void setAmbienteNfe(AmbienteNfe ambienteNfe) {
        this.ambienteNfe = ambienteNfe == null ? null : ambienteNfe.getId();
    }

    public CodRegimeTributario getCrt() {
        return crt == null ? null : CodRegimeTributario.fromId(crt);
    }

    public void setCrt(CodRegimeTributario crt) {
        this.crt = crt == null ? null : crt.getId();
    }

    public FileRef getLogo() {
        return logo;
    }

    public void setLogo(FileRef logo) {
        this.logo = logo;
    }

    public Boolean getSelecionada() {
        return selecionada;
    }

    public void setSelecionada(Boolean selecionada) {
        this.selecionada = selecionada;
    }

    public Integer getCodigo() {
        return codigo;
    }

    public void setCodigo(Integer codigo) {
        this.codigo = codigo;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getApelido() {
        return apelido;
    }

    public void setApelido(String apelido) {
        this.apelido = apelido;
    }

    public TipoLogradouro getTipoLogradouro() {
        return tipoLogradouro;
    }

    public void setTipoLogradouro(TipoLogradouro tipoLogradouro) {
        this.tipoLogradouro = tipoLogradouro;
    }

    public String getLogradouro() {
        return logradouro;
    }

    public void setLogradouro(String logradouro) {
        this.logradouro = logradouro;
    }

    public String getNumero() {
        return numero;
    }

    public void setNumero(String numero) {
        this.numero = numero;
    }

    public String getComplemento() {
        return complemento;
    }

    public void setComplemento(String complemento) {
        this.complemento = complemento;
    }

    public String getBairro() {
        return bairro;
    }

    public void setBairro(String bairro) {
        this.bairro = bairro;
    }

    public Municipio getMunicipio() {
        return municipio;
    }

    public void setMunicipio(Municipio municipio) {
        this.municipio = municipio;
    }

    public String getCep() {
        return cep;
    }

    public void setCep(String cep) {
        this.cep = cep;
    }

    public String getCnpj() {
        return cnpj;
    }

    public void setCnpj(String cnpj) {
        this.cnpj = cnpj;
    }

    public String getInscEst() {
        return inscEst;
    }

    public void setInscEst(String inscEst) {
        this.inscEst = inscEst;
    }

    public String getInscMun() {
        return inscMun;
    }

    public void setInscMun(String inscMun) {
        this.inscMun = inscMun;
    }

    public String getNire() {
        return nire;
    }

    public void setNire(String nire) {
        this.nire = nire;
    }

    public IndSitEsp getIndSitEsp() {
        return indSitEsp == null ? null : IndSitEsp.fromId(indSitEsp);
    }

    public void setIndSitEsp(IndSitEsp indSitEsp) {
        this.indSitEsp = indSitEsp == null ? null : indSitEsp.getId();
    }

    public IndSitIniPer getIndSitIniPer() {
        return indSitIniPer == null ? null : IndSitIniPer.fromId(indSitIniPer);
    }

    public void setIndSitIniPer(IndSitIniPer indSitIniPer) {
        this.indSitIniPer = indSitIniPer == null ? null : indSitIniPer.getId();
    }

    public IndNire getIndNire() {
        return indNire == null ? null : IndNire.fromId(indNire) ;
    }

    public void setIndNire(IndNire indNire) {
        this.indNire = indNire == null ? null : indNire.getId();
    }

    public IndFinEsc getIndFinEsc() {
        return indFinEsc == null ? null : IndFinEsc.fromId(indFinEsc);
    }

    public void setIndFinEsc(IndFinEsc indFinEsc) {
        this.indFinEsc = indFinEsc == null ? null : indFinEsc.getId();
    }

    public IndGrandePorte getIndGrandePorte() {
        return indGrandePorte == null ? null : IndGrandePorte.fromId(indGrandePorte);
    }

    public void setIndGrandePorte(IndGrandePorte indGrandePorte) {
        this.indGrandePorte = indGrandePorte == null ? null : indGrandePorte.getId();
    }

    public TipEcd getTipEcd() {
        return tipEcd == null ? null : TipEcd.fromId(tipEcd);
    }

    public void setTipEcd(TipEcd tipEcd) {
        this.tipEcd = tipEcd == null ? null : tipEcd.getId();
    }

    public Long getCodScp() {
        return codScp;
    }

    public void setCodScp(Long codScp) {
        this.codScp = codScp;
    }

    public Boolean getIdentMf() {
        return identMf;
    }

    public void setIdentMf(Boolean identMf) {
        this.identMf = identMf;
    }

    public Boolean getIndEscCons() {
        return indEscCons;
    }

    public void setIndEscCons(Boolean indEscCons) {
        this.indEscCons = indEscCons;
    }

    public IndCentralizada getIndCentralizada() {
        return indCentralizada == null ? null : IndCentralizada.fromId(indCentralizada);
    }

    public void setIndCentralizada(IndCentralizada indCentralizada) {
        this.indCentralizada = indCentralizada == null ? null : indCentralizada.getId();
    }

    public IndMudancaPc getIndMudancaPc() {
        return indMudancaPc == null ? null : IndMudancaPc.fromId(indMudancaPc);
    }

    public void setIndMudancaPc(IndMudancaPc indMudancaPc) {
        this.indMudancaPc = indMudancaPc == null ? null : indMudancaPc.getId();
    }

    public CodPlanRef getCodPlanRef() {
        return codPlanRef == null ? null : CodPlanRef.fromId(codPlanRef);
    }

    public void setCodPlanRef(CodPlanRef codPlanRef) {
        this.codPlanRef = codPlanRef == null ? null : codPlanRef.getId();
    }

    public Integer getNumOrdemLivroEcd() {
        return numOrdemLivroEcd;
    }

    public void setNumOrdemLivroEcd(Integer numOrdemLivroEcd) {
        this.numOrdemLivroEcd = numOrdemLivroEcd;
    }

    public LocalDate getDtArq() {
        return dtArq;
    }

    public void setDtArq(LocalDate dtArq) {
        this.dtArq = dtArq;
    }

    public String getNmResponsavel() {
        return nmResponsavel;
    }

    public void setNmResponsavel(String nmResponsavel) {
        this.nmResponsavel = nmResponsavel;
    }

    public String getCpfResponsavel() {
        return cpfResponsavel;
    }

    public void setCpfResponsavel(String cpfResponsavel) {
        this.cpfResponsavel = cpfResponsavel;
    }

    public CodAssin getCodAssinResponsavel() {
        return codAssinResponsavel == null ? null : CodAssin.fromId(codAssinResponsavel);
    }

    public void setCodAssinResponsavel(CodAssin codAssinResponsavel) {
        this.codAssinResponsavel = codAssinResponsavel == null ? null : codAssinResponsavel.getId();
    }

    public String getEmailResponsavel() {
        return emailResponsavel;
    }

    public void setEmailResponsavel(String emailResponsavel) {
        this.emailResponsavel = emailResponsavel;
    }

    public String getFoneResponsavel() {
        return foneResponsavel;
    }

    public void setFoneResponsavel(String foneResponsavel) {
        this.foneResponsavel = foneResponsavel;
    }

    public Boolean getIndRespLegal() {
        return indRespLegal;
    }

    public void setIndRespLegal(Boolean indRespLegal) {
        this.indRespLegal = indRespLegal;
    }

    public String getNmContabilista() {
        return nmContabilista;
    }

    public void setNmContabilista(String nmContabilista) {
        this.nmContabilista = nmContabilista;
    }

    public String getCpfContabilista() {
        return cpfContabilista;
    }

    public void setCpfContabilista(String cpfContabilista) {
        this.cpfContabilista = cpfContabilista;
    }

    public CodAssin getCodAssinContabilista() {
        return codAssinContabilista == null ? null : CodAssin.fromId(codAssinContabilista);
    }

    public void setCodAssinContabilista(CodAssin codAssinContabilista) {
        this.codAssinContabilista = codAssinContabilista == null ? null : codAssinContabilista.getId();
    }

    public String getEmailContabilista() {
        return emailContabilista;
    }

    public void setEmailContabilista(String emailContabilista) {
        this.emailContabilista = emailContabilista;
    }

    public String getFoneContabilista() {
        return foneContabilista;
    }

    public void setFoneContabilista(String foneContabilista) {
        this.foneContabilista = foneContabilista;
    }

    public String getInscCrc() {
        return inscCrc;
    }

    public void setInscCrc(String inscCrc) {
        this.inscCrc = inscCrc;
    }

    public String getNumSeqCrc() {
        return numSeqCrc;
    }

    public void setNumSeqCrc(String numSeqCrc) {
        this.numSeqCrc = numSeqCrc;
    }

    public LocalDate getDtCrc() {
        return dtCrc;
    }

    public void setDtCrc(LocalDate dtCrc) {
        this.dtCrc = dtCrc;
    }

    public String getUfCrc() {
        return ufCrc;
    }

    public void setUfCrc(String ufCrc) {
        this.ufCrc = ufCrc;
    }

    public String getMascContabil() {
        return mascContabil;
    }

    public void setMascContabil(String mascContabil) {
        this.mascContabil = mascContabil;
    }

    public String getCodContaEnc() {
        return codContaEnc;
    }

    public void setCodContaEnc(String codContaEnc) {
        this.codContaEnc = codContaEnc;
    }

    public ContaContabil getContaParceiroSaida() {
        return contaParceiroSaida;
    }

    public void setContaParceiroSaida(ContaContabil contaParceiroSaida) {
        this.contaParceiroSaida = contaParceiroSaida;
    }

    public ContaContabil getContaParceiroEntrada() {
        return contaParceiroEntrada;
    }

    public void setContaParceiroEntrada(ContaContabil contaParceiroEntrada) {
        this.contaParceiroEntrada = contaParceiroEntrada;
    }

    public ContaContabil getContaIpiSaida() {
        return contaIpiSaida;
    }

    public void setContaIpiSaida(ContaContabil contaIpiSaida) {
        this.contaIpiSaida = contaIpiSaida;
    }

    public ContaContabil getContaGnreSaida() {
        return contaGnreSaida;
    }

    public void setContaGnreSaida(ContaContabil contaGnreSaida) {
        this.contaGnreSaida = contaGnreSaida;
    }

    public ContaContabil getContaSubstituicaoSaida() {
        return contaSubstituicaoSaida;
    }

    public void setContaSubstituicaoSaida(ContaContabil contaSubstituicaoSaida) {
        this.contaSubstituicaoSaida = contaSubstituicaoSaida;
    }

    public ContaContabil getContaRevenda() {
        return contaRevenda;
    }

    public void setContaRevenda(ContaContabil contaRevenda) {
        this.contaRevenda = contaRevenda;
    }

    public ContaContabil getContaCaixa() {
        return contaCaixa;
    }

    public void setContaCaixa(ContaContabil contaCaixa) {
        this.contaCaixa = contaCaixa;
    }

    public ContaContabil getContaMovimentoBancario() {
        return contaMovimentoBancario;
    }

    public void setContaMovimentoBancario(ContaContabil contaMovimentoBancario) {
        this.contaMovimentoBancario = contaMovimentoBancario;
    }

    public HistoricoContabil getHistoricoGnreSaida() {
        return historicoGnreSaida;
    }

    public void setHistoricoGnreSaida(HistoricoContabil historicoGnreSaida) {
        this.historicoGnreSaida = historicoGnreSaida;
    }

    public HistoricoContabil getHistoricoSubstituicaoSaida() {
        return historicoSubstituicaoSaida;
    }

    public void setHistoricoSubstituicaoSaida(HistoricoContabil historicoSubstituicaoSaida) {
        this.historicoSubstituicaoSaida = historicoSubstituicaoSaida;
    }

    public HistoricoContabil getHistoricoRevenda() {
        return historicoRevenda;
    }

    public void setHistoricoRevenda(HistoricoContabil historicoRevenda) {
        this.historicoRevenda = historicoRevenda;
    }

    public HistoricoContabil getHistoricoIpi() {
        return historicoIpi;
    }

    public void setHistoricoIpi(HistoricoContabil historicoIpi) {
        this.historicoIpi = historicoIpi;
    }

    public HistoricoContabil getHistoricoBaixaReceber() {
        return historicoBaixaReceber;
    }

    public void setHistoricoBaixaReceber(HistoricoContabil historicoBaixaReceber) {
        this.historicoBaixaReceber = historicoBaixaReceber;
    }

    public HistoricoContabil getHistoricoBaixaPagar() {
        return historicoBaixaPagar;
    }

    public void setHistoricoBaixaPagar(HistoricoContabil historicoBaixaPagar) {
        this.historicoBaixaPagar = historicoBaixaPagar;
    }

    public HistoricoFinanceiro getHistFinBaixaReceber() {
        return histFinBaixaReceber;
    }

    public void setHistFinBaixaReceber(HistoricoFinanceiro histFinBaixaReceber) {
        this.histFinBaixaReceber = histFinBaixaReceber;
    }

    public HistoricoFinanceiro getHistFinBaixaJurosReceber() {
        return histFinBaixaJurosReceber;
    }

    public void setHistFinBaixaJurosReceber(HistoricoFinanceiro histFinBaixaJurosReceber) {
        this.histFinBaixaJurosReceber = histFinBaixaJurosReceber;
    }

    public HistoricoFinanceiro getHistFinBaixaDescontoReceber() {
        return histFinBaixaDescontoReceber;
    }

    public void setHistFinBaixaDescontoReceber(HistoricoFinanceiro histFinBaixaDescontoReceber) {
        this.histFinBaixaDescontoReceber = histFinBaixaDescontoReceber;
    }

    public HistoricoFinanceiro getHistFinBaixaPagar() {
        return histFinBaixaPagar;
    }

    public void setHistFinBaixaPagar(HistoricoFinanceiro histFinBaixaPagar) {
        this.histFinBaixaPagar = histFinBaixaPagar;
    }

    public HistoricoFinanceiro getHistFinBaixaJurosPagar() {
        return histFinBaixaJurosPagar;
    }

    public void setHistFinBaixaJurosPagar(HistoricoFinanceiro histFinBaixaJurosPagar) {
        this.histFinBaixaJurosPagar = histFinBaixaJurosPagar;
    }

    public HistoricoFinanceiro getHistFinBaixaDescontoPagar() {
        return histFinBaixaDescontoPagar;
    }

    public void setHistFinBaixaDescontoPagar(HistoricoFinanceiro histFinBaixaDescontoPagar) {
        this.histFinBaixaDescontoPagar = histFinBaixaDescontoPagar;
    }

    public OffsetDateTime getDeletedDate() {
        return deletedDate;
    }

    public void setDeletedDate(OffsetDateTime deletedDate) {
        this.deletedDate = deletedDate;
    }

    public String getDeletedBy() {
        return deletedBy;
    }

    public void setDeletedBy(String deletedBy) {
        this.deletedBy = deletedBy;
    }

    public OffsetDateTime getLastModifiedDate() {
        return lastModifiedDate;
    }

    public void setLastModifiedDate(OffsetDateTime lastModifiedDate) {
        this.lastModifiedDate = lastModifiedDate;
    }

    public String getLastModifiedBy() {
        return lastModifiedBy;
    }

    public void setLastModifiedBy(String lastModifiedBy) {
        this.lastModifiedBy = lastModifiedBy;
    }

    public OffsetDateTime getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(OffsetDateTime createdDate) {
        this.createdDate = createdDate;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    @InstanceName
    @DependsOnProperties({"codigo", "nome"})
    public String getInstanceName(MetadataTools metadataTools, DatatypeFormatter datatypeFormatter) {
        return String.format("%s %s",
                datatypeFormatter.formatInteger(codigo),
                metadataTools.format(nome));
    }
}