package br.com.axialsoftware.axctg3.view.fiscal.itemnotasaida;

import br.com.axialsoftware.axctg3.entity.fiscal.ItemNotaSaida;
import br.com.axialsoftware.axctg3.entity.fiscal.NaturezaOperacao;
import br.com.axialsoftware.axctg3.entity.fiscal.NotaSaida;
import br.com.axialsoftware.axctg3.entity.fiscal.Produto;
import br.com.axialsoftware.axctg3.service.UtilGeralService;
import br.com.axialsoftware.axctg3.service.fiscal.ItemNotaSaidaTributacaoService;
import br.com.axialsoftware.axctg3.view.main.MainView;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.model.CollectionLoader;
import io.jmix.flowui.model.InstanceContainer;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Set;

@Route(value = "item-nota-saidas/:id", layout = MainView.class)
@ViewController(id = "ItemNotaSaida.detail")
@ViewDescriptor(path = "item-nota-saida-detail-view.xml")
@EditedEntityContainer("itemNotaSaidaDc")
@DialogMode(width = "80%")
public class ItemNotaSaidaDetailView extends StandardDetailView<ItemNotaSaida> {

    // Propriedades que, ao mudar, exigem recalcular a prévia de CST/ICMS na tela — antes
    // de salvar o item de verdade (quando ItemNotaSaidaEventListener grava pra valer).
    private static final Set<String> PROPRIEDADES_QUE_AFETAM_ICMS = Set.of("quantidade", "valorUnitario", "produto");

    @Autowired
    private ItemNotaSaidaTributacaoService tributacaoService;
    @Autowired
    private UtilGeralService utilGeralService;
    @ViewComponent
    private CollectionLoader<Produto> produtosDl;

    /**
     * Carrega manualmente o itemsContainer de produtoField (entityComboBox, troca de
     * entityPicker) — dataLoadCoordinator auto="true" NÃO carrega sozinho um loader com
     * parâmetro "solto" como :codEmpresa; precisa chamar .load() na mão (ver memória
     * entitycombobox-toggle-nao-abre).
     */
    @Subscribe
    public void onBeforeShow(final BeforeShowEvent event) {
        produtosDl.setParameter("codEmpresa", utilGeralService.getCodEmpresa());
        produtosDl.load();
    }

    /**
     * Pré-preenche o CFOP na inclusão a partir de {@code NotaSaida.natureza.cfop} — o
     * container do item já é {@code Nested} (collection property-bound de {@code
     * itensDc}), então o framework seta {@code item.notaSaida} antes de disparar este
     * evento (confirmado em {@code DetailWindowBuilderProcessor.initNewEntity}). Continua
     * editável; só evita o operador redigitar o CFOP que já é o padrão da natureza em
     * quase todo item.
     */
    @Subscribe
    public void onInitEntity(final InitEntityEvent<ItemNotaSaida> event) {
        ItemNotaSaida item = event.getEntity();
        NotaSaida notaSaida = item.getNotaSaida();
        if (item.getCfop() == null && notaSaida != null && notaSaida.getNatureza() != null) {
            item.setCfop(notaSaida.getNatureza().getCfop());
        }
    }

    /**
     * Preview ao vivo de CST/base/alíquota/valor do ICMS enquanto o usuário digita —
     * sem isso, esses campos (read-only) ficam em branco até o primeiro Salvar, porque a
     * gravação de verdade só acontece em {@code ItemNotaSaidaEventListener.
     * onItemNotaSaidaSaving}. Mesmo cálculo, mesma precedência natureza/produto — só que
     * aqui é preview, não persiste nada (quem persiste continua sendo o listener no
     * save). Reavaliado a cada mudança de quantidade/valorUnitario/produto.
     */
    @Subscribe(id = "itemNotaSaidaDc", target = Target.DATA_CONTAINER)
    public void onItemNotaSaidaDcItemPropertyChange(
            final InstanceContainer.ItemPropertyChangeEvent<ItemNotaSaida> event) {
        if (!PROPRIEDADES_QUE_AFETAM_ICMS.contains(event.getProperty())) {
            return;
        }
        ItemNotaSaida item = event.getItem();
        NotaSaida notaSaida = item.getNotaSaida();
        NaturezaOperacao natureza = notaSaida == null ? null : notaSaida.getNatureza();
        Produto produto = item.getProduto();

        if (item.getCst() == null) {
            String cst = tributacaoService.resolverCstIcms(natureza, produto);
            if (cst != null) {
                item.setCst(cst);
            }
        }
        // Mesma precedência natureza/produto do CST (ver reforma-tributaria-cclasstrib-
        // precedencia), preview em vez de só resolver no save (ItemNotaSaidaEventListener.
        // onItemNotaSaidaSaving já faz isso, mas só depois de salvar).
        if (item.getCodClassTrib() == null) {
            Integer codClassTrib = tributacaoService.resolverCodClassTrib(natureza, produto);
            if (codClassTrib != null) {
                item.setCodClassTrib(codClassTrib);
            }
        }
        tributacaoService.aplicarCalculoIcms(item, natureza, produto);
    }
}
