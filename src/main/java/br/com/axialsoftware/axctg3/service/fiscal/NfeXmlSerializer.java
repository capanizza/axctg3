package br.com.axialsoftware.axctg3.service.fiscal;

import br.com.axialsoftware.axctg3.entity.fiscal.Nfe;
import br.com.axialsoftware.axctg3.entity.fiscal.NfeDi;
import br.com.axialsoftware.axctg3.entity.fiscal.NfeDiAdicao;
import br.com.axialsoftware.axctg3.entity.fiscal.NfeDuplicata;
import br.com.axialsoftware.axctg3.entity.fiscal.NfeItem;
import br.com.axialsoftware.axctg3.entity.fiscal.NfePagamento;
import br.com.axialsoftware.axctg3.entity.fiscal.NfeVolume;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static br.com.axialsoftware.axctg3.service.fiscal.NfeXml.dec;
import static br.com.axialsoftware.axctg3.service.fiscal.NfeXml.element;
import static br.com.axialsoftware.axctg3.service.fiscal.NfeXml.somenteDigitos;
import static br.com.axialsoftware.axctg3.service.fiscal.NfeXml.text;

/**
 * Escreve o XML da NFe 4.00 (grupo {@code infNFe}, sem assinatura — ver {@link
 * NfeXmlSigner}) direto dos campos de uma {@link Nfe} digitada, <b>sem calcular nada</b>:
 * cada tag sai com o valor que está no campo. É o inverso do {@link NfeXmlParser} — os
 * dois cobrem os mesmos campos, de modo que ler um XML e escrevê-lo de volta preserva tudo
 * o que a entidade modela ({@code NfeXmlSerializerTest} confere essa ida e volta e valida
 * o resultado contra o XSD oficial).
 *
 * <p>Diferente do {@link NfeXmlBuilder}, que monta a nota a partir de {@code NotaSaida}
 * calculando impostos, rateios e totais. Este serve à NFe digitada: a porta de exceção pra
 * notas que a NotaSaida não cobre (importação, por exemplo), em que o operador informa
 * cada valor. Quem chama é responsável por já ter preenchido a identificação da emissão
 * (chave, número, série, cNF, cDV, dhEmi) e o emitente.
 *
 * <p>Regra geral pros campos opcionais do leiaute: sai a tag quando o valor é diferente de
 * zero (ou não nulo, pra texto). Os tipos {@code *Opc} do schema nem aceitam zero, então
 * omitir é o único jeito correto de "não informar". Campos obrigatórios saem sempre.
 *
 * <p>Fora de escopo (a entidade não modela, então a nota digitada não tem como informar —
 * {@link #serializar} recusa com mensagem clara quando o dado pede um desses casos):
 * ICMS monofásico de combustíveis (CST 02/15/53/61), {@code ICMSPart}/{@code ICMSST},
 * PIS/COFINS por quantidade (CST 03), IPI por unidade, FCP-ST, ICMS-ST desonerado,
 * {@code ICMSUFDest} por item, retirada/entrega, autXML, exportação, compra, cana,
 * infRespTec, rastro/med/arma/veicProd/comb e as partes da Reforma Tributária já
 * listadas como fora de escopo no Javadoc de {@link NfeItem}.
 */
@Component
public class NfeXmlSerializer {

    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    public Document serializar(Nfe nfe) {
        String chave = nfe.getChave();
        if (chave == null || !chave.matches("\\d{44}")) {
            throw new IllegalStateException("NFe sem chave de acesso de 44 dígitos — a chave é gerada na transmissão");
        }
        List<NfeItem> itens = lista(nfe.getItens());
        if (itens.isEmpty()) {
            throw new IllegalStateException("NFe sem itens");
        }

        Document doc = NfeXml.novoDocumento();
        Element nfeEl = element(doc, "NFe");
        doc.appendChild(nfeEl);
        Element infNFe = element(doc, "infNFe");
        infNFe.setAttribute("Id", "NFe" + chave);
        infNFe.setAttribute("versao", "4.00");
        nfeEl.appendChild(infNFe);

        infNFe.appendChild(ide(doc, nfe));
        infNFe.appendChild(emit(doc, nfe));
        infNFe.appendChild(dest(doc, nfe));
        int posicao = 1;
        for (NfeItem item : itens) {
            infNFe.appendChild(det(doc, item, posicao++));
        }
        infNFe.appendChild(total(doc, nfe, itens));
        infNFe.appendChild(transp(doc, nfe));
        Element cobr = cobr(doc, nfe);
        if (cobr != null) {
            infNFe.appendChild(cobr);
        }
        infNFe.appendChild(pag(doc, nfe));
        Element infAdic = infAdic(doc, nfe);
        if (infAdic != null) {
            infNFe.appendChild(infAdic);
        }
        return doc;
    }

    // ---- ide ----

