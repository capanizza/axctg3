package br.com.axialsoftware.axctg3.service.importacao;

import br.com.axialsoftware.axctg3.entity.contabil.ContaContabil;
import br.com.axialsoftware.axctg3.entity.contabil.HistoricoContabil;
import br.com.axialsoftware.axctg3.entity.enums.TipoLote;
import br.com.axialsoftware.axctg3.entity.financeiro.HistoricoFinanceiro;
import br.com.axialsoftware.axctg3.entity.importacao.ImpHistoricoFinanceiro;
import br.com.axialsoftware.axctg3.entity.importacao.ImpLote;
import io.jmix.core.DataManager;
import io.jmix.core.FetchPlan;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Importa um {@link ImpLote} de históricos financeiros do legado (HISTFIN) para a empresa do
 * lote — comportamento em {@link ImportacaoCadastroBase}. Faz o papel de
 * {@code exportacao.ExportacaoHistFin} do projeto Axial, que gravava direto e pulava o que já
 * existia.
 * <p>
 * Lados como no Axial: ENT (entrada, CODCONTABILENT/HISTENT) é o de pagar, SAI (saída) o de
 * receber. As contas são procuradas no plano do ano mais recente da empresa e os históricos
 * contábeis pelo código; o que não for achado fica vazio, com aviso. Histórico contábil 0 é
 * "sem histórico" no legado. As marcas do legado sem equivalente no axctg3 (comissão,
 * antecipado, documento, lança contábil) não são trazidas.
 */
@Service
public class ImportacaoHistoricosFinanceirosService {

    private final DataManager dataManager;

    public ImportacaoHistoricosFinanceirosService(DataManager dataManager) {
        this.dataManager = dataManager;
    }

    /** Confere o lote e o compara com os históricos financeiros da empresa. Só grava o relatório no lote. */
    @Transactional
    public ImportacaoPlanoContasService.Relatorio conferir(UUID loteId) {
        return new Importacao(dataManager).conferir(dataManager.load(ImpLote.class).id(loteId).one());
    }

    /** Confere e, sem erro, cria os novos e atualiza os alterados. Tudo ou nada. */
    @Transactional
    public ImportacaoPlanoContasService.Relatorio importar(UUID loteId) {
        return new Importacao(dataManager).importar(dataManager.load(ImpLote.class).id(loteId).one());
    }

