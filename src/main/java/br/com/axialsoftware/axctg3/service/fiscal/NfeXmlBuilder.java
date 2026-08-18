package br.com.axialsoftware.axctg3.service.fiscal;

import br.com.axialsoftware.axctg3.entity.cadastros.Empresa;
import br.com.axialsoftware.axctg3.entity.cadastros.Parceiro;
import br.com.axialsoftware.axctg3.entity.enums.AmbienteNfe;
import br.com.axialsoftware.axctg3.entity.enums.CodRegimeTributario;
import br.com.axialsoftware.axctg3.entity.fiscal.ItemNotaSaida;
import br.com.axialsoftware.axctg3.entity.fiscal.NaturezaOperacao;
import br.com.axialsoftware.axctg3.entity.fiscal.NotaSaida;
import br.com.axialsoftware.axctg3.entity.financeiro.TituloReceber;
import br.com.axialsoftware.axctg3.entity.tabelas.AliquotaIbsCbs;
import br.com.axialsoftware.axctg3.entity.tabelas.ClassTrib;
import io.jmix.core.DataManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Constrói o XML da NFe 4.00 (grupo {@code infNFe}, ainda sem assinatura — ver {@link
 * NfeXmlSigner}) a partir de {@link NotaSaida}/{@link ItemNotaSaida}, pra emissão própria
 * (docs/EMISSAO-NFE.md). Espelha exatamente os grupos e nomes de tag que {@link
 * NfeXmlParser} sabe ler de volta — depois de assinada e autorizada, a NFe emitida vira só
 * mais um XML que passa pelo mesmo parser (não existe um segundo mapeador entidade→XML e
 * XML→entidade fazendo a mesma coisa em paralelo).
 *
 * <p>Mesmo recorte de escopo do {@code Nfe}/{@code NfeItem} (ver Javadoc deles): sem
 * NFref/retirada/entrega/autXML/detExport/compra/cana/rastro/med/arma/veicProd/comb/
 * ISSQNtot/infIntermed/infRespTec/reboque/vagao/balsa/NVE/DI. Simplificações adicionais
 * específicas da emissão própria (primeira versão, docs/EMISSAO-NFE.md tem a lista
 * completa): sem diferimento/devolução de tributo no bloco IBS/CBS (fica zerado), sem
 * crédito de ICMS do Simples (pCredSN/vCredICMSSN zerado), frete/transportador não
 * modelados em {@code NotaSaida} (modFrete fixo em 9 = sem transporte).
 */
@Service
public class NfeXmlBuilder {

    private static final String NS_NFE = "http://www.portalfiscal.inf.br/nfe";
    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final DataManager dataManager;
    private final NfeChaveService chaveService;

    // DIAGNÓSTICO TEMPORÁRIO (2026-08-17, ver [[emissao-nfe-propria]]): homologação SEFAZ-SP
    // ainda valida a nota contra o pacote de schema PL_008i2 (anterior à Reforma
    // Tributária — confirmado pelo verAplic da resposta), enquanto produção já está em
    // PL009_V4 (com IBS/CBS, estrutura conferida contra 173 notas reais aceitas). Com essa
    // flag em false, pula IBSCBS/IBSCBSTot/vItem só pra isolar se o resto do XML passa no
    // schema antigo de homologação. Restaurar (deletar a flag e sempre incluir) assim que
    // SEFAZ-SP atualizar homologação, ou quando o teste passar a ser feito em produção.
    @Value("${axctg3.nfe.incluir-reforma-tributaria:true}")
    private boolean incluirReformaTributaria;

    public NfeXmlBuilder(DataManager dataManager, NfeChaveService chaveService) {
        this.dataManager = dataManager;
        this.chaveService = chaveService;
    }

    public record Resultado(Document documento, String chave) {
    }

