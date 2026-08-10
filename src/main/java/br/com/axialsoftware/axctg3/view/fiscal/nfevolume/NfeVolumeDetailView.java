package br.com.axialsoftware.axctg3.view.fiscal.nfevolume;

import br.com.axialsoftware.axctg3.entity.fiscal.NfeVolume;
import br.com.axialsoftware.axctg3.view.main.MainView;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.view.*;

@Route(value = "nfe-volumes/:id", layout = MainView.class)
@ViewController(id = "NfeVolume.detail")
@ViewDescriptor(path = "nfe-volume-detail-view.xml")
@EditedEntityContainer("nfeVolumeDc")
@DialogMode(width = "60%")
public class NfeVolumeDetailView extends StandardDetailView<NfeVolume> {
}
