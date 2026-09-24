package br.com.axialsoftware.axctg3.view.fiscal.pedidovenda;

import br.com.axialsoftware.axctg3.bean.MenuBean;
import br.com.axialsoftware.axctg3.entity.cadastros.ConfigRel;
import br.com.axialsoftware.axctg3.entity.fiscal.PedidoVenda;
import br.com.axialsoftware.axctg3.service.UtilGeralService;
import br.com.axialsoftware.axctg3.service.fiscal.PedidoVendaService;
import br.com.axialsoftware.axctg3.view.main.MainView;

import com.vaadin.flow.router.Route;
import io.jmix.core.DataManager;
import io.jmix.core.SaveContext;
import io.jmix.flowui.Dialogs;
import io.jmix.flowui.app.inputdialog.DialogActions;
import io.jmix.flowui.app.inputdialog.DialogOutcome;
import io.jmix.flowui.component.UiComponentUtils;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.kit.action.ActionPerformedEvent;
import io.jmix.flowui.model.CollectionLoader;
import io.jmix.flowui.view.*;

import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.Optional;

import static io.jmix.flowui.app.inputdialog.InputParameter.localDateParameter;

@Route(value = "pedido-vendas", layout = MainView.class)
@ViewController(id = "PedidoVenda.list")
@ViewDescriptor(path = "pedido-venda-list-view.xml")
@LookupComponent("pedidoVendasDataGrid")
@DialogMode(width = "64em")
public class PedidoVendaListView extends StandardListView<PedidoVenda> {

    @ViewComponent
    private CollectionLoader<PedidoVenda> pedidoVendasDl;
    @ViewComponent
    private DataGrid<PedidoVenda> pedidoVendasDataGrid;
    @ViewComponent
    private MessageBundle messageBundle;
    @Autowired
    private UtilGeralService utilGeralService;
    @Autowired
    private MenuBean menuBean;
    @Autowired
    private PedidoVendaService pedidoVendaService;
    @Autowired
    private Dialogs dialogs;
    @Autowired
    private DataManager dataManager;

    // Período delimitado (ConfigRel, por usuário) — mesmo padrão de NotaSaidaListView e
    // das listas do financeiro; sem período gravado, vale o dia de hoje.
    private LocalDate dataEntradaInicial;
    private LocalDate dataEntradaFinal;

    @Subscribe
    public void onBeforeShow(final BeforeShowEvent event) {
        ConfigRel configRel = utilGeralService.prepararConfigRel();
        dataEntradaInicial = Optional.ofNullable(configRel.getDataEntradaPedidoVendaInicial()).orElse(LocalDate.now());
        dataEntradaFinal = Optional.ofNullable(configRel.getDataEntradaPedidoVendaFinal()).orElse(LocalDate.now());
        carregarPedidos();
    }

    @Override
    public String getPageTitle() {
        return super.getPageTitle() + utilGeralService.formatIntervaloTitulo("entrada", dataEntradaInicial, dataEntradaFinal);
    }

    private void carregarPedidos() {
        pedidoVendasDl.setParameter("dataEntradaInicial", dataEntradaInicial);
        pedidoVendasDl.setParameter("dataEntradaFinal", dataEntradaFinal);
        pedidoVendasDl.setParameter("codEmpresa", utilGeralService.getCodEmpresa());
        pedidoVendasDl.load();
    }

    @Subscribe("pedidoVendasDataGrid.delimitarAction")
    public void onPedidoVendasDataGridDelimitarAction(final ActionPerformedEvent event) {
        ConfigRel configRel = utilGeralService.prepararConfigRel();
        dialogs.createInputDialog(UiComponentUtils.getCurrentView())
                .withHeader("Pedidos de venda")
                .withParameters(
                        localDateParameter("dataEntradaInicial")
                                .withLabel("Data entrada inicial")
                                .withDefaultValue(dataEntradaInicial),
                        localDateParameter("dataEntradaFinal")
                                .withLabel("Data entrada final")
                                .withDefaultValue(dataEntradaFinal)
                )
                .withActions(DialogActions.OK_CANCEL)
                .withCloseListener(closeEvent -> {
                    if (closeEvent.closedWith(DialogOutcome.OK)) {
                        configRel.setDataEntradaPedidoVendaInicial(closeEvent.getValue("dataEntradaInicial"));
                        configRel.setDataEntradaPedidoVendaFinal(closeEvent.getValue("dataEntradaFinal"));
                        dataManager.save(new SaveContext().saving(configRel));
                        dataEntradaInicial = Optional.ofNullable(configRel.getDataEntradaPedidoVendaInicial()).orElse(LocalDate.now());
                        dataEntradaFinal = Optional.ofNullable(configRel.getDataEntradaPedidoVendaFinal()).orElse(LocalDate.now());
                        carregarPedidos();
                        // setPageTitle() (não UI.getPage().setTitle()) — só ele atualiza o H1
                        // do cabeçalho, ver TituloReceberListView.
                        setPageTitle(getPageTitle());
                    }
                })
                .open();
    }

    @Subscribe("pedidoVendasDataGrid.listagemAction")
    public void onPedidoVendasDataGridListagemAction(final ActionPerformedEvent event) {
        menuBean.listarPedidosVenda(pedidoVendasDataGrid.getSingleSelectedItem());
    }

    /**
     * Cliente que não separa pedido de nota lança a {@code NotaSaida} direto — este botão
     * é só pro cliente que fecha o pedido primeiro e emite a nota (com numeração própria,
     * via {@code nota_saida_seq}) dias depois. De propósito NÃO navega pra
     * {@code NotaSaidaListView}/emite a NFe em seguida — fica um passo manual e posterior,
     * pra o operador poder emitir várias notas primeiro e mandar os NFes depois.
     */
    @Subscribe("pedidoVendasDataGrid.emitirNotaSaidaAction")
    public void onPedidoVendasDataGridEmitirNotaSaidaAction(final ActionPerformedEvent event) {
        PedidoVenda selecionado = pedidoVendasDataGrid.getSingleSelectedItem();
        if (selecionado == null) {
            dialogs.createMessageDialog()
                    .withHeader(messageBundle.getMessage("pedidoVendaListView.emitirNotaSaidaAction.text"))
                    .withText(messageBundle.getMessage("pedidoVendaListView.emitirNotaSaida.naoSelecionado"))
                    .open();
            return;
        }
        PedidoVendaService.ResultadoEmitirNotaSaida resultado = pedidoVendaService.emitirNotaSaida(selecionado.getId());
        if (resultado.sucesso()) {
            dialogs.createMessageDialog()
                    .withHeader(messageBundle.getMessage("pedidoVendaListView.emitirNotaSaida.sucesso.header"))
                    .withText(messageBundle.formatMessage("pedidoVendaListView.emitirNotaSaida.sucesso.text",
                            resultado.notaSaida().getNumero()))
                    .open();
        } else {
            dialogs.createMessageDialog()
                    .withHeader(messageBundle.getMessage("pedidoVendaListView.emitirNotaSaida.erro.header"))
                    .withText(messageBundle.formatMessage("pedidoVendaListView.emitirNotaSaida.erro.text", resultado.motivo()))
                    .open();
        }
    }
}
