package br.com.axialsoftware.axctg3.service.tabelas;

import br.com.axialsoftware.axctg3.entity.tabelas.TabelaIbpt;
import io.jmix.core.DataManager;
import io.jmix.core.SaveContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Import da Tabela IBPT a partir do CSV oficial "De Olho no Imposto"
 * ({@code TabelaIBPTax<UF><versão>.csv}, separador ";", encoding Windows-1252, cabeçalho
 * {@code codigo;ex;tipo;descricao;nacionalfederal;importadosfederal;estadual;municipal;
 * vigenciainicio;vigenciafim;chave;versao;fonte}). O arquivo não traz a UF em nenhuma
 * coluna — ela vem só do nome do arquivo, então o nome original precisa ser mantido.
 * Substitui a UF inteira: apaga todas as linhas dela e grava as do arquivo, numa só
 * transação (arquivo com erro não deixa a UF pela metade).
 */
@Service
public class TabelaIbptImportService {

    private static final Pattern UF_NO_NOME = Pattern.compile("TabelaIBPTax([A-Za-z]{2})", Pattern.CASE_INSENSITIVE);
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Charset CP1252 = Charset.forName("windows-1252");
    private static final int LOTE = 1000;

    private final DataManager dataManager;

    public TabelaIbptImportService(DataManager dataManager) {
        this.dataManager = dataManager;
    }

    public record ImportResult(String uf, String versao, int removidos, int importados, int ignorados) {
    }

    public String ufDoNomeArquivo(String nomeArquivo) {
        Matcher m = nomeArquivo == null ? null : UF_NO_NOME.matcher(nomeArquivo);
        if (m == null || !m.find()) {
            throw new IllegalArgumentException("Não foi possível identificar a UF pelo nome do arquivo (\""
                    + nomeArquivo + "\") — use o arquivo com o nome original do IBPT, ex.: TabelaIBPTaxSP26.2.B.csv");
        }
        return m.group(1).toUpperCase(Locale.ROOT);
    }

    @Transactional
    public ImportResult importar(byte[] conteudo, String nomeArquivo) {
        String uf = ufDoNomeArquivo(nomeArquivo);

        List<TabelaIbpt> novas = new ArrayList<>();
        int ignorados = 0;
        String versao = null;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new ByteArrayInputStream(conteudo), CP1252))) {
            String cabecalho = reader.readLine();
            if (cabecalho == null || !cabecalho.toLowerCase(Locale.ROOT).startsWith("codigo;ex;tipo;descricao")) {
                throw new IllegalArgumentException("Arquivo não está no layout da Tabela IBPT (cabeçalho inesperado)");
            }
            String linha;
            while ((linha = reader.readLine()) != null) {
                if (linha.isBlank()) {
                    continue;
                }
                TabelaIbpt t = parsearLinha(linha, uf);
                if (t == null) {
                    ignorados++;
                    continue;
                }
                versao = t.getVersao();
                novas.add(t);
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Erro lendo o arquivo: " + e.getMessage(), e);
        }
        if (novas.isEmpty()) {
            throw new IllegalArgumentException("Nenhuma linha válida no arquivo");
        }

        List<TabelaIbpt> antigas = dataManager.load(TabelaIbpt.class)
                .query("select e from TabelaIbpt e where e.uf = :uf")
                .parameter("uf", uf)
                .list();
        for (int i = 0; i < antigas.size(); i += LOTE) {
            SaveContext ctx = new SaveContext().setDiscardSaved(true);
            antigas.subList(i, Math.min(i + LOTE, antigas.size())).forEach(ctx::removing);
            dataManager.save(ctx);
        }
        for (int i = 0; i < novas.size(); i += LOTE) {
            SaveContext ctx = new SaveContext().setDiscardSaved(true);
            novas.subList(i, Math.min(i + LOTE, novas.size())).forEach(ctx::saving);
            dataManager.save(ctx);
        }

        return new ImportResult(uf, versao, antigas.size(), novas.size(), ignorados);
    }

    // Colunas separadas por ";" — só a descrição vem entre aspas, e pode conter ";" dentro.
    private TabelaIbpt parsearLinha(String linha, String uf) {
        List<String> campos = dividir(linha);
        if (campos.size() < 13) {
            return null;
        }
        String codigo = campos.get(0).trim();
        if (codigo.isEmpty() || codigo.length() > 9) {
            return null;
        }
        Integer tipo;
        try {
            tipo = Integer.valueOf(campos.get(2).trim());
        } catch (NumberFormatException e) {
            return null;
        }
        TabelaIbpt t = dataManager.create(TabelaIbpt.class);
        t.setUf(uf);
        t.setCodigo(codigo);
        String ex = campos.get(1).trim();
        t.setEx(ex.isEmpty() ? null : ex);
        t.setTipo(tipo);
        String descricao = campos.get(3).trim();
        t.setDescricao(descricao.length() > 500 ? descricao.substring(0, 500) : descricao);
        t.setAliqNacionalFederal(decimal(campos.get(4)));
        t.setAliqImportadosFederal(decimal(campos.get(5)));
        t.setAliqEstadual(decimal(campos.get(6)));
        t.setAliqMunicipal(decimal(campos.get(7)));
        t.setVigenciaInicio(data(campos.get(8)));
        t.setVigenciaFim(data(campos.get(9)));
        t.setChave(limitar(campos.get(10), 10));
        t.setVersao(limitar(campos.get(11), 10));
        t.setFonte(limitar(campos.get(12), 60));
        return t;
    }

    private List<String> dividir(String linha) {
        List<String> campos = new ArrayList<>();
        StringBuilder atual = new StringBuilder();
        boolean entreAspas = false;
        for (int i = 0; i < linha.length(); i++) {
            char c = linha.charAt(i);
            if (c == '"') {
                if (entreAspas && i + 1 < linha.length() && linha.charAt(i + 1) == '"') {
                    atual.append('"');
                    i++;
                } else {
                    entreAspas = !entreAspas;
                }
            } else if (c == ';' && !entreAspas) {
                campos.add(atual.toString());
                atual.setLength(0);
            } else {
                atual.append(c);
            }
        }
        campos.add(atual.toString());
        return campos;
    }

    private BigDecimal decimal(String texto) {
        String t = texto.trim().replace(',', '.');
        if (t.isEmpty()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(t);
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    private LocalDate data(String texto) {
        String t = texto.trim();
        if (t.isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(t, DATA);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private String limitar(String texto, int max) {
        String t = texto.trim();
        if (t.isEmpty()) {
            return null;
        }
        return t.length() > max ? t.substring(0, max) : t;
    }
}