    private Element ide(Document doc, Nfe nfe) {
        Element ide = element(doc, "ide");
        text(doc, ide, "cUF", obrigatorio(nfe.getCodUf(), "UF do emitente (cUF)"));
        text(doc, ide, "cNF", String.format("%08d", obrigatorio(nfe.getCodNf(), "código numérico (cNF)")));
        text(doc, ide, "natOp", obrigatorio(aparar(nfe.getNatOp()), "natureza da operação"));
        text(doc, ide, "mod", nfe.getMod() != null ? nfe.getMod() : 55);
        text(doc, ide, "serie", obrigatorio(nfe.getSerie(), "série"));
        text(doc, ide, "nNF", obrigatorio(nfe.getNumeroNf(), "número da nota"));
        text(doc, ide, "dhEmi", dataHora(obrigatorio(nfe.getDhEmi(), "data/hora de emissão")));
        if (nfe.getDhSaiEnt() != null) {
            text(doc, ide, "dhSaiEnt", dataHora(nfe.getDhSaiEnt()));
        }
        text(doc, ide, "tpNF", obrigatorio(nfe.getTpNf(), "tipo da operação (entrada/saída)"));
        text(doc, ide, "idDest", obrigatorio(nfe.getIdDest(), "destino da operação (idDest)"));
        text(doc, ide, "cMunFG", obrigatorio(nfe.getCodMunFg(), "município do fato gerador"));
        text(doc, ide, "tpImp", nfe.getTpImp() != null ? nfe.getTpImp() : 1);
        text(doc, ide, "tpEmis", nfe.getTpEmis() != null ? nfe.getTpEmis() : 1);
        text(doc, ide, "cDV", obrigatorio(nfe.getCodDv(), "dígito verificador da chave"));
        text(doc, ide, "tpAmb", obrigatorio(nfe.getTpAmb(), "ambiente (tpAmb)"));
        text(doc, ide, "finNFe", nfe.getFinNfe() != null ? nfe.getFinNfe() : 1);
        text(doc, ide, "indFinal", nfe.getIndFinal() != null ? nfe.getIndFinal() : 0);
        text(doc, ide, "indPres", nfe.getIndPres() != null ? nfe.getIndPres() : 0);
        text(doc, ide, "indIntermed", nfe.getIndIntermed());
        text(doc, ide, "procEmi", nfe.getProcEmi() != null ? nfe.getProcEmi() : 0);
        text(doc, ide, "verProc", nfe.getVerProc() != null ? nfe.getVerProc() : "axctg3");
        String refNfe = aparar(nfe.getRefNfe());
        if (refNfe != null) {
            Element nfRef = element(doc, "NFref");
            text(doc, nfRef, "refNFe", refNfe);
            ide.appendChild(nfRef);
        }
        return ide;
    }

    // ---- emit ----

    private Element emit(Document doc, Nfe nfe) {
        Element emit = element(doc, "emit");
        String cnpjOuCpf = somenteDigitos(nfe.getEmitCnpj());
        text(doc, emit, cnpjOuCpf.length() == 11 ? "CPF" : "CNPJ", obrigatorio(vazioComoNulo(cnpjOuCpf), "CNPJ do emitente"));
        text(doc, emit, "xNome", obrigatorio(aparar(nfe.getEmitXNome()), "razão social do emitente"));
        text(doc, emit, "xFant", aparar(nfe.getEmitXFant()));
        Element ender = element(doc, "enderEmit");
        text(doc, ender, "xLgr", aparar(nfe.getEmitXLgr()));
        text(doc, ender, "nro", aparar(nfe.getEmitNro()));
        text(doc, ender, "xCpl", aparar(nfe.getEmitXCpl()));
        text(doc, ender, "xBairro", aparar(nfe.getEmitXBairro()));
        text(doc, ender, "cMun", nfe.getEmitCMun());
        text(doc, ender, "xMun", aparar(nfe.getEmitXMun()));
        text(doc, ender, "UF", aparar(nfe.getEmitUf()));
        text(doc, ender, "CEP", vazioComoNulo(somenteDigitos(nfe.getEmitCep())));
        // emitente de NFe é sempre estabelecimento no Brasil
        text(doc, ender, "cPais", 1058);
        text(doc, ender, "xPais", "BRASIL");
        text(doc, ender, "fone", vazioComoNulo(somenteDigitos(nfe.getEmitFone())));
        emit.appendChild(ender);
        text(doc, emit, "IE", obrigatorio(aparar(nfe.getEmitIe()), "IE do emitente"));
        text(doc, emit, "CRT", obrigatorio(nfe.getEmitCrt(), "regime tributário do emitente (CRT)"));
        return emit;
    }

    // ---- dest ----

    private Element dest(Document doc, Nfe nfe) {
        Element dest = element(doc, "dest");
        String cnpj = vazioComoNulo(somenteDigitos(nfe.getDestCnpj()));
        String cpf = vazioComoNulo(somenteDigitos(nfe.getDestCpf()));
        if (cnpj != null) {
            text(doc, dest, "CNPJ", cnpj);
        } else if (cpf != null) {
            text(doc, dest, "CPF", cpf);
        } else {
            // destinatário estrangeiro: o leiaute aceita idEstrangeiro vazio
            String idEstrangeiro = aparar(nfe.getDestIdEstrangeiro());
            text(doc, dest, "idEstrangeiro", idEstrangeiro != null ? idEstrangeiro : "");
        }
        text(doc, dest, "xNome", aparar(nfe.getDestXNome()));
        if (aparar(nfe.getDestXLgr()) != null) {
            Element ender = element(doc, "enderDest");
            text(doc, ender, "xLgr", aparar(nfe.getDestXLgr()));
            text(doc, ender, "nro", aparar(nfe.getDestNro()));
            text(doc, ender, "xCpl", aparar(nfe.getDestXCpl()));
            text(doc, ender, "xBairro", aparar(nfe.getDestXBairro()));
            text(doc, ender, "cMun", nfe.getDestCMun());
            text(doc, ender, "xMun", aparar(nfe.getDestXMun()));
            text(doc, ender, "UF", aparar(nfe.getDestUf()));
            text(doc, ender, "CEP", vazioComoNulo(somenteDigitos(nfe.getDestCep())));
            text(doc, ender, "cPais", nfe.getDestCPais());
            text(doc, ender, "xPais", aparar(nfe.getDestXPais()));
            text(doc, ender, "fone", vazioComoNulo(somenteDigitos(nfe.getDestFone())));
            dest.appendChild(ender);
        }
        text(doc, dest, "indIEDest", nfe.getDestIndIe() != null ? nfe.getDestIndIe() : 9);
        text(doc, dest, "IE", vazioComoNulo(somenteDigitos(nfe.getDestIe())));
        text(doc, dest, "email", aparar(nfe.getDestEmail()));
        return dest;
    }

    // ---- det ----

