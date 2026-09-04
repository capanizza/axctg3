package br.com.axialsoftware.axctg3.view.fiscal.notasaida;

import br.com.axialsoftware.axctg3.entity.fiscal.NotaSaida;
import br.com.axialsoftware.axctg3.view.main.MainView;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.view.*;

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
 */
@Route(value = "nota-saidas-complementar/:id", layout = MainView.class)
@ViewController(id = "NotaSaida.complementar")
@ViewDescriptor(path = "nota-saida-complementar-detail-view.xml")
@EditedEntityContainer("notaSaidaDc")
public class NotaSaidaComplementarDetailView extends StandardDetailView<NotaSaida> {
}
