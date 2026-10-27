package br.com.axialsoftware.axctg3.service.importacao;

import br.com.axialsoftware.axctg3.entity.enums.TipoLote;
import br.com.axialsoftware.axctg3.entity.enums.TipoProduto;
import br.com.axialsoftware.axctg3.entity.fiscal.GrupoProduto;
import br.com.axialsoftware.axctg3.entity.importacao.ImpGrupoProduto;
import br.com.axialsoftware.axctg3.entity.importacao.ImpLote;
import io.jmix.core.DataManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Importa um {@link ImpLote} de grupos de produtos do legado (GRUPOPROD) para a empresa do lote —
 * comportamento em {@link ImportacaoCadastroBase}. Faz o papel de
 * {@code exportacao.ExportacaoGrupoProduto} do projeto Axial, que gravava direto e pulava o que
 * já existia. Vem antes dos produtos, que se ligam ao grupo pelo código.
 * <p>
 * O tipo do produto é obrigatório no axctg3, mas há bancos do legado sem a coluna (GB): grupo
 * novo sem tipo é gravado como "Outras" (99), com aviso, e a reimportação sem tipo não troca o
 * que já foi escolhido no axctg3. CODCONTABIL e CENTRO do legado não têm lugar no grupo.
 */
@Service
public class ImportacaoGruposProdutoService {

    private final DataManager dataManager;

    public ImportacaoGruposProdutoService(DataManager dataManager) {
        this.dataManager = dataManager;
    }

    /** Confere o lote e o compara com os grupos de produtos da empresa. Só grava o relatório no lote. */
    @Transactional
    public ImportacaoPlanoContasService.Relatorio conferir(UUID loteId) {
        return new Importacao(dataManager).conferir(dataManager.load(ImpLote.class).id(loteId).one());
    }

    /** Confere e, sem erro, cria os novos e atualiza os alterados. Tudo ou nada. */
    @Transactional
    public ImportacaoPlanoContasService.Relatorio importar(UUID loteId) {
        return new Importacao(dataManager).importar(dataManager.load(ImpLote.class).id(loteId).one());
    }

    private static class Importacao extends ImportacaoCadastroBase<ImpGrupoProduto, GrupoProduto, Integer> {

        Importacao(DataManager dataManager) {
            super(dataManager);
        }

        @Override
        protected TipoLote tipo() {
            return TipoLote.GRUPOS_PRODUTO;
        }

        @Override
        protected String nome() {
            return "Grupo de produtos";
        }

        @Override
        protected List<ImpGrupoProduto> linhas(ImpLote lote) {
            return dataManager.load(ImpGrupoProduto.class)
                    .query("select e from ImpGrupoProduto e where e.lote = :lote order by e.codigo")
                    .parameter("lote", lote)
                    .list();
        }

        @Override
        protected List<GrupoProduto> gravados(ImpLote lote) {
            return dataManager.load(GrupoProduto.class)
                    .query("select e from GrupoProduto e where e.codEmpresa = :codEmpresa")
                    .parameter("codEmpresa", lote.getCodEmpresa())
                    .list();
        }

        @Override
        protected Integer codigoLinha(ImpGrupoProduto linha) {
            return linha.getCodigo();
        }

        @Override
        protected Integer codigoGravado(GrupoProduto grupo) {
            return grupo.getCodigo();
        }

        @Override
        protected GrupoProduto criar(ImpLote lote, Integer codigo) {
            GrupoProduto grupo = dataManager.create(GrupoProduto.class);
            grupo.setCodigo(codigo);
            grupo.setCodEmpresa(lote.getCodEmpresa());
            return grupo;
        }

        @Override
        protected void aplicar(ImpLote lote, ImpGrupoProduto l, GrupoProduto g, Campos campos, String titulo,
                               ImportacaoPlanoContasService.Relatorio relatorio) {
            String nome = texto(l.getNome());
            if (nome == null) {
                relatorio.erros.add(titulo + " sem nome.");
            }
            campos.campo("nome", g::getNome, g::setNome, cortar(nome, 30, titulo, "nome", relatorio));

            TipoProduto tipo = l.getTipoProduto() == null ? null : TipoProduto.fromId(l.getTipoProduto());
            if (l.getTipoProduto() != null && tipo == null) {
                relatorio.avisos.add(titulo + ": tipo de produto " + l.getTipoProduto() + " não existe no axctg3.");
            }
            if (tipo == null && g.getTipoProduto() == null) {
                tipo = TipoProduto.OUTRAS;
                relatorio.avisos.add(titulo + ": sem tipo de produto no legado; gravado como \"Outras\".");
            }
            if (tipo != null) {
                campos.campo("tipo de produto", g::getTipoProduto, g::setTipoProduto, tipo);
            }
        }

        @Override
        protected String descrever(GrupoProduto grupo) {
            return grupo.getNome();
        }
    }
}
