package br.com.axialsoftware.axctg3.service.financeiro;

import br.com.axialsoftware.axctg3.entity.cadastros.Empresa;
import br.com.axialsoftware.axctg3.entity.cadastros.Parceiro;
import br.com.axialsoftware.axctg3.entity.financeiro.Banco;
import br.com.axialsoftware.axctg3.entity.financeiro.TituloReceber;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * CNAB400 do Sicredi (Febraban 748) — leiaute conferido campo a campo contra o manual
 * oficial ("Sicredi CNAB400.pdf", seções 8 e 9). Boleto é emitido pelo cliente
 * (beneficiário/cedente), não pelo Sicredi — por isso o Nosso Número é calculado aqui
 * (seção 4.4/4.5 do manual: formato {@code AABXXXXXD}, DV por módulo 11) em vez de deixado
 * em branco pro banco preencher.
 *
 * <p>Cada linha (header, detalhe, trailer) tem exatamente 400 colunas. Registros de
 * remessa fora do Tipo 1 (Mensagem/Informativo/Beneficiário Final/Descontos/Híbrido) e
 * ocorrências de retorno além de entrada/liquidação (protesto, negativação, abatimento,
 * alteração de vencimento) ficam fora de escopo — ver plano da feature.
 */
@Component
public class SicrediCnab400Handler implements BancoCobrancaHandler {

    private static final int COD_GERAL_SICREDI = 748;
    private static final int TAMANHO_LINHA = 400;

    // Dígito verificador Febraban do código do banco (fixo, não depende do título) — sai
    // impresso como "748-X" no canto do boleto (ver os PDFs reais de referência).
    private static final String DIGITO_VERIFICADOR_BANCO = "X";

    // Base do fator de vencimento (seção "Composição do Código de Barras" do manual) —
    // fórmula e data-base conferidas contra o código-fonte do ACBrBoleto
    // (TACBrBancoClass.CalcularFatorVencimento em ACBrBoleto.pas, o mesmo componente que
    // gerou os PDFs de referência): fator = ((diasDesde03/07/2000) mod 9000) + 1000. Não é
    // a data-base "22/02/2025" da nova regra Febraban por coincidência — o mod 9000 dessa
    // fórmula mais antiga já cruza por 1000 exatamente nessa data.
    private static final LocalDate BASE_FATOR_VENCIMENTO = LocalDate.of(2000, 7, 3);

    private static final DateTimeFormatter DATA_AAAAMMDD = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter DATA_DDMMAA = DateTimeFormatter.ofPattern("ddMMyy");
    // "." fica liberado porque o campo "Versão do sistema" (391-394) exige o ponto
    // literal ("2.00") — ver header(). "," fica liberado porque o endereço do sacado
    // (275-314) usa vírgula como separador entre logradouro/número/bairro/cidade/UF — ver
    // enderecoSacado().
    private static final Pattern NAO_ALFANUMERICO_ESPACO = Pattern.compile("[^A-Z0-9 .,]");
    private static final Pattern NAO_DIGITO = Pattern.compile("\\D");

    @Override
    public Integer getCodGeralSuportado() {
        return COD_GERAL_SICREDI;
    }

    // ------------------------------------------------------------------------------
    // Remessa
    // ------------------------------------------------------------------------------

    @Override
    public byte[] gerarRemessa(Empresa empresa, Banco banco, List<TituloReceber> titulos, int numeroRemessa) {
        LocalDate dataRemessa = LocalDate.now();
        StringBuilder arquivo = new StringBuilder();
        arquivo.append(header(empresa, banco, numeroRemessa, dataRemessa)).append("\r\n");

        int sequencial = 2; // 1 é o header
        int anoDoisDigitos = dataRemessa.getYear() % 100;
        for (TituloReceber tituloReceber : titulos) {
            int seqNossoNumero = proximoSequencialNossoNumero(banco);
            String nossoNumero = gerarNossoNumero(banco, anoDoisDigitos, seqNossoNumero);
            tituloReceber.setNumBanco(nossoNumero);
            arquivo.append(detalheTipo1(tituloReceber, nossoNumero, sequencial, dataRemessa)).append("\r\n");
            sequencial++;
        }

        arquivo.append(trailer(banco, sequencial)).append("\r\n");
        return arquivo.toString().getBytes(StandardCharsets.ISO_8859_1);
    }

