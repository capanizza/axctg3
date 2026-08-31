package br.com.axialsoftware.axctg3.service.fiscal;

import br.com.axialsoftware.axctg3.entity.fiscal.DanfeItemDto;
import br.com.axialsoftware.axctg3.entity.fiscal.Nfe;
import br.com.axialsoftware.axctg3.entity.fiscal.NfeDuplicata;
import br.com.axialsoftware.axctg3.entity.fiscal.NfeItem;
import br.com.axialsoftware.axctg3.entity.fiscal.NfeVolume;
import br.com.axialsoftware.axctg3.service.RelatorioService;
import br.com.axialsoftware.axctg3.service.UtilGeralService;
import io.jmix.core.DataManager;
import io.jmix.core.FetchPlan;
import net.sf.jasperreports.engine.JRDataSource;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Emissão do DANFE (Documento Auxiliar da Nota Fiscal Eletrônica), modelo 55, retrato — a
 * partir de uma {@link Nfe} já persistida (emitida pelo próprio sistema via
 * {@link NfeEmissaoService}, ou importada do legado via {@link NfeImportService}). Reaproveita
 * o mesmo pipeline Jasper do restante do projeto (ver {@link RelatorioService}), mas sem a
 * etapa de {@code InputDialog}/{@code ConfigRel} dos relatórios com filtro (Balancete etc.):
 * aqui não há faixa configurável, é a impressão de uma nota já selecionada na tela.
 *
 * <p>NFC-e (modelo 65) está fora de escopo — leiaute e formato de página (bobina, QR Code
 * obrigatório) são bem diferentes do modelo 55 e não há emissão de NFC-e implementada ainda.
 */
@Service
public class NfeDanfeService {

    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final DataManager dataManager;
    private final UtilGeralService utilGeralService;
    private final RelatorioService relatorioService;

    public NfeDanfeService(DataManager dataManager, UtilGeralService utilGeralService, RelatorioService relatorioService) {
        this.dataManager = dataManager;
        this.utilGeralService = utilGeralService;
        this.relatorioService = relatorioService;
    }

