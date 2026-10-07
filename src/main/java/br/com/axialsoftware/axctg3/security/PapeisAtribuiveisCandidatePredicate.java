package br.com.axialsoftware.axctg3.security;

import io.jmix.security.model.BaseRole;
import io.jmix.securityflowui.util.RoleAssignmentCandidatePredicate;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

/**
 * Filtra os papéis oferecidos nos lookups da tela de atribuição de papéis do Jmix: quem
 * não é da Axial só vê {@link AdminGrupoRole#PAPEIS_ATRIBUIVEIS}. É só a lista da tela — a
 * trava de verdade é o predicado de {@link IsolamentoGrupoRole} ao gravar.
 */
@Component
public class PapeisAtribuiveisCandidatePredicate implements RoleAssignmentCandidatePredicate {

    private final GrupoAcesso grupoAcesso;

    public PapeisAtribuiveisCandidatePredicate(GrupoAcesso grupoAcesso) {
        this.grupoAcesso = grupoAcesso;
    }

    @Override
    public boolean test(UserDetails usuario, BaseRole papel) {
        return grupoAcesso.usuarioAtualEnxergaTodosGrupos()
                || AdminGrupoRole.PAPEIS_ATRIBUIVEIS.contains(papel.getCode());
    }
}
