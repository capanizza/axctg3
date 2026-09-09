package br.com.axialsoftware.axctg3.service.fiscal;

import br.com.axialsoftware.axctg3.entity.cadastros.Empresa;
import br.com.axialsoftware.axctg3.entity.fiscal.NfeInutilizacao;
import br.com.axialsoftware.axctg3.service.RelatorioService;
import br.com.axialsoftware.axctg3.service.UtilGeralService;
import io.jmix.core.DataManager;
import io.jmix.core.Messages;
import net.sf.jasperreports.engine.JREmptyDataSource;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.UUID;

/**
 * Emissão do comprovante de um {@link NfeInutilizacao} já persistido (Pedido de Inutilização
 * de Numeração já enviado pra SEFAZ por {@link NfeInutilizacaoService}, homologado ou não).
 * Mesmo pipeline Jasper de {@link NfeDanfeService}, mas separado dele porque é um documento
 * bem mais simples — não existe leiaute oficial da SEFAZ pra esse comprovante (diferente do
 * DANFE, regido pelo MOC), então o template aqui é uma folha de dados única, sem tabela de
 * itens, montada com {@link JREmptyDataSource} (um único "registro" vazio — todo o conteúdo
 * vem de parâmetros, igual ao padrão já usado em {@code ContaContabil2.jrxml}).
 */
@Service
public class NfeInutilizacaoComprovanteService {

    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    private final DataManager dataManager;
    private final UtilGeralService utilGeralService;
    private final RelatorioService relatorioService;
    private final Messages messages;

    public NfeInutilizacaoComprovanteService(DataManager dataManager, UtilGeralService utilGeralService,
                                              RelatorioService relatorioService, Messages messages) {
        this.dataManager = dataManager;
        this.utilGeralService = utilGeralService;
        this.relatorioService = relatorioService;
        this.messages = messages;
    }

    /** Carrega o {@link NfeInutilizacao} pelo id e emite o comprovante. Usado por {@code NfeInutilizacaoListView}. */
    public void imprimir(UUID id) {
        NfeInutilizacao inut = dataManager.load(NfeInutilizacao.class).id(id).one();
        emitir(inut);
    }

    private void emitir(NfeInutilizacao inut) {
        HashMap<String, Object> parametros = montarParametros(inut);
        String nomeArquivo = "Inutilizacao_" + inut.getAno() + "_" + inut.getSerie() + "_"
                + inut.getNumeroInicial() + "_" + inut.getNumeroFinal() + ".pdf";
        relatorioService.emitirRelatorio("ComprovanteInutilizacao.jasper", new JREmptyDataSource(), parametros, nomeArquivo);
    }

    private HashMap<String, Object> montarParametros(NfeInutilizacao inut) {
        Empresa empresa = utilGeralService.getEmpresa();
        var parametros = new HashMap<String, Object>();

        parametros.put("LOGO", utilGeralService.getLogoEmpresa());
        parametros.put("NOME_EMPRESA", nvl(empresa.getNome()));
        parametros.put("CNPJ_EMPRESA", formatarCnpj(empresa.getCnpj()));
        parametros.put("AMBIENTE_DESC", empresa.getAmbienteNfe() == null ? "" : messages.getMessage(empresa.getAmbienteNfe()));

        parametros.put("MODELO", inut.getModelo());
        parametros.put("SERIE", inut.getSerie());
        parametros.put("ANO", inut.getAno());
        parametros.put("NUMERO_INICIAL", inut.getNumeroInicial());
        parametros.put("NUMERO_FINAL", inut.getNumeroFinal());
        parametros.put("JUSTIFICATIVA", nvl(inut.getJustificativa()));

        parametros.put("PROTOCOLO", nvl(inut.getRetNProt()));
        parametros.put("DH_RECEBIMENTO", formatarDataHora(inut.getDhRecbto()));
        parametros.put("STATUS_DESC", statusDescricao(inut));

        parametros.put("DATA_EMISSAO", formatarDataHora(OffsetDateTime.now()));

        return parametros;
    }

    /** "102 - Inutilização de número homologada" — sem cStat, mostra só o motivo (pedido nunca chegou a ser respondido). */
    private String statusDescricao(NfeInutilizacao inut) {
        if (inut.getRetCStat() == null) {
            return nvl(inut.getRetXMotivo());
        }
        return inut.getRetCStat() + " - " + nvl(inut.getRetXMotivo());
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
     * {@link NfeDanfeService#nvl(String)}).
     */
    private static String nvl(String valor) {
        return valor == null ? "" : valor;
    }
}
