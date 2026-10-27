package br.com.axialsoftware.axctg3.view.fiscal.itemnotasaida;

import br.com.axialsoftware.axctg3.entity.fiscal.ItemNotaSaida;
import br.com.axialsoftware.axctg3.entity.fiscal.NaturezaOperacao;
import br.com.axialsoftware.axctg3.entity.fiscal.NotaSaida;
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

import java.util.Set;

@Route(value = "item-nota-saidas/:id", layout = MainView.class)
@ViewController(id = "ItemNotaSaida.detail")
@ViewDescriptor(path = "item-nota-saida-detail-view.xml")
@EditedEntityContainer("itemNotaSaidaDc")
@DialogMode(width = "80%")
public class ItemNotaSaidaDetailView extends StandardDetailView<ItemNotaSaida> {

    // Propriedades que, ao mudar, exigem recalcular a prévia de CST/ICMS na tela — antes
    // de salvar o item de verdade (quando ItemNotaSaidaEventListener grava pra valer).
    private static final Set<String> PROPRIEDADES_QUE_AFETAM_ICMS =
            Set.of("quantidade", "valorUnitario", "produto", "cfop", "cst");

    @Autowired
    private ItemNotaSaidaTributacaoService tributacaoService;
    @Autowired
    private UtilGeralService utilGeralService;
    @Autowired
    private EntityStates entityStates;

    // CST sugerido pela tela (true enquanto ela mesma o escreve) x digitado pelo operador:
    // depois de digitado, troca de CFOP/produto não sobrescreve mais.
    private boolean atualizandoCst;
    private boolean cstDigitado;
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
        // CST/cClassTrib já na abertura quando o cabeçalho/natureza decidem sozinhos
        // (ex.: doação — CST 41 na natureza, 410999 no cabeçalho), sem esperar o
        // produto; o que ficar em branco aqui é resolvido quando o produto for escolhido.
        resolverTributacao(item, false);
        item.setForaDoTotal(tributacaoService.foraDoTotalPorPadrao(notaSaida, item.getCfop()));
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
        String propriedade = event.getProperty();
        if ("cst".equals(propriedade)) {
            if (!atualizandoCst) {
                cstDigitado = true;
            }
        } else {
            // Item novo: CFOP/produto mudam o CSOSN sugerido (Simples) até o operador
            // digitar o CST; em item já gravado só preenche o que estiver em branco.
            boolean novo = entityStates.isNew(item);
            resolverTributacao(item, novo && !cstDigitado
                    && ("cfop".equals(propriedade) || "produto".equals(propriedade)));
            if (novo && "cfop".equals(propriedade)) {
                item.setForaDoTotal(tributacaoService.foraDoTotalPorPadrao(item.getNotaSaida(), item.getCfop()));
            }
        }
        NotaSaida notaSaida = item.getNotaSaida();
        NaturezaOperacao natureza = notaSaida == null ? null : notaSaida.getNatureza();
        tributacaoService.aplicarCalculoIcms(item, natureza, item.getProduto());
    }

    // Mesma precedência do save (ItemNotaSaidaEventListener.onItemNotaSaidaSaving) —
    // cabeçalho/natureza/produto e, no Simples, a regra do CSOSN do legado (ver
    // ItemNotaSaidaTributacaoService.resolverCstItem). Só preenche o que está em branco,
    // a não ser com recalcularCst (item novo cujo CST o operador ainda não digitou).
    private void resolverTributacao(ItemNotaSaida item, boolean recalcularCst) {
        NotaSaida notaSaida = item.getNotaSaida();
        NaturezaOperacao natureza = notaSaida == null ? null : notaSaida.getNatureza();
        Produto produto = item.getProduto();
        if (item.getCst() == null || recalcularCst) {
            String cst = tributacaoService.resolverCstItem(natureza, produto, notaSaida, item.getCfop(),
                    primeiroItem(notaSaida, item));
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
                    notaSaida == null ? null : notaSaida.getClassTrib(), natureza, produto);
            if (codClassTrib != null) {
                item.setCodClassTrib(codClassTrib);
            }
        }
    }

    // Itens da nota em memória (os da tela da nota, ainda não gravados, contam); sem eles,
    // pergunta ao banco.
    private boolean primeiroItem(NotaSaida notaSaida, ItemNotaSaida item) {
        if (notaSaida != null && entityStates.isLoaded(notaSaida, "itens") && notaSaida.getItens() != null) {
            return notaSaida.getItens().stream().noneMatch(outro -> !outro.equals(item));
        }
        return tributacaoService.primeiroItem(notaSaida, item);
    }
}
