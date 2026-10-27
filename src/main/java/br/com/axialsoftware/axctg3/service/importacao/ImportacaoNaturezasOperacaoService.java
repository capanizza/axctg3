package br.com.axialsoftware.axctg3.service.importacao;

import br.com.axialsoftware.axctg3.entity.cadastros.Empresa;
import br.com.axialsoftware.axctg3.entity.contabil.ContaContabil;
import br.com.axialsoftware.axctg3.entity.enums.CodRegimeTributario;
import br.com.axialsoftware.axctg3.entity.enums.TipoLote;
import br.com.axialsoftware.axctg3.entity.fiscal.NaturezaOperacao;
import br.com.axialsoftware.axctg3.entity.importacao.ImpLote;
import br.com.axialsoftware.axctg3.entity.importacao.ImpNaturezaOperacao;
import br.com.axialsoftware.axctg3.entity.tabelas.Cst;
import io.jmix.core.DataManager;
import io.jmix.core.FetchPlan;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Importa um {@link ImpLote} de naturezas de operação do legado (NATUREZA) para a empresa do lote
 * — comportamento em {@link ImportacaoCadastroBase}. Faz o papel de
 * {@code exportacao.ExportacaoNaturezaOperacao} do projeto Axial, que gravava direto e pulava o
 * que já existia.
 * <p>
 * A conta contábil vem pelo código no plano do ano mais recente da empresa; sem plano, ou conta
 * que não existe nele, a natureza fica com a conta que já tinha (aviso). O CST vem do
 * CONDICAOICM do legado só para a natureza que ainda não tem CST — depois de escolhido no
 * axctg3, a reimportação não mexe. No Simples segue a procedure CALCULACSOSN (1 com alíquota =
 * 101, 1 sem alíquota ou 3 = 102, 2 = 400); no regime normal, 1 = 00 e 2 = 40, e o 3 ("outras")
 * fica sem CST para alguém escolher.
 */
@Service
public class ImportacaoNaturezasOperacaoService {

    private final DataManager dataManager;

    public ImportacaoNaturezasOperacaoService(DataManager dataManager) {
        this.dataManager = dataManager;
    }

    /** Confere o lote e o compara com as naturezas da empresa. Só grava o relatório no lote. */
    @Transactional
    public ImportacaoPlanoContasService.Relatorio conferir(UUID loteId) {
        return new Importacao(dataManager).conferir(dataManager.load(ImpLote.class).id(loteId).one());
    }

    /** Confere e, sem erro, cria as novas e atualiza as alteradas. Tudo ou nada. */
    @Transactional
    public ImportacaoPlanoContasService.Relatorio importar(UUID loteId) {
        return new Importacao(dataManager).importar(dataManager.load(ImpLote.class).id(loteId).one());
    }