    /** Carrega a {@link Nfe} pelo id e emite o DANFE. Usado por {@code NfeListView}. */
    public void emitirDanfe(UUID nfeId) {
        Nfe nfe = dataManager.load(Nfe.class)
                .id(nfeId)
                .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE)
                        .add("itens", FetchPlan.BASE)
                        .add("duplicatas", FetchPlan.BASE)
                        .add("volumes", FetchPlan.BASE))
                .one();
        emitir(nfe);
    }

    /**
     * Carrega a {@link Nfe} pela chave de acesso e emite o DANFE. Usado logo após
     * {@link NfeEmissaoService#emitir(UUID)}, que só devolve a chave, não o id da {@code Nfe}.
     */
    public void emitirDanfePorChave(String chave) {
        Nfe nfe = dataManager.load(Nfe.class)
                .query("select e from Nfe e where e.chave = :chave")
                .parameter("chave", chave)
                .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE)
                        .add("itens", FetchPlan.BASE)
                        .add("duplicatas", FetchPlan.BASE)
                        .add("volumes", FetchPlan.BASE))
                .one();
        emitir(nfe);
    }

    private void emitir(Nfe nfe) {
        HashMap<String, Object> parametros = montarParametros(nfe);
        List<DanfeItemDto> itensDto = montarItensDto(nfe);
        JRDataSource dataSource = new JRBeanCollectionDataSource(itensDto);

        relatorioService.emitirRelatorio("Danfe.jasper", dataSource, parametros, "Danfe_" + nfe.getChave() + ".pdf");
    }

    private HashMap<String, Object> montarParametros(Nfe nfe) {
        var parametros = new HashMap<String, Object>();

        parametros.put("LOGO", utilGeralService.getLogoEmpresa());

        parametros.put("CHAVE", nfe.getChave());
        parametros.put("CHAVE_FORMATADA", formatarChave(nfe.getChave()));
        parametros.put("NAT_OP", nfe.getNatOp());
        parametros.put("NUMERO_NF", nfe.getNumeroNf());
        parametros.put("SERIE", nfe.getSerie());
        parametros.put("DH_EMI", formatarDataHora(nfe.getDhEmi()));
        parametros.put("DH_SAI_ENT", formatarDataHora(nfe.getDhSaiEnt()));
        parametros.put("TP_NF_DESC", tpNfDescricao(nfe.getTpNf()));
        parametros.put("TP_AMB_DESC", tpAmbDescricao(nfe.getTpAmb()));
        parametros.put("PROT_N_PROT", nfe.getProtNProt());
        parametros.put("PROT_DH_RECBTO", formatarDataHora(nfe.getProtDhRecbto()));

        parametros.put("EMIT_CNPJ", formatarCnpj(nfe.getEmitCnpj()));
        parametros.put("EMIT_X_NOME", nfe.getEmitXNome());
        parametros.put("EMIT_X_FANT", nfe.getEmitXFant());
        parametros.put("EMIT_ENDERECO", enderecoCompleto(nfe.getEmitXLgr(), nfe.getEmitNro(), nfe.getEmitXCpl(), nfe.getEmitXBairro()));
        parametros.put("EMIT_MUN_UF", municipioUf(nfe.getEmitXMun(), nfe.getEmitUf()));
        parametros.put("EMIT_CEP", formatarCep(nfe.getEmitCep()));
        parametros.put("EMIT_FONE", nfe.getEmitFone());
        parametros.put("EMIT_IE", nfe.getEmitIe());

        parametros.put("DEST_DOC", nfe.getDestCnpj() != null ? formatarCnpj(nfe.getDestCnpj()) : formatarCpf(nfe.getDestCpf()));
        parametros.put("DEST_X_NOME", nfe.getDestXNome());
        parametros.put("DEST_ENDERECO", enderecoCompleto(nfe.getDestXLgr(), nfe.getDestNro(), nfe.getDestXCpl(), nfe.getDestXBairro()));
        parametros.put("DEST_MUN_UF", municipioUf(nfe.getDestXMun(), nfe.getDestUf()));
        parametros.put("DEST_CEP", formatarCep(nfe.getDestCep()));
        parametros.put("DEST_FONE", nfe.getDestFone());
        parametros.put("DEST_IE", nfe.getDestIe());

        parametros.put("TRANSP_X_NOME", nfe.getTranspXNome());
        parametros.put("TRANSP_ENDERECO", nfe.getTranspXEnder());
        parametros.put("TRANSP_MUN_UF", municipioUf(nfe.getTranspXMun(), nfe.getTranspUf()));
        parametros.put("TRANSP_DOC", nfe.getTranspCnpj() != null ? formatarCnpj(nfe.getTranspCnpj()) : formatarCpf(nfe.getTranspCpf()));
        parametros.put("TRANSP_IE", nfe.getTranspIe());
        parametros.put("MOD_FRETE_DESC", modFreteDescricao(nfe.getModFrete()));
        parametros.put("VEIC_PLACA_UF", nfe.getVeicPlaca() == null ? "" : nfe.getVeicPlaca() + "/" + nfe.getVeicUf());
        parametros.put("VOLUMES", formatarVolumes(nfe.getVolumes()));

        parametros.put("DUPLICATAS", formatarDuplicatas(nfe.getDuplicatas()));

        parametros.put("VALOR_BC", nfe.getValorBc());
        parametros.put("VALOR_ICMS", nfe.getValorIcms());
        parametros.put("VALOR_BC_ST", nfe.getValorBcSt());
        parametros.put("VALOR_ST", nfe.getValorSt());
        parametros.put("VALOR_PROD", nfe.getValorProd());
        parametros.put("VALOR_FRETE", nfe.getValorFrete());
        parametros.put("VALOR_SEG", nfe.getValorSeg());
        parametros.put("VALOR_DESC", nfe.getValorDesc());
        parametros.put("VALOR_OUTRO", nfe.getValorOutro());
        parametros.put("VALOR_IPI", nfe.getValorIpi());
        parametros.put("VALOR_NF", nfe.getValorNf());

        parametros.put("INF_CPL", nfe.getInfCpl());

        return parametros;
    }

    private List<DanfeItemDto> montarItensDto(Nfe nfe) {
        List<DanfeItemDto> itensDto = new ArrayList<>();
        List<NfeItem> itens = nfe.getItens();
        if (itens == null) {
            return itensDto;
        }
        for (NfeItem item : itens) {
            DanfeItemDto dto = dataManager.create(DanfeItemDto.class);
            dto.setItem(item.getItem());
            dto.setCodProd(item.getCodProd());
            dto.setDescProd(item.getDescProd());
            dto.setNcm(item.getNcm());
            dto.setCst(item.getCstIcms() != null ? item.getCstIcms() : item.getCsosnIcms());
            dto.setCfop(item.getCfop());
            dto.setUnCom(item.getUnCom());
            dto.setQuantCom(item.getQuantCom());
            dto.setValorUnCom(item.getValorUnCom());
            dto.setValorProd(item.getValorProd());
            dto.setBaseIcms(item.getBaseIcms());
            dto.setValorIcms(item.getValorIcms());
            dto.setValorIpi(item.getValorIpi());
            dto.setAliqIcms(item.getAliqIcms());
            dto.setAliqIpi(item.getAliqIpi());
            itensDto.add(dto);
        }
        return itensDto;
    }

    private static String formatarChave(String chave) {
        if (chave == null || chave.length() != 44) {
            return chave;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 44; i += 4) {
            if (i > 0) {
                sb.append(' ');
            }
            sb.append(chave, i, i + 4);
        }
        return sb.toString();
    }

    private static String formatarDataHora(OffsetDateTime data) {
        return data == null ? "" : data.format(DATA_HORA);
    }

    private static String formatarCnpj(String cnpj) {
        if (cnpj == null || cnpj.length() != 14) {
            return cnpj;
        }
        return cnpj.substring(0, 2) + "." + cnpj.substring(2, 5) + "." + cnpj.substring(5, 8)
                + "/" + cnpj.substring(8, 12) + "-" + cnpj.substring(12, 14);
    }

    private static String formatarCpf(String cpf) {
        if (cpf == null || cpf.length() != 11) {
            return cpf;
        }
        return cpf.substring(0, 3) + "." + cpf.substring(3, 6) + "." + cpf.substring(6, 9)
                + "-" + cpf.substring(9, 11);
    }

    private static String formatarCep(String cep) {
        if (cep == null || cep.length() != 8) {
            return cep;
        }
        return cep.substring(0, 5) + "-" + cep.substring(5, 8);
    }

    private static String enderecoCompleto(String logradouro, String numero, String complemento, String bairro) {
        StringBuilder sb = new StringBuilder();
        if (logradouro != null) {
            sb.append(logradouro);
        }
        if (numero != null) {
            sb.append(", ").append(numero);
        }
        if (complemento != null && !complemento.isBlank()) {
            sb.append(" - ").append(complemento);
        }
        if (bairro != null && !bairro.isBlank()) {
            sb.append(" - ").append(bairro);
        }
        return sb.toString();
    }

    private static String municipioUf(String municipio, String uf) {
        if (municipio == null) {
            return uf == null ? "" : uf;
        }
        return uf == null ? municipio : municipio + "/" + uf;
    }

    private static String tpNfDescricao(Integer tpNf) {
        if (tpNf == null) {
            return "";
        }
        return tpNf == 0 ? "0 - ENTRADA" : "1 - SAÍDA";
    }

    private static String tpAmbDescricao(Integer tpAmb) {
        if (tpAmb == null) {
            return "";
        }
        return tpAmb == 1 ? "PRODUÇÃO" : "HOMOLOGAÇÃO - SEM VALOR FISCAL";
    }

    private static String modFreteDescricao(Integer modFrete) {
        if (modFrete == null) {
            return "";
        }
        return switch (modFrete) {
            case 0 -> "0 - Contratação do Frete por conta do Remetente (CIF)";
            case 1 -> "1 - Contratação do Frete por conta do Destinatário (FOB)";
            case 2 -> "2 - Contratação do Frete por conta de Terceiros";
            case 3 -> "3 - Transporte Próprio por conta do Remetente";
            case 4 -> "4 - Transporte Próprio por conta do Destinatário";
            default -> "9 - Sem Ocorrência de Transporte";
        };
    }

    private static String formatarVolumes(List<NfeVolume> volumes) {
        if (volumes == null || volumes.isEmpty()) {
            return "";
        }
        return volumes.stream()
                .map(v -> String.format("%s  %s  %s  %s  Peso líq.: %s  Peso bruto: %s",
                        v.getQuantVol() == null ? "" : v.getQuantVol(),
                        v.getEspecie() == null ? "" : v.getEspecie(),
                        v.getMarca() == null ? "" : v.getMarca(),
                        v.getNumeracao() == null ? "" : v.getNumeracao(),
                        v.getPesoLiquido() == null ? BigDecimal.ZERO : v.getPesoLiquido(),
                        v.getPesoBruto() == null ? BigDecimal.ZERO : v.getPesoBruto()))
                .collect(Collectors.joining("\n"));
    }

    private static String formatarDuplicatas(List<NfeDuplicata> duplicatas) {
        if (duplicatas == null || duplicatas.isEmpty()) {
            return "";
        }
        return duplicatas.stream()
                .map(d -> String.format("%-10s %s   %s",
                        d.getNumDup() == null ? "" : d.getNumDup(),
                        d.getDataVenc() == null ? "" : d.getDataVenc().format(DATA),
                        d.getValorDup() == null ? BigDecimal.ZERO : d.getValorDup()))
                .collect(Collectors.joining("\n"));
    }
}
