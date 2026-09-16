package br.com.axialsoftware.axctg3.view.financeiro.diversopagar;

import br.com.axialsoftware.axctg3.entity.cadastros.Parceiro;
import br.com.axialsoftware.axctg3.entity.contabil.ContaContabil;
import br.com.axialsoftware.axctg3.entity.financeiro.DiversoPagar;
import br.com.axialsoftware.axctg3.entity.financeiro.ItemDiversoPagar;
import br.com.axialsoftware.axctg3.service.UtilGeralService;
import br.com.axialsoftware.axctg3.view.main.MainView;
import com.vaadin.flow.data.provider.Query;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.data.renderer.Renderer;
import com.vaadin.flow.router.Route;
import io.jmix.core.DataManager;
import io.jmix.flowui.UiComponents;
import io.jmix.flowui.component.checkbox.JmixCheckbox;
import io.jmix.flowui.model.CollectionLoader;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.stream.Stream;

@Route(value = "diversoPagars/:id", layout = MainView.class)
@ViewController(id = "DiversoPagar.detail")
@ViewDescriptor(path = "diverso-pagar-detail-view.xml")
@EditedEntityContainer("diversoPagarDc")
public class DiversoPagarDetailView extends StandardDetailView<DiversoPagar> {

    @Autowired
    private UiComponents uiComponents;
    @Autowired
    private UtilGeralService utilGeralService;
    @Autowired
    private DataManager dataManager;

    /**
     * Parâmetro do itemsContainer de contaContabilField (entityComboBox, troca de
     * entityPicker) — codEmpresa/ano da sessão, mesmo critério de
     * ContaContabilListView quando aberta como lookup (apenasAnaliticas=true).
     */
    @Subscribe(id = "contasContabeisDl", target = Target.DATA_LOADER)
    public void onContasContabeisDlPreLoad(final CollectionLoader.PreLoadEvent<ContaContabil> event) {
        event.getSource().setParameter("codEmpresa", utilGeralService.getCodEmpresa());
        event.getSource().setParameter("ano", utilGeralService.getAnoContabil());
    }

    /**
     * Busca preguiçosa de {@code parceiroField} (entityComboBox, troca de entityPicker)
     * — mesmo padrão de {@code NotaSaidaDetailView.parceiroFieldItemsFetchCallback}, sem
     * filtro cliente/fornecedor.
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

    @Supply(to = "itensDataGrid.contabilizado", subject = "renderer")
    private Renderer<ItemDiversoPagar> itensDataGridContabilizadoRenderer() {
        return new ComponentRenderer<>(itemDiversoPagar -> {
            JmixCheckbox checkbox = uiComponents.create(JmixCheckbox.class);
            checkbox.setValue(Boolean.TRUE.equals(itemDiversoPagar.getContabilizado()));
            checkbox.setReadOnly(true);
            checkbox.addClassName("grid-value-checkbox");
            return checkbox;
        });
    }

}
