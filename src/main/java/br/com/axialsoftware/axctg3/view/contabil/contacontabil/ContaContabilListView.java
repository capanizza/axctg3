package br.com.axialsoftware.axctg3.view.contabil.contacontabil;

import br.com.axialsoftware.axctg3.bean.MenuBean;
import br.com.axialsoftware.axctg3.entity.contabil.ContaContabil;

import br.com.axialsoftware.axctg3.service.FormatacaoService;
import br.com.axialsoftware.axctg3.service.UtilGeralService;
import br.com.axialsoftware.axctg3.view.main.MainView;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.data.renderer.Renderer;
import com.vaadin.flow.data.renderer.TextRenderer;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.UiComponents;
import io.jmix.flowui.component.UiComponentUtils;
import io.jmix.flowui.component.checkbox.JmixCheckbox;
import io.jmix.flowui.kit.action.ActionPerformedEvent;
import io.jmix.flowui.model.CollectionLoader;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashMap;
import java.util.Map;


@Route(value = "conta-contabils", layout = MainView.class)
@ViewController(id = "ContaContabil.list")
@ViewDescriptor(path = "conta-contabil-list-view.xml")
@LookupComponent("contaContabilsDataGrid")
@DialogMode(width = "64em")
public class ContaContabilListView extends StandardListView<ContaContabil> {

    static Boolean isNew = false;
    
    @ViewComponent
    private CollectionLoader<ContaContabil> contaContabilsDl;
    @Autowired
    private UtilGeralService utilGeralService;
    @ViewComponent
    private HorizontalLayout buttonsPanel;
    @Autowired
    private UiComponents uiComponents;
    @Autowired
    private FormatacaoService formatacaoService;
    @Autowired
    private MenuBean menuBean;

    @Subscribe
    public void onBeforeShow(final BeforeShowEvent event) {
        Dialog dialog = UiComponentUtils.findDialog(this);

        contaContabilsDl.setParameter("codEmpresa", utilGeralService.getCodEmpresa());
        contaContabilsDl.setParameter("ano", utilGeralService.getAnoContabil());
        // Lançamentos contábeis só podem ser feitos em contas analíticas — quando esta
        // tela abre como dialog (lookup do contaDevedoraField/contaCredoraField do
        // Lancamento), restringe a listagem a elas. Fora do dialog (gestão do plano de
        // contas), mostra tudo, inclusive as sintéticas.
        contaContabilsDl.setParameter("apenasAnaliticas", dialog != null);
        contaContabilsDl.load();

        buttonsPanel.setVisible(dialog == null);
    }

    @Override
    public String getPageTitle() {
        String title = super.getPageTitle();
        if (isNew) {
            isNew = false;
        } else {
            int ano = utilGeralService.getAnoContabil();
            String title2 = "ano contábil " + ano;
            title = title + " "  + title2;
        }
        return title;
    }

    @Supply(to = "contaContabilsDataGrid.codigo", subject = "renderer")
    private Renderer<ContaContabil> contaContabilsDataGridCodigoRenderer() {
        return new TextRenderer<>(contaContabil ->
                formatacaoService.formatacaoMascContabil(contaContabil.getCodigo()));
    }

    @Supply(to = "contaContabilsDataGrid.analitica", subject = "renderer")
    private Renderer<ContaContabil> contaContabilsDataGridAnaliticaRenderer() {
        return new ComponentRenderer<>(contaContabil -> {
            JmixCheckbox checkbox = uiComponents.create(JmixCheckbox.class);
            checkbox.setValue(Boolean.TRUE.equals(contaContabil.getAnalitica()));
            checkbox.setReadOnly(true);
            checkbox.addClassName("grid-value-checkbox");
            return checkbox;
        });
    }

    @Subscribe("listagemSwitcher.listagemItem.listagemAction")
    public void onListagemSwitcherListagemItemListagemAction(final ActionPerformedEvent event) {
        menuBean.listarContasContabeis();
    }

    @Subscribe("listagemSwitcher.balanceteItem.balanceteAction")
    public void onListagemSwitcherBalanceteItemBalanceteAction(final ActionPerformedEvent event) {
        Map<String, Object> parameters = new HashMap<String, Object>();
        parameters.put("tipo", "1");
        menuBean.listarBalancete(parameters);
    }

    @Subscribe("listagemSwitcher.balancoItem.balancoAction")
    public void onListagemSwitcherBalancoItemBalancoAction(final ActionPerformedEvent event) {
        Map<String, Object> parameters = new HashMap<String, Object>();
        parameters.put("tipo", "2");
        menuBean.listarBalancete(parameters);
    }

    @Subscribe("contaContabilsDataGrid.verificarAction")
    public void onContaContabilsDataGridVerificarAction(final ActionPerformedEvent event) {
        menuBean.verificarContas();
    }
}