package br.com.axialsoftware.axctg3.service;

import com.vaadin.flow.component.UI;
import io.jmix.flowui.Dialogs;
import io.jmix.flowui.download.DownloadFormat;
import io.jmix.flowui.download.Downloader;
import net.sf.jasperreports.engine.JRDataSource;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.export.JRPdfExporter;
import net.sf.jasperreports.export.SimpleExporterInput;
import net.sf.jasperreports.export.SimpleOutputStreamExporterOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.HashMap;
import java.util.List;

@Service
public class RelatorioService {

    @Autowired
    private Downloader downloader;

    @Autowired
    private Dialogs dialogs;

    private static final Logger log = LoggerFactory.getLogger(RelatorioService.class);

    public void emitirRelatorio(String nomeRelatorio,
                                JRDataSource dataSource,
                                HashMap<String, Object> parametros,
                                String nomeSaida) {
        try (InputStream is = abrirTemplate(nomeRelatorio)) {
            JasperPrint jasperPrint = JasperFillManager.fillReport(
                    is,
                    parametros,
                    dataSource);

            byte[] arq = JasperExportManager.exportReportToPdf(jasperPrint);
            downloader.download(arq, nomeSaida, DownloadFormat.PDF);
        } catch (Exception e) {
            log.error("Falha ao emitir relatório {}", nomeRelatorio, e);
            // Sem UI ativa (chamada de teste/background), não dá pra abrir diálogo — só loga.
            if (UI.getCurrent() != null) {
                dialogs.createMessageDialog()
                        .withHeader("Erro ao gerar relatório")
                        .withText("Não foi possível gerar o relatório. Consulte o log da aplicação.")
                        .open();
            }
        }
    }

    /**
     * Igual a {@link #emitirRelatorio}, mas devolve os bytes do PDF em vez de baixar pro
     * navegador — pra quem ainda vai fazer algo com o resultado (ex.: empacotar num zip).
     * Diferente de {@code emitirRelatorio}, não engole a exceção: quem chama precisa saber
     * se a geração falhou.
     */
    public byte[] gerarRelatorioPdf(String nomeRelatorio, JRDataSource dataSource, HashMap<String, Object> parametros) {
        return exportarPdf(List.of(preencherRelatorio(nomeRelatorio, dataSource, parametros)));
    }

    /**
     * Só o preenchimento (sem exportar) — pra juntar vários relatórios, inclusive de
     * templates diferentes, num PDF só via {@link #exportarPdf(List)} (ex.: boletos de
     * bancos diferentes numa mesma seleção). Não engole a exceção.
     */
    public JasperPrint preencherRelatorio(String nomeRelatorio, JRDataSource dataSource, HashMap<String, Object> parametros) {
        try (InputStream is = abrirTemplate(nomeRelatorio)) {
            return JasperFillManager.fillReport(is, parametros, dataSource);
        } catch (Exception e) {
            throw new RuntimeException("Falha ao gerar relatório " + nomeRelatorio + ": " + e.getMessage(), e);
        }
    }

    /** Exporta os relatórios preenchidos, na ordem da lista, como um único PDF. */
    public byte[] exportarPdf(List<JasperPrint> relatorios) {
        try {
            JRPdfExporter exporter = new JRPdfExporter();
            exporter.setExporterInput(SimpleExporterInput.getInstance(relatorios));
            ByteArrayOutputStream saida = new ByteArrayOutputStream();
            exporter.setExporterOutput(new SimpleOutputStreamExporterOutput(saida));
            exporter.exportReport();
            return saida.toByteArray();
        } catch (JRException e) {
            throw new RuntimeException("Falha ao exportar PDF: " + e.getMessage(), e);
        }
    }

    public void emitirRelatorio2(String nomeRelatorio,
                                 HashMap<String, Object> parametros,
                                 String nomeSaida) {
        String conexao = "jdbc:postgresql://localhost:5432/axialdb";
        try (InputStream is = abrirTemplate(nomeRelatorio)) {
            Connection connection = DriverManager.getConnection(conexao, "postgres", "root");
            JasperPrint jasperPrint = JasperFillManager.fillReport(
                    is,
                    parametros,
                    connection);

            byte[] arq = JasperExportManager.exportReportToPdf(jasperPrint);
            downloader.download(arq, nomeSaida, DownloadFormat.PDF);
        } catch (Exception e) {
            log.error("Falha ao emitir relatório {}", nomeRelatorio, e);
            // Sem UI ativa (chamada de teste/background), não dá pra abrir diálogo — só loga.
            if (UI.getCurrent() != null) {
                dialogs.createMessageDialog()
                        .withHeader("Erro ao gerar relatório")
                        .withText("Não foi possível gerar o relatório. Consulte o log da aplicação.")
                        .open();
            }
        }
    }

    /**
     * Templates .jasper são empacotados dentro do jar (ver processResources em
     * build.gradle, que copia relatorios/*.jasper do workspace do Jaspersoft Studio
     * pra raiz do classpath) — não são mais lidos do disco em tempo de execução.
     */
    private InputStream abrirTemplate(String nomeRelatorio) {
        InputStream is = getClass().getResourceAsStream("/relatorios/" + nomeRelatorio);
        if (is == null) {
            throw new IllegalStateException("Template de relatório não encontrado no classpath: relatorios/" + nomeRelatorio);
        }
        return is;
    }

}