package br.com.axialsoftware.axctg3.view.fiscal.notasaida;

import br.com.axialsoftware.axctg3.entity.cadastros.ConfigRel;
import br.com.axialsoftware.axctg3.entity.cadastros.Empresa;
import br.com.axialsoftware.axctg3.entity.enums.AmbienteNfe;
import br.com.axialsoftware.axctg3.entity.fiscal.NotaSaida;
import br.com.axialsoftware.axctg3.service.UtilGeralService;
import br.com.axialsoftware.axctg3.service.fiscal.NfeDanfeService;
import br.com.axialsoftware.axctg3.service.fiscal.NfeEmissaoService;
import br.com.axialsoftware.axctg3.service.fiscal.NfeWebserviceClient;
import br.com.axialsoftware.axctg3.view.main.MainView;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.router.Route;
import io.jmix.core.DataManager;
import io.jmix.core.Messages;
import io.jmix.core.SaveContext;
import io.jmix.flowui.Dialogs;
import io.jmix.flowui.ViewNavigators;
import io.jmix.flowui.action.DialogAction;
import io.jmix.flowui.app.inputdialog.DialogActions;
import io.jmix.flowui.app.inputdialog.DialogOutcome;
import io.jmix.flowui.component.UiComponentUtils;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.kit.action.ActionPerformedEvent;
import io.jmix.flowui.model.CollectionLoader;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.Optional;

import static io.jmix.flowui.app.inputdialog.InputParameter.enumParameter;
import static io.jmix.flowui.app.inputdialog.InputParameter.localDateParameter;

@Route(value = "nota-saidas", layout = MainView.class)
@ViewController(id = "NotaSaida.list")
@ViewDescriptor(path = "nota-saida-list-view.xml")
@LookupComponent("notaSaidasDataGrid")
@DialogMode(width = "64em")
public class NotaSaidaListView extends StandardListView<NotaSaida> {

    static Boolean isNew = true;

    @ViewComponent
    private CollectionLoader<NotaSaida> notaSaidasDl;
    @ViewComponent
    private DataGrid<NotaSaida> notaSaidasDataGrid;
    @ViewComponent
    private MessageBundle messageBundle;
    @ViewComponent
    private Span ambienteBadge;
    @Autowired
    private UtilGeralService utilGeralService;
    @Autowired
    private Dialogs dialogs;
    @Autowired
    private ViewNavigators viewNavigators;
    @Autowired
    private DataManager dataManager;
    @Autowired
    private NfeEmissaoService nfeEmissaoService;
    @Autowired
    private NfeDanfeService nfeDanfeService;
    @Autowired
    private NfeWebserviceClient nfeWebserviceClient;
    @Autowired
    private Messages messages;

    @Subscribe
    public void onBeforeShow(final BeforeShowEvent event) {
        ConfigRel configRel = utilGeralService.prepararConfigRel();
        LocalDate dataEmissaoInicial = Optional.ofNullable(configRel.getDataEmissaoNotaSaidaInicial())
                .orElse(LocalDate.now());
        LocalDate dataEmissaoFinal = Optional.ofNullable(configRel.getDataEmissaoNotaSaidaFinal())
                .orElse(LocalDate.now());
        notaSaidasDl.setParameter("dataEmissaoInicial", dataEmissaoInicial);
        notaSaidasDl.setParameter("dataEmissaoFinal", dataEmissaoFinal);
        notaSaidasDl.setParameter("codEmpresa", utilGeralService.getCodEmpresa());
        notaSaidasDl.load();

        atualizarBadgeAmbiente();
    }

    @Override
    public String getPageTitle() {
        ConfigRel configRel = utilGeralService.prepararConfigRel();
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");
        String title = super.getPageTitle();
        if (isNew) {
            isNew = false;
        } else {
            LocalDate dataEmissaoInicial = Optional.ofNullable(configRel.getDataEmissaoNotaSaidaInicial())
                    .orElse(LocalDate.now());
            LocalDate dataEmissaoFinal = Optional.ofNullable(configRel.getDataEmissaoNotaSaidaFinal())
                    .orElse(LocalDate.now());
            String title2 = "emissão " +
                    sdf.format(utilGeralService.localDateToSqlDate(dataEmissaoInicial)) +
                    " a " +
                    sdf.format(utilGeralService.localDateToSqlDate(dataEmissaoFinal));
            title = title + " " + title2;
        }
        AmbienteNfe ambiente = ambienteAtual();
        return ambiente == null ? title : "[" + messages.getMessage(ambiente) + "] " + title;
    }

    @Subscribe("notaSaidasDataGrid.delimitarAction")
    public void onNotaSaidasDataGridDelimitarAction(final ActionPerformedEvent event) {
        ConfigRel configRel = utilGeralService.prepararConfigRel();
        LocalDate dataEmissaoInicial = Optional.ofNullable(configRel.getDataEmissaoNotaSaidaInicial())
                .orElse(LocalDate.now());
        LocalDate dataEmissaoFinal = Optional.ofNullable(configRel.getDataEmissaoNotaSaidaFinal())
                .orElse(LocalDate.now());
        dialogs.createInputDialog(UiComponentUtils.getCurrentView())
                .withHeader("Notas de saída")
                .withParameters(
                        localDateParameter("dataEmissaoInicial")
                                .withLabel("Data emissão inicial")
                                .withDefaultValue(dataEmissaoInicial),
                        localDateParameter("dataEmissaoFinal")
                                .withLabel("Data emissão final")
                                .withDefaultValue(dataEmissaoFinal)
                )
                .withActions(DialogActions.OK_CANCEL)
                .withCloseListener(closeEvent -> {
                    if (closeEvent.closedWith(DialogOutcome.OK)) {
                        SaveContext saveContext = new SaveContext();
                        configRel.setDataEmissaoNotaSaidaInicial(closeEvent.getValue("dataEmissaoInicial"));
                        configRel.setDataEmissaoNotaSaidaFinal(closeEvent.getValue("dataEmissaoFinal"));
                        saveContext.saving(configRel);
                        dataManager.save(saveContext);
                        notaSaidasDl.setParameter("dataEmissaoInicial", configRel.getDataEmissaoNotaSaidaInicial());
                        notaSaidasDl.setParameter("dataEmissaoFinal", configRel.getDataEmissaoNotaSaidaFinal());
                        notaSaidasDl.setParameter("codEmpresa", utilGeralService.getCodEmpresa());
                        notaSaidasDl.load();
                        getPageTitle();
                        viewNavigators.listView(this, NotaSaida.class)
                                .withViewClass(NotaSaidaListView.class)
                                .navigate();
                    }
                })
                .open();
    }

    @Subscribe("notaSaidasDataGrid.emitirNfeAction")
    public void onNotaSaidasDataGridEmitirNfeAction(final ActionPerformedEvent event) {
        NotaSaida selecionada = notaSaidasDataGrid.getSingleSelectedItem();
        if (selecionada == null) {
            dialogs.createMessageDialog()
                    .withHeader(messageBundle.getMessage("notaSaidaListView.emitirNfeAction.text"))
                    .withText(messageBundle.getMessage("notaSaidaListView.emitirNfe.naoSelecionado"))
                    .open();
            return;
        }
        if (ambienteAtual() == AmbienteNfe.HOMOLOGACAO) {
            dialogs.createOptionDialog()
                    .withHeader(messageBundle.getMessage("notaSaidaListView.emitirNfe.confirmarHomologacao.header"))
                    .withText(messageBundle.getMessage("notaSaidaListView.emitirNfe.confirmarHomologacao.text"))
                    .withActions(
                            new DialogAction(DialogAction.Type.OK)
                                    .withText(messageBundle.getMessage("notaSaidaListView.emitirNfe.confirmarHomologacao.confirmar"))
                                    .withHandler(e -> executarEmissaoNfe(selecionada)),
                            new DialogAction(DialogAction.Type.CANCEL)
                                    .withText(messageBundle.getMessage("notaSaidaListView.emitirNfe.confirmarHomologacao.cancelar"))
                    )
                    .open();
            return;
        }
        executarEmissaoNfe(selecionada);
    }

    private void executarEmissaoNfe(NotaSaida selecionada) {
        NfeEmissaoService.ResultadoEmissao resultado = nfeEmissaoService.emitir(selecionada.getId());
        if (resultado.sucesso()) {
            dialogs.createOptionDialog()
                    .withHeader(messageBundle.getMessage("notaSaidaListView.emitirNfe.sucesso.header"))
                    .withText(messageBundle.formatMessage("notaSaidaListView.emitirNfe.sucesso.text", resultado.chave()))
                    .withActions(
                            new DialogAction(DialogAction.Type.OK)
                                    .withText(messageBundle.getMessage("notaSaidaListView.emitirNfe.sucesso.imprimirDanfe"))
                                    .withHandler(e -> nfeDanfeService.emitirDanfePorChave(resultado.chave())),
                            new DialogAction(DialogAction.Type.CANCEL)
                                    .withText(messageBundle.getMessage("notaSaidaListView.emitirNfe.sucesso.fechar"))
                    )
                    .open();
            notaSaidasDl.load();
        } else {
            dialogs.createMessageDialog()
                    .withHeader(messageBundle.getMessage("notaSaidaListView.emitirNfe.erro.header"))
                    .withText(messageBundle.formatMessage("notaSaidaListView.emitirNfe.erro.text", resultado.motivo()))
                    .open();
        }
    }

    @Subscribe("notaSaidasDataGrid.emitirDanfeAction")
    public void onNotaSaidasDataGridEmitirDanfeAction(final ActionPerformedEvent event) {
        NotaSaida selecionada = notaSaidasDataGrid.getSingleSelectedItem();
        if (selecionada == null) {
            dialogs.createMessageDialog()
                    .withHeader(messageBundle.getMessage("notaSaidaListView.emitirDanfeAction.text"))
                    .withText(messageBundle.getMessage("notaSaidaListView.emitirDanfe.naoSelecionado"))
                    .open();
            return;
        }
        if (selecionada.getChave() == null || selecionada.getChave().isBlank()) {
            dialogs.createMessageDialog()
                    .withHeader(messageBundle.getMessage("notaSaidaListView.emitirDanfeAction.text"))
                    .withText(messageBundle.getMessage("notaSaidaListView.emitirDanfe.naoEmitida"))
                    .open();
            return;
        }
        boolean emitido = nfeDanfeService.emitirDanfePorChave(selecionada.getChave());
        if (!emitido) {
            dialogs.createMessageDialog()
                    .withHeader(messageBundle.getMessage("notaSaidaListView.emitirDanfeAction.text"))
                    .withText(messageBundle.getMessage("notaSaidaListView.emitirDanfe.naoEncontrada"))
                    .open();
        }
    }

    /*
     * Cancelar NFe, Consultar NFe e Inutilizar números de notas ainda não têm service
     * implementado. Placeholders no dropDownButton pra já fixar a estrutura do menu; cada
     * um vira handler de verdade quando o service correspondente for implementado.
     */
    @Subscribe("notaSaidasDataGrid.cancelarNfeAction")
    public void onNotaSaidasDataGridCancelarNfeAction(final ActionPerformedEvent event) {
        mostrarEmDesenvolvimento("notaSaidaListView.cancelarNfeAction.text");
    }

    @Subscribe("notaSaidasDataGrid.consultarNfeAction")
    public void onNotaSaidasDataGridConsultarNfeAction(final ActionPerformedEvent event) {
        mostrarEmDesenvolvimento("notaSaidaListView.consultarNfeAction.text");
    }

    /** Mesma lógica de {@code EmpresaDetailView.onTestarConexaoSefazButtonClick}. */
    @Subscribe("notaSaidasDataGrid.verificarStatusServicoAction")
    public void onNotaSaidasDataGridVerificarStatusServicoAction(final ActionPerformedEvent event) {
        Empresa empresa = utilGeralService.getEmpresa();
        if (empresa.getCrt() == null || empresa.getAmbienteNfe() == null
                || empresa.getCertificadoArquivo() == null || empresa.getCertificadoSenha() == null) {
            dialogs.createMessageDialog()
                    .withHeader(messageBundle.getMessage("notaSaidaListView.verificarStatusServicoAction.text"))
                    .withText(messageBundle.getMessage("notaSaidaListView.verificarStatusServico.semConfig"))
                    .open();
            return;
        }
        try {
            NfeWebserviceClient.Resposta resposta = nfeWebserviceClient.consultarStatusServico(empresa);
            String ambiente = messages.getMessage(empresa.getAmbienteNfe());
            dialogs.createMessageDialog()
                    .withHeader(messageBundle.getMessage("notaSaidaListView.verificarStatusServico.sucesso.header"))
                    .withText(messageBundle.formatMessage("notaSaidaListView.verificarStatusServico.sucesso.text",
                            ambiente, resposta.cStat(), resposta.xMotivo()))
                    .open();
        } catch (Exception e) {
            dialogs.createMessageDialog()
                    .withHeader(messageBundle.getMessage("notaSaidaListView.verificarStatusServico.falha.header"))
                    .withText(messageBundle.formatMessage("notaSaidaListView.verificarStatusServico.falha.text", e.getMessage()))
                    .open();
        }
    }

    @Subscribe("notaSaidasDataGrid.inutilizarNumerosAction")
    public void onNotaSaidasDataGridInutilizarNumerosAction(final ActionPerformedEvent event) {
        mostrarEmDesenvolvimento("notaSaidaListView.inutilizarNumerosAction.text");
    }

    @Subscribe("notaSaidasDataGrid.alternarAmbienteAction")
    public void onNotaSaidasDataGridAlternarAmbienteAction(final ActionPerformedEvent event) {
        Empresa empresa = utilGeralService.getEmpresa();
        dialogs.createInputDialog(UiComponentUtils.getCurrentView())
                .withHeader(messageBundle.getMessage("notaSaidaListView.alternarAmbienteAction.text"))
                .withParameters(
                        enumParameter("ambienteNfe", AmbienteNfe.class)
                                .withLabel(messageBundle.getMessage("notaSaidaListView.alternarAmbiente.label"))
                                .withDefaultValue(empresa.getAmbienteNfe())
                )
                .withActions(DialogActions.OK_CANCEL)
                .withCloseListener(closeEvent -> {
                    if (closeEvent.closedWith(DialogOutcome.OK)) {
                        AmbienteNfe novoAmbiente = closeEvent.getValue("ambienteNfe");
                        empresa.setAmbienteNfe(novoAmbiente);
                        dataManager.save(empresa);
                        atualizarBadgeAmbiente();
                        // getPageTitle() não é reconsultado automaticamente pelo Vaadin fora de
                        // navegação — atualiza o título da aba in-place, sem navigate() (que
                        // reinstanciaria a view e perderia seleção/filtro/ordenação da grid).
                        UI.getCurrent().getPage().setTitle(getPageTitle());
                        dialogs.createMessageDialog()
                                .withHeader(messageBundle.getMessage("notaSaidaListView.alternarAmbienteAction.text"))
                                .withText(messageBundle.formatMessage("notaSaidaListView.alternarAmbiente.sucesso",
                                        messages.getMessage(novoAmbiente)))
                                .open();
                    }
                })
                .open();
    }

    private void mostrarEmDesenvolvimento(String chaveTextoAcao) {
        dialogs.createMessageDialog()
                .withHeader(messageBundle.getMessage(chaveTextoAcao))
                .withText(messageBundle.getMessage("notaSaidaListView.emDesenvolvimento.text"))
                .open();
    }

    /** {@code null} enquanto nenhuma empresa foi selecionada (ver {@code SelecionarEmpresaListView}). */
    private AmbienteNfe ambienteAtual() {
        if (utilGeralService.getCodEmpresa() == null) {
            return null;
        }
        return utilGeralService.getEmpresa().getAmbienteNfe();
    }

    /**
     * Badge colorido — produção em azul, homologação em vermelho — pra deixar o ambiente
     * ativo visível na tela sem precisar abrir o cadastro da empresa. {@code getPageTitle}
     * (só o título da aba do navegador) reforça a mesma informação, mas em texto puro: a
     * API de título de página do Vaadin (HasDynamicTitle) não renderiza HTML/cor.
     */
    private void atualizarBadgeAmbiente() {
        AmbienteNfe ambiente = ambienteAtual();
        if (ambiente == null) {
            ambienteBadge.setText(messageBundle.getMessage("notaSaidaListView.ambienteBadge.naoConfigurado"));
            ambienteBadge.getStyle().set("color", "var(--vaadin-text-color-secondary)").set("font-weight", "bold");
            return;
        }
        ambienteBadge.setText(messageBundle.formatMessage("notaSaidaListView.ambienteBadge.text", messages.getMessage(ambiente)));
        String cor = ambiente == AmbienteNfe.PRODUCAO ? "var(--aura-blue-text)" : "var(--aura-red-text)";
        ambienteBadge.getStyle().set("color", cor).set("font-weight", "bold");
    }
}
