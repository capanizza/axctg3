package br.com.axialsoftware.axctg3.view.financeiro.itempagar;

import br.com.axialsoftware.axctg3.entity.financeiro.HistoricoFinanceiro;
import br.com.axialsoftware.axctg3.entity.financeiro.ItemPagar;
import br.com.axialsoftware.axctg3.entity.financeiro.TituloPagar;
import br.com.axialsoftware.axctg3.service.financeiro.UtilFinanceiroService;
import br.com.axialsoftware.axctg3.view.main.MainView;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.Dialogs;
import io.jmix.flowui.action.DialogAction;
import io.jmix.flowui.component.textfield.TypedTextField;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;

@Route(value = "itemPagars/:id", layout = MainView.class)
@ViewController(id = "ItemPagar.detail")
@ViewDescriptor(path = "item-pagar-detail-view.xml")
@EditedEntityContainer("itemPagarDc")
public class ItemPagarDetailView extends StandardDetailView<ItemPagar> {

    @ViewComponent
    private TypedTextField<BigDecimal> jurosField;
    @ViewComponent
    private TypedTextField<BigDecimal> descontoField;
    @ViewComponent
    private TypedTextField<BigDecimal> valorLiquidoField;
    @Autowired
    private Dialogs dialogs;
    @Autowired
    private UtilFinanceiroService utilFinanceiroService;

    @Subscribe
    public void onBeforeShow(final BeforeShowEvent event) {
        ItemPagar itemPagar = getEditedEntity();
        TituloPagar tituloPagar = itemPagar.getTituloPagar();
        if (itemPagar.getValor().compareTo(BigDecimal.ZERO) == 0) {
            itemPagar.setValor(tituloPagar.getValor().subtract(tituloPagar.getValorBaixado()));
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
        ItemPagar itemPagar = getEditedEntity();
        if (itemPagar.getHistoricoFinanceiro() == null) {
            return;
        }
        HistoricoFinanceiro historicoFinanceiro = itemPagar.getHistoricoFinanceiro();
        if (historicoFinanceiro.getBaixa() && itemPagar.getBanco() == null) {
            dialogs.createOptionDialog()
                    .withHeader("Êrro na baixa")
                    .withText("Indicar banco da baixa")
                    .withActions(new DialogAction(DialogAction.Type.OK))
                    .open();
            return;
        }
        TituloPagar tituloPagar = itemPagar.getTituloPagar();
        utilFinanceiroService.verificarBaixa(value, tituloPagar.getValorAberto(), historicoFinanceiro, tituloPagar.getValor(), tituloPagar.getValorBaixado());
    }

}
