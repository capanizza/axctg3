package br.com.axialsoftware.axctg3.service.fiscal;

import br.com.axialsoftware.axctg3.entity.cadastros.Empresa;
import br.com.axialsoftware.axctg3.entity.enums.AmbienteNfe;
import br.com.axialsoftware.axctg3.entity.enums.ModFrete;
import br.com.axialsoftware.axctg3.entity.fiscal.Nfe;
import br.com.axialsoftware.axctg3.entity.fiscal.NfeDi;
import br.com.axialsoftware.axctg3.entity.fiscal.NfeDiAdicao;
import br.com.axialsoftware.axctg3.entity.fiscal.NfeDuplicata;
import br.com.axialsoftware.axctg3.entity.fiscal.NfeItem;
import br.com.axialsoftware.axctg3.entity.fiscal.NfePagamento;
import br.com.axialsoftware.axctg3.entity.fiscal.NfeVolume;
import br.com.axialsoftware.axctg3.service.UtilGeralService;
import io.jmix.core.DataManager;
import io.jmix.core.FetchPlan;
import io.jmix.core.SaveContext;
import org.springframework.stereotype.Service;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * NFe digitada (ver Javadoc de {@link Nfe}): cria o rascunho, copia uma NFe existente como
 * rascunho e soma os itens nos totais. A transmissão fica em {@link NfeDigitadaEmissaoService}.
 *
 * <p>O emitente nunca é digitado: vem sempre do cadastro da {@link Empresa} (CNPJ precisa bater
 * com o certificado, IE e endereço com o cadastro da SEFAZ, e o CRT decide entre CST e CSOSN)
 * — preenchido aqui ao criar/copiar e relido na transmissão ({@link #preencherEmitente}).
 */
@Service
public class NfeDigitadaService {

    /**
     * Razão social que a SEFAZ exige no destinatário em homologação (cStat=598) — gravada na
     * nota de teste junto com o resto do XML autorizado. Nunca pode sair em produção.
     */
    public static final String HOMOLOGACAO_X_NOME = "NF-E EMITIDA EM AMBIENTE DE HOMOLOGACAO - SEM VALOR FISCAL";

    // Fora da cópia: identidade/auditoria, protocolo, cancelamento e XMLs gravados — tudo o
    // que pertence à nota já emitida, não ao conteúdo que se quer reaproveitar
    private static final Set<String> NAO_COPIAR = Set.of(
            "id", "version", "createdBy", "createdDate", "lastModifiedBy", "lastModifiedDate", "deletedBy",
            "deletedDate", "codEmpresa", "chave", "chaveTentativa", "digitada", "codNf", "codDv", "numeroNf",
            "dhEmi", "xmlEnvio", "xmlRetorno", "protTpAmb", "protVerAplic", "protDhRecbto", "protNProt",
            "protDigVal", "protCStat", "protXMotivo", "cancCStat", "cancXMotivo", "cancNProt",
            "cancDhRegEvento", "cancXJust", "cancXmlRetorno");

    private final DataManager dataManager;
    private final UtilGeralService utilGeralService;

    public NfeDigitadaService(DataManager dataManager, UtilGeralService utilGeralService) {
        this.dataManager = dataManager;
        this.utilGeralService = utilGeralService;
    }

    /** Rascunho novo, já gravado, com identificação e emitente da empresa corrente. */
    public Nfe criarRascunho() {
        Empresa empresa = empresaCorrente();
        Nfe nfe = dataManager.create(Nfe.class);
        nfe.setDigitada(true);
        nfe.setCodEmpresa(empresa.getCodigo());
        nfe.setMod(55);
        nfe.setTpNf(1);
        nfe.setIdDest(1);
        nfe.setTpImp(1);
        nfe.setTpEmis(1);
        nfe.setFinNfe(1);
        nfe.setIndFinal(0);
        nfe.setIndPres(0);
        nfe.setProcEmi(0);
        nfe.setVerProc("axctg3");
        nfe.setCodMunFg(empresa.getMunicipio() != null ? empresa.getMunicipio().getCodigo() : null);
        nfe.setDestIndIe(9);
        ModFrete modFrete = empresa.getModFretePadrao();
        nfe.setModFrete(modFrete != null ? modFrete.getId() : ModFrete.SEM_TRANSPORTE.getId());
        preencherEmitente(nfe, empresa);
        return dataManager.save(nfe);
    }

    /**
     * Rascunho novo com o mesmo conteúdo de outra NFe (itens com DI/adições, volumes,
     * duplicatas, pagamentos) — sem chave, número, protocolo nem cancelamento, e com o
     * emitente relido da empresa corrente, não o da nota de origem. Grupos que a {@link Nfe}
     * não modela (autXML, infRespTec, obsCont...) não vêm junto, porque nunca foram gravados.
     */
    public Nfe copiarComoRascunho(UUID nfeOrigemId) {
        Nfe origem = carregarCompleta(nfeOrigemId);
        Empresa empresa = empresaCorrente();

        Nfe nova = dataManager.create(Nfe.class);
        copiarAtributosSimples(origem, nova);
        nova.setDigitada(true);
        nova.setCodEmpresa(empresa.getCodigo());
        preencherEmitente(nova, empresa);
        // copiada de uma nota de homologação: o nome real do destinatário não existe mais ali
        // (a NF-e 12781 da GB saiu em produção com esse texto, 2026-10-09) — fica em branco
        // pra ser digitado
        if (HOMOLOGACAO_X_NOME.equals(nova.getDestXNome())) {
            nova.setDestXNome(null);
        }

        SaveContext saveContext = new SaveContext().saving(nova);
        List<NfeItem> itens = new ArrayList<>();
        for (NfeItem itemOrigem : lista(origem.getItens())) {
            NfeItem item = dataManager.create(NfeItem.class);
            copiarAtributosSimples(itemOrigem, item);
            item.setNfe(nova);
            List<NfeDi> dis = new ArrayList<>();
            for (NfeDi diOrigem : lista(itemOrigem.getDis())) {
                NfeDi di = dataManager.create(NfeDi.class);
                copiarAtributosSimples(diOrigem, di);
                di.setNfeItem(item);
                List<NfeDiAdicao> adicoes = new ArrayList<>();
                for (NfeDiAdicao adicaoOrigem : lista(diOrigem.getAdicoes())) {
                    NfeDiAdicao adicao = dataManager.create(NfeDiAdicao.class);
                    copiarAtributosSimples(adicaoOrigem, adicao);
                    adicao.setNfeDi(di);
                    adicoes.add(adicao);
                    saveContext.saving(adicao);
                }
                di.setAdicoes(adicoes);
                dis.add(di);
                saveContext.saving(di);
            }
            item.setDis(dis);
            itens.add(item);
            saveContext.saving(item);
        }
        nova.setItens(itens);
        for (NfeVolume volumeOrigem : lista(origem.getVolumes())) {
            NfeVolume volume = dataManager.create(NfeVolume.class);
            copiarAtributosSimples(volumeOrigem, volume);
            volume.setNfe(nova);
            saveContext.saving(volume);
        }
        for (NfeDuplicata duplicataOrigem : lista(origem.getDuplicatas())) {
            NfeDuplicata duplicata = dataManager.create(NfeDuplicata.class);
            copiarAtributosSimples(duplicataOrigem, duplicata);
            duplicata.setNfe(nova);
            saveContext.saving(duplicata);
        }
        for (NfePagamento pagamentoOrigem : lista(origem.getPagamentos())) {
            NfePagamento pagamento = dataManager.create(NfePagamento.class);
            copiarAtributosSimples(pagamentoOrigem, pagamento);
            pagamento.setNfe(nova);
            saveContext.saving(pagamento);
        }
        dataManager.save(saveContext);
        return dataManager.load(Nfe.class).id(nova.getId()).one();
    }

    /**
     * Emitente sempre a partir do cadastro da empresa — chamado ao criar/copiar o rascunho e de
     * novo na transmissão ({@link NfeDigitadaEmissaoService}), pra uma mudança de cadastro entre
     * o rascunho e o envio entrar na nota. Mesmos campos e tratamentos do
     * {@code NfeXmlBuilder.construirEmit}.
     */
    public void preencherEmitente(Nfe nfe, Empresa empresa) {
        nfe.setEmitCnpj(NfeXml.somenteDigitos(empresa.getCnpj()));
        nfe.setEmitXNome(empresa.getNome());
        nfe.setEmitXFant(empresa.getApelido());
        String tipoLogradouro = empresa.getTipoLogradouro() == null ? null : empresa.getTipoLogradouro().getDescricao();
        String logradouro = empresa.getLogradouro() == null ? "" : empresa.getLogradouro();
        nfe.setEmitXLgr(tipoLogradouro == null || tipoLogradouro.isBlank() ? logradouro : tipoLogradouro + " " + logradouro);
        nfe.setEmitNro(empresa.getNumero());
        nfe.setEmitXCpl(empresa.getComplemento());
        nfe.setEmitXBairro(empresa.getBairro());
        if (empresa.getMunicipio() != null) {
            nfe.setEmitCMun(empresa.getMunicipio().getCodigo());
            nfe.setEmitXMun(empresa.getMunicipio().getNome());
            nfe.setEmitUf(empresa.getMunicipio().getUf());
            nfe.setCodUf(UfIbge.codigo(empresa.getMunicipio().getUf()));
        }
        nfe.setEmitCep(NfeXml.somenteDigitos(empresa.getCep()));
        String ie = empresa.getInscEst();
        nfe.setEmitIe(ie != null && ie.trim().equalsIgnoreCase("ISENTO") ? "ISENTO" : NfeXml.somenteDigitos(ie));
        nfe.setEmitCrt(empresa.getCrt() != null ? empresa.getCrt().getId() : null);
        AmbienteNfe ambiente = empresa.getAmbienteNfe();
        nfe.setTpAmb(ambiente != null ? ambiente.getId() : null);
        try {
            nfe.setSerie(empresa.getSerieNfe() == null ? null : Integer.valueOf(empresa.getSerieNfe().trim()));
        } catch (NumberFormatException e) {
            nfe.setSerie(null);
        }
    }

    /**
     * Texto sugerido pro {@code infCpl} de uma nota de importação, a partir das DIs e dos
     * valores digitados nos itens — {@code null} quando nenhum item tem DI. O manual do DANFE
     * (MOC 7.0, Anexo II) não tem campo pro II, PIS e COFINS no quadro "Cálculo do Imposto",
     * e o {@code infCpl} é de impressão obrigatória: é por ele que esses valores chegam ao
     * DANFE, como o despachante faz no espelho.
     *
     * <p>A Taxa Siscomex não tem campo próprio na NF-e; segue a convenção da nota de
     * importação digitada, em que "outras despesas" = PIS + COFINS + Siscomex + ICMS, e sai
     * como a diferença — só quando der positiva.
     */
    public String textoImportacao(Nfe nfe) {
        List<NfeItem> itens = lista(nfe.getItens());
        List<NfeDi> dis = itens.stream().flatMap(i -> lista(i.getDis()).stream()).toList();
        if (dis.isEmpty()) {
            return null;
        }
        DateTimeFormatter data = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        List<String> declaracoes = new ArrayList<>();
        for (NfeDi di : dis) {
            StringBuilder d = new StringBuilder("DI ").append(di.getNumeroDi() == null ? "" : di.getNumeroDi().trim());
            if (di.getDataDi() != null) {
                d.append(" de ").append(di.getDataDi().format(data));
            }
            if (di.getLocalDesembaraco() != null && !di.getLocalDesembaraco().isBlank()) {
                d.append(", desembaraço em ").append(di.getLocalDesembaraco().trim());
                if (di.getUfDesembaraco() != null && !di.getUfDesembaraco().isBlank()) {
                    d.append("/").append(di.getUfDesembaraco().trim());
                }
            }
            if (di.getDataDesembaraco() != null) {
                d.append(" em ").append(di.getDataDesembaraco().format(data));
            }
            declaracoes.add(d.toString());
        }

        BigDecimal pis = somar(itens, NfeItem::getValorPis);
        BigDecimal cofins = somar(itens, NfeItem::getValorCofins);
        BigDecimal icms = somar(itens, NfeItem::getValorIcms);
        BigDecimal siscomex = somar(itens, NfeItem::getValorOutro).subtract(pis).subtract(cofins).subtract(icms);
        BigDecimal afrmm = dis.stream().map(NfeDi::getValorAfrmm).map(NfeDigitadaService::nvl)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<String> valores = new ArrayList<>();
        valores.add("Valor aduaneiro " + reais(somar(itens, NfeItem::getBaseIi)));
        valores.add("II " + reais(somar(itens, NfeItem::getValorIi)));
        BigDecimal ipi = somar(itens, NfeItem::getValorIpi);
        if (ipi.signum() != 0) {
            valores.add("IPI " + reais(ipi));
        }
        valores.add("PIS " + reais(pis));
        valores.add("COFINS " + reais(cofins));
        valores.add("ICMS " + reais(icms));
        BigDecimal iof = somar(itens, NfeItem::getValorIof);
        if (iof.signum() != 0) {
            valores.add("IOF " + reais(iof));
        }
        BigDecimal despesasAduaneiras = somar(itens, NfeItem::getValorDespAdu);
        if (despesasAduaneiras.signum() != 0) {
            valores.add("Despesas aduaneiras " + reais(despesasAduaneiras));
        }
        if (afrmm.signum() != 0) {
            valores.add("AFRMM " + reais(afrmm));
        }
        if (siscomex.signum() > 0) {
            valores.add("Taxa Siscomex " + reais(siscomex));
        }
        return "Importação: " + String.join("; ", declaracoes) + ". " + String.join("; ", valores) + ".";
    }

    // separadores fixos, sem depender do locale da JVM (mesmo critério do NfeDanfeService)
    private static String reais(BigDecimal valor) {
        DecimalFormatSymbols simbolos = new DecimalFormatSymbols();
        simbolos.setDecimalSeparator(',');
        simbolos.setGroupingSeparator('.');
        return "R$ " + new DecimalFormat("#,##0.00", simbolos).format(nvl(valor));
    }

    /**
     * Soma os itens nos totais da nota (ICMSTot, IBSCBSTot, ISTot) e no {@code vItem} de cada
     * item, e renumera os itens 1..n — o botão "Recalcular totais" da tela. A SEFAZ confere
     * cada total contra a soma dos itens (cStat 531, 535, 564, 610...), então isto existe pra
     * que o operador não precise somar à mão; não calcula imposto nenhum, só soma o que foi
     * digitado em cada item. Altera a instância recebida, sem gravar.
     *
     * <p>{@code vNF} segue a fórmula do leiaute: vProd − vDesc − vICMSDeson + vST + vFCPST +
     * vFrete + vSeg + vOutro + vII + vIPI + vIPIDevol. Só os itens com {@code indTot = 1} somam
     * no vProd. O {@code vItem} (Reforma Tributária) só é preenchido nos itens com IBS/CBS, e o
     * vNFTot só quando há algum — mesmo critério do {@code NfeXmlBuilder}.
     */
    public void recalcularTotais(Nfe nfe) {
        List<NfeItem> itens = lista(nfe.getItens());
        int numero = 1;
        for (NfeItem item : itens) {
            item.setItem(numero++);
            boolean temIbsCbs = item.getCstIbsCbs() != null && !item.getCstIbsCbs().isBlank();
            item.setValorItem(temIbsCbs ? valorDoItem(item).add(nvl(item.getValorIbs())).add(nvl(item.getValorCbs()))
                    : BigDecimal.ZERO);
        }

        nfe.setValorBc(somar(itens, NfeItem::getBaseIcms));
        nfe.setValorIcms(somar(itens, NfeItem::getValorIcms));
        nfe.setValorIcmsDeson(somar(itens, NfeItem::getValorIcmsDeson));
        nfe.setValorFcp(somar(itens, NfeItem::getValorFcp));
        nfe.setValorBcSt(somar(itens, NfeItem::getBaseIcmsSt));
        nfe.setValorSt(somar(itens, NfeItem::getValorIcmsSt));
        nfe.setValorProd(somar(itens.stream().filter(i -> i.getIndTot() == null || i.getIndTot() == 1).toList(),
                NfeItem::getValorProd));
        nfe.setValorFrete(somar(itens, NfeItem::getValorFrete));
        nfe.setValorSeg(somar(itens, NfeItem::getValorSeg));
        nfe.setValorDesc(somar(itens, NfeItem::getValorDesc));
        nfe.setValorOutro(somar(itens, NfeItem::getValorOutro));
        nfe.setValorIi(somar(itens, NfeItem::getValorIi));
        nfe.setValorIpi(somar(itens, NfeItem::getValorIpi));
        nfe.setValorPis(somar(itens, NfeItem::getValorPis));
        nfe.setValorCofins(somar(itens, NfeItem::getValorCofins));
        nfe.setValorTotTrib(somar(itens, NfeItem::getValorTotTributos));
        nfe.setValorNf(nvl(nfe.getValorProd())
                .subtract(nvl(nfe.getValorDesc()))
                .subtract(nvl(nfe.getValorIcmsDeson()))
                .add(nvl(nfe.getValorSt()))
                .add(nvl(nfe.getValorFcpSt()))
                .add(nvl(nfe.getValorFrete()))
                .add(nvl(nfe.getValorSeg()))
                .add(nvl(nfe.getValorOutro()))
                .add(nvl(nfe.getValorIi()))
                .add(nvl(nfe.getValorIpi()))
                .add(nvl(nfe.getValorIpiDevol())));

        nfe.setValorBcIbsCbs(somar(itens, NfeItem::getBaseIbsCbs));
        nfe.setValorDifIbsUf(somar(itens, NfeItem::getValorDifIbsUf));
        nfe.setValorDevTribIbsUf(somar(itens, NfeItem::getValorDevTribIbsUf));
        nfe.setValorIbsUf(somar(itens, NfeItem::getValorIbsUf));
        nfe.setValorDifIbsMun(somar(itens, NfeItem::getValorDifIbsMun));
        nfe.setValorDevTribIbsMun(somar(itens, NfeItem::getValorDevTribIbsMun));
        nfe.setValorIbsMun(somar(itens, NfeItem::getValorIbsMun));
        nfe.setValorIbs(somar(itens, NfeItem::getValorIbs));
        nfe.setValorDifCbs(somar(itens, NfeItem::getValorDifCbs));
        nfe.setValorDevTribCbs(somar(itens, NfeItem::getValorDevTribCbs));
        nfe.setValorCbs(somar(itens, NfeItem::getValorCbs));
        nfe.setValorIs(somar(itens, NfeItem::getValorIs));
        boolean temIbsCbs = itens.stream().anyMatch(i -> i.getCstIbsCbs() != null && !i.getCstIbsCbs().isBlank());
        nfe.setValorNfTot(temIbsCbs ? nfe.getValorNf().add(nfe.getValorIbs()).add(nfe.getValorCbs()) : BigDecimal.ZERO);
    }

    /**
     * Lista as diferenças entre os totais gravados e a soma dos itens — vazia quando bate. Usado
     * na transmissão pra recusar uma nota com total desatualizado antes de a SEFAZ recusar.
     */
    public List<String> divergenciasDeTotais(Nfe nfe) {
        Nfe conferencia = dataManager.create(Nfe.class);
        conferencia.setItens(new ArrayList<>(lista(nfe.getItens())));
        conferencia.setValorFcpSt(nfe.getValorFcpSt());
        conferencia.setValorIpiDevol(nfe.getValorIpiDevol());
        // recalcularTotais renumera e mexe no vItem dos itens — aqui só interessa o cabeçalho,
        // então guarda e devolve o que estava nos itens
        List<Integer> numeros = new ArrayList<>();
        List<BigDecimal> valoresItem = new ArrayList<>();
        for (NfeItem item : lista(nfe.getItens())) {
            numeros.add(item.getItem());
            valoresItem.add(item.getValorItem());
        }
        recalcularTotais(conferencia);
        int i = 0;
        for (NfeItem item : lista(nfe.getItens())) {
            item.setItem(numeros.get(i));
            item.setValorItem(valoresItem.get(i));
            i++;
        }

        List<String> divergencias = new ArrayList<>();
        comparar(divergencias, "vBC", nfe.getValorBc(), conferencia.getValorBc());
        comparar(divergencias, "vICMS", nfe.getValorIcms(), conferencia.getValorIcms());
        comparar(divergencias, "vICMSDeson", nfe.getValorIcmsDeson(), conferencia.getValorIcmsDeson());
        comparar(divergencias, "vFCP", nfe.getValorFcp(), conferencia.getValorFcp());
        comparar(divergencias, "vBCST", nfe.getValorBcSt(), conferencia.getValorBcSt());
        comparar(divergencias, "vST", nfe.getValorSt(), conferencia.getValorSt());
        comparar(divergencias, "vProd", nfe.getValorProd(), conferencia.getValorProd());
        comparar(divergencias, "vFrete", nfe.getValorFrete(), conferencia.getValorFrete());
        comparar(divergencias, "vSeg", nfe.getValorSeg(), conferencia.getValorSeg());
        comparar(divergencias, "vDesc", nfe.getValorDesc(), conferencia.getValorDesc());
        comparar(divergencias, "vOutro", nfe.getValorOutro(), conferencia.getValorOutro());
        comparar(divergencias, "vII", nfe.getValorIi(), conferencia.getValorIi());
        comparar(divergencias, "vIPI", nfe.getValorIpi(), conferencia.getValorIpi());
        comparar(divergencias, "vPIS", nfe.getValorPis(), conferencia.getValorPis());
        comparar(divergencias, "vCOFINS", nfe.getValorCofins(), conferencia.getValorCofins());
        comparar(divergencias, "vNF", nfe.getValorNf(), conferencia.getValorNf());
        comparar(divergencias, "vIBS", nfe.getValorIbs(), conferencia.getValorIbs());
        comparar(divergencias, "vCBS", nfe.getValorCbs(), conferencia.getValorCbs());
        return divergencias;
    }

    /** Nota com todos os filhos (itens com DI/adições, volumes, duplicatas, pagamentos). */
    public Nfe carregarCompleta(UUID nfeId) {
        return dataManager.load(Nfe.class)
                .id(nfeId)
                .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE)
                        .add("itens", fpItem -> fpItem.addFetchPlan(FetchPlan.BASE)
                                .add("dis", fpDi -> fpDi.addFetchPlan(FetchPlan.BASE)
                                        .add("adicoes", FetchPlan.BASE)))
                        .add("volumes", FetchPlan.BASE)
                        .add("duplicatas", FetchPlan.BASE)
                        .add("pagamentos", FetchPlan.BASE))
                .one();
    }

    // vProd − vDesc − vICMSDeson + vST + vFrete + vSeg + vOutro + vII + vIPI do item
    private static BigDecimal valorDoItem(NfeItem item) {
        return nvl(item.getValorProd())
                .subtract(nvl(item.getValorDesc()))
                .subtract(nvl(item.getValorIcmsDeson()))
                .add(nvl(item.getValorIcmsSt()))
                .add(nvl(item.getValorFrete()))
                .add(nvl(item.getValorSeg()))
                .add(nvl(item.getValorOutro()))
                .add(nvl(item.getValorIi()))
                .add(nvl(item.getValorIpi()));
    }

    private Empresa empresaCorrente() {
        Empresa empresa = utilGeralService.getEmpresa();
        if (empresa == null) {
            throw new IllegalStateException("Selecione uma empresa antes de digitar uma NFe");
        }
        return empresa;
    }

    private static void comparar(List<String> divergencias, String tag, BigDecimal gravado, BigDecimal soma) {
        if (nvl(gravado).compareTo(nvl(soma)) != 0) {
            divergencias.add(tag + ": " + nvl(gravado).toPlainString() + " na nota, " + nvl(soma).toPlainString()
                    + " na soma dos itens");
        }
    }

    private static BigDecimal somar(List<NfeItem> itens, Function<NfeItem, BigDecimal> campo) {
        return itens.stream().map(campo).map(NfeDigitadaService::nvl).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal nvl(BigDecimal valor) {
        return valor == null ? BigDecimal.ZERO : valor;
    }

    private static <T> List<T> lista(List<T> lista) {
        return lista == null ? Collections.emptyList() : lista;
    }

    /**
     * Copia os atributos simples (texto, número, data, booleano, enum) de uma entidade pra
     * outra da mesma classe, pelos pares getter/setter — referências e coleções ficam de fora
     * (a cópia dos filhos é feita à mão em {@link #copiarComoRascunho}), assim como os
     * atributos de {@link #NAO_COPIAR}. Por reflexão pra não ter de repetir à mão os ~200
     * campos de Nfe/NfeItem, que crescem a cada grupo novo do leiaute.
     */
    private static void copiarAtributosSimples(Object origem, Object destino) {
        for (Method getter : origem.getClass().getMethods()) {
            String nome = getter.getName();
            if (!nome.startsWith("get") || nome.length() <= 3 || getter.getParameterCount() != 0) {
                continue;
            }
            Class<?> tipo = getter.getReturnType();
            boolean simples = tipo == String.class || tipo == Integer.class || tipo == BigDecimal.class
                    || tipo == LocalDate.class || tipo == OffsetDateTime.class || tipo == Boolean.class || tipo.isEnum();
            String propriedade = Character.toLowerCase(nome.charAt(3)) + nome.substring(4);
            if (!simples || NAO_COPIAR.contains(propriedade)) {
                continue;
            }
            try {
                Method setter = destino.getClass().getMethod("set" + nome.substring(3), tipo);
                setter.invoke(destino, getter.invoke(origem));
            } catch (NoSuchMethodException e) {
                // atributo calculado, sem setter — não é dado da nota
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Falha ao copiar " + propriedade, e);
            }
        }
    }
}