    /** Uma execução: guarda contas, CSTs e o regime da empresa entre {@code preparar} e {@code aplicar}. */
    private static class Importacao
            extends ImportacaoCadastroBase<ImpNaturezaOperacao, NaturezaOperacao, Integer> {

        private final Map<String, ContaContabil> contas = new HashMap<>();
        private final Map<String, Cst> csts = new HashMap<>();
        private boolean simples;

        Importacao(DataManager dataManager) {
            super(dataManager);
        }

        @Override
        protected TipoLote tipo() {
            return TipoLote.NATUREZAS_OPERACAO;
        }

        @Override
        protected String nome() {
            return "Natureza";
        }

        @Override
        protected List<ImpNaturezaOperacao> linhas(ImpLote lote) {
            return dataManager.load(ImpNaturezaOperacao.class)
                    .query("select e from ImpNaturezaOperacao e where e.lote = :lote order by e.codigo")
                    .parameter("lote", lote)
                    .list();
        }

        @Override
        protected List<NaturezaOperacao> gravados(ImpLote lote) {
            return dataManager.load(NaturezaOperacao.class)
                    .query("select e from NaturezaOperacao e where e.codEmpresa = :codEmpresa")
                    .parameter("codEmpresa", lote.getCodEmpresa())
                    .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE)
                            .add("contaContabil", FetchPlan.BASE)
                            .add("cst", FetchPlan.BASE))
                    .list();
        }

        @Override
        protected Integer codigoLinha(ImpNaturezaOperacao linha) {
            return linha.getCodigo();
        }

        @Override
        protected Integer codigoGravado(NaturezaOperacao natureza) {
            return natureza.getCodigo();
        }

        @Override
        protected NaturezaOperacao criar(ImpLote lote, Integer codigo) {
            NaturezaOperacao natureza = dataManager.create(NaturezaOperacao.class);
            natureza.setCodigo(codigo);
            natureza.setCodEmpresa(lote.getCodEmpresa());
            return natureza;
        }

        @Override
        protected void preparar(ImpLote lote, ImportacaoPlanoContasService.Relatorio relatorio) {
            CodRegimeTributario crt = dataManager.load(Empresa.class)
                    .query("select e from Empresa e where e.codigo = :codigo")
                    .parameter("codigo", lote.getCodEmpresa())
                    .one()
                    .getCrt();
            simples = crt == CodRegimeTributario.SIMPLES_NACIONAL
                    || crt == CodRegimeTributario.SIMPLES_NACIONAL_EXCESSO_SUBLIMITE;
            dataManager.load(Cst.class).all().list().forEach(c -> csts.put(c.getCodigo(), c));

            Integer ano = dataManager.loadValue(
                            "select max(e.ano) from ContaContabil e where e.codEmpresa = :codEmpresa", Integer.class)
                    .parameter("codEmpresa", lote.getCodEmpresa())
                    .optional()
                    .orElse(null);
            if (ano == null) {
                relatorio.avisos.add("A empresa " + lote.getCodEmpresa()
                        + " não tem plano de contas: as naturezas ficam sem conta contábil. Importe o plano e"
                        + " reimporte as naturezas para ligar as contas.");
                return;
            }
            dataManager.load(ContaContabil.class)
                    .query("select e from ContaContabil e where e.codEmpresa = :codEmpresa and e.ano = :ano")
                    .parameter("codEmpresa", lote.getCodEmpresa())
                    .parameter("ano", ano)
                    .list()
                    .forEach(c -> contas.put(c.getCodigo(), c));
        }

        @Override
        protected void aplicar(ImpLote lote, ImpNaturezaOperacao l, NaturezaOperacao n, Campos campos, String titulo,
                               ImportacaoPlanoContasService.Relatorio relatorio) {
            String nome = texto(l.getNome());
            if (nome == null) {
                relatorio.erros.add(titulo + " sem nome.");
            }
            if (l.getCfop() == null || l.getCfop() == 0) {
                relatorio.erros.add(titulo + " sem CFOP.");
            }
            BigDecimal aliqIcms = l.getAliqIcms() == null ? BigDecimal.ZERO : l.getAliqIcms();
            campos.campo("nome", n::getNome, n::setNome, cortar(nome, 50, titulo, "nome", relatorio));
            campos.campo("CFOP", n::getCfop, n::setCfop, l.getCfop());
            campos.campo("alíquota de ICMS", n::getAliqIcms, n::setAliqIcms, aliqIcms);
            campos.campo("venda", n::getVenda, n::setVenda, "S".equalsIgnoreCase(texto(l.getVenda())));

            String codConta = texto(l.getCodContabil());
            if (codConta != null && !contas.isEmpty()) {
                ContaContabil conta = contas.get(codConta);
                if (conta == null) {
                    relatorio.avisos.add(titulo + ": conta " + codConta
                            + " não existe no plano mais recente da empresa; a conta da natureza não muda.");
                } else {
                    campos.campo("conta contábil", () -> descreverConta(n.getContaContabil()),
                            v -> n.setContaContabil(conta), descreverConta(conta));
                }
            }

            if (n.getCst() == null) {
                String codCst = cstDaCondicao(texto(l.getCondicaoIcm()), aliqIcms);
                Cst cst = codCst == null ? null : csts.get(codCst);
                if (codCst != null && cst == null) {
                    relatorio.avisos.add(titulo + ": CST " + codCst + " não existe no catálogo; fica sem CST.");
                }
                campos.campo("CST", () -> n.getCst() == null ? null : n.getCst().getCodigo(),
                        v -> n.setCst(cst), cst == null ? null : cst.getCodigo());
            }
        }

        private String cstDaCondicao(String condicao, BigDecimal aliqIcms) {
            if (condicao == null) {
                return null;
            }
            if (simples) {
                return switch (condicao) {
                    case "1" -> aliqIcms.signum() != 0 ? "101" : "102";
                    case "2" -> "400";
                    case "3" -> "102";
                    default -> null;
                };
            }
            return switch (condicao) {
                case "1" -> "00";
                case "2" -> "40";
                default -> null;
            };
        }

        @Override
        protected String descrever(NaturezaOperacao natureza) {
            return natureza.getNome();
        }
    }
}
