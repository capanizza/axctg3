package br.com.axialsoftware.axctg3.view.financeiro.banco;

import br.com.axialsoftware.axctg3.entity.contabil.ContaContabil;
import br.com.axialsoftware.axctg3.entity.financeiro.Banco;
import br.com.axialsoftware.axctg3.service.financeiro.BancoUpdateService;
import br.com.axialsoftware.axctg3.service.UtilGeralService;
import br.com.axialsoftware.axctg3.view.main.MainView;
import com.vaadin.flow.router.Route;
import io.jmix.core.SaveContext;
import io.jmix.flowui.model.CollectionLoader;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Set;

@Route(value = "bancoes/:id", layout = MainView.class)
@ViewController(id = "Banco.detail")
@ViewDescriptor(path = "banco-detail-view.xml")
@EditedEntityContainer("bancoDc")
public class BancoDetailView extends StandardDetailView<Banco> {
    @Autowired
    private BancoUpdateService updateService;
    @ViewComponent
    private CollectionLoader<ContaContabil> contaContabilsDl;
    @Autowired
    private UtilGeralService utilGeralService;

    @Install(target = Target.DATA_CONTEXT)
    private Set<Object> saveDelegate(SaveContext saveContext) {
        return Set.of(updateService.save(getEditedEntity(), saveContext));
    }

    @Subscribe
    public void onBeforeShow(final BeforeShowEvent event) {
        contaContabilsDl.setParameter("codEmpresa", utilGeralService.getCodEmpresa());
        contaContabilsDl.setParameter("ano", utilGeralService.getAnoContabil());
        contaContabilsDl.load();
    }
}