package br.com.axialsoftware.axctg3.view.fiscal.itempedidovenda;

import br.com.axialsoftware.axctg3.entity.fiscal.ItemPedidoVenda;
import br.com.axialsoftware.axctg3.entity.fiscal.NaturezaOperacao;
import br.com.axialsoftware.axctg3.entity.fiscal.PedidoVenda;
import br.com.axialsoftware.axctg3.entity.fiscal.Produto;
import br.com.axialsoftware.axctg3.service.UtilGeralService;
import br.com.axialsoftware.axctg3.service.fiscal.ItemNotaSaidaTributacaoService;
import br.com.axialsoftware.axctg3.view.main.MainView;
import com.vaadin.flow.router.Route;
import io.jmix.core.EntityStates;
import io.jmix.flowui.model.CollectionLoader;
import io.jmix.flowui.model.InstanceContainer;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;

@Route(value = "item-pedido-vendas/:id", layout = MainView.class)
@ViewController(id = "ItemPedidoVenda.detail")
@ViewDescriptor(path = "item-pedido-venda-detail-view.xml")
@EditedEntityContainer("itemPedidoVendaDc")
public class ItemPedidoVendaDetailView extends StandardDetailView<ItemPedidoVenda> {

    @Autowired
    private UtilGeralService utilGeralService;
    @Autowired
    private ItemNotaSaidaTributacaoService tributacaoService;
    @Autowired
    private EntityStates entityStates;

    // Mesmo controle do ItemNotaSaidaDetailView: CST sugerido x digitado pelo operador.
    private boolean atualizandoCst;
    private boolean cstDigitado;
    @ViewComponent
    private CollectionLoader<Produto> produtosDl;

    @Subscribe
    public void onBeforeShow(final BeforeShowEvent event) {
        produtosDl.setParameter("codEmpresa", utilGeralService.getCodEmpresa());
        produtosDl.load();
    }

    /**
     * CST/cClassTrib pré-preenchidos, mesma precedência do item da nota (cabeçalho →
     * natureza → produto, ver ItemNotaSaidaTributacaoService) — na abertura com o que o
     * cabeçalho/natureza decidem sozinhos, e de novo quando o produto/CFOP muda. O
     * pedido continua sem cálculo de ICMS.
     */
    @Subscribe
    public void onInitEntity(final InitEntityEvent<ItemPedidoVenda> event) {
        ItemPedidoVenda item = event.getEntity();
        // CFOP padrão da natureza, igual ao item da nota (ItemNotaSaidaDetailView) —
        // continua editável.
        PedidoVenda pedido = item.getPedidoVenda();
        if (item.getCfop() == null && pedido != null && pedido.getNatureza() != null) {
            item.setCfop(pedido.getNatureza().getCfop());
        }
        resolverTributacao(item, false);
        item.setForaDoTotal(tributacaoService.foraDoTotalPorPadrao(pedido, item.getCfop()));
    }

    // Item novo: CFOP/produto mudam o CST/CSOSN sugerido (no Simples, a regra do legado —
    // ver ItemNotaSaidaTributacaoService.resolverCstItem) até o operador digitar o CST.
    @Subscribe(id = "itemPedidoVendaDc", target = Target.DATA_CONTAINER)
    public void onItemPedidoVendaDcItemPropertyChange(
            final InstanceContainer.ItemPropertyChangeEvent<ItemPedidoVenda> event) {
        ItemPedidoVenda item = event.getItem();
        String propriedade = event.getProperty();
        if ("cst".equals(propriedade)) {
            if (!atualizandoCst) {
                cstDigitado = true;
            }
            return;
        }
        if (!"produto".equals(propriedade) && !"cfop".equals(propriedade)) {
            return;
        }
        boolean novo = entityStates.isNew(item);
        resolverTributacao(item, novo && !cstDigitado);
        if (novo && "cfop".equals(propriedade)) {
            item.setForaDoTotal(tributacaoService.foraDoTotalPorPadrao(item.getPedidoVenda(), item.getCfop()));
        }
    }

    private void resolverTributacao(ItemPedidoVenda item, boolean recalcularCst) {
        PedidoVenda pedido = item.getPedidoVenda();
        NaturezaOperacao natureza = pedido == null ? null : pedido.getNatureza();
        Produto produto = item.getProduto();
        if (item.getCst() == null || recalcularCst) {
            String cst = tributacaoService.resolverCstItem(natureza, produto, pedido, item.getCfop(),
                    primeiroItem(pedido, item));
            if (cst != null) {
                atualizandoCst = true;
                try {
                    item.setCst(cst);
                } finally {
                    atualizandoCst = false;
                }
            }
        }
        if (item.getCodClassTrib() == null) {
            Integer codClassTrib = tributacaoService.resolverCodClassTrib(
                    pedido == null ? null : pedido.getClassTrib(), natureza, produto);
            if (codClassTrib != null) {
                item.setCodClassTrib(codClassTrib);
            }
        }
    }

    // Itens do pedido em memória (os da tela do pedido); pedido sem itens carregados é
    // tratado como vazio — a conversão em nota refaz a regra do 1º item se o CST vier vazio.
    private boolean primeiroItem(PedidoVenda pedido, ItemPedidoVenda item) {
        if (pedido == null || !entityStates.isLoaded(pedido, "itens") || pedido.getItens() == null) {
            return true;
        }
        return pedido.getItens().stream().noneMatch(outro -> !outro.equals(item));
    }
}
