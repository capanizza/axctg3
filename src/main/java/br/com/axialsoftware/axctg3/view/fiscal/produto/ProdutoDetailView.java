package br.com.axialsoftware.axctg3.view.fiscal.produto;

import br.com.axialsoftware.axctg3.entity.fiscal.GrupoProduto;
import br.com.axialsoftware.axctg3.entity.fiscal.Produto;
import br.com.axialsoftware.axctg3.entity.tabelas.ClassificacaoFiscal;
import br.com.axialsoftware.axctg3.service.UtilGeralService;
import br.com.axialsoftware.axctg3.view.main.MainView;
import com.vaadin.flow.data.provider.Query;
import com.vaadin.flow.router.Route;
import io.jmix.core.DataManager;
import io.jmix.flowui.model.CollectionLoader;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.stream.Stream;

@Route(value = "produtos/:id", layout = MainView.class)
@ViewController(id = "Produto.detail")
@ViewDescriptor(path = "produto-detail-view.xml")
@EditedEntityContainer("produtoDc")
public class ProdutoDetailView extends StandardDetailView<Produto> {

    @Autowired
    private UtilGeralService utilGeralService;
    @Autowired
    private DataManager dataManager;
    @ViewComponent
    private CollectionLoader<GrupoProduto> gruposProdutoDl;

    /**
     * Carrega manualmente o itemsContainer de grupoProdutoField (entityComboBox, troca
     * de entityPicker) — dataLoadCoordinator auto="true" NÃO carrega sozinho um loader
     * com parâmetro "solto" como :codEmpresa; precisa chamar .load() na mão (ver memória
     * entitycombobox-toggle-nao-abre).
     */
    @Subscribe
    public void onBeforeShow(final BeforeShowEvent event) {
        gruposProdutoDl.setParameter("codEmpresa", utilGeralService.getCodEmpresa());
        gruposProdutoDl.load();
    }

    /**
     * Busca preguiçosa de {@code classificacaoFiscalField} (entityComboBox, troca de
     * entityPicker) — tabela NCM global (~10 mil linhas), não dá pra carregar via
     * itemsContainer.
     */
    @Install(to = "classificacaoFiscalField", subject = "itemsFetchCallback")
    private Stream<ClassificacaoFiscal> classificacaoFiscalFieldItemsFetchCallback(
            final Query<ClassificacaoFiscal, String> query) {
        String texto = query.getFilter().orElse("");
        return dataManager.load(ClassificacaoFiscal.class)
                .query("select e from ClassificacaoFiscal e "
                        + "where upper(e.descricao) like upper(concat('%', :texto, '%')) order by e.codigo")
                .parameter("texto", texto)
                .firstResult(query.getOffset())
                .maxResults(query.getLimit())
                .list()
                .stream();
    }
}