    private String header(Empresa empresa, Banco banco, int numeroRemessa, LocalDate dataRemessa) {
        Linha l = new Linha();
        l.numerico("0", 1);                                    // 001-001 id. registro
        l.numerico("1", 1);                                    // 002-002 id. arquivo remessa
        l.alfa("REMESSA", 7);                                   // 003-009
        l.numerico("01", 2);                                    // 010-011 cód. serviço cobrança
        l.alfa("COBRANCA", 8);                                   // 012-019
        l.filler(7);                                             // 020-026
        l.numerico(banco.getCodCedente(), 5);                    // 027-031
        l.numerico(empresa.getCnpj(), 14);                       // 032-045
        l.filler(31);                                            // 046-076
        l.numerico(String.valueOf(COD_GERAL_SICREDI), 3);        // 077-079
        l.alfa("SICREDI", 15);                                   // 080-094
        l.numerico(dataRemessa.format(DATA_AAAAMMDD), 8);        // 095-102
        l.filler(8);                                              // 103-110
        l.numerico(String.valueOf(numeroRemessa), 7);            // 111-117
        l.filler(273);                                            // 118-390
        l.alfa("2.00", 4);                                        // 391-394 versão do sistema
        l.numerico("1", 6);                                       // 395-400 nº sequencial do registro
        return l.fechar();
    }

    private String detalheTipo1(TituloReceber tituloReceber, String nossoNumero, int numeroSequencial,
                                 LocalDate dataRemessa) {
        Parceiro sacado = tituloReceber.getParceiro();
        String cnpjCpfSacado = soDigitos(sacado.getCnpj());
        boolean sacadoPessoaJuridica = cnpjCpfSacado.length() > 11;

        Linha l = new Linha();
        l.numerico("1", 1);                              // 001 id. registro detalhe
        l.alfa("A", 1);                                   // 002 tipo cobrança: Sicredi com registro
        l.alfa("A", 1);                                   // 003 tipo carteira: Simples
        l.alfa("A", 1);                                   // 004 tipo impressão: Normal
        l.filler(1);                                      // 005
        l.alfa(" ", 1);                                   // 006 tipo boleto: não híbrido
        l.filler(10);                                     // 007-016
        l.alfa("A", 1);                                   // 017 tipo moeda: Real
        l.alfa("A", 1);                                   // 018 tipo desconto: valor monetário
        // 019-020 e 021-036: TituloReceber não tem campo de juros/multa — sempre isento,
        // conferido contra remessa real (ValorMoraJuros/PercentualMulta sempre 0,00 no
        // arquivo que o Axial manda pro ACBrMonitorPLUS).
        l.alfa("B", 1);                                   // 019 tipo juros: isento
        l.alfa(" ", 1);                                   // 020 tipo multa: não usado
        l.filler(8);                                      // 021-028 data início juros: não usado
        l.filler(8);                                      // 029-036 data início multa: não usado
        l.filler(11);                                     // 037-047
        l.alfa(nossoNumero, 9);                            // 048-056 nosso número
        l.filler(6);                                       // 057-062
        l.numerico(dataRemessa.format(DATA_AAAAMMDD), 8);   // 063-070 data instrução (data de geração da remessa)
        l.alfa(" ", 1);                                    // 071 (só usado com instrução 31)
        l.alfa("N", 1);                                    // 072 postagem: beneficiário posta
        l.filler(1);                                       // 073
        l.alfa("B", 1);                                    // 074 impressão: beneficiário/cedente
        // 075-078: numérico no manual, mas o Axial (via ACBrMonitorPLUS, aceito pelo banco
        // na prática) deixa em branco quando não é carnê — mantido igual ao Axial.
        l.filler(2);                                       // 075-076 nº parcela carnê: não usado
        l.filler(2);                                       // 077-078 nº total parcelas carnê: não usado
        l.filler(4);                                       // 079-082
        l.numerico("0", 10);                               // 083-092 desconto por antecipação: não usado
        l.numerico("0", 4);                                // 093-096 multa percentual: não usado
        l.filler(12);                                       // 097-108 multa valor: não usado (isento, ver 020)
        l.numerico("01", 2);                               // 109-110 instrução: cadastro de título
        l.alfa(tituloReceber.getNumero(), 10);              // 111-120 seu número
        l.numerico(tituloReceber.getDataVencimento().format(DATA_DDMMAA), 6); // 121-126
        l.numerico(valorEmCentavos(tituloReceber.getValor()), 13);            // 127-139
        l.filler(9);                                        // 140-148
        // 149 espécie: DSI (Duplicata de Serviço por Indicação) — só emitimos cobrança de
        // serviço até agora (o arquivo real de referência traz Especie=DSI em 100% dos
        // títulos); quando entrar cliente que vende mercadoria, isso precisa virar campo.
        l.alfa("J", 1);
        l.alfa("N", 1);                                     // 150 aceite
        l.numerico(tituloReceber.getDataEmissao().format(DATA_DDMMAA), 6);    // 151-156
        l.numerico("00", 2);                                // 157-158 protesto automático: não protestar
        l.numerico("00", 2);                                // 159-160 dias pra protesto
        l.numerico("0", 13);                                // 161-173 juros por dia de atraso: não usado
        l.numerico("0", 6);                                 // 174-179 data limite desconto: não usado
        l.numerico("0", 13);                                // 180-192 desconto: não usado
        l.numerico("00", 2);                                // 193-194 negativação automática: não negativar
        l.numerico("00", 2);                                // 195-196 dias pra negativação
        l.numerico("0", 9);                                 // 197-205 (manual e Axial preenchem com zeros)
        l.numerico("0", 13);                                // 206-218 abatimento: não usado
        l.numerico(sacadoPessoaJuridica ? "2" : "1", 1);    // 219 tipo inscrição sacado
        l.numerico("0", 1);                                 // 220
        l.numerico(cnpjCpfSacado, 14);                      // 221-234
        l.alfa(sacado.getNome(), 40);                       // 235-274
        l.alfa(enderecoSacado(sacado), 40);                 // 275-314
        l.numerico("0", 5);                                 // 315-319 código pagador na cooperativa
        l.numerico("0", 6);                                 // 320-325
        l.filler(1);                                        // 326
        l.numerico(soDigitos(sacado.getCep()), 8);          // 327-334
        l.numerico("0", 5);                                 // 335-339 código pagador junto ao cliente
        l.filler(14);                                       // 340-353 CPF/CNPJ beneficiário final: não usado
        l.filler(41);                                       // 354-394 nome beneficiário final: não usado
        l.numerico(String.valueOf(numeroSequencial), 6);    // 395-400
        return l.fechar();
    }