    /** Uma execução: guarda contas e históricos contábeis entre {@code preparar} e {@code aplicar}. */
    private static class Importacao
            extends ImportacaoCadastroBase<ImpHistoricoFinanceiro, HistoricoFinanceiro, Integer> {

        private Map<String, ContaContabil> contas = Map.of();
        private final Map<Integer, HistoricoContabil> historicos = new HashMap<>();

        Importacao(DataManager dataManager) {
            super(dataManager);
        }

        @Override
        protected TipoLote tipo() {
            return TipoLote.HISTORICOS_FINANCEIROS;
        }

        @Override
        protected String nome() {
            return "Histórico financeiro";
        }

        @Override
        protected List<ImpHistoricoFinanceiro> linhas(ImpLote lote) {
            return dataManager.load(ImpHistoricoFinanceiro.class)
                    .query("select e from ImpHistoricoFinanceiro e where e.lote = :lote order by e.codigo")
                    .parameter("lote", lote)
                    .list();
        }

        @Override
        protected List<HistoricoFinanceiro> gravados(ImpLote lote) {
            return dataManager.load(HistoricoFinanceiro.class)
                    .query("select e from HistoricoFinanceiro e where e.codEmpresa = :codEmpresa")
                    .parameter("codEmpresa", lote.getCodEmpresa())
                    .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE)
                            .add("contaContabilPagar", FetchPlan.BASE)
                            .add("historicoContabilPagar", FetchPlan.BASE)
                            .add("contaContabilReceber", FetchPlan.BASE)
                            .add("historicoContabilReceber", FetchPlan.BASE))
                    .list();
        }

        @Override
        protected Integer codigoLinha(ImpHistoricoFinanceiro linha) {
            return linha.getCodigo();
        }

        @Override
        protected Integer codigoGravado(HistoricoFinanceiro historico) {
            return historico.getCodigo();
        }

        @Override
        protected HistoricoFinanceiro criar(ImpLote lote, Integer codigo) {
            HistoricoFinanceiro historico = dataManager.create(HistoricoFinanceiro.class);
            historico.setCodigo(codigo);
            historico.setCodEmpresa(lote.getCodEmpresa());
            return historico;
        }

        @Override
        protected void preparar(ImpLote lote, ImportacaoPlanoContasService.Relatorio relatorio) {
            contas = contasDoPlanoMaisRecente(lote, "o histórico financeiro aponta para contas", relatorio);
            dataManager.load(HistoricoContabil.class)
                    .query("select e from HistoricoContabil e where e.codEmpresa = :codEmpresa")
                    .parameter("codEmpresa", lote.getCodEmpresa())
                    .list()
                    .forEach(h -> historicos.put(h.getCodigo(), h));
            if (historicos.isEmpty()) {
                relatorio.erros.add("A empresa " + lote.getCodEmpresa() + " não tem históricos contábeis. "
                        + "Importe antes os históricos contábeis: o histórico financeiro aponta para eles.");
            }
        }

        @Override
        protected void aplicar(ImpLote lote, ImpHistoricoFinanceiro l, HistoricoFinanceiro h, Campos campos,
                               String titulo, ImportacaoPlanoContasService.Relatorio relatorio) {
            String nome = texto(l.getNome());
            if (nome == null) {
                relatorio.erros.add(titulo + " sem nome.");
            }
            campos.campo("nome", h::getNome, h::setNome, cortar(nome, 30, titulo, "nome", relatorio));
            campos.campo("emissão", h::getEmissao, h::setEmissao, sim(l.getEmissao()));
            campos.campo("baixa", h::getBaixa, h::setBaixa, sim(l.getBaixa()));
            campos.campo("parcial", h::getParcial, h::setParcial, sim(l.getParcial()));
            campos.campo("juros", h::getJuros, h::setJuros, sim(l.getJuros()));
            campos.campo("desconto", h::getDesconto, h::setDesconto, sim(l.getDesconto()));

            conta(campos, "conta de pagar", h::getContaContabilPagar, h::setContaContabilPagar,
                    l.getCodContabilEnt(), titulo, relatorio);
            historico(campos, "histórico de pagar", h::getHistoricoContabilPagar, h::setHistoricoContabilPagar,
                    l.getHistEnt(), titulo, relatorio);
            conta(campos, "conta de receber", h::getContaContabilReceber, h::setContaContabilReceber,
                    l.getCodContabilSai(), titulo, relatorio);
            historico(campos, "histórico de receber", h::getHistoricoContabilReceber, h::setHistoricoContabilReceber,
                    l.getHistSai(), titulo, relatorio);
        }

        private void conta(Campos campos, String nome, Supplier<ContaContabil> atual, Consumer<ContaContabil> definir,
                           String codigoLegado, String titulo, ImportacaoPlanoContasService.Relatorio relatorio) {
            String codigo = texto(codigoLegado);
            ContaContabil conta = codigo == null ? null : contas.get(codigo);
            if (codigo != null && conta == null) {
                relatorio.avisos.add(titulo + ": " + nome + " " + codigo
                        + " não existe no plano mais recente da empresa; fica vazia.");
            }
            campos.campo(nome, () -> descreverConta(atual.get()), v -> definir.accept(conta), descreverConta(conta));
        }

        private void historico(Campos campos, String nome, Supplier<HistoricoContabil> atual,
                               Consumer<HistoricoContabil> definir, Integer codigo, String titulo,
                               ImportacaoPlanoContasService.Relatorio relatorio) {
            Integer cod = codigo == null || codigo == 0 ? null : codigo;
            HistoricoContabil historico = cod == null ? null : historicos.get(cod);
            if (cod != null && historico == null) {
                relatorio.avisos.add(titulo + ": " + nome + " " + cod
                        + " não existe nos históricos contábeis da empresa; fica vazio.");
            }
            campos.campo(nome, () -> atual.get() == null ? null : atual.get().getCodigo(),
                    v -> definir.accept(historico), historico == null ? null : historico.getCodigo());
        }

        @Override
        protected String descrever(HistoricoFinanceiro historico) {
            return historico.getNome();
        }

        private static Boolean sim(String marca) {
            return "S".equalsIgnoreCase(texto(marca));
        }
    }
}
