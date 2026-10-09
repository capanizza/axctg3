package br.com.axialsoftware.axctg3.service.fiscal;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.regex.Pattern;

/**
 * Utilitários de escrita do XML da NFe compartilhados por {@link NfeXmlBuilder} (monta a
 * partir de NotaSaida) e {@link NfeXmlSerializer} (monta a partir de uma Nfe digitada) —
 * namespace, criação de elemento de texto, formatação decimal.
 */
final class NfeXml {

    static final String NS_NFE = "http://www.portalfiscal.inf.br/nfe";

    // Caracteres de controle (fora de \t\n\r) e surrogates soltos não são válidos em XML 1.0 —
    // o parser DOM recusa com INVALID_CHARACTER_ERR ao tentar setTextContent. Dado vindo de
    // import do sistema legado às vezes carrega esse tipo de lixo (padding com bytes de
    // controle); sanitiza aqui, no único ponto que cria nó de texto, em vez de em cada campo.
    private static final Pattern CARACTER_XML_INVALIDO =
            Pattern.compile("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\uFFFE\\uFFFF]");

    private NfeXml() {
    }

    static Document novoDocumento() {
        try {
            // namespace-aware=true: exigido pra criar elementos com createElementNS — com
            // setAttribute("xmlns", ...) a canonicalização da assinatura não enxerga o
            // namespace herdado e a SEFAZ devolve cStat=297 "Assinatura difere do calculado"
            // (ver NfeXmlBuilder.construir).
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            return builder.newDocument();
        } catch (ParserConfigurationException e) {
            throw new IllegalStateException(e);
        }
    }

    static Element element(Document doc, String tag) {
        return doc.createElementNS(NS_NFE, tag);
    }

    /** Acrescenta {@code <tag>valor</tag>} a {@code parent}; valor nulo não gera a tag. */
    static void text(Document doc, Element parent, String tag, Object valor) {
        if (valor == null) {
            return;
        }
        Element el = doc.createElementNS(NS_NFE, tag);
        el.setTextContent(CARACTER_XML_INVALIDO.matcher(String.valueOf(valor)).replaceAll(""));
        parent.appendChild(el);
    }

    static String dec(BigDecimal valor, int scale) {
        return (valor == null ? BigDecimal.ZERO : valor).setScale(scale, RoundingMode.HALF_UP).toPlainString();
    }

    static String somenteDigitos(String texto) {
        return texto == null ? "" : texto.replaceAll("\\D", "");
    }
}
