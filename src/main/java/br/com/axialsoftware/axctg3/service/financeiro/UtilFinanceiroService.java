package br.com.axialsoftware.axctg3.service.financeiro;

import br.com.axialsoftware.axctg3.entity.contabil.ContaContabil;
import br.com.axialsoftware.axctg3.entity.contabil.HistoricoContabil;
import br.com.axialsoftware.axctg3.entity.contabil.Lancamento;
import br.com.axialsoftware.axctg3.entity.financeiro.Banco;
import br.com.axialsoftware.axctg3.entity.financeiro.DiversoPagar;
import br.com.axialsoftware.axctg3.entity.financeiro.HistoricoFinanceiro;
import br.com.axialsoftware.axctg3.entity.financeiro.ItemDiversoPagar;
import br.com.axialsoftware.axctg3.entity.financeiro.ItemPagar;
import br.com.axialsoftware.axctg3.entity.financeiro.ItemReceber;
import br.com.axialsoftware.axctg3.entity.financeiro.MovimentoBanco;
import br.com.axialsoftware.axctg3.entity.financeiro.TituloPagar;
import br.com.axialsoftware.axctg3.service.UtilGeralService;
import io.jmix.core.DataManager;
import io.jmix.flowui.Dialogs;
import io.jmix.flowui.action.DialogAction;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Utilitário do módulo financeiro, trazido do axctg-flow. {@code gerarLancamento} é o
 * ponto único de postagem contábil das entidades financeiras (diversos, títulos) — o
 * {@code origem} do {@link Lancamento} identifica a entidade de origem. O restante do
 * ciclo de vida do lançamento (numero via Sequence, ano/mes/dia/dataLancamento,
 * atualização de saldos) é feito por {@code LancamentoEventListener}, não aqui.
 */
@Service
public class UtilFinanceiroService {

    private final Dialogs dialogs;
    private final DataManager dataManager;
    private final UtilGeralService utilGeralService;

    public UtilFinanceiroService(Dialogs dialogs, DataManager dataManager, UtilGeralService utilGeralService) {
        this.dialogs = dialogs;
        this.dataManager = dataManager;
        this.utilGeralService = utilGeralService;
    }

    /**
     * Confirma que o valor de uma baixa é compatível com o título/diverso: não pode
     * exceder o valor em aberto, e uma baixa menor que o valor total só é aceita se o
     * histórico financeiro permitir baixa parcial.
     */
    public void verificarBaixa(BigDecimal valor, BigDecimal valorAberto, HistoricoFinanceiro hist, BigDecimal valorTitulo, BigDecimal valorBaixado) {
        if (valor.compareTo(valorAberto) > 0) {
            dialogs.createOptionDialog()
                    .withHeader("Erro na baixa")
                    .withText("Valor da baixa maior que valor do título")
                    .withActions(new DialogAction(DialogAction.Type.OK))
                    .open();
            return;
        }
        Boolean parcial = hist.getParcial();
        if (valor.compareTo(valorTitulo) < 0) {
            if (!parcial) {
                dialogs.createOptionDialog()
                        .withHeader("Erro na baixa")
                        .withText("Valor da baixa menor que valor do título, use baixa parcial")
                        .withActions(new DialogAction(DialogAction.Type.OK))
                        .open();
                return;
            }
            if (valorBaixado.add(valor).compareTo(valorTitulo) > 0) {
                dialogs.createOptionDialog()
                        .withHeader("Erro na baixa")
                        .withText("Valor da baixa + valor baixado maior que valor do título")
                        .withActions(new DialogAction(DialogAction.Type.OK))
                        .open();
                return;
            }
        }
        if (parcial && valor.compareTo(valorTitulo) == 0) {
            dialogs.createOptionDialog()
                    .withHeader("Erro na baixa")
                    .withText("Use baixa total")
                    .withActions(new DialogAction(DialogAction.Type.OK))
                    .open();
        }
    }

    public void gerarLancamento(Object objeto,
                                 int dia,
                                 ContaContabil contaDevedora,
                                 ContaContabil contaCredora,
                                 BigDecimal valor,
                                 HistoricoContabil historico,
                                 String complementoHistorico) {
        String origem = switch (objeto) {
            case DiversoPagar ignored -> "em diverso";
            case ItemDiversoPagar ignored -> "bx diverso";
            case ItemReceber ignored -> "bx receber";
            case TituloPagar ignored -> "em pagar";
            case ItemPagar ignored -> "bx pagar";
            case MovimentoBanco ignored -> "mov banco";
            default -> null;
        };
        if (origem == null) {
            return;
        }
        Lancamento lancamento = dataManager.create(Lancamento.class);
        lancamento.setDia(dia);
        lancamento.setContaDevedora(contaDevedora);
        lancamento.setContaCredora(contaCredora);
        lancamento.setValor(valor);
        lancamento.setHistoricoContabil(historico);
        lancamento.setComplementoHistorico(complementoHistorico);
        lancamento.setOrigem(origem);
        dataManager.save(lancamento);
    }

    public String getNomeBanco(Integer codigo) {
        if (codigo == null) {
            return "";
        }
        Banco banco = dataManager.load(Banco.class)
                .query("select e from Banco e " +
                        "where e.codigo = :codigo " +
                        "and e.codEmpresa = :codEmpresa")
                .parameter("codigo", codigo)
                .parameter("codEmpresa", utilGeralService.getCodEmpresa())
                .one();
        return String.format("%03d %03d %s", codigo, banco.getCodGeral(), banco.getNome());
    }

}
