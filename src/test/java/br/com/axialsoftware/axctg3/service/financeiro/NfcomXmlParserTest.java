package br.com.axialsoftware.axctg3.service.financeiro;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Teste puro (sem Spring) do parser de NFCom — tags e valores conferidos contra 25 XMLs reais
 * da Radio (2026-06), reduzidos aqui só aos campos que {@link NfcomXmlParser} lê. Ver
 * [[nfcom-import-titulo-receber]].
 */
class NfcomXmlParserTest {

    private final NfcomXmlParser parser = new NfcomXmlParser();

    /**
     * Nota real: nNF=259, dhEmi=2026-06-05, vNF=1300.00, dVencFat=2026-06-15 — bate com o
     * TituloReceber (numero=000259) já existente na base pra essa mesma nota.
     */
    private static final String XML_NOTA = "<?xml version=\"1.0\" encoding=\"utf-8\"?>"
            + "<nfcomProc versao=\"1.00\" xmlns=\"http://www.portalfiscal.inf.br/nfcom\">"
            + "<NFCom><infNFCom versao=\"1.00\" Id=\"NFCom35260645624871000114620010000002591035076056\">"
            + "<ide><cUF>35</cUF><mod>62</mod><serie>1</serie><nNF>259</nNF>"
            + "<dhEmi>2026-06-05T03:00:00+00:00</dhEmi></ide>"
            + "<dest><xNome>PLANO DE SAÚDE DA SANTA CASA DE BRAGANÇA PAULISTA</xNome>"
            + "<CNPJ>24645912000189</CNPJ><indIEDest>9</indIEDest>"
            + "<enderDest><xLgr>Rua Alpheu Grimello</xLgr><nro>464</nro><xBairro>Taboão</xBairro>"
            + "<cMun>3507605</cMun><xMun>Bragança Paulista</xMun><CEP>12916010</CEP><UF>SP</UF>"
            + "<fone>1144818198</fone></enderDest></dest>"
            + "<total><vProd>1300.00</vProd><vNF>1300.00</vNF></total>"
            + "<gFat><CompetFat>202605</CompetFat><dVencFat>2026-06-15</dVencFat></gFat>"
            + "</infNFCom></NFCom>"
            + "<protNFCom><infProt><chNFCom>35260645624871000114620010000002591035076056</chNFCom>"
            + "<cStat>100</cStat></infProt></protNFCom></nfcomProc>";

    /** Evento de cancelamento real (tpEvento 110111) — chave do mesmo lote de teste. */
    private static final String XML_CANCELAMENTO = "<?xml version=\"1.0\" encoding=\"utf-8\"?>"
            + "<procEventoNFCom versao=\"1.00\" xmlns=\"http://www.portalfiscal.inf.br/nfcom\">"
            + "<eventoNFCom><infEvento Id=\"ID1101113526064562487100011462001000000265\">"
            + "<cOrgao>35</cOrgao><CNPJ>45624871000114</CNPJ>"
            + "<chNFCom>35260645624871000114620010000002651035076053</chNFCom>"
            + "<tpEvento>110111</tpEvento><nSeqEvento>1</nSeqEvento>"
            + "<detEvento><evCancNFCom><xJust>fiz para cliente errado</xJust></evCancNFCom></detEvento>"
            + "</infEvento></eventoNFCom>"
            + "<retEventoNFCom><infEvento><cStat>135</cStat><chNFCom>35260645624871000114620010000002651035076053</chNFCom>"
            + "<tpEvento>110111</tpEvento></infEvento></retEventoNFCom>"
            + "</procEventoNFCom>";

    @Test
    void test_parseNota() {
        NfcomXmlLido lido = parser.parse(XML_NOTA.getBytes(StandardCharsets.UTF_8));

        assertThat(lido).isInstanceOf(NfcomXmlLido.Nota.class);
        NfcomXmlLido.Nota nota = (NfcomXmlLido.Nota) lido;
        assertThat(nota.numero()).isEqualTo("000259");
        assertThat(nota.dataEmissao()).isEqualTo(LocalDate.of(2026, 6, 5));
        assertThat(nota.dataVencimento()).isEqualTo(LocalDate.of(2026, 6, 15));
        assertThat(nota.valor()).isEqualByComparingTo("1300.00");

        NfcomXmlLido.Nota.Destinatario destinatario = nota.destinatario();
        assertThat(destinatario.cnpjCpf()).isEqualTo("24645912000189");
        assertThat(destinatario.nome()).isEqualTo("PLANO DE SAÚDE DA SANTA CASA DE BRAGANÇA PAULISTA");
        assertThat(destinatario.inscricaoEstadual()).isNull(); // indIEDest=9 → não contribuinte
        assertThat(destinatario.logradouro()).isEqualTo("Rua Alpheu Grimello");
        assertThat(destinatario.numero()).isEqualTo("464");
        assertThat(destinatario.bairro()).isEqualTo("Taboão");
        assertThat(destinatario.codMunicipio()).isEqualTo(3507605);
        assertThat(destinatario.cep()).isEqualTo("12916010");
        assertThat(destinatario.uf()).isEqualTo("SP");
        assertThat(destinatario.telefone()).isEqualTo("1144818198");
    }

    @Test
    void test_parseCancelamento() {
        NfcomXmlLido lido = parser.parse(XML_CANCELAMENTO.getBytes(StandardCharsets.UTF_8));

        assertThat(lido).isInstanceOf(NfcomXmlLido.Cancelamento.class);
        assertThat(((NfcomXmlLido.Cancelamento) lido).numero()).isEqualTo("000265");
    }

    @Test
    void test_parseCancelamento_eventoNaoSuportadoLancaExcecao() {
        String xmlOutroEvento = XML_CANCELAMENTO.replace("110111", "110112");

        assertThatThrownBy(() -> parser.parse(xmlOutroEvento.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void test_parseIndIEDestDiferenteDe9MantemInscricao() {
        String xmlComIe = XML_NOTA
                .replace("<indIEDest>9</indIEDest>", "<IE>225270750118</IE><indIEDest>1</indIEDest>");

        NfcomXmlLido.Nota nota = (NfcomXmlLido.Nota) parser.parse(xmlComIe.getBytes(StandardCharsets.UTF_8));

        assertThat(nota.destinatario().inscricaoEstadual()).isEqualTo("225270750118");
    }

    @Test
    void test_parseXmlInvalidoLancaExcecao() {
        assertThatThrownBy(() -> parser.parse("<no>".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void test_valorZeroPreenchidoComSeisDigitos() {
        String xmlNumeroPequeno = XML_NOTA.replace("<nNF>259</nNF>", "<nNF>7</nNF>");

        NfcomXmlLido.Nota nota = (NfcomXmlLido.Nota) parser.parse(xmlNumeroPequeno.getBytes(StandardCharsets.UTF_8));

        assertThat(nota.numero()).isEqualTo("000007");
    }
}
