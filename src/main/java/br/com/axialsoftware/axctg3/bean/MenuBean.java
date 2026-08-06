package br.com.axialsoftware.axctg3.bean;

import br.com.axialsoftware.axctg3.entity.cadastros.ConfigRel;
import br.com.axialsoftware.axctg3.entity.contabil.ContaContabil;
import br.com.axialsoftware.axctg3.service.UtilGeralService;
import br.com.axialsoftware.axctg3.service.contabil.ContaContabilService;
import br.com.axialsoftware.axctg3.service.contabil.LancamentoService;
import io.jmix.core.DataManager;
import io.jmix.core.SaveContext;
import io.jmix.flowui.Dialogs;
import io.jmix.flowui.action.DialogAction;
import io.jmix.flowui.app.inputdialog.DialogActions;
import io.jmix.flowui.app.inputdialog.DialogOutcome;
import io.jmix.flowui.component.UiComponentUtils;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

import static io.jmix.flowui.app.inputdialog.InputParameter.*;

@Component("MenuBean")
public class MenuBean {

    private final UtilGeralService utilGeralService;
    private final Dialogs dialogs;
    private final ContaContabilService contaContabilService;
    private final DataManager dataManager;
    private final LancamentoService lancamentoService;

    public MenuBean(UtilGeralService utilGeralService, Dialogs dialogs, ContaContabilService contaContabilService, DataManager dataManager, LancamentoService lancamentoService) {
        this.utilGeralService = utilGeralService;
        this.dialogs = dialogs;
        this.contaContabilService = contaContabilService;
        this.dataManager = dataManager;
        this.lancamentoService = lancamentoService;
    }

    public void listarLancamentos() {
        ConfigRel configRel = utilGeralService.prepararConfigRel();
        String codCtaDev, codCtaCre, origem;
        Optional<String> optConta = Optional.ofNullable(configRel.getContaDevedora());
        codCtaDev = optConta.orElse("");
        optConta = Optional.ofNullable(configRel.getContaCredora());
        codCtaCre = optConta.orElse("");
        optConta = Optional.ofNullable(configRel.getOrigem());
        origem = optConta.orElse("");
        Optional<Integer> optLancamento = Optional.ofNullable(configRel.getLancamentoInicial());
        Integer lancamentoInicial = optLancamento.orElse(0);
        Optional<Integer> optLancamentoFinal = Optional.ofNullable(configRel.getLancamentoFinal());
        Integer lancamentoFinal = optLancamentoFinal.orElse(999999);
        Optional<LocalDate> optData = Optional.ofNullable(configRel.getDataInicial());
        LocalDate dataInicial = optData.orElse(LocalDate.now());
        optData = Optional.ofNullable(configRel.getDataFinal());
        LocalDate dataFinal = optData.orElse(LocalDate.now());
        ContaContabil contaDevedora = contaContabilService.contaContabilPeloCodigo(codCtaDev);
        dialogs.createInputDialog(UiComponentUtils.getCurrentView())
                .withHeader("Listagem de lançamentos")
                .withParameters(
                        intParameter("lancamentoInicial")
                                .withLabel("Lançamento inicial")
                                .withDefaultValue(lancamentoInicial),
                        intParameter("lancamentoFinal")
                                .withLabel("Lançamento final")
                                .withDefaultValue(lancamentoFinal),
                        localDateParameter("dataInicial")
                                .withLabel("Data inicial")
                                .withDefaultValue(dataInicial),
                        localDateParameter("dataFinal")
                                .withLabel("Data final")
                                .withDefaultValue(dataFinal),
                        stringParameter("contaDevedora")
                                .withLabel("Conta devedora")
                                .withDefaultValue(codCtaDev),
                        stringParameter("contaCredora")
                                .withLabel("Conta credora")
                                .withDefaultValue(codCtaCre),
                        stringParameter("origem")
                                .withLabel("Origem lançamento")
                                .withDefaultValue(origem)
                )
                .withActions(DialogActions.OK_CANCEL)
                .withCloseListener(closeEvent -> {
                    if (closeEvent.closedWith(DialogOutcome.OK)) {
                        SaveContext saveContext = new SaveContext();
                        Integer lanc = closeEvent.getValue("lancamentoInicial");
                        // configRel.setLancamentoInicial(closeEvent.getValue("lancamentoInicial"));
                        configRel.setLancamentoInicial(lanc);
                        lanc = closeEvent.getValue("lancamentoFinal");
                        configRel.setLancamentoFinal(lanc);
                        configRel.setDataInicial(closeEvent.getValue("dataInicial"));
                        configRel.setDataFinal(closeEvent.getValue("dataFinal"));
                        configRel.setContaDevedora(closeEvent.getValue("contaDevedora"));
                        configRel.setContaCredora(closeEvent.getValue("contaCredora"));
                        configRel.setOrigem(closeEvent.getValue("origem"));
                        saveContext.saving(configRel);
                        dataManager.save(saveContext);
                        lancamentoService.listarLancamentos(configRel);
                    }
                })
                .open();
    }

