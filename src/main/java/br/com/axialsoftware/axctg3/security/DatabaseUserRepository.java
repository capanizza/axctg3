package br.com.axialsoftware.axctg3.security;

import br.com.axialsoftware.axctg3.entity.User;
import io.jmix.securitydata.user.AbstractDatabaseUserRepository;
import org.springframework.context.annotation.Primary;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Primary
@Component("UserRepository")
public class DatabaseUserRepository extends AbstractDatabaseUserRepository<User> {

    @Override
    protected Class<User> getUserClass() {
        return User.class;
    }

    /**
     * Todo usuário que entra recebe {@link IsolamentoGrupoRole}, além dos papéis atribuídos
     * no banco — fixo no código para que nenhum usuário fique sem o isolamento entre
     * grupos (e o admin de um grupo não consiga retirá-lo pela tela de papéis).
     */
    @Override
    protected Collection<? extends GrantedAuthority> createAuthorities(final String username) {
        final List<GrantedAuthority> authorities = new ArrayList<>(super.createAuthorities(username));
        authorities.addAll(getGrantedAuthoritiesBuilder()
                .addRowLevelRole(IsolamentoGrupoRole.CODE)
                .build());
        return authorities;
    }

    @Override
    protected void initSystemUser(final User systemUser) {
        final Collection<GrantedAuthority> authorities = getGrantedAuthoritiesBuilder()
                .addResourceRole(FullAccessRole.CODE)
                .build();
        systemUser.setAuthorities(authorities);
    }

    @Override
    protected void initAnonymousUser(final User anonymousUser) {
    }
}