package br.com.axialsoftware.axctg3.view.financeiro.baixatitulopagar;

import br.com.axialsoftware.axctg3.entity.cadastros.Parceiro;
import br.com.axialsoftware.axctg3.entity.financeiro.ItemPagar;
import br.com.axialsoftware.axctg3.entity.financeiro.TituloPagar;
import br.com.axialsoftware.axctg3.service.UtilGeralService;
import br.com.axialsoftware.axctg3.view.main.MainView;
import com.vaadin.flow.data.provider.Query;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.data.renderer.Renderer;
import com.vaadin.flow.router.Route;
import io.jmix.core.DataManager;
import io.jmix.flowui.UiComponents;
import io.jmix.flowui.component.checkbox.JmixCheckbox;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.stream.Stream;

/**
 * Reusa a entidade {@link TituloPagar}, com view id próprio
 * ({@code BaixaTituloPagar.detail}) — só a baixa manual (item 2+), cabeçalho
 * somente-leitura. Ver {@code TituloPagar.detail} para a tela de emissão.
 */
@Route(value = "baixaTituloPagars/:id", layout = MainView.class)
@ViewController(id = "BaixaTituloPagar.detail")
@ViewDescriptor(path = "baixa-titulo-pagar-detail-view.xml")
@EditedEntityContainer("tituloPagarDc")
@DialogMode(width = "1200px", height = "800px")
public class BaixaTituloPagarDetailView extends StandardDetailView<TituloPagar> {

    @Autowired
    private UiComponents uiComponents;
    @Autowired
    private UtilGeralService utilGeralService;
    @Autowired
    private DataManager dataManager;

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

    @Supply(to = "itensDataGrid.contabilizado", subject = "renderer")
    private Renderer<ItemPagar> itensDataGridContabilizadoRenderer() {
        return new ComponentRenderer<>(itemPagar -> {
            JmixCheckbox checkbox = uiComponents.create(JmixCheckbox.class);
            checkbox.setValue(Boolean.TRUE.equals(itemPagar.getContabilizado()));
            checkbox.setReadOnly(true);
            checkbox.addClassName("grid-value-checkbox");
            return checkbox;
        });
    }

}