    private Element det(Document doc, NfeItem item, int posicao) {
        Element det = element(doc, "det");
        det.setAttribute("nItem", String.valueOf(item.getItem() != null ? item.getItem() : posicao));
        det.appendChild(prod(doc, item));
        det.appendChild(imposto(doc, item));
        // infAdProd antes de vItem: vItem é extensão da Reforma Tributária, sempre por último
        // em det (ordem errada deu cStat=225, ver NfeXmlBuilder.construirDet)
        text(doc, det, "infAdProd", aparar(item.getInfoAdicionalProduto()));
        opcional(doc, det, "vItem", item.getValorItem(), 2);
        return det;
    }

    private Element prod(Document doc, NfeItem item) {
        String rotulo = "item " + item.getItem();
        Element prod = element(doc, "prod");
        text(doc, prod, "cProd", obrigatorio(aparar(item.getCodProd()), "código do produto (" + rotulo + ")"));
        text(doc, prod, "cEAN", semGtin(item.getCodEan()));
        text(doc, prod, "xProd", obrigatorio(aparar(item.getDescProd()), "descrição do produto (" + rotulo + ")"));
        text(doc, prod, "NCM", obrigatorio(aparar(item.getNcm()), "NCM (" + rotulo + ")"));
        text(doc, prod, "CEST", aparar(item.getCest()));
        text(doc, prod, "cBenef", aparar(item.getCodBenef()));
        text(doc, prod, "CFOP", obrigatorio(item.getCfop(), "CFOP (" + rotulo + ")"));
        text(doc, prod, "uCom", obrigatorio(aparar(item.getUnCom()), "unidade comercial (" + rotulo + ")"));
        text(doc, prod, "qCom", dec(item.getQuantCom(), 4));
        text(doc, prod, "vUnCom", dec(item.getValorUnCom(), 10));
        text(doc, prod, "vProd", dec(item.getValorProd(), 2));
        text(doc, prod, "cEANTrib", semGtin(item.getCodEanTrib()));
        String unTrib = aparar(item.getUnTrib());
        text(doc, prod, "uTrib", unTrib != null ? unTrib : aparar(item.getUnCom()));
        boolean temQuantTrib = naoZero(item.getQuantTrib());
        text(doc, prod, "qTrib", dec(temQuantTrib ? item.getQuantTrib() : item.getQuantCom(), 4));
        text(doc, prod, "vUnTrib", dec(temQuantTrib ? item.getValorUnTrib() : item.getValorUnCom(), 10));
        opcional(doc, prod, "vFrete", item.getValorFrete(), 2);
        opcional(doc, prod, "vSeg", item.getValorSeg(), 2);
        opcional(doc, prod, "vDesc", item.getValorDesc(), 2);
        opcional(doc, prod, "vOutro", item.getValorOutro(), 2);
        text(doc, prod, "indTot", item.getIndTot() != null ? item.getIndTot() : 1);
        for (NfeDi di : lista(item.getDis())) {
            prod.appendChild(di(doc, di, rotulo));
        }
        return prod;
    }

    private Element di(Document doc, NfeDi di, String rotulo) {
        String r = "DI do " + rotulo;
        Element el = element(doc, "DI");
        text(doc, el, "nDI", obrigatorio(aparar(di.getNumeroDi()), "número da " + r));
        text(doc, el, "dDI", obrigatorio(di.getDataDi(), "data de registro da " + r).toString());
        text(doc, el, "xLocDesemb", obrigatorio(aparar(di.getLocalDesembaraco()), "local do desembaraço da " + r));
        text(doc, el, "UFDesemb", obrigatorio(aparar(di.getUfDesembaraco()), "UF do desembaraço da " + r));
        text(doc, el, "dDesemb", obrigatorio(di.getDataDesembaraco(), "data do desembaraço da " + r).toString());
        text(doc, el, "tpViaTransp", obrigatorio(di.getViaTransporte(), "via de transporte da " + r).getId());
        opcional(doc, el, "vAFRMM", di.getValorAfrmm(), 2);
        text(doc, el, "tpIntermedio", obrigatorio(di.getFormaImportacao(), "forma de importação da " + r).getId());
        String adquirente = vazioComoNulo(somenteDigitos(di.getCnpjCpfAdquirente()));
        if (adquirente != null) {
            text(doc, el, adquirente.length() == 11 ? "CPF" : "CNPJ", adquirente);
        }
        text(doc, el, "UFTerceiro", aparar(di.getUfTerceiro()));
        text(doc, el, "cExportador", obrigatorio(aparar(di.getCodExportador()), "código do exportador da " + r));
        List<NfeDiAdicao> adicoes = lista(di.getAdicoes());
        if (adicoes.isEmpty()) {
            throw new IllegalStateException("A " + r + " precisa de pelo menos uma adição");
        }
        for (NfeDiAdicao adicao : adicoes) {
            Element adi = element(doc, "adi");
            text(doc, adi, "nAdicao", adicao.getNumeroAdicao());
            text(doc, adi, "nSeqAdic", obrigatorio(adicao.getSequencial(), "sequencial da adição da " + r));
            text(doc, adi, "cFabricante", obrigatorio(aparar(adicao.getCodFabricante()), "código do fabricante da adição da " + r));
            opcional(doc, adi, "vDescDI", adicao.getValorDesconto(), 2);
            text(doc, adi, "nDraw", aparar(adicao.getNumDrawback()));
            el.appendChild(adi);
        }
        return el;
    }

    // ---- imposto ----