    public Resultado construir(NotaSaida notaSaida) {
        Empresa empresa = notaSaida.getNatureza() == null ? null : buscarEmpresa(notaSaida);
        if (empresa == null) {
            throw new IllegalStateException("Empresa da nota não encontrada (codEmpresa=" + notaSaida.getCodEmpresa() + ")");
        }
        if (empresa.getCrt() == null || empresa.getAmbienteNfe() == null) {
            throw new IllegalStateException(
                    "Empresa sem regime tributário (CRT) ou ambiente de NFe configurado — ver aba \"Emissão NFe\" do cadastro");
        }
        if (empresa.getMunicipio() == null) {
            throw new IllegalStateException("Empresa sem município cadastrado — necessário pra cUF/cMunFG da NFe");
        }
        String cnpjSoDigitos = empresa.getCnpj() == null ? "" : empresa.getCnpj().replaceAll("\\D", "");
        if (cnpjSoDigitos.length() != 14) {
            throw new IllegalStateException(
                    "Empresa sem CNPJ válido cadastrado — necessário pra montar a chave de acesso da NFe");
        }
        // IE do emitente é obrigatória no schema (padrão [0-9]{2,14}|ISENTO, sem minOccurs="0")
        // — confirmado validando contra o XSD oficial em 2026-08-17; empresa sem IE precisa do
        // literal "ISENTO" cadastrado, não campo vazio.
        if (empresa.getInscEst() == null || empresa.getInscEst().isBlank()) {
            throw new IllegalStateException(
                    "Empresa sem Inscrição Estadual cadastrada — preencha com o número ou, se isenta, "
                            + "com o texto \"ISENTO\" (aba \"Emissão NFe\" do cadastro)");
        }
        if (notaSaida.getItens() == null || notaSaida.getItens().isEmpty()) {
            throw new IllegalStateException("Nota de saída sem itens");
        }
        String serieTexto = notaSaida.getSerie() == null ? "" : notaSaida.getSerie().trim();
        Integer serie;
        try {
            serie = Integer.valueOf(serieTexto);
        } catch (NumberFormatException e) {
            throw new IllegalStateException(
                    "Série da nota inválida (\"" + notaSaida.getSerie() + "\") — informe um número na aba de dados da nota");
        }

        Document doc = novoDocumento();
        Integer cUf = UfIbge.codigo(empresa.getMunicipio().getUf());
        Integer cNf = chaveService.gerarCNf();
        Integer tpEmis = 1;
        // dhEmi é "agora" (momento real da transmissão pra SEFAZ, não a data de emissão
        // gravada na nota — que pode ser bem anterior, ver dhSaiEnt/dataSaida) — e o campo
        // AAMM da chave de acesso TEM que vir do mesmo ano/mês desse dhEmi, senão a SEFAZ
        // rejeita com cStat=502 "Campo Id não corresponde à concatenação dos campos
        // correspondentes" (confirmado 2026-08-17: usar notaSaida.getDataEmissao() aqui
        // gerava AAMM=0607/julho enquanto dhEmi ia com agosto). Calculado uma vez só e
        // repassado pra construirIde() em vez de cada um chamar OffsetDateTime.now() por
        // conta própria.
        OffsetDateTime dhEmi = OffsetDateTime.now(ZoneOffset.of("-03:00")).truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        String chave = chaveService.gerarChave(cUf, dhEmi.toLocalDate(), empresa.getCnpj(),
                55, serie, notaSaida.getNumero(), tpEmis, cNf);
        int cDv = Integer.parseInt(chave.substring(43));

        // xmlns não é setAttribute manual: com createElementNS o elemento já carrega o
        // namespace de verdade (Node.getNamespaceURI()), e é isso que a canonicalização do
        // XML-DSig (NfeXmlSigner) precisa pra calcular o mesmo digest que a SEFAZ recalcula
        // ao validar — setAttribute("xmlns", ...) só escrevia o texto "xmlns=..." sem o nó
        // de namespace por trás, causando cStat=297 "Assinatura difere do calculado" (ver
        // [[emissao-nfe-propria]]) porque o C14N incluso, que herda o namespace do
        // ancestral NFe para dentro do infNFe assinado, não enxergava esse namespace herdado
        // num documento não-namespace-aware.
        Element nfeEl = doc.createElementNS(NS_NFE, "NFe");
        doc.appendChild(nfeEl);

        Element infNFe = doc.createElementNS(NS_NFE, "infNFe");
        infNFe.setAttribute("Id", "NFe" + chave);
        infNFe.setAttribute("versao", "4.00");
        nfeEl.appendChild(infNFe);

        infNFe.appendChild(construirIde(doc, notaSaida, empresa, cUf, cNf, cDv, tpEmis, dhEmi));
        infNFe.appendChild(construirEmit(doc, empresa));
        infNFe.appendChild(construirDest(doc, notaSaida.getParceiro(), empresa.getAmbienteNfe()));

        // Alíquota-teste nacional do ano de emissão (LC 214/2025) — busca uma vez só e
        // repassa adiante; ver Javadoc de aliqIbsUfEfetiva()/aliqCbsEfetiva() sobre por que
        // isso entra como fallback e não como valor sempre usado.
        AliquotaIbsCbs aliquotaTeste = buscarAliquotaIbsCbs(dhEmi.getYear());

        BigDecimal totalVIbs = BigDecimal.ZERO;
        BigDecimal totalVIbsUf = BigDecimal.ZERO;
        BigDecimal totalVIbsMun = BigDecimal.ZERO;
        BigDecimal totalVCbs = BigDecimal.ZERO;
        int nItem = 1;
        for (ItemNotaSaida item : notaSaida.getItens()) {
            Element det = construirDet(doc, item, empresa, notaSaida.getNatureza(), nItem, aliquotaTeste);
            infNFe.appendChild(det);
            totalVIbsUf = totalVIbsUf.add(valorIbsUfDoItem(item, notaSaida.getNatureza(), aliquotaTeste));
            totalVIbsMun = totalVIbsMun.add(valorIbsMunDoItem(item, notaSaida.getNatureza(), aliquotaTeste));
            totalVIbs = totalVIbs.add(valorIbsDoItem(item, notaSaida.getNatureza(), aliquotaTeste));
            totalVCbs = totalVCbs.add(valorCbsDoItem(item, notaSaida.getNatureza(), aliquotaTeste));
            nItem++;
        }

        infNFe.appendChild(construirTotal(doc, notaSaida, totalVIbs, totalVIbsUf, totalVIbsMun, totalVCbs));
        infNFe.appendChild(construirTransp(doc, notaSaida));
        List<TituloReceber> titulos = buscarTitulos(notaSaida);
        Element cobr = construirCobr(doc, notaSaida, titulos);
        if (cobr != null) {
            infNFe.appendChild(cobr);
        }
        infNFe.appendChild(construirPag(doc, titulos));
        Element infAdic = construirInfAdic(doc, notaSaida);
        if (infAdic != null) {
            infNFe.appendChild(infAdic);
        }

        return new Resultado(doc, chave);
    }

    // ---- empresa/emitente ----

    private Empresa buscarEmpresa(NotaSaida notaSaida) {
        return dataManager.load(Empresa.class)
                .query("select e from Empresa e where e.codigo = :codigo")
                .parameter("codigo", notaSaida.getCodEmpresa())
                .optional()
                .orElse(null);
    }

    // ---- ide ----

    private Element construirIde(Document doc, NotaSaida notaSaida, Empresa empresa, Integer cUf,
                                  Integer cNf, int cDv, Integer tpEmis, OffsetDateTime dhEmi) {
        Element ide = doc.createElementNS(NS_NFE, "ide");
        text(doc, ide, "cUF", cUf);
        text(doc, ide, "cNF", String.format("%08d", cNf));
        text(doc, ide, "natOp", notaSaida.getNatureza() != null ? notaSaida.getNatureza().getNome() : "");
        text(doc, ide, "mod", 55);
        text(doc, ide, "serie", notaSaida.getSerie().trim());
        text(doc, ide, "nNF", notaSaida.getNumero());
        // dhEmi recebido de construir() (mesmo valor usado pro AAMM da chave de acesso —
        // ver comentário lá) — truncatedTo(SECONDS) já aplicado na origem, porque
        // TDateTimeUTC no schema oficial não aceita fração de segundo.
        text(doc, ide, "dhEmi", DATA_HORA.format(dhEmi));
        // dhSaiEnt tem que ser >= dhEmi (rejeição cStat=506 "Data de Saída menor que a
        // Data de Emissão", confirmado 2026-08-17) — notaSaida.getDataSaida() é a data de
        // negócio da nota (pode ser bem anterior a "agora", que é quando a emissão de
        // verdade acontece). Mesmo padrão das 173 notas reais de produção conferidas (ver
        // [[nfe-xmls-reais-teste]]): lá dhSaiEnt nunca é anterior a dhEmi — quando a saída é
        // no mesmo dia, os dois batem exatamente. Aqui: usa a data de saída se ela já é
        // igual/posterior a dhEmi, senão cai pra dhEmi mesmo (não dá pra declarar uma saída
        // no passado quando a emissão de fato só está acontecendo agora).
        OffsetDateTime dataSaidaInicioDia = notaSaida.getDataSaida().atStartOfDay().atOffset(ZoneOffset.of("-03:00"));
        OffsetDateTime dhSaiEnt = dataSaidaInicioDia.isBefore(dhEmi) ? dhEmi : dataSaidaInicioDia;
        text(doc, ide, "dhSaiEnt", DATA_HORA.format(dhSaiEnt));
        text(doc, ide, "tpNF", 1);
        String ufDestino = notaSaida.getParceiro() != null && notaSaida.getParceiro().getEstado() != null
                ? notaSaida.getParceiro().getEstado() : empresa.getMunicipio().getUf();
        text(doc, ide, "idDest", empresa.getMunicipio().getUf().equalsIgnoreCase(ufDestino) ? 1 : 2);
        text(doc, ide, "cMunFG", empresa.getMunicipio().getCodigo());
        text(doc, ide, "tpImp", 1);
        text(doc, ide, "tpEmis", tpEmis);
        text(doc, ide, "cDV", cDv);
        text(doc, ide, "tpAmb", empresa.getAmbienteNfe().getId());
        text(doc, ide, "finNFe", 1);
        text(doc, ide, "indFinal", 0);
        text(doc, ide, "indPres", 9);
        text(doc, ide, "indIntermed", 0);
        text(doc, ide, "procEmi", 0);
        text(doc, ide, "verProc", "axctg3");
        return ide;
    }

