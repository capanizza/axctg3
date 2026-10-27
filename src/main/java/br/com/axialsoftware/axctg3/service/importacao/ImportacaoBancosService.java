package br.com.axialsoftware.axctg3.service.importacao;

import br.com.axialsoftware.axctg3.entity.contabil.ContaContabil;
import br.com.axialsoftware.axctg3.entity.enums.TipoLote;
import br.com.axialsoftware.axctg3.entity.financeiro.Banco;
import br.com.axialsoftware.axctg3.entity.importacao.ImpBanco;
import br.com.axialsoftware.axctg3.entity.importacao.ImpLote;
import io.jmix.core.DataManager;
import io.jmix.core.FetchPlan;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Importa um {@link ImpLote} de bancos do legado (BANCO + CONFBOLETO) para a empresa do lote —
 * comportamento em {@link ImportacaoCadastroBase}. Substitui {@code exportacao.ExportacaoBanco}
 * do projeto Axial, que gravava direto só código, nome e conta, e pulava o que já existia.
 * <p>
 * A conta contábil é procurada pelo código no plano do ano mais recente da empresa (o banco
 * aponta para uma conta de um ano só). Não achada, o banco fica sem conta, com aviso.
 * <p>
 * No Sicredi (748) o dígito da agência do legado (CONFBOLETO.DIGNUMAGENCIA) é o "posto" do
 * manual do banco, que é onde o {@code SicrediCnab400Handler} o lê; nos outros bancos vai para
 * o dígito da agência. O byte de geração do Nosso Número não existe no legado: não é trocado
 * pela importação e, vazio num banco com boleto, vira aviso.
 */
@Service
public class ImportacaoBancosService {

    private static final int SICREDI = 748;

    private final DataManager dataManager;

    public ImportacaoBancosService(DataManager dataManager) {
        this.dataManager = dataManager;
    }

    /** Confere o lote e o compara com os bancos da empresa. Só grava o relatório no lote. */
    @Transactional
    public ImportacaoPlanoContasService.Relatorio conferir(UUID loteId) {
        return new Importacao(dataManager).conferir(dataManager.load(ImpLote.class).id(loteId).one());
    }

    /** Confere e, sem erro, cria os novos e atualiza os alterados. Tudo ou nada. */
    @Transactional
    public ImportacaoPlanoContasService.Relatorio importar(UUID loteId) {
        return new Importacao(dataManager).importar(dataManager.load(ImpLote.class).id(loteId).one());
    }

    /** Uma execução: guarda as contas do plano entre {@code preparar} e {@code aplicar}. */
    private static class Importacao extends ImportacaoCadastroBase<ImpBanco, Banco, Integer> {

        private Map<String, ContaContabil> contas = Map.of();

        Importacao(DataManager dataManager) {
            super(dataManager);
        }

        @Override
        protected TipoLote tipo() {
            return TipoLote.BANCOS;
        }

        @Override
        protected String nome() {
            return "Banco";
        }

        @Override
        protected List<ImpBanco> linhas(ImpLote lote) {
            return dataManager.load(ImpBanco.class)
                    .query("select e from ImpBanco e where e.lote = :lote order by e.codigo")
                    .parameter("lote", lote)
                    .list();
        }