    private Element imposto(Document doc, NfeItem item) {
        Element imposto = element(doc, "imposto");
        opcional(doc, imposto, "vTotTrib", item.getValorTotTributos(), 2);
        imposto.appendChild(icms(doc, item));
        Element ipi = ipi(doc, item);
        if (ipi != null) {
            imposto.appendChild(ipi);
        }
        if (!lista(item.getDis()).isEmpty() || naoZero(item.getBaseIi()) || naoZero(item.getValorDespAdu())
                || naoZero(item.getValorIi()) || naoZero(item.getValorIof())) {
            Element ii = element(doc, "II");
            text(doc, ii, "vBC", dec(item.getBaseIi(), 2));
            text(doc, ii, "vDespAdu", dec(item.getValorDespAdu(), 2));
            text(doc, ii, "vII", dec(item.getValorIi(), 2));
            text(doc, ii, "vIOF", dec(item.getValorIof(), 2));
            imposto.appendChild(ii);
        }
        Element pis = pisCofins(doc, item, "PIS", item.getCstPis(), item.getBasePis(), item.getAliqPis(), item.getValorPis());
        if (pis != null) {
            imposto.appendChild(pis);
        }
        Element cofins = pisCofins(doc, item, "COFINS", item.getCstCofins(), item.getBaseCofins(), item.getAliqCofins(),
                item.getValorCofins());
        if (cofins != null) {
            imposto.appendChild(cofins);
        }
        Element is = impostoSeletivo(doc, item);
        if (is != null) {
            imposto.appendChild(is);
        }
        Element ibsCbs = ibsCbs(doc, item);
        if (ibsCbs != null) {
            imposto.appendChild(ibsCbs);
        }
        return imposto;
    }

    /**
     * Escolhe a variante do grupo ICMS pelo CSOSN (Simples) ou CST e escreve os campos
     * dela na ordem do schema. CST 41/50 vão no elemento ICMS40, e os CSOSN 103/300/400
     * no ICMSSN102 (203 no ICMSSN202) — o schema agrupa, não é um elemento por código.
     */
    private Element icms(Document doc, NfeItem item) {
        String rotulo = "item " + item.getItem();
        String csosn = aparar(item.getCsosnIcms());
        String cst = aparar(item.getCstIcms());
        Element icms = element(doc, "ICMS");
        Element v;
        if (csosn != null) {
            String elemento = switch (csosn) {
                case "101", "102", "201", "202", "500", "900" -> csosn;
                case "103", "300", "400" -> "102";
                case "203" -> "202";
                default -> throw new IllegalStateException("CSOSN \"" + csosn + "\" inválido (" + rotulo + ")");
            };
            v = element(doc, "ICMSSN" + elemento);
            text(doc, v, "orig", origem(item));
            text(doc, v, "CSOSN", csosn);
            switch (elemento) {
                case "101" -> creditoSimples(doc, v, item);
                case "201" -> {
                    icmsSt(doc, v, item);
                    creditoSimples(doc, v, item);
                }
                case "202" -> icmsSt(doc, v, item);
                case "500" -> stRetido(doc, v, item);
                case "900" -> {
                    if (naoZero(item.getBaseIcms()) || naoZero(item.getValorIcms()) || naoZero(item.getAliqIcms())) {
                        text(doc, v, "modBC", modBc(item));
                        text(doc, v, "vBC", dec(item.getBaseIcms(), 2));
                        opcional(doc, v, "pRedBC", item.getPercReducaoBcIcms(), 4);
                        text(doc, v, "pICMS", dec(item.getAliqIcms(), 4));
                        text(doc, v, "vICMS", dec(item.getValorIcms(), 2));
                    }
                    if (naoZero(item.getBaseIcmsSt()) || naoZero(item.getValorIcmsSt())) {
                        icmsSt(doc, v, item);
                    }
                    if (naoZero(item.getAliqCredSn()) || naoZero(item.getValorCredIcmsSn())) {
                        creditoSimples(doc, v, item);
                    }
                }
                default -> {
                    // 102: só orig + CSOSN
                }
            }
        } else if (cst != null) {
            String elemento = switch (cst) {
                case "00", "10", "20", "30", "40", "51", "60", "70", "90" -> cst;
                case "41", "50" -> "40";
                case "02", "15", "53", "61" -> throw new IllegalStateException(
                        "ICMS monofásico de combustíveis (CST " + cst + ") não é suportado na NFe digitada (" + rotulo + ")");
                default -> throw new IllegalStateException("CST de ICMS \"" + cst + "\" inválido (" + rotulo + ")");
            };
            v = element(doc, "ICMS" + elemento);
            text(doc, v, "orig", origem(item));
            text(doc, v, "CST", cst);
            switch (elemento) {
                case "00" -> {
                    icmsProprio(doc, v, item, false);
                    fcp(doc, v, item, false);
                }
                case "10" -> {
                    icmsProprio(doc, v, item, false);
                    fcp(doc, v, item, true);
                    icmsSt(doc, v, item);
                }
                case "20" -> {
                    icmsProprio(doc, v, item, true);
                    fcp(doc, v, item, true);
                    desoneracao(doc, v, item);
                }
                case "30" -> {
                    icmsSt(doc, v, item);
                    desoneracao(doc, v, item);
                }
                case "40" -> desoneracao(doc, v, item);
                case "51" -> {
                    text(doc, v, "modBC", item.getModBcIcms());
                    opcional(doc, v, "pRedBC", item.getPercReducaoBcIcms(), 4);
                    text(doc, v, "vBC", dec(item.getBaseIcms(), 2));
                    text(doc, v, "pICMS", dec(item.getAliqIcms(), 4));
                    text(doc, v, "vICMSOp", dec(item.getValorIcmsOp(), 2));
                    text(doc, v, "pDif", dec(item.getPercDifIcms(), 4));
                    text(doc, v, "vICMSDif", dec(item.getValorIcmsDif(), 2));
                    text(doc, v, "vICMS", dec(item.getValorIcms(), 2));
                    fcp(doc, v, item, true);
                }
                case "60" -> stRetido(doc, v, item);
                case "70" -> {
                    icmsProprio(doc, v, item, true);
                    fcp(doc, v, item, true);
                    icmsSt(doc, v, item);
                    desoneracao(doc, v, item);
                }
                default -> {
                    // 90: ICMS próprio e ST opcionais, cada um só quando informado
                    if (naoZero(item.getBaseIcms()) || naoZero(item.getValorIcms()) || naoZero(item.getAliqIcms())) {
                        text(doc, v, "modBC", modBc(item));
                        text(doc, v, "vBC", dec(item.getBaseIcms(), 2));
                        opcional(doc, v, "pRedBC", item.getPercReducaoBcIcms(), 4);
                        text(doc, v, "pICMS", dec(item.getAliqIcms(), 4));
                        text(doc, v, "vICMS", dec(item.getValorIcms(), 2));
                        fcp(doc, v, item, true);
                    }
                    if (naoZero(item.getBaseIcmsSt()) || naoZero(item.getValorIcmsSt())) {
                        icmsSt(doc, v, item);
                    }
                    desoneracao(doc, v, item);
                }
            }
        } else {
            throw new IllegalStateException("Informe o CST ou o CSOSN do ICMS (" + rotulo + ")");
        }
        icms.appendChild(v);
        return icms;
    }

    private void icmsProprio(Document doc, Element v, NfeItem item, boolean comReducao) {
        text(doc, v, "modBC", modBc(item));
        if (comReducao) {
            text(doc, v, "pRedBC", dec(item.getPercReducaoBcIcms(), 4));
        }
        text(doc, v, "vBC", dec(item.getBaseIcms(), 2));
        text(doc, v, "pICMS", dec(item.getAliqIcms(), 4));
        text(doc, v, "vICMS", dec(item.getValorIcms(), 2));
    }

    // FCP: no ICMS00 sem vBCFCP; nas demais variantes com base própria
    private void fcp(Document doc, Element v, NfeItem item, boolean comBase) {
        if (!naoZero(item.getValorFcp())) {
            return;
        }
        if (comBase) {
            text(doc, v, "vBCFCP", dec(item.getBaseFcp(), 2));
        }
        text(doc, v, "pFCP", dec(item.getAliqFcp(), 4));
        text(doc, v, "vFCP", dec(item.getValorFcp(), 2));
    }

    private void icmsSt(Document doc, Element v, NfeItem item) {
        text(doc, v, "modBCST", item.getModBcIcmsSt() != null ? item.getModBcIcmsSt() : 4);
        opcional(doc, v, "pMVAST", item.getPercMvaIcmsSt(), 4);
        opcional(doc, v, "pRedBCST", item.getPercReducaoBcIcmsSt(), 4);
        text(doc, v, "vBCST", dec(item.getBaseIcmsSt(), 2));
        text(doc, v, "pICMSST", dec(item.getAliqIcmsSt(), 4));
        text(doc, v, "vICMSST", dec(item.getValorIcmsSt(), 2));
    }

    private void desoneracao(Document doc, Element v, NfeItem item) {
        if (!naoZero(item.getValorIcmsDeson())) {
            return;
        }
        text(doc, v, "vICMSDeson", dec(item.getValorIcmsDeson(), 2));
        text(doc, v, "motDesICMS", obrigatorio(item.getMotDesIcms(),
                "motivo da desoneração do ICMS (item " + item.getItem() + ")"));
    }

    private void stRetido(Document doc, Element v, NfeItem item) {
        if (!naoZero(item.getBaseIcmsStRet()) && !naoZero(item.getValorIcmsStRet())) {
            return;
        }
        text(doc, v, "vBCSTRet", dec(item.getBaseIcmsStRet(), 2));
        text(doc, v, "pST", dec(item.getAliqIcmsStRet(), 4));
        opcional(doc, v, "vICMSSubstituto", item.getValorIcmsSubstituto(), 2);
        text(doc, v, "vICMSSTRet", dec(item.getValorIcmsStRet(), 2));
    }

    private void creditoSimples(Document doc, Element v, NfeItem item) {
        text(doc, v, "pCredSN", dec(item.getAliqCredSn(), 4));
        text(doc, v, "vCredICMSSN", dec(item.getValorCredIcmsSn(), 2));
    }

    private Element ipi(Document doc, NfeItem item) {
        String cst = aparar(item.getCstIpi());
        if (cst == null) {
            return null;
        }
        Element ipi = element(doc, "IPI");
        String cEnq = aparar(item.getCodEnqIpi());
        text(doc, ipi, "cEnq", cEnq != null ? cEnq : "999");
        switch (cst) {
            case "00", "49", "50", "99" -> {
                Element trib = element(doc, "IPITrib");
                text(doc, trib, "CST", cst);
                text(doc, trib, "vBC", dec(item.getBaseIpi(), 2));
                text(doc, trib, "pIPI", dec(item.getAliqIpi(), 4));
                text(doc, trib, "vIPI", dec(item.getValorIpi(), 2));
                ipi.appendChild(trib);
            }
            default -> {
                Element nt = element(doc, "IPINT");
                text(doc, nt, "CST", cst);
                ipi.appendChild(nt);
            }
        }
        return ipi;
    }

    /** PIS e COFINS têm a mesma estrutura — só muda o prefixo das tags e dos elementos. */
    private Element pisCofins(Document doc, NfeItem item, String tributo, String cstBruto, BigDecimal base,
                              BigDecimal aliq, BigDecimal valor) {
        String cst = aparar(cstBruto);
        if (cst == null) {
            return null;
        }
        Element grupo = element(doc, tributo);
        String variante = switch (cst) {
            case "01", "02" -> "Aliq";
            case "03" -> throw new IllegalStateException(tributo + " por quantidade (CST 03) não é suportado na NFe digitada (item "
                    + item.getItem() + ")");
            case "04", "05", "06", "07", "08", "09" -> "NT";
            default -> "Outr";
        };
        Element v = element(doc, tributo + variante);
        text(doc, v, "CST", cst);
        if (!variante.equals("NT")) {
            text(doc, v, "vBC", dec(base, 2));
            text(doc, v, "p" + tributo, dec(aliq, 4));
            text(doc, v, "v" + tributo, dec(valor, 2));
        }
        grupo.appendChild(v);
        return grupo;
    }

