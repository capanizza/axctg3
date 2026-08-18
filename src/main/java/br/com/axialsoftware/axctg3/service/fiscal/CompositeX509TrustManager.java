package br.com.axialsoftware.axctg3.service.fiscal;

import javax.net.ssl.X509TrustManager;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

/**
 * {@link X509TrustManager} que tenta uma lista de delegados em ordem — só falha se
 * **todos** rejeitarem. Usado por {@link NfeWebserviceClient} pra confiar tanto no
 * truststore padrão da JVM quanto na cadeia ICP-Brasil embarcada no projeto (a SEFAZ usa
 * AC que normalmente não está no truststore padrão — ver docs/EMISSAO-NFE.md).
 */
class CompositeX509TrustManager implements X509TrustManager {

    private final List<X509TrustManager> delegados;

    CompositeX509TrustManager(List<X509TrustManager> delegados) {
        this.delegados = delegados;
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        CertificateException ultimoErro = null;
        for (X509TrustManager delegado : delegados) {
            try {
                delegado.checkClientTrusted(chain, authType);
                return;
            } catch (CertificateException e) {
                ultimoErro = e;
            }
        }
        throw ultimoErro != null ? ultimoErro : new CertificateException("Nenhum trust manager configurado");
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        CertificateException ultimoErro = null;
        for (X509TrustManager delegado : delegados) {
            try {
                delegado.checkServerTrusted(chain, authType);
                return;
            } catch (CertificateException e) {
                ultimoErro = e;
            }
        }
        throw ultimoErro != null ? ultimoErro : new CertificateException("Nenhum trust manager configurado");
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
        List<X509Certificate> todos = new ArrayList<>();
        for (X509TrustManager delegado : delegados) {
            todos.addAll(List.of(delegado.getAcceptedIssuers()));
        }
        return todos.toArray(new X509Certificate[0]);
    }
}