        @Override
        protected List<Banco> gravados(ImpLote lote) {
            return dataManager.load(Banco.class)
                    .query("select e from Banco e where e.codEmpresa = :codEmpresa")
                    .parameter("codEmpresa", lote.getCodEmpresa())
                    .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE).add("contaContabil", FetchPlan.BASE))
                    .list();
        }

        @Override
        protected Integer codigoLinha(ImpBanco linha) {
            return linha.getCodigo();
        }

        @Override
        protected Integer codigoGravado(Banco banco) {
            return banco.getCodigo();
        }

        @Override
        protected Banco criar(ImpLote lote, Integer codigo) {
            Banco banco = dataManager.create(Banco.class);
            banco.setCodigo(codigo);
            banco.setCodEmpresa(lote.getCodEmpresa());
            return banco;
        }

        @Override
        protected void preparar(ImpLote lote, ImportacaoPlanoContasService.Relatorio relatorio) {
            contas = contasDoPlanoMaisRecente(lote, "o banco aponta para uma conta", relatorio);
        }

        @Override
        protected void aplicar(ImpLote lote, ImpBanco l, Banco b, Campos campos, String titulo,
                               ImportacaoPlanoContasService.Relatorio relatorio) {
            String nome = texto(l.getNome());
            if (nome == null) {
                relatorio.erros.add(titulo + " sem nome.");
            }
            campos.campo("nome", b::getNome, b::setNome, cortar(nome, 30, titulo, "nome", relatorio));
            campos.campo("código do banco", b::getCodGeral, b::setCodGeral, l.getCodGeral());

            String codContabil = texto(l.getCodContabil());
            ContaContabil conta = codContabil == null ? null : contas.get(codContabil);
            if (codContabil != null && conta == null && !semPlano) {
                relatorio.avisos.add(titulo + ": conta contábil " + codContabil
                        + " não existe no plano mais recente da empresa; o banco fica sem conta.");
            }
            campos.campo("código contábil", b::getCodContabil, b::setCodContabil,
                    cortar(codContabil, 25, titulo, "código contábil", relatorio));
            campos.campo("conta contábil", () -> descreverConta(b.getContaContabil()),
                    v -> b.setContaContabil(conta), descreverConta(conta));

            boolean sicredi = Integer.valueOf(SICREDI).equals(l.getCodGeral());
            String digAgencia = texto(l.getDigAgencia());
            campos.campo("agência", b::getAgencia, b::setAgencia, cortar(texto(l.getAgencia()), 10, titulo, "agência", relatorio));
            campos.campo("posto", b::getPosto, b::setPosto,
                    sicredi ? cortar(digAgencia, 2, titulo, "posto", relatorio) : null);
            campos.campo("dígito da agência", b::getDigAgencia, b::setDigAgencia,
                    sicredi ? null : cortar(digAgencia, 3, titulo, "dígito da agência", relatorio));
            campos.campo("conta", b::getConta, b::setConta, cortar(texto(l.getConta()), 10, titulo, "conta", relatorio));
            campos.campo("dígito da conta", b::getDigConta, b::setDigConta,
                    cortar(texto(l.getDigConta()), 3, titulo, "dígito da conta", relatorio));
            campos.campo("código do cedente", b::getCodCedente, b::setCodCedente,
                    cortar(texto(l.getCodCedente()), 10, titulo, "código do cedente", relatorio));
            campos.campo("dígito do cedente", b::getDigCedente, b::setDigCedente,
                    cortar(texto(l.getDigCedente()), 2, titulo, "dígito do cedente", relatorio));
            campos.campo("nome do cedente", b::getNomeCedente, b::setNomeCedente,
                    cortar(texto(l.getNomeCedente()), 50, titulo, "nome do cedente", relatorio));
            campos.campo("local de pagamento", b::getLocalPagamento, b::setLocalPagamento,
                    cortar(texto(l.getLocalPagamento()), 50, titulo, "local de pagamento", relatorio));
            campos.campo("espécie", b::getEspecie, b::setEspecie, cortar(texto(l.getEspecie()), 10, titulo, "espécie", relatorio));
            campos.campo("carteira", b::getCarteira, b::setCarteira, cortar(texto(l.getCarteira()), 10, titulo, "carteira", relatorio));
            campos.campo("convênio", b::getConvenio, b::setConvenio, cortar(texto(l.getConvenio()), 10, titulo, "convênio", relatorio));
            campos.campo("nosso número inicial", b::getNossoNumInicial, b::setNossoNumInicial,
                    cortar(texto(l.getNossoNumInicial()), 10, titulo, "nosso número inicial", relatorio));
            campos.campo("nosso número final", b::getNossoNumFinal, b::setNossoNumFinal,
                    cortar(texto(l.getNossoNumFinal()), 10, titulo, "nosso número final", relatorio));
            campos.campo("nosso número atual", b::getNossoNumAtual, b::setNossoNumAtual,
                    cortar(texto(l.getNossoNumAtual()), 10, titulo, "nosso número atual", relatorio));
            campos.campo("número da remessa", b::getNumRemessa, b::setNumRemessa, l.getNumRemessa());
            // dias para protesto não vem mais do legado (a coluna falta em vários bancos e estava
            // vazia nos outros): fica o que estiver no axctg3
            campos.campo("multa por atraso", b::getMultaAtraso, b::setMultaAtraso, l.getMulta());
            campos.campo("multa por dia", b::getMultaDia, b::setMultaDia, l.getMultaDia());
            // mensagem e históricos de baixa faltam em alguns bancos do legado (vêm vazios): vazio
            // não apaga o que foi configurado no axctg3
            String mensagem = texto(l.getMensagem());
            if (mensagem != null) {
                campos.campo("mensagem", b::getMensagem, b::setMensagem, mensagem);
            }
            if (l.getBaixaNormal() != null) {
                campos.campo("histórico de baixa normal", b::getBaixaNormal, b::setBaixaNormal, l.getBaixaNormal());
            }
            if (l.getBaixaJuros() != null) {
                campos.campo("histórico de baixa com juros", b::getBaixaJuros, b::setBaixaJuros, l.getBaixaJuros());
            }
            if (l.getBaixaDesconto() != null) {
                campos.campo("histórico de baixa com desconto", b::getBaixaDesconto, b::setBaixaDesconto,
                        l.getBaixaDesconto());
            }

            if (sicredi && b.getAgencia() != null && b.getByteGeracaoNossoNumero() == null) {
                relatorio.avisos.add(titulo + ": o byte de geração do Nosso Número não existe no legado. "
                        + "Preencha no cadastro do banco antes de gerar remessa ou emitir boleto.");
            }
        }

        @Override
        protected String descrever(Banco banco) {
            return banco.getNome();
        }
    }
}