    private Element impostoSeletivo(Document doc, NfeItem item) {
        String cst = aparar(item.getCstIs());
        if (cst == null) {
            return null;
        }
        Element is = element(doc, "IS");
        text(doc, is, "CSTIS", cst);
        text(doc, is, "cClassTribIS", obrigatorio(aparar(item.getCodClassTribIs()),
                "cClassTrib do Imposto Seletivo (item " + item.getItem() + ")"));
        if (naoZero(item.getBaseIs()) || naoZero(item.getAliqIs()) || naoZero(item.getValorIs())) {
            text(doc, is, "vBCIS", dec(item.getBaseIs(), 2));
            text(doc, is, "pIS", dec(item.getAliqIs(), 4));
            opcional(doc, is, "pISEspec", item.getAdRemIs(), 4);
            if (aparar(item.getUnTribIs()) != null) {
                text(doc, is, "uTrib", aparar(item.getUnTribIs()));
                text(doc, is, "qTrib", dec(item.getQuantTribIs(), 4));
            }
            text(doc, is, "vIS", dec(item.getValorIs(), 2));
        }
        return is;
    }

    private Element ibsCbs(Document doc, NfeItem item) {
        String cst = aparar(item.getCstIbsCbs());
        if (cst == null) {
            return null;
        }
        Element ibsCbs = element(doc, "IBSCBS");
        text(doc, ibsCbs, "CST", cst);
        text(doc, ibsCbs, "cClassTrib", obrigatorio(aparar(item.getCodClassTrib()),
                "cClassTrib do IBS/CBS (item " + item.getItem() + ")"));
        // indDoacao (NfeItem.indDoacao) não sai: a tag existiu numa versão anterior da NT
        // 2025.002 e não está no XSD atual, que a rejeita logo depois de cClassTrib
        // Sem base nem alíquota = código "Sem alíquota" (CST 4xx/5xx/8xx): só CST+cClassTrib,
        // sem gIBSCBS — mandar o grupo zerado é rejeitado (ver NfeXmlBuilder.construirIbsCbs)
        boolean temGrupo = naoZero(item.getBaseIbsCbs()) || naoZero(item.getAliqIbsUf()) || naoZero(item.getAliqIbsMun())
                || naoZero(item.getAliqCbs()) || naoZero(item.getValorIbs()) || naoZero(item.getValorCbs());
        if (!temGrupo) {
            return ibsCbs;
        }
        Element g = element(doc, "gIBSCBS");
        text(doc, g, "vBC", dec(item.getBaseIbsCbs(), 2));
        g.appendChild(tributoIbsCbs(doc, "gIBSUF", "pIBSUF", "vIBSUF", item.getAliqIbsUf(), item.getPercDifIbsUf(),
                item.getValorDifIbsUf(), item.getValorDevTribIbsUf(), item.getPercRedAliqIbsUf(), item.getAliqEfetIbsUf(),
                item.getValorIbsUf()));
        g.appendChild(tributoIbsCbs(doc, "gIBSMun", "pIBSMun", "vIBSMun", item.getAliqIbsMun(), item.getPercDifIbsMun(),
                item.getValorDifIbsMun(), item.getValorDevTribIbsMun(), item.getPercRedAliqIbsMun(),
                item.getAliqEfetIbsMun(), item.getValorIbsMun()));
        text(doc, g, "vIBS", dec(item.getValorIbs(), 2));
        g.appendChild(tributoIbsCbs(doc, "gCBS", "pCBS", "vCBS", item.getAliqCbs(), item.getPercDifCbs(),
                item.getValorDifCbs(), item.getValorDevTribCbs(), item.getPercRedAliqCbs(), item.getAliqEfetCbs(),
                item.getValorCbs()));
        ibsCbs.appendChild(g);
        return ibsCbs;
    }

    // gIBSUF/gIBSMun/gCBS: alíquota, gDif, gDevTrib, gRed e valor — os subgrupos só quando informados
    private Element tributoIbsCbs(Document doc, String grupo, String tagAliq, String tagValor, BigDecimal aliq,
                                  BigDecimal percDif, BigDecimal valorDif, BigDecimal valorDevTrib,
                                  BigDecimal percRedAliq, BigDecimal aliqEfet, BigDecimal valor) {
        Element el = element(doc, grupo);
        text(doc, el, tagAliq, dec(aliq, 4));
        if (naoZero(percDif) || naoZero(valorDif)) {
            Element gDif = element(doc, "gDif");
            text(doc, gDif, "pDif", dec(percDif, 4));
            text(doc, gDif, "vDif", dec(valorDif, 2));
            el.appendChild(gDif);
        }
        if (naoZero(valorDevTrib)) {
            Element gDevTrib = element(doc, "gDevTrib");
            text(doc, gDevTrib, "vDevTrib", dec(valorDevTrib, 2));
            el.appendChild(gDevTrib);
        }
        if (naoZero(percRedAliq) || naoZero(aliqEfet)) {
            Element gRed = element(doc, "gRed");
            text(doc, gRed, "pRedAliq", dec(percRedAliq, 4));
            text(doc, gRed, "pAliqEfet", dec(aliqEfet, 4));
            el.appendChild(gRed);
        }
        text(doc, el, tagValor, dec(valor, 2));
        return el;
    }

    // ---- total ----

