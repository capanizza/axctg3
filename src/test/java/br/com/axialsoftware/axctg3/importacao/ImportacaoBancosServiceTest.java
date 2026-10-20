package br.com.axialsoftware.axctg3.importacao;

import br.com.axialsoftware.axctg3.entity.User;
import br.com.axialsoftware.axctg3.entity.cadastros.Empresa;
import br.com.axialsoftware.axctg3.entity.contabil.ContaContabil;
import br.com.axialsoftware.axctg3.entity.enums.CodNat;
import br.com.axialsoftware.axctg3.entity.enums.SituacaoLote;
import br.com.axialsoftware.axctg3.entity.enums.TipoLote;
import br.com.axialsoftware.axctg3.entity.financeiro.Banco;
import br.com.axialsoftware.axctg3.entity.importacao.ImpBanco;
import br.com.axialsoftware.axctg3.entity.importacao.ImpLote;
import br.com.axialsoftware.axctg3.service.importacao.ImportacaoBancosService;
import br.com.axialsoftware.axctg3.service.importacao.ImportacaoPlanoContasService;
import br.com.axialsoftware.axctg3.test_support.AuthenticatedAsAdmin;
import io.jmix.core.DataManager;
import io.jmix.core.FetchPlan;
import io.jmix.core.SaveContext;
import io.jmix.core.security.CurrentAuthentication;
import io.jmix.data.PersistenceHints;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Importação de um lote de bancos do legado: na empresa do lote, com a conta do plano mais
 * recente, o dígito da agência do Sicredi no posto, e sem tocar no byte de geração na
 * reimportação.
 */
@SpringBootTest
@ExtendWith(AuthenticatedAsAdmin.class)
@ActiveProfiles("test")
class ImportacaoBancosServiceTest {

    private static final int COD_EMPRESA = 9311;

    @Autowired
    DataManager dataManager;
    @Autowired
    ImportacaoBancosService service;
    @Autowired
    CurrentAuthentication currentAuthentication;

    @BeforeEach
    void setUp() {
        limpar();
        Empresa empresa = dataManager.create(Empresa.class);
        empresa.setCodigo(COD_EMPRESA);
        empresa.setNome("Empresa de teste da importação de bancos");
        empresa.setApelido("Imp bancos");
        dataManager.save(empresa);
        conta("111020001", 2025);
        conta("111020001", 2026);
        conta("113030001", 2026);
        // a sessão aponta para outra empresa: a importação não pode usá-la
        User admin = (User) currentAuthentication.getUser();
        admin.setCodEmpresa(COD_EMPRESA + 1);
    }

    @AfterEach
    void tearDown() {
        limpar();
    }

    @Test
    void test_criaComBoletoEContaDoAnoMaisRecente() {
        ImpLote lote = criarLote(
                linha(1, 748, "SICREDI", "111020001", l -> {
                    l.setAgencia("0738");
                    l.setDigAgencia("33");
                    l.setConta("59622");
                    l.setDigConta("2");
                    l.setCodCedente("59622");
                    l.setDigCedente("0");
                    l.setNomeCedente("RADIO CULTURA DE BRAGANCA PAULISTA LTDA");
                    l.setLocalPagamento("Pagável em qualquer banco até o vencimento");
                    l.setEspecie("DSI");
                    l.setNossoNumInicial("00001");
                    l.setNossoNumFinal("99999");
                    l.setNossoNumAtual("00036");
                    l.setNumRemessa(6);
                    l.setMulta(new BigDecimal("0.00"));
                    l.setBaixaNormal(2);
                    l.setBaixaJuros(3);
                    l.setBaixaDesconto(4);
                }),
                linha(2, 2, "ANTECIPADO", "113030001", l -> { }),
                linha(22, null, "PERDA RECEBIMENTOS", "334010001", l -> { }));

        ImportacaoPlanoContasService.Relatorio conferencia = service.conferir(lote.getId());
        assertThat(conferencia.getErros()).isEmpty();
        assertThat(bancos()).isEmpty(); // conferir não grava

        ImportacaoPlanoContasService.Relatorio relatorio = service.importar(lote.getId());

        assertThat(relatorio.getErros()).isEmpty();
        assertThat(relatorio.getImportadas()).isEqualTo(3);
        assertThat(relatorio.getAvisos())
                .anyMatch(a -> a.startsWith("Banco 22: conta contábil 334010001 não existe"))
                .anyMatch(a -> a.startsWith("Banco 1: o byte de geração do Nosso Número não existe no legado"));

        Map<Integer, Banco> bancos = bancos();
        assertThat(bancos).containsOnlyKeys(1, 2, 22);
        Banco sicredi = bancos.get(1);
        assertThat(sicredi.getCodGeral()).isEqualTo(748);
        assertThat(sicredi.getContaContabil().getAno()).isEqualTo(2026);
        assertThat(sicredi.getCodContabil()).isEqualTo("111020001");
        assertThat(sicredi.getPosto()).isEqualTo("33");
        assertThat(sicredi.getDigAgencia()).isNull();
        assertThat(sicredi.getNossoNumAtual()).isEqualTo("00036");
        assertThat(sicredi.getNomeCedente()).isEqualTo("RADIO CULTURA DE BRAGANCA PAULISTA LTDA");
        assertThat(sicredi.getBaixaNormal()).isEqualTo(2);
        assertThat(bancos.get(22).getContaContabil()).isNull();
        assertThat(bancos.get(22).getCodContabil()).isEqualTo("334010001");
        assertThat(dataManager.load(Banco.class)
                .query("select e from Banco e where e.codEmpresa = :cod")
                .parameter("cod", COD_EMPRESA + 1)
                .list()).isEmpty();

        ImpLote recarregado = dataManager.load(ImpLote.class).id(lote.getId()).one();
        assertThat(recarregado.getSituacao()).isEqualTo(SituacaoLote.IMPORTADO);
        assertThat(recarregado.getMensagem()).contains("3 criados, 0 alterados, 0 iguais");
        assertThat(service.importar(lote.getId()).getErros()).anyMatch(e -> e.contains("já foi importado"));
    }

    @Test
    void test_reimportacaoAtualizaEMantemOByteDeGeracao() {
        Banco existente = dataManager.create(Banco.class);
        existente.setCodigo(1);
        existente.setCodEmpresa(COD_EMPRESA);
        existente.setNome("SICREDI");
        existente.setCodGeral(748);
        existente.setAgencia("0738");
        existente.setPosto("33");
        existente.setNossoNumAtual("00036");
        existente.setByteGeracaoNossoNumero(2);
        dataManager.save(existente);
        Banco soNoAxctg3 = dataManager.create(Banco.class);
        soNoAxctg3.setCodigo(9);
        soNoAxctg3.setCodEmpresa(COD_EMPRESA);
        soNoAxctg3.setNome("CAIXA");
        dataManager.save(soNoAxctg3);

        ImpLote lote = criarLote(linha(1, 748, "SICREDI", null, l -> {
            l.setAgencia("0738");
            l.setDigAgencia("33");
            l.setNossoNumAtual("00040");
        }));

        ImportacaoPlanoContasService.Relatorio relatorio = service.importar(lote.getId());

        assertThat(relatorio.getErros()).isEmpty();
        assertThat(relatorio.getImportadas()).isEqualTo(1);
        assertThat(relatorio.getAvisos())
                .anyMatch(a -> a.equals("Banco 1: nosso número atual \"00036\" passa a \"00040\"."))
                .anyMatch(a -> a.startsWith("Banco 9 (\"CAIXA\") existe no axctg3"))
                .noneMatch(a -> a.contains("byte de geração"));
        Banco sicredi = bancos().get(1);
        assertThat(sicredi.getNossoNumAtual()).isEqualTo("00040");
        assertThat(sicredi.getByteGeracaoNossoNumero()).isEqualTo(2);
        assertThat(bancos()).containsKey(9);
    }

    @Test
    void test_codigoRepetidoOuSemNomeImpedeAImportacao() {
        ImpLote lote = criarLote(
                linha(4, null, "PRIMEIRO", null, l -> { }),
                linha(4, null, "REPETIDO", null, l -> { }),
                linha(5, null, "  ", null, l -> { }));

        ImportacaoPlanoContasService.Relatorio relatorio = service.importar(lote.getId());

        assertThat(relatorio.getErros())
                .anyMatch(e -> e.equals("Banco 4 repetido no lote."))
                .anyMatch(e -> e.equals("Banco 5 sem nome."));
        assertThat(bancos()).isEmpty();
        assertThat(dataManager.load(ImpLote.class).id(lote.getId()).one().getSituacao())
                .isEqualTo(SituacaoLote.PRONTO);
    }

    private void conta(String codigo, int ano) {
        ContaContabil c = dataManager.create(ContaContabil.class);
        c.setCodigo(codigo);
        c.setAno(ano);
        c.setCodEmpresa(COD_EMPRESA);
        c.setNome("CONTA " + codigo);
        c.setGrau(1);
        c.setCodContaSup(codigo);
        c.setAnalitica(true);
        c.setCodNat(CodNat.CONTAS_DE_ATIVO);
        dataManager.save(c);
    }

    private ImpBanco linha(Integer codigo, Integer codGeral, String nome, String codContabil, Consumer<ImpBanco> resto) {
        ImpBanco l = dataManager.create(ImpBanco.class);
        l.setCodigo(codigo);
        l.setCodGeral(codGeral);
        l.setNome(nome);
        l.setCodContabil(codContabil);
        resto.accept(l);
        return l;
    }

    private ImpLote criarLote(ImpBanco... linhas) {
        ImpLote lote = dataManager.create(ImpLote.class);
        lote.setTipo(TipoLote.BANCOS);
        lote.setSituacao(SituacaoLote.PRONTO);
        lote.setCodEmpresa(COD_EMPRESA);
        lote.setCodEmpresaLegado(1);
        lote = dataManager.save(lote);
        SaveContext ctx = new SaveContext();
        for (ImpBanco l : linhas) {
            l.setLote(lote);
            ctx.saving(l);
        }
        dataManager.save(ctx);
        return lote;
    }

    private Map<Integer, Banco> bancos() {
        return dataManager.load(Banco.class)
                .query("select e from Banco e where e.codEmpresa = :cod")
                .parameter("cod", COD_EMPRESA)
                .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE).add("contaContabil", FetchPlan.BASE))
                .list()
                .stream()
                .collect(Collectors.toMap(Banco::getCodigo, b -> b));
    }

    private void limpar() {
        apagar(carregar(Banco.class, "select e from Banco e where e.codEmpresa = :cod"));
        apagar(carregar(ContaContabil.class, "select e from ContaContabil e where e.codEmpresa = :cod"));
        apagar(carregar(ImpBanco.class, "select e from ImpBanco e where e.lote.codEmpresa = :cod"));
        apagar(carregar(ImpLote.class, "select e from ImpLote e where e.codEmpresa = :cod"));
        apagar(carregar(Empresa.class, "select e from Empresa e where e.codigo = :cod"));
    }

    private <T> List<T> carregar(Class<T> classe, String jpql) {
        return dataManager.load(classe)
                .query(jpql)
                .parameter("cod", COD_EMPRESA)
                .hint(PersistenceHints.SOFT_DELETION, false)
                .list();
    }

    private void apagar(List<?> entidades) {
        if (entidades.isEmpty()) {
            return;
        }
        dataManager.save(new SaveContext()
                .setHint(PersistenceHints.SOFT_DELETION, false)
                .setHint(PersistenceHints.SKIP_ENTITY_CHANGED_EVENT, true)
                .removing(entidades.toArray()));
    }
}
