package br.com.axialsoftware.axctg3.financeiro;

import br.com.axialsoftware.axctg3.entity.User;
import br.com.axialsoftware.axctg3.entity.cadastros.Empresa;
import br.com.axialsoftware.axctg3.entity.cadastros.Parceiro;
import br.com.axialsoftware.axctg3.entity.financeiro.Banco;
import br.com.axialsoftware.axctg3.entity.financeiro.HistoricoFinanceiro;
import br.com.axialsoftware.axctg3.entity.financeiro.ItemReceber;
import br.com.axialsoftware.axctg3.entity.financeiro.TituloReceber;
import br.com.axialsoftware.axctg3.entity.tabelas.Municipio;
import br.com.axialsoftware.axctg3.service.financeiro.NfcomImportService;
import br.com.axialsoftware.axctg3.test_support.AuthenticatedAsAdmin;
import io.jmix.core.DataManager;
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
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cobre a orquestração de {@link NfcomImportService} (dedup, resolução/criação de
 * {@link Parceiro}, resolução do {@link Banco} único, cancelamento) — o parse do XML em si já é
 * testado em {@code NfcomXmlParserTest}. Regras conferidas contra 25 XMLs reais da Radio
 * (2026-06) e a lógica de referência do Axial (ver [[nfcom-import-titulo-receber]]).
 */
@SpringBootTest
@ExtendWith(AuthenticatedAsAdmin.class)
@ActiveProfiles("test")
class NfcomImportServiceTest {

    private static final int COD_EMPRESA = 9105;
    private static final int ANO = 2099;
    private static final int MES = 8;
    private static final int MUNICIPIO_TESTE = 9999999;

    @Autowired
    DataManager dataManager;

    @Autowired
    CurrentAuthentication currentAuthentication;

    @Autowired
    NfcomImportService nfcomImportService;

    private Banco bancoSicredi;

    @BeforeEach
    void setUp() {
        User admin = (User) currentAuthentication.getUser();
        admin.setCodEmpresa(COD_EMPRESA);
        admin.setAnoContabil(ANO);
        admin.setMesContabil(MES);

        limparDadosDaEmpresa();

        Empresa empresa = dataManager.create(Empresa.class);
        empresa.setCodigo(COD_EMPRESA);
        empresa.setNome("Empresa de teste");
        empresa.setApelido("Teste");
        empresa.setCnpj("00000000000191");
        dataManager.save(empresa);

        bancoSicredi = dataManager.create(Banco.class);
        bancoSicredi.setCodigo(9501);
        bancoSicredi.setCodEmpresa(COD_EMPRESA);
        bancoSicredi.setNome("Sicredi");
        bancoSicredi.setCodGeral(748);
        bancoSicredi = dataManager.save(bancoSicredi);

        HistoricoFinanceiro histEmissao = dataManager.create(HistoricoFinanceiro.class);
        histEmissao.setCodigo(1);
        histEmissao.setCodEmpresa(COD_EMPRESA);
        histEmissao.setNome("Emissão de teste");
        histEmissao.setEmissao(true);
        dataManager.save(histEmissao);

        HistoricoFinanceiro histBaixa = dataManager.create(HistoricoFinanceiro.class);
        histBaixa.setCodigo(2);
        histBaixa.setCodEmpresa(COD_EMPRESA);
        histBaixa.setNome("Baixa de teste");
        histBaixa.setBaixa(true);
        dataManager.save(histBaixa);

        garantirMunicipioTeste();
    }

    @Test
    void test_importarNotaNovaCriaTituloEParceiroNovo() {
        byte[] xml = xmlNota("321", "24645912000189", "PLANO DE SAÚDE TESTE",
                "2026-06-05", "2026-06-15", "1300.00");

        NfcomImportService.ImportResult resultado = nfcomImportService.importar("nota.xml", xml);

        assertThat(resultado.resultado()).isEqualTo(NfcomImportService.Resultado.CRIADA);
        TituloReceber tituloReceber = buscarTitulo("000321");
        assertThat(tituloReceber).isNotNull();
        assertThat(tituloReceber.getDataEmissao()).isEqualTo(LocalDate.of(2026, 6, 5));
        assertThat(tituloReceber.getDataVencimento()).isEqualTo(LocalDate.of(2026, 6, 15));
        assertThat(tituloReceber.getValor()).isEqualByComparingTo("1300.00");
        assertThat(tituloReceber.getBanco().getId()).isEqualTo(bancoSicredi.getId());

        Parceiro parceiro = tituloReceber.getParceiro();
        assertThat(parceiro.getCnpj()).isEqualTo("24645912000189");
        assertThat(parceiro.getNome()).isEqualTo("PLANO DE SAÚDE TESTE");
        assertThat(parceiro.getApelido()).isEqualTo("PLANO DE SAÚDE TESTE");
        assertThat(parceiro.getCliente()).isTrue();
        assertThat(parceiro.getMunicipio().getCodigo()).isEqualTo(MUNICIPIO_TESTE);
        assertThat(parceiro.getCodigo()).isEqualTo(1L); // primeiro parceiro da empresa
    }

    @Test
    void test_importarNotaComParceiroExistenteReaproveitaCadastro() {
        Parceiro existente = dataManager.create(Parceiro.class);
        existente.setCodigo(50L);
        existente.setCodEmpresa(COD_EMPRESA);
        existente.setCnpj("24645912000189");
        existente.setNome("Cliente já cadastrado");
        existente.setApelido("Cliente");
        existente.setCliente(true);
        dataManager.save(existente);

        byte[] xml = xmlNota("322", "24645912000189", "NOME DIFERENTE NO XML",
                "2026-06-05", "2026-06-15", "500.00");
        nfcomImportService.importar("nota.xml", xml);

        TituloReceber tituloReceber = buscarTitulo("000322");
        assertThat(tituloReceber.getParceiro().getId()).isEqualTo(existente.getId());
        assertThat(contarParceiros()).isEqualTo(1); // não criou um segundo
    }

    @Test
    void test_importarNotaJaExistenteRetornaDuplicada() {
        byte[] xml = xmlNota("323", "24645912000189", "CLIENTE", "2026-06-05", "2026-06-15", "500.00");
        nfcomImportService.importar("primeira.xml", xml);

        NfcomImportService.ImportResult segundaVez = nfcomImportService.importar("segunda.xml", xml);

        assertThat(segundaVez.resultado()).isEqualTo(NfcomImportService.Resultado.DUPLICADA);
        assertThat(dataManager.load(TituloReceber.class)
                .query("select e from TituloReceber e where e.numero = :numero and e.codEmpresa = :codEmpresa")
                .parameter("numero", "000323")
                .parameter("codEmpresa", COD_EMPRESA)
                .list()).hasSize(1);
    }

    @Test
    void test_importarCancelamentoRemoveTituloAberto() {
        byte[] xmlNota = xmlNota("324", "24645912000189", "CLIENTE", "2026-06-05", "2026-06-15", "500.00");
        nfcomImportService.importar("nota.xml", xmlNota);
        assertThat(buscarTitulo("000324")).isNotNull();

        NfcomImportService.ImportResult resultado = nfcomImportService.importar(
                "cancelamento.xml", xmlCancelamento("000324"));

        assertThat(resultado.resultado()).isEqualTo(NfcomImportService.Resultado.CANCELADA);
        assertThat(buscarTitulo("000324")).isNull();
    }

    @Test
    void test_importarCancelamentoTituloNaoEncontradoRetornaNaoEncontrada() {
        NfcomImportService.ImportResult resultado = nfcomImportService.importar(
                "cancelamento.xml", xmlCancelamento("999999"));

        assertThat(resultado.resultado()).isEqualTo(NfcomImportService.Resultado.NAO_ENCONTRADA);
    }

    @Test
    void test_importarCancelamentoTituloJaBaixadoNaoRemove() {
        byte[] xmlNota = xmlNota("325", "24645912000189", "CLIENTE", "2026-06-05", "2026-06-15", "500.00");
        nfcomImportService.importar("nota.xml", xmlNota);
        TituloReceber tituloReceber = buscarTitulo("000325");

        ItemReceber baixa = dataManager.create(ItemReceber.class);
        baixa.setTituloReceber(tituloReceber);
        baixa.setItem(2);
        baixa.setData(LocalDate.of(ANO, MES, 20));
        baixa.setHistoricoFinanceiro(dataManager.load(HistoricoFinanceiro.class)
                .query("select e from HistoricoFinanceiro e where e.codigo = 2 and e.codEmpresa = :codEmpresa")
                .parameter("codEmpresa", COD_EMPRESA)
                .one());
        baixa.setValor(new BigDecimal("500.00"));
        baixa.setJuros(BigDecimal.ZERO);
        baixa.setDesconto(BigDecimal.ZERO);
        dataManager.save(baixa);

        NfcomImportService.ImportResult resultado = nfcomImportService.importar(
                "cancelamento.xml", xmlCancelamento("000325"));

        assertThat(resultado.resultado()).isEqualTo(NfcomImportService.Resultado.IGNORADA);
        assertThat(buscarTitulo("000325")).isNotNull(); // não excluiu
    }

    @Test
    void test_importarSemBancoCadastradoRetornaErro() {
        dataManager.remove(bancoSicredi);

        byte[] xml = xmlNota("326", "24645912000189", "CLIENTE", "2026-06-05", "2026-06-15", "500.00");
        NfcomImportService.ImportResult resultado = nfcomImportService.importar("nota.xml", xml);

        assertThat(resultado.resultado()).isEqualTo(NfcomImportService.Resultado.ERRO);
    }

    @Test
    void test_importarXmlInvalidoRetornaErro() {
        NfcomImportService.ImportResult resultado = nfcomImportService.importar(
                "invalido.xml", "not xml".getBytes(StandardCharsets.UTF_8));

        assertThat(resultado.resultado()).isEqualTo(NfcomImportService.Resultado.ERRO);
    }

    @AfterEach
    void tearDown() {
        limparDadosDaEmpresa();
    }

    private TituloReceber buscarTitulo(String numero) {
        return dataManager.load(TituloReceber.class)
                .query("select e from TituloReceber e where e.numero = :numero and e.codEmpresa = :codEmpresa")
                .parameter("numero", numero)
                .parameter("codEmpresa", COD_EMPRESA)
                .optional()
                .orElse(null);
    }

    private long contarParceiros() {
        return dataManager.load(Parceiro.class)
                .query("select e from Parceiro e where e.codEmpresa = :codEmpresa")
                .parameter("codEmpresa", COD_EMPRESA)
                .list()
                .size();
    }

    private void garantirMunicipioTeste() {
        Optional<Municipio> existente = dataManager.load(Municipio.class)
                .query("select e from Municipio e where e.codigo = :codigo")
                .parameter("codigo", MUNICIPIO_TESTE)
                .optional();
        if (existente.isPresent()) {
            return;
        }
        Municipio municipio = dataManager.create(Municipio.class);
        municipio.setCodigo(MUNICIPIO_TESTE);
        municipio.setNome("Município Teste");
        municipio.setUf("SP");
        dataManager.save(municipio);
    }

    private byte[] xmlNota(String nNF, String cnpj, String nome, String dataEmissaoIso,
                            String dataVencimentoIso, String valor) {
        String xml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>"
                + "<nfcomProc versao=\"1.00\" xmlns=\"http://www.portalfiscal.inf.br/nfcom\">"
                + "<NFCom><infNFCom versao=\"1.00\" Id=\"NFComTeste\">"
                + "<ide><cUF>35</cUF><mod>62</mod><serie>1</serie><nNF>" + nNF + "</nNF>"
                + "<dhEmi>" + dataEmissaoIso + "T03:00:00+00:00</dhEmi></ide>"
                + "<dest><xNome>" + nome + "</xNome><CNPJ>" + cnpj + "</CNPJ><indIEDest>9</indIEDest>"
                + "<enderDest><xLgr>Rua Teste</xLgr><nro>100</nro><xBairro>Centro</xBairro>"
                + "<cMun>" + MUNICIPIO_TESTE + "</cMun><xMun>Teste</xMun><CEP>12900000</CEP><UF>SP</UF>"
                + "<fone>1140000000</fone></enderDest></dest>"
                + "<total><vNF>" + valor + "</vNF></total>"
                + "<gFat><dVencFat>" + dataVencimentoIso + "</dVencFat></gFat>"
                + "</infNFCom></NFCom>"
                + "<protNFCom><infProt><cStat>100</cStat></infProt></protNFCom></nfcomProc>";
        return xml.getBytes(StandardCharsets.UTF_8);
    }

    private byte[] xmlCancelamento(String numero) {
        String chave = "0".repeat(28) + numero + "0".repeat(44 - 28 - numero.length());
        String xml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>"
                + "<procEventoNFCom versao=\"1.00\" xmlns=\"http://www.portalfiscal.inf.br/nfcom\">"
                + "<eventoNFCom><infEvento Id=\"IDTeste\">"
                + "<chNFCom>" + chave + "</chNFCom><tpEvento>110111</tpEvento>"
                + "<detEvento><evCancNFCom><xJust>teste</xJust></evCancNFCom></detEvento>"
                + "</infEvento></eventoNFCom>"
                + "</procEventoNFCom>";
        return xml.getBytes(StandardCharsets.UTF_8);
    }

    private void limparDadosDaEmpresa() {
        apagar(carregar(ItemReceber.class, "select e from ItemReceber e where e.tituloReceber.codEmpresa = :codEmpresa"));
        apagar(carregar(TituloReceber.class, "select e from TituloReceber e where e.codEmpresa = :codEmpresa"));
        apagar(carregar(Banco.class, "select e from Banco e where e.codEmpresa = :codEmpresa"));
        apagar(carregar(HistoricoFinanceiro.class, "select e from HistoricoFinanceiro e where e.codEmpresa = :codEmpresa"));
        apagar(carregar(Parceiro.class, "select e from Parceiro e where e.codEmpresa = :codEmpresa"));
        apagar(carregar(Empresa.class, "select e from Empresa e where e.codigo = :codEmpresa"));
        apagar(carregar(Municipio.class, "select e from Municipio e where e.codigo = :codigo", "codigo", MUNICIPIO_TESTE));
    }

    private <E> List<E> carregar(Class<E> entityClass, String query) {
        return carregar(entityClass, query, "codEmpresa", COD_EMPRESA);
    }

    private <E> List<E> carregar(Class<E> entityClass, String query, String paramName, Object paramValue) {
        return dataManager.load(entityClass)
                .query(query)
                .parameter(paramName, paramValue)
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
