package br.com.axialsoftware.axctg3.view.financeiro.itemreceber;

import br.com.axialsoftware.axctg3.entity.financeiro.HistoricoFinanceiro;
import br.com.axialsoftware.axctg3.entity.financeiro.ItemReceber;
import br.com.axialsoftware.axctg3.entity.financeiro.TituloReceber;
import br.com.axialsoftware.axctg3.service.financeiro.UtilFinanceiroService;
import br.com.axialsoftware.axctg3.view.main.MainView;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.Dialogs;
import io.jmix.flowui.action.DialogAction;
import io.jmix.flowui.component.textfield.TypedTextField;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;

@Route(value = "itemRecebers/:id", layout = MainView.class)
@ViewController(id = "ItemReceber.detail")
@ViewDescriptor(path = "item-receber-detail-view.xml")
@EditedEntityContainer("itemReceberDc")
@DialogMode(width = "800px")
public class ItemReceberDetailView extends StandardDetailView<ItemReceber> {

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
        ItemReceber itemReceber = getEditedEntity();
        TituloReceber tituloReceber = itemReceber.getTituloReceber();
        if (itemReceber.getValor().compareTo(BigDecimal.ZERO) == 0) {
            itemReceber.setValor(tituloReceber.getValor().subtract(tituloReceber.getValorBaixado()));
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
        ItemReceber itemReceber = getEditedEntity();
        if (itemReceber.getHistoricoFinanceiro() == null) {
            return;
        }
        TituloReceber tituloReceber = itemReceber.getTituloReceber();
        HistoricoFinanceiro historicoFinanceiro = itemReceber.getHistoricoFinanceiro();
        utilFinanceiroService.verificarBaixa(value, tituloReceber.getValorAberto(), historicoFinanceiro, tituloReceber.getValor(), tituloReceber.getValorBaixado());
    }

}
