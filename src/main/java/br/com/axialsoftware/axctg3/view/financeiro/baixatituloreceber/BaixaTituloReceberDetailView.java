package br.com.axialsoftware.axctg3.view.financeiro.baixatituloreceber;

import br.com.axialsoftware.axctg3.entity.cadastros.Parceiro;
import br.com.axialsoftware.axctg3.entity.financeiro.Banco;
import br.com.axialsoftware.axctg3.entity.financeiro.ItemReceber;
import br.com.axialsoftware.axctg3.entity.financeiro.TituloReceber;
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

/**
 * Reusa a entidade {@link TituloReceber}, com view id próprio
 * ({@code BaixaTituloReceber.detail}) — só a baixa manual (item 2+), cabeçalho
 * somente-leitura. Ver {@code TituloReceber.detail} para a tela de emissão.
 */
@Route(value = "baixaTituloRecebers/:id", layout = MainView.class)
@ViewController(id = "BaixaTituloReceber.detail")
@ViewDescriptor(path = "baixa-titulo-receber-detail-view.xml")
@EditedEntityContainer("tituloReceberDc")
@DialogMode(width = "1000px", height = "800px")
public class BaixaTituloReceberDetailView extends StandardDetailView<TituloReceber> {

    @Autowired
    private UiComponents uiComponents;
    @Autowired
    private UtilGeralService utilGeralService;
    @Autowired
    private DataManager dataManager;

    /**
     * Parâmetro do itemsContainer de bancoField (entityComboBox, troca de entityPicker)
     * — codEmpresa da sessão.
     */
    @Subscribe(id = "bancosDl", target = Target.DATA_LOADER)
    public void onBancosDlPreLoad(final CollectionLoader.PreLoadEvent<Banco> event) {
        event.getSource().setParameter("codEmpresa", utilGeralService.getCodEmpresa());
    }

    /**
     * Busca preguiçosa de {@code parceiroField} (entityComboBox, campo dentro de vbox
     * {@code enabled="false"}) — mesmo padrão de
     * {@code NotaSaidaDetailView.parceiroFieldItemsFetchCallback}, sem filtro
     * cliente/fornecedor.
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
