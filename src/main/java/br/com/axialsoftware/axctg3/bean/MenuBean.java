package br.com.axialsoftware.axctg3.bean;

import br.com.axialsoftware.axctg3.entity.cadastros.ConfigRel;
import br.com.axialsoftware.axctg3.entity.contabil.ContaContabil;
import br.com.axialsoftware.axctg3.entity.financeiro.Banco;
import br.com.axialsoftware.axctg3.service.UtilGeralService;
import br.com.axialsoftware.axctg3.service.contabil.ContaContabilService;
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
    private final DiversoPagarService diversoPagarService;
    private final ItemDiversoPagarService itemDiversoPagarService;
    private final TituloReceberService tituloReceberService;
    private final ItemReceberService itemReceberService;
    private final TituloPagarService tituloPagarService;
    private final ItemPagarService itemPagarService;
    private final MovimentoBancoService movimentoBancoService;

    public MenuBean(UtilGeralService utilGeralService, Dialogs dialogs, ContaContabilService contaContabilService, DataManager dataManager, LancamentoService lancamentoService, DiversoPagarService diversoPagarService, ItemDiversoPagarService itemDiversoPagarService, TituloReceberService tituloReceberService, ItemReceberService itemReceberService, TituloPagarService tituloPagarService, ItemPagarService itemPagarService, MovimentoBancoService movimentoBancoService) {
        this.utilGeralService = utilGeralService;
        this.dialogs = dialogs;
        this.contaContabilService = contaContabilService;
        this.dataManager = dataManager;
        this.lancamentoService = lancamentoService;
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
}