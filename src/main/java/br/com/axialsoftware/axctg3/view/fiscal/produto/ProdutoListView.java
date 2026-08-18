package br.com.axialsoftware.axctg3.view.fiscal.produto;

import br.com.axialsoftware.axctg3.entity.cadastros.ConfigRel;
import br.com.axialsoftware.axctg3.entity.enums.TipoProduto;
import br.com.axialsoftware.axctg3.entity.fiscal.Produto;
import br.com.axialsoftware.axctg3.service.UtilGeralService;
import br.com.axialsoftware.axctg3.view.main.MainView;

import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.data.renderer.Renderer;
import com.vaadin.flow.router.Route;
import io.jmix.core.DataManager;
import io.jmix.core.SaveContext;
import io.jmix.flowui.Dialogs;
import io.jmix.flowui.UiComponents;
import io.jmix.flowui.app.inputdialog.DialogActions;
import io.jmix.flowui.app.inputdialog.DialogOutcome;
import io.jmix.flowui.component.UiComponentUtils;
import io.jmix.flowui.component.checkbox.JmixCheckbox;
import io.jmix.flowui.kit.action.ActionPerformedEvent;
import io.jmix.flowui.model.CollectionLoader;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;

import static io.jmix.flowui.app.inputdialog.InputParameter.enumParameter;

@Route(value = "produtos", layout = MainView.class)
@ViewController(id = "Produto.list")
@ViewDescriptor(path = "produto-list-view.xml")
@LookupComponent("produtosDataGrid")
@DialogMode(width = "64em")
public class ProdutoListView extends StandardListView<Produto> {

    @ViewComponent
    private CollectionLoader<Produto> produtosDl;
    @Autowired
    private UtilGeralService utilGeralService;
    @ViewComponent
    private HorizontalLayout buttonsPanel;
    @Autowired
    private UiComponents uiComponents;
    @Autowired
    private Dialogs dialogs;
    @Autowired
    private DataManager dataManager;

    @Subscribe
    public void onBeforeShow(final BeforeShowEvent event) {
        ConfigRel configRel = utilGeralService.prepararConfigRel();
        TipoProduto tipoProduto = configRel.getTipoProduto();
        produtosDl.setParameter("codEmpresa", utilGeralService.getCodEmpresa());
        produtosDl.setParameter("tipoProduto", tipoProduto == null ? null : tipoProduto.getId());
        produtosDl.setParameter("listarTodos", tipoProduto == null ? "sim" : "nao");
        produtosDl.load();

        Dialog dialog = UiComponentUtils.findDialog(this);
        buttonsPanel.setVisible(dialog == null);
    }

    @Subscribe("produtosDataGrid.selecionarAction")
    public void onProdutosDataGridSelecionarAction(final ActionPerformedEvent event) {
        ConfigRel configRel = utilGeralService.prepararConfigRel();
        dialogs.createInputDialog(UiComponentUtils.getCurrentView())
                .withHeader("Selecionar produtos")
                .withParameters(
                        enumParameter("tipoProduto", TipoProduto.class)
                                .withLabel("Tipo de produto (em branco = todos)")
                                .withDefaultValue(configRel.getTipoProduto())
                )
                .withActions(DialogActions.OK_CANCEL)
                .withCloseListener(closeEvent -> {
                    if (closeEvent.closedWith(DialogOutcome.OK)) {
                        TipoProduto tipoProduto = closeEvent.getValue("tipoProduto");
                        SaveContext saveContext = new SaveContext();
                        configRel.setTipoProduto(tipoProduto);
                        saveContext.saving(configRel);
                        dataManager.save(saveContext);
                        produtosDl.setParameter("codEmpresa", utilGeralService.getCodEmpresa());
                        produtosDl.setParameter("tipoProduto", tipoProduto == null ? null : tipoProduto.getId());
                        produtosDl.setParameter("listarTodos", tipoProduto == null ? "sim" : "nao");
                        produtosDl.load();
                    }
                })
                .open();
    }

    @Supply(to = "produtosDataGrid.inativo", subject = "renderer")
    private Renderer<Produto> produtosDataGridInativoRenderer() {
        return new ComponentRenderer<>(produto -> {
            JmixCheckbox checkbox = uiComponents.create(JmixCheckbox.class);
            checkbox.setValue(Boolean.TRUE.equals(produto.getInativo()));
            checkbox.setReadOnly(true);
            checkbox.addClassName("grid-value-checkbox");
            return checkbox;
        });
    }
}