    // ---- emit ----

    private Element construirEmit(Document doc, Empresa empresa) {
        Element emit = doc.createElementNS(NS_NFE, "emit");
        text(doc, emit, "CNPJ", somenteDigitos(empresa.getCnpj()));
        text(doc, emit, "xNome", empresa.getNome());
        text(doc, emit, "xFant", empresa.getApelido());
        Element ender = doc.createElementNS(NS_NFE, "enderEmit");
        text(doc, ender, "xLgr", enderecoCompleto(empresa.getTipoLogradouro() == null ? null
                : empresa.getTipoLogradouro().getDescricao(), empresa.getLogradouro()));
        text(doc, ender, "nro", empresa.getNumero());
        if (empresa.getComplemento() != null && !empresa.getComplemento().isBlank()) {
            text(doc, ender, "xCpl", empresa.getComplemento());
        }
        text(doc, ender, "xBairro", empresa.getBairro());
        text(doc, ender, "cMun", empresa.getMunicipio().getCodigo());
        text(doc, ender, "xMun", empresa.getMunicipio().getNome());
        text(doc, ender, "UF", empresa.getMunicipio().getUf());
        text(doc, ender, "CEP", somenteDigitos(empresa.getCep()));
        text(doc, ender, "cPais", 1058);
        text(doc, ender, "xPais", "BRASIL");
        emit.appendChild(ender);
        text(doc, emit, "IE", inscEstOuIsento(empresa.getInscEst()));
        text(doc, emit, "CRT", empresa.getCrt().getId());
        return emit;
    }

    // ---- dest ----

    private Element construirDest(Document doc, Parceiro parceiro, AmbienteNfe ambiente) {
        Element dest = doc.createElementNS(NS_NFE, "dest");
        String cnpjOuCpf = somenteDigitos(parceiro.getCnpj());
        if (cnpjOuCpf.length() == 11) {
            text(doc, dest, "CPF", cnpjOuCpf);
        } else {
            text(doc, dest, "CNPJ", cnpjOuCpf);
        }
        // Homologação exige literalmente essa razão social no destinatário — não é dado
        // real, é validação da própria SEFAZ (rejeição cStat=598, confirmado 2026-08-17);
        // sem isso a nota nunca autoriza em ambiente de teste, mesmo com tudo mais certo.
        text(doc, dest, "xNome", ambiente == AmbienteNfe.HOMOLOGACAO
                ? "NF-E EMITIDA EM AMBIENTE DE HOMOLOGACAO - SEM VALOR FISCAL"
                : parceiro.getNome());
        Element ender = doc.createElementNS(NS_NFE, "enderDest");
        text(doc, ender, "xLgr", enderecoCompleto(parceiro.getTipoLogradouro() == null ? null
                : parceiro.getTipoLogradouro().getDescricao(), parceiro.getLogradouro()));
        text(doc, ender, "nro", parceiro.getNumero());
        if (parceiro.getComplemento() != null && !parceiro.getComplemento().isBlank()) {
            text(doc, ender, "xCpl", parceiro.getComplemento());
        }
        text(doc, ender, "xBairro", parceiro.getBairro());
        text(doc, ender, "cMun", parceiro.getMunicipio() != null ? parceiro.getMunicipio().getCodigo() : null);
        text(doc, ender, "xMun", parceiro.getMunicipio() != null ? parceiro.getMunicipio().getNome() : null);
        text(doc, ender, "UF", parceiro.getEstado());
        text(doc, ender, "CEP", somenteDigitos(parceiro.getCep()));
        text(doc, ender, "cPais", 1058);
        text(doc, ender, "xPais", "BRASIL");
        dest.appendChild(ender);
        String inscricao = parceiro.getInscricao();
        if (inscricao == null || inscricao.isBlank()) {
            text(doc, dest, "indIEDest", 9);
        } else {
            text(doc, dest, "indIEDest", 1);
            text(doc, dest, "IE", somenteDigitos(inscricao));
        }
        return dest;
    }

    // ---- det (item) ----

