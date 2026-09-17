package br.com.axialsoftware.axctg3.view.contabil.lancamento;

import br.com.axialsoftware.axctg3.entity.cadastros.CentroCusto;
import br.com.axialsoftware.axctg3.entity.contabil.ContaContabil;
import br.com.axialsoftware.axctg3.entity.contabil.HistoricoContabil;
import br.com.axialsoftware.axctg3.entity.contabil.Lancamento;
import br.com.axialsoftware.axctg3.service.UtilGeralService;
import br.com.axialsoftware.axctg3.view.main.MainView;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.component.datepicker.TypedDatePicker;
import io.jmix.flowui.component.textfield.JmixIntegerField;
import io.jmix.flowui.model.CollectionLoader;
import io.jmix.flowui.view.EditedEntityContainer;
import io.jmix.flowui.view.StandardDetailView;
import io.jmix.flowui.view.Subscribe;
import io.jmix.flowui.view.Target;
import io.jmix.flowui.view.ViewComponent;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewDescriptor;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;

@Route(value = "lancamentoes/:id", layout = MainView.class)
@ViewController(id = "Lancamento.detail")
@ViewDescriptor(path = "lancamento-detail-view.xml")
@EditedEntityContainer("lancamentoDc")
public class LancamentoDetailView extends StandardDetailView<Lancamento> {

    @ViewComponent
    private JmixIntegerField numeroField;
    @ViewComponent
    private JmixIntegerField anoField;
    @ViewComponent
    private JmixIntegerField mesField;
    @ViewComponent
    private TypedDatePicker<LocalDate> dataLancamentoField;
    @Autowired
    private UtilGeralService utilGeralService;
    @ViewComponent
    private CollectionLoader<ContaContabil> contasContabeisDl;
    @ViewComponent
    private CollectionLoader<CentroCusto> centrosCustoDl;
    @ViewComponent
    private CollectionLoader<HistoricoContabil> historicosContabeisDl;

    /**
     * numero/ano/mes/dataLancamento são {@code @NotNull} na entidade, mas carimbados
     * pelo {@code LancamentoEventListener} no save (numero via sequence, dataLancamento
     * a partir de dia+período contábil) — não digitados pelo usuário. Se ficassem
     * vinculados via {@code property=} no XML, o Bean Validation por-componente do Jmix
     * (anexado a todo campo ligado a um atributo {@code @NotNull}) barraria o botão
     * salvar com esses campos em branco, antes do listener sequer rodar — por isso
     * ficam desvinculados (só exibição) no XML, e sincronizados aqui manualmente com o
     * valor real assim que a tela abre (existente: mostra o que já foi carimbado; nova:
     * fica em branco até salvar). codEmpresa não aparece na tela — é a empresa
     * selecionada na sessão, não faz sentido mostrar/editar aqui.
     */
    @Subscribe
    public void onBeforeShow(final BeforeShowEvent event) {
        // itemsContainer de conta/centroCusto/historicoContabil (entityComboBox, troca
        // de entityPicker) — dataLoadCoordinator auto="true" NÃO carrega sozinho um
        // loader com parâmetro "solto"; precisa chamar .load() na mão (ver memória
        // entitycombobox-toggle-nao-abre).
        Integer codEmpresa = utilGeralService.getCodEmpresa();
        contasContabeisDl.setParameter("codEmpresa", codEmpresa);
        contasContabeisDl.setParameter("ano", utilGeralService.getAnoContabil());
        contasContabeisDl.load();

        centrosCustoDl.setParameter("codEmpresa", codEmpresa);
        centrosCustoDl.load();

        historicosContabeisDl.setParameter("codEmpresa", codEmpresa);
        historicosContabeisDl.load();

        Lancamento lancamento = getEditedEntity();
        numeroField.setValue(lancamento.getNumero());
        anoField.setValue(lancamento.getAno());
        mesField.setValue(lancamento.getMes());
        dataLancamentoField.setValue(lancamento.getDataLancamento());
    }
}
