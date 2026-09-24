package br.com.axialsoftware.axctg3.view.fiscal.notasaida;

import br.com.axialsoftware.axctg3.entity.cadastros.CondicaoPagamento;
import br.com.axialsoftware.axctg3.entity.cadastros.Empresa;
import br.com.axialsoftware.axctg3.entity.cadastros.Mensagem;
import br.com.axialsoftware.axctg3.entity.cadastros.Parceiro;
import br.com.axialsoftware.axctg3.entity.cadastros.Transportadora;
import br.com.axialsoftware.axctg3.entity.cadastros.Vendedor;
import br.com.axialsoftware.axctg3.entity.financeiro.Banco;
import br.com.axialsoftware.axctg3.entity.fiscal.NaturezaOperacao;
import br.com.axialsoftware.axctg3.entity.fiscal.NotaSaida;
import br.com.axialsoftware.axctg3.entity.tabelas.ClassTrib;
import br.com.axialsoftware.axctg3.service.UtilGeralService;
import br.com.axialsoftware.axctg3.service.fiscal.ItemNotaSaidaTributacaoService;
import br.com.axialsoftware.axctg3.view.main.MainView;
import com.vaadin.flow.data.provider.Query;
import com.vaadin.flow.router.Route;
import io.jmix.core.DataManager;
import io.jmix.core.EntityStates;
import io.jmix.flowui.Dialogs;
import io.jmix.flowui.model.CollectionLoader;
import io.jmix.flowui.model.InstanceContainer;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.stream.Stream;

@Route(value = "nota-saidas/:id", layout = MainView.class)
@ViewController(id = "NotaSaida.detail")
@ViewDescriptor(path = "nota-saida-detail-view.xml")
@EditedEntityContainer("notaSaidaDc")
public class NotaSaidaDetailView extends StandardDetailView<NotaSaida> {

    @Autowired
    private UtilGeralService utilGeralService;
    @Autowired
    private ItemNotaSaidaTributacaoService tributacaoService;
    @Autowired
    private Dialogs dialogs;
    @Autowired
    private EntityStates entityStates;
    @Autowired
    private DataManager dataManager;
    @ViewComponent
    private MessageBundle messageBundle;
    @ViewComponent
    private CollectionLoader<NaturezaOperacao> naturezaOperacoesDl;
    @ViewComponent
    private CollectionLoader<CondicaoPagamento> condicoesPagamentoDl;
    @ViewComponent
    private CollectionLoader<Banco> bancosDl;
    @ViewComponent
    private CollectionLoader<Transportadora> transportadorasDl;
    @ViewComponent
    private CollectionLoader<Vendedor> vendedoresDl;
    @ViewComponent
    private CollectionLoader<Mensagem> mensagensDl;

    /**
     * Busca preguiçosa dos parceiros de {@code parceiroField} (entityComboBox) — a
     * empresa 1 sozinha tem ~20 mil {@code Parceiro} com {@code cliente=true}; carregar
     * tudo de uma vez via {@code itemsContainer} deixou a tela visivelmente mais lenta
     * (achado ao vivo 2026-09-15). Busca no banco por lote (offset/limit vêm do próprio
     * {@code Query} do Vaadin, tamanho padrão 50) filtrando por {@code cliente=true} e
     * pelo texto já digitado — nunca carrega a lista inteira.
     */
    @Install(to = "parceiroField", subject = "itemsFetchCallback")
    private Stream<Parceiro> parceiroFieldItemsFetchCallback(final Query<Parceiro, String> query) {
        String texto = query.getFilter().orElse("");
        return dataManager.load(Parceiro.class)
                .query("select e from Parceiro e where e.codEmpresa = :codEmpresa and e.cliente = true "
                        + "and upper(e.nome) like upper(concat('%', :texto, '%')) order by e.nome")
                .parameter("codEmpresa", utilGeralService.getCodEmpresa())
                .parameter("texto", texto)
                .firstResult(query.getOffset())
                .maxResults(query.getLimit())
                .list()
                .stream();
    }

    /**
     * Carrega manualmente os itemsContainer de natureza/condicaoPagamento/banco/
     * transportadora/vendedor/mensagem (entityComboBox, troca de entityPicker) —
     * {@code dataLoadCoordinator auto="true"} NÃO carrega sozinho um loader com
     * parâmetro "solto" (sem prefixo container_/component_) como {@code :codEmpresa} —
     * precisa chamar {@code .load()} na mão (ver memória
     * entitycombobox-toggle-nao-abre). classTribField usa classTribsDc (tabela global,
     * sem parâmetro), carregada automaticamente pelo coordinator.
     */
    @Subscribe
    public void onBeforeShow(final BeforeShowEvent event) {
        Integer codEmpresa = utilGeralService.getCodEmpresa();
        naturezaOperacoesDl.setParameter("codEmpresa", codEmpresa);
        naturezaOperacoesDl.load();

        condicoesPagamentoDl.setParameter("codEmpresa", codEmpresa);
        condicoesPagamentoDl.load();

        bancosDl.setParameter("codEmpresa", codEmpresa);
        bancosDl.load();

        transportadorasDl.setParameter("codEmpresa", codEmpresa);
        transportadorasDl.load();

        vendedoresDl.setParameter("codEmpresa", codEmpresa);
        vendedoresDl.load();

        mensagensDl.setParameter("codEmpresa", codEmpresa);
        mensagensDl.load();
    }

    /**
     * Pré-preenche espécie/série/modalidade de frete na inclusão a partir de {@code
     * Empresa.especieNfe}/{@code .serieNfe}/{@code .modFretePadrao} (aba "Emissão NFe") —
     * evita o operador digitar o mesmo valor em toda nota nova. Só roda pra entidade nova
     * de verdade (InitEntityEvent não dispara na edição). Espécie/série ficaram somente
     * leitura na tela (`especieField`/`serieField`), então um valor não configurado na
     * Empresa deixa a nota impossível de salvar — ver {@link #onReady}, que avisa disso
     * antes do operador digitar o resto e perder o trabalho. {@code modFrete} continua
     * editável (pode variar nota a nota, diferente de espécie/série).
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
        notaSaida.setModFrete(empresa.getModFretePadrao());
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

    /**
     * Ao escolher a natureza, o cClassTrib do cabeçalho vem com o dela (quando a natureza
     * tem um cadastrado) — continua editável. Pedido do usuário 2026-09-24: o cabeçalho
     * é quem decide o cClassTrib dos itens quando não é o de tributação integral (ver
     * ItemNotaSaidaTributacaoService.resolverCodClassTrib).
     */
    @Subscribe(id = "notaSaidaDc", target = Target.DATA_CONTAINER)
    public void onNotaSaidaDcItemPropertyChange(final InstanceContainer.ItemPropertyChangeEvent<NotaSaida> event) {
        if (!"natureza".equals(event.getProperty())) {
            return;
        }
        ClassTrib classTrib = tributacaoService.classTribDaNatureza(event.getItem().getNatureza());
        if (classTrib != null) {
            event.getItem().setClassTrib(getViewData().getDataContext().merge(classTrib));
        }
    }
}
