package br.com.axialsoftware.axctg3.view.fiscal.notasaida;

import br.com.axialsoftware.axctg3.entity.cadastros.Empresa;
import br.com.axialsoftware.axctg3.entity.fiscal.NotaSaida;
import br.com.axialsoftware.axctg3.service.UtilGeralService;
import br.com.axialsoftware.axctg3.view.main.MainView;
import com.vaadin.flow.router.Route;
import io.jmix.core.EntityStates;
import io.jmix.flowui.Dialogs;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;

@Route(value = "nota-saidas/:id", layout = MainView.class)
@ViewController(id = "NotaSaida.detail")
@ViewDescriptor(path = "nota-saida-detail-view.xml")
@EditedEntityContainer("notaSaidaDc")
public class NotaSaidaDetailView extends StandardDetailView<NotaSaida> {

    @Autowired
    private UtilGeralService utilGeralService;
    @Autowired
    private Dialogs dialogs;
    @Autowired
    private EntityStates entityStates;
    @ViewComponent
    private MessageBundle messageBundle;

    /**
     * Pré-preenche espécie/série na inclusão a partir de {@code Empresa.especieNfe}/
     * {@code .serieNfe} (aba "Emissão NFe") — evita o operador digitar o mesmo valor em
     * toda nota nova. Só roda pra entidade nova de verdade (InitEntityEvent não dispara
     * na edição). Os campos ficaram somente leitura na tela (`especieField`/
     * `serieField`), então um valor não configurado na Empresa deixa a nota impossível
     * de salvar — ver {@link #onReady}, que avisa disso antes do operador digitar o
     * resto e perder o trabalho.
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

    /**
     * Alerta na abertura (não só na tentativa de Salvar) quando a nota é nova e espécie/
     * série vieram em branco — os campos são somente leitura agora (ver {@link
     * #onInitEntity}), então sem isso o operador só descobre o problema depois de
     * preencher parceiro/natureza/itens e tentar salvar, perdendo tudo.
     */
    @Subscribe
    public void onReady(final ReadyEvent event) {
        NotaSaida notaSaida = getEditedEntity();
        if (!entityStates.isNew(notaSaida)) {
            return;
        }
        boolean semEspecie = notaSaida.getEspecie() == null || notaSaida.getEspecie().isBlank();
        boolean semSerie = notaSaida.getSerie() == null || notaSaida.getSerie().isBlank();
        if (semEspecie || semSerie) {
            dialogs.createMessageDialog()
                    .withHeader(messageBundle.getMessage("notaSaidaDetailView.especieSerieNaoConfigurada.header"))
                    .withText(messageBundle.getMessage("notaSaidaDetailView.especieSerieNaoConfigurada.text"))
                    .open();
        }
    }
}
