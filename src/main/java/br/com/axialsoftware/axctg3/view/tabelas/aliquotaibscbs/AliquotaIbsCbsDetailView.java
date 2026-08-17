package br.com.axialsoftware.axctg3.view.tabelas.aliquotaibscbs;

import br.com.axialsoftware.axctg3.entity.tabelas.AliquotaIbsCbs;
import br.com.axialsoftware.axctg3.view.main.MainView;

import com.vaadin.flow.router.Route;
import io.jmix.flowui.view.EditedEntityContainer;
import io.jmix.flowui.view.StandardDetailView;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewDescriptor;

@Route(value = "aliquota-ibs-cbses/:id", layout = MainView.class)
@ViewController(id = "AliquotaIbsCbs.detail")
@ViewDescriptor(path = "aliquota-ibs-cbs-detail-view.xml")
@EditedEntityContainer("aliquotaIbsCbsDc")
public class AliquotaIbsCbsDetailView extends StandardDetailView<AliquotaIbsCbs> {
}
