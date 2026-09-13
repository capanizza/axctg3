package br.com.axialsoftware.axctg3.view.fiscal.notasaida;

import br.com.axialsoftware.axctg3.entity.fiscal.NotaSaida;
import br.com.axialsoftware.axctg3.entity.fiscal.NotaSaidaComplementarValores;
import br.com.axialsoftware.axctg3.view.main.MainView;
import com.vaadin.flow.router.Route;
import io.jmix.core.DataManager;
import io.jmix.flowui.model.DataContext;
import io.jmix.flowui.model.InstanceContainer;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Tela dedicada de entrada de dados de uma NFe complementar (finNFe=2) — subconjunto
 * enxuto de {@link NotaSaidaDetailView}, aberta só a partir de {@code
 * NotaSaidaListView.onNotaSaidasDataGridEmitirNfeComplementarAction}. Natureza de
 * operação, classificação tributária e cliente vêm travados (herdados da nota
 * original); o operador só edita a data de emissão e os valores que efetivamente
 * mudam numa complementar. Sem aba de itens: o pseudo item é gerado sozinho por
 * {@code NfeEmissaoService.gerarItemComplementar} na hora de emitir — ver o Javadoc de
 * lá pra saber por que a geração acontece na emissão, não no save desta tela.
 *
 * <p><b>Decisão de 2026-09-13:</b> os valores fiscais (mercadoria/ICMS/IPI — sem ST,
 * nenhum cliente ativo usa) não ficam mais no cabeçalho de {@link NotaSaida} (que
 * parou de guardar valor calculado nenhum) — moraram pra {@link
 * NotaSaidaComplementarValores}, uma entidade satélite 1:1 editada junto nesta mesma
 * tela. {@code valoresDc} é um {@code InstanceContainer} sem loader próprio
 * (populado aqui via {@link #onReady}, não por query declarativa no XML — a linha
 * pode não existir ainda numa complementar nova) e mesclado no {@code DataContext} da
 * view pra ser salvo junto com {@code notaSaidaDc} num único clique em "Salvar".
 */
@Route(value = "nota-saidas-complementar/:id", layout = MainView.class)
@ViewController(id = "NotaSaida.complementar")
@ViewDescriptor(path = "nota-saida-complementar-detail-view.xml")
@EditedEntityContainer("notaSaidaDc")
public class NotaSaidaComplementarDetailView extends StandardDetailView<NotaSaida> {

    @ViewComponent
    private InstanceContainer<NotaSaidaComplementarValores> valoresDc;
    @Autowired
    private DataManager dataManager;

    @Subscribe
    public void onReady(final ReadyEvent event) {
        NotaSaida notaSaida = getEditedEntity();
        NotaSaidaComplementarValores valores = dataManager.load(NotaSaidaComplementarValores.class)
                .query("select e from NotaSaidaComplementarValores e where e.notaSaida = :notaSaida")
                .parameter("notaSaida", notaSaida)
                .optional()
                .orElseGet(() -> {
                    NotaSaidaComplementarValores nova = dataManager.create(NotaSaidaComplementarValores.class);
                    nova.setNotaSaida(notaSaida);
                    return nova;
                });
        DataContext dataContext = getViewData().getDataContext();
        valoresDc.setItem(dataContext.merge(valores));
    }
}