    private String trailer(Banco banco, int numeroSequencial) {
        Linha l = new Linha();
        l.numerico("9", 1);                                  // 001 id. registro trailer
        l.numerico("1", 1);                                   // 002 id. arquivo remessa
        l.numerico(String.valueOf(COD_GERAL_SICREDI), 3);     // 003-005
        l.numerico(banco.getCodCedente(), 5);                 // 006-010
        l.filler(384);                                         // 011-394
        l.numerico(String.valueOf(numeroSequencial), 6);      // 395-400
        return l.fechar();
    }

    /**
     * Formato {@code LOGRADOURO,NUMERO,BAIRRO,CIDADE,UF} — conferido caractere a caractere
     * contra o arquivo real que o Axial manda pro ACBrMonitorPLUS e a remessa real gerada a
     * partir dele (ex.: {@code FERNAO DIAS,0,PIRES,EXTREMA,MG}). Truncado em 40 colunas pelo
     * {@code alfa()} da {@link Linha}.
     */
    private static String enderecoSacado(Parceiro sacado) {
        String cidade = sacado.getMunicipio() != null ? sacado.getMunicipio().getNome() : null;
        return String.join(",",
                nvl(sacado.getLogradouro()),
                nvl(sacado.getNumero()),
                nvl(sacado.getBairro()),
                nvl(cidade),
                nvl(sacado.getEstado()));
    }

    private static String nvl(String valor) {
        return valor == null ? "" : valor;
    }

    private static String valorEmCentavos(BigDecimal valor) {
        return valor.setScale(2, RoundingMode.HALF_UP)
                .movePointRight(2)
                .toBigInteger()
                .toString();
    }

    // ------------------------------------------------------------------------------
    // Nosso Número (seção 4.4/4.5 do manual) — gerado pelo axctg3 porque o boleto é
    // emitido pelo beneficiário, não pelo Sicredi.
    // ------------------------------------------------------------------------------

    /** Avança e devolve o próximo sequencial (0-99999) — mesmo padrão do {@code NUMBANCOATUAL} do Axial. */
    private static int proximoSequencialNossoNumero(Banco banco) {
        int atual = 0;
        if (banco.getNossoNumAtual() != null && !banco.getNossoNumAtual().isBlank()) {
            try {
                atual = Integer.parseInt(soDigitos(banco.getNossoNumAtual()));
            } catch (NumberFormatException ignored) {
                atual = 0;
            }
        }
        int proximo = atual + 1;
        banco.setNossoNumAtual(String.format("%05d", proximo % 100000));
        return proximo % 100000;
    }