    private Element construirDet(Document doc, ItemNotaSaida item, Empresa empresa, NaturezaOperacao natureza, int nItem,
                                  AliquotaIbsCbs aliquotaTeste) {
        Element det = doc.createElementNS(NS_NFE, "det");
        det.setAttribute("nItem", String.valueOf(nItem));

        Element prod = doc.createElementNS(NS_NFE, "prod");
        text(doc, prod, "cProd", item.getProduto().getCodigo());
        text(doc, prod, "cEAN", "SEM GTIN");
        text(doc, prod, "xProd", item.getProduto().getDescricao());
        text(doc, prod, "NCM", item.getProduto().getClassificacaoFiscal() != null
                ? item.getProduto().getClassificacaoFiscal().getCodNcm() : "00000000");
        // cBenef fica em prod, não em imposto/ICMS, apesar de depender do CST do ICMS —
        // ver Javadoc de cBenefDoItem().
        text(doc, prod, "cBenef", cBenefDoItem(item, simplesNacional(empresa)));
        text(doc, prod, "CFOP", item.getCfop());
        text(doc, prod, "uCom", vazioComo(item.getProduto().getUnidade(), "UN"));
        text(doc, prod, "qCom", dec(item.getQuantidade(), 4));
        text(doc, prod, "vUnCom", dec(item.getValorUnitario(), 10));
        text(doc, prod, "vProd", dec(item.getSubTotal(), 2));
        text(doc, prod, "cEANTrib", "SEM GTIN");
        text(doc, prod, "uTrib", vazioComo(item.getProduto().getUnidade(), "UN"));
        text(doc, prod, "qTrib", dec(item.getQuantidade(), 4));
        text(doc, prod, "vUnTrib", dec(item.getValorUnitario(), 10));
        // vFrete/vSeg por item — SEFAZ confere que o somatório desses valores nos itens bate
        // com total/ICMSTot/vFrete e vSeg (rejeição cStat=535 "Total do Frete difere do
        // somatório dos itens", confirmado 2026-08-18: total vinha de notaSaida.getFrete()
        // mas nenhum item carregava vFrete, então a soma dos itens dava zero). Só emite
        // quando > 0 (campo opcional no schema, mesmo padrão das 176 notas reais — nem todo
        // item tem frete/seguro rateado).
        if (item.getFrete() != null && item.getFrete().compareTo(BigDecimal.ZERO) != 0) {
            text(doc, prod, "vFrete", dec(item.getFrete(), 2));
        }
        if (item.getSeguro() != null && item.getSeguro().compareTo(BigDecimal.ZERO) != 0) {
            text(doc, prod, "vSeg", dec(item.getSeguro(), 2));
        }
        text(doc, prod, "indTot", 1);
        det.appendChild(prod);

        Element imposto = doc.createElementNS(NS_NFE, "imposto");
        text(doc, imposto, "vTotTrib", "0.00");
        imposto.appendChild(construirIcms(doc, item, empresa));
        Element ipi = construirIpi(doc, item);
        if (ipi != null) {
            imposto.appendChild(ipi);
        }
        imposto.appendChild(construirPis(doc, item, natureza));
        imposto.appendChild(construirCofins(doc, item, natureza));
        if (incluirReformaTributaria) {
            imposto.appendChild(construirIbsCbs(doc, item, natureza, aliquotaTeste));
        }
        det.appendChild(imposto);

        if (incluirReformaTributaria) {
            // vItem (valor total do item já com IBS/CBS somado) — grupo novo da Reforma
            // Tributária, sibling de imposto, confirmado obrigatório em 30/30 notas reais de
            // teste com IBSCBS (ver conversa 2026-08-17); mesma fórmula do vNFTot do total.
            BigDecimal vItem = (item.getSubTotal() == null ? BigDecimal.ZERO : item.getSubTotal())
                    .add(valorIbsDoItem(item, natureza, aliquotaTeste))
                    .add(valorCbsDoItem(item, natureza, aliquotaTeste));
            text(doc, det, "vItem", dec(vItem, 2));
        }

        return det;
    }

    /** CST/CSOSN de ICMS efetivo do item — "102" (Simples) ou "40" como default quando
     * {@link ItemNotaSaida#getCst()} vem vazio, senão o valor gravado. Extraído de {@link
     * #construirIcms} pra ser reaproveitado por {@link #cBenefDoItem} (o código de
     * benefício fiscal depende do mesmo CST resolvido). */
    private String resolverCstIcms(ItemNotaSaida item, boolean simples) {
        return item.getCst() == null || item.getCst().isBlank()
                ? (simples ? "102" : "40") : item.getCst().trim();
    }

    private boolean simplesNacional(Empresa empresa) {
        return empresa.getCrt() == CodRegimeTributario.SIMPLES_NACIONAL
                || empresa.getCrt() == CodRegimeTributario.SIMPLES_NACIONAL_EXCESSO_SUBLIMITE;
    }

    /** Código de benefício fiscal (tabela CONFAZ, campo {@code prod/cBenef} — não é dentro
     * de {@code imposto/ICMS} apesar do nome sugerir) — obrigatório pra SEFAZ-SP sempre que
     * o CST de ICMS é 40/41/50 (isenta/não-tributada/suspensão), senão rejeita com
     * cStat=930 "CST com benefício fiscal e não informado o código de benefício fiscal"
     * (confirmado 2026-08-17). "SP099999" cravado é o mesmo valor usado em 573/573 itens
     * com CST 40/41 nas notas reais de produção já conferidas ([[nfe-xmls-reais-teste]]) —
     * não modelado em lugar nenhum da entidade (nem {@code Produto} nem
     * {@code NaturezaOperacao}), então por ora é fixo igual ao legado; se aparecer uma UF/
     * situação com código diferente, precisa virar campo de verdade. */
    private String cBenefDoItem(ItemNotaSaida item, boolean simples) {
        if (simples) {
            return null;
        }
        String codigo = resolverCstIcms(item, false);
        return switch (codigo) {
            case "40", "41", "50" -> "SP099999";
            default -> null;
        };
    }

