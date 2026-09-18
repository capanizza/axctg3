package br.com.axialsoftware.axctg3.view.fiscal.pedidovenda;

import br.com.axialsoftware.axctg3.bean.MenuBean;
import br.com.axialsoftware.axctg3.entity.fiscal.PedidoVenda;
import br.com.axialsoftware.axctg3.service.UtilGeralService;
import br.com.axialsoftware.axctg3.service.fiscal.PedidoVendaService;
import br.com.axialsoftware.axctg3.view.main.MainView;

import com.vaadin.flow.router.Route;
import io.jmix.flowui.Dialogs;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.kit.action.ActionPerformedEvent;
import io.jmix.flowui.model.CollectionLoader;
import io.jmix.flowui.view.*;

import org.springframework.beans.factory.annotation.Autowired;

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

    @Subscribe
    public void onBeforeShow(final BeforeShowEvent event) {
        pedidoVendasDl.setParameter("codEmpresa", utilGeralService.getCodEmpresa());
        pedidoVendasDl.load();
    }

    @Subscribe("pedidoVendasDataGrid.listagemAction")
    public void onPedidoVendasDataGridListagemAction(final ActionPerformedEvent event) {
        menuBean.listarPedidosVenda();
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
