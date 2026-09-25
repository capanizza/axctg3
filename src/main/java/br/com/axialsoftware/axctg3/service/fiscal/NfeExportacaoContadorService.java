package br.com.axialsoftware.axctg3.service.fiscal;

import br.com.axialsoftware.axctg3.entity.cadastros.Empresa;
import br.com.axialsoftware.axctg3.entity.fiscal.Nfe;
import br.com.axialsoftware.axctg3.entity.fiscal.NfeCartaCorrecao;
import br.com.axialsoftware.axctg3.entity.fiscal.NfeInutilizacao;
import br.com.axialsoftware.axctg3.service.RelatorioService;
import br.com.axialsoftware.axctg3.service.UtilGeralService;
import io.jmix.core.DataManager;
import io.jmix.core.FetchPlan;
import io.jmix.core.entity.KeyValueEntity;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Monta o zip mensal de XMLs pro contador (NfeListView, "Exportar XMLs do período"):
 * <pre>
 *   nfe/&lt;chave&gt;-nfe.xml                   nfeProc de cada NFe emitida pelo sistema no período
 *   canceladas/&lt;chave&gt;-can.xml            procEventoNFe dos cancelamentos do período
 *   cartas-correcao/&lt;chave&gt;-cce-&lt;seq&gt;.xml  retorno da SEFAZ das CC-e do período
 *   inutilizacoes/inut-&lt;ano&gt;-&lt;serie&gt;-&lt;ini&gt;-&lt;fim&gt;.xml  retorno da SEFAZ
 *   Conferencia.pdf                          relatório de conferência (ExportacaoContador.jasper)
 * </pre>
 * Só produção (tpAmb=1). NFe importada não entra (não guarda XML — já veio de um XML).
 * Cada evento entra pela <b>própria</b> data, não pela da nota: uma CC-e de outubro sobre
 * nota de setembro vai no zip de outubro, e o de setembro, já enviado, não muda. NFe emitida
 * e cancelada no período aparece nas duas pastas.
 */
@Service
public class NfeExportacaoContadorService {

    /** Mesmo fuso usado pra gravar cancDhRegEvento/cceDhRegEvento (NfeCancelamentoService). */
    private static final ZoneOffset FUSO = ZoneOffset.of("-03:00");
    private static final DateTimeFormatter DATA_BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final int AMBIENTE_PRODUCAO = 1;
    private static final int PROT_AUTORIZADA = 100;
    private static final int PROT_CANCELADA = 101;
    private static final int EVENTO_REGISTRADO = 135;
    private static final int INUTILIZACAO_HOMOLOGADA = 102;
    private static final int FIN_NFE_COMPLEMENTAR = 2;

    private final DataManager dataManager;
    private final UtilGeralService utilGeralService;
    private final RelatorioService relatorioService;

    public NfeExportacaoContadorService(DataManager dataManager, UtilGeralService utilGeralService,
                                        RelatorioService relatorioService) {
        this.dataManager = dataManager;
        this.utilGeralService = utilGeralService;
        this.relatorioService = relatorioService;
    }

    public record Resultado(byte[] zip, String nomeArquivo, int nfes, int canceladas, int cartasCorrecao,
                            int inutilizacoes) {
        public boolean vazio() {
            return nfes + canceladas + cartasCorrecao + inutilizacoes == 0;
        }
    }

