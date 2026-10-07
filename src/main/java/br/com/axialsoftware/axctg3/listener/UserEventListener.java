package br.com.axialsoftware.axctg3.listener;

import br.com.axialsoftware.axctg3.entity.User;
import br.com.axialsoftware.axctg3.entity.cadastros.Grupo;
import br.com.axialsoftware.axctg3.security.GrupoAcesso;
import io.jmix.core.event.EntitySavingEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Mantém cada usuário dentro do seu grupo.
 * <ul>
 *     <li>codGrupo: quem não é da Axial só grava usuário no próprio grupo; a Axial
 *     escolhe (em branco = grupo de quem grava, ou 0 numa rotina interna).</li>
 *     <li>codEmpresa: tem que ser de uma empresa do mesmo grupo do usuário. Todas as
 *     consultas filtram só pelo codEmpresa, então é esta verificação que impede alguém de
 *     apontar o próprio usuário para a empresa de outro cliente.</li>
 * </ul>
 */
@Component
public class UserEventListener {

    private final GrupoAcesso grupoAcesso;

    public UserEventListener(GrupoAcesso grupoAcesso) {
        this.grupoAcesso = grupoAcesso;
    }

    @EventListener
    public void onUserSaving(final EntitySavingEvent<User> event) {
        User user = event.getEntity();
        Integer grupoUsuarioAtual = grupoAcesso.getCodGrupoUsuarioAtual();
        boolean enxergaTodos = grupoAcesso.usuarioAtualEnxergaTodosGrupos();
        if (!enxergaTodos) {
            user.setCodGrupo(grupoUsuarioAtual);
        } else if (user.getCodGrupo() == null) {
            user.setCodGrupo(grupoUsuarioAtual != null ? grupoUsuarioAtual : Grupo.CODIGO_AXIAL);
        }

        Integer codEmpresa = user.getCodEmpresa();
        if (codEmpresa != null) {
            Integer grupoEmpresa = grupoAcesso.getCodGrupoDaEmpresa(codEmpresa);
            // Empresa inexistente só passa para a Axial (testes e importações preparam o
            // usuário antes de criar a empresa); para os demais seria uma porta para um
            // código que ainda vai ser criado em outro grupo.
            boolean permitido = grupoEmpresa == null
                    ? enxergaTodos
                    : Objects.equals(grupoEmpresa, user.getCodGrupo());
            if (!permitido) {
                throw new IllegalStateException(String.format(
                        "Empresa %d não pertence ao grupo %d do usuário %s",
                        codEmpresa, user.getCodGrupo(), user.getUsername()));
            }
        }
    }
}