    private Element construirIcms(Document doc, ItemNotaSaida item, Empresa empresa) {
        Element icms = doc.createElementNS(NS_NFE, "ICMS");
        boolean simples = simplesNacional(empresa);
        String codigo = resolverCstIcms(item, simples);

        Element variante;
        if (simples) {
            variante = doc.createElementNS(NS_NFE, "ICMSSN" + codigo);
            text(doc, variante, "orig", 0);
            text(doc, variante, "CSOSN", codigo);
            // Crédito de ICMS do Simples (pCredSN/vCredICMSSN) fica zerado nesta versão —
            // ver Javadoc da classe e docs/EMISSAO-NFE.md.
        } else {
            // CST 40 (isenta), 41 (não tributada) e 50 (suspensão) são agrupados no mesmo
            // elemento ICMS40 no schema oficial — não é 1:1 elemento/CST como os demais
            // (confirmado validando contra o XSD oficial, 2026-08-17: "ICMS41" não existe,
            // schema espera um dos ICMS00/02/10/15/20/30/ICMS40/51/53/60/61/70/90/...).
            String elementoIcms = switch (codigo) {
                case "41", "50" -> "40";
                default -> codigo;
            };
            variante = doc.createElementNS(NS_NFE, "ICMS" + elementoIcms);
            text(doc, variante, "orig", 0);
            text(doc, variante, "CST", codigo);
            switch (codigo) {
                case "00":
                    text(doc, variante, "modBC", 3);
                    text(doc, variante, "vBC", dec(item.getBaseIcms(), 2));
                    text(doc, variante, "pICMS", dec(item.getAliqIcms(), 2));
                    text(doc, variante, "vICMS", dec(item.getValorIcms(), 2));
                    break;
                case "20":
                    text(doc, variante, "modBC", 3);
                    text(doc, variante, "vBC", dec(item.getBaseIcms(), 2));
                    text(doc, variante, "pRedBC", BigDecimal.ZERO.toPlainString());
                    text(doc, variante, "pICMS", dec(item.getAliqIcms(), 2));
                    text(doc, variante, "vICMS", dec(item.getValorIcms(), 2));
                    break;
                case "51":
                    // Diferimento — mesmo tratamento que o Axial cravava incondicionalmente
                    // (ver plano); aqui vem do dado (ItemNotaSaida.cst), não de hardcode.
                    text(doc, variante, "modBC", 3);
                    text(doc, variante, "vBC", "0.00");
                    text(doc, variante, "pICMS", dec(item.getAliqIcms(), 2));
                    text(doc, variante, "vICMSOp", dec(item.getValorIcms(), 2));
                    text(doc, variante, "pDif", "100.00");
                    text(doc, variante, "vICMSDif", dec(item.getValorIcms(), 2));
                    text(doc, variante, "vICMS", "0.00");
                    break;
                case "40":
                case "41":
                case "50":
                    break;
                case "10":
                    // ICMS10 (tributada com cobrança de ICMS por ST) exige o bloco ST
                    // completo (modBCST/pMVAST/vBCST/pICMSST/vICMSST) — schema rejeita sem
                    // isso com cStat=225 genérico "Falha no Schema XML do lote" (confirmado
                    // 2026-08-18: ItemNotaSaida já tem baseSt/valorSt, mas caía no `default`
                    // abaixo, que só cobre o ICMS próprio, sem ST nenhum). Estrutura e
                    // modBCST=4 conferidos contra 14/14 ocorrências reais de ICMS10 nas 176
                    // notas de referência ([[nfe-xmls-reais-teste]]); pICMSST=pICMS em
                    // 14/14 delas também. pMVAST não é campo próprio de ItemNotaSaida —
                    // derivado de vBCST/vBC (a margem que já gerou o baseSt gravado).
                    text(doc, variante, "modBC", 3);
                    text(doc, variante, "vBC", dec(item.getBaseIcms(), 2));
                    text(doc, variante, "pICMS", dec(item.getAliqIcms(), 2));
                    text(doc, variante, "vICMS", dec(item.getValorIcms(), 2));
                    text(doc, variante, "modBCST", 4);
                    text(doc, variante, "pMVAST", dec(mvaSt(item), 4));
                    text(doc, variante, "vBCST", dec(item.getBaseSt(), 2));
                    text(doc, variante, "pICMSST", dec(item.getAliqIcms(), 2));
                    text(doc, variante, "vICMSST", dec(item.getValorSt(), 2));
                    break;
                case "60":
                    text(doc, variante, "vBCSTRet", dec(item.getBaseSt(), 2));
                    text(doc, variante, "vICMSSTRet", dec(item.getValorSt(), 2));
                    break;
                default:
                    // 90/outras — mesma forma do CST 00, mais permissiva
                    text(doc, variante, "modBC", 3);
                    text(doc, variante, "vBC", dec(item.getBaseIcms(), 2));
                    text(doc, variante, "pICMS", dec(item.getAliqIcms(), 2));
                    text(doc, variante, "vICMS", dec(item.getValorIcms(), 2));
                    break;
            }
        }
        icms.appendChild(variante);
        return icms;
    }

    /** MVA (Margem de Valor Agregado) implícita: derivada de vBCST/vBC do item — não é
     * campo próprio de {@link ItemNotaSaida} (só {@code baseIcms}/{@code baseSt} existem),
     * então em vez de inventar um valor, recalcula a margem que já produziu o {@code baseSt}
     * gravado. Só faz sentido junto do CST 10 (ver Javadoc no switch de {@link
     * #construirIcms}). */
    private BigDecimal mvaSt(ItemNotaSaida item) {
        BigDecimal vBc = item.getBaseIcms();
        BigDecimal vBcSt = item.getBaseSt();
        if (vBc == null || vBc.compareTo(BigDecimal.ZERO) == 0 || vBcSt == null) {
            return BigDecimal.ZERO;
        }
        return vBcSt.divide(vBc, 6, RoundingMode.HALF_UP).subtract(BigDecimal.ONE).multiply(new BigDecimal(100));
    }

