package br.com.axialsoftware.axctg3.view.financeiro.itemdiversopagar;

import br.com.axialsoftware.axctg3.entity.financeiro.Banco;
import br.com.axialsoftware.axctg3.entity.financeiro.DiversoPagar;
import br.com.axialsoftware.axctg3.entity.financeiro.HistoricoFinanceiro;
import br.com.axialsoftware.axctg3.entity.financeiro.ItemDiversoPagar;
import br.com.axialsoftware.axctg3.service.UtilGeralService;
import br.com.axialsoftware.axctg3.service.financeiro.UtilFinanceiroService;
import br.com.axialsoftware.axctg3.view.main.MainView;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.Dialogs;
import io.jmix.flowui.action.DialogAction;
import io.jmix.flowui.component.textfield.TypedTextField;
import io.jmix.flowui.model.CollectionLoader;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;

@Route(value = "itemDiversoPagars/:id", layout = MainView.class)
@ViewController(id = "ItemDiversoPagar.detail")
@ViewDescriptor(path = "item-diverso-pagar-detail-view.xml")
@EditedEntityContainer("itemDiversoPagarDc")
public class ItemDiversoPagarDetailView extends StandardDetailView<ItemDiversoPagar> {

    @ViewComponent
    private CollectionLoader<Banco> bancosDl;
    @Autowired
    private UtilGeralService utilGeralService;
    @ViewComponent
    private CollectionLoader<HistoricoFinanceiro> historicosFinanceirosDl;
    @Autowired
    private Dialogs dialogs;
    @ViewComponent
    private TypedTextField<BigDecimal> jurosField;
    @ViewComponent
    private TypedTextField<BigDecimal> descontoField;
    @ViewComponent
    private TypedTextField<BigDecimal> valorLiquidoField;
    @Autowired
    private UtilFinanceiroService utilFinanceiroService;

    @Subscribe
    public void onBeforeShow(final BeforeShowEvent event) {
        historicosFinanceirosDl.setParameter("codEmpresa", utilGeralService.getCodEmpresa());
        historicosFinanceirosDl.load();

        bancosDl.setParameter("codEmpresa", utilGeralService.getCodEmpresa());
        bancosDl.load();

        ItemDiversoPagar itemDiversoPagar = getEditedEntity();
        DiversoPagar diversoPagar = itemDiversoPagar.getDiversoPagar();
        if (itemDiversoPagar.getValor().compareTo(BigDecimal.ZERO) == 0) {
            itemDiversoPagar.setValor(diversoPagar.getValor().subtract(diversoPagar.getValorBaixado()));
        }

        jurosField.setEnabled(false);
        descontoField.setEnabled(false);
        valorLiquidoField.setEnabled(false);
    }

    @Install(to = "historicoFinanceiroField", subject = "validator")
    private void historicoFinanceiroFieldValidator(final HistoricoFinanceiro value) {
        if (!value.getBaixa()) {
            dialogs.createOptionDialog()
                    .withHeader("Êrro na baixa")
                    .withText("Histórico não é de baixa")
                    .withActions(new DialogAction(DialogAction.Type.OK))
                    .open();
            return;
        }
        jurosField.setEnabled(false);
        descontoField.setEnabled(false);
        if (value.getJuros()) {
            jurosField.setEnabled(true);
        }
        if (value.getDesconto()) {
            descontoField.setEnabled(true);
        }
    }

    @Install(to = "valorField", subject = "validator")
    private void valorFieldValidator(final BigDecimal value) {
        ItemDiversoPagar itemDiversoPagar = getEditedEntity();
        if (itemDiversoPagar.getHistoricoFinanceiro() == null) {
            return;
        }
        HistoricoFinanceiro historicoFinanceiro = itemDiversoPagar.getHistoricoFinanceiro();
        if (historicoFinanceiro.getBaixa() && itemDiversoPagar.getBanco() == null) {
            dialogs.createOptionDialog()
                    .withHeader("Êrro na baixa")
                    .withText("Indicar banco da baixa")
                    .withActions(new DialogAction(DialogAction.Type.OK))
                    .open();
            return;
        }
        DiversoPagar diversoPagar = itemDiversoPagar.getDiversoPagar();
        utilFinanceiroService.verificarBaixa(value, diversoPagar.getValorAberto(), historicoFinanceiro, diversoPagar.getValor(), diversoPagar.getValorBaixado());
    }

}