    /** Formato {@code AABXXXXXD}: ano (2) + byte de geração (1) + sequencial (5) + DV (1). */
    static String gerarNossoNumero(Banco banco, int anoDoisDigitos, int sequencial) {
        int byteGeracao = banco.getByteGeracaoNossoNumero() != null ? banco.getByteGeracaoNossoNumero() : 0;
        String ano = String.format("%02d", anoDoisDigitos % 100);
        String seq = String.format("%05d", sequencial);
        int dv = calcularDvNossoNumero(banco, ano, byteGeracao, seq);
        return ano + byteGeracao + seq + dv;
    }

    /**
     * DV por módulo 11 sobre {@code aaaappcccccyybnnnnn} (agência/cooperativa + posto +
     * código do cedente + ano + byte de geração + sequencial, 19 dígitos), pesos 2..9
     * ciclando da direita pra esquerda. Resto 0 ou 1 → DV = 0 (regra do manual).
     *
     * <p>"Posto" aqui é a nomenclatura do próprio manual — não é um Posto de Atendimento
     * físico separado; na prática é o dígito verificador da agência/cooperativa (o mesmo
     * número que o ACBrBoleto guarda em {@code DigitoAgencia} no cadastro de conta). Ver
     * {@link Banco#getPosto()}.
     *
     * <p>Algoritmo confirmado em 2026-09-07 contra remessa real da Sicredi: Nosso Número
     * {@code 262000369} (ano=26, byte=2, seq=00036) com agência 0738/posto 33/cedente 59622
     * dá DV=9, batendo com o dígito real gerado pelo Axial/ACBr.
     */
    static int calcularDvNossoNumero(Banco banco, String ano, int byteGeracao, String sequencial) {
        String agencia = numerico(banco.getAgencia(), 4);
        String posto = numerico(banco.getPosto(), 2);
        String codCedente = numerico(banco.getCodCedente(), 5);
        String base = agencia + posto + codCedente + ano + byteGeracao + sequencial;
        return dvModulo11RestoZeroUmViraZero(base);
    }

    /**
     * Módulo 11 padrão (pesos 2..9 ciclando da direita pra esquerda), resto 0 ou 1 → DV 0 —
     * regra usada tanto pro DV do Nosso Número quanto pro DV do campo livre do código de
     * barras (confirmado contra {@code TACBrBancoSicredi.CalcularDigitoVerificador} e
     * {@code TACBrBancoSicredi.MontarCodigoBarras} em ACBrBancoSicredi.pas — mesmo
     * {@code Modulo.CalculoPadrao} nos dois casos).
     */
    private static int dvModulo11RestoZeroUmViraZero(String base) {
        int soma = 0;
        int peso = 2;
        for (int i = base.length() - 1; i >= 0; i--) {
            soma += (base.charAt(i) - '0') * peso;
            peso = peso == 9 ? 2 : peso + 1;
        }
        int resto = soma % 11;
        int dv = 11 - resto;
        return (dv == 10 || dv == 11) ? 0 : dv;
    }

    /**
     * Módulo 11 padrão do DV geral do código de barras (Febraban, todos os bancos) — mesmos
     * pesos 2..9, mas resto 0 ou 1 → DV 1 (regra diferente da do Nosso Número/campo livre).
     * Confirmado contra {@code TACBrBancoClass.CalcularDigitoCodigoBarras} em ACBrBoleto.pas
     * e recalculado na mão contra um boleto real (748-9-<b>9</b>-1381-0000120000-...).
     */
    private static int dvModulo11RestoZeroUmViraUm(String base) {
        int soma = 0;
        int peso = 2;
        for (int i = base.length() - 1; i >= 0; i--) {
            soma += (base.charAt(i) - '0') * peso;
            peso = peso == 9 ? 2 : peso + 1;
        }
        int resto = soma % 11;
        int dv = 11 - resto;
        return (dv == 10 || dv == 11) ? 1 : dv;
    }

    // ------------------------------------------------------------------------------
    // Boleto — código de barras (44 dígitos) e campo livre (seção "Composição do Código
    // de Barras" do manual Sicredi CNAB400.pdf, seção 4). Fórmula do campo livre e do
    // fator de vencimento conferidas campo a campo contra ACBrBancoSicredi.pas/
    // ACBrBoleto.pas e contra 2 boletos reais (ver SicrediCnab400HandlerTest).
    // ------------------------------------------------------------------------------

