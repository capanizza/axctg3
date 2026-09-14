package br.com.axialsoftware.axctg3.view.fiscal.itemnotasaida;

import br.com.axialsoftware.axctg3.entity.fiscal.ItemNotaSaida;
import br.com.axialsoftware.axctg3.entity.fiscal.NaturezaOperacao;
import br.com.axialsoftware.axctg3.entity.fiscal.NotaSaida;
import br.com.axialsoftware.axctg3.entity.fiscal.Produto;
import br.com.axialsoftware.axctg3.service.fiscal.ItemNotaSaidaTributacaoService;
import br.com.axialsoftware.axctg3.view.main.MainView;
import com.vaadin.flow.router.Route;
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
        tributacaoService.aplicarCalculoIcms(item, natureza, produto);
    }
}
