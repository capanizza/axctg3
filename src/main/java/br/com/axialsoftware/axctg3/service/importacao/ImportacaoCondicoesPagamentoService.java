package br.com.axialsoftware.axctg3.service.importacao;

import br.com.axialsoftware.axctg3.entity.cadastros.CondicaoPagamento;
import br.com.axialsoftware.axctg3.entity.enums.TipoLote;
import br.com.axialsoftware.axctg3.entity.importacao.ImpCondicaoPagamento;
import br.com.axialsoftware.axctg3.entity.importacao.ImpLote;
import io.jmix.core.DataManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Importa um {@link ImpLote} de condições de pagamento do legado (CONDICAO) para a empresa do
 * lote — comportamento em {@link ImportacaoCadastroBase}. Faz o papel de
 * {@code exportacao.ExportacaoCondicaoPagamento} do projeto Axial, que gravava direto e pulava o
 * que já existia. ACRESCIMO e VAIBANCO do legado não têm lugar no axctg3.
 */
@Service
public class ImportacaoCondicoesPagamentoService {

    private final DataManager dataManager;

    public ImportacaoCondicoesPagamentoService(DataManager dataManager) {
        this.dataManager = dataManager;
    }

    /** Confere o lote e o compara com as condições de pagamento da empresa. Só grava o relatório no lote. */
    @Transactional
    public ImportacaoPlanoContasService.Relatorio conferir(UUID loteId) {
        return new Importacao(dataManager).conferir(dataManager.load(ImpLote.class).id(loteId).one());
    }

    /** Confere e, sem erro, cria as novas e atualiza as alteradas. Tudo ou nada. */
    @Transactional
    public ImportacaoPlanoContasService.Relatorio importar(UUID loteId) {
        return new Importacao(dataManager).importar(dataManager.load(ImpLote.class).id(loteId).one());
    }

    private static class Importacao
            extends ImportacaoCadastroBase<ImpCondicaoPagamento, CondicaoPagamento, Integer> {

        Importacao(DataManager dataManager) {
            super(dataManager);
        }

        @Override
        protected TipoLote tipo() {
            return TipoLote.CONDICOES_PAGAMENTO;
        }

        @Override
        protected String nome() {
            return "Condição de pagamento";
        }

        @Override
        protected List<ImpCondicaoPagamento> linhas(ImpLote lote) {
            return dataManager.load(ImpCondicaoPagamento.class)
                    .query("select e from ImpCondicaoPagamento e where e.lote = :lote order by e.codigo")
                    .parameter("lote", lote)
                    .list();
        }

        @Override
        protected List<CondicaoPagamento> gravados(ImpLote lote) {
            return dataManager.load(CondicaoPagamento.class)
                    .query("select e from CondicaoPagamento e where e.codEmpresa = :codEmpresa")
                    .parameter("codEmpresa", lote.getCodEmpresa())
                    .list();
        }

        @Override
        protected Integer codigoLinha(ImpCondicaoPagamento linha) {
            return linha.getCodigo();
        }

        @Override
        protected Integer codigoGravado(CondicaoPagamento condicao) {
            return condicao.getCodigo();
        }

        @Override
        protected CondicaoPagamento criar(ImpLote lote, Integer codigo) {
            CondicaoPagamento condicao = dataManager.create(CondicaoPagamento.class);
            condicao.setCodigo(codigo);
            condicao.setCodEmpresa(lote.getCodEmpresa());
            return condicao;
        }

        @Override
        protected void aplicar(ImpLote lote, ImpCondicaoPagamento l, CondicaoPagamento c, Campos campos, String titulo,
                               ImportacaoPlanoContasService.Relatorio relatorio) {
            String nome = texto(l.getNome());
            if (nome == null) {
                relatorio.erros.add(titulo + " sem nome.");
            }
            if (l.getParcelas() == null || l.getParcelas() < 1) {
                relatorio.avisos.add(titulo + ": " + (l.getParcelas() == null ? "sem" : l.getParcelas())
                        + " parcelas no legado; gravada com 1.");
            }
            campos.campo("nome", c::getNome, c::setNome, cortar(nome, 50, titulo, "nome", relatorio));
            campos.campo("parcelas", c::getParcelas, c::setParcelas,
                    l.getParcelas() == null || l.getParcelas() < 1 ? 1 : l.getParcelas());
            campos.campo("primeira", c::getPrimeira, c::setPrimeira, l.getPrimeira() == null ? 0 : l.getPrimeira());
            campos.campo("diferença", c::getDiferenca, c::setDiferenca, l.getDiferenca() == null ? 0 : l.getDiferenca());
        }

        @Override
        protected String descrever(CondicaoPagamento condicao) {
            return condicao.getNome();
        }
    }
}