    @Override
    public String getDigitoVerificadorBanco() {
        return DIGITO_VERIFICADOR_BANCO;
    }

    @Override
    public String montarCodigoBarras(Banco banco, TituloReceber tituloReceber) {
        String campoLivre = montarCampoLivre(banco, tituloReceber);
        String fatorVencimento = fatorVencimento(tituloReceber.getDataVencimento());
        String valor = numerico(valorEmCentavos(tituloReceber.getValor()), 10);

        String semDv = COD_GERAL_SICREDI + "9" + fatorVencimento + valor + campoLivre; // 43 dígitos
        int dvGeral = dvModulo11RestoZeroUmViraUm(semDv);

        return COD_GERAL_SICREDI + "9" + dvGeral + fatorVencimento + valor + campoLivre; // 44 dígitos
    }

    /**
     * 25 dígitos: modalidade (1, fixo "com registro") + carteira (1, fixo "simples") +
     * Nosso Número (9) + agência/cooperativa (4) + posto (2) + código do cedente (5) +
     * filler "1" (documentado no ACBr como "será 1 quando valor do documento for diferente
     * de zero" — sempre 1 na prática) + filler "0" + DV do campo livre (1).
     */
    private static String montarCampoLivre(Banco banco, TituloReceber tituloReceber) {
        String digitosNumBanco = soDigitos(tituloReceber.getNumBanco());
        // numerico() silenciosamente zero-preenche pela esquerda — um Nosso Número mais
        // curto que 9 dígitos (ex.: importado do legado como sequencial cru, sem o
        // prefixo ano+byte) viraria um código de barras com ano/byte errados sem avisar
        // ninguém. Bug real encontrado em 2026-09-09: título com numBanco="00036" (5
        // dígitos) gerou "00/000003-6" em vez do "26/200036-9" correto.
        if (digitosNumBanco.length() != 9) {
            throw new IllegalArgumentException("Título " + tituloReceber.getNumero()
                    + " tem Nosso Número \"" + tituloReceber.getNumBanco() + "\" fora do formato esperado "
                    + "(9 dígitos: ano+byte+sequencial+DV) — provavelmente importado do legado sem o prefixo "
                    + "ano/byte. Gere a remessa bancária de novo pra esse título antes de emitir o boleto.");
        }
        String nossoNumero = numerico(digitosNumBanco, 9);
        String semDv = "1" + "1" + nossoNumero
                + numerico(banco.getAgencia(), 4)
                + numerico(banco.getPosto(), 2)
                + numerico(banco.getCodCedente(), 5)
                + "1" + "0"; // 24 dígitos
        int dv = dvModulo11RestoZeroUmViraZero(semDv);
        return semDv + dv; // 25 dígitos
    }

    /**
     * {@code ((diasDesde03/07/2000) mod 9000) + 1000}, 4 dígitos. Confirmado contra
     * {@code TACBrBancoClass.CalcularFatorVencimento} (ACBrBoleto.pas) e contra vencimento
     * real 10/03/2026 → fator 1381.
     */
    static String fatorVencimento(LocalDate vencimento) {
        long dias = ChronoUnit.DAYS.between(BASE_FATOR_VENCIMENTO, vencimento);
        long fator = Math.floorMod(dias, 9000) + 1000;
        return String.format("%04d", fator);
    }

    /** {@code AA/BXXXXX-D} a partir do Nosso Número {@code AABXXXXXD} (9 dígitos). */
    @Override
    public String formatarNossoNumero(String numBanco) {
        String digitos = numerico(numBanco, 9);
        return digitos.substring(0, 2) + "/" + digitos.substring(2, 8) + "-" + digitos.substring(8, 9);
    }

    /** {@code AAAA.PP.CCCCC} — agência (4) + posto (2) + código do cedente (5). */
    @Override
    public String formatarAgenciaCodigoBeneficiario(Banco banco) {
        return numerico(banco.getAgencia(), 4) + "." + numerico(banco.getPosto(), 2) + "." + numerico(banco.getCodCedente(), 5);
    }

    // ------------------------------------------------------------------------------
    // Retorno
    // ------------------------------------------------------------------------------

