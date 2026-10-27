package br.com.axialsoftware.axctg3.service.importacao;

import br.com.axialsoftware.axctg3.entity.enums.TipoLote;
import br.com.axialsoftware.axctg3.entity.fiscal.GrupoProduto;
import br.com.axialsoftware.axctg3.entity.fiscal.Produto;
import br.com.axialsoftware.axctg3.entity.importacao.ImpLote;
import br.com.axialsoftware.axctg3.entity.importacao.ImpProduto;
import br.com.axialsoftware.axctg3.entity.tabelas.ClassTrib;
import br.com.axialsoftware.axctg3.entity.tabelas.ClassificacaoFiscal;
import io.jmix.core.DataManager;
import io.jmix.core.FetchPlan;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Importa um {@link ImpLote} de produtos do legado (PRODUTO) para a empresa do lote —
 * comportamento em {@link ImportacaoCadastroBase}. Faz o papel de
 * {@code exportacao.ExportacaoProduto} do projeto Axial, que gravava direto e pulava o que já
 * existia.
 * <p>
 * Os grupos de produtos vêm antes (lote próprio): o produto se liga ao grupo pelo código e, sem
 * tipo próprio, herda o tipo dele. Lote com grupos e empresa sem nenhum grupo é erro. A
 * classificação fiscal vem pelo NCM (a tabela NCM do Siscomex substituiu as classificações do
 * legado): NCM ou grupo que não existe no axctg3 deixa o produto como estava, com um aviso por
 * NCM/grupo. Sem descrição, usa o apelido; sem apelido, o começo da descrição (como a rotina
 * antiga). O cClassTrib, obrigatório e sem equivalente no legado, entra como 000001
 * (tributação integral) só no produto novo — o que foi escolhido no axctg3 não muda. CST, rua,
 * lado e caixa ficam como estão.
 */
@Service
public class ImportacaoProdutosService {

    private final DataManager dataManager;

    public ImportacaoProdutosService(DataManager dataManager) {
        this.dataManager = dataManager;
    }

    /** Confere o lote e o compara com os produtos da empresa. Só grava o relatório no lote. */
    @Transactional
    public ImportacaoPlanoContasService.Relatorio conferir(UUID loteId) {
        return new Importacao(dataManager).conferir(dataManager.load(ImpLote.class).id(loteId).one());
    }

    /** Confere e, sem erro, cria os novos e atualiza os alterados. Tudo ou nada. */
    @Transactional
    public ImportacaoPlanoContasService.Relatorio importar(UUID loteId) {
        return new Importacao(dataManager).importar(dataManager.load(ImpLote.class).id(loteId).one());
    }

    /** Uma execução: guarda grupos, NCMs e o cClassTrib padrão entre {@code preparar} e {@code aplicar}. */
    private static class Importacao extends ImportacaoCadastroBase<ImpProduto, Produto, Integer> {

        private static final int CLASS_TRIB_PADRAO = 1;

        private final Map<Integer, GrupoProduto> grupos = new HashMap<>();
        private final Map<String, ClassificacaoFiscal> classificacoes = new HashMap<>();
        private ClassTrib classTribPadrao;

        Importacao(DataManager dataManager) {
            super(dataManager);
        }

        @Override
        protected TipoLote tipo() {
            return TipoLote.PRODUTOS;
        }

        @Override
        protected String nome() {
            return "Produto";
        }

        @Override
        protected List<ImpProduto> linhas(ImpLote lote) {
            return dataManager.load(ImpProduto.class)
                    .query("select e from ImpProduto e where e.lote = :lote order by e.codigo")
                    .parameter("lote", lote)
                    .list();
        }

        @Override
        protected List<Produto> gravados(ImpLote lote) {
            return dataManager.load(Produto.class)
                    .query("select e from Produto e where e.codEmpresa = :codEmpresa")
                    .parameter("codEmpresa", lote.getCodEmpresa())
                    .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE)
                            .add("grupoProduto", FetchPlan.BASE)
                            .add("classificacaoFiscal", FetchPlan.BASE)
                            .add("classTrib", FetchPlan.BASE))
                    .list();
        }

        @Override
        protected Integer codigoLinha(ImpProduto linha) {
            return linha.getCodigo();
        }

        @Override
        protected Integer codigoGravado(Produto produto) {
            return produto.getCodigo();
        }

        @Override
        protected Produto criar(ImpLote lote, Integer codigo) {
            Produto produto = dataManager.create(Produto.class);
            produto.setCodigo(codigo);
            produto.setCodEmpresa(lote.getCodEmpresa());
            produto.setClassTrib(classTribPadrao);
            return produto;
        }

        @Override
        protected void preparar(ImpLote lote, ImportacaoPlanoContasService.Relatorio relatorio) {
            classTribPadrao = dataManager.load(ClassTrib.class)
                    .query("select e from ClassTrib e where e.codigo = :codigo")
                    .parameter("codigo", CLASS_TRIB_PADRAO)
                    .optional()
                    .orElse(null);
            if (classTribPadrao == null) {
                relatorio.erros.add("O cClassTrib 000001 (tributação integral), usado nos produtos novos, não está"
                        + " cadastrado. Importe a tabela de cClassTrib antes.");
            }

            List<ImpProduto> linhas = linhas(lote);
            dataManager.load(GrupoProduto.class)
                    .query("select e from GrupoProduto e where e.codEmpresa = :codEmpresa")
                    .parameter("codEmpresa", lote.getCodEmpresa())
                    .list()
                    .forEach(g -> grupos.put(g.getCodigo(), g));
            Map<Integer, List<Integer>> semGrupo = new TreeMap<>(linhas.stream()
                    .filter(l -> l.getCodigo() != null && grupoDe(l) != null && !grupos.containsKey(grupoDe(l)))
                    .collect(Collectors.groupingBy(this::grupoDe,
                            Collectors.mapping(ImpProduto::getCodigo, Collectors.toList()))));
            if (!semGrupo.isEmpty() && grupos.isEmpty()) {
                relatorio.erros.add("A empresa " + lote.getCodEmpresa()
                        + " não tem grupos de produtos. Importe antes os grupos de produtos.");
            } else {
                semGrupo.forEach((grupo, produtos) -> relatorio.avisos.add("Grupo de produtos " + grupo
                        + " não existe no axctg3; fica o grupo que já havia em " + resumir(produtos) + "."));
            }

            List<String> ncms = linhas.stream().map(Importacao::ncmDe).filter(n -> n != null).distinct().toList();
            if (!ncms.isEmpty()) {
                dataManager.load(ClassificacaoFiscal.class)
                        .query("select e from ClassificacaoFiscal e where e.codNcm in :ncms")
                        .parameter("ncms", ncms)
                        .list()
                        .forEach(c -> classificacoes.put(c.getCodNcm(), c));
            }
            new TreeMap<>(linhas.stream()
                    .filter(l -> l.getCodigo() != null && ncmDe(l) != null && !classificacoes.containsKey(ncmDe(l)))
                    .collect(Collectors.groupingBy(Importacao::ncmDe,
                            Collectors.mapping(ImpProduto::getCodigo, Collectors.toList()))))
                    .forEach((ncm, produtos) -> relatorio.avisos.add("NCM " + ncm
                            + " não existe na tabela NCM; fica a classificação fiscal que já havia em "
                            + resumir(produtos) + "."));
        }

        @Override
        protected void aplicar(ImpLote lote, ImpProduto l, Produto p, Campos campos, String titulo,
                               ImportacaoPlanoContasService.Relatorio relatorio) {
            String descricao = texto(l.getDescricao());
            String apelido = texto(l.getApelido());
            if (descricao == null) {
                descricao = apelido;
            }
            if (apelido == null && descricao != null) {
                apelido = descricao.length() > 20 ? descricao.substring(0, 20).trim() : descricao;
                relatorio.avisos.add(titulo + ": sem apelido no legado; usado \"" + apelido + "\".");
            }
            if (descricao == null) {
                relatorio.erros.add(titulo + " sem descrição nem apelido.");
            }
            campos.campo("descrição", p::getDescricao, p::setDescricao, cortar(descricao, 255, titulo, "descrição", relatorio));
            campos.campo("apelido", p::getApelido, p::setApelido, cortar(apelido, 20, titulo, "apelido", relatorio));
            campos.campo("unidade", p::getUnidade, p::setUnidade, cortar(texto(l.getUnidade()), 6, titulo, "unidade", relatorio));
            campos.campo("embalagem", p::getEmbalagem, p::setEmbalagem,
                    cortar(texto(l.getEmbalagem()), 6, titulo, "embalagem", relatorio));
            campos.campo("quantidade na embalagem", p::getQuantEmbalagem, p::setQuantEmbalagem, zero(l.getQuantEmbalagem()));
            campos.campo("peso líquido", p::getPesoLiquido, p::setPesoLiquido, zero(l.getPesoLiquido()));
            campos.campo("peso bruto", p::getPesoBruto, p::setPesoBruto, zero(l.getPesoBruto()));
            campos.campo("alíquota de ICMS", p::getAliqIcms, p::setAliqIcms, zero(l.getAliqIcms()));
            campos.campo("redução de ICMS", p::getReducaoIcms, p::setReducaoIcms, zero(l.getReducaoIcms()));
            campos.campo("alíquota de IPI", p::getAliqIpi, p::setAliqIpi, zero(l.getAliqIpi()));
            campos.campo("inativo", p::getInativo, p::setInativo, "S".equalsIgnoreCase(texto(l.getInativo())));

            GrupoProduto grupo = grupoDe(l) == null ? null : grupos.get(grupoDe(l));
            if (grupoDe(l) == null || grupo != null) {
                campos.campo("grupo", () -> p.getGrupoProduto() == null ? null : p.getGrupoProduto().getCodigo(),
                        v -> p.setGrupoProduto(grupo), grupo == null ? null : grupo.getCodigo());
            }
            if (p.getTipoProduto() == null && p.getGrupoProduto() != null) {
                campos.campo("tipo de produto", p::getTipoProduto, p::setTipoProduto, p.getGrupoProduto().getTipoProduto());
            }

            ClassificacaoFiscal classificacao = ncmDe(l) == null ? null : classificacoes.get(ncmDe(l));
            if (classificacao != null) {
                campos.campo("NCM", () -> p.getClassificacaoFiscal() == null ? null : p.getClassificacaoFiscal().getCodNcm(),
                        v -> p.setClassificacaoFiscal(classificacao), classificacao.getCodNcm());
            }
        }

        @Override
        protected String descrever(Produto produto) {
            return produto.getDescricao();
        }

        /** GRUPOPROD do legado; 0 também é "sem grupo". */
        private Integer grupoDe(ImpProduto linha) {
            return linha.getGrupoProd() == null || linha.getGrupoProd() == 0 ? null : linha.getGrupoProd();
        }

        /** Só os dígitos do NCM do legado (há bancos com pontos); vazio é "sem NCM". */
        private static String ncmDe(ImpProduto linha) {
            String ncm = texto(linha.getNcm());
            if (ncm == null) {
                return null;
            }
            String digitos = ncm.replaceAll("\\D", "");
            return digitos.isEmpty() ? null : digitos;
        }

        private static BigDecimal zero(BigDecimal valor) {
            return valor == null ? BigDecimal.ZERO : valor;
        }

        private static String resumir(List<Integer> produtos) {
            return produtos.size() == 1 ? "produto " + produtos.get(0)
                    : produtos.size() + " produtos (" + produtos.stream().limit(10).map(String::valueOf)
                    .collect(Collectors.joining(", ")) + (produtos.size() > 10 ? ", ..." : "") + ")";
        }
    }
}
