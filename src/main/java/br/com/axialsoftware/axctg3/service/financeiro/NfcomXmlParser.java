package br.com.axialsoftware.axctg3.service.financeiro;

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
import java.time.format.DateTimeParseException;

/**
 * Parser DOM do XML de NFCom (modelo 62) — nota autorizada ({@code nfcomProc}) ou evento de
 * cancelamento ({@code procEventoNFCom}, tpEvento 110111). Mapeamento conferido campo a campo
 * contra 25 XMLs reais da Radio (2026-06) e contra a lógica de referência do sistema
 * intermediário Axial ({@code ReceberGridController.importarXmls/lerXMLCliente}, fora deste
 * repo) — ver [[nfcom-import-titulo-receber]].
 *
 * <p>Ao contrário do {@code NfeXmlParser}, este parser TEM side-effect intencional em
 * {@code Parceiro} (cria o cliente quando o CNPJ/CPF do destinatário não existe ainda) — decisão
 * explícita do usuário pra este fluxo, replicando o comportamento do Axial.
 *
 * <p>Sem namespace awareness (mesmo motivo do {@code NfeXmlParser}: XML da NFCom só usa um
 * namespace default, sem prefixo, então buscar por nome de tag sem qualificação funciona).
 */
@Component
public class NfcomXmlParser {

    public NfcomXmlLido parse(byte[] xmlContent) {
        Document doc = parseDocument(xmlContent);

        // Só o evento de cancelamento tem <infEvento> — <eventoNFCom>/<infEvento> aparece antes
        // de <retEventoNFCom>/<infEvento> no documento, mas os dois têm chNFCom/tpEvento, então
        // pegar o primeiro por getElementsByTagName funciona de qualquer forma.
        Element infEvento = firstByTag(doc, "infEvento");
        if (infEvento != null) {
            return parseCancelamento(infEvento);
        }

        Element ide = firstByTag(doc, "ide");
        if (ide == null) {
            throw new IllegalArgumentException(
                    "XML não é uma NFCom nem um evento de cancelamento — elemento ide/infEvento não encontrado");
        }
        return parseNota(doc, ide);
    }

    private NfcomXmlLido.Cancelamento parseCancelamento(Element infEvento) {
        String tpEvento = text(infEvento, "tpEvento");
        if (!"110111".equals(tpEvento)) {
            throw new IllegalArgumentException(
                    "Evento de NFCom não suportado (tpEvento=" + tpEvento + ") — só cancelamento (110111)");
        }
        String chave = text(infEvento, "chNFCom");
        return new NfcomXmlLido.Cancelamento(numeroDaChave(chave));
    }

    private NfcomXmlLido.Nota parseNota(Document doc, Element ide) {
        String numero = numeroFormatado(text(ide, "nNF"));
        LocalDate dataEmissao = date(ide, "dhEmi");

        BigDecimal valor = decimal(firstByTag(doc, "total"), "vNF");
        LocalDate dataVencimento = date(firstByTag(doc, "gFat"), "dVencFat");
        NfcomXmlLido.Nota.Destinatario destinatario = parseDestinatario(firstByTag(doc, "dest"));

        return new NfcomXmlLido.Nota(numero, dataEmissao, dataVencimento, valor, destinatario);
    }

    private NfcomXmlLido.Nota.Destinatario parseDestinatario(Element dest) {
        String cnpj = text(dest, "CNPJ");
        String cnpjCpf = cnpj != null ? cnpj : text(dest, "CPF");
        String nome = text(dest, "xNome");
        // indIEDest=9 → não contribuinte, IE não se aplica (mesma regra do Axial).
        String inscricaoEstadual = "9".equals(text(dest, "indIEDest")) ? null : text(dest, "IE");

        Element ender = child(dest, "enderDest");
        return new NfcomXmlLido.Nota.Destinatario(
                cnpjCpf, nome, inscricaoEstadual,
                text(ender, "xLgr"), text(ender, "nro"), text(ender, "xBairro"),
                integer(ender, "cMun"), text(ender, "CEP"), text(ender, "UF"), text(ender, "fone"));
    }

    // 259 → "000259", igual ao Formatacao.IntStr(num, 6) do Axial.
    private static String numeroFormatado(String nNF) {
        if (nNF == null) {
            return null;
        }
        try {
            return String.format("%06d", Integer.parseInt(nNF));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // Chave de acesso (44 dígitos): UF(2)+AAMM(4)+CNPJ(14)+mod(2)+série(3)+nNF(9)+tpEmis(1)+
    // cNF(8)+cDV(1) — nNF ocupa os índices 25-33; os últimos 6 desses 9 dígitos (índices 28-33)
    // já são o número zero-preenchido a 6 dígitos, igual ao Axial extrai da posição equivalente
    // no nome do arquivo (`nomeXml.substring(28, 34)`, já que nome do arquivo = chave + ".xml").
    private static String numeroDaChave(String chave) {
        if (chave == null || chave.length() < 34) {
            return null;
        }
        return chave.substring(28, 34);
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

    // dhEmi vem completo ("2026-06-05T03:00:00+00:00"), dVencFat vem como data pura ("2026-06-15").
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
