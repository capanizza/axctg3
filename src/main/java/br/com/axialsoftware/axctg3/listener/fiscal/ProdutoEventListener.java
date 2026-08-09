package br.com.axialsoftware.axctg3.listener.fiscal;

import br.com.axialsoftware.axctg3.entity.fiscal.Produto;
import br.com.axialsoftware.axctg3.service.UtilGeralService;
import io.jmix.core.event.EntitySavingEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class ProdutoEventListener {

    private final UtilGeralService utilGeralService;

    public ProdutoEventListener(UtilGeralService utilGeralService) {
        this.utilGeralService = utilGeralService;
    }

    @EventListener
    public void onProdutoSaving(final EntitySavingEvent<Produto> event) {
        if (event.isNewEntity()) {
            Produto produto = event.getEntity();
            if (produto.getCodEmpresa() == null) {
                produto.setCodEmpresa(utilGeralService.getCodEmpresa());
            }
            if (produto.getTipoProduto() == null && produto.getGrupoProduto() != null) {
                produto.setTipoProduto(produto.getGrupoProduto().getTipoProduto());
            }
        }
    }
}
