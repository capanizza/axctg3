package br.com.axialsoftware.axctg3.view.financeiro.tituloreceber;

import br.com.axialsoftware.axctg3.entity.cadastros.Parceiro;
import br.com.axialsoftware.axctg3.entity.contabil.ContaContabil;
import br.com.axialsoftware.axctg3.entity.financeiro.Banco;
import br.com.axialsoftware.axctg3.entity.financeiro.ItemReceber;
import br.com.axialsoftware.axctg3.entity.financeiro.TituloReceber;
import br.com.axialsoftware.axctg3.service.UtilGeralService;
import br.com.axialsoftware.axctg3.service.financeiro.TituloReceberService;
import br.com.axialsoftware.axctg3.view.main.MainView;
import com.vaadin.flow.data.provider.Query;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.data.renderer.Renderer;
import com.vaadin.flow.router.Route;
import io.jmix.core.DataManager;
import io.jmix.flowui.UiComponents;
import io.jmix.flowui.component.checkbox.JmixCheckbox;
import io.jmix.flowui.component.textfield.TypedTextField;
import io.jmix.flowui.model.CollectionLoader;
import io.jmix.flowui.model.InstanceContainer;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.stream.Stream;

@Route(value = "tituloRecebers/:id", layout = MainView.class)
@ViewController(id = "TituloReceber.detail")
@ViewDescriptor(path = "titulo-receber-detail-view.xml")
@EditedEntityContainer("tituloReceberDc")
public class TituloReceberDetailView extends StandardDetailView<TituloReceber> {

    @ViewComponent
    private InstanceContainer<TituloReceber> tituloReceberDc;
    @Autowired
    private TituloReceberService tituloReceberService;
    @ViewComponent
    private TypedTextField<BigDecimal> valorField;
    @Autowired
    private UiComponents uiComponents;
    @Autowired
    private UtilGeralService utilGeralService;
    @Autowired
    private DataManager dataManager;
    @ViewComponent
    private CollectionLoader<Banco> bancosDl;
    @ViewComponent
    private CollectionLoader<ContaContabil> contasContabeisDl;

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

    @Subscribe
    public void onBeforeShow(final BeforeShowEvent event) {
        // itemsContainer de banco/contaContabil (entityComboBox, troca de entityPicker)
        // — dataLoadCoordinator auto="true" NÃO carrega sozinho um loader com parâmetro
        // "solto"; precisa chamar .load() na mão (ver memória
        // entitycombobox-toggle-nao-abre). codEmpresa/ano da sessão, mesmo critério de
        // ContaContabilListView quando aberta como lookup (apenasAnaliticas=true).
        Integer codEmpresa = utilGeralService.getCodEmpresa();
        bancosDl.setParameter("codEmpresa", codEmpresa);
        bancosDl.load();
        contasContabeisDl.setParameter("codEmpresa", codEmpresa);
        contasContabeisDl.setParameter("ano", utilGeralService.getAnoContabil());
        contasContabeisDl.load();

        TituloReceber tituloReceber = tituloReceberDc.getItem();
        valorField.setReadOnly(tituloReceberService.valorRecebidoTitulo(tituloReceber).compareTo(BigDecimal.ZERO) != 0);
    }

    @Supply(to = "itensDataGrid.contabilizado", subject = "renderer")
    private Renderer<ItemReceber> itensDataGridContabilizadoRenderer() {
        return new ComponentRenderer<>(itemReceber -> {
            JmixCheckbox checkbox = uiComponents.create(JmixCheckbox.class);
            checkbox.setValue(Boolean.TRUE.equals(itemReceber.getContabilizado()));
            checkbox.setReadOnly(true);
            checkbox.addClassName("grid-value-checkbox");
            return checkbox;
        });
    }

}
