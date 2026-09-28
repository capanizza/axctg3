package br.com.axialsoftware.axctg3.service.financeiro;

import br.com.axialsoftware.axctg3.entity.cadastros.Empresa;
import br.com.axialsoftware.axctg3.entity.cadastros.Parceiro;
import br.com.axialsoftware.axctg3.entity.financeiro.Banco;
import br.com.axialsoftware.axctg3.entity.financeiro.BoletoDto;
import br.com.axialsoftware.axctg3.entity.financeiro.TituloReceber;
import br.com.axialsoftware.axctg3.service.RelatorioService;
import br.com.axialsoftware.axctg3.service.UtilGeralService;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Emissão do boleto (Recibo do Pagador + Ficha de Compensação) a partir de
 * {@link TituloReceber} já enviados em remessa (precisam de {@code numBanco} preenchido —
 * ver {@link RemessaBancoService#gerarRemessa}). Orquestração bank-agnostic, mesmo espírito
 * de {@link RemessaBancoService}: resolve a implementação certa pelo
 * {@link Banco#getCodGeral()}, delega a codificação do código de barras a
 * {@link BancoCobrancaHandler}, e escolhe o template {@code boleto<codGeral>.jasper} — o
 * leiaute impresso varia de banco pra banco (só a formatação da linha digitável a partir do
 * código de barras de 44 dígitos é padrão Febraban, comum a todos).
 *
 * <p>Um PDF por título, como no legado — os boletos vão por e-mail, um pra cada cliente.
 * {@link #emitirBoleto} devolve o PDF de um título só (a view mostra na tela);
 * {@link #emitirBoletosZip} empacota vários num zip, um {@code Boleto <número>.pdf} por
 * título. Nada é gravado em disco nem no FileStorage: o boleto é regerado a qualquer
 * momento pelo mesmo botão.
 */
@Service
public class BoletoService {

    private final RelatorioService relatorioService;
    private final UtilGeralService utilGeralService;
    private final List<BancoCobrancaHandler> handlers;

    // Data já formatada "dd/MM/yyyy" — o atributo pattern do JasperReports não formata
    // campo java.time.LocalDate como esperado nesse projeto (ver Javadoc de BoletoDto).
    private static final DateTimeFormatter DATA_BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public BoletoService(RelatorioService relatorioService, UtilGeralService utilGeralService,
                          List<BancoCobrancaHandler> handlers) {
        this.relatorioService = relatorioService;
        this.utilGeralService = utilGeralService;
        this.handlers = handlers;
    }

    /** Ver o mesmo comentário em {@code RemessaBancoService.resolverHandler}. */
    private BancoCobrancaHandler resolverHandler(Integer codGeral) {
        return handlers.stream()
                .filter(h -> codGeral != null && codGeral.equals(h.getCodGeralSuportado()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Banco código " + codGeral + " ainda não emite boleto"));
    }

    /** PDF do boleto de um título, com o template do banco dele. */
    public byte[] emitirBoleto(TituloReceber tituloReceber) {
        return relatorioService.exportarPdf(List.of(preencherBoleto(utilGeralService.getEmpresa(), tituloReceber)));
    }

    /**
     * Zip com um PDF por título ({@link #nomeArquivo}), na ordem recebida. Todos os títulos
     * são preenchidos antes de montar o zip, então um título inválido (sem Nosso Número,
     * banco sem handler) derruba a geração inteira em vez de sair um zip incompleto.
     */
    public byte[] emitirBoletosZip(List<TituloReceber> titulos) {
        Empresa empresa = utilGeralService.getEmpresa();
        Map<String, JasperPrint> boletos = new LinkedHashMap<>();
        for (TituloReceber tituloReceber : titulos) {
            // Número é único por empresa (IDX_TITULO_RECEBER_UNQ_NUMERO_COD_EMPRESA), então o
            // nome da entrada não colide.
            boletos.put(nomeArquivo(tituloReceber), preencherBoleto(empresa, tituloReceber));
        }

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
            for (Map.Entry<String, JasperPrint> boleto : boletos.entrySet()) {
                zip.putNextEntry(new ZipEntry(boleto.getKey()));
                zip.write(relatorioService.exportarPdf(List.of(boleto.getValue())));
                zip.closeEntry();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return bytes.toByteArray();
    }

    public static String nomeArquivo(TituloReceber tituloReceber) {
        return "Boleto " + tituloReceber.getNumero() + ".pdf";
    }

    private JasperPrint preencherBoleto(Empresa empresa, TituloReceber tituloReceber) {
        Integer codGeral = tituloReceber.getBanco().getCodGeral();
        BancoCobrancaHandler handler = resolverHandler(codGeral);

        HashMap<String, Object> parametros = new HashMap<>();
        parametros.put("LOGO", logoBanco(codGeral));

        BoletoDto boleto = montarDto(empresa, tituloReceber, handler);
        return relatorioService.preencherRelatorio("boleto" + codGeral + ".jasper",
                new JRBeanCollectionDataSource(List.of(boleto)), parametros);
    }

    /**
     * Logo do banco pro cabeçalho do boleto ({@code $P{LOGO}}) — empacotado no jar em
     * {@code src/main/resources/relatorios/logos/<codGeral>.png} (fonte: ACBrMonitorPLUS,
     * pasta {@code logos/}, arquivo nomeado pelo código Febraban do banco), materializado
     * num arquivo temporário estável porque o {@code imageExpression} do Jasper espera um
     * caminho de arquivo (String) — mesmo padrão de {@code UtilGeralService.getLogoEmpresa}.
     * {@code null} se o banco não tiver logo empacotado ainda; o template deve tolerar.
     */
    private String logoBanco(Integer codGeral) {
        String recurso = "/relatorios/logos/" + codGeral + ".png";
        try (InputStream is = getClass().getResourceAsStream(recurso)) {
            if (is == null) {
                return null;
            }
            Path destino = Paths.get(System.getProperty("java.io.tmpdir"), "axctg3-logo-banco-" + codGeral + ".png");
            Files.copy(is, destino, StandardCopyOption.REPLACE_EXISTING);
            return destino.toString();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private BoletoDto montarDto(Empresa empresa, TituloReceber tituloReceber, BancoCobrancaHandler handler) {
        if (tituloReceber.getNumBanco() == null || tituloReceber.getNumBanco().isBlank()) {
            throw new IllegalArgumentException("Título " + tituloReceber.getNumero()
                    + " ainda não tem Nosso Número — gere a remessa bancária primeiro.");
        }
        Banco banco = tituloReceber.getBanco();
        Parceiro pagador = tituloReceber.getParceiro();
        String codigoBarras = handler.montarCodigoBarras(banco, tituloReceber);

        BoletoDto dto = new BoletoDto();
        dto.setNumeroDocumento(tituloReceber.getNumero());
        dto.setDataDocumento(DATA_BR.format(tituloReceber.getDataEmissao()));
        dto.setDataVencimento(DATA_BR.format(tituloReceber.getDataVencimento()));
        dto.setDataProcessamento(DATA_BR.format(LocalDate.now()));
        dto.setValorDocumento(tituloReceber.getValor());
        dto.setEspecieDoc("DSI"); // única espécie emitida hoje — ver SicrediCnab400Handler.detalheTipo1
        dto.setAceite("N");
        dto.setCodigoBancoComDv(banco.getCodGeral() + "-" + handler.getDigitoVerificadorBanco());
        dto.setLocalPagamento(banco.getLocalPagamento());
        dto.setNossoNumeroFormatado(handler.formatarNossoNumero(banco, tituloReceber));
        dto.setAgenciaCodigoBeneficiario(handler.formatarAgenciaCodigoBeneficiario(banco));
        dto.setNomeBeneficiario(empresa.getNome());
        dto.setCnpjBeneficiario(formatarCnpj(empresa.getCnpj()));
        dto.setEnderecoBeneficiario(enderecoEmpresa(empresa));
        dto.setNomePagador(pagador.getNome());
        dto.setCnpjCpfPagador(formatarCnpjOuCpf(pagador.getCnpj()));
        dto.setEnderecoPagador(enderecoParceiro(pagador));
        dto.setInstrucoes(banco.getMensagem());
        dto.setCodigoBarras(codigoBarras);
        dto.setLinhaDigitavel(formatarLinhaDigitavel(codigoBarras));
        return dto;
    }

    // ------------------------------------------------------------------------------
    // Linha digitável — formatação padrão Febraban a partir dos 44 dígitos do código de
    // barras, igual pra qualquer banco (só a composição do código de barras em si varia por
    // banco, isso fica no BancoCobrancaHandler). Layout dos 5 campos e o módulo 10 dos
    // campos 1-3 conferidos na mão contra 2 boletos reais Sicredi (DV bateu nos dois).
    // ------------------------------------------------------------------------------

    static String formatarLinhaDigitavel(String codigoBarras) {
        String campoLivre = codigoBarras.substring(19, 44);
        String campo1Base = codigoBarras.substring(0, 4) + campoLivre.substring(0, 5);
        String campo2Base = campoLivre.substring(5, 15);
        String campo3Base = campoLivre.substring(15, 25);

        String campo1 = agrupar(campo1Base + dvModulo10(campo1Base));
        String campo2 = agrupar(campo2Base + dvModulo10(campo2Base));
        String campo3 = agrupar(campo3Base + dvModulo10(campo3Base));
        String dvGeral = codigoBarras.substring(4, 5);
        String fatorEValor = codigoBarras.substring(5, 19);

        return campo1 + " " + campo2 + " " + campo3 + " " + dvGeral + " " + fatorEValor;
    }

    /**
     * Primeiros 5 dígitos + ponto + resto — campo 1 (base de 9 + DV = 10 dígitos, grupos
     * 5+5) e campos 2/3 (base de 10 + DV = 11 dígitos, grupos 5+6) têm tamanho total
     * diferente, mas o corte é sempre no 5º dígito.
     */
    private static String agrupar(String campoComDv) {
        return campoComDv.substring(0, 5) + "." + campoComDv.substring(5);
    }

    /** Módulo 10 (Luhn) padrão Febraban dos campos 1-3 da linha digitável — peso 2 no dígito mais à direita. */
    private static int dvModulo10(String base) {
        int soma = 0;
        boolean pesoDois = true;
        for (int i = base.length() - 1; i >= 0; i--) {
            int digito = base.charAt(i) - '0';
            int produto = digito * (pesoDois ? 2 : 1);
            soma += produto > 9 ? produto - 9 : produto;
            pesoDois = !pesoDois;
        }
        int resto = soma % 10;
        return resto == 0 ? 0 : 10 - resto;
    }

    // ------------------------------------------------------------------------------
    // Formatação de exibição — beneficiário (empresa) e pagador (parceiro).
    // ------------------------------------------------------------------------------

    private static String enderecoEmpresa(Empresa empresa) {
        String cidade = empresa.getMunicipio() != null ? empresa.getMunicipio().getNome() : "";
        String uf = empresa.getMunicipio() != null ? nvl(empresa.getMunicipio().getUf()) : "";
        return nvl(empresa.getLogradouro()) + "," + nvl(empresa.getNumero()) + " " + nvl(empresa.getBairro())
                + ", " + cidade + "/" + uf + " " + formatarCep(empresa.getCep()) + " Fone: ";
    }

    private static String enderecoParceiro(Parceiro parceiro) {
        String cidade = parceiro.getMunicipio() != null ? parceiro.getMunicipio().getNome() : "";
        return nvl(parceiro.getLogradouro()) + " " + nvl(parceiro.getNumero()) + " - " + nvl(parceiro.getBairro())
                + ", " + cidade + " / " + nvl(parceiro.getEstado()) + " - " + nvl(parceiro.getCep());
    }

    private static String nvl(String valor) {
        return valor == null ? "" : valor;
    }

    private static String formatarCnpjOuCpf(String documento) {
        if (documento == null) {
            return "";
        }
        String digitos = documento.replaceAll("\\D", "");
        return digitos.length() > 11 ? formatarCnpj(digitos) : formatarCpf(digitos);
    }

    private static String formatarCnpj(String cnpj) {
        if (cnpj == null || cnpj.length() != 14) {
            return nvl(cnpj);
        }
        return cnpj.substring(0, 2) + "." + cnpj.substring(2, 5) + "." + cnpj.substring(5, 8)
                + "/" + cnpj.substring(8, 12) + "-" + cnpj.substring(12, 14);
    }

    private static String formatarCpf(String cpf) {
        if (cpf == null || cpf.length() != 11) {
            return nvl(cpf);
        }
        return cpf.substring(0, 3) + "." + cpf.substring(3, 6) + "." + cpf.substring(6, 9) + "-" + cpf.substring(9, 11);
    }

    private static String formatarCep(String cep) {
        if (cep == null) {
            return "";
        }
        String digitos = cep.replaceAll("\\D", "");
        if (digitos.length() != 8) {
            return cep;
        }
        return digitos.substring(0, 5) + "-" + digitos.substring(5, 8);
    }
}
