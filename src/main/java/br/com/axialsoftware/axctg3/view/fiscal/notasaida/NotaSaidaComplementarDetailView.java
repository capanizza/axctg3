package br.com.axialsoftware.axctg3.view.fiscal.notasaida;

import br.com.axialsoftware.axctg3.entity.cadastros.Parceiro;
import br.com.axialsoftware.axctg3.entity.fiscal.NaturezaOperacao;
import br.com.axialsoftware.axctg3.entity.fiscal.NotaSaida;
import br.com.axialsoftware.axctg3.service.UtilGeralService;
import br.com.axialsoftware.axctg3.view.main.MainView;
import com.vaadin.flow.data.provider.Query;
import com.vaadin.flow.router.Route;
import io.jmix.core.DataManager;
import io.jmix.flowui.component.textfield.JmixBigDecimalField;
import io.jmix.flowui.model.CollectionLoader;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.stream.Stream;

/**
 * Tela dedicada de entrada de dados de uma NFe complementar (finNFe=2) — subconjunto
 * enxuto de {@link NotaSaidaDetailView}, aberta só a partir de {@code
 * NotaSaidaListView.onNotaSaidasDataGridEmitirNfeComplementarAction}. Natureza de
 * operação, classificação tributária e cliente vêm travados (herdados da nota
 * original); o operador só edita a data de emissão e os valores que efetivamente
 * mudam numa complementar (mercadoria/ICMS/ST/IPI + frete/seguro/desconto/despesas).
 * Sem aba de itens: o pseudo item é gerado sozinho por {@code
 * NfeEmissaoService.gerarItemComplementar} na hora de emitir — ver o Javadoc de lá
 * pra saber por que a geração acontece na emissão, não no save desta tela.
 *
 * <p>{@code aliqIcmsField} não é gravado ({@code NotaSaida} não tem alíquota de ICMS):
 * serve só pra o operador conferir base × alíquota = valor do ICMS no save. Na emissão
 * o pICMS do pseudo item é derivado de {@code valorIcms/baseIcms} — e bate com a
 * alíquota digitada justamente porque esta conferência não deixa salvar valores
 * incoerentes (a SEFAZ rejeitaria com 528 de qualquer jeito).
 */
@Route(value = "nota-saidas-complementar/:id", layout = MainView.class)
@ViewController(id = "NotaSaida.complementar")
@ViewDescriptor(path = "nota-saida-complementar-detail-view.xml")
@EditedEntityContainer("notaSaidaDc")
public class NotaSaidaComplementarDetailView extends StandardDetailView<NotaSaida> {

    @Autowired
    private UtilGeralService utilGeralService;
    @Autowired
    private DataManager dataManager;
    @ViewComponent
    private CollectionLoader<NaturezaOperacao> naturezaOperacoesDl;
    @ViewComponent
    private JmixBigDecimalField aliqIcmsField;
    @ViewComponent
    private MessageBundle messageBundle;

    /** Diferença aceita entre base × alíquota e o valor do ICMS (arredondamento de centavo). */
    private static final BigDecimal TOLERANCIA_ICMS = new BigDecimal("0.01");

    /**
     * Carrega manualmente o itemsContainer de naturezaField (entityComboBox, troca de
     * entityPicker) — dataLoadCoordinator auto="true" NÃO carrega sozinho um loader com
     * parâmetro "solto" como :codEmpresa; precisa chamar .load() na mão (ver memória
     * entitycombobox-toggle-nao-abre).
     */
    @Subscribe
    public void onBeforeShow(final BeforeShowEvent event) {
        naturezaOperacoesDl.setParameter("codEmpresa", utilGeralService.getCodEmpresa());
        naturezaOperacoesDl.load();
    }

    /**
     * Pré-preenche a alíquota: nota já salva com base e valor → valor÷base (é o que a
     * emissão vai usar); senão a alíquota de ICMS da natureza de operação. Em ReadyEvent
     * porque o initializer de NotaSaidaListView (natureza herdada da original) já rodou.
     */
    @Subscribe
    public void onReady(final ReadyEvent event) {
        NotaSaida nota = getEditedEntity();
        BigDecimal base = nvl(nota.getBaseIcms());
        BigDecimal valor = nvl(nota.getValorIcms());
        if (base.signum() != 0 && valor.signum() != 0) {
            aliqIcmsField.setValue(valor.multiply(new BigDecimal(100)).divide(base, 2, RoundingMode.HALF_UP));
        } else if (nota.getNatureza() != null) {
            // natureza chega com fetch plan _instance_name — recarrega pra ler aliqIcms
            aliqIcmsField.setValue(dataManager.load(NaturezaOperacao.class)
                    .id(nota.getNatureza().getId())
                    .one()
                    .getAliqIcms());
        }
    }

    /** Barra o save quando base × alíquota não fecha com o valor do ICMS digitado. */
    @Subscribe
    public void onValidation(final ValidationEvent event) {
        NotaSaida nota = getEditedEntity();
        BigDecimal base = nvl(nota.getBaseIcms());
        BigDecimal valor = nvl(nota.getValorIcms());
        BigDecimal aliquota = nvl(aliqIcmsField.getValue());
        if (base.signum() == 0 && valor.signum() == 0) {
            return;
        }
        if (base.signum() == 0) {
            event.getErrors().add(messageBundle.getMessage("notaSaidaComplementarDetailView.icmsSemBase"));
            return;
        }
        BigDecimal esperado = base.multiply(aliquota).divide(new BigDecimal(100), 2, RoundingMode.HALF_UP);
        if (esperado.subtract(valor).abs().compareTo(TOLERANCIA_ICMS) > 0) {
            event.getErrors().add(messageBundle.formatMessage("notaSaidaComplementarDetailView.icmsNaoConfere",
                    esperado.toPlainString(), valor.setScale(2, RoundingMode.HALF_UP).toPlainString()));
        }
    }

    private static BigDecimal nvl(BigDecimal valor) {
        return valor == null ? BigDecimal.ZERO : valor;
    }

    /**
     * Busca preguiçosa de {@code parceiroField} (entityComboBox, campo readOnly) — mesmo
     * padrão de {@code NotaSaidaDetailView.parceiroFieldItemsFetchCallback}, sem filtro
     * cliente/fornecedor (nunca é aberto pelo usuário aqui, só exibição).
     */
    @Install(to = "parceiroField", subject = "itemsFetchCallback")
    private Stream<Parceiro> parceiroFieldItemsFetchCallback(final Query<Parceiro, String> query) {
        String texto = query.getFilter().orElse("");
        return dataManager.load(Parceiro.class)
                .query("select e from Parceiro e where e.codEmpresa = :codEmpresa "
                        + "and upper(e.nome) like upper(concat('%', :texto, '%')) order by e.nome")
                .parameter("codEmpresa", utilGeralService.getCodEmpresa())
                .parameter("texto", texto)
                .firstResult(query.getOffset())
                .maxResults(query.getLimit())
                .list()
                .stream();
    }
}
