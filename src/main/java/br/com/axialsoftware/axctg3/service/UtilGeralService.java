package br.com.axialsoftware.axctg3.service;

import br.com.axialsoftware.axctg3.entity.User;
import br.com.axialsoftware.axctg3.entity.cadastros.ConfigRel;
import br.com.axialsoftware.axctg3.entity.cadastros.Empresa;
import io.jmix.core.DataManager;
import io.jmix.core.security.CurrentAuthentication;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.*;

@Component
public class UtilGeralService {

    private final CurrentAuthentication currentAuthentication;
    private final DataManager dataManager;

    public UtilGeralService(CurrentAuthentication currentAuthentication, DataManager dataManager) {
        this.currentAuthentication = currentAuthentication;
        this.dataManager = dataManager;
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

    public String getLogoEmpresa() {
        return getEmpresa().getLogo();
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
}