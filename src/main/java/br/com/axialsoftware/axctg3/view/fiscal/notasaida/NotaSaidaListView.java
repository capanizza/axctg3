package br.com.axialsoftware.axctg3.view.fiscal.notasaida;

import br.com.axialsoftware.axctg3.entity.cadastros.ConfigRel;
import br.com.axialsoftware.axctg3.entity.cadastros.Empresa;
import br.com.axialsoftware.axctg3.entity.enums.AmbienteNfe;
import br.com.axialsoftware.axctg3.entity.enums.FinNfe;
import br.com.axialsoftware.axctg3.entity.fiscal.NotaSaida;
import br.com.axialsoftware.axctg3.service.UtilGeralService;
import br.com.axialsoftware.axctg3.service.fiscal.NfeCancelamentoService;
import br.com.axialsoftware.axctg3.service.fiscal.NfeCartaCorrecaoService;
import br.com.axialsoftware.axctg3.service.fiscal.NfeDanfeService;
import br.com.axialsoftware.axctg3.service.fiscal.NfeEmissaoService;
import br.com.axialsoftware.axctg3.service.fiscal.NfeWebserviceClient;
import br.com.axialsoftware.axctg3.view.fiscal.nfecartacorrecao.NfeCartaCorrecaoListView;
import br.com.axialsoftware.axctg3.view.main.MainView;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.router.Route;
import io.jmix.core.DataManager;
import io.jmix.core.Messages;
import io.jmix.core.SaveContext;
import io.jmix.flowui.DialogWindows;
import io.jmix.flowui.Dialogs;
import io.jmix.flowui.UiComponents;
import io.jmix.flowui.ViewNavigators;
import io.jmix.flowui.action.DialogAction;
import io.jmix.flowui.app.inputdialog.DialogActions;
import io.jmix.flowui.app.inputdialog.DialogOutcome;
import io.jmix.flowui.component.UiComponentUtils;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.component.textarea.JmixTextArea;
import io.jmix.flowui.component.validation.ValidationErrors;
import io.jmix.flowui.kit.action.ActionPerformedEvent;
import io.jmix.flowui.model.CollectionLoader;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.Optional;

import static io.jmix.flowui.app.inputdialog.InputParameter.enumParameter;
import static io.jmix.flowui.app.inputdialog.InputParameter.stringParameter;
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
    private UiComponents uiComponents;
    @Autowired
    private DataManager dataManager;
    @Autowired
    private NfeEmissaoService nfeEmissaoService;
    @Autowired
    private NfeDanfeService nfeDanfeService;
    @Autowired
    private NfeCancelamentoService nfeCancelamentoService;
    @Autowired
    private NfeCartaCorrecaoService nfeCartaCorrecaoService;
    @Autowired
    private NfeWebserviceClient nfeWebserviceClient;
    @Autowired
    private DialogWindows dialogWindows;
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
     * Consultar NFe ainda não tem service implementado. Placeholder no dropDownButton pra
     * já fixar a estrutura do menu; vira handler de verdade quando o service for
     * implementado. ("Inutilizar números de notas" foi removido daqui em 2026-09-02 — a
     * faixa inutilizada não tem NotaSaida correspondente pra selecionar, então o pedido de
     * verdade só faz sentido em NfeListView, que já tem o fluxo completo.)
     */
    @Subscribe("notaSaidasDataGrid.cancelarNfeAction")
    public void onNotaSaidasDataGridCancelarNfeAction(final ActionPerformedEvent event) {
        NotaSaida selecionada = notaSaidasDataGrid.getSingleSelectedItem();
        if (selecionada == null) {
            dialogs.createMessageDialog()
                    .withHeader(messageBundle.getMessage("notaSaidaListView.cancelarNfeAction.text"))
                    .withText(messageBundle.getMessage("notaSaidaListView.cancelarNfe.naoSelecionado"))
                    .open();
            return;
        }
        if (selecionada.getChave() == null || selecionada.getChave().isBlank()) {
            dialogs.createMessageDialog()
                    .withHeader(messageBundle.getMessage("notaSaidaListView.cancelarNfeAction.text"))
                    .withText(messageBundle.getMessage("notaSaidaListView.cancelarNfe.naoEmitida"))
                    .open();
            return;
        }
        pedirJustificativaECancelar(selecionada.getChave());
    }

    /**
     * O check de "só cancela NFe autorizada (cStat=100)" e de comprimento mínimo da
     * justificativa (quando preenchida) acontece em {@code NfeCancelamentoService} — aqui
     * só pede a justificativa (pré-preenchida com o último texto usado, via
     * {@code ConfigRel}) e mostra o resultado, sem duplicar a regra de negócio. Em branco
     * é permitido: o service substitui por uma desculpa genérica ("NFe emitida
     * incorretamente.") — só um valor digitado e curto demais é barrado aqui.
     */
    private void pedirJustificativaECancelar(String chave) {
        ConfigRel configRel = utilGeralService.prepararConfigRel();
        dialogs.createInputDialog(UiComponentUtils.getCurrentView())
                .withHeader(messageBundle.getMessage("notaSaidaListView.cancelarNfeAction.text"))
                .withParameters(
                        stringParameter("justificativa")
                                .withLabel(messageBundle.getMessage("notaSaidaListView.cancelarNfe.justificativa.label"))
                                .withDefaultValue(configRel.getJustificativaCancelamentoNfe())
                )
                .withActions(DialogActions.OK_CANCEL)
                .withValidator(context -> {
                    String justificativa = context.getValue("justificativa");
                    if (justificativa != null && !justificativa.isBlank() && justificativa.trim().length() < 15) {
                        return ValidationErrors.of(messageBundle.getMessage("notaSaidaListView.cancelarNfe.justificativa.minima"));
                    }
                    return ValidationErrors.none();
                })
                .withCloseListener(closeEvent -> {
                    if (!closeEvent.closedWith(DialogOutcome.OK)) {
                        return;
                    }
                    String justificativa = closeEvent.getValue("justificativa");
                    configRel.setJustificativaCancelamentoNfe(justificativa);
                    dataManager.save(configRel);
                    NfeCancelamentoService.ResultadoCancelamento resultado = nfeCancelamentoService.cancelarPorChave(chave, justificativa);
                    if (resultado.sucesso()) {
                        dialogs.createMessageDialog()
                                .withHeader(messageBundle.getMessage("notaSaidaListView.cancelarNfe.sucesso.header"))
                                .withText(messageBundle.formatMessage("notaSaidaListView.cancelarNfe.sucesso.text", resultado.motivo()))
                                .open();
                        notaSaidasDl.load();
                    } else {
                        dialogs.createMessageDialog()
                                .withHeader(messageBundle.getMessage("notaSaidaListView.cancelarNfe.erro.header"))
                                .withText(messageBundle.formatMessage("notaSaidaListView.cancelarNfe.erro.text", resultado.motivo()))
                                .open();
                    }
                })
                .open();
    }

    @Subscribe("notaSaidasDataGrid.emitirCceAction")
    public void onNotaSaidasDataGridEmitirCceAction(final ActionPerformedEvent event) {
        NotaSaida selecionada = notaSaidasDataGrid.getSingleSelectedItem();
        if (selecionada == null) {
            dialogs.createMessageDialog()
                    .withHeader(messageBundle.getMessage("notaSaidaListView.emitirCceAction.text"))
                    .withText(messageBundle.getMessage("notaSaidaListView.emitirCce.naoSelecionado"))
                    .open();
            return;
        }
        if (selecionada.getChave() == null || selecionada.getChave().isBlank()) {
            dialogs.createMessageDialog()
                    .withHeader(messageBundle.getMessage("notaSaidaListView.emitirCceAction.text"))
                    .withText(messageBundle.getMessage("notaSaidaListView.emitirCce.naoEmitida"))
                    .open();
            return;
        }
        pedirTextoEEmitirCce(selecionada.getChave());
    }

    /**
     * Diferente de {@link #pedirJustificativaECancelar}, aqui o texto é sempre obrigatório —
     * uma CC-e sem correção real não faz sentido, então não há desculpa padrão pra texto em
     * branco. Também não pré-preenche com {@code ConfigRel} (mesma decisão de
     * {@code NfeListView.pedirTextoEEmitirCce}: cada correção é sobre um erro diferente).
     */
    private void pedirTextoEEmitirCce(String chave) {
        dialogs.createInputDialog(UiComponentUtils.getCurrentView())
                .withHeader(messageBundle.getMessage("notaSaidaListView.emitirCceAction.text"))
                .withParameters(
                        stringParameter("textoCorrecao")
                                .withLabel(messageBundle.getMessage("notaSaidaListView.emitirCce.textoCorrecao.label"))
                                .withField(this::criarTextAreaCorrecao)
                )
                .withActions(DialogActions.OK_CANCEL)
                .withValidator(context -> {
                    String texto = context.getValue("textoCorrecao");
                    if (texto == null || texto.isBlank()) {
                        return ValidationErrors.of(messageBundle.getMessage("notaSaidaListView.emitirCce.textoCorrecao.obrigatorio"));
                    }
                    if (texto.trim().length() < 15) {
                        return ValidationErrors.of(messageBundle.getMessage("notaSaidaListView.emitirCce.textoCorrecao.minima"));
                    }
                    return ValidationErrors.none();
                })
                .withCloseListener(closeEvent -> {
                    if (!closeEvent.closedWith(DialogOutcome.OK)) {
                        return;
                    }
                    String texto = closeEvent.getValue("textoCorrecao");
                    NfeCartaCorrecaoService.ResultadoCorrecao resultado = nfeCartaCorrecaoService.corrigirPorChave(chave, texto);
                    if (resultado.sucesso()) {
                        dialogs.createMessageDialog()
                                .withHeader(messageBundle.getMessage("notaSaidaListView.emitirCce.sucesso.header"))
                                .withText(messageBundle.formatMessage("notaSaidaListView.emitirCce.sucesso.text",
                                        resultado.numeroSequencial(), resultado.cStat(), resultado.motivo()))
                                .open();
                    } else {
                        dialogs.createMessageDialog()
                                .withHeader(messageBundle.getMessage("notaSaidaListView.emitirCce.erro.header"))
                                .withText(messageBundle.formatMessage("notaSaidaListView.emitirCce.erro.text", resultado.motivo()))
                                .open();
                    }
                })
                .open();
    }

    /**
     * Campo de várias linhas pro texto da correção — mesmo motivo/cuidado de
     * {@code NfeListView.criarTextAreaCorrecao} (não compartilhado entre as duas classes
     * porque não têm base comum; é só um helper de UI, sem lógica de negócio pra duplicar).
     */
    private JmixTextArea criarTextAreaCorrecao() {
        JmixTextArea textArea = uiComponents.create(JmixTextArea.class);
        textArea.setWidthFull();
        textArea.setMinHeight("8em");
        return textArea;
    }

    /**
     * Mesmos dados já visíveis na aba "Carta de Correção" de {@code NfeDetailView} — atalho
     * de um clique, mesmo motivo de {@code NfeListView.onNfesDataGridVerCartasCorrecaoAction}.
     */
    @Subscribe("notaSaidasDataGrid.verCartasCorrecaoAction")
    public void onNotaSaidasDataGridVerCartasCorrecaoAction(final ActionPerformedEvent event) {
        NotaSaida selecionada = notaSaidasDataGrid.getSingleSelectedItem();
        if (selecionada == null) {
            dialogs.createMessageDialog()
                    .withHeader(messageBundle.getMessage("notaSaidaListView.verCartasCorrecaoAction.text"))
                    .withText(messageBundle.getMessage("notaSaidaListView.verCartasCorrecao.naoSelecionado"))
                    .open();
            return;
        }
        if (selecionada.getChave() == null || selecionada.getChave().isBlank()) {
            dialogs.createMessageDialog()
                    .withHeader(messageBundle.getMessage("notaSaidaListView.verCartasCorrecaoAction.text"))
                    .withText(messageBundle.getMessage("notaSaidaListView.verCartasCorrecao.naoEmitida"))
                    .open();
            return;
        }
        DialogWindow<NfeCartaCorrecaoListView> dialogWindow = dialogWindows.view(this, NfeCartaCorrecaoListView.class).build();
        dialogWindow.getView().setChave(selecionada.getChave());
        dialogWindow.open();
    }

    /**
     * Abre {@code NotaSaidaComplementarDetailView} já em modo de criação, pré-preenchida
     * com a finalidade "Complementar" e a chave da nota original — usado quando o erro
     * está em VALOR (base de cálculo, alíquota, diferença de preço, quantidade), algo que
     * a CC-e explicitamente não pode corrigir (ver {@code
     * NfeCartaCorrecaoService.X_COND_USO}). Natureza/classTrib/cliente vêm travados nessa
     * tela dedicada (herdados da nota original, o operador não edita); itens ficam vazios
     * de propósito: só o operador sabe qual é a diferença de valor a lançar, nenhum
     * cálculo automático de item aqui (arriscado e não pedido) — o pseudo item nasce
     * sozinho na hora de emitir, ver {@code NfeEmissaoService.gerarItemComplementar}.
     * Depois de preencher os valores e salvar, a emissão de verdade acontece pelo botão
     * "Emitir NFe" já existente — {@code NfeEmissaoService} não ganha um fluxo novo, só
     * passa a montar {@code finNFe}/{@code NFref} corretos porque a nota carrega esses
     * dados agora.
     */
    @Subscribe("notaSaidasDataGrid.emitirNfeComplementarAction")
    public void onNotaSaidasDataGridEmitirNfeComplementarAction(final ActionPerformedEvent event) {
        NotaSaida original = notaSaidasDataGrid.getSingleSelectedItem();
        if (original == null) {
            dialogs.createMessageDialog()
                    .withHeader(messageBundle.getMessage("notaSaidaListView.emitirNfeComplementarAction.text"))
                    .withText(messageBundle.getMessage("notaSaidaListView.emitirNfeComplementar.naoSelecionado"))
                    .open();
            return;
        }
        if (original.getChave() == null || original.getChave().isBlank()) {
            dialogs.createMessageDialog()
                    .withHeader(messageBundle.getMessage("notaSaidaListView.emitirNfeComplementarAction.text"))
                    .withText(messageBundle.getMessage("notaSaidaListView.emitirNfeComplementar.naoEmitida"))
                    .open();
            return;
        }
        dialogWindows.detail(this, NotaSaida.class)
                .withViewClass(NotaSaidaComplementarDetailView.class)
                .newEntity()
                .withInitializer(nova -> {
                    nova.setFinNfe(FinNfe.COMPLEMENTAR);
                    nova.setChaveNotaOriginal(original.getChave());
                    nova.setParceiro(original.getParceiro());
                    nova.setNatureza(original.getNatureza());
                    nova.setClassTrib(original.getClassTrib());
                    nova.setEspecie(original.getEspecie());
                    nova.setSerie(original.getSerie());
                    nova.setDataEmissao(LocalDate.now());
                    nova.setDataSaida(LocalDate.now());
                })
                .open();
    }

    @Subscribe("notaSaidasDataGrid.consultarNfeAction")
    public void onNotaSaidasDataGridConsultarNfeAction(final ActionPerformedEvent event) {
        NotaSaida selecionada = notaSaidasDataGrid.getSingleSelectedItem();
        if (selecionada == null) {
            dialogs.createMessageDialog()
                    .withHeader(messageBundle.getMessage("notaSaidaListView.consultarNfeAction.text"))
                    .withText(messageBundle.getMessage("notaSaidaListView.consultarNfe.naoSelecionado"))
                    .open();
            return;
        }
        // Sem chave confirmada, cai pra chaveTentativa (última chave calculada antes de uma
        // transmissão que não confirmou — comunicação/timeout, ver NfeEmissaoService) — é
        // exatamente o caso em que essa consulta mais importa: resolve se a SEFAZ recebeu
        // de verdade ou não, sem precisar reemitir às cegas.
        String chave = selecionada.getChave() != null && !selecionada.getChave().isBlank()
                ? selecionada.getChave() : selecionada.getChaveTentativa();
        if (chave == null || chave.isBlank()) {
            dialogs.createMessageDialog()
                    .withHeader(messageBundle.getMessage("notaSaidaListView.consultarNfeAction.text"))
                    .withText(messageBundle.getMessage("notaSaidaListView.consultarNfe.naoEmitida"))
                    .open();
            return;
        }
        consultarEExibir(selecionada, chave);
    }

    /**
     * {@code NFeConsultaProtocolo4} — situação oficial e atual de uma chave na SEFAZ
     * (autorizada/cancelada/denegada/não localizada), independente do que está gravado
     * localmente. Mesma checagem de config e mesmo texto de falha de rede de
     * {@code onNotaSaidasDataGridVerificarStatusServicoAction} — não é um "sucesso/erro" de
     * pedido, então uma única mensagem informativa (sem branch sucesso/erro), igual ao
     * padrão de "Verificar status do serviço".
     *
     * <p>Se a consulta voltar autorizada e {@code notaSaida.chave} ainda estiver vazia
     * (consultada via {@code chaveTentativa} — resposta anterior perdida/timeout), completa
     * o registro local na hora ({@link NfeEmissaoService#completarSeAutorizada}) — sem isso,
     * "Consultar NFe" confirmava a autorização mas não gravava chave/protocolo em lugar
     * nenhum, deixando a nota travada mesmo sabendo que estava tudo certo (caso real,
     * 2026-09-02).
     */
    private void consultarEExibir(NotaSaida notaSaida, String chave) {
        Empresa empresa = utilGeralService.getEmpresa();
        if (empresa.getCrt() == null || empresa.getAmbienteNfe() == null
                || empresa.getCertificadoArquivo() == null || empresa.getCertificadoSenha() == null) {
            dialogs.createMessageDialog()
                    .withHeader(messageBundle.getMessage("notaSaidaListView.consultarNfeAction.text"))
                    .withText(messageBundle.getMessage("notaSaidaListView.verificarStatusServico.semConfig"))
                    .open();
            return;
        }
        try {
            NfeWebserviceClient.RespostaConsulta resposta = nfeWebserviceClient.consultarProtocolo(chave, empresa);
            String texto = resposta.nProt() == null
                    ? messageBundle.formatMessage("notaSaidaListView.consultarNfe.resultado.semProtocolo",
                            resposta.cStat(), resposta.xMotivo())
                    : messageBundle.formatMessage("notaSaidaListView.consultarNfe.resultado.comProtocolo",
                            resposta.cStat(), resposta.xMotivo(), resposta.nProt(), resposta.dhRecbto());

            boolean precisaCompletar = (notaSaida.getChave() == null || notaSaida.getChave().isBlank())
                    && resposta.cStat() != null && resposta.cStat() == 100;
            if (precisaCompletar) {
                NfeEmissaoService.ResultadoEmissao resultado =
                        nfeEmissaoService.completarSeAutorizada(notaSaida.getId(), resposta);
                if (resultado.sucesso()) {
                    texto = texto + " " + messageBundle.getMessage("notaSaidaListView.consultarNfe.chaveSalva");
                    notaSaidasDl.load();
                } else {
                    texto = texto + " " + resultado.motivo();
                }
            }

            dialogs.createMessageDialog()
                    .withHeader(messageBundle.getMessage("notaSaidaListView.consultarNfeAction.text"))
                    .withText(texto)
                    .open();
        } catch (Exception e) {
            dialogs.createMessageDialog()
                    .withHeader(messageBundle.getMessage("notaSaidaListView.verificarStatusServico.falha.header"))
                    .withText(messageBundle.formatMessage("notaSaidaListView.verificarStatusServico.falha.text", e.getMessage()))
                    .open();
        }
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
                        dialogs.createMessageDialog()
                                .withHeader(messageBundle.getMessage("notaSaidaListView.alternarAmbienteAction.text"))
                                .withText(messageBundle.formatMessage("notaSaidaListView.alternarAmbiente.sucesso",
                                        messages.getMessage(novoAmbiente)))
                                .open();
                        // O <h1 id="viewTitle"> do cabeçalho (StandardMainView) só é recalculado
                        // em AfterNavigationEvent — setTitle() muda só o document.title invisível
                        // da aba. Mesmo padrão de SelecionarEmpresaListView pra troca de
                        // empresa/período: reload() depois de abrir o diálogo de confirmação (o
                        // diálogo já foi enviado ao cliente antes do reload chegar).
                        UI.getCurrent().getPage().reload();
                    }
                })
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
