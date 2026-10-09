package br.com.axialsoftware.axctg3.view.fiscal.nfe;

import br.com.axialsoftware.axctg3.entity.fiscal.Nfe;
import br.com.axialsoftware.axctg3.entity.fiscal.NfeCartaCorrecao;
import br.com.axialsoftware.axctg3.service.fiscal.NfeCartaCorrecaoComprovanteService;
import br.com.axialsoftware.axctg3.service.fiscal.NfeDigitadaService;
import br.com.axialsoftware.axctg3.view.main.MainView;
import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.HasValueAndElement;
import com.vaadin.flow.router.Route;
import io.jmix.core.AccessManager;
import io.jmix.core.Metadata;
import io.jmix.core.accesscontext.CrudEntityContext;
import io.jmix.flowui.Dialogs;
import io.jmix.flowui.Notifications;
import io.jmix.flowui.action.DialogAction;
import io.jmix.flowui.component.UiComponentUtils;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.kit.action.ActionPerformedEvent;
import io.jmix.flowui.kit.component.button.JmixButton;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Set;

// Nfe é o espelho do XML autorizado pela SEFAZ (ou importado de terceiros) — não existe
// cenário de negócio em que o usuário deva editar esses dados pela UI, então a tela inteira
// (todas as abas) é travada em modo somente leitura via ReadOnlyAwareView.setReadOnly, o
// mecanismo nativo do Jmix (propaga pra todo componente da view, inclusive as ações
// list_create/list_edit/list_remove dos dataGrids filhos de itens/duplicatas/pagamentos/
// volumes, que carregam @AdjustWhenViewReadOnly). detail_saveClose não carrega essa anotação,
// por isso o botão de salvar é escondido à mão enquanto a tela estiver travada.
//
// Exceção, desde 2026-10-09: o rascunho de uma NFe digitada (digitada = true, sem chave) abre
// editável para quem pode digitar — gerente fiscal e admin, os mesmos que podem excluir NFe
// (GerenteFiscalRole). Rascunho com tentativa pendente (chaveTentativa: o envio caiu sem
// resposta) continua travado: a SEFAZ pode ter autorizado o XML já enviado, e editar agora
// deixaria a nota gravada diferente dele — "Transmitir" consulta a SEFAZ e resolve. Mesmo aí, identificação da emissão, emitente, protocolo e XML ficam
// travados: são preenchidos pelo sistema na transmissão (NfeDigitadaEmissaoService), o
// emitente sempre a partir do cadastro da Empresa.
//
// A aba "Carta de Correção" foge um pouco desse espírito só-histórico: tem um botão
// "Imprimir" que reemite o comprovante de uma CC-e já registrada (NfeCartaCorrecaoComprovanteService).
// Isso não é uma ação mutadora — só reimpressão — e o botão é uma ação "bare" (sem type=
// list_*), que fica fora do alcance de setReadOnly/@AdjustWhenViewReadOnly (mesma observação
// já registrada acima pro botão "Visualizar" da aba Itens). A ação de EMITIR uma CC-e nova
// fica de fora daqui de propósito — mora em NfeListView/NotaSaidaListView, junto das outras
// ações contra a SEFAZ (cancelar, consultar, inutilizar), mantendo a separação "lista = ações,
// detalhe = espelho somente-leitura".
@Route(value = "nfes/:id", layout = MainView.class)
@ViewController(id = "Nfe.detail")
@ViewDescriptor(path = "nfe-detail-view.xml")
@EditedEntityContainer("nfeDc")
public class NfeDetailView extends StandardDetailView<Nfe> {

    // preenchidos pelo sistema mesmo no rascunho
    private static final Set<String> CAMPOS_DO_SISTEMA = Set.of(
            "chaveField", "codUfField", "codNfField", "modField", "serieField", "numeroNfField", "codDvField",
            "dhEmiField", "tpAmbField",
            "emitCnpjField", "emitXNomeField", "emitXFantField", "emitIeField", "emitCrtField", "emitXLgrField",
            "emitNroField", "emitXCplField", "emitXBairroField", "emitCMunField", "emitXMunField", "emitUfField",
            "emitCepField", "emitFoneField",
            "protTpAmbField", "protVerAplicField", "protDhRecbtoField", "protNProtField", "protDigValField",
            "protCStatField", "protXMotivoField", "xmlEnvioField", "xmlRetornoField");

    @ViewComponent
    private DataGrid<NfeCartaCorrecao> nfeCartasCorrecaoDataGrid;
    @ViewComponent
    private JmixButton saveAndCloseButton;
    @ViewComponent
    private JmixButton recalcularTotaisButton;
    @ViewComponent
    private JmixButton textoImportacaoButton;
    @Autowired
    private NfeCartaCorrecaoComprovanteService nfeCartaCorrecaoComprovanteService;
    @Autowired
    private NfeDigitadaService nfeDigitadaService;
    @Autowired
    private Dialogs dialogs;
    @Autowired
    private Notifications notifications;
    @Autowired
    private AccessManager accessManager;
    @Autowired
    private Metadata metadata;
    @ViewComponent
    private MessageBundle messageBundle;

    @Subscribe
    public void onInit(final InitEvent event) {
        setReadOnly(true);
    }

    @Subscribe
    public void onReady(final ReadyEvent event) {
        Nfe nfe = getEditedEntity();
        boolean rascunho = Boolean.TRUE.equals(nfe.getDigitada()) && nfe.getChave() == null;
        boolean pendente = nfe.getChaveTentativa() != null;
        if (rascunho && podeDigitar()) {
            if (pendente) {
                notifications.create(messageBundle.getMessage("nfeDetailView.tentativaPendente"))
                        .withType(Notifications.Type.WARNING)
                        .show();
            } else {
                setReadOnly(false);
                travarCamposDoSistema();
            }
        }
        saveAndCloseButton.setVisible(!isReadOnly());
        recalcularTotaisButton.setVisible(!isReadOnly());
        textoImportacaoButton.setVisible(!isReadOnly());
    }

    @Subscribe(id = "recalcularTotaisButton", subject = "clickListener")
    public void onRecalcularTotaisButtonClick(final ClickEvent<JmixButton> event) {
        nfeDigitadaService.recalcularTotais(getEditedEntity());
        notifications.create(messageBundle.getMessage("nfeDetailView.recalcularTotais.feito"))
                .withType(Notifications.Type.SUCCESS)
                .show();
    }

    /**
     * Sugere o infCpl de uma nota de importação a partir das DIs e valores dos itens
     * ({@link NfeDigitadaService#textoImportacao}). É só sugestão: o texto fica no campo
     * pra ser editado, e substituir um texto já digitado pede confirmação.
     */
    @Subscribe(id = "textoImportacaoButton", subject = "clickListener")
    public void onTextoImportacaoButtonClick(final ClickEvent<JmixButton> event) {
        Nfe nfe = getEditedEntity();
        String texto = nfeDigitadaService.textoImportacao(nfe);
        if (texto == null) {
            notifications.create(messageBundle.getMessage("nfeDetailView.textoImportacao.semDi"))
                    .withType(Notifications.Type.WARNING)
                    .show();
            return;
        }
        if (nfe.getInfCpl() == null || nfe.getInfCpl().isBlank()) {
            nfe.setInfCpl(texto);
            return;
        }
        dialogs.createOptionDialog()
                .withHeader(messageBundle.getMessage("nfeDetailView.textoImportacaoButton.text"))
                .withText(messageBundle.getMessage("nfeDetailView.textoImportacao.substituir"))
                .withActions(
                        new DialogAction(DialogAction.Type.YES).withHandler(e -> nfe.setInfCpl(texto)),
                        new DialogAction(DialogAction.Type.NO))
                .open();
    }

    @Subscribe("nfeCartasCorrecaoDataGrid.imprimirCceAction")
    public void onNfeCartasCorrecaoDataGridImprimirCceAction(final ActionPerformedEvent event) {
        NfeCartaCorrecao selecionada = nfeCartasCorrecaoDataGrid.getSingleSelectedItem();
        if (selecionada == null) {
            dialogs.createMessageDialog()
                    .withHeader(messageBundle.getMessage("nfeDetailView.imprimirCceAction.text"))
                    .withText(messageBundle.getMessage("nfeDetailView.imprimirCce.naoSelecionado"))
                    .open();
            return;
        }
        nfeCartaCorrecaoComprovanteService.imprimir(selecionada.getId());
    }

    // gerente fiscal e admin — os mesmos que podem excluir NFe (ver GerenteFiscalRole)
    private boolean podeDigitar() {
        CrudEntityContext contexto = new CrudEntityContext(metadata.getClass(Nfe.class));
        accessManager.applyRegisteredConstraints(contexto);
        return contexto.isDeletePermitted();
    }

    // por id na view inteira: os campos ficam em abas, cujo conteúdo não aparece em
    // getChildren() enquanto a aba não é aberta
    private void travarCamposDoSistema() {
        for (String id : CAMPOS_DO_SISTEMA) {
            if (UiComponentUtils.getComponent(this, id) instanceof HasValueAndElement<?, ?> campo) {
                campo.setReadOnly(true);
            }
        }
    }
}
