package br.com.axialsoftware.axctg3.view.contabil.bem;

import br.com.axialsoftware.axctg3.entity.cadastros.Parceiro;
import br.com.axialsoftware.axctg3.entity.contabil.Bem;
import br.com.axialsoftware.axctg3.entity.contabil.ContaContabil;
import br.com.axialsoftware.axctg3.entity.contabil.Depreciacao;
import br.com.axialsoftware.axctg3.service.UtilGeralService;
import br.com.axialsoftware.axctg3.view.main.MainView;
import com.vaadin.flow.data.provider.Query;
import com.vaadin.flow.router.Route;
import io.jmix.core.DataManager;
import io.jmix.flowui.model.CollectionContainer;
import io.jmix.flowui.model.CollectionLoader;
import io.jmix.flowui.model.CollectionPropertyContainer;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

@Route(value = "bems/:id", layout = MainView.class)
@ViewController(id = "Bem.detail")
@ViewDescriptor(path = "bem-detail-view.xml")
@EditedEntityContainer("bemDc")
public class BemDetailView extends StandardDetailView<Bem> {
    @ViewComponent
    private CollectionPropertyContainer<Depreciacao> depreciacaosDc;
    @ViewComponent
    private CollectionContainer<Depreciacao> depreciacaos1Dc;
    @ViewComponent
    private CollectionContainer<Depreciacao> depreciacaos2Dc;
    @Autowired
    private UtilGeralService utilGeralService;
    @Autowired
    private DataManager dataManager;

    /**
     * Parâmetro do itemsContainer de contaContabil* (entityComboBox, troca de
     * entityPicker) — codEmpresa da sessão, mesmo critério dos list views.
     */
    @Subscribe(id = "contasContabeisDl", target = Target.DATA_LOADER)
    public void onContasContabeisDlPreLoad(final CollectionLoader.PreLoadEvent<ContaContabil> event) {
        event.getSource().setParameter("codEmpresa", utilGeralService.getCodEmpresa());
        event.getSource().setParameter("ano", utilGeralService.getAnoContabil());
    }

    /**
     * Busca preguiçosa de {@code parceiroField} (entityComboBox) — mesmo padrão de
     * {@code NotaSaidaDetailView.parceiroFieldItemsFetchCallback}, sem o filtro
     * {@code cliente = true} (Bem aceita qualquer parceiro, não só clientes).
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

    @Subscribe
    public void onBeforeShow(final BeforeShowEvent event) {
        List<Depreciacao> all = new ArrayList<>(depreciacaosDc.getItems());

        int splitIndex = Math.min(all.size() / 2, all.size());

        List<Depreciacao> first = all.subList(0, splitIndex);
        List<Depreciacao> second = all.subList(splitIndex, all.size());

        depreciacaos1Dc.getMutableItems().clear();
        depreciacaos1Dc.getMutableItems().addAll(first);

        depreciacaos2Dc.getMutableItems().clear();
        depreciacaos2Dc.getMutableItems().addAll(second);
    }
}
