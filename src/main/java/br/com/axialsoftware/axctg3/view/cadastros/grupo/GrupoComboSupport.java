package br.com.axialsoftware.axctg3.view.cadastros.grupo;

import br.com.axialsoftware.axctg3.entity.cadastros.Grupo;
import br.com.axialsoftware.axctg3.security.GrupoAcesso;
import com.vaadin.flow.component.combobox.ComboBox;
import io.jmix.core.DataManager;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Campo "Grupo" das telas de usuário e empresa: Grupo.codigo é um Integer solto (como o
 * codEmpresa), então o combo lista os códigos e mostra o nome. Só aparece para a Axial —
 * para os demais o grupo é sempre o do próprio usuário (forçado pelos listeners).
 */
@Component
public class GrupoComboSupport {

    private final DataManager dataManager;
    private final GrupoAcesso grupoAcesso;

    public GrupoComboSupport(DataManager dataManager, GrupoAcesso grupoAcesso) {
        this.dataManager = dataManager;
        this.grupoAcesso = grupoAcesso;
    }

    public void configurar(ComboBox<Integer> combo) {
        boolean axial = grupoAcesso.usuarioAtualEnxergaTodosGrupos();
        combo.setVisible(axial);
        if (!axial) {
            // escondido, mas o binding ainda grava o valor nele — e o ComboBox do Vaadin
            // recusa valor sem itens
            combo.setItems(grupoPadrao());
            return;
        }
        Map<Integer, String> grupos = new LinkedHashMap<>();
        dataManager.load(Grupo.class)
                .query("select g from Grupo g order by g.codigo")
                .list()
                .forEach(g -> grupos.put(g.getCodigo(), g.getInstanceName()));
        combo.setItems(grupos.keySet());
        combo.setItemLabelGenerator(codigo -> grupos.getOrDefault(codigo, String.valueOf(codigo)));
    }

    /** Grupo para um usuário/empresa novo: o de quem está cadastrando (0 sem usuário). */
    public Integer grupoPadrao() {
        Integer codGrupo = grupoAcesso.getCodGrupoUsuarioAtual();
        return codGrupo != null ? codGrupo : Grupo.CODIGO_AXIAL;
    }

    public boolean usuarioAtualEnxergaTodosGrupos() {
        return grupoAcesso.usuarioAtualEnxergaTodosGrupos();
    }
}
