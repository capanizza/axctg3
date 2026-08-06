package br.com.axialsoftware.axctg3.view.contabil.contacontabil;

import br.com.axialsoftware.axctg3.entity.contabil.ContaContabil;
import br.com.axialsoftware.axctg3.entity.contabil.SaldoConta;
import br.com.axialsoftware.axctg3.service.UtilGeralService;
import br.com.axialsoftware.axctg3.view.main.MainView;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.model.CollectionContainer;
import io.jmix.flowui.model.CollectionPropertyContainer;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;


@Route(value = "conta-contabils/:id", layout = MainView.class)
@ViewController(id = "ContaContabil.detail")
@ViewDescriptor(path = "conta-contabil-detail-view.xml")
@EditedEntityContainer("contaContabilDc")
public class ContaContabilDetailView extends StandardDetailView<ContaContabil> {

    static Boolean isNew = false;
    
    @Autowired
    private UtilGeralService utilGeralService;
    @ViewComponent
    private CollectionContainer<SaldoConta> saldos1Dc;
    @ViewComponent
    private CollectionContainer<SaldoConta> saldos2Dc;
    @ViewComponent
    private CollectionPropertyContainer<SaldoConta> saldosContaDc;

    @Subscribe
    public void onBeforeShow(final BeforeShowEvent event) {
        // pega a lista completa (12 registros)
        List<SaldoConta> all = new ArrayList<>(saldosContaDc.getItems());

        int splitIndex = Math.min(6, all.size());

        List<SaldoConta> first = all.subList(0, splitIndex);
        List<SaldoConta> second = all.subList(splitIndex, all.size());

        saldos1Dc.getMutableItems().clear();
        saldos1Dc.getMutableItems().addAll(first);

        saldos2Dc.getMutableItems().clear();
        saldos2Dc.getMutableItems().addAll(second);
    }

    @Override
    public String getPageTitle() {
        String title = super.getPageTitle();
        if (isNew) {
            isNew = false;
        } else {
            int ano = utilGeralService.getAnoContabil();
            String title2 = "ano contábil " + ano;
            title = title + " "  + title2;
        }
        return title;
    }
}