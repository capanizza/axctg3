package br.com.axialsoftware.axctg3.service.fiscal;

import br.com.axialsoftware.axctg3.entity.cadastros.Empresa;
import br.com.axialsoftware.axctg3.entity.fiscal.Nfe;
import br.com.axialsoftware.axctg3.entity.fiscal.NfeCartaCorrecao;
import br.com.axialsoftware.axctg3.service.RelatorioService;
import br.com.axialsoftware.axctg3.service.UtilGeralService;
import io.jmix.core.DataManager;
import io.jmix.core.FetchPlan;
import io.jmix.core.Messages;
import net.sf.jasperreports.engine.JREmptyDataSource;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.UUID;

/**
 * Emissão do comprovante de uma {@link NfeCartaCorrecao} já registrada (evento 110110, ver
 * {@link NfeCartaCorrecaoService}). Mesmo pipeline Jasper de {@link NfeInutilizacaoComprovanteService}
 * — sem leiaute oficial da SEFAZ pra esse comprovante (diferente do DANFE), folha única via
 * {@link JREmptyDataSource}, todo o conteúdo por parâmetros.
 */
@Service
public class NfeCartaCorrecaoComprovanteService {

    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    private final DataManager dataManager;
    private final UtilGeralService utilGeralService;
    private final RelatorioService relatorioService;
    private final Messages messages;

    public NfeCartaCorrecaoComprovanteService(DataManager dataManager, UtilGeralService utilGeralService,
                                               RelatorioService relatorioService, Messages messages) {
        this.dataManager = dataManager;
        this.utilGeralService = utilGeralService;
        this.relatorioService = relatorioService;
        this.messages = messages;
    }

    /** Carrega a {@link NfeCartaCorrecao} pelo id e emite o comprovante. Usado por {@code NfeDetailView}. */
    public void imprimir(UUID id) {
        NfeCartaCorrecao cartaCorrecao = dataManager.load(NfeCartaCorrecao.class).id(id)
                .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE).add("nfe", FetchPlan.BASE))
                .one();
        emitir(cartaCorrecao);
    }

    private void emitir(NfeCartaCorrecao cartaCorrecao) {
        HashMap<String, Object> parametros = montarParametros(cartaCorrecao);
        Nfe nfe = cartaCorrecao.getNfe();
        String nomeArquivo = "CCe_" + nfe.getNumeroNf() + "_" + nfe.getSerie() + "_"
                + cartaCorrecao.getNumeroSequencial() + ".pdf";
        relatorioService.emitirRelatorio("ComprovanteCartaCorrecao.jasper", new JREmptyDataSource(), parametros, nomeArquivo);
    }

    private HashMap<String, Object> montarParametros(NfeCartaCorrecao cartaCorrecao) {
        Nfe nfe = cartaCorrecao.getNfe();
        Empresa empresa = utilGeralService.getEmpresa();
        var parametros = new HashMap<String, Object>();

        parametros.put("LOGO", utilGeralService.getLogoEmpresa());
        parametros.put("NOME_EMPRESA", nvl(empresa.getNome()));
        parametros.put("CNPJ_EMPRESA", formatarCnpj(empresa.getCnpj()));
        parametros.put("AMBIENTE_DESC", empresa.getAmbienteNfe() == null ? "" : messages.getMessage(empresa.getAmbienteNfe()));

        parametros.put("CHAVE", nvl(nfe.getChave()));
        parametros.put("NUMERO_NF", nfe.getNumeroNf());
        parametros.put("SERIE", nfe.getSerie());
        parametros.put("DEST_NOME", nvl(nfe.getDestXNome()));

        parametros.put("NUM_SEQ_EVENTO", cartaCorrecao.getNumeroSequencial());
        parametros.put("TEXTO_CORRECAO", nvl(cartaCorrecao.getTextoCorrecao()));
        parametros.put("PROTOCOLO", nvl(cartaCorrecao.getCceNProt()));
        parametros.put("DH_REGISTRO", formatarDataHora(cartaCorrecao.getCceDhRegEvento()));
        parametros.put("STATUS_DESC", statusDescricao(cartaCorrecao));
        parametros.put("CONDICOES_USO", NfeCartaCorrecaoService.X_COND_USO);

        parametros.put("DATA_EMISSAO", formatarDataHora(OffsetDateTime.now()));

        return parametros;
    }

    /** "135 - Evento registrado e vinculado a NF-e" — sem cStat, mostra só o motivo. */
    private String statusDescricao(NfeCartaCorrecao cartaCorrecao) {
        if (cartaCorrecao.getCceCStat() == null) {
            return nvl(cartaCorrecao.getCceXMotivo());
        }
        return cartaCorrecao.getCceCStat() + " - " + nvl(cartaCorrecao.getCceXMotivo());
    }

    private static String formatarDataHora(OffsetDateTime data) {
        return data == null ? "" : data.format(DATA_HORA);
    }

    private static String formatarCnpj(String cnpj) {
        if (cnpj == null || cnpj.length() != 14) {
            return nvl(cnpj);
        }
        return cnpj.substring(0, 2) + "." + cnpj.substring(2, 5) + "." + cnpj.substring(5, 8)
                + "/" + cnpj.substring(8, 12) + "-" + cnpj.substring(12, 14);
    }

    /**
     * Nunca deixa um parâmetro de texto virar {@code null} — no {@code .jrxml}, uma expressão
     * como {@code "Protocolo: " + $P{X}} imprime a string literal "null" quando {@code $P{X}}
     * é nulo (concatenação de String em Java), em vez de ficar em branco (mesmo cuidado de
     * {@link NfeInutilizacaoComprovanteService#nvl(String)}).
     */
    private static String nvl(String valor) {
        return valor == null ? "" : valor;
    }
}
