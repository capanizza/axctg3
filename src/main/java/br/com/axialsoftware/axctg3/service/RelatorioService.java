package br.com.axialsoftware.axctg3.service;

import io.jmix.flowui.download.DownloadFormat;
import io.jmix.flowui.download.Downloader;
import net.sf.jasperreports.engine.JRDataSource;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.HashMap;

@Service
public class RelatorioService {

    @Autowired
    private Downloader downloader;

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
            // colocar log do erro
            log.info(e.getMessage());
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
            log.info(e.getMessage());
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