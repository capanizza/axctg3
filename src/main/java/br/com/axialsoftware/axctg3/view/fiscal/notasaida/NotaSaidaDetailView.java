package br.com.axialsoftware.axctg3.view.fiscal.notasaida;

import br.com.axialsoftware.axctg3.entity.cadastros.Empresa;
import br.com.axialsoftware.axctg3.entity.fiscal.NotaSaida;
import br.com.axialsoftware.axctg3.service.UtilGeralService;
import br.com.axialsoftware.axctg3.view.main.MainView;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;

@Route(value = "nota-saidas/:id", layout = MainView.class)
@ViewController(id = "NotaSaida.detail")
@ViewDescriptor(path = "nota-saida-detail-view.xml")
@EditedEntityContainer("notaSaidaDc")
public class NotaSaidaDetailView extends StandardDetailView<NotaSaida> {

    @Autowired
    private UtilGeralService utilGeralService;

    /**
     * Pré-preenche espécie/série na inclusão a partir de {@code Empresa.especieNfe}/
     * {@code .serieNfe} (aba "Emissão NFe") — evita o operador digitar o mesmo valor em
     * toda nota nova. Só roda pra entidade nova de verdade (InitEntityEvent não dispara
     * na edição); campos continuam editáveis, e um valor não configurado na Empresa
     * simplesmente deixa o campo em branco, igual hoje.
     */
    @Subscribe
    public void onInitEntity(final InitEntityEvent<NotaSaida> event) {
        Empresa empresa = utilGeralService.getEmpresa();
        if (empresa == null) {
            return;
        }
        NotaSaida notaSaida = event.getEntity();
        notaSaida.setEspecie(empresa.getEspecieNfe());
        notaSaida.setSerie(empresa.getSerieNfe());
    }
}
