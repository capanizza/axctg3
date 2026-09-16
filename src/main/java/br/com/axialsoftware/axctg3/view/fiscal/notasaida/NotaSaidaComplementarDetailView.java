package br.com.axialsoftware.axctg3.view.fiscal.notasaida;

import br.com.axialsoftware.axctg3.entity.cadastros.Parceiro;
import br.com.axialsoftware.axctg3.entity.fiscal.NaturezaOperacao;
import br.com.axialsoftware.axctg3.entity.fiscal.NotaSaida;
import br.com.axialsoftware.axctg3.service.UtilGeralService;
import br.com.axialsoftware.axctg3.view.main.MainView;
import com.vaadin.flow.data.provider.Query;
import com.vaadin.flow.router.Route;
import io.jmix.core.DataManager;
import io.jmix.flowui.model.CollectionLoader;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.stream.Stream;

/**
 * Tela dedicada de entrada de dados de uma NFe complementar (finNFe=2) — subconjunto
 * enxuto de {@link NotaSaidaDetailView}, aberta só a partir de {@code
 * NotaSaidaListView.onNotaSaidasDataGridEmitirNfeComplementarAction}. Natureza de
 * operação, classificação tributária e cliente vêm travados (herdados da nota
 * original); o operador só edita a data de emissão e os valores que efetivamente
 * mudam numa complementar (mercadoria/ICMS/ST/IPI + frete/seguro/desconto/despesas).
 * Sem aba de itens: o pseudo item é gerado sozinho por {@code
 * NfeEmissaoService.gerarItemComplementar} na hora de emitir — ver o Javadoc de lá
 * pra saber por que a geração acontece na emissão, não no save desta tela.
 */
@Route(value = "nota-saidas-complementar/:id", layout = MainView.class)
@ViewController(id = "NotaSaida.complementar")
@ViewDescriptor(path = "nota-saida-complementar-detail-view.xml")
@EditedEntityContainer("notaSaidaDc")
public class NotaSaidaComplementarDetailView extends StandardDetailView<NotaSaida> {

    @Autowired
    private UtilGeralService utilGeralService;
    @Autowired
    private DataManager dataManager;

    /**
     * Parâmetro do itemsContainer de naturezaField (entityComboBox, troca de
     * entityPicker) — codEmpresa da sessão.
     */
    @Subscribe(id = "naturezaOperacoesDl", target = Target.DATA_LOADER)
    public void onNaturezaOperacoesDlPreLoad(final CollectionLoader.PreLoadEvent<NaturezaOperacao> event) {
        event.getSource().setParameter("codEmpresa", utilGeralService.getCodEmpresa());
    }

    /**
     * Busca preguiçosa de {@code parceiroField} (entityComboBox, campo readOnly) — mesmo
     * padrão de {@code NotaSaidaDetailView.parceiroFieldItemsFetchCallback}, sem filtro
     * cliente/fornecedor (nunca é aberto pelo usuário aqui, só exibição).
     */
    @Install(to = "parceiroField", subject = "itemsFetchCallback")
    private Stream<Parceiro> parceiroFieldItemsFetchCallback(final Query<Parceiro, String> query) {
        String texto = query.getFilter().orElse("");
        return dataManager.load(Parceiro.class)
                .query("select e from Parceiro e where e.codEmpresa = :codEmpresa "
                        + "and upper(e.nome) like upper(concat('%', :texto, '%')) order by e.nome")
                .parameter("codEmpresa", utilGeralService.getCodEmpresa())
                .parameter("texto", texto)
                .firstResult(query.getOffset())
                .maxResults(query.getLimit())
                .list()
                .stream();
    }
}
