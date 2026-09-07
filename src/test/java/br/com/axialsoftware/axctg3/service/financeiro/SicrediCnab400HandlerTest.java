package br.com.axialsoftware.axctg3.service.financeiro;

import br.com.axialsoftware.axctg3.entity.cadastros.Empresa;
import br.com.axialsoftware.axctg3.entity.cadastros.Parceiro;
import br.com.axialsoftware.axctg3.entity.financeiro.Banco;
import br.com.axialsoftware.axctg3.entity.financeiro.TituloReceber;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Teste puro (sem Spring) do leiaute CNAB400 Sicredi — as entidades são construídas com
 * {@code new} em vez de {@code dataManager.create()} porque nada aqui é persistido nem
 * comparado por identidade/hashCode (ver memory "new Xxx() vs dataManager.create() em
 * Jmix" — o problema daquele padrão é só no caminho de persistência).
 */
class SicrediCnab400HandlerTest {

    private final SicrediCnab400Handler handler = new SicrediCnab400Handler();

    @Test
    void test_gerarRemessaTemUmaLinhaDeHeaderMaisUmaPorTituloMaisTrailer() {
        Banco banco = criarBanco();
        Empresa empresa = criarEmpresa();
        List<TituloReceber> titulos = List.of(
                criarTitulo("0000001", new BigDecimal("500.00")),
                criarTitulo("0000002", new BigDecimal("1234.56")));

        byte[] arquivo = handler.gerarRemessa(empresa, banco, titulos, 1);
        String[] linhas = separarLinhas(arquivo);

        assertThat(linhas).hasSize(4); // header + 2 detalhes + trailer
        for (String linha : linhas) {
            assertThat(linha).hasSize(400);
        }
    }

    @Test
    void test_headerRemessa() {
        Banco banco = criarBanco();
        Empresa empresa = criarEmpresa();
        byte[] arquivo = handler.gerarRemessa(empresa, banco, List.of(criarTitulo("0000001", BigDecimal.TEN)), 7);
        String header = separarLinhas(arquivo)[0];

        assertThat(header.substring(0, 1)).isEqualTo("0");
        assertThat(header.substring(1, 2)).isEqualTo("1");
        assertThat(header.substring(2, 9)).isEqualTo("REMESSA");
        assertThat(header.substring(9, 11)).isEqualTo("01");
        assertThat(header.substring(11, 19)).isEqualTo("COBRANCA");
        assertThat(header.substring(26, 31)).isEqualTo("00623"); // código do cedente, zero à esquerda
        assertThat(header.substring(31, 45)).isEqualTo("00000000000191"); // CNPJ, zero à esquerda
        assertThat(header.substring(76, 79)).isEqualTo("748");
        assertThat(header.substring(79, 94).trim()).isEqualTo("SICREDI");
        assertThat(header.substring(94, 102))
                .isEqualTo(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd").format(LocalDate.now()));
        assertThat(header.substring(110, 117)).isEqualTo("0000007"); // número da remessa
        assertThat(header.substring(390, 394)).isEqualTo("2.00");
        assertThat(header.substring(394, 400)).isEqualTo("000001");
    }

    @Test
    void test_detalheTipo1_impressaoEPostagemPeloBeneficiario() {
        Banco banco = criarBanco();
        Empresa empresa = criarEmpresa();
        TituloReceber titulo = criarTitulo("0000123", new BigDecimal("1500.75"));
        byte[] arquivo = handler.gerarRemessa(empresa, banco, List.of(titulo), 1);
        String detalhe = separarLinhas(arquivo)[1];

        assertThat(detalhe.substring(0, 1)).isEqualTo("1");
        assertThat(detalhe.substring(1, 2)).isEqualTo("A"); // tipo cobrança
        assertThat(detalhe.substring(2, 3)).isEqualTo("A"); // tipo carteira
        assertThat(detalhe.substring(3, 4)).isEqualTo("A"); // tipo impressão (normal)
        assertThat(detalhe.substring(71, 72)).isEqualTo("N"); // postagem: beneficiário
        assertThat(detalhe.substring(73, 74)).isEqualTo("B"); // impressão: beneficiário
        assertThat(detalhe.substring(108, 110)).isEqualTo("01"); // instrução: cadastro
        assertThat(detalhe.substring(110, 120)).isEqualTo("0000123   "); // seu número, alinhado à esquerda
        assertThat(detalhe.substring(120, 126)).isEqualTo(titulo.getDataVencimento()
                .format(java.time.format.DateTimeFormatter.ofPattern("ddMMyy")));
        assertThat(detalhe.substring(126, 139)).isEqualTo("0000000150075"); // valor em centavos
        assertThat(detalhe.substring(148, 149)).isEqualTo("J"); // espécie: DSI (duplicata de serviço)
        assertThat(detalhe.substring(149, 150)).isEqualTo("N"); // aceite
        assertThat(detalhe.substring(218, 219)).isEqualTo("2"); // CNPJ (14 dígitos)
        assertThat(detalhe.substring(220, 234)).isEqualTo("00000000000191");
        assertThat(detalhe.substring(234, 274).trim()).isEqualTo("CLIENTE DE TESTE");
        assertThat(detalhe.substring(326, 334)).isEqualTo("01310100"); // CEP
        assertThat(detalhe.substring(394, 400)).isEqualTo("000002"); // nº sequencial (header é 1)
    }

    @Test
    void test_detalheTipo1_jurosEMultaSempreIsento() {
        // TituloReceber não tem campo de juros/multa — conferido contra a remessa real da
        // Radio, onde ValorMoraJuros/PercentualMulta sempre saem 0,00 (ver SicrediCnab400Handler).
        Banco banco = criarBanco();
        Empresa empresa = criarEmpresa();
        TituloReceber titulo = criarTitulo("0000001", BigDecimal.TEN);
        byte[] arquivo = handler.gerarRemessa(empresa, banco, List.of(titulo), 1);
        String detalhe = separarLinhas(arquivo)[1];

        assertThat(detalhe.substring(18, 19)).isEqualTo("B"); // tipo juros: isento
        assertThat(detalhe.substring(19, 20)).isEqualTo(" "); // tipo multa: não usado
        assertThat(detalhe.substring(20, 28)).isEqualTo("        "); // data início juros
        assertThat(detalhe.substring(28, 36)).isEqualTo("        "); // data início multa
        assertThat(detalhe.substring(96, 108)).isEqualTo("            "); // valor multa
    }

    @Test
    void test_detalheTipo1_enderecoSacadoComBairroCidadeUf() {
        // Mesmo título 1 da remessa real da Radio (Pousada Boa Vida) — conferido caractere a
        // caractere contra o arquivo real: "FERNAO DIAS,0,PIRES,EXTREMA,MG".
        Banco banco = criarBanco();
        Empresa empresa = criarEmpresa();
        Parceiro sacado = criarParceiro();
        sacado.setLogradouro("Fernão Dias");
        sacado.setNumero("0");
        sacado.setBairro("Pires");
        br.com.axialsoftware.axctg3.entity.tabelas.Municipio municipio =
                new br.com.axialsoftware.axctg3.entity.tabelas.Municipio();
        municipio.setNome("Extrema");
        sacado.setMunicipio(municipio);
        sacado.setEstado("MG");
        TituloReceber titulo = criarTitulo("0000001", BigDecimal.TEN);
        titulo.setParceiro(sacado);

        byte[] arquivo = handler.gerarRemessa(empresa, banco, List.of(titulo), 1);
        String detalhe = separarLinhas(arquivo)[1];

        String esperado = "FERNAO DIAS,0,PIRES,EXTREMA,MG";
        assertThat(detalhe.substring(274, 314)).isEqualTo(esperado + " ".repeat(40 - esperado.length()));
    }

    @Test
    void test_trailerRemessa() {
        Banco banco = criarBanco();
        Empresa empresa = criarEmpresa();
        List<TituloReceber> titulos = List.of(criarTitulo("0000001", BigDecimal.TEN), criarTitulo("0000002", BigDecimal.ONE));
        byte[] arquivo = handler.gerarRemessa(empresa, banco, titulos, 1);
        String trailer = separarLinhas(arquivo)[3];

        assertThat(trailer.substring(0, 1)).isEqualTo("9");
        assertThat(trailer.substring(1, 2)).isEqualTo("1");
        assertThat(trailer.substring(2, 5)).isEqualTo("748");
        assertThat(trailer.substring(5, 10)).isEqualTo("00623");
        assertThat(trailer.substring(394, 400)).isEqualTo("000004"); // header + 2 detalhes + este trailer
    }

    @Test
    void test_gerarRemessaGravaNumBancoENossoNumAtualAvanca() {
        Banco banco = criarBanco();
        banco.setNossoNumAtual(null);
        Empresa empresa = criarEmpresa();
        TituloReceber titulo1 = criarTitulo("0000001", BigDecimal.TEN);
        TituloReceber titulo2 = criarTitulo("0000002", BigDecimal.ONE);

        handler.gerarRemessa(empresa, banco, List.of(titulo1, titulo2), 1);

        assertThat(titulo1.getNumBanco()).hasSize(9);
        assertThat(titulo2.getNumBanco()).hasSize(9);
        assertThat(titulo1.getNumBanco()).isNotEqualTo(titulo2.getNumBanco());
        assertThat(banco.getNossoNumAtual()).isEqualTo("00002");
    }

    @Test
    void test_calculoDvNossoNumero_deterministicoEUmDigito() {
        Banco banco = criarBanco(); // agência 0165, posto 02, cedente 00623 (exemplo do manual)
        int dv1 = SicrediCnab400Handler.calcularDvNossoNumero(banco, "07", 2, "00003");
        int dv2 = SicrediCnab400Handler.calcularDvNossoNumero(banco, "07", 2, "00003");

        // Mesma entrada do exemplo do manual (seção 4.5) — o PDF não trouxe o dígito
        // esperado (provável imagem, não texto selecionável), então só confere que o
        // cálculo é determinístico e produz um único dígito; conferir contra um
        // caso real/homologação Sicredi quando possível.
        assertThat(dv1).isEqualTo(dv2);
        assertThat(dv1).isBetween(0, 9);
    }

    @Test
    void test_calculoDvNossoNumero_confereContraRemessaRealDaRadio() {
        // Nosso Número real gerado pelo Axial/ACBr numa remessa Sicredi de verdade:
        // 262000369 (ano=26, byte=2, seq=00036, dv=9) — agência 0738, posto 33 (= dígito
        // verificador da agência, ver Banco.getPosto()), cedente 59622.
        Banco banco = new Banco();
        banco.setCodigo(1);
        banco.setCodGeral(748);
        banco.setNome("Sicredi");
        banco.setCodCedente("59622");
        banco.setAgencia("0738");
        banco.setPosto("33");

        int dv = SicrediCnab400Handler.calcularDvNossoNumero(banco, "26", 2, "00036");

        assertThat(dv).isEqualTo(9);
    }

    @Test
    void test_lerRetorno_entradaConfirmadaELiquidacao() {
        String linhaConfirmacao = linhaDetalheRetorno("02", "0000001", "250325200000123450",
                "010125", "150125", new BigDecimal("500.00"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        String linhaLiquidacao = linhaDetalheRetorno("06", "0000002", "250325200000123460",
                "020125", "150125", new BigDecimal("1000.00"), new BigDecimal("990.00"), new BigDecimal("10.00"), BigDecimal.ZERO);
        String arquivoTexto = linhaConfirmacao + "\r\n" + linhaLiquidacao + "\r\n";

        List<RetornoDetalheLido> detalhes = handler.lerRetorno(arquivoTexto.getBytes(StandardCharsets.ISO_8859_1));

        assertThat(detalhes).hasSize(2);
        RetornoDetalheLido confirmacao = detalhes.get(0);
        assertThat(confirmacao.seuNumero()).isEqualTo("0000001");
        assertThat(confirmacao.ocorrencia()).isEqualTo("02");
        assertThat(confirmacao.dataOcorrencia()).isEqualTo(LocalDate.of(2025, 1, 1));
        assertThat(confirmacao.valorTitulo()).isEqualByComparingTo("500.00");

        RetornoDetalheLido liquidacao = detalhes.get(1);
        assertThat(liquidacao.seuNumero()).isEqualTo("0000002");
        assertThat(liquidacao.ocorrencia()).isEqualTo("06");
        assertThat(liquidacao.valorPago()).isEqualByComparingTo("990.00");
        assertThat(liquidacao.juros()).isEqualByComparingTo("10.00");
    }

    private static String[] separarLinhas(byte[] arquivo) {
        String texto = new String(arquivo, StandardCharsets.ISO_8859_1);
        return texto.split("\r\n");
    }

    /** Monta uma linha de detalhe de retorno sintética só com os campos usados pelo parser. */
    private static String linhaDetalheRetorno(String ocorrencia, String seuNumero, String nossoNumero,
                                               String dataOcorrenciaDdmmaa, String dataVencimentoDdmmaa,
                                               BigDecimal valorTitulo, BigDecimal valorPago, BigDecimal juros, BigDecimal desconto) {
        SicrediCnab400Handler.Linha l = new SicrediCnab400Handler.Linha();
        l.numerico("1", 1);                                 // 001 id registro
        l.alfa("A", 1);                                      // 002 tipo carteira
        l.filler(11);                                         // 003-013
        l.alfa("A", 1);                                       // 014 tipo cobrança
        l.filler(5);                                           // 015-019 código pagador cooperativa
        l.filler(5);                                            // 020-024 código pagador associado
        l.numerico("2", 1);                                      // 025 boleto DDA
        l.filler(22);                                             // 026-047
        l.alfa(nossoNumero, 15);                                   // 048-062
        l.filler(46);                                               // 063-108
        l.alfa(ocorrencia, 2);                                       // 109-110
        l.numerico(dataOcorrenciaDdmmaa, 6);                          // 111-116
        l.alfa(seuNumero, 10);                                         // 117-126
        l.filler(20);                                                   // 127-146
        l.numerico(dataVencimentoDdmmaa, 6);                             // 147-152
        l.numerico(valorEmCentavosTeste(valorTitulo), 13);                // 153-165
        l.filler(9);                                                       // 166-174
        l.alfa("A", 1);                                                     // 175
        l.numerico("0", 13);                                                 // 176-188 despesas cobrança
        l.numerico("0", 13);                                                  // 189-201 despesas custas protesto
        l.numerico("0", 26);                                                   // 202-227
        l.numerico("0", 13);                                                    // 228-240 abatimento
        l.numerico(valorEmCentavosTeste(desconto), 13);                         // 241-253 desconto
        l.numerico(valorEmCentavosTeste(valorPago), 13);                        // 254-266 valor pago
        l.numerico(valorEmCentavosTeste(juros), 13);                            // 267-279 juros
        l.numerico("0", 13);                                                    // 280-292 multa
        l.filler(2);                                                            // 293-294
        l.alfa(" ", 1);                                                        // 295
        l.filler(23);                                                          // 296-318
        l.alfa(" ", 10);                                                       // 319-328 motivos
        l.filler(66);                                                          // 329-394
        l.numerico("1", 6);                                                    // 395-400
        return l.fechar();
    }

    private static String valorEmCentavosTeste(BigDecimal valor) {
        return valor.movePointRight(2).toBigInteger().toString();
    }

    private static Banco criarBanco() {
        Banco banco = new Banco();
        banco.setCodigo(1);
        banco.setCodGeral(748);
        banco.setNome("Sicredi");
        banco.setCodCedente("623"); // manual: "00623"
        banco.setAgencia("165");    // manual: "0165"
        banco.setPosto("2");        // manual: "02"
        banco.setByteGeracaoNossoNumero(2);
        banco.setNossoNumAtual(null);
        return banco;
    }

    private static Empresa criarEmpresa() {
        Empresa empresa = new Empresa();
        empresa.setCnpj("00.000.000/0001-91");
        return empresa;
    }

    private static TituloReceber criarTitulo(String numero, BigDecimal valor) {
        TituloReceber titulo = new TituloReceber();
        titulo.setNumero(numero);
        titulo.setDataEmissao(LocalDate.of(2026, 1, 1));
        titulo.setDataVencimento(LocalDate.of(2026, 1, 15));
        titulo.setValor(valor);
        titulo.setParceiro(criarParceiro());
        return titulo;
    }

    private static Parceiro criarParceiro() {
        Parceiro parceiro = new Parceiro();
        parceiro.setNome("Cliente de teste");
        parceiro.setCnpj("00.000.000/0001-91");
        parceiro.setLogradouro("Rua Teste");
        parceiro.setNumero("100");
        parceiro.setCep("01310-100");
        return parceiro;
    }
}
