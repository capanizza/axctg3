package br.com.axialsoftware.axctg3.service.fiscal;

import br.com.axialsoftware.axctg3.entity.cadastros.Empresa;
import io.jmix.core.FileStorage;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.crypto.dsig.CanonicalizationMethod;
import javax.xml.crypto.dsig.DigestMethod;
import javax.xml.crypto.dsig.Reference;
import javax.xml.crypto.dsig.SignatureMethod;
import javax.xml.crypto.dsig.SignedInfo;
import javax.xml.crypto.dsig.Transform;
import javax.xml.crypto.dsig.XMLSignature;
import javax.xml.crypto.dsig.XMLSignatureFactory;
import javax.xml.crypto.dsig.dom.DOMSignContext;
import javax.xml.crypto.dsig.keyinfo.KeyInfo;
import javax.xml.crypto.dsig.keyinfo.KeyInfoFactory;
import javax.xml.crypto.dsig.keyinfo.X509Data;
import javax.xml.crypto.dsig.spec.C14NMethodParameterSpec;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

/**
 * Assinatura digital da NFe (enveloped signature, C14N sem comentários, RSA-SHA1 — o
 * leiaute atual ainda exige SHA-1, apesar de defasado pros padrões de hoje) usando o
 * certificado A1 (.pfx/.p12) configurado em {@link Empresa#getCertificadoArquivo()}. Usa
 * só {@code javax.xml.crypto.dsig} (módulo padrão do JDK, sem dependência nova). Parte da
 * emissão própria de NFe — ver docs/EMISSAO-NFE.md.
 */
@Service
public class NfeXmlSigner {

    private final FileStorage fileStorage;

    public NfeXmlSigner(FileStorage fileStorage) {
        this.fileStorage = fileStorage;
    }

    /** Carrega o keystore PKCS12 do certificado configurado na empresa. */
    public KeyStore carregarKeyStore(Empresa empresa) throws Exception {
        if (empresa.getCertificadoArquivo() == null || empresa.getCertificadoSenha() == null) {
            throw new IllegalStateException("Empresa sem certificado digital configurado — ver aba \"Emissão NFe\"");
        }
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (InputStream in = fileStorage.openStream(empresa.getCertificadoArquivo())) {
            keyStore.load(in, empresa.getCertificadoSenha().toCharArray());
        }
        return keyStore;
    }

    /** Assina o {@code infNFe} de chave {@code chave} dentro do documento, in-place. */
    public Document assinar(Document doc, String chave, Empresa empresa) throws Exception {
        return assinarElemento(doc, "infNFe", empresa);
    }

    /**
     * Assina o {@code infEvento} de um evento (ex.: cancelamento, tpEvento 110111) —
     * mesma técnica de assinatura de {@link #assinar}, só muda a tag do elemento assinado
     * ({@code infEvento} em vez de {@code infNFe}), já que o leiaute de evento usa a
     * própria estrutura de assinatura da NFe (enveloped, C14N sem comentários, RSA-SHA1).
     */
    public Document assinarEvento(Document doc, Empresa empresa) throws Exception {
        return assinarElemento(doc, "infEvento", empresa);
    }

    private Document assinarElemento(Document doc, String tagElementoAssinado, Empresa empresa) throws Exception {
        KeyStore keyStore = carregarKeyStore(empresa);
        String alias = primeiroAlias(keyStore);
        PrivateKey privateKey = (PrivateKey) keyStore.getKey(alias, empresa.getCertificadoSenha().toCharArray());
        X509Certificate certificado = (X509Certificate) keyStore.getCertificate(alias);

        Element elementoAssinado = primeiroPorTag(doc, tagElementoAssinado);
        // Registra "Id" como atributo de tipo ID de verdade — sem isso a referência
        // "#<Id>" no Reference abaixo não resolve na hora de assinar.
        elementoAssinado.setIdAttribute("Id", true);
        Element raiz = (Element) elementoAssinado.getParentNode();

        XMLSignatureFactory fac = XMLSignatureFactory.getInstance("DOM");

        Reference ref = fac.newReference(
                "#" + elementoAssinado.getAttribute("Id"),
                fac.newDigestMethod(DigestMethod.SHA1, null),
                List.of(
                        fac.newTransform(Transform.ENVELOPED, (javax.xml.crypto.dsig.spec.TransformParameterSpec) null),
                        fac.newTransform(CanonicalizationMethod.INCLUSIVE, (javax.xml.crypto.dsig.spec.TransformParameterSpec) null)
                ),
                null, null);

        SignedInfo signedInfo = fac.newSignedInfo(
                fac.newCanonicalizationMethod(CanonicalizationMethod.INCLUSIVE, (C14NMethodParameterSpec) null),
                fac.newSignatureMethod(SignatureMethod.RSA_SHA1, null),
                List.of(ref));

        KeyInfoFactory kif = fac.getKeyInfoFactory();
        X509Data x509Data = kif.newX509Data(List.of(certificado));
        KeyInfo keyInfo = kif.newKeyInfo(List.of(x509Data));

        DOMSignContext signContext = new DOMSignContext(privateKey, raiz);
        XMLSignature signature = fac.newXMLSignature(signedInfo, keyInfo);
        signature.sign(signContext);

        return doc;
    }

    private String primeiroAlias(KeyStore keyStore) throws Exception {
        Enumeration<String> aliases = keyStore.aliases();
        if (!aliases.hasMoreElements()) {
            throw new IllegalStateException("Certificado sem nenhum alias — arquivo .pfx inválido ou corrompido");
        }
        return aliases.nextElement();
    }

    private Element primeiroPorTag(Document doc, String tag) {
        NodeList lista = doc.getElementsByTagName(tag);
        if (lista.getLength() == 0) {
            throw new IllegalStateException("Elemento <" + tag + "> não encontrado no XML a assinar");
        }
        return (Element) lista.item(0);
    }
}