    public void listarRazao() {
        ConfigRel configRel = utilGeralService.prepararConfigRel();
        Optional<LocalDate> optData = Optional.ofNullable(configRel.getDataInicial());
        LocalDate dataInicial = optData.orElse(LocalDate.now());
        optData = Optional.ofNullable(configRel.getDataFinal());
        LocalDate dataFinal = optData.orElse(LocalDate.now());
        Optional<String> optConta = Optional.ofNullable(configRel.getContaInicial());
        String contaInicial = optConta.orElse("");
        optConta = Optional.ofNullable(configRel.getContaFinal());
        String contaFinal = optConta.orElse("");
        dialogs.createInputDialog(UiComponentUtils.getCurrentView())
                .withHeader("Razão contábil")
                .withParameters(
                        localDateParameter("dataInicial")
                                .withLabel("Data inicial")
                                .withDefaultValue(dataInicial),
                        localDateParameter("dataFinal")
                                .withLabel("Data final")
                                .withDefaultValue(dataFinal),
                        stringParameter("contaInicial")
                                .withLabel("Conta inicial")
                                .withDefaultValue(contaInicial),
                        stringParameter("contaFinal")
                                .withLabel("Conta final")
                                .withDefaultValue(contaFinal)
                )
                .withActions(DialogActions.OK_CANCEL)
                .withCloseListener(closeEvent -> {
                    if (closeEvent.closedWith(DialogOutcome.OK)) {
                        SaveContext saveContext = new SaveContext();
                        configRel.setDataInicial(closeEvent.getValue("dataInicial"));
                        configRel.setDataFinal(closeEvent.getValue("dataFinal"));
                        configRel.setContaInicial(closeEvent.getValue("contaInicial"));
                        configRel.setContaFinal(closeEvent.getValue("contaFinal"));
                        configRel.setOrigem(closeEvent.getValue("origem"));
                        saveContext.saving(configRel);
                        dataManager.save(saveContext);
                        lancamentoService.listarRazao(configRel);
                    }
                })
                .open();
    }

    public void listarContasContabeis() {
        dialogs.createOptionDialog()
                .withHeader("Confirmação")
                .withText("Confirma listagem das contas contábeis?")
                .withActions(
                        new DialogAction(DialogAction.Type.YES)
                                .withHandler(e -> {
                                    contaContabilService.listarContasContabeis();
                                }),
                        new DialogAction(DialogAction.Type.NO)
                )
                .open();
    }

    public void listarBalancete(Map<String, Object> parameters) {
        int tipo = Integer.parseInt((String) parameters.get("tipo"));

        ConfigRel configRel = utilGeralService.prepararConfigRel();

        String caption;
        if (tipo == 1) {
            caption = "Balancete";
        } else {
            caption = "Balanço";
        }
        String contaInicial = configRel.getContaInicial();
        String contaFinal = configRel.getContaFinal();
        Integer grauFechamento = configRel.getGrauFechamento();
        dialogs.createInputDialog(UiComponentUtils.getCurrentView())
                .withHeader(caption)
                .withParameters(
                        stringParameter("contaInicial")
                                .withLabel("Conta inicial")
                                .withDefaultValue(contaInicial),
                        stringParameter("contaFinal")
                                .withLabel("Conta final")
                                .withDefaultValue(contaFinal),
                        intParameter("grauFechamento")
                                .withLabel("Grau fechamento")
                                .withDefaultValue(grauFechamento)
                )
                .withActions(DialogActions.OK_CANCEL)
                .withCloseListener(closeEvent -> {
                    if (closeEvent.closedWith(DialogOutcome.OK)) {
                        SaveContext saveContext = new SaveContext();
                        configRel.setContaInicial(closeEvent.getValue("contaInicial"));
                        configRel.setContaFinal(closeEvent.getValue("contaFinal"));
                        configRel.setGrauFechamento(closeEvent.getValue("grauFechamento"));
                        saveContext.saving(configRel);
                        dataManager.save(saveContext);
                        contaContabilService.listarBalancete(tipo, configRel);
                    }
                })
                .open();
    }
}