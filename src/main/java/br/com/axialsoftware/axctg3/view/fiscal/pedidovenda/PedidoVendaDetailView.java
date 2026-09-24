package br.com.axialsoftware.axctg3.view.fiscal.pedidovenda;

import br.com.axialsoftware.axctg3.entity.cadastros.CondicaoPagamento;
import br.com.axialsoftware.axctg3.entity.cadastros.Mensagem;
import br.com.axialsoftware.axctg3.entity.cadastros.Parceiro;
import br.com.axialsoftware.axctg3.entity.cadastros.Transportadora;
import br.com.axialsoftware.axctg3.entity.cadastros.Vendedor;
import br.com.axialsoftware.axctg3.entity.financeiro.Banco;
import br.com.axialsoftware.axctg3.entity.fiscal.NaturezaOperacao;
import br.com.axialsoftware.axctg3.entity.fiscal.PedidoVenda;
import br.com.axialsoftware.axctg3.entity.tabelas.ClassTrib;
import br.com.axialsoftware.axctg3.service.UtilGeralService;
import br.com.axialsoftware.axctg3.service.fiscal.ItemNotaSaidaTributacaoService;
import br.com.axialsoftware.axctg3.view.main.MainView;
import com.vaadin.flow.data.provider.Query;
import com.vaadin.flow.router.Route;
import io.jmix.core.DataManager;
import io.jmix.flowui.model.CollectionLoader;
import io.jmix.flowui.model.InstanceContainer;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.stream.Stream;

@Route(value = "pedido-vendas/:id", layout = MainView.class)
@ViewController(id = "PedidoVenda.detail")
@ViewDescriptor(path = "pedido-venda-detail-view.xml")
@EditedEntityContainer("pedidoVendaDc")
public class PedidoVendaDetailView extends StandardDetailView<PedidoVenda> {

    @Autowired
    private UtilGeralService utilGeralService;
    @Autowired
    private ItemNotaSaidaTributacaoService tributacaoService;
    @Autowired
    private DataManager dataManager;
    @ViewComponent
    private CollectionLoader<NaturezaOperacao> naturezaOperacoesDl;
    @ViewComponent
    private CollectionLoader<CondicaoPagamento> condicoesPagamentoDl;
    @ViewComponent
    private CollectionLoader<Banco> bancosDl;
    @ViewComponent
    private CollectionLoader<Transportadora> transportadorasDl;
    @ViewComponent
    private CollectionLoader<Vendedor> vendedoresDl;
    @ViewComponent
    private CollectionLoader<Mensagem> mensagensDl;

    /**
     * Busca preguiçosa dos parceiros de {@code parceiroField}, mesmo padrão de
     * {@code NotaSaidaDetailView.parceiroFieldItemsFetchCallback} — nunca carrega a
     * lista inteira de clientes de uma vez.
     */
    @Install(to = "parceiroField", subject = "itemsFetchCallback")
    private Stream<Parceiro> parceiroFieldItemsFetchCallback(final Query<Parceiro, String> query) {
        String texto = query.getFilter().orElse("");
        return dataManager.load(Parceiro.class)
                .query("select e from Parceiro e where e.codEmpresa = :codEmpresa and e.cliente = true "
                        + "and upper(e.nome) like upper(concat('%', :texto, '%')) order by e.nome")
                .parameter("codEmpresa", utilGeralService.getCodEmpresa())
                .parameter("texto", texto)
                .firstResult(query.getOffset())
                .maxResults(query.getLimit())
                .list()
                .stream();
    }

    /**
     * Carrega manualmente os itemsContainer de natureza/condicaoPagamento/banco/
     * transportadora/vendedor/mensagem (entityComboBox) — {@code dataLoadCoordinator
     * auto="true"} não carrega sozinho um loader com parâmetro "solto" como
     * {@code :codEmpresa} (ver memória entitycombobox-toggle-nao-abre). classTribField
     * usa classTribsDc (tabela global, sem parâmetro), carregada automaticamente.
     */
    @Subscribe
    public void onBeforeShow(final BeforeShowEvent event) {
        Integer codEmpresa = utilGeralService.getCodEmpresa();
        naturezaOperacoesDl.setParameter("codEmpresa", codEmpresa);
        naturezaOperacoesDl.load();

        condicoesPagamentoDl.setParameter("codEmpresa", codEmpresa);
        condicoesPagamentoDl.load();

        bancosDl.setParameter("codEmpresa", codEmpresa);
        bancosDl.load();

        transportadorasDl.setParameter("codEmpresa", codEmpresa);
        transportadorasDl.load();

        vendedoresDl.setParameter("codEmpresa", codEmpresa);
        vendedoresDl.load();

        mensagensDl.setParameter("codEmpresa", codEmpresa);
        mensagensDl.load();
    }

    /**
     * Ao escolher a natureza, o cClassTrib do cabeçalho vem com o dela (quando a natureza
     * tem um cadastrado) — continua editável. Pedido do usuário 2026-09-24: o cabeçalho
     * é quem decide o cClassTrib dos itens quando não é o de tributação integral (ver
     * ItemNotaSaidaTributacaoService.resolverCodClassTrib).
     */
    @Subscribe(id = "pedidoVendaDc", target = Target.DATA_CONTAINER)
    public void onPedidoVendaDcItemPropertyChange(final InstanceContainer.ItemPropertyChangeEvent<PedidoVenda> event) {
        if (!"natureza".equals(event.getProperty())) {
            return;
        }
        ClassTrib classTrib = tributacaoService.classTribDaNatureza(event.getItem().getNatureza());
        if (classTrib != null) {
            event.getItem().setClassTrib(getViewData().getDataContext().merge(classTrib));
        }
    }
}
