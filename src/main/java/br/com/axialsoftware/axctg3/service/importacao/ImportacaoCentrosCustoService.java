package br.com.axialsoftware.axctg3.service.importacao;

import br.com.axialsoftware.axctg3.entity.cadastros.CentroCusto;
import br.com.axialsoftware.axctg3.entity.enums.TipoLote;
import br.com.axialsoftware.axctg3.entity.importacao.ImpCentroCusto;
import br.com.axialsoftware.axctg3.entity.importacao.ImpLote;
import io.jmix.core.DataManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Importa um {@link ImpLote} de centros de custo do legado (CENTRO) para a empresa do lote —
 * comportamento em {@link ImportacaoCadastroBase}. Faz o papel de
 * {@code exportacao.ExportacaoCentroCusto} do projeto Axial, que gravava direto e pulava o que
 * já existia. O código é texto, como no legado; o REDUZIDO do legado não tem lugar no axctg3.
 */
@Service
public class ImportacaoCentrosCustoService {

    private final DataManager dataManager;

    public ImportacaoCentrosCustoService(DataManager dataManager) {
        this.dataManager = dataManager;
    }

    /** Confere o lote e o compara com os centros de custo da empresa. Só grava o relatório no lote. */
    @Transactional
    public ImportacaoPlanoContasService.Relatorio conferir(UUID loteId) {
        return new Importacao(dataManager).conferir(dataManager.load(ImpLote.class).id(loteId).one());
    }

    /** Confere e, sem erro, cria os novos e atualiza os alterados. Tudo ou nada. */
    @Transactional
    public ImportacaoPlanoContasService.Relatorio importar(UUID loteId) {
        return new Importacao(dataManager).importar(dataManager.load(ImpLote.class).id(loteId).one());
    }

    private static class Importacao extends ImportacaoCadastroBase<ImpCentroCusto, CentroCusto, String> {

        Importacao(DataManager dataManager) {
            super(dataManager);
        }

        @Override
        protected TipoLote tipo() {
            return TipoLote.CENTROS_CUSTO;
        }

        @Override
        protected String nome() {
            return "Centro de custo";
        }

        @Override
        protected List<ImpCentroCusto> linhas(ImpLote lote) {
            return dataManager.load(ImpCentroCusto.class)
                    .query("select e from ImpCentroCusto e where e.lote = :lote order by e.codigo")
                    .parameter("lote", lote)
                    .list();
        }

        @Override
        protected List<CentroCusto> gravados(ImpLote lote) {
            return dataManager.load(CentroCusto.class)
                    .query("select e from CentroCusto e where e.codEmpresa = :codEmpresa")
                    .parameter("codEmpresa", lote.getCodEmpresa())
                    .list();
        }

        @Override
        protected String codigoLinha(ImpCentroCusto linha) {
            return texto(linha.getCodigo());
        }

        @Override
        protected String codigoGravado(CentroCusto centroCusto) {
            return centroCusto.getCodigo();
        }

        @Override
        protected CentroCusto criar(ImpLote lote, String codigo) {
            CentroCusto centroCusto = dataManager.create(CentroCusto.class);
            centroCusto.setCodigo(codigo);
            centroCusto.setCodEmpresa(lote.getCodEmpresa());
            return centroCusto;
        }

        @Override
        protected void aplicar(ImpLote lote, ImpCentroCusto l, CentroCusto c, Campos campos, String titulo,
                               ImportacaoPlanoContasService.Relatorio relatorio) {
            if (c.getCodigo() != null && c.getCodigo().length() > 10) {
                relatorio.erros.add(titulo + ": código com mais de 10 caracteres.");
            }
            String nome = texto(l.getNome());
            if (nome == null) {
                relatorio.erros.add(titulo + " sem nome.");
            }
            campos.campo("nome", c::getNome, c::setNome, cortar(nome, 50, titulo, "nome", relatorio));
        }

        @Override
        protected String descrever(CentroCusto centroCusto) {
            return centroCusto.getNome();
        }
    }
}
