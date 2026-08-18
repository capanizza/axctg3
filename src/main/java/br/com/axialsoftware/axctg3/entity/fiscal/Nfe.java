package br.com.axialsoftware.axctg3.entity.fiscal;

import io.jmix.core.DeletePolicy;
import io.jmix.core.annotation.DeletedBy;
import io.jmix.core.annotation.DeletedDate;
import io.jmix.core.entity.annotation.JmixGeneratedValue;
import io.jmix.core.entity.annotation.OnDelete;
import io.jmix.core.metamodel.annotation.Composition;
import io.jmix.core.metamodel.annotation.InstanceName;
import io.jmix.core.metamodel.annotation.JmixEntity;
import io.jmix.core.metamodel.annotation.NumberFormat;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Nota Fiscal Eletrônica — entidade <b>separada</b> de {@link NotaSaida}, ligada só pela
 * chave de acesso ({@link NotaSaida#getChave()} == {@link #getChave()}, sem FK entre as
 * duas), conforme decidido pelo usuário em 2026-08-09. Cobre o leiaute oficial 4.00 da
 * NFe (grupos {@code ide}/{@code emit}/{@code dest}/{@code det}/{@code total}/
 * {@code transp}/{@code cobr}/{@code pag}/{@code infAdic}/{@code protNFe}), com os
 * campos de {@code emit}/{@code dest} guardados como <b>snapshot</b> (cópia do que foi
 * efetivamente transmitido) — não FK pra {@code Empresa}/{@code Parceiro}, porque o
 * schema em si não referencia entidades internas e porque dados cadastrais podem mudar
 * depois da nota emitida sem que a NFe histórica deva mudar junto.
 *
 * <p><b>Escopo deliberadamente de fora</b> (grupos do leiaute pouco usados por uma
 * revenda/indústria genérica, não modelados aqui): {@code NFref} (notas referenciadas),
 * {@code retirada}/{@code entrega} (locais alternativos), {@code autXML},
 * {@code detExport}/{@code exporta} (exportação), {@code compra} (empenho público),
 * {@code cana} (agroindústria canavieira), {@code rastro}/{@code med}/{@code arma}/
 * {@code veicProd}/{@code comb} (rastreabilidade e produtos regulados específicos),
 * {@code ISSQNtot}/{@code infIntermed} (prestação de serviço/intermediador),
 * {@code infRespTec}, {@code reboque}/{@code vagao}/{@code balsa} (multimodal), NVE/DI.
 * Até 2026-08-17 esta entidade era só de registro/consulta, sem integração com a SEFAZ
 * — decisão revertida nessa data (docs/EMISSAO-NFE.md): a emissão própria de NFe
 * ({@code NfeEmissaoService}) gera, assina e transmite a partir de {@code NotaSaida}, e
 * populada aqui com o mesmo {@code NfeXmlParser} que já lia XML importado — uma NFe
 * emitida pelo próprio axctg3 vira uma linha igual a uma importada, sem mapeador
 * duplicado. A ligação com {@code NotaSaida} continua só pela chave, sem FK, pelo mesmo
 * motivo de antes.
 *
 * <p>Campos percentuais ({@code p*}) usam 4 casas decimais (padrão do leiaute SEFAZ,
 * igual a {@link br.com.axialsoftware.axctg3.entity.tabelas.ClassTrib}), diferente do
 * padrão de 2 casas usado em {@link NotaSaida}/{@link Produto} — aqui a fidelidade ao
 * schema oficial pesa mais que a consistência com o resto do projeto.
 *
 * <p><b>Reforma Tributária (IBS/CBS/Imposto Seletivo — LC 214/2025, NT 2025.002-RTC)</b>: o
 * total da nota (grupo {@code W03}/{@code IBSCBSTot}/{@code ISTot}) está coberto — soma de
 * {@code vIBSUF}/{@code vIBSMun}/{@code vIBS}/{@code vCBS}/{@code vIS} e {@code vNFTot}. O
 * detalhamento por tributo (alíquota, base, diferimento etc.) fica em {@link NfeItem} (grupo
 * {@code UB}). <b>Fora de escopo</b> dentro da reforma, mesmo critério acima: totais de
 * crédito presumido, tributação monofásica de combustíveis ({@code gMono}) e estorno de
 * crédito ({@code gEstornoCred}) — ver o Javadoc de {@link NfeItem} para a lista completa.
 */
@JmixEntity
@Table(name = "NFE", indexes = {
        @Index(name = "IDX_NFE_UNQ_CHAVE", columnList = "CHAVE", unique = true),
        @Index(name = "IDX_NFE_COD_EMPRESA", columnList = "COD_EMPRESA"),
        @Index(name = "IDX_NFE_DH_EMI", columnList = "DH_EMI")
})
@Entity
public class Nfe {
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

    // chave de acesso completa (44 dígitos) — chave de negócio da NFe; é por este valor
    // que NotaSaida.chave "encontra" o registro correspondente aqui
    @InstanceName
    @Column(name = "CHAVE", nullable = false, length = 44)
    @NotNull
    private String chave;

    // sem valor padrão: o NfeEventListener preenche com a empresa corrente quando nulo
    @Column(name = "COD_EMPRESA", nullable = false)
    @NotNull
    private Integer codEmpresa;

    // ---- ide (Identificação da NF-e) ----

    @Column(name = "C_UF")
    private Integer codUf;

    @Column(name = "C_NF")
    private Integer codNf;

    @Column(name = "NAT_OP", length = 60)
    private String natOp;

    // modelo do documento fiscal: sempre 55 (NFe) ou 65 (NFCe)
    @Column(name = "MOD")
    private Integer mod = 55;

    @Column(name = "SERIE")
    private Integer serie;

    @Column(name = "N_NF")
    private Integer numeroNf;

    @Column(name = "DH_EMI")
    private OffsetDateTime dhEmi;

    @Column(name = "DH_SAI_ENT")
    private OffsetDateTime dhSaiEnt;

    // 0=entrada, 1=saída
    @Column(name = "TP_NF")
    private Integer tpNf;

    // 1=operação interna, 2=interestadual, 3=exterior
    @Column(name = "ID_DEST")
    private Integer idDest;

    // código IBGE do município do fato gerador
    @Column(name = "C_MUN_FG")
    private Integer codMunFg;

    // formato de impressão do DANFE: 0=sem geração, 1=retrato, 2=paisagem...
    @Column(name = "TP_IMP")
    private Integer tpImp;

    // forma de emissão: 1=normal, 2=contingência FS-IA, 9=contingência off-line...
    @Column(name = "TP_EMIS")
    private Integer tpEmis;

    // dígito verificador da chave de acesso
    @Column(name = "C_DV")
    private Integer codDv;

    // 1=produção, 2=homologação
    @Column(name = "TP_AMB")
    private Integer tpAmb;

    // finalidade: 1=normal, 2=complementar, 3=ajuste, 4=devolução
    @Column(name = "FIN_NFE")
    private Integer finNfe;

    // indica operação com consumidor final: 0=não, 1=sim
    @Column(name = "IND_FINAL")
    private Integer indFinal;

    // indicador de presença do comprador: 0=não se aplica, 1=presencial, 2=internet...
    @Column(name = "IND_PRES")
    private Integer indPres;

    @Column(name = "PROC_EMI")
    private Integer procEmi;

    @Column(name = "VER_PROC", length = 20)
    private String verProc;

    // ---- emit (Emitente) — snapshot ----

    @Column(name = "EMIT_CNPJ", length = 14)
    private String emitCnpj;

    @Column(name = "EMIT_X_NOME", length = 60)
    private String emitXNome;

    @Column(name = "EMIT_X_FANT", length = 60)
    private String emitXFant;

    @Column(name = "EMIT_X_LGR", length = 60)
    private String emitXLgr;

    @Column(name = "EMIT_NRO", length = 10)
    private String emitNro;

    @Column(name = "EMIT_X_CPL", length = 60)
    private String emitXCpl;

    @Column(name = "EMIT_X_BAIRRO", length = 60)
    private String emitXBairro;

    @Column(name = "EMIT_C_MUN")
    private Integer emitCMun;

    @Column(name = "EMIT_X_MUN", length = 60)
    private String emitXMun;

    @Column(name = "EMIT_UF", length = 2)
    private String emitUf;

    @Column(name = "EMIT_CEP", length = 8)
    private String emitCep;

    @Column(name = "EMIT_FONE", length = 15)
    private String emitFone;

    @Column(name = "EMIT_IE", length = 14)
    private String emitIe;

    // Código de Regime Tributário: 1=Simples Nacional, 2=Simples excesso sublimite, 3=Normal
    @Column(name = "EMIT_CRT")
    private Integer emitCrt;

    // ---- dest (Destinatário) — snapshot ----

    @Column(name = "DEST_CNPJ", length = 14)
    private String destCnpj;

    @Column(name = "DEST_CPF", length = 11)
    private String destCpf;

    @Column(name = "DEST_X_NOME", length = 60)
    private String destXNome;

    @Column(name = "DEST_X_LGR", length = 60)
    private String destXLgr;

    @Column(name = "DEST_NRO", length = 10)
    private String destNro;

    @Column(name = "DEST_X_CPL", length = 60)
    private String destXCpl;

    @Column(name = "DEST_X_BAIRRO", length = 60)
    private String destXBairro;

    @Column(name = "DEST_C_MUN")
    private Integer destCMun;

    @Column(name = "DEST_X_MUN", length = 60)
    private String destXMun;

    @Column(name = "DEST_UF", length = 2)
    private String destUf;

    @Column(name = "DEST_CEP", length = 8)
    private String destCep;

    @Column(name = "DEST_FONE", length = 15)
    private String destFone;

    // indicador da IE do destinatário: 1=contribuinte ICMS, 2=isento, 9=não contribuinte
    @Column(name = "DEST_IND_IE")
    private Integer destIndIe;

    @Column(name = "DEST_IE", length = 14)
    private String destIe;

    @Column(name = "DEST_EMAIL", length = 60)
    private String destEmail;

    // ---- total (ICMSTot) ----

    @NumberFormat(pattern = "###,###,##0.00", decimalSeparator = ",", groupingSeparator = ".")
    @Column(name = "V_BC", precision = 19, scale = 2)
    private BigDecimal valorBc = BigDecimal.ZERO;

    @NumberFormat(pattern = "###,###,##0.00", decimalSeparator = ",", groupingSeparator = ".")
    @Column(name = "V_ICMS", precision = 19, scale = 2)
    private BigDecimal valorIcms = BigDecimal.ZERO;

    @NumberFormat(pattern = "###,###,##0.00", decimalSeparator = ",", groupingSeparator = ".")
    @Column(name = "V_ICMS_DESON", precision = 19, scale = 2)
    private BigDecimal valorIcmsDeson = BigDecimal.ZERO;

    @NumberFormat(pattern = "###,###,##0.00", decimalSeparator = ",", groupingSeparator = ".")
    @Column(name = "V_FCP_UF_DEST", precision = 19, scale = 2)
    private BigDecimal valorFcpUfDest = BigDecimal.ZERO;

    @NumberFormat(pattern = "###,###,##0.00", decimalSeparator = ",", groupingSeparator = ".")
    @Column(name = "V_ICMS_UF_DEST", precision = 19, scale = 2)
    private BigDecimal valorIcmsUfDest = BigDecimal.ZERO;

    @NumberFormat(pattern = "###,###,##0.00", decimalSeparator = ",", groupingSeparator = ".")
    @Column(name = "V_ICMS_UF_REMET", precision = 19, scale = 2)
    private BigDecimal valorIcmsUfRemet = BigDecimal.ZERO;

    @NumberFormat(pattern = "###,###,##0.00", decimalSeparator = ",", groupingSeparator = ".")
    @Column(name = "V_FCP", precision = 19, scale = 2)
    private BigDecimal valorFcp = BigDecimal.ZERO;

    @NumberFormat(pattern = "###,###,##0.00", decimalSeparator = ",", groupingSeparator = ".")
    @Column(name = "V_BC_ST", precision = 19, scale = 2)
    private BigDecimal valorBcSt = BigDecimal.ZERO;

    @NumberFormat(pattern = "###,###,##0.00", decimalSeparator = ",", groupingSeparator = ".")
    @Column(name = "V_ST", precision = 19, scale = 2)
    private BigDecimal valorSt = BigDecimal.ZERO;

    @NumberFormat(pattern = "###,###,##0.00", decimalSeparator = ",", groupingSeparator = ".")
    @Column(name = "V_FCP_ST", precision = 19, scale = 2)
    private BigDecimal valorFcpSt = BigDecimal.ZERO;

    @NumberFormat(pattern = "###,###,##0.00", decimalSeparator = ",", groupingSeparator = ".")
    @Column(name = "V_FCP_ST_RET", precision = 19, scale = 2)
    private BigDecimal valorFcpStRet = BigDecimal.ZERO;

    @NumberFormat(pattern = "###,###,##0.00", decimalSeparator = ",", groupingSeparator = ".")
    @Column(name = "V_PROD", precision = 19, scale = 2)
    private BigDecimal valorProd = BigDecimal.ZERO;

    @NumberFormat(pattern = "###,###,##0.00", decimalSeparator = ",", groupingSeparator = ".")
    @Column(name = "V_FRETE", precision = 19, scale = 2)
    private BigDecimal valorFrete = BigDecimal.ZERO;

    @NumberFormat(pattern = "###,###,##0.00", decimalSeparator = ",", groupingSeparator = ".")
    @Column(name = "V_SEG", precision = 19, scale = 2)
    private BigDecimal valorSeg = BigDecimal.ZERO;

    @NumberFormat(pattern = "###,###,##0.00", decimalSeparator = ",", groupingSeparator = ".")
    @Column(name = "V_DESC", precision = 19, scale = 2)
    private BigDecimal valorDesc = BigDecimal.ZERO;

    @NumberFormat(pattern = "###,###,##0.00", decimalSeparator = ",", groupingSeparator = ".")
    @Column(name = "V_II", precision = 19, scale = 2)
    private BigDecimal valorIi = BigDecimal.ZERO;

    @NumberFormat(pattern = "###,###,##0.00", decimalSeparator = ",", groupingSeparator = ".")
    @Column(name = "V_IPI", precision = 19, scale = 2)
    private BigDecimal valorIpi = BigDecimal.ZERO;

    @NumberFormat(pattern = "###,###,##0.00", decimalSeparator = ",", groupingSeparator = ".")
    @Column(name = "V_IPI_DEVOL", precision = 19, scale = 2)
    private BigDecimal valorIpiDevol = BigDecimal.ZERO;

    @NumberFormat(pattern = "###,###,##0.00", decimalSeparator = ",", groupingSeparator = ".")
    @Column(name = "V_PIS", precision = 19, scale = 2)
    private BigDecimal valorPis = BigDecimal.ZERO;

    @NumberFormat(pattern = "###,###,##0.00", decimalSeparator = ",", groupingSeparator = ".")
    @Column(name = "V_COFINS", precision = 19, scale = 2)
    private BigDecimal valorCofins = BigDecimal.ZERO;

    @NumberFormat(pattern = "###,###,##0.00", decimalSeparator = ",", groupingSeparator = ".")
    @Column(name = "V_OUTRO", precision = 19, scale = 2)
    private BigDecimal valorOutro = BigDecimal.ZERO;

    @NumberFormat(pattern = "###,###,##0.00", decimalSeparator = ",", groupingSeparator = ".")
    @Column(name = "V_NF", precision = 19, scale = 2)
    private BigDecimal valorNf = BigDecimal.ZERO;

    @NumberFormat(pattern = "###,###,##0.00", decimalSeparator = ",", groupingSeparator = ".")
    @Column(name = "V_TOT_TRIB", precision = 19, scale = 2)
    private BigDecimal valorTotTrib = BigDecimal.ZERO;

    // ---- IBSCBSTot / ISTot (Grupo W03 — Total da NF-e com IBS/CBS/IS) — Reforma Tributária,
    // LC 214/2025, NT 2025.002-RTC. Núcleo: somatório dos campos do grupo UB de NfeItem. Fora de
    // escopo (mesmo critério do restante da entidade — ver NfeItem): totais de crédito presumido,
    // tributação monofásica de combustíveis (gMono) e estorno de crédito (gEstornoCred).

    // vBCIBSCBS
    @NumberFormat(pattern = "###,###,##0.00", decimalSeparator = ",", groupingSeparator = ".")
    @Column(name = "V_BC_IBS_CBS", precision = 19, scale = 2)
    private BigDecimal valorBcIbsCbs = BigDecimal.ZERO;

    // gIBSUF/vDif (total)
    @NumberFormat(pattern = "###,###,##0.00", decimalSeparator = ",", groupingSeparator = ".")
    @Column(name = "V_DIF_IBS_UF", precision = 19, scale = 2)
    private BigDecimal valorDifIbsUf = BigDecimal.ZERO;

    // gIBSUF/vDevTrib (total)
    @NumberFormat(pattern = "###,###,##0.00", decimalSeparator = ",", groupingSeparator = ".")
    @Column(name = "V_DEV_TRIB_IBS_UF", precision = 19, scale = 2)
    private BigDecimal valorDevTribIbsUf = BigDecimal.ZERO;

    // vIBSUF (total)
    @NumberFormat(pattern = "###,###,##0.00", decimalSeparator = ",", groupingSeparator = ".")
    @Column(name = "V_IBS_UF", precision = 19, scale = 2)
    private BigDecimal valorIbsUf = BigDecimal.ZERO;

    // gIBSMun/vDif (total)
    @NumberFormat(pattern = "###,###,##0.00", decimalSeparator = ",", groupingSeparator = ".")
    @Column(name = "V_DIF_IBS_MUN", precision = 19, scale = 2)
    private BigDecimal valorDifIbsMun = BigDecimal.ZERO;

    // gIBSMun/vDevTrib (total)
    @NumberFormat(pattern = "###,###,##0.00", decimalSeparator = ",", groupingSeparator = ".")
    @Column(name = "V_DEV_TRIB_IBS_MUN", precision = 19, scale = 2)
    private BigDecimal valorDevTribIbsMun = BigDecimal.ZERO;

    // vIBSMun (total)
    @NumberFormat(pattern = "###,###,##0.00", decimalSeparator = ",", groupingSeparator = ".")
    @Column(name = "V_IBS_MUN", precision = 19, scale = 2)
    private BigDecimal valorIbsMun = BigDecimal.ZERO;

    // vIBS (total geral — soma de vIBSUF e vIBSMun)
    @NumberFormat(pattern = "###,###,##0.00", decimalSeparator = ",", groupingSeparator = ".")
    @Column(name = "V_IBS", precision = 19, scale = 2)
    private BigDecimal valorIbs = BigDecimal.ZERO;

    // gCBS/vDif (total)
    @NumberFormat(pattern = "###,###,##0.00", decimalSeparator = ",", groupingSeparator = ".")
    @Column(name = "V_DIF_CBS", precision = 19, scale = 2)
    private BigDecimal valorDifCbs = BigDecimal.ZERO;

    // gCBS/vDevTrib (total)
    @NumberFormat(pattern = "###,###,##0.00", decimalSeparator = ",", groupingSeparator = ".")
    @Column(name = "V_DEV_TRIB_CBS", precision = 19, scale = 2)
    private BigDecimal valorDevTribCbs = BigDecimal.ZERO;

    // vCBS (total)
    @NumberFormat(pattern = "###,###,##0.00", decimalSeparator = ",", groupingSeparator = ".")
    @Column(name = "V_CBS", precision = 19, scale = 2)
    private BigDecimal valorCbs = BigDecimal.ZERO;

    // vIS (total do Imposto Seletivo)
    @NumberFormat(pattern = "###,###,##0.00", decimalSeparator = ",", groupingSeparator = ".")
    @Column(name = "V_IS", precision = 19, scale = 2)
    private BigDecimal valorIs = BigDecimal.ZERO;

    // vNFTot — valor total da NF-e já somando IBS/CBS/IS (tributos "por fora")
    @NumberFormat(pattern = "###,###,##0.00", decimalSeparator = ",", groupingSeparator = ".")
    @Column(name = "V_NF_TOT", precision = 19, scale = 2)
    private BigDecimal valorNfTot = BigDecimal.ZERO;

    // ---- transp (Transporte) — só transportadora/veículo únicos (sem reboque/vagão/balsa) ----

    // 0=emitente, 1=destinatário, 2=terceiros, 3=próprio remetente, 4=próprio destinatário, 9=sem frete
    @Column(name = "MOD_FRETE")
    private Integer modFrete;

    @Column(name = "TRANSP_CNPJ", length = 14)
    private String transpCnpj;

    @Column(name = "TRANSP_CPF", length = 11)
    private String transpCpf;

    @Column(name = "TRANSP_X_NOME", length = 60)
    private String transpXNome;

    @Column(name = "TRANSP_IE", length = 14)
    private String transpIe;

    @Column(name = "TRANSP_X_ENDER", length = 60)
    private String transpXEnder;

    @Column(name = "TRANSP_X_MUN", length = 60)
    private String transpXMun;

    @Column(name = "TRANSP_UF", length = 2)
    private String transpUf;

    @Column(name = "VEIC_PLACA", length = 7)
    private String veicPlaca;

    @Column(name = "VEIC_UF", length = 2)
    private String veicUf;

    @Column(name = "VEIC_RNTC", length = 20)
    private String veicRntc;

    // ---- cobr (Cobrança) — dados da fatura; parcelas em NfeDuplicata ----

    @Column(name = "NUM_FAT", length = 60)
    private String numFat;

    @NumberFormat(pattern = "###,###,##0.00", decimalSeparator = ",", groupingSeparator = ".")
    @Column(name = "VALOR_ORIG_FAT", precision = 19, scale = 2)
    private BigDecimal valorOrigFat = BigDecimal.ZERO;

    @NumberFormat(pattern = "###,###,##0.00", decimalSeparator = ",", groupingSeparator = ".")
    @Column(name = "VALOR_DESC_FAT", precision = 19, scale = 2)
    private BigDecimal valorDescFat = BigDecimal.ZERO;

    @NumberFormat(pattern = "###,###,##0.00", decimalSeparator = ",", groupingSeparator = ".")
    @Column(name = "VALOR_LIQ_FAT", precision = 19, scale = 2)
    private BigDecimal valorLiqFat = BigDecimal.ZERO;

    // ---- pag (Pagamento) — troco total; formas de pagamento em NfePagamento ----

    @NumberFormat(pattern = "###,###,##0.00", decimalSeparator = ",", groupingSeparator = ".")
    @Column(name = "V_TROCO", precision = 19, scale = 2)
    private BigDecimal valorTroco = BigDecimal.ZERO;

    // ---- infAdic (Informações adicionais) ----

    @Column(name = "INF_AD_FISCO")
    @Lob
    private String infAdFisco;

    @Column(name = "INF_CPL")
    @Lob
    private String infCpl;

    // ---- protNFe (Protocolo de autorização de uso) ----

    @Column(name = "PROT_TP_AMB")
    private Integer protTpAmb;

    @Column(name = "PROT_VER_APLIC", length = 20)
    private String protVerAplic;

    @Column(name = "PROT_DH_RECBTO")
    private OffsetDateTime protDhRecbto;

    @Column(name = "PROT_N_PROT", length = 15)
    private String protNProt;

    @Column(name = "PROT_DIG_VAL", length = 28)
    private String protDigVal;

    // código de status do retorno da SEFAZ (100=autorizada, 101/151=cancelada, 110=denegada...)
    @Column(name = "PROT_C_STAT")
    private Integer protCStat;

    @Column(name = "PROT_X_MOTIVO", length = 255)
    private String protXMotivo;

    // ---- XML bruto (opcional — útil pra reconsulta/reimportação) ----

    @Column(name = "XML_ENVIO")
    @Lob
    private String xmlEnvio;

    @Column(name = "XML_RETORNO")
    @Lob
    private String xmlRetorno;

    @OnDelete(DeletePolicy.CASCADE)
    @Composition
    @OrderBy("item")
    @OneToMany(mappedBy = "nfe")
    private List<NfeItem> itens;

    @OnDelete(DeletePolicy.CASCADE)
    @Composition
    @OneToMany(mappedBy = "nfe")
    private List<NfeDuplicata> duplicatas;

    @OnDelete(DeletePolicy.CASCADE)
    @Composition
    @OneToMany(mappedBy = "nfe")
    private List<NfePagamento> pagamentos;

    @OnDelete(DeletePolicy.CASCADE)
    @Composition
    @OneToMany(mappedBy = "nfe")
    private List<NfeVolume> volumes;

    public List<NfeVolume> getVolumes() {
        return volumes;
    }

    public void setVolumes(List<NfeVolume> volumes) {
        this.volumes = volumes;
    }

    public List<NfePagamento> getPagamentos() {
        return pagamentos;
    }

    public void setPagamentos(List<NfePagamento> pagamentos) {
        this.pagamentos = pagamentos;
    }

    public List<NfeDuplicata> getDuplicatas() {
        return duplicatas;
    }

    public void setDuplicatas(List<NfeDuplicata> duplicatas) {
        this.duplicatas = duplicatas;
    }

    public List<NfeItem> getItens() {
        return itens;
    }

    public void setItens(List<NfeItem> itens) {
        this.itens = itens;
    }

    public String getXmlRetorno() {
        return xmlRetorno;
    }

    public void setXmlRetorno(String xmlRetorno) {
        this.xmlRetorno = xmlRetorno;
    }

    public String getXmlEnvio() {
        return xmlEnvio;
    }

    public void setXmlEnvio(String xmlEnvio) {
        this.xmlEnvio = xmlEnvio;
    }

    public String getProtXMotivo() {
        return protXMotivo;
    }

    public void setProtXMotivo(String protXMotivo) {
        this.protXMotivo = protXMotivo;
    }

    public Integer getProtCStat() {
        return protCStat;
    }

    public void setProtCStat(Integer protCStat) {
        this.protCStat = protCStat;
    }

    public String getProtDigVal() {
        return protDigVal;
    }

    public void setProtDigVal(String protDigVal) {
        this.protDigVal = protDigVal;
    }

    public String getProtNProt() {
        return protNProt;
    }

    public void setProtNProt(String protNProt) {
        this.protNProt = protNProt;
    }

    public OffsetDateTime getProtDhRecbto() {
        return protDhRecbto;
    }

    public void setProtDhRecbto(OffsetDateTime protDhRecbto) {
        this.protDhRecbto = protDhRecbto;
    }

    public String getProtVerAplic() {
        return protVerAplic;
    }

    public void setProtVerAplic(String protVerAplic) {
        this.protVerAplic = protVerAplic;
    }

    public Integer getProtTpAmb() {
        return protTpAmb;
    }

    public void setProtTpAmb(Integer protTpAmb) {
        this.protTpAmb = protTpAmb;
    }

    public String getInfCpl() {
        return infCpl;
    }

    public void setInfCpl(String infCpl) {
        this.infCpl = infCpl;
    }

    public String getInfAdFisco() {
        return infAdFisco;
    }

    public void setInfAdFisco(String infAdFisco) {
        this.infAdFisco = infAdFisco;
    }

    public BigDecimal getValorTroco() {
        return valorTroco;
    }

    public void setValorTroco(BigDecimal valorTroco) {
        this.valorTroco = valorTroco;
    }

    public BigDecimal getValorLiqFat() {
        return valorLiqFat;
    }

    public void setValorLiqFat(BigDecimal valorLiqFat) {
        this.valorLiqFat = valorLiqFat;
    }

    public BigDecimal getValorDescFat() {
        return valorDescFat;
    }

    public void setValorDescFat(BigDecimal valorDescFat) {
        this.valorDescFat = valorDescFat;
    }

    public BigDecimal getValorOrigFat() {
        return valorOrigFat;
    }

    public void setValorOrigFat(BigDecimal valorOrigFat) {
        this.valorOrigFat = valorOrigFat;
    }

    public String getNumFat() {
        return numFat;
    }

    public void setNumFat(String numFat) {
        this.numFat = numFat;
    }

    public String getVeicRntc() {
        return veicRntc;
    }

    public void setVeicRntc(String veicRntc) {
        this.veicRntc = veicRntc;
    }

    public String getVeicUf() {
        return veicUf;
    }

    public void setVeicUf(String veicUf) {
        this.veicUf = veicUf;
    }

    public String getVeicPlaca() {
        return veicPlaca;
    }

    public void setVeicPlaca(String veicPlaca) {
        this.veicPlaca = veicPlaca;
    }

    public String getTranspUf() {
        return transpUf;
    }

    public void setTranspUf(String transpUf) {
        this.transpUf = transpUf;
    }

    public String getTranspXMun() {
        return transpXMun;
    }

    public void setTranspXMun(String transpXMun) {
        this.transpXMun = transpXMun;
    }

    public String getTranspXEnder() {
        return transpXEnder;
    }

    public void setTranspXEnder(String transpXEnder) {
        this.transpXEnder = transpXEnder;
    }

    public String getTranspIe() {
        return transpIe;
    }

    public void setTranspIe(String transpIe) {
        this.transpIe = transpIe;
    }

    public String getTranspXNome() {
        return transpXNome;
    }

    public void setTranspXNome(String transpXNome) {
        this.transpXNome = transpXNome;
    }

    public String getTranspCpf() {
        return transpCpf;
    }

    public void setTranspCpf(String transpCpf) {
        this.transpCpf = transpCpf;
    }

    public String getTranspCnpj() {
        return transpCnpj;
    }

    public void setTranspCnpj(String transpCnpj) {
        this.transpCnpj = transpCnpj;
    }

    public Integer getModFrete() {
        return modFrete;
    }

    public void setModFrete(Integer modFrete) {
        this.modFrete = modFrete;
    }

    public BigDecimal getValorTotTrib() {
        return valorTotTrib;
    }

    public void setValorTotTrib(BigDecimal valorTotTrib) {
        this.valorTotTrib = valorTotTrib;
    }

    public BigDecimal getValorBcIbsCbs() {
        return valorBcIbsCbs;
    }

    public void setValorBcIbsCbs(BigDecimal valorBcIbsCbs) {
        this.valorBcIbsCbs = valorBcIbsCbs;
    }

    public BigDecimal getValorDifIbsUf() {
        return valorDifIbsUf;
    }

    public void setValorDifIbsUf(BigDecimal valorDifIbsUf) {
        this.valorDifIbsUf = valorDifIbsUf;
    }

    public BigDecimal getValorDevTribIbsUf() {
        return valorDevTribIbsUf;
    }

    public void setValorDevTribIbsUf(BigDecimal valorDevTribIbsUf) {
        this.valorDevTribIbsUf = valorDevTribIbsUf;
    }

    public BigDecimal getValorIbsUf() {
        return valorIbsUf;
    }

    public void setValorIbsUf(BigDecimal valorIbsUf) {
        this.valorIbsUf = valorIbsUf;
    }

    public BigDecimal getValorDifIbsMun() {
        return valorDifIbsMun;
    }

    public void setValorDifIbsMun(BigDecimal valorDifIbsMun) {
        this.valorDifIbsMun = valorDifIbsMun;
    }

    public BigDecimal getValorDevTribIbsMun() {
        return valorDevTribIbsMun;
    }

    public void setValorDevTribIbsMun(BigDecimal valorDevTribIbsMun) {
        this.valorDevTribIbsMun = valorDevTribIbsMun;
    }

    public BigDecimal getValorIbsMun() {
        return valorIbsMun;
    }

    public void setValorIbsMun(BigDecimal valorIbsMun) {
        this.valorIbsMun = valorIbsMun;
    }

    public BigDecimal getValorIbs() {
        return valorIbs;
    }

    public void setValorIbs(BigDecimal valorIbs) {
        this.valorIbs = valorIbs;
    }

    public BigDecimal getValorDifCbs() {
        return valorDifCbs;
    }

    public void setValorDifCbs(BigDecimal valorDifCbs) {
        this.valorDifCbs = valorDifCbs;
    }

    public BigDecimal getValorDevTribCbs() {
        return valorDevTribCbs;
    }

    public void setValorDevTribCbs(BigDecimal valorDevTribCbs) {
        this.valorDevTribCbs = valorDevTribCbs;
    }

    public BigDecimal getValorCbs() {
        return valorCbs;
    }

    public void setValorCbs(BigDecimal valorCbs) {
        this.valorCbs = valorCbs;
    }

    public BigDecimal getValorIs() {
        return valorIs;
    }

    public void setValorIs(BigDecimal valorIs) {
        this.valorIs = valorIs;
    }

    public BigDecimal getValorNfTot() {
        return valorNfTot;
    }

    public void setValorNfTot(BigDecimal valorNfTot) {
        this.valorNfTot = valorNfTot;
    }

    public BigDecimal getValorNf() {
        return valorNf;
    }

    public void setValorNf(BigDecimal valorNf) {
        this.valorNf = valorNf;
    }

    public BigDecimal getValorOutro() {
        return valorOutro;
    }

    public void setValorOutro(BigDecimal valorOutro) {
        this.valorOutro = valorOutro;
    }

    public BigDecimal getValorCofins() {
        return valorCofins;
    }

    public void setValorCofins(BigDecimal valorCofins) {
        this.valorCofins = valorCofins;
    }

    public BigDecimal getValorPis() {
        return valorPis;
    }

    public void setValorPis(BigDecimal valorPis) {
        this.valorPis = valorPis;
    }

    public BigDecimal getValorIpiDevol() {
        return valorIpiDevol;
    }

    public void setValorIpiDevol(BigDecimal valorIpiDevol) {
        this.valorIpiDevol = valorIpiDevol;
    }

    public BigDecimal getValorIpi() {
        return valorIpi;
    }

    public void setValorIpi(BigDecimal valorIpi) {
        this.valorIpi = valorIpi;
    }

    public BigDecimal getValorIi() {
        return valorIi;
    }

    public void setValorIi(BigDecimal valorIi) {
        this.valorIi = valorIi;
    }

    public BigDecimal getValorDesc() {
        return valorDesc;
    }

    public void setValorDesc(BigDecimal valorDesc) {
        this.valorDesc = valorDesc;
    }

    public BigDecimal getValorSeg() {
        return valorSeg;
    }

    public void setValorSeg(BigDecimal valorSeg) {
        this.valorSeg = valorSeg;
    }

    public BigDecimal getValorFrete() {
        return valorFrete;
    }

    public void setValorFrete(BigDecimal valorFrete) {
        this.valorFrete = valorFrete;
    }

    public BigDecimal getValorProd() {
        return valorProd;
    }

    public void setValorProd(BigDecimal valorProd) {
        this.valorProd = valorProd;
    }

    public BigDecimal getValorFcpStRet() {
        return valorFcpStRet;
    }

    public void setValorFcpStRet(BigDecimal valorFcpStRet) {
        this.valorFcpStRet = valorFcpStRet;
    }

    public BigDecimal getValorFcpSt() {
        return valorFcpSt;
    }

    public void setValorFcpSt(BigDecimal valorFcpSt) {
        this.valorFcpSt = valorFcpSt;
    }

    public BigDecimal getValorSt() {
        return valorSt;
    }

    public void setValorSt(BigDecimal valorSt) {
        this.valorSt = valorSt;
    }

    public BigDecimal getValorBcSt() {
        return valorBcSt;
    }

    public void setValorBcSt(BigDecimal valorBcSt) {
        this.valorBcSt = valorBcSt;
    }

    public BigDecimal getValorFcp() {
        return valorFcp;
    }

    public void setValorFcp(BigDecimal valorFcp) {
        this.valorFcp = valorFcp;
    }

    public BigDecimal getValorIcmsUfRemet() {
        return valorIcmsUfRemet;
    }

    public void setValorIcmsUfRemet(BigDecimal valorIcmsUfRemet) {
        this.valorIcmsUfRemet = valorIcmsUfRemet;
    }

    public BigDecimal getValorIcmsUfDest() {
        return valorIcmsUfDest;
    }

    public void setValorIcmsUfDest(BigDecimal valorIcmsUfDest) {
        this.valorIcmsUfDest = valorIcmsUfDest;
    }

    public BigDecimal getValorFcpUfDest() {
        return valorFcpUfDest;
    }

    public void setValorFcpUfDest(BigDecimal valorFcpUfDest) {
        this.valorFcpUfDest = valorFcpUfDest;
    }

    public BigDecimal getValorIcmsDeson() {
        return valorIcmsDeson;
    }

    public void setValorIcmsDeson(BigDecimal valorIcmsDeson) {
        this.valorIcmsDeson = valorIcmsDeson;
    }

    public BigDecimal getValorIcms() {
        return valorIcms;
    }

    public void setValorIcms(BigDecimal valorIcms) {
        this.valorIcms = valorIcms;
    }

    public BigDecimal getValorBc() {
        return valorBc;
    }

    public void setValorBc(BigDecimal valorBc) {
        this.valorBc = valorBc;
    }

    public String getDestEmail() {
        return destEmail;
    }

    public void setDestEmail(String destEmail) {
        this.destEmail = destEmail;
    }

    public String getDestIe() {
        return destIe;
    }

    public void setDestIe(String destIe) {
        this.destIe = destIe;
    }

    public Integer getDestIndIe() {
        return destIndIe;
    }

    public void setDestIndIe(Integer destIndIe) {
        this.destIndIe = destIndIe;
    }

    public String getDestFone() {
        return destFone;
    }

    public void setDestFone(String destFone) {
        this.destFone = destFone;
    }

    public String getDestCep() {
        return destCep;
    }

    public void setDestCep(String destCep) {
        this.destCep = destCep;
    }

    public String getDestUf() {
        return destUf;
    }

    public void setDestUf(String destUf) {
        this.destUf = destUf;
    }

    public String getDestXMun() {
        return destXMun;
    }

    public void setDestXMun(String destXMun) {
        this.destXMun = destXMun;
    }

    public Integer getDestCMun() {
        return destCMun;
    }

    public void setDestCMun(Integer destCMun) {
        this.destCMun = destCMun;
    }

    public String getDestXBairro() {
        return destXBairro;
    }

    public void setDestXBairro(String destXBairro) {
        this.destXBairro = destXBairro;
    }

    public String getDestXCpl() {
        return destXCpl;
    }

    public void setDestXCpl(String destXCpl) {
        this.destXCpl = destXCpl;
    }

    public String getDestNro() {
        return destNro;
    }

    public void setDestNro(String destNro) {
        this.destNro = destNro;
    }

    public String getDestXLgr() {
        return destXLgr;
    }

    public void setDestXLgr(String destXLgr) {
        this.destXLgr = destXLgr;
    }

    public String getDestXNome() {
        return destXNome;
    }

    public void setDestXNome(String destXNome) {
        this.destXNome = destXNome;
    }

    public String getDestCpf() {
        return destCpf;
    }

    public void setDestCpf(String destCpf) {
        this.destCpf = destCpf;
    }

    public String getDestCnpj() {
        return destCnpj;
    }

    public void setDestCnpj(String destCnpj) {
        this.destCnpj = destCnpj;
    }

    public Integer getEmitCrt() {
        return emitCrt;
    }

    public void setEmitCrt(Integer emitCrt) {
        this.emitCrt = emitCrt;
    }

    public String getEmitIe() {
        return emitIe;
    }

    public void setEmitIe(String emitIe) {
        this.emitIe = emitIe;
    }

    public String getEmitFone() {
        return emitFone;
    }

    public void setEmitFone(String emitFone) {
        this.emitFone = emitFone;
    }

    public String getEmitCep() {
        return emitCep;
    }

    public void setEmitCep(String emitCep) {
        this.emitCep = emitCep;
    }

    public String getEmitUf() {
        return emitUf;
    }

    public void setEmitUf(String emitUf) {
        this.emitUf = emitUf;
    }

    public String getEmitXMun() {
        return emitXMun;
    }

    public void setEmitXMun(String emitXMun) {
        this.emitXMun = emitXMun;
    }

    public Integer getEmitCMun() {
        return emitCMun;
    }

    public void setEmitCMun(Integer emitCMun) {
        this.emitCMun = emitCMun;
    }

    public String getEmitXBairro() {
        return emitXBairro;
    }

    public void setEmitXBairro(String emitXBairro) {
        this.emitXBairro = emitXBairro;
    }

    public String getEmitXCpl() {
        return emitXCpl;
    }

    public void setEmitXCpl(String emitXCpl) {
        this.emitXCpl = emitXCpl;
    }

    public String getEmitNro() {
        return emitNro;
    }

    public void setEmitNro(String emitNro) {
        this.emitNro = emitNro;
    }

    public String getEmitXLgr() {
        return emitXLgr;
    }

    public void setEmitXLgr(String emitXLgr) {
        this.emitXLgr = emitXLgr;
    }

    public String getEmitXFant() {
        return emitXFant;
    }

    public void setEmitXFant(String emitXFant) {
        this.emitXFant = emitXFant;
    }

    public String getEmitXNome() {
        return emitXNome;
    }

    public void setEmitXNome(String emitXNome) {
        this.emitXNome = emitXNome;
    }

    public String getEmitCnpj() {
        return emitCnpj;
    }

    public void setEmitCnpj(String emitCnpj) {
        this.emitCnpj = emitCnpj;
    }

    public String getVerProc() {
        return verProc;
    }

    public void setVerProc(String verProc) {
        this.verProc = verProc;
    }

    public Integer getProcEmi() {
        return procEmi;
    }

    public void setProcEmi(Integer procEmi) {
        this.procEmi = procEmi;
    }

    public Integer getIndPres() {
        return indPres;
    }

    public void setIndPres(Integer indPres) {
        this.indPres = indPres;
    }

    public Integer getIndFinal() {
        return indFinal;
    }

    public void setIndFinal(Integer indFinal) {
        this.indFinal = indFinal;
    }

    public Integer getFinNfe() {
        return finNfe;
    }

    public void setFinNfe(Integer finNfe) {
        this.finNfe = finNfe;
    }

    public Integer getTpAmb() {
        return tpAmb;
    }

    public void setTpAmb(Integer tpAmb) {
        this.tpAmb = tpAmb;
    }

    public Integer getCodDv() {
        return codDv;
    }

    public void setCodDv(Integer codDv) {
        this.codDv = codDv;
    }

    public Integer getTpEmis() {
        return tpEmis;
    }

    public void setTpEmis(Integer tpEmis) {
        this.tpEmis = tpEmis;
    }

    public Integer getTpImp() {
        return tpImp;
    }

    public void setTpImp(Integer tpImp) {
        this.tpImp = tpImp;
    }

    public Integer getCodMunFg() {
        return codMunFg;
    }

    public void setCodMunFg(Integer codMunFg) {
        this.codMunFg = codMunFg;
    }

    public Integer getIdDest() {
        return idDest;
    }

    public void setIdDest(Integer idDest) {
        this.idDest = idDest;
    }

    public Integer getTpNf() {
        return tpNf;
    }

    public void setTpNf(Integer tpNf) {
        this.tpNf = tpNf;
    }

    public OffsetDateTime getDhSaiEnt() {
        return dhSaiEnt;
    }

    public void setDhSaiEnt(OffsetDateTime dhSaiEnt) {
        this.dhSaiEnt = dhSaiEnt;
    }

    public OffsetDateTime getDhEmi() {
        return dhEmi;
    }

    public void setDhEmi(OffsetDateTime dhEmi) {
        this.dhEmi = dhEmi;
    }

    public Integer getNumeroNf() {
        return numeroNf;
    }

    public void setNumeroNf(Integer numeroNf) {
        this.numeroNf = numeroNf;
    }

    public Integer getSerie() {
        return serie;
    }

    public void setSerie(Integer serie) {
        this.serie = serie;
    }

    public Integer getMod() {
        return mod;
    }

    public void setMod(Integer mod) {
        this.mod = mod;
    }

    public String getNatOp() {
        return natOp;
    }

    public void setNatOp(String natOp) {
        this.natOp = natOp;
    }

    public Integer getCodNf() {
        return codNf;
    }

    public void setCodNf(Integer codNf) {
        this.codNf = codNf;
    }

    public Integer getCodUf() {
        return codUf;
    }

    public void setCodUf(Integer codUf) {
        this.codUf = codUf;
    }

    public @NotNull Integer getCodEmpresa() {
        return codEmpresa;
    }

    public void setCodEmpresa(@NotNull Integer codEmpresa) {
        this.codEmpresa = codEmpresa;
    }

    public @NotNull String getChave() {
        return chave;
    }

    public void setChave(@NotNull String chave) {
        this.chave = chave;
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
}