    private Element construirIpi(Document doc, ItemNotaSaida item) {
        if (item.getValorIpi() == null || item.getValorIpi().compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        Element ipi = doc.createElementNS(NS_NFE, "IPI");
        Element trib = doc.createElementNS(NS_NFE, "IPITrib");
        text(doc, trib, "CST", "50");
        text(doc, trib, "vBC", dec(item.getBaseIpi(), 2));
        text(doc, trib, "pIPI", dec(item.getAliqIpi(), 2));
        text(doc, trib, "vIPI", dec(item.getValorIpi(), 2));
        ipi.appendChild(trib);
        return ipi;
    }

    private Element construirPis(Document doc, ItemNotaSaida item, NaturezaOperacao natureza) {
        Element pis = doc.createElementNS(NS_NFE, "PIS");
        boolean venda = natureza != null && Boolean.TRUE.equals(natureza.getVenda());
        if (venda) {
            Element aliq = doc.createElementNS(NS_NFE, "PISAliq");
            text(doc, aliq, "CST", "01");
            text(doc, aliq, "vBC", dec(item.getSubTotal(), 2));
            text(doc, aliq, "pPIS", "0.00");
            text(doc, aliq, "vPIS", "0.00");
            pis.appendChild(aliq);
        } else {
            Element outr = doc.createElementNS(NS_NFE, "PISNT");
            text(doc, outr, "CST", "06");
            pis.appendChild(outr);
        }
        return pis;
    }

    private Element construirCofins(Document doc, ItemNotaSaida item, NaturezaOperacao natureza) {
        Element cofins = doc.createElementNS(NS_NFE, "COFINS");
        boolean venda = natureza != null && Boolean.TRUE.equals(natureza.getVenda());
        if (venda) {
            Element aliq = doc.createElementNS(NS_NFE, "COFINSAliq");
            text(doc, aliq, "CST", "01");
            text(doc, aliq, "vBC", dec(item.getSubTotal(), 2));
            text(doc, aliq, "pCOFINS", "0.00");
            text(doc, aliq, "vCOFINS", "0.00");
            cofins.appendChild(aliq);
        } else {
            Element outr = doc.createElementNS(NS_NFE, "COFINSNT");
            text(doc, outr, "CST", "06");
            cofins.appendChild(outr);
        }
        return cofins;
    }

    // ---- IBS/CBS (Reforma Tributária) ----

    /** Alíquota de IBS-UF efetiva: a gravada em {@code NaturezaOperacao} se o usuário
     * configurou algo diferente de zero; senão a alíquota-teste nacional do ano ({@link
     * AliquotaIbsCbs}, ver Javadoc de {@link #buscarAliquotaIbsCbs}); senão zero. Testa
     * contra zero, não contra {@code null}: {@code NaturezaOperacao.aliqIbsUf} é {@code
     * nullable = false} com default {@code BigDecimal.ZERO} na declaração (convenção do
     * projeto pra campo monetário) — nunca vem null do banco, então um check de nulidade
     * nunca dispara o fallback (bug confirmado 2026-08-18: reemissão depois do changeset de
     * correção do split continuou mandando pIBSUF=0.0000). NaturezaOperacao continua tendo
     * prioridade sobre o valor nacional quando configurada com um valor não-zero — é o único
     * jeito de um caso específico sobrepor a alíquota-teste. */
    private BigDecimal aliqIbsUfEfetiva(NaturezaOperacao natureza, AliquotaIbsCbs aliquotaTeste) {
        if (natureza != null && natureza.getAliqIbsUf() != null && natureza.getAliqIbsUf().compareTo(BigDecimal.ZERO) != 0) {
            return natureza.getAliqIbsUf();
        }
        return aliquotaTeste != null ? aliquotaTeste.getAliqIbsUf() : BigDecimal.ZERO;
    }

    private BigDecimal aliqIbsMunEfetiva(NaturezaOperacao natureza, AliquotaIbsCbs aliquotaTeste) {
        if (natureza != null && natureza.getAliqIbsMun() != null && natureza.getAliqIbsMun().compareTo(BigDecimal.ZERO) != 0) {
            return natureza.getAliqIbsMun();
        }
        return aliquotaTeste != null ? aliquotaTeste.getAliqIbsMun() : BigDecimal.ZERO;
    }

    private BigDecimal aliqCbsEfetiva(NaturezaOperacao natureza, AliquotaIbsCbs aliquotaTeste) {
        if (natureza != null && natureza.getAliqCbs() != null && natureza.getAliqCbs().compareTo(BigDecimal.ZERO) != 0) {
            return natureza.getAliqCbs();
        }
        return aliquotaTeste != null ? aliquotaTeste.getAliqCbs() : BigDecimal.ZERO;
    }

    /** vIBSUF do item — extraído pra ser somado nos totais ({@code total/IBSCBSTot/gIBS/
     * gIBSUF/vIBSUF}), que a SEFAZ confere contra o somatório dos itens (rejeição
     * cStat=1080 "Total de IBS UF difere da soma dos itens", confirmado 2026-08-18 — o
     * total vinha cravado em zero, nunca somando os itens). */
    private BigDecimal valorIbsUfDoItem(ItemNotaSaida item, NaturezaOperacao natureza, AliquotaIbsCbs aliquotaTeste) {
        BigDecimal base = item.getSubTotal() == null ? BigDecimal.ZERO : item.getSubTotal();
        return valorPercentual(base, aliqIbsUfEfetiva(natureza, aliquotaTeste));
    }

    private BigDecimal valorIbsMunDoItem(ItemNotaSaida item, NaturezaOperacao natureza, AliquotaIbsCbs aliquotaTeste) {
        BigDecimal base = item.getSubTotal() == null ? BigDecimal.ZERO : item.getSubTotal();
        return valorPercentual(base, aliqIbsMunEfetiva(natureza, aliquotaTeste));
    }

    // Soma de vIBSUF + vIBSMun já arredondados (não recalcula sobre a alíquota somada) —
    // garante vIBS = vIBSUF + vIBSMun sempre, mesmo em bordas de arredondamento.
    private BigDecimal valorIbsDoItem(ItemNotaSaida item, NaturezaOperacao natureza, AliquotaIbsCbs aliquotaTeste) {
        return valorIbsUfDoItem(item, natureza, aliquotaTeste).add(valorIbsMunDoItem(item, natureza, aliquotaTeste));
    }

    private BigDecimal valorCbsDoItem(ItemNotaSaida item, NaturezaOperacao natureza, AliquotaIbsCbs aliquotaTeste) {
        BigDecimal aliqCbs = aliqCbsEfetiva(natureza, aliquotaTeste);
        BigDecimal base = item.getSubTotal() == null ? BigDecimal.ZERO : item.getSubTotal();
        return base.multiply(aliqCbs).divide(new BigDecimal(100), 2, RoundingMode.HALF_UP);
    }

    private Element construirIbsCbs(Document doc, ItemNotaSaida item, NaturezaOperacao natureza, AliquotaIbsCbs aliquotaTeste) {
        Element ibsCbs = doc.createElementNS(NS_NFE, "IBSCBS");
        // Default quando o item não tem cClassTrib associado (Produto/NaturezaOperacao sem
        // FK configurada — notas antigas, de antes da associação virar obrigatória): "1"
        // ("000001", tributação integral sem benefício, CST "000") — não "0"/"000000", que
        // a SEFAZ rejeita com cStat=1023 "Classificação Tributária do IBS/CBS informada
        // inexistente" por não existir na tabela oficial (confirmado 2026-08-17; "000001" é
        // o código mais usado nas 176 notas reais de referência, 838/1175 itens).
        ClassTrib classTrib = buscarClassTrib(item.getCodClassTrib() != null ? item.getCodClassTrib() : 1);
        String cst = classTrib != null ? String.format("%03d", classTrib.getCst()) : "000";
        text(doc, ibsCbs, "CST", cst);
        text(doc, ibsCbs, "cClassTrib", String.format("%06d",
                item.getCodClassTrib() != null ? item.getCodClassTrib() : 1));

        Element gIbsCbs = doc.createElementNS(NS_NFE, "gIBSCBS");
        BigDecimal base = item.getSubTotal() == null ? BigDecimal.ZERO : item.getSubTotal();
        text(doc, gIbsCbs, "vBC", dec(base, 2));

        BigDecimal aliqUf = aliqIbsUfEfetiva(natureza, aliquotaTeste);
        BigDecimal aliqMun = aliqIbsMunEfetiva(natureza, aliquotaTeste);
        BigDecimal aliqCbs = aliqCbsEfetiva(natureza, aliquotaTeste);

        Element gIbsUf = doc.createElementNS(NS_NFE, "gIBSUF");
        text(doc, gIbsUf, "pIBSUF", dec(aliqUf, 4));
        text(doc, gIbsUf, "vIBSUF", dec(valorIbsUfDoItem(item, natureza, aliquotaTeste), 2));
        gIbsCbs.appendChild(gIbsUf);

        Element gIbsMun = doc.createElementNS(NS_NFE, "gIBSMun");
        text(doc, gIbsMun, "pIBSMun", dec(aliqMun, 4));
        text(doc, gIbsMun, "vIBSMun", dec(valorIbsMunDoItem(item, natureza, aliquotaTeste), 2));
        gIbsCbs.appendChild(gIbsMun);

        text(doc, gIbsCbs, "vIBS", dec(valorIbsDoItem(item, natureza, aliquotaTeste), 2));

        Element gCbs = doc.createElementNS(NS_NFE, "gCBS");
        text(doc, gCbs, "pCBS", dec(aliqCbs, 4));
        text(doc, gCbs, "vCBS", dec(valorCbsDoItem(item, natureza, aliquotaTeste), 2));
        gIbsCbs.appendChild(gCbs);

        ibsCbs.appendChild(gIbsCbs);
        return ibsCbs;
    }

    private BigDecimal valorPercentual(BigDecimal base, BigDecimal aliq) {
        return base.multiply(aliq).divide(new BigDecimal(100), 2, RoundingMode.HALF_UP);
    }

    private ClassTrib buscarClassTrib(Integer codigo) {
        return dataManager.load(ClassTrib.class)
                .query("select e from ClassTrib e where e.codigo = :codigo")
                .parameter("codigo", codigo)
                .optional()
                .orElse(null);
    }

    /** Alíquota-teste nacional (LC 214/2025) do ano de emissão — fallback pras alíquotas
     * de IBS/CBS quando {@code NaturezaOperacao} não tem valor próprio configurado (ver
     * [[aliquota-ibs-cbs-base]]: a tabela é só "informativa" por design, mas usá-la como
     * fallback aqui é diferente de fazer a UI ler dela automaticamente). Sem isso a SEFAZ
     * rejeita com cStat=1026 "Alíquota do IBS da UF inválida" (confirmado 2026-08-18: pIBSUF
     * tem que ser exatamente 0,1 e pIBSMun exatamente 0 pra 2026 — não a divisão 0,05/0,05
     * que o changelog original assumiu, corrigida em changeset separado). */
    private AliquotaIbsCbs buscarAliquotaIbsCbs(Integer ano) {
        return dataManager.load(AliquotaIbsCbs.class)
                .query("select e from AliquotaIbsCbs e where e.ano = :ano")
                .parameter("ano", ano)
                .optional()
                .orElse(null);
    }

    // ---- total ----

    private Element construirTotal(Document doc, NotaSaida notaSaida, BigDecimal totalVIbs, BigDecimal totalVIbsUf,
                                    BigDecimal totalVIbsMun, BigDecimal totalVCbs) {
        Element total = doc.createElementNS(NS_NFE, "total");
        Element icmsTot = doc.createElementNS(NS_NFE, "ICMSTot");
        text(doc, icmsTot, "vBC", dec(notaSaida.getBaseIcms(), 2));
        text(doc, icmsTot, "vICMS", dec(notaSaida.getValorIcms(), 2));
        text(doc, icmsTot, "vICMSDeson", "0.00");
        text(doc, icmsTot, "vFCP", "0.00");
        text(doc, icmsTot, "vBCST", dec(notaSaida.getBaseSt(), 2));
        text(doc, icmsTot, "vST", dec(notaSaida.getValorSt(), 2));
        text(doc, icmsTot, "vFCPST", "0.00");
        text(doc, icmsTot, "vFCPSTRet", "0.00");
        text(doc, icmsTot, "vProd", dec(notaSaida.getValorMercadoria(), 2));
        text(doc, icmsTot, "vFrete", dec(notaSaida.getFrete(), 2));
        text(doc, icmsTot, "vSeg", dec(notaSaida.getSeguro(), 2));
        text(doc, icmsTot, "vDesc", dec(notaSaida.getDesconto(), 2));
        text(doc, icmsTot, "vII", "0.00");
        text(doc, icmsTot, "vIPI", dec(notaSaida.getValorIpi(), 2));
        text(doc, icmsTot, "vIPIDevol", "0.00");
        text(doc, icmsTot, "vPIS", "0.00");
        text(doc, icmsTot, "vCOFINS", "0.00");
        text(doc, icmsTot, "vOutro", dec(notaSaida.getDespesas(), 2));
        text(doc, icmsTot, "vNF", dec(notaSaida.getValor(), 2));
        text(doc, icmsTot, "vTotTrib", "0.00");
        total.appendChild(icmsTot);

        if (incluirReformaTributaria) {
            // IBSCBSTot: grupos vDif/vDevTrib (diferimento/devolução) e
            // vCredPres/vCredPresCondSus (crédito presumido) confirmados obrigatórios contra
            // 30 notas reais de teste com IBSCBS (ver conversa 2026-08-17) — zerados aqui,
            // mesma simplificação já documentada em docs/EMISSAO-NFE.md (sem
            // diferimento/devolução/crédito presumido nesta versão).
            Element ibsCbsTot = doc.createElementNS(NS_NFE, "IBSCBSTot");
            text(doc, ibsCbsTot, "vBCIBSCBS", dec(notaSaida.getValorMercadoria(), 2));
            Element gIbs = doc.createElementNS(NS_NFE, "gIBS");
            Element gIbsUf = doc.createElementNS(NS_NFE, "gIBSUF");
            text(doc, gIbsUf, "vDif", "0.00");
            text(doc, gIbsUf, "vDevTrib", "0.00");
            text(doc, gIbsUf, "vIBSUF", dec(totalVIbsUf, 2));
            gIbs.appendChild(gIbsUf);
            Element gIbsMun = doc.createElementNS(NS_NFE, "gIBSMun");
            text(doc, gIbsMun, "vDif", "0.00");
            text(doc, gIbsMun, "vDevTrib", "0.00");
            text(doc, gIbsMun, "vIBSMun", dec(totalVIbsMun, 2));
            gIbs.appendChild(gIbsMun);
            text(doc, gIbs, "vIBS", dec(totalVIbs, 2));
            text(doc, gIbs, "vCredPres", "0.00");
            text(doc, gIbs, "vCredPresCondSus", "0.00");
            ibsCbsTot.appendChild(gIbs);
            Element gCbs = doc.createElementNS(NS_NFE, "gCBS");
            text(doc, gCbs, "vDif", "0.00");
            text(doc, gCbs, "vDevTrib", "0.00");
            text(doc, gCbs, "vCBS", dec(totalVCbs, 2));
            text(doc, gCbs, "vCredPres", "0.00");
            text(doc, gCbs, "vCredPresCondSus", "0.00");
            ibsCbsTot.appendChild(gCbs);
            total.appendChild(ibsCbsTot);

            // vNFTot também é grupo novo da Reforma (não existe no TTotal clássico) — só faz
            // sentido junto com IBSCBSTot, por isso dentro do mesmo if.
            text(doc, total, "vNFTot", dec(notaSaida.getValor().add(totalVIbs).add(totalVCbs), 2));
        }

        return total;
    }

    // ---- transp ----

    private Element construirTransp(Document doc, NotaSaida notaSaida) {
        Element transp = doc.createElementNS(NS_NFE, "transp");
        // NotaSaida não modela transportador/frete ainda — sem transporte é o único valor
        // seguro de cravar aqui (ver docs/EMISSAO-NFE.md).
        text(doc, transp, "modFrete", 9);
        BigDecimal pesoLiquido = notaSaida.getPesoLiquido();
        BigDecimal pesoBruto = notaSaida.getPesoBruto();
        if ((pesoLiquido != null && pesoLiquido.compareTo(BigDecimal.ZERO) > 0)
                || (pesoBruto != null && pesoBruto.compareTo(BigDecimal.ZERO) > 0)) {
            Element vol = doc.createElementNS(NS_NFE, "vol");
            text(doc, vol, "pesoL", dec(pesoLiquido, 3));
            text(doc, vol, "pesoB", dec(pesoBruto, 3));
            transp.appendChild(vol);
        }
        return transp;
    }

    // ---- cobr/pag ----

    private List<TituloReceber> buscarTitulos(NotaSaida notaSaida) {
        return dataManager.load(TituloReceber.class)
                .query("select e from TituloReceber e where e.notaSaida = :notaSaida order by e.dataVencimento")
                .parameter("notaSaida", notaSaida)
                .list();
    }

    private Element construirCobr(Document doc, NotaSaida notaSaida, List<TituloReceber> titulos) {
        if (titulos.isEmpty()) {
            return null;
        }
        Element cobr = doc.createElementNS(NS_NFE, "cobr");
        Element fat = doc.createElementNS(NS_NFE, "fat");
        text(doc, fat, "nFat", notaSaida.getNumero());
        text(doc, fat, "vOrig", dec(notaSaida.getValor(), 2));
        text(doc, fat, "vLiq", dec(notaSaida.getValor(), 2));
        cobr.appendChild(fat);
        int n = 1;
        for (TituloReceber titulo : titulos) {
            Element dup = doc.createElementNS(NS_NFE, "dup");
            text(doc, dup, "nDup", String.format("%03d", n));
            text(doc, dup, "dVenc", titulo.getDataVencimento().toString());
            text(doc, dup, "vDup", dec(titulo.getValor(), 2));
            cobr.appendChild(dup);
            n++;
        }
        return cobr;
    }

    private Element construirPag(Document doc, List<TituloReceber> titulos) {
        Element pag = doc.createElementNS(NS_NFE, "pag");
        if (titulos.isEmpty()) {
            Element detPag = doc.createElementNS(NS_NFE, "detPag");
            // indPag: presente em 173/173 notas reais de teste com IBSCBS conferidas em
            // 2026-08-17 — mandatório na sequência do schema atual, antes de tPag.
            text(doc, detPag, "indPag", 0);
            text(doc, detPag, "tPag", "90");
            text(doc, detPag, "vPag", "0.00");
            pag.appendChild(detPag);
        } else {
            for (TituloReceber titulo : titulos) {
                Element detPag = doc.createElementNS(NS_NFE, "detPag");
                text(doc, detPag, "indPag", 1);
                text(doc, detPag, "tPag", "15");
                text(doc, detPag, "vPag", dec(titulo.getValor(), 2));
                pag.appendChild(detPag);
            }
        }
        return pag;
    }

    // ---- infAdic ----

    private Element construirInfAdic(Document doc, NotaSaida notaSaida) {
        return null;
    }

    // ---- utilitários ----

    private Document novoDocumento() {
        try {
            // namespace-aware=true: exigido pra criar elementos com createElementNS (usado em
            // todo o resto da classe) — ver comentário em construir() sobre o motivo real
            // (cStat=297 de assinatura).
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            return builder.newDocument();
        } catch (ParserConfigurationException e) {
            throw new IllegalStateException(e);
        }
    }

    // Caracteres de controle (fora de \t\n\r) e surrogates soltos não são válidos em XML 1.0 —
    // o parser DOM recusa com INVALID_CHARACTER_ERR ao tentar setTextContent. Dado vindo de
    // import do sistema legado às vezes carrega esse tipo de lixo (padding com bytes de
    // controle); sanitiza aqui, no único ponto que cria nó de texto, em vez de em cada campo.
    private static final java.util.regex.Pattern CARACTER_XML_INVALIDO =
            java.util.regex.Pattern.compile("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\uFFFE\\uFFFF]");

    private void text(Document doc, Element parent, String tag, Object valor) {
        if (valor == null) {
            return;
        }
        Element el = doc.createElementNS(NS_NFE, tag);
        el.setTextContent(CARACTER_XML_INVALIDO.matcher(String.valueOf(valor)).replaceAll(""));
        parent.appendChild(el);
    }

    private String dec(BigDecimal valor, int scale) {
        return (valor == null ? BigDecimal.ZERO : valor).setScale(scale, RoundingMode.HALF_UP).toPlainString();
    }

    private String somenteDigitos(String texto) {
        return texto == null ? "" : texto.replaceAll("\\D", "");
    }

    // TIe aceita dígitos OU o literal "ISENTO" — somenteDigitos() sozinho zeraria "ISENTO"
    // (nenhum dígito sobra), voltando pro mesmo <IE/> vazio inválido contra o schema.
    private String inscEstOuIsento(String texto) {
        if (texto != null && texto.trim().equalsIgnoreCase("ISENTO")) {
            return "ISENTO";
        }
        return somenteDigitos(texto);
    }

    private String vazioComo(String valor, String padrao) {
        return valor == null || valor.isBlank() ? padrao : valor;
    }

    private String enderecoCompleto(String tipoLogradouro, String logradouro) {
        String base = logradouro == null ? "" : logradouro;
        return tipoLogradouro == null || tipoLogradouro.isBlank() ? base : tipoLogradouro + " " + base;
    }
}
