package br.com.axialsoftware.axctg3.test_support;

import io.jmix.core.security.SystemAuthenticator;
import io.jmix.flowui.testassist.UiTestAuthenticator;
import org.springframework.context.ApplicationContext;

/**
 * Autenticador de {@code @UiTest} como o usuário "admin" gravado no banco — o padrão do
 * {@code @UiTest} usa o usuário "system", montado em memória e nunca persistido, então
 * tela que grava algo ligado ao usuário logado (ex.: {@code ConfigRel} via
 * {@code UtilGeralService.prepararConfigRel()}, que o botão "Delimitar" das listas usa)
 * estoura "a new object was found through a relationship that was not marked cascade
 * PERSIST". Mesmo usuário do {@link AuthenticatedAsAdmin} dos testes de serviço.
 */
public class AdminUiTestAuthenticator implements UiTestAuthenticator {

    @Override
    public void setupAuthentication(ApplicationContext context) {
        context.getBean(SystemAuthenticator.class).begin("admin");
    }

    @Override
    public void removeAuthentication(ApplicationContext context) {
        context.getBean(SystemAuthenticator.class).end();
    }
}