    private Element total(Document doc, Nfe nfe, List<NfeItem> itens) {
        Element total = element(doc, "total");
        Element t = element(doc, "ICMSTot");
        text(doc, t, "vBC", dec(nfe.getValorBc(), 2));
        text(doc, t, "vICMS", dec(nfe.getValorIcms(), 2));
        text(doc, t, "vICMSDeson", dec(nfe.getValorIcmsDeson(), 2));
        opcional(doc, t, "vFCPUFDest", nfe.getValorFcpUfDest(), 2);
        opcional(doc, t, "vICMSUFDest", nfe.getValorIcmsUfDest(), 2);
        opcional(doc, t, "vICMSUFRemet", nfe.getValorIcmsUfRemet(), 2);
        text(doc, t, "vFCP", dec(nfe.getValorFcp(), 2));
        text(doc, t, "vBCST", dec(nfe.getValorBcSt(), 2));
        text(doc, t, "vST", dec(nfe.getValorSt(), 2));
        text(doc, t, "vFCPST", dec(nfe.getValorFcpSt(), 2));
        text(doc, t, "vFCPSTRet", dec(nfe.getValorFcpStRet(), 2));
        text(doc, t, "vProd", dec(nfe.getValorProd(), 2));
        text(doc, t, "vFrete", dec(nfe.getValorFrete(), 2));
        text(doc, t, "vSeg", dec(nfe.getValorSeg(), 2));
        text(doc, t, "vDesc", dec(nfe.getValorDesc(), 2));
        text(doc, t, "vII", dec(nfe.getValorIi(), 2));
        text(doc, t, "vIPI", dec(nfe.getValorIpi(), 2));
        text(doc, t, "vIPIDevol", dec(nfe.getValorIpiDevol(), 2));
        text(doc, t, "vPIS", dec(nfe.getValorPis(), 2));
        text(doc, t, "vCOFINS", dec(nfe.getValorCofins(), 2));
        text(doc, t, "vOutro", dec(nfe.getValorOutro(), 2));
        text(doc, t, "vNF", dec(nfe.getValorNf(), 2));
        opcional(doc, t, "vTotTrib", nfe.getValorTotTrib(), 2);
        total.appendChild(t);

        if (itens.stream().anyMatch(i -> aparar(i.getCstIs()) != null)) {
            Element isTot = element(doc, "ISTot");
            text(doc, isTot, "vIS", dec(nfe.getValorIs(), 2));
            total.appendChild(isTot);
        }

        if (itens.stream().anyMatch(i -> aparar(i.getCstIbsCbs()) != null)) {
            Element ibsCbsTot = element(doc, "IBSCBSTot");
            text(doc, ibsCbsTot, "vBCIBSCBS", dec(nfe.getValorBcIbsCbs(), 2));
            Element gIbs = element(doc, "gIBS");
            Element gIbsUf = element(doc, "gIBSUF");
            text(doc, gIbsUf, "vDif", dec(nfe.getValorDifIbsUf(), 2));
            text(doc, gIbsUf, "vDevTrib", dec(nfe.getValorDevTribIbsUf(), 2));
            text(doc, gIbsUf, "vIBSUF", dec(nfe.getValorIbsUf(), 2));
            gIbs.appendChild(gIbsUf);
            Element gIbsMun = element(doc, "gIBSMun");
            text(doc, gIbsMun, "vDif", dec(nfe.getValorDifIbsMun(), 2));
            text(doc, gIbsMun, "vDevTrib", dec(nfe.getValorDevTribIbsMun(), 2));
            text(doc, gIbsMun, "vIBSMun", dec(nfe.getValorIbsMun(), 2));
            gIbs.appendChild(gIbsMun);
            text(doc, gIbs, "vIBS", dec(nfe.getValorIbs(), 2));
            // crédito presumido fora de escopo (ver Javadoc de NfeItem) — sempre zero
            text(doc, gIbs, "vCredPres", "0.00");
            text(doc, gIbs, "vCredPresCondSus", "0.00");
            ibsCbsTot.appendChild(gIbs);
            Element gCbs = element(doc, "gCBS");
            text(doc, gCbs, "vDif", dec(nfe.getValorDifCbs(), 2));
            text(doc, gCbs, "vDevTrib", dec(nfe.getValorDevTribCbs(), 2));
            text(doc, gCbs, "vCBS", dec(nfe.getValorCbs(), 2));
            text(doc, gCbs, "vCredPres", "0.00");
            text(doc, gCbs, "vCredPresCondSus", "0.00");
            ibsCbsTot.appendChild(gCbs);
            total.appendChild(ibsCbsTot);
            opcional(doc, total, "vNFTot", nfe.getValorNfTot(), 2);
        }
        return total;
    }

    // ---- transp ----

    private Element transp(Document doc, Nfe nfe) {
        Element transp = element(doc, "transp");
        text(doc, transp, "modFrete", nfe.getModFrete() != null ? nfe.getModFrete() : 9);
        String cnpj = vazioComoNulo(somenteDigitos(nfe.getTranspCnpj()));
        String cpf = vazioComoNulo(somenteDigitos(nfe.getTranspCpf()));
        if (cnpj != null || cpf != null || aparar(nfe.getTranspXNome()) != null) {
            Element transporta = element(doc, "transporta");
            if (cnpj != null) {
                text(doc, transporta, "CNPJ", cnpj);
            } else if (cpf != null) {
                text(doc, transporta, "CPF", cpf);
            }
            text(doc, transporta, "xNome", aparar(nfe.getTranspXNome()));
            text(doc, transporta, "IE", aparar(nfe.getTranspIe()));
            text(doc, transporta, "xEnder", aparar(nfe.getTranspXEnder()));
            text(doc, transporta, "xMun", aparar(nfe.getTranspXMun()));
            text(doc, transporta, "UF", aparar(nfe.getTranspUf()));
            transp.appendChild(transporta);
        }
        if (aparar(nfe.getVeicPlaca()) != null) {
            Element veic = element(doc, "veicTransp");
            text(doc, veic, "placa", aparar(nfe.getVeicPlaca()));
            text(doc, veic, "UF", aparar(nfe.getVeicUf()));
            text(doc, veic, "RNTC", aparar(nfe.getVeicRntc()));
            transp.appendChild(veic);
        }
        for (NfeVolume volume : lista(nfe.getVolumes())) {
            Element vol = element(doc, "vol");
            text(doc, vol, "qVol", volume.getQuantVol());
            text(doc, vol, "esp", aparar(volume.getEspecie()));
            text(doc, vol, "marca", aparar(volume.getMarca()));
            text(doc, vol, "nVol", aparar(volume.getNumeracao()));
            opcional(doc, vol, "pesoL", volume.getPesoLiquido(), 3);
            opcional(doc, vol, "pesoB", volume.getPesoBruto(), 3);
            transp.appendChild(vol);
        }
        return transp;
    }

