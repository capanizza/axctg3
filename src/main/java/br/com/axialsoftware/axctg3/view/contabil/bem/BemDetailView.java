package br.com.axialsoftware.axctg3.view.contabil.bem;

import br.com.axialsoftware.axctg3.entity.contabil.Bem;
import br.com.axialsoftware.axctg3.entity.contabil.Depreciacao;
import br.com.axialsoftware.axctg3.view.main.MainView;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.model.CollectionContainer;
import io.jmix.flowui.model.CollectionPropertyContainer;
import io.jmix.flowui.view.*;

import java.util.ArrayList;
import java.util.List;

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