    @Override
    public List<RetornoDetalheLido> lerRetorno(byte[] arquivo) {
        String conteudo = new String(arquivo, StandardCharsets.ISO_8859_1);
        List<RetornoDetalheLido> detalhes = new ArrayList<>();
        for (String linha : conteudo.split("\r\n|\n|\r")) {
            if (linha.length() < TAMANHO_LINHA) {
                continue; // header/trailer curtos ou linha em branco final — ignora
            }
            char idRegistro = linha.charAt(0);
            if (idRegistro != '1') {
                continue; // só o registro de detalhe interessa (header '0', trailer '9')
            }
            detalhes.add(lerDetalhe(linha));
        }
        return detalhes;
    }

    private RetornoDetalheLido lerDetalhe(String linha) {
        String nossoNumero = campo(linha, 48, 62).trim();
        String ocorrencia = campo(linha, 109, 110).trim();
        LocalDate dataOcorrencia = parseDataDdmmaa(campo(linha, 111, 116));
        String seuNumero = campo(linha, 117, 126).trim();
        LocalDate dataVencimento = parseDataDdmmaa(campo(linha, 147, 152));
        BigDecimal valorTitulo = parseValorCentavos(campo(linha, 153, 165));
        BigDecimal abatimento = parseValorCentavos(campo(linha, 228, 240));
        BigDecimal desconto = parseValorCentavos(campo(linha, 241, 253));
        BigDecimal valorPago = parseValorCentavos(campo(linha, 254, 266));
        BigDecimal juros = parseValorCentavos(campo(linha, 267, 279));
        String motivos = campo(linha, 319, 328).trim();

        return new RetornoDetalheLido(seuNumero, nossoNumero, ocorrencia, dataOcorrencia, dataVencimento,
                valorTitulo, valorPago, juros, desconto, abatimento, motivos);
    }

    /** Posições do manual são 1-based e inclusivas em ambas as pontas. */
    private static String campo(String linha, int inicioUmBased, int fimUmBased) {
        return linha.substring(inicioUmBased - 1, fimUmBased);
    }

    private static LocalDate parseDataDdmmaa(String ddmmaa) {
        if (ddmmaa == null || ddmmaa.isBlank() || ddmmaa.chars().allMatch(c -> c == '0')) {
            return null;
        }
        try {
            return LocalDate.parse(ddmmaa, DATA_DDMMAA);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static BigDecimal parseValorCentavos(String valor) {
        if (valor == null || valor.isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(valor).movePointLeft(2);
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    // ------------------------------------------------------------------------------
    // Utilidades de formatação de campo fixo
    // ------------------------------------------------------------------------------

    private static String soDigitos(String s) {
        return s == null ? "" : NAO_DIGITO.matcher(s).replaceAll("");
    }

    /** Alinhado à direita, zeros à esquerda; corta os dígitos mais à esquerda se sobrar. */
    private static String numerico(String valor, int tamanho) {
        String digitos = soDigitos(valor);
        if (digitos.length() > tamanho) {
            digitos = digitos.substring(digitos.length() - tamanho);
        }
        return "0".repeat(tamanho - digitos.length()) + digitos;
    }

    /** Maiúsculo, sem acento/caractere especial, alinhado à esquerda com espaço; corta se sobrar. */
    private static String semAcentoMaiusculo(String valor) {
        if (valor == null) {
            return "";
        }
        String semAcento = Normalizer.normalize(valor, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toUpperCase();
        return NAO_ALFANUMERICO_ESPACO.matcher(semAcento).replaceAll("");
    }

    /**
     * Uma linha CNAB de 400 colunas sendo montada campo a campo, na ordem do leiaute.
     * Pacote-visível (não {@code private}) só pra {@code SicrediCnab400HandlerTest}
     * conseguir montar retornos sintéticos de teste com os mesmos helpers.
     */
    static class Linha {
        private final StringBuilder buffer = new StringBuilder();

        void numerico(String valor, int tamanho) {
            buffer.append(SicrediCnab400Handler.numerico(valor, tamanho));
        }

        void alfa(String valor, int tamanho) {
            String texto = semAcentoMaiusculo(valor);
            if (texto.length() > tamanho) {
                texto = texto.substring(0, tamanho);
            }
            buffer.append(texto).append(" ".repeat(tamanho - texto.length()));
        }

        void filler(int tamanho) {
            buffer.append(" ".repeat(tamanho));
        }

        String fechar() {
            if (buffer.length() != TAMANHO_LINHA) {
                throw new IllegalStateException(
                        "Linha CNAB400 Sicredi com " + buffer.length() + " colunas, esperado " + TAMANHO_LINHA);
            }
            return buffer.toString();
        }
    }
}