    // ---- cobr/pag ----

    private Element cobr(Document doc, Nfe nfe) {
        List<NfeDuplicata> duplicatas = lista(nfe.getDuplicatas());
        boolean temFatura = aparar(nfe.getNumFat()) != null || naoZero(nfe.getValorOrigFat()) || naoZero(nfe.getValorLiqFat());
        if (!temFatura && duplicatas.isEmpty()) {
            return null;
        }
        Element cobr = element(doc, "cobr");
        if (temFatura) {
            Element fat = element(doc, "fat");
            text(doc, fat, "nFat", aparar(nfe.getNumFat()));
            text(doc, fat, "vOrig", dec(nfe.getValorOrigFat(), 2));
            text(doc, fat, "vDesc", dec(nfe.getValorDescFat(), 2));
            text(doc, fat, "vLiq", dec(nfe.getValorLiqFat(), 2));
            cobr.appendChild(fat);
        }
        for (NfeDuplicata duplicata : duplicatas) {
            Element dup = element(doc, "dup");
            text(doc, dup, "nDup", aparar(duplicata.getNumDup()));
            text(doc, dup, "dVenc", duplicata.getDataVenc() != null ? duplicata.getDataVenc().toString() : null);
            text(doc, dup, "vDup", dec(duplicata.getValorDup(), 2));
            cobr.appendChild(dup);
        }
        return cobr;
    }

    private Element pag(Document doc, Nfe nfe) {
        Element pag = element(doc, "pag");
        List<NfePagamento> pagamentos = lista(nfe.getPagamentos());
        if (pagamentos.isEmpty()) {
            // sem pagamento informado: "90 - sem pagamento", mesmo padrão do NfeXmlBuilder
            Element detPag = element(doc, "detPag");
            text(doc, detPag, "indPag", 0);
            text(doc, detPag, "tPag", "90");
            text(doc, detPag, "vPag", "0.00");
            pag.appendChild(detPag);
        }
        for (NfePagamento pagamento : pagamentos) {
            Element detPag = element(doc, "detPag");
            text(doc, detPag, "indPag", pagamento.getIndPag());
            text(doc, detPag, "tPag", obrigatorio(aparar(pagamento.getTipoPag()), "meio de pagamento (tPag)"));
            text(doc, detPag, "xPag", aparar(pagamento.getDescricaoPag()));
            text(doc, detPag, "vPag", dec(pagamento.getValorPag(), 2));
            if (pagamento.getTipoIntegracaoCartao() != null) {
                Element card = element(doc, "card");
                text(doc, card, "tpIntegra", pagamento.getTipoIntegracaoCartao());
                text(doc, card, "CNPJ", vazioComoNulo(somenteDigitos(pagamento.getCnpjCredenciadora())));
                text(doc, card, "tBand", aparar(pagamento.getBandeiraCartao()));
                text(doc, card, "cAut", aparar(pagamento.getNumeroAutorizacaoCartao()));
                detPag.appendChild(card);
            }
            pag.appendChild(detPag);
        }
        opcional(doc, pag, "vTroco", nfe.getValorTroco(), 2);
        return pag;
    }

    private Element infAdic(Document doc, Nfe nfe) {
        String infAdFisco = aparar(nfe.getInfAdFisco());
        String infCpl = aparar(nfe.getInfCpl());
        if (infAdFisco == null && infCpl == null) {
            return null;
        }
        Element infAdic = element(doc, "infAdic");
        text(doc, infAdic, "infAdFisco", infAdFisco);
        text(doc, infAdic, "infCpl", infCpl);
        return infAdic;
    }

    // ---- utilitários ----

    private static <T> List<T> lista(List<T> lista) {
        return lista == null ? Collections.emptyList() : lista;
    }

    private static boolean naoZero(BigDecimal valor) {
        return valor != null && valor.signum() != 0;
    }

    private static void opcional(Document doc, Element parent, String tag, BigDecimal valor, int casas) {
        if (naoZero(valor)) {
            text(doc, parent, tag, dec(valor, casas));
        }
    }

    private static <T> T obrigatorio(T valor, String descricao) {
        if (valor == null) {
            throw new IllegalStateException("Campo obrigatório não preenchido: " + descricao);
        }
        return valor;
    }

    // o schema rejeita texto com espaço à frente/atrás; vazio vira null pra tag sumir
    private static String aparar(String texto) {
        if (texto == null) {
            return null;
        }
        String aparado = texto.trim();
        return aparado.isEmpty() ? null : aparado;
    }

    private static String vazioComoNulo(String texto) {
        return texto == null || texto.isEmpty() ? null : texto;
    }

    private static String semGtin(String codigo) {
        String c = aparar(codigo);
        return c != null ? c : "SEM GTIN";
    }

    private static Integer origem(NfeItem item) {
        return Objects.requireNonNullElse(item.getOrigemIcms(), 0);
    }

    private static Integer modBc(NfeItem item) {
        return Objects.requireNonNullElse(item.getModBcIcms(), 3);
    }

    // TDateTimeUTC não aceita fração de segundo
    private static String dataHora(OffsetDateTime valor) {
        return DATA_HORA.format(valor.truncatedTo(ChronoUnit.SECONDS));
    }
}
