package br.com.axialsoftware.axctg3.service;

import br.com.axialsoftware.axctg3.entity.User;
import br.com.axialsoftware.axctg3.entity.cadastros.ConfigRel;
import br.com.axialsoftware.axctg3.entity.cadastros.Empresa;
import io.jmix.core.DataManager;
import io.jmix.core.FileRef;
import io.jmix.core.FileStorage;
import io.jmix.core.security.CurrentAuthentication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Component
public class UtilGeralService {

    private static final Logger log = LoggerFactory.getLogger(UtilGeralService.class);

    private final CurrentAuthentication currentAuthentication;
    private final DataManager dataManager;
    private final FileStorage fileStorage;

    public UtilGeralService(CurrentAuthentication currentAuthentication, DataManager dataManager, FileStorage fileStorage) {
        this.currentAuthentication = currentAuthentication;
        this.dataManager = dataManager;
        this.fileStorage = fileStorage;
    }

    public Integer getCodEmpresa() {
        User user = (User) currentAuthentication.getUser();
        return user.getCodEmpresa();
    }
    public String getUserName() {
        User user = (User) currentAuthentication.getUser();
        return user.getUsername();
    }

    public String getUserEmail() {
        User user = (User) currentAuthentication.getUser();
        return user.getEmail();
    }

    public Integer getAnoContabil() {
        User user = (User) currentAuthentication.getUser();
        return user.getAnoContabil();
    }

    public Integer getMesContabil() {
        User user = (User) currentAuthentication.getUser();
        return user.getMesContabil();
    }

    public Empresa getEmpresa() {
        Integer codEmpresa = getCodEmpresa();
        return dataManager.load(Empresa.class)
                .query("select e from Empresa e " +
                        "where e.codigo = :codEmpresa")
                .parameter("codEmpresa", codEmpresa)
                .one();
    }

    public String getMascContabil() {
        return getEmpresa().getMascContabil();
    }

    public String getApelidoEmpresa() {
        Integer codEmpresa = getCodEmpresa();
        if (codEmpresa == null) {
            return "";
        }
        return dataManager.load(Empresa.class)
                .query("select e from Empresa e " +
                        "where e.codigo = :codEmpresa")
                .parameter("codEmpresa", codEmpresa)
                .optional()
                .map(Empresa::getApelido)
                .orElse("");
    }

    public String getNomeEmpresa() {
        return getEmpresa().getNome();
    }

    /**
     * Caminho no disco pro logo da empresa atual, pronto pra virar o parâmetro LOGO dos
     * relatórios Jasper (imageExpression $P{LOGO} espera um caminho String, não mudou nos
     * .jrxml). Empresa.logo é FileRef (jmix-localfs); aqui materializamos o conteúdo num
     * arquivo temporário estável por empresa — reescrito a cada chamada (o logo é pequeno
     * e troca raramente), sem crescimento de disco porque o nome é sempre o mesmo por
     * codEmpresa. Retorna null se a empresa não tem logo configurado ou se a leitura falhar.
     */
    public String getLogoEmpresa() {
        Empresa empresa = getEmpresa();
        FileRef logoFileRef = empresa.getLogo();
        if (logoFileRef == null) {
            return null;
        }
        try {
            String extensao = "";
            int pontoIdx = logoFileRef.getFileName().lastIndexOf('.');
            if (pontoIdx >= 0) {
                extensao = logoFileRef.getFileName().substring(pontoIdx);
            }
            Path destino = Paths.get(System.getProperty("java.io.tmpdir"),
                    "axctg3-logo-" + empresa.getCodigo() + extensao);
            try (InputStream is = fileStorage.openStream(logoFileRef)) {
                Files.copy(is, destino, StandardCopyOption.REPLACE_EXISTING);
            }
            return destino.toAbsolutePath().toString();
        } catch (IOException e) {
            log.info("Falha ao materializar logo da empresa {}: {}", empresa.getCodigo(), e.getMessage());
            return null;
        }
    }

    public ConfigRel prepararConfigRel() {
        User user = (User) currentAuthentication.getUser();

        // tenta carregar, pode voltar vazio
        Optional<ConfigRel> loaded = dataManager.load(ConfigRel.class)
                .query("select c from ConfigRel c where c.user = :user")
                .parameter("user", user)
                .optional();   // não lança exceção se não achar

        if (loaded.isPresent()) {
            return loaded.get();
        }

        // não encontrou: cria um novo
        ConfigRel configRel = dataManager.create(ConfigRel.class);
        configRel.setUser(user);
        // normalmente não é preciso setar o ID manualmente, o JPA gera
        dataManager.saveWithoutReload(configRel);

        return configRel;
    }

    public String valorComLetra(BigDecimal valor) {
        DecimalFormat df = new DecimalFormat("###,###,##0.00");
        if (valor == null) { return ""; }
        if (valor.compareTo(BigDecimal.ZERO) == 0) {
            return "";
        }
        String st;
        if (valor.compareTo(BigDecimal.ZERO) > 0) {
            st = df.format(valor) + " D";
        } else {
            st = df.format(valor.negate()) + " C";
        }
        return st;
    }

    public String semNull(String st) {
        return st == null ? "" : st.trim();
    }

    public java.sql.Date localDateToSqlDate(LocalDate dt) {
        if (dt == null) {
            return null;
        }
        return java.sql.Date.valueOf(dt);
    }

    public String extensoPeriodoContabil() {
        int codEmpresa = getCodEmpresa();
        int anoContabil = getAnoContabil();
        int mesContabil = getMesContabil();
        Calendar cal = Calendar.getInstance();
        cal.set(anoContabil, mesContabil - 1, 1);
        Date dt = cal.getTime();
        SimpleDateFormat df = new SimpleDateFormat("MMMM/yyyy");
        return df.format(dt);
    }

    public Map<String, LocalDate> prepararDatas(Integer anoContabil, Integer mesContabil) {
        LocalDate dt = LocalDate.of(anoContabil, mesContabil, 1);
        Map<String, LocalDate> mapa = new HashMap<>();
        mapa.put("dataInicial", dt);
        dt = dt.with(java.time.temporal.TemporalAdjusters.lastDayOfMonth());
        mapa.put("dataFinal", dt);
        return mapa;
    }

    /**
     * Sufixo " rotulo: dd/MM/yyyy a dd/MM/yyyy" usado no getPageTitle() das listagens
     * financeiras (emissão/vencimento). Datas nulas caem em hoje — mesma tolerância do
     * onBeforeShow dessas telas, que lê o mesmo par de campos do ConfigRel (nullable).
     */
    public String formatIntervaloTitulo(String rotulo, LocalDate dataInicial, LocalDate dataFinal) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        LocalDate inicial = dataInicial == null ? LocalDate.now() : dataInicial;
        LocalDate fim = dataFinal == null ? LocalDate.now() : dataFinal;
        return " " + rotulo + ": " + inicial.format(formatter) + " a " + fim.format(formatter);
    }
}