package br.com.axialsoftware.axctg3.view.fiscal.naturezaoperacao;

import br.com.axialsoftware.axctg3.entity.contabil.ContaContabil;
import br.com.axialsoftware.axctg3.entity.fiscal.NaturezaOperacao;
import br.com.axialsoftware.axctg3.service.UtilGeralService;
import br.com.axialsoftware.axctg3.view.main.MainView;

import com.vaadin.flow.router.Route;
import io.jmix.flowui.model.CollectionLoader;
import io.jmix.flowui.view.*;

import org.springframework.beans.factory.annotation.Autowired;

@Route(value = "natureza-operacaos/:id", layout = MainView.class)
@ViewController(id = "NaturezaOperacao.detail")
@ViewDescriptor(path = "natureza-operacao-detail-view.xml")
@EditedEntityContainer("naturezaOperacaoDc")
public class NaturezaOperacaoDetailView extends StandardDetailView<NaturezaOperacao> {

    @ViewComponent
    private CollectionLoader<ContaContabil> contaContabilsDl;
    @Autowired
    private UtilGeralService utilGeralService;

    @Subscribe
    public void onBeforeShow(final BeforeShowEvent event) {
        contaContabilsDl.setParameter("codEmpresa", utilGeralService.getCodEmpresa());
        contaContabilsDl.setParameter("ano", utilGeralService.getAnoContabil());
        contaContabilsDl.load();
    }
}
