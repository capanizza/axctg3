package br.com.axialsoftware.axctg3.bean;

import br.com.axialsoftware.axctg3.entity.cadastros.ConfigRel;
import br.com.axialsoftware.axctg3.entity.contabil.ContaContabil;
import br.com.axialsoftware.axctg3.entity.contabil.HistoricoContabil;
import br.com.axialsoftware.axctg3.entity.financeiro.Banco;
import br.com.axialsoftware.axctg3.service.UtilGeralService;
import br.com.axialsoftware.axctg3.service.contabil.ContaContabilService;
import br.com.axialsoftware.axctg3.service.contabil.DepreciacaoService;
import br.com.axialsoftware.axctg3.service.contabil.EncerramentoService;
import br.com.axialsoftware.axctg3.service.contabil.LancamentoService;
import br.com.axialsoftware.axctg3.service.financeiro.DiversoPagarService;
import br.com.axialsoftware.axctg3.service.financeiro.ItemDiversoPagarService;
import br.com.axialsoftware.axctg3.service.financeiro.ItemPagarService;
import br.com.axialsoftware.axctg3.service.financeiro.ItemReceberService;
import br.com.axialsoftware.axctg3.service.financeiro.MovimentoBancoService;
import br.com.axialsoftware.axctg3.service.financeiro.TituloPagarService;
import br.com.axialsoftware.axctg3.service.financeiro.TituloReceberService;
import io.jmix.core.DataManager;
import io.jmix.core.SaveContext;
import io.jmix.flowui.Dialogs;
import io.jmix.flowui.action.DialogAction;
import io.jmix.flowui.app.inputdialog.DialogActions;
import io.jmix.flowui.app.inputdialog.DialogOutcome;
import io.jmix.flowui.backgroundtask.BackgroundTask;
import io.jmix.flowui.backgroundtask.TaskLifeCycle;
import io.jmix.flowui.component.UiComponentUtils;
import io.jmix.flowui.view.View;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static io.jmix.flowui.app.inputdialog.InputParameter.*;

@Component("MenuBean")
public class MenuBean {

    /** Uma empresa raramente passa de algumas centenas de contas — folga generosa mesmo assim. */
    private static final long TIMEOUT_ENCERRAMENTO_MINUTOS = 10;

    private final UtilGeralService utilGeralService;
    private final Dialogs dialogs;
    private final ContaContabilService contaContabilService;
    private final DataManager dataManager;
    private final LancamentoService lancamentoService;
    private final DepreciacaoService depreciacaoService;
    private final EncerramentoService encerramentoService;
    private final DiversoPagarService diversoPagarService;
    private final ItemDiversoPagarService itemDiversoPagarService;
    private final TituloReceberService tituloReceberService;
    private final ItemReceberService itemReceberService;
    private final TituloPagarService tituloPagarService;
    private final ItemPagarService itemPagarService;
    private final MovimentoBancoService movimentoBancoService;

    public MenuBean(UtilGeralService utilGeralService, Dialogs dialogs, ContaContabilService contaContabilService, DataManager dataManager, LancamentoService lancamentoService, DepreciacaoService depreciacaoService, EncerramentoService encerramentoService, DiversoPagarService diversoPagarService, ItemDiversoPagarService itemDiversoPagarService, TituloReceberService tituloReceberService, ItemReceberService itemReceberService, TituloPagarService tituloPagarService, ItemPagarService itemPagarService, MovimentoBancoService movimentoBancoService) {
        this.utilGeralService = utilGeralService;
        this.dialogs = dialogs;
        this.contaContabilService = contaContabilService;
        this.dataManager = dataManager;
        this.lancamentoService = lancamentoService;
        this.depreciacaoService = depreciacaoService;
        this.encerramentoService = encerramentoService;
        this.diversoPagarService = diversoPagarService;
        this.itemDiversoPagarService = itemDiversoPagarService;
        this.tituloReceberService = tituloReceberService;
        this.itemReceberService = itemReceberService;
        this.tituloPagarService = tituloPagarService;
        this.itemPagarService = itemPagarService;
        this.movimentoBancoService = movimentoBancoService;
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

    public void verificarContas() {
        dialogs.createOptionDialog()
                .withHeader("Confirmação")
                .withText("Confirma verificação das contas contábeis?")
                .withActions(
                        new DialogAction(DialogAction.Type.YES)
                                .withHandler(e -> {
                                    contaContabilService.verificarContas();
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

    public void calcularDepreciacoes() {
        ConfigRel configRel = utilGeralService.prepararConfigRel();
        Integer ano = utilGeralService.getAnoContabil();
        Integer mes = utilGeralService.getMesContabil();
        LocalDate ultimoDiaMes = LocalDate.of(ano, mes, 1).with(TemporalAdjusters.lastDayOfMonth());
        LocalDate dataDepreciacao = Optional.ofNullable(configRel.getDataDepreciacao()).orElse(ultimoDiaMes);
        dialogs.createInputDialog(UiComponentUtils.getCurrentView())
                .withHeader("Calcular depreciações")
                .withParameters(
                        localDateParameter("dataDepreciacao")
                                .withLabel("Data depreciação")
                                .withDefaultValue(dataDepreciacao)
                )
                .withActions(DialogActions.OK_CANCEL)
                .withCloseListener(closeEvent -> {
                    if (closeEvent.closedWith(DialogOutcome.OK)) {
                        SaveContext saveContext = new SaveContext();
                        configRel.setDataDepreciacao(closeEvent.getValue("dataDepreciacao"));
                        saveContext.saving(configRel);
                        dataManager.save(saveContext);
                        depreciacaoService.calcularDepreciacoes(configRel);
                    }
                })
                .open();
    }

    public void lancarDepreciacoes() {
        ConfigRel configRel = utilGeralService.prepararConfigRel();
        Integer ano = utilGeralService.getAnoContabil();
        Integer mes = utilGeralService.getMesContabil();
        LocalDate ultimoDiaMes = LocalDate.of(ano, mes, 1).with(TemporalAdjusters.lastDayOfMonth());
        LocalDate dataDepreciacao = Optional.ofNullable(configRel.getDataDepreciacao()).orElse(ultimoDiaMes);
        Integer historicoDepreciacao = Optional.ofNullable(configRel.getHistoricoDepreciacao()).orElse(0);
        dialogs.createInputDialog(UiComponentUtils.getCurrentView())
                .withHeader("Lançar depreciações")
                .withParameters(
                        intParameter("historicoDepreciacao")
                                .withLabel("Histórico depreciação")
                                .withDefaultValue(historicoDepreciacao),
                        localDateParameter("dataDepreciacao")
                                .withLabel("Data depreciação")
                                .withDefaultValue(dataDepreciacao)
                )
                .withActions(DialogActions.OK_CANCEL)
                .withCloseListener(closeEvent -> {
                    if (closeEvent.closedWith(DialogOutcome.OK)) {
                        SaveContext saveContext = new SaveContext();
                        configRel.setHistoricoDepreciacao(closeEvent.getValue("historicoDepreciacao"));
                        configRel.setDataDepreciacao(closeEvent.getValue("dataDepreciacao"));
                        saveContext.saving(configRel);
                        dataManager.save(saveContext);
                        depreciacaoService.lancarDepreciacoes(configRel);
                    }
                })
                .open();
    }

    public void listarDepreciacoes() {
        ConfigRel configRel = utilGeralService.prepararConfigRel();
        Integer ano = utilGeralService.getAnoContabil();
        Integer mes = utilGeralService.getMesContabil();
        LocalDate ultimoDiaMes = LocalDate.of(ano, mes, 1).with(TemporalAdjusters.lastDayOfMonth());
        LocalDate dataDepreciacao = Optional.ofNullable(configRel.getDataDepreciacao()).orElse(ultimoDiaMes);
        dialogs.createInputDialog(UiComponentUtils.getCurrentView())
                .withHeader("Listar depreciações")
                .withParameters(
                        localDateParameter("dataDepreciacao")
                                .withLabel("Data depreciação")
                                .withDefaultValue(dataDepreciacao),
                        booleanParameter("listagemCompleta")
                                .withLabel("Listagem completa")
                                .withDefaultValue(false)
                )
                .withWidth("AUTO")
                .withActions(DialogActions.OK_CANCEL)
                .withCloseListener(closeEvent -> {
                    if (closeEvent.closedWith(DialogOutcome.OK)) {
                        SaveContext saveContext = new SaveContext();
                        configRel.setDataDepreciacao(closeEvent.getValue("dataDepreciacao"));
                        saveContext.saving(configRel);
                        dataManager.save(saveContext);
                        Boolean listagemCompleta = closeEvent.getValue("listagemCompleta");
                        depreciacaoService.listarDepreciacoes(configRel, listagemCompleta);
                    }
                })
                .open();
    }

    public void resumoCorrecaoMonetaria() {
        dialogs.createOptionDialog()
                .withHeader("Confirmação")
                .withText("Confirma emissão do resumo de correção monetária?")
                .withActions(
                        new DialogAction(DialogAction.Type.YES)
                                .withHandler(e -> {
                                    depreciacaoService.resumoCorrecaoMonetaria();
                                }),
                        new DialogAction(DialogAction.Type.NO)
                )
                .open();
    }

    public void listarEntradaDiversosPagar() {
        ConfigRel configRel = utilGeralService.prepararConfigRel();
        LocalDate dataInicial = Optional.ofNullable(configRel.getDataEmissaoDiversoInicialListagem()).orElse(LocalDate.now());
        LocalDate dataFinal = Optional.ofNullable(configRel.getDataEmissaoDiversoFinalListagem()).orElse(LocalDate.now());
        dialogs.createInputDialog(UiComponentUtils.getCurrentView())
                .withHeader("Entrada de diversos a pagar")
                .withParameters(
                        localDateParameter("dataEmissaoInicial")
                                .withLabel("Data emissão inicial")
                                .withDefaultValue(dataInicial),
                        localDateParameter("dataEmissaoFinal")
                                .withLabel("Data emissão final")
                                .withDefaultValue(dataFinal)
                )
                .withActions(DialogActions.OK_CANCEL)
                .withCloseListener(closeEvent -> {
                    if (closeEvent.closedWith(DialogOutcome.OK)) {
                        SaveContext saveContext = new SaveContext();
                        configRel.setDataEmissaoDiversoInicialListagem(closeEvent.getValue("dataEmissaoInicial"));
                        configRel.setDataEmissaoDiversoFinalListagem(closeEvent.getValue("dataEmissaoFinal"));
                        saveContext.saving(configRel);
                        dataManager.save(saveContext);
                        diversoPagarService.listarEntradaDiversosPagar(configRel);
                    }
                })
                .open();
    }

    public void listarBaixaDiversosPagar() {
        ConfigRel configRel = utilGeralService.prepararConfigRel();
        LocalDate dataInicial = Optional.ofNullable(configRel.getDataBaixaDiversoInicialListagem()).orElse(LocalDate.now());
        LocalDate dataFinal = Optional.ofNullable(configRel.getDataBaixaDiversoFinalListagem()).orElse(LocalDate.now());
        dialogs.createInputDialog(UiComponentUtils.getCurrentView())
                .withHeader("Baixa de diversos a pagar")
                .withParameters(
                        localDateParameter("dataBaixaInicial")
                                .withLabel("Data baixa inicial")
                                .withDefaultValue(dataInicial),
                        localDateParameter("dataBaixaFinal")
                                .withLabel("Data baixa final")
                                .withDefaultValue(dataFinal)
                )
                .withActions(DialogActions.OK_CANCEL)
                .withCloseListener(closeEvent -> {
                    if (closeEvent.closedWith(DialogOutcome.OK)) {
                        SaveContext saveContext = new SaveContext();
                        configRel.setDataBaixaDiversoInicialListagem(closeEvent.getValue("dataBaixaInicial"));
                        configRel.setDataBaixaDiversoFinalListagem(closeEvent.getValue("dataBaixaFinal"));
                        saveContext.saving(configRel);
                        dataManager.save(saveContext);
                        itemDiversoPagarService.listarBaixaDiversosPagar(configRel);
                    }
                })
                .open();
    }

    public void listarEntradaTitulosReceber() {
        ConfigRel configRel = utilGeralService.prepararConfigRel();
        LocalDate dataInicial = Optional.ofNullable(configRel.getDataEmissaoReceberInicialListagem()).orElse(LocalDate.now());
        LocalDate dataFinal = Optional.ofNullable(configRel.getDataEmissaoReceberFinalListagem()).orElse(LocalDate.now());
        Banco banco = configRel.getBanco();
        dialogs.createInputDialog(UiComponentUtils.getCurrentView())
                .withHeader("Entrada de títulos a receber")
                .withParameters(
                        localDateParameter("dataEmissaoInicial")
                                .withLabel("Data emissão inicial")
                                .withDefaultValue(dataInicial),
                        localDateParameter("dataEmissaoFinal")
                                .withLabel("Data emissão final")
                                .withDefaultValue(dataFinal),
                        entityParameter("banco", Banco.class)
                                .withLabel("Banco (em branco = todos)")
                                .withDefaultValue(banco)
                )
                .withActions(DialogActions.OK_CANCEL)
                .withCloseListener(closeEvent -> {
                    if (closeEvent.closedWith(DialogOutcome.OK)) {
                        SaveContext saveContext = new SaveContext();
                        configRel.setDataEmissaoReceberInicialListagem(closeEvent.getValue("dataEmissaoInicial"));
                        configRel.setDataEmissaoReceberFinalListagem(closeEvent.getValue("dataEmissaoFinal"));
                        Banco bancoEscolhido = closeEvent.getValue("banco");
                        configRel.setBancoInicial(bancoEscolhido == null ? null : bancoEscolhido.getCodigo());
                        saveContext.saving(configRel);
                        dataManager.save(saveContext);
                        tituloReceberService.listarEntradaTitulosReceber(configRel);
                    }
                })
                .open();
    }

    public void tituloReceberVencimento() {
        ConfigRel configRel = utilGeralService.prepararConfigRel();
        LocalDate dataEmissaoInicial = Optional.ofNullable(configRel.getDataEmissaoReceberInicialListagem()).orElse(LocalDate.now());
        LocalDate dataEmissaoFinal = Optional.ofNullable(configRel.getDataEmissaoReceberFinalListagem()).orElse(LocalDate.now());
        LocalDate dataVencimentoInicial = Optional.ofNullable(configRel.getDataVencimentoReceberInicialListagem()).orElse(LocalDate.now());
        LocalDate dataVencimentoFinal = Optional.ofNullable(configRel.getDataVencimentoReceberFinalListagem()).orElse(LocalDate.now());
        dialogs.createInputDialog(UiComponentUtils.getCurrentView())
                .withHeader("Títulos a receber por vencimento")
                .withParameters(
                        localDateParameter("dataEmissaoInicial")
                                .withLabel("Data emissão inicial")
                                .withDefaultValue(dataEmissaoInicial),
                        localDateParameter("dataEmissaoFinal")
                                .withLabel("Data emissão final")
                                .withDefaultValue(dataEmissaoFinal),
                        localDateParameter("dataVencimentoInicial")
                                .withLabel("Data vencimento inicial")
                                .withDefaultValue(dataVencimentoInicial),
                        localDateParameter("dataVencimentoFinal")
                                .withLabel("Data vencimento final")
                                .withDefaultValue(dataVencimentoFinal)
                )
                .withActions(DialogActions.OK_CANCEL)
                .withCloseListener(closeEvent -> {
                    if (closeEvent.closedWith(DialogOutcome.OK)) {
                        SaveContext saveContext = new SaveContext();
                        configRel.setDataEmissaoReceberInicialListagem(closeEvent.getValue("dataEmissaoInicial"));
                        configRel.setDataEmissaoReceberFinalListagem(closeEvent.getValue("dataEmissaoFinal"));
                        configRel.setDataVencimentoReceberInicialListagem(closeEvent.getValue("dataVencimentoInicial"));
                        configRel.setDataVencimentoReceberFinalListagem(closeEvent.getValue("dataVencimentoFinal"));
                        saveContext.saving(configRel);
                        dataManager.save(saveContext);
                        tituloReceberService.tituloReceberVencimento(configRel);
                    }
                })
                .open();
    }

    public void listarBaixaTituloReceber() {
        ConfigRel configRel = utilGeralService.prepararConfigRel();
        LocalDate dataInicial = Optional.ofNullable(configRel.getDataBaixaReceberInicialListagem()).orElse(LocalDate.now());
        LocalDate dataFinal = Optional.ofNullable(configRel.getDataBaixaReceberFinalListagem()).orElse(LocalDate.now());
        Banco banco = configRel.getBanco();
        dialogs.createInputDialog(UiComponentUtils.getCurrentView())
                .withHeader("Baixa de títulos a receber")
                .withParameters(
                        localDateParameter("dataBaixaInicial")
                                .withLabel("Data baixa inicial")
                                .withDefaultValue(dataInicial),
                        localDateParameter("dataBaixaFinal")
                                .withLabel("Data baixa final")
                                .withDefaultValue(dataFinal),
                        entityParameter("banco", Banco.class)
                                .withLabel("Banco (em branco = todos)")
                                .withDefaultValue(banco)
                )
                .withActions(DialogActions.OK_CANCEL)
                .withCloseListener(closeEvent -> {
                    if (closeEvent.closedWith(DialogOutcome.OK)) {
                        SaveContext saveContext = new SaveContext();
                        configRel.setDataBaixaReceberInicialListagem(closeEvent.getValue("dataBaixaInicial"));
                        configRel.setDataBaixaReceberFinalListagem(closeEvent.getValue("dataBaixaFinal"));
                        Banco bancoEscolhido = closeEvent.getValue("banco");
                        configRel.setBancoInicial(bancoEscolhido == null ? null : bancoEscolhido.getCodigo());
                        saveContext.saving(configRel);
                        dataManager.save(saveContext);
                        itemReceberService.listarBaixaTitulosReceber(configRel);
                    }
                })
                .open();
    }

    public void listarEntradaTitulosPagar() {
        ConfigRel configRel = utilGeralService.prepararConfigRel();
        LocalDate dataInicial = Optional.ofNullable(configRel.getDataEmissaoPagarInicialListagem()).orElse(LocalDate.now());
        LocalDate dataFinal = Optional.ofNullable(configRel.getDataEmissaoPagarFinalListagem()).orElse(LocalDate.now());
        dialogs.createInputDialog(UiComponentUtils.getCurrentView())
                .withHeader("Entrada de títulos a pagar")
                .withParameters(
                        localDateParameter("dataEmissaoInicial")
                                .withLabel("Data emissão inicial")
                                .withDefaultValue(dataInicial),
                        localDateParameter("dataEmissaoFinal")
                                .withLabel("Data emissão final")
                                .withDefaultValue(dataFinal)
                )
                .withActions(DialogActions.OK_CANCEL)
                .withCloseListener(closeEvent -> {
                    if (closeEvent.closedWith(DialogOutcome.OK)) {
                        SaveContext saveContext = new SaveContext();
                        configRel.setDataEmissaoPagarInicialListagem(closeEvent.getValue("dataEmissaoInicial"));
                        configRel.setDataEmissaoPagarFinalListagem(closeEvent.getValue("dataEmissaoFinal"));
                        saveContext.saving(configRel);
                        dataManager.save(saveContext);
                        tituloPagarService.listarEntradaTitulosPagar(configRel);
                    }
                })
                .open();
    }

    public void tituloPagarVencimento() {
        ConfigRel configRel = utilGeralService.prepararConfigRel();
        LocalDate dataEmissaoInicial = Optional.ofNullable(configRel.getDataEmissaoPagarInicialListagem()).orElse(LocalDate.now());
        LocalDate dataEmissaoFinal = Optional.ofNullable(configRel.getDataEmissaoPagarFinalListagem()).orElse(LocalDate.now());
        LocalDate dataVencimentoInicial = Optional.ofNullable(configRel.getDataVencimentoPagarInicialListagem()).orElse(LocalDate.now());
        LocalDate dataVencimentoFinal = Optional.ofNullable(configRel.getDataVencimentoPagarFinalListagem()).orElse(LocalDate.now());
        dialogs.createInputDialog(UiComponentUtils.getCurrentView())
                .withHeader("Títulos a pagar por vencimento")
                .withParameters(
                        localDateParameter("dataEmissaoInicial")
                                .withLabel("Data emissão inicial")
                                .withDefaultValue(dataEmissaoInicial),
                        localDateParameter("dataEmissaoFinal")
                                .withLabel("Data emissão final")
                                .withDefaultValue(dataEmissaoFinal),
                        localDateParameter("dataVencimentoInicial")
                                .withLabel("Data vencimento inicial")
                                .withDefaultValue(dataVencimentoInicial),
                        localDateParameter("dataVencimentoFinal")
                                .withLabel("Data vencimento final")
                                .withDefaultValue(dataVencimentoFinal)
                )
                .withActions(DialogActions.OK_CANCEL)
                .withCloseListener(closeEvent -> {
                    if (closeEvent.closedWith(DialogOutcome.OK)) {
                        SaveContext saveContext = new SaveContext();
                        configRel.setDataEmissaoPagarInicialListagem(closeEvent.getValue("dataEmissaoInicial"));
                        configRel.setDataEmissaoPagarFinalListagem(closeEvent.getValue("dataEmissaoFinal"));
                        configRel.setDataVencimentoPagarInicialListagem(closeEvent.getValue("dataVencimentoInicial"));
                        configRel.setDataVencimentoPagarFinalListagem(closeEvent.getValue("dataVencimentoFinal"));
                        saveContext.saving(configRel);
                        dataManager.save(saveContext);
                        tituloPagarService.tituloPagarVencimento(configRel);
                    }
                })
                .open();
    }

    public void listarBaixaTituloPagar() {
        ConfigRel configRel = utilGeralService.prepararConfigRel();
        LocalDate dataInicial = Optional.ofNullable(configRel.getDataBaixaPagarInicialListagem()).orElse(LocalDate.now());
        LocalDate dataFinal = Optional.ofNullable(configRel.getDataBaixaPagarFinalListagem()).orElse(LocalDate.now());
        Banco banco = configRel.getBanco();
        dialogs.createInputDialog(UiComponentUtils.getCurrentView())
                .withHeader("Baixa de títulos a pagar")
                .withParameters(
                        localDateParameter("dataBaixaInicial")
                                .withLabel("Data baixa inicial")
                                .withDefaultValue(dataInicial),
                        localDateParameter("dataBaixaFinal")
                                .withLabel("Data baixa final")
                                .withDefaultValue(dataFinal),
                        entityParameter("banco", Banco.class)
                                .withLabel("Banco (em branco = todos)")
                                .withDefaultValue(banco)
                )
                .withActions(DialogActions.OK_CANCEL)
                .withCloseListener(closeEvent -> {
                    if (closeEvent.closedWith(DialogOutcome.OK)) {
                        SaveContext saveContext = new SaveContext();
                        configRel.setDataBaixaPagarInicialListagem(closeEvent.getValue("dataBaixaInicial"));
                        configRel.setDataBaixaPagarFinalListagem(closeEvent.getValue("dataBaixaFinal"));
                        Banco bancoEscolhido = closeEvent.getValue("banco");
                        configRel.setBancoInicial(bancoEscolhido == null ? null : bancoEscolhido.getCodigo());
                        saveContext.saving(configRel);
                        dataManager.save(saveContext);
                        itemPagarService.listarBaixaTitulosPagar(configRel);
                    }
                })
                .open();
    }

    public void listarMovimentoBanco(Integer tipoListagem) {
        String header = tipoListagem == 1 ? "Movimento bancário" : "Movimento financeiro";
        ConfigRel configRel = utilGeralService.prepararConfigRel();
        LocalDate dataInicial = Optional.ofNullable(configRel.getDataMovimentoBancoInicialListagem()).orElse(LocalDate.now());
        LocalDate dataFinal = Optional.ofNullable(configRel.getDataMovimentoBancoFinalListagem()).orElse(LocalDate.now());
        Integer codigoAtual = configRel.getBancoMovimentoBanco();
        Banco bancoAtual = codigoAtual == null ? null : dataManager.load(Banco.class)
                .query("select e from Banco e where e.codigo = :codigo and e.codEmpresa = :codEmpresa")
                .parameter("codigo", codigoAtual)
                .parameter("codEmpresa", utilGeralService.getCodEmpresa())
                .optional()
                .orElse(null);
        dialogs.createInputDialog(UiComponentUtils.getCurrentView())
                .withHeader(header)
                .withParameters(
                        localDateParameter("dataInicial")
                                .withLabel("Data inicial")
                                .withDefaultValue(dataInicial),
                        localDateParameter("dataFinal")
                                .withLabel("Data final")
                                .withDefaultValue(dataFinal),
                        entityParameter("banco", Banco.class)
                                .withLabel(tipoListagem == 1 ? "Banco (em branco = todos)" : "Banco")
                                .withDefaultValue(bancoAtual)
                                .withRequired(tipoListagem != 1)
                )
                .withActions(DialogActions.OK_CANCEL)
                .withCloseListener(closeEvent -> {
                    if (closeEvent.closedWith(DialogOutcome.OK)) {
                        SaveContext saveContext = new SaveContext();
                        configRel.setDataMovimentoBancoInicialListagem(closeEvent.getValue("dataInicial"));
                        configRel.setDataMovimentoBancoFinalListagem(closeEvent.getValue("dataFinal"));
                        Banco banco = closeEvent.getValue("banco");
                        if (tipoListagem == 2 && banco == null) {
                            dialogs.createMessageDialog()
                                    .withHeader("Movimento financeiro")
                                    .withText("Preencher banco")
                                    .open();
                            return;
                        }
                        configRel.setBancoMovimentoBanco(banco == null ? null : banco.getCodigo());
                        saveContext.saving(configRel);
                        dataManager.save(saveContext);
                        if (tipoListagem == 1) {
                            movimentoBancoService.listarMovimentoBanco(configRel);
                        } else {
                            movimentoBancoService.listarMovimentoFinanceiro(configRel);
                        }
                    }
                })
                .open();
    }

    // ===================== Encerramento de exercício =====================

    private String resumirCodigos(List<String> codigos) {
        int limite = 20;
        if (codigos.size() <= limite) {
            return String.join(", ", codigos);
        }
        return String.join(", ", codigos.subList(0, limite)) + " (+" + (codigos.size() - limite) + ")";
    }

    public void lancarEncerramento() {
        if (utilGeralService.getMesContabil() != 12) {
            dialogs.createMessageDialog()
                    .withHeader("Lançamentos de encerramento")
                    .withText("Só é possível lançar o encerramento com o período contábil em dezembro.")
                    .open();
            return;
        }
        ContaContabil contaEncerramento = encerramentoService.resolverContaEncerramento();
        if (contaEncerramento == null) {
            dialogs.createMessageDialog()
                    .withHeader("Lançamentos de encerramento")
                    .withText("Configure a conta de encerramento no cadastro da empresa antes de continuar.")
                    .open();
            return;
        }
        List<EncerramentoService.ContaEncerramento> itens = encerramentoService.prepararLancamentoEncerramento();
        if (itens.isEmpty()) {
            dialogs.createMessageDialog()
                    .withHeader("Lançamentos de encerramento")
                    .withText("Nenhuma conta de resultado com saldo a encerrar.")
                    .open();
            return;
        }

        ConfigRel configRel = utilGeralService.prepararConfigRel();
        Integer historicoAtual = configRel.getHistoricoEncerramento();
        View<?> ownerView = UiComponentUtils.getCurrentView();
        dialogs.createInputDialog(ownerView)
                .withHeader("Lançamentos de encerramento")
                .withParameters(
                        intParameter("historicoEncerramento")
                                .withLabel("Histórico")
                                .withDefaultValue(historicoAtual)
                                .withRequired(true)
                )
                .withActions(DialogActions.OK_CANCEL)
                .withCloseListener(closeEvent -> {
                    if (!closeEvent.closedWith(DialogOutcome.OK)) {
                        return;
                    }
                    Integer codHistorico = closeEvent.getValue("historicoEncerramento");
                    HistoricoContabil historico;
                    try {
                        historico = dataManager.load(HistoricoContabil.class)
                                .query("select e from HistoricoContabil e " +
                                        "where e.codigo = :codigo " +
                                        "and e.codEmpresa = :codEmpresa")
                                .parameter("codigo", codHistorico)
                                .parameter("codEmpresa", utilGeralService.getCodEmpresa())
                                .one();
                    } catch (Exception e) {
                        dialogs.createMessageDialog()
                                .withHeader("Lançamentos de encerramento")
                                .withText("Histórico contábil não encontrado.")
                                .open();
                        return;
                    }
                    SaveContext saveContext = new SaveContext();
                    configRel.setHistoricoEncerramento(codHistorico);
                    saveContext.saving(configRel);
                    dataManager.save(saveContext);

                    dialogs.createBackgroundTaskDialog(
                                    new LancarEncerramentoTask(ownerView, itens, historico, contaEncerramento))
                            .withHeader("Lançamentos de encerramento")
                            .withText("Lançando encerramento...")
                            .withTotal(itens.size())
                            .withShowProgressInPercentage(true)
                            .withCancelAllowed(true)
                            .open();
                })
                .open();
    }

    public void criarProximoExercicio() {
        List<ContaContabil> contas = encerramentoService.prepararCriarProximoExercicio();
        if (contas.isEmpty()) {
            dialogs.createMessageDialog()
                    .withHeader("Criar próximo exercício")
                    .withText("Todas as contas do próximo exercício já foram criadas.")
                    .open();
            return;
        }
        Integer anoSeguinte = utilGeralService.getAnoContabil() + 1;
        View<?> ownerView = UiComponentUtils.getCurrentView();
        dialogs.createOptionDialog()
                .withHeader("Criar próximo exercício")
                .withText("Confirma criar " + contas.size() + " conta(s) no exercício " + anoSeguinte + "?")
                .withActions(
                        new DialogAction(DialogAction.Type.YES)
                                .withHandler(e -> dialogs
                                        .createBackgroundTaskDialog(new CriarProximoExercicioTask(ownerView, contas))
                                        .withHeader("Criar próximo exercício")
                                        .withText("Criando contas...")
                                        .withTotal(contas.size())
                                        .withShowProgressInPercentage(true)
                                        .withCancelAllowed(true)
                                        .open()),
                        new DialogAction(DialogAction.Type.NO)
                )
                .open();
    }

    public void transferirSaldosProximoExercicio() {
        EncerramentoService.PreparoTransferenciaSaldos preparo = encerramentoService.prepararTransferenciaSaldos();
        if (!preparo.contasFaltantes().isEmpty()) {
            dialogs.createMessageDialog()
                    .withHeader("Saldos para o próximo exercício")
                    .withText("Rode \"Criar próximo exercício\" antes — faltam as contas: "
                            + resumirCodigos(preparo.contasFaltantes()))
                    .open();
            return;
        }
        if (preparo.pendentes().isEmpty()) {
            dialogs.createMessageDialog()
                    .withHeader("Saldos para o próximo exercício")
                    .withText("Nenhuma conta para transferir.")
                    .open();
            return;
        }
        View<?> ownerView = UiComponentUtils.getCurrentView();
        dialogs.createOptionDialog()
                .withHeader("Saldos para o próximo exercício")
                .withText("Confirma transferir os saldos de " + preparo.pendentes().size() + " conta(s)?")
                .withActions(
                        new DialogAction(DialogAction.Type.YES)
                                .withHandler(e -> dialogs
                                        .createBackgroundTaskDialog(
                                                new TransferirSaldosTask(ownerView, preparo.pendentes()))
                                        .withHeader("Saldos para o próximo exercício")
                                        .withText("Transferindo saldos...")
                                        .withTotal(preparo.pendentes().size())
                                        .withShowProgressInPercentage(true)
                                        .withCancelAllowed(true)
                                        .open()),
                        new DialogAction(DialogAction.Type.NO)
                )
                .open();
    }

    /**
     * Lança o encerramento item a item — mesmo raciocínio das outras BackgroundTask do
     * projeto (ex.: {@code AssociacaoListView.CopiarAnoAnteriorTask}): nada de UI dentro de
     * {@link #run}, só em {@link #done}/{@link #canceled}. Classe interna (não estática) só
     * pra reaproveitar os serviços/dialogs do {@code MenuBean} — o dono da task pro Jmix
     * continua sendo a view corrente, passada explicitamente pelo construtor.
     */
    protected class LancarEncerramentoTask extends BackgroundTask<Integer, Integer> {

        private final List<EncerramentoService.ContaEncerramento> itens;
        private final HistoricoContabil historico;
        private final ContaContabil contaEncerramento;
        private final AtomicInteger processadas = new AtomicInteger();

        protected LancarEncerramentoTask(View<?> ownerView, List<EncerramentoService.ContaEncerramento> itens,
                                          HistoricoContabil historico, ContaContabil contaEncerramento) {
            super(TIMEOUT_ENCERRAMENTO_MINUTOS, TimeUnit.MINUTES, ownerView);
            this.itens = itens;
            this.historico = historico;
            this.contaEncerramento = contaEncerramento;
        }

        @Override
        public Integer run(TaskLifeCycle<Integer> taskLifeCycle) throws Exception {
            for (EncerramentoService.ContaEncerramento item : itens) {
                if (taskLifeCycle.isCancelled() || taskLifeCycle.isInterrupted()) {
                    break;
                }
                encerramentoService.lancarEncerramentoConta(item, historico, contaEncerramento);
                taskLifeCycle.publish(processadas.incrementAndGet());
            }
            return processadas.get();
        }

        @Override
        public void done(Integer total) {
            dialogs.createMessageDialog()
                    .withHeader("Lançamentos de encerramento")
                    .withText(processadas.get() + " lançamento(s) de encerramento gravado(s).")
                    .open();
        }

        @Override
        public void canceled() {
            dialogs.createMessageDialog()
                    .withHeader("Lançamentos de encerramento")
                    .withText("Cancelado após " + processadas.get() + " lançamento(s).")
                    .open();
        }
    }

    protected class CriarProximoExercicioTask extends BackgroundTask<Integer, Integer> {

        private final List<ContaContabil> contas;
        private final AtomicInteger processadas = new AtomicInteger();

        protected CriarProximoExercicioTask(View<?> ownerView, List<ContaContabil> contas) {
            super(TIMEOUT_ENCERRAMENTO_MINUTOS, TimeUnit.MINUTES, ownerView);
            this.contas = contas;
        }

        @Override
        public Integer run(TaskLifeCycle<Integer> taskLifeCycle) throws Exception {
            for (ContaContabil conta : contas) {
                if (taskLifeCycle.isCancelled() || taskLifeCycle.isInterrupted()) {
                    break;
                }
                encerramentoService.criarContaProximoExercicio(conta);
                taskLifeCycle.publish(processadas.incrementAndGet());
            }
            return processadas.get();
        }

        @Override
        public void done(Integer total) {
            dialogs.createMessageDialog()
                    .withHeader("Criar próximo exercício")
                    .withText(processadas.get() + " conta(s) criada(s) no próximo exercício.")
                    .open();
        }

        @Override
        public void canceled() {
            dialogs.createMessageDialog()
                    .withHeader("Criar próximo exercício")
                    .withText("Cancelado após " + processadas.get() + " conta(s) criada(s).")
                    .open();
        }
    }

    protected class TransferirSaldosTask extends BackgroundTask<Integer, Integer> {

        private final List<EncerramentoService.TransferenciaSaldo> pendentes;
        private final AtomicInteger processadas = new AtomicInteger();

        protected TransferirSaldosTask(View<?> ownerView, List<EncerramentoService.TransferenciaSaldo> pendentes) {
            super(TIMEOUT_ENCERRAMENTO_MINUTOS, TimeUnit.MINUTES, ownerView);
            this.pendentes = pendentes;
        }

        @Override
        public Integer run(TaskLifeCycle<Integer> taskLifeCycle) throws Exception {
            for (EncerramentoService.TransferenciaSaldo pendente : pendentes) {
                if (taskLifeCycle.isCancelled() || taskLifeCycle.isInterrupted()) {
                    break;
                }
                encerramentoService.transferirSaldoConta(pendente);
                taskLifeCycle.publish(processadas.incrementAndGet());
            }
            return processadas.get();
        }

        @Override
        public void done(Integer total) {
            dialogs.createMessageDialog()
                    .withHeader("Saldos para o próximo exercício")
                    .withText(processadas.get() + " conta(s) com saldo transferido.")
                    .open();
        }

        @Override
        public void canceled() {
            dialogs.createMessageDialog()
                    .withHeader("Saldos para o próximo exercício")
                    .withText("Cancelado após " + processadas.get() + " conta(s).")
                    .open();
        }
    }
}