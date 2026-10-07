package br.com.axialsoftware.axctg3.listener.cadastros;

import br.com.axialsoftware.axctg3.entity.cadastros.Empresa;
import br.com.axialsoftware.axctg3.entity.cadastros.Grupo;
import br.com.axialsoftware.axctg3.security.GrupoAcesso;
import br.com.axialsoftware.axctg3.service.cadastros.GrupoService;
import io.jmix.core.event.EntitySavingEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Grupo e código da empresa.
 * <ul>
 *     <li>codGrupo: quem não é da Axial só grava empresa no próprio grupo, seja qual for o
 *     valor que veio; a Axial escolhe o grupo (em branco = grupo do usuário, ou 0 numa
 *     rotina interna sem usuário).</li>
 *     <li>codigo: empresa nova com código 0/em branco recebe o próximo da Sequence global
 *     ({@link GrupoService#proximoCodigoEmpresa()}) — o codEmpresa é único no banco todo, é ele que separa os
 *     dados de um cliente dos de outro.</li>
 * </ul>
 */
@Component
public class EmpresaEventListener {

    private final GrupoAcesso grupoAcesso;
    private final GrupoService grupoService;

    public EmpresaEventListener(GrupoAcesso grupoAcesso, GrupoService grupoService) {
        this.grupoAcesso = grupoAcesso;
        this.grupoService = grupoService;
    }

    @EventListener
    public void onEmpresaSaving(final EntitySavingEvent<Empresa> event) {
        Empresa empresa = event.getEntity();
        Integer grupoUsuario = grupoAcesso.getCodGrupoUsuarioAtual();
        if (!grupoAcesso.usuarioAtualEnxergaTodosGrupos()) {
            empresa.setCodGrupo(grupoUsuario);
        } else if (empresa.getCodGrupo() == null) {
            empresa.setCodGrupo(grupoUsuario != null ? grupoUsuario : Grupo.CODIGO_AXIAL);
        }

        if (event.isNewEntity() && (empresa.getCodigo() == null || empresa.getCodigo() == 0)) {
            empresa.setCodigo(grupoService.proximoCodigoEmpresa());
        }
    }
}
