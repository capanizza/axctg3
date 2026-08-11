package br.com.axialsoftware.axctg3.service.fiscal;

import br.com.axialsoftware.axctg3.entity.fiscal.Nfe;
import br.com.axialsoftware.axctg3.entity.fiscal.NfeDuplicata;
import br.com.axialsoftware.axctg3.entity.fiscal.NfeItem;
import br.com.axialsoftware.axctg3.entity.fiscal.NfePagamento;
import br.com.axialsoftware.axctg3.entity.fiscal.NfeVolume;

import io.jmix.core.DataManager;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Parser DOM do XML autorizado de NFe (leiaute 4.00, com ou sem o wrapper {@code nfeProc} de
 * protocolo) pros campos já modelados em {@link Nfe}/{@link NfeItem}/{@link NfeVolume}/
 * {@link NfeDuplicata}/{@link NfePagamento} — mapeamento 1:1 tag→campo, sem side-effects em
 * Parceiro/NotaSaida/Receber (decidido na conversa que introduziu este arquivo, 2026-08-11; o
 * sistema legado AxFiscal em Delphi faz esses side-effects, mas contra um modelo de dados
 * diferente — ver {@code F_LeituraXML.pas} fora deste repo).
 *
 * <p>Cada campo do XML usado aqui está documentado com a tag original nos comentários de
 * {@link Nfe}/{@link NfeItem}/{@link NfeVolume}/{@link NfeDuplicata}/{@link NfePagamento} — este
 * parser segue esses comentários como fonte da verdade em vez de reconsultar o schema oficial.
 *
 * <p>Usa {@code DocumentBuilder} puro do JDK (sem namespace awareness, já que o XML da NFe não
 * usa prefixo — só um namespace default — então buscar por nome de tag sem qualificação
 * funciona). Não precisa de JAXB nem dom4j: um XML de NFe por vez, poucas dezenas de KB, não
 * justifica dependência nova no {@code build.gradle}.
 *
 * <p><b>Risco de ambiguidade de tag</b>: várias tags se repetem em pontos diferentes do
 * documento (ex.: {@code vBC} aparece em ICMS, PIS, COFINS, IBSCBS...; {@code pDif}/{@code vDif}
 * se repetem idênticos em {@code gIBSUF}/{@code gIBSMun}/{@code gCBS}). Por isso a extração
 * nunca faz busca profunda a partir de um elemento amplo ({@code det}, {@code imposto},
 * {@code IBSCBSTot}) — sempre desce filho-direto por filho-direto ({@link #child}) até um
 * escopo estreito o bastante pra tag não se repetir, só então lê o texto.
 */
@Component
public class NfeXmlParser {

    private final DataManager dataManager;

    public NfeXmlParser(DataManager dataManager) {
        this.dataManager = dataManager;
    }

    public Nfe parse(byte[] xmlContent) {
        Document doc = parseDocument(xmlContent);

        Element infNFe = firstByTag(doc, "infNFe");
        if (infNFe == null) {
            throw new IllegalArgumentException("XML não é uma NFe — elemento infNFe não encontrado");
        }

        // dataManager.create(), não "new Nfe()": é o create() que atribui o @JmixGeneratedValue
        // id — sem ele, hashCode()/equals() da entidade (baseados no id gerado) quebram assim que
        // a instância entra num Set, como o SaveContext.saving() faz internamente.
        Nfe nfe = dataManager.create(Nfe.class);
        nfe.setChave(chaveFromId(infNFe.getAttribute("Id")));

        parseIde(nfe, child(infNFe, "ide"));
        parseEmit(nfe, child(infNFe, "emit"));
        parseDest(nfe, child(infNFe, "dest"));
        parseTotal(nfe, child(infNFe, "total"));

        Element transp = child(infNFe, "transp");
        parseTransp(nfe, transp);
        nfe.setVolumes(parseVolumes(nfe, transp));

        Element cobr = child(infNFe, "cobr");
        parseCobr(nfe, child(cobr, "fat"));
        nfe.setDuplicatas(parseDuplicatas(nfe, cobr));

        Element pag = child(infNFe, "pag");
        nfe.setValorTroco(decimal(pag, "vTroco"));
        nfe.setPagamentos(parsePagamentos(nfe, pag));

        parseInfAdic(nfe, child(infNFe, "infAdic"));
        nfe.setItens(parseItens(nfe, infNFe));

        Element protNFe = firstByTag(doc, "protNFe");
        if (protNFe != null) {
            parseProtNFe(nfe, child(protNFe, "infProt"));
            String chNFe = text(child(protNFe, "infProt"), "chNFe");
            if (chNFe != null && !chNFe.isBlank()) {
                nfe.setChave(chNFe);
            }
        }

        return nfe;
    }

    // ---- grupos do cabeçalho ----

    private void parseIde(Nfe nfe, Element ide) {
        nfe.setCodUf(integer(ide, "cUF"));
        nfe.setCodNf(integer(ide, "cNF"));
        nfe.setNatOp(text(ide, "natOp"));
        Integer mod = integer(ide, "mod");
        if (mod != null) {
            nfe.setMod(mod);
        }
        nfe.setSerie(integer(ide, "serie"));
        nfe.setNumeroNf(integer(ide, "nNF"));
        nfe.setDhEmi(dateTime(ide, "dhEmi"));
        nfe.setDhSaiEnt(dateTime(ide, "dhSaiEnt"));
        nfe.setTpNf(integer(ide, "tpNF"));
        nfe.setIdDest(integer(ide, "idDest"));
        nfe.setCodMunFg(integer(ide, "cMunFG"));
        nfe.setTpImp(integer(ide, "tpImp"));
        nfe.setTpEmis(integer(ide, "tpEmis"));
        nfe.setCodDv(integer(ide, "cDV"));
        nfe.setTpAmb(integer(ide, "tpAmb"));
        nfe.setFinNfe(integer(ide, "finNFe"));
        nfe.setIndFinal(integer(ide, "indFinal"));
        nfe.setIndPres(integer(ide, "indPres"));
        nfe.setProcEmi(integer(ide, "procEmi"));
        nfe.setVerProc(text(ide, "verProc"));
    }

    private void parseEmit(Nfe nfe, Element emit) {
        nfe.setEmitCnpj(text(emit, "CNPJ"));
        nfe.setEmitXNome(text(emit, "xNome"));
        nfe.setEmitXFant(text(emit, "xFant"));
        nfe.setEmitIe(text(emit, "IE"));
        nfe.setEmitCrt(integer(emit, "CRT"));
        Element ender = child(emit, "enderEmit");
        nfe.setEmitXLgr(text(ender, "xLgr"));
        nfe.setEmitNro(text(ender, "nro"));
        nfe.setEmitXCpl(text(ender, "xCpl"));
        nfe.setEmitXBairro(text(ender, "xBairro"));
        nfe.setEmitCMun(integer(ender, "cMun"));
        nfe.setEmitXMun(text(ender, "xMun"));
        nfe.setEmitUf(text(ender, "UF"));
        nfe.setEmitCep(text(ender, "CEP"));
        nfe.setEmitFone(text(ender, "fone"));
    }

    private void parseDest(Nfe nfe, Element dest) {
        nfe.setDestCnpj(text(dest, "CNPJ"));
        nfe.setDestCpf(text(dest, "CPF"));
        nfe.setDestXNome(text(dest, "xNome"));
        nfe.setDestIndIe(integer(dest, "indIEDest"));
        nfe.setDestIe(text(dest, "IE"));
        nfe.setDestEmail(text(dest, "email"));
        Element ender = child(dest, "enderDest");
        nfe.setDestXLgr(text(ender, "xLgr"));
        nfe.setDestNro(text(ender, "nro"));
        nfe.setDestXCpl(text(ender, "xCpl"));
        nfe.setDestXBairro(text(ender, "xBairro"));
        nfe.setDestCMun(integer(ender, "cMun"));
        nfe.setDestXMun(text(ender, "xMun"));
        nfe.setDestUf(text(ender, "UF"));
        nfe.setDestCep(text(ender, "CEP"));
        nfe.setDestFone(text(ender, "fone"));
    }

    private void parseTotal(Nfe nfe, Element total) {
        Element icmsTot = child(total, "ICMSTot");
        nfe.setValorBc(decimal(icmsTot, "vBC"));
        nfe.setValorIcms(decimal(icmsTot, "vICMS"));
        nfe.setValorIcmsDeson(decimal(icmsTot, "vICMSDeson"));
        nfe.setValorFcpUfDest(decimal(icmsTot, "vFCPUFDest"));
        nfe.setValorIcmsUfDest(decimal(icmsTot, "vICMSUFDest"));
        nfe.setValorIcmsUfRemet(decimal(icmsTot, "vICMSUFRemet"));
        nfe.setValorFcp(decimal(icmsTot, "vFCP"));
        nfe.setValorBcSt(decimal(icmsTot, "vBCST"));
        nfe.setValorSt(decimal(icmsTot, "vST"));
        nfe.setValorFcpSt(decimal(icmsTot, "vFCPST"));
        nfe.setValorFcpStRet(decimal(icmsTot, "vFCPSTRet"));
        nfe.setValorProd(decimal(icmsTot, "vProd"));
        nfe.setValorFrete(decimal(icmsTot, "vFrete"));
        nfe.setValorSeg(decimal(icmsTot, "vSeg"));
        nfe.setValorDesc(decimal(icmsTot, "vDesc"));
        nfe.setValorIi(decimal(icmsTot, "vII"));
        nfe.setValorIpi(decimal(icmsTot, "vIPI"));
        nfe.setValorIpiDevol(decimal(icmsTot, "vIPIDevol"));
        nfe.setValorPis(decimal(icmsTot, "vPIS"));
        nfe.setValorCofins(decimal(icmsTot, "vCOFINS"));
        nfe.setValorOutro(decimal(icmsTot, "vOutro"));
        nfe.setValorNf(decimal(icmsTot, "vNF"));
        nfe.setValorTotTrib(decimal(icmsTot, "vTotTrib"));

        // Grupo W03 (IBSCBSTot/ISTot) — Reforma Tributária. Estrutura real confirmada contra XMLs
        // de produção em 2026-08-11 (a suposição inicial, sem XML real em mãos, estava errada em
        // dois pontos): gIBSUF/gIBSMun/vIBS ficam dentro de um wrapper gIBS (não direto em
        // IBSCBSTot); e vNFTot é IRMÃO de IBSCBSTot dentro de total, não filho dele. vDif/
        // vDevTrib, esses sim, ficam direto em gIBSUF/gIBSMun/gCBS (sem gDif/gDevTrib
        // intermediário) — isso já estava certo, é mais raso que o nível de item mesmo.
        Element ibsCbsTot = child(total, "IBSCBSTot");
        if (ibsCbsTot != null) {
            nfe.setValorBcIbsCbs(decimal(ibsCbsTot, "vBCIBSCBS"));
            Element gIbs = child(ibsCbsTot, "gIBS");
            Element gIbsUf = child(gIbs, "gIBSUF");
            nfe.setValorDifIbsUf(decimal(gIbsUf, "vDif"));
            nfe.setValorDevTribIbsUf(decimal(gIbsUf, "vDevTrib"));
            nfe.setValorIbsUf(decimal(gIbsUf, "vIBSUF"));
            Element gIbsMun = child(gIbs, "gIBSMun");
            nfe.setValorDifIbsMun(decimal(gIbsMun, "vDif"));
            nfe.setValorDevTribIbsMun(decimal(gIbsMun, "vDevTrib"));
            nfe.setValorIbsMun(decimal(gIbsMun, "vIBSMun"));
            nfe.setValorIbs(decimal(gIbs, "vIBS"));
            Element gCbs = child(ibsCbsTot, "gCBS");
            nfe.setValorDifCbs(decimal(gCbs, "vDif"));
            nfe.setValorDevTribCbs(decimal(gCbs, "vDevTrib"));
            nfe.setValorCbs(decimal(gCbs, "vCBS"));
        }
        nfe.setValorNfTot(decimal(total, "vNFTot"));
        Element isTot = child(total, "ISTot");
        if (isTot != null) {
            nfe.setValorIs(decimal(isTot, "vIS"));
        }
    }

    private void parseTransp(Nfe nfe, Element transp) {
        nfe.setModFrete(integer(transp, "modFrete"));
        Element transporta = child(transp, "transporta");
        nfe.setTranspCnpj(text(transporta, "CNPJ"));
        nfe.setTranspCpf(text(transporta, "CPF"));
        nfe.setTranspXNome(text(transporta, "xNome"));
        nfe.setTranspIe(text(transporta, "IE"));
        nfe.setTranspXEnder(text(transporta, "xEnder"));
        nfe.setTranspXMun(text(transporta, "xMun"));
        nfe.setTranspUf(text(transporta, "UF"));
        Element veicTransp = child(transp, "veicTransp");
        nfe.setVeicPlaca(text(veicTransp, "placa"));
        nfe.setVeicUf(text(veicTransp, "UF"));
        nfe.setVeicRntc(text(veicTransp, "RNTC"));
    }

    private List<NfeVolume> parseVolumes(Nfe nfe, Element transp) {
        List<NfeVolume> volumes = new ArrayList<>();
        if (transp == null) {
            return volumes;
        }
        for (Element vol : children(transp, "vol")) {
            NfeVolume volume = dataManager.create(NfeVolume.class);
            volume.setNfe(nfe);
            volume.setQuantVol(integer(vol, "qVol"));
            volume.setEspecie(text(vol, "esp"));
            volume.setMarca(text(vol, "marca"));
            volume.setNumeracao(text(vol, "nVol"));
            volume.setPesoLiquido(decimal(vol, "pesoL"));
            volume.setPesoBruto(decimal(vol, "pesoB"));
            volumes.add(volume);
        }
        return volumes;
    }

    private void parseCobr(Nfe nfe, Element fat) {
        if (fat == null) {
            return;
        }
        nfe.setNumFat(text(fat, "nFat"));
        nfe.setValorOrigFat(decimal(fat, "vOrig"));
        nfe.setValorDescFat(decimal(fat, "vDesc"));
        nfe.setValorLiqFat(decimal(fat, "vLiq"));
    }

    private List<NfeDuplicata> parseDuplicatas(Nfe nfe, Element cobr) {
        List<NfeDuplicata> duplicatas = new ArrayList<>();
        if (cobr == null) {
            return duplicatas;
        }
        for (Element dup : children(cobr, "dup")) {
            NfeDuplicata duplicata = dataManager.create(NfeDuplicata.class);
            duplicata.setNfe(nfe);
            duplicata.setNumDup(text(dup, "nDup"));
            duplicata.setDataVenc(date(dup, "dVenc"));
            duplicata.setValorDup(decimal(dup, "vDup"));
            duplicatas.add(duplicata);
        }
        return duplicatas;
    }

    private List<NfePagamento> parsePagamentos(Nfe nfe, Element pag) {
        List<NfePagamento> pagamentos = new ArrayList<>();
        if (pag == null) {
            return pagamentos;
        }
        for (Element detPag : children(pag, "detPag")) {
            NfePagamento pagamento = dataManager.create(NfePagamento.class);
            pagamento.setNfe(nfe);
            pagamento.setIndPag(integer(detPag, "indPag"));
            pagamento.setTipoPag(text(detPag, "tPag"));
            pagamento.setDescricaoPag(text(detPag, "xPag"));
            pagamento.setValorPag(decimal(detPag, "vPag"));
            Element card = child(detPag, "card");
            if (card != null) {
                pagamento.setTipoIntegracaoCartao(integer(card, "tpIntegra"));
                pagamento.setCnpjCredenciadora(text(card, "CNPJ"));
                pagamento.setBandeiraCartao(text(card, "tBand"));
                pagamento.setNumeroAutorizacaoCartao(text(card, "cAut"));
            }
            pagamentos.add(pagamento);
        }
        return pagamentos;
    }

    private void parseInfAdic(Nfe nfe, Element infAdic) {
        nfe.setInfAdFisco(text(infAdic, "infAdFisco"));
        nfe.setInfCpl(infAdic == null ? null : lobText(infAdic, "infCpl"));
    }

    private void parseProtNFe(Nfe nfe, Element infProt) {
        nfe.setProtTpAmb(integer(infProt, "tpAmb"));
        nfe.setProtVerAplic(text(infProt, "verAplic"));
        nfe.setProtDhRecbto(dateTime(infProt, "dhRecbto"));
        nfe.setProtNProt(text(infProt, "nProt"));
        nfe.setProtDigVal(text(infProt, "digVal"));
        nfe.setProtCStat(integer(infProt, "cStat"));
        nfe.setProtXMotivo(text(infProt, "xMotivo"));
    }

    // ---- itens (det[]) ----

    private List<NfeItem> parseItens(Nfe nfe, Element infNFe) {
        List<NfeItem> itens = new ArrayList<>();
        for (Element det : children(infNFe, "det")) {
            NfeItem item = dataManager.create(NfeItem.class);
            item.setNfe(nfe);
            item.setItem(integerAttr(det, "nItem"));
            item.setInfoAdicionalProduto(text(det, "infAdProd"));

            parseProd(item, child(det, "prod"));

            Element imposto = child(det, "imposto");
            parseImpostoIcms(item, child(imposto, "ICMS"));
            parseImpostoIpi(item, child(imposto, "IPI"));
            parseImpostoPis(item, child(imposto, "PIS"));
            parseImpostoCofins(item, child(imposto, "COFINS"));
            item.setValorTotTributos(decimal(imposto, "vTotTrib"));
            parseImpostoIs(item, child(imposto, "IS"));
            parseImpostoIbsCbs(item, child(imposto, "IBSCBS"));

            itens.add(item);
        }
        return itens;
    }

    private void parseProd(NfeItem item, Element prod) {
        item.setCodProd(text(prod, "cProd"));
        item.setCodEan(text(prod, "cEAN"));
        item.setDescProd(text(prod, "xProd"));
        item.setNcm(text(prod, "NCM"));
        item.setCest(text(prod, "CEST"));
        item.setCfop(integer(prod, "CFOP"));
        item.setUnCom(text(prod, "uCom"));
        item.setQuantCom(decimal(prod, "qCom"));
        item.setValorUnCom(decimal(prod, "vUnCom"));
        item.setValorProd(decimal(prod, "vProd"));
        item.setCodEanTrib(text(prod, "cEANTrib"));
        item.setUnTrib(text(prod, "uTrib"));
        item.setQuantTrib(decimal(prod, "qTrib"));
        item.setValorUnTrib(decimal(prod, "vUnTrib"));
        item.setValorFrete(decimal(prod, "vFrete"));
        item.setValorSeg(decimal(prod, "vSeg"));
        item.setValorDesc(decimal(prod, "vDesc"));
        item.setValorOutro(decimal(prod, "vOutro"));
        item.setIndTot(integer(prod, "indTot"));
    }

    private void parseImpostoIcms(NfeItem item, Element icms) {
        Element variante = firstChildElement(icms);
        if (variante == null) {
            return;
        }
        if (variante.getTagName().startsWith("ICMSSN")) {
            item.setCsosnIcms(text(variante, "CSOSN"));
        } else {
            item.setCstIcms(text(variante, "CST"));
        }
        item.setOrigemIcms(integer(variante, "orig"));
        item.setModBcIcms(integer(variante, "modBC"));
        item.setBaseIcms(decimal(variante, "vBC"));
        item.setPercReducaoBcIcms(decimal(variante, "pRedBC"));
        item.setAliqIcms(decimal(variante, "pICMS"));
        item.setValorIcms(decimal(variante, "vICMS"));
        item.setModBcIcmsSt(integer(variante, "modBCST"));
        item.setPercMvaIcmsSt(decimal(variante, "pMVAST"));
        item.setPercReducaoBcIcmsSt(decimal(variante, "pRedBCST"));
        item.setBaseIcmsSt(decimal(variante, "vBCST"));
        item.setAliqIcmsSt(decimal(variante, "pICMSST"));
        item.setValorIcmsSt(decimal(variante, "vICMSST"));
        item.setAliqFcp(decimal(variante, "pFCP"));
        item.setValorFcp(decimal(variante, "vFCP"));
    }

    private void parseImpostoIpi(NfeItem item, Element ipi) {
        if (ipi == null) {
            return;
        }
        item.setCodEnqIpi(text(ipi, "cEnq"));
        Element trib = child(ipi, "IPITrib");
        Element variante = trib != null ? trib : child(ipi, "IPINT");
        if (variante == null) {
            return;
        }
        item.setCstIpi(text(variante, "CST"));
        item.setBaseIpi(decimal(variante, "vBC"));
        item.setAliqIpi(decimal(variante, "pIPI"));
        item.setValorIpi(decimal(variante, "vIPI"));
    }

    private void parseImpostoPis(NfeItem item, Element pis) {
        Element variante = firstChildElement(pis);
        if (variante == null) {
            return;
        }
        item.setCstPis(text(variante, "CST"));
        item.setBasePis(decimal(variante, "vBC"));
        item.setAliqPis(decimal(variante, "pPIS"));
        item.setValorPis(decimal(variante, "vPIS"));
    }

    private void parseImpostoCofins(NfeItem item, Element cofins) {
        Element variante = firstChildElement(cofins);
        if (variante == null) {
            return;
        }
        item.setCstCofins(text(variante, "CST"));
        item.setBaseCofins(decimal(variante, "vBC"));
        item.setAliqCofins(decimal(variante, "pCOFINS"));
        item.setValorCofins(decimal(variante, "vCOFINS"));
    }

    private void parseImpostoIs(NfeItem item, Element is) {
        if (is == null) {
            return;
        }
        item.setCstIs(text(is, "CSTIS"));
        item.setCodClassTribIs(text(is, "cClassTribIS"));
        item.setBaseIs(decimal(is, "vBCIS"));
        item.setAliqIs(decimal(is, "pIS"));
        item.setAdRemIs(decimal(is, "adRemIS"));
        item.setUnTribIs(text(is, "uTrib"));
        item.setQuantTribIs(decimal(is, "qTrib"));
        item.setValorIs(decimal(is, "vIS"));
    }

    // Grupo UB (IBSCBS) — Reforma Tributária. Mesma ressalva do total: pDif/vDif, pDevTrib/
    // vDevTrib e pRedAliq/pAliqEfet se repetem idênticos em gIBSUF/gIBSMun/gCBS, por isso cada
    // sub-bloco é isolado (child()) antes de ler qualquer tag de dentro dele.
    //
    // CST/cClassTrib/indDoacao ficam direto em IBSCBS, mas vBC/gIBSUF/gIBSMun/vIBS/gCBS moram
    // dentro de um wrapper gIBSCBS — confirmado contra XML de produção em 2026-08-11 (a primeira
    // versão deste parser não tinha esse wrapper, tratava tudo como filho direto de IBSCBS).
    private void parseImpostoIbsCbs(NfeItem item, Element ibsCbs) {
        if (ibsCbs == null) {
            return;
        }
        item.setCstIbsCbs(text(ibsCbs, "CST"));
        item.setCodClassTrib(text(ibsCbs, "cClassTrib"));
        item.setIndDoacao(integer(ibsCbs, "indDoacao"));

        Element gIbsCbs = child(ibsCbs, "gIBSCBS");
        item.setBaseIbsCbs(decimal(gIbsCbs, "vBC"));

        Element gIbsUf = child(gIbsCbs, "gIBSUF");
        item.setAliqIbsUf(decimal(gIbsUf, "pIBSUF"));
        Element gDifUf = child(gIbsUf, "gDif");
        item.setPercDifIbsUf(decimal(gDifUf, "pDif"));
        item.setValorDifIbsUf(decimal(gDifUf, "vDif"));
        Element gDevTribUf = child(gIbsUf, "gDevTrib");
        item.setPercDevTribIbsUf(decimal(gDevTribUf, "pDevTrib"));
        item.setValorDevTribIbsUf(decimal(gDevTribUf, "vDevTrib"));
        Element gRedUf = child(gIbsUf, "gRed");
        item.setPercRedAliqIbsUf(decimal(gRedUf, "pRedAliq"));
        item.setAliqEfetIbsUf(decimal(gRedUf, "pAliqEfet"));
        item.setValorIbsUf(decimal(gIbsUf, "vIBSUF"));

        Element gIbsMun = child(gIbsCbs, "gIBSMun");
        item.setAliqIbsMun(decimal(gIbsMun, "pIBSMun"));
        Element gDifMun = child(gIbsMun, "gDif");
        item.setPercDifIbsMun(decimal(gDifMun, "pDif"));
        item.setValorDifIbsMun(decimal(gDifMun, "vDif"));
        Element gDevTribMun = child(gIbsMun, "gDevTrib");
        item.setPercDevTribIbsMun(decimal(gDevTribMun, "pDevTrib"));
        item.setValorDevTribIbsMun(decimal(gDevTribMun, "vDevTrib"));
        Element gRedMun = child(gIbsMun, "gRed");
        item.setPercRedAliqIbsMun(decimal(gRedMun, "pRedAliq"));
        item.setAliqEfetIbsMun(decimal(gRedMun, "pAliqEfet"));
        item.setValorIbsMun(decimal(gIbsMun, "vIBSMun"));

        item.setValorIbs(decimal(gIbsCbs, "vIBS"));

        Element gCbs = child(gIbsCbs, "gCBS");
        item.setAliqCbs(decimal(gCbs, "pCBS"));
        Element gDifCbs = child(gCbs, "gDif");
        item.setPercDifCbs(decimal(gDifCbs, "pDif"));
        item.setValorDifCbs(decimal(gDifCbs, "vDif"));
        Element gDevTribCbs = child(gCbs, "gDevTrib");
        item.setPercDevTribCbs(decimal(gDevTribCbs, "pDevTrib"));
        item.setValorDevTribCbs(decimal(gDevTribCbs, "vDevTrib"));
        Element gRedCbs = child(gCbs, "gRed");
        item.setPercRedAliqCbs(decimal(gRedCbs, "pRedAliq"));
        item.setAliqEfetCbs(decimal(gRedCbs, "pAliqEfet"));
        item.setValorCbs(decimal(gCbs, "vCBS"));
    }

    // ---- chave de acesso ----

    // infNFe/@Id vem como "NFe" + 44 dígitos (ex.: "NFe35240512345678000199550010000000011234567890")
    private String chaveFromId(String id) {
        if (id == null) {
            return null;
        }
        String semPrefixo = id.startsWith("NFe") ? id.substring(3) : id;
        return semPrefixo.length() > 44 ? semPrefixo.substring(0, 44) : semPrefixo;
    }

    // ---- parsing do documento ----

    private Document parseDocument(byte[] xmlContent) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            return builder.parse(new ByteArrayInputStream(xmlContent));
        } catch (ParserConfigurationException | SAXException | IOException e) {
            throw new IllegalArgumentException("Arquivo não é um XML válido: " + e.getMessage(), e);
        }
    }

    // ---- navegação DOM — só filho direto, nunca busca profunda a partir de um escopo amplo ----

    private static Element firstByTag(Document doc, String tag) {
        NodeList list = doc.getElementsByTagName(tag);
        return list.getLength() > 0 ? (Element) list.item(0) : null;
    }

    private static Element child(Element parent, String tag) {
        if (parent == null) {
            return null;
        }
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE && tag.equals(node.getNodeName())) {
                return (Element) node;
            }
        }
        return null;
    }

    private static List<Element> children(Element parent, String tag) {
        List<Element> result = new ArrayList<>();
        if (parent == null) {
            return result;
        }
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE && tag.equals(node.getNodeName())) {
                result.add((Element) node);
            }
        }
        return result;
    }

    // primeiro filho elemento, seja qual for o nome — usado nos grupos "escolha um-de-N"
    // (variante do ICMS por CST/CSOSN, variante do PIS/COFINS por Aliq/Qtde/NT/Outr)
    private static Element firstChildElement(Element parent) {
        if (parent == null) {
            return null;
        }
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                return (Element) node;
            }
        }
        return null;
    }

    // ---- leitura de valor ----

    private static String text(Element parent, String tag) {
        Element el = child(parent, tag);
        if (el == null) {
            return null;
        }
        String value = el.getTextContent();
        if (value == null) {
            return null;
        }
        value = value.trim();
        return value.isEmpty() ? null : value;
    }

    // sem limite prático de tamanho (infCpl pode ser texto longo) — mesma leitura de text(),
    // só nomeado à parte pra deixar claro no call site que o destino é uma coluna @Lob
    private static String lobText(Element parent, String tag) {
        return text(parent, tag);
    }

    private static Integer integer(Element parent, String tag) {
        String value = text(parent, tag);
        if (value == null) {
            return null;
        }
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer integerAttr(Element el, String attr) {
        if (el == null || !el.hasAttribute(attr)) {
            return null;
        }
        try {
            return Integer.valueOf(el.getAttribute(attr).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ZERO, não null, quando a tag falta: todo campo BigDecimal de Nfe/NfeItem/... já nasce
    // BigDecimal.ZERO por inicializador de campo (convenção do projeto p/ campo monetário) — um
    // grupo opcional ausente (ex.: ST num item sem substituição tributária) deve preservar esse
    // padrão, não sobrescrever com null.
    private static BigDecimal decimal(Element parent, String tag) {
        String value = text(parent, tag);
        if (value == null) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    // dhEmi/dhSaiEnt/dhRecbto vêm no formato completo do leiaute 4.00, ex. "2024-05-20T10:15:30-03:00"
    private static OffsetDateTime dateTime(Element parent, String tag) {
        String value = text(parent, tag);
        if (value == null) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    // dVenc vem como data pura "2024-06-20"
    private static LocalDate date(Element parent, String tag) {
        String value = text(parent, tag);
        if (value == null) {
            return null;
        }
        try {
            return LocalDate.parse(value.length() > 10 ? value.substring(0, 10) : value);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