    /** Período fechado nas duas pontas ({@code inicio} e {@code fim} inclusos). */
    public Resultado exportar(LocalDate inicio, LocalDate fim) {
        Integer codEmpresa = utilGeralService.getCodEmpresa();
        OffsetDateTime de = inicio.atStartOfDay().atOffset(FUSO);
        OffsetDateTime ate = fim.plusDays(1).atStartOfDay().atOffset(FUSO);

        List<Nfe> nfes = buscarNfes(codEmpresa, de, ate);
        List<Nfe> canceladas = buscarCanceladas(codEmpresa, de, ate);
        List<NfeCartaCorrecao> cartas = buscarCartasCorrecao(codEmpresa, de, ate);
        List<NfeInutilizacao> inutilizacoes = buscarInutilizacoes(codEmpresa, de, ate);

        Empresa empresa = utilGeralService.getEmpresa();
        String periodo = descreverPeriodo(inicio, fim);
        String nomeArquivo = "NFe_" + (empresa.getCnpj() == null ? codEmpresa : empresa.getCnpj())
                + "_" + periodo + ".zip";

        if (nfes.isEmpty() && canceladas.isEmpty() && cartas.isEmpty() && inutilizacoes.isEmpty()) {
            return new Resultado(null, nomeArquivo, 0, 0, 0, 0);
        }

        List<ExportacaoContadorLinha> linhas = new ArrayList<>();
        Map<UUID, String> cfops = buscarCfops(codEmpresa, de, ate);

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
            for (Nfe nfe : nfes) {
                adicionar(zip, "nfe/" + nfe.getChave() + "-nfe.xml", nfe.getXmlRetorno());
                linhas.add(linhaNfe(nfe, cfops.getOrDefault(nfe.getId(), "")));
            }
            for (Nfe nfe : canceladas) {
                adicionar(zip, "canceladas/" + nfe.getChave() + "-can.xml", nfe.getCancXmlRetorno());
                linhas.add(new ExportacaoContadorLinha(2, "NFe canceladas", texto(nfe.getNumeroNf()),
                        texto(nfe.getSerie()), data(nfe.getCancDhRegEvento()), nfe.getDestXNome(),
                        nfe.getCancXJust(), nfe.getValorNf(), nvl(nfe.getValorNf())));
            }
            for (NfeCartaCorrecao cce : cartas) {
                Nfe nfe = cce.getNfe();
                adicionar(zip, "cartas-correcao/" + nfe.getChave() + "-cce-" + cce.getNumeroSequencial() + ".xml",
                        cce.getCceXmlRetorno());
                linhas.add(new ExportacaoContadorLinha(3, "Cartas de correção", texto(nfe.getNumeroNf()),
                        texto(nfe.getSerie()), data(cce.getCceDhRegEvento()), nfe.getDestXNome(),
                        "Seq. " + cce.getNumeroSequencial() + ": " + cce.getTextoCorrecao(), null, BigDecimal.ZERO));
            }
            for (NfeInutilizacao inut : inutilizacoes) {
                adicionar(zip, "inutilizacoes/inut-" + inut.getAno() + "-" + inut.getSerie() + "-"
                        + inut.getNumeroInicial() + "-" + inut.getNumeroFinal() + ".xml", inut.getXmlRetorno());
                linhas.add(new ExportacaoContadorLinha(4, "Inutilizações",
                        inut.getNumeroInicial() + " a " + inut.getNumeroFinal(), texto(inut.getSerie()),
                        data(inut.getDhRecbto()), inut.getJustificativa(), "Protocolo " + inut.getRetNProt(),
                        null, BigDecimal.ZERO));
            }

            HashMap<String, Object> parametros = new HashMap<>();
            parametros.put("TITULO_RELATORIO", "Conferência de XMLs de NFe");
            parametros.put("NOME_EMPRESA", empresa.getNome());
            parametros.put("PERIODO_RELATORIO", "Período: " + inicio.format(DATA_BR) + " a " + fim.format(DATA_BR));
            parametros.put("LOGO", utilGeralService.getLogoEmpresa());
            byte[] pdf = relatorioService.gerarRelatorioPdf("ExportacaoContador.jasper",
                    new JRBeanCollectionDataSource(linhas), parametros);
            zip.putNextEntry(new ZipEntry("Conferencia_" + periodo + ".pdf"));
            zip.write(pdf);
            zip.closeEntry();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        return new Resultado(bytes.toByteArray(), nomeArquivo, nfes.size(), canceladas.size(), cartas.size(),
                inutilizacoes.size());
    }

    /** NFe emitidas pelo sistema (têm o nfeProc gravado) — inclui as canceladas depois. */
    private List<Nfe> buscarNfes(Integer codEmpresa, OffsetDateTime de, OffsetDateTime ate) {
        return dataManager.load(Nfe.class)
                .query("select e from Nfe e where e.codEmpresa = :codEmpresa and e.protTpAmb = :producao "
                        + "and e.protCStat in :situacoes and e.xmlRetorno is not null "
                        + "and e.dhEmi >= :de and e.dhEmi < :ate order by e.serie, e.numeroNf")
                .parameter("codEmpresa", codEmpresa)
                .parameter("producao", AMBIENTE_PRODUCAO)
                .parameter("situacoes", List.of(PROT_AUTORIZADA, PROT_CANCELADA))
                .parameter("de", de)
                .parameter("ate", ate)
                .list();
    }

    private List<Nfe> buscarCanceladas(Integer codEmpresa, OffsetDateTime de, OffsetDateTime ate) {
        return dataManager.load(Nfe.class)
                .query("select e from Nfe e where e.codEmpresa = :codEmpresa and e.protTpAmb = :producao "
                        + "and e.cancCStat = :registrado and e.cancXmlRetorno is not null "
                        + "and e.cancDhRegEvento >= :de and e.cancDhRegEvento < :ate order by e.serie, e.numeroNf")
                .parameter("codEmpresa", codEmpresa)
                .parameter("producao", AMBIENTE_PRODUCAO)
                .parameter("registrado", EVENTO_REGISTRADO)
                .parameter("de", de)
                .parameter("ate", ate)
                .list();
    }

    private List<NfeCartaCorrecao> buscarCartasCorrecao(Integer codEmpresa, OffsetDateTime de, OffsetDateTime ate) {
        return dataManager.load(NfeCartaCorrecao.class)
                .query("select e from NfeCartaCorrecao e where e.nfe.codEmpresa = :codEmpresa "
                        + "and e.nfe.protTpAmb = :producao and e.cceCStat = :registrado "
                        + "and e.cceXmlRetorno is not null "
                        + "and e.cceDhRegEvento >= :de and e.cceDhRegEvento < :ate "
                        + "order by e.nfe.numeroNf, e.numeroSequencial")
                .parameter("codEmpresa", codEmpresa)
                .parameter("producao", AMBIENTE_PRODUCAO)
                .parameter("registrado", EVENTO_REGISTRADO)
                .parameter("de", de)
                .parameter("ate", ate)
                .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE).add("nfe", FetchPlan.BASE))
                .list();
    }

    /**
     * NfeInutilizacao não grava o ambiente em coluna própria — o tpAmb vem do retInutNFe
     * guardado em xmlRetorno.
     */
    private List<NfeInutilizacao> buscarInutilizacoes(Integer codEmpresa, OffsetDateTime de, OffsetDateTime ate) {
        return dataManager.load(NfeInutilizacao.class)
                .query("select e from NfeInutilizacao e where e.codEmpresa = :codEmpresa "
                        + "and e.retCStat = :homologada and e.xmlRetorno is not null "
                        + "and e.dhRecbto >= :de and e.dhRecbto < :ate "
                        + "order by e.serie, e.numeroInicial")
                .parameter("codEmpresa", codEmpresa)
                .parameter("homologada", INUTILIZACAO_HOMOLOGADA)
                .parameter("de", de)
                .parameter("ate", ate)
                .list()
                .stream()
                .filter(i -> i.getXmlRetorno().contains("<tpAmb>" + AMBIENTE_PRODUCAO + "</tpAmb>"))
                .toList();
    }

    /** CFOPs distintos dos itens de cada NFe do período, "5102/5405". */
    private Map<UUID, String> buscarCfops(Integer codEmpresa, OffsetDateTime de, OffsetDateTime ate) {
        List<KeyValueEntity> valores = dataManager.loadValues(
                        "select i.nfe.id, i.cfop from NfeItem i where i.nfe.codEmpresa = :codEmpresa "
                                + "and i.nfe.dhEmi >= :de and i.nfe.dhEmi < :ate")
                .properties("nfeId", "cfop")
                .parameter("codEmpresa", codEmpresa)
                .parameter("de", de)
                .parameter("ate", ate)
                .list();
        Map<UUID, TreeSet<Integer>> porNfe = new HashMap<>();
        for (KeyValueEntity v : valores) {
            Integer cfop = v.getValue("cfop");
            if (cfop != null) {
                porNfe.computeIfAbsent(v.getValue("nfeId"), k -> new TreeSet<>()).add(cfop);
            }
        }
        Map<UUID, String> resultado = new HashMap<>();
        porNfe.forEach((id, set) -> resultado.put(id, "CFOP " + String.join("/", set.stream().map(String::valueOf).toList())));
        return resultado;
    }

    private ExportacaoContadorLinha linhaNfe(Nfe nfe, String cfops) {
        boolean cancelada = Integer.valueOf(PROT_CANCELADA).equals(nfe.getProtCStat());
        String detalhe = cfops;
        if (Integer.valueOf(FIN_NFE_COMPLEMENTAR).equals(nfe.getFinNfe())) {
            detalhe += " (complementar)";
        }
        if (cancelada) {
            detalhe += " CANCELADA";
        }
        return new ExportacaoContadorLinha(1, "NFe emitidas", texto(nfe.getNumeroNf()), texto(nfe.getSerie()),
                data(nfe.getDhEmi()), nfe.getDestXNome(), detalhe.trim(), nfe.getValorNf(),
                cancelada ? BigDecimal.ZERO : nvl(nfe.getValorNf()));
    }

    private static void adicionar(ZipOutputStream zip, String caminho, String conteudo) throws IOException {
        zip.putNextEntry(new ZipEntry(caminho));
        zip.write(conteudo.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    /** "2026-09" pro mês cheio; senão "20260901_20260915". */
    private static String descreverPeriodo(LocalDate inicio, LocalDate fim) {
        boolean mesCheio = inicio.getDayOfMonth() == 1 && fim.equals(inicio.withDayOfMonth(inicio.lengthOfMonth()));
        if (mesCheio) {
            return inicio.format(DateTimeFormatter.ofPattern("yyyy-MM"));
        }
        DateTimeFormatter compacto = DateTimeFormatter.BASIC_ISO_DATE;
        return inicio.format(compacto) + "_" + fim.format(compacto);
    }

    /** Data no fuso de Brasília — o do servidor pode ser UTC (container Docker). */
    private static java.sql.Date data(OffsetDateTime dataHora) {
        return dataHora == null ? null : java.sql.Date.valueOf(dataHora.atZoneSameInstant(FUSO).toLocalDate());
    }

    private static String texto(Integer valor) {
        return valor == null ? "" : valor.toString();
    }

    private static BigDecimal nvl(BigDecimal valor) {
        return valor == null ? BigDecimal.ZERO : valor;
    }
}
