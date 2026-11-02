package br.com.axialsoftware.axctg3.service.financeiro;

import br.com.axialsoftware.axctg3.entity.financeiro.Banco;
import br.com.axialsoftware.axctg3.entity.financeiro.HistoricoFinanceiro;
import br.com.axialsoftware.axctg3.entity.financeiro.ItemReceber;
import br.com.axialsoftware.axctg3.entity.financeiro.RetornoBanco;
import br.com.axialsoftware.axctg3.entity.financeiro.TituloReceber;
import br.com.axialsoftware.axctg3.service.UtilGeralService;
import io.jmix.core.DataManager;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * Orquestração bank-agnostic da leitura de retorno bancário: resolve a implementação certa
 * pelo {@link Banco#getCodGeral()}, aplica a regra de negócio por ocorrência (mesma tabela
 * usada pela maioria dos bancos CNAB — confirmação de entrada e liquidação são os únicos
 * casos com efeito hoje; ver plano da feature pros códigos fora de escopo) e audita em
 * {@link RetornoBanco}. A leitura do arquivo em si (campo a campo) fica em
 * {@link BancoCobrancaHandler}.
 *
 * <p>A criação da baixa segue o mesmo padrão de
 * {@code BaixaTituloReceberListView.onTituloRecebersDataGridBaixaAutomaticaAction}: só cria
 * o {@link ItemReceber}, não contabiliza sozinho — contabilização continua sendo o passo
 * manual "Lançamentos" já existente.
 */
@Service
public class RetornoBancoService {

    // Tabela de Ocorrências (seção 7.2 do manual Sicredi — convenção comum à maioria dos
    // retornos CNAB): 02 = entrada confirmada; liquidação normal/em cartório/após baixa;
    // rejeições de entrada/baixa/alteração/instrução. 09/10 (baixado automaticamente/conforme
    // instrução) NÃO são pagamento: o título sai da cobrança sem valor pago — retorno real da
    // Rádio (set/2026) trouxe quatro 09, todos com valor pago 0,00. Antes estavam aqui e
    // criavam uma baixa de R$ 0,00.
    private static final String OCORRENCIA_ENTRADA_CONFIRMADA = "02";
    private static final Set<String> OCORRENCIAS_LIQUIDACAO = Set.of("06", "15", "17");
    private static final Set<String> OCORRENCIAS_REJEICAO = Set.of("03", "27", "30", "32");

    private final DataManager dataManager;
    private final UtilGeralService utilGeralService;
    private final List<BancoCobrancaHandler> handlers;

    public RetornoBancoService(DataManager dataManager, UtilGeralService utilGeralService,
                                List<BancoCobrancaHandler> handlers) {
        this.dataManager = dataManager;
        this.utilGeralService = utilGeralService;
        this.handlers = handlers;
    }

    /**
     * Resolvido a cada chamada — ver o mesmo comentário em {@code RemessaBancoService},
     * pro dublê em teste funcionar mesmo estubado só no {@code @BeforeEach}.
     */
    private BancoCobrancaHandler resolverHandler(Integer codGeral) {
        return handlers.stream()
                .filter(h -> codGeral != null && codGeral.equals(h.getCodGeralSuportado()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Banco código " + codGeral + " ainda não suportado para remessa/retorno"));
    }

    public RetornoBanco processarRetorno(Banco banco, byte[] arquivo, String nomeArquivo) {
        BancoCobrancaHandler handler = resolverHandler(banco.getCodGeral());

        Integer codEmpresa = utilGeralService.getCodEmpresa();
        HistoricoFinanceiro historicoBaixa = utilGeralService.getEmpresa().getHistFinBaixaReceber();

        int confirmados = 0;
        int baixados = 0;
        int rejeitados = 0;
        int naoEncontrados = 0;

        for (RetornoDetalheLido detalhe : handler.lerRetorno(arquivo)) {
            TituloReceber tituloReceber = buscarTituloReceber(detalhe.seuNumero(), codEmpresa);
            if (tituloReceber == null) {
                naoEncontrados++;
                continue;
            }

            if (OCORRENCIA_ENTRADA_CONFIRMADA.equals(detalhe.ocorrencia())) {
                confirmados++;
                if ((tituloReceber.getNumBanco() == null || tituloReceber.getNumBanco().isBlank())
                        && detalhe.nossoNumero() != null && !detalhe.nossoNumero().isBlank()) {
                    tituloReceber.setNumBanco(detalhe.nossoNumero());
                    dataManager.save(tituloReceber);
                }
            } else if (OCORRENCIAS_LIQUIDACAO.contains(detalhe.ocorrencia())) {
                if (Boolean.TRUE.equals(tituloReceber.getAberto())
                        && detalhe.valorPago() != null && detalhe.valorPago().signum() > 0) {
                    criarBaixa(tituloReceber, detalhe, historicoBaixa);
                    baixados++;
                }
                // já fechado — reprocessamento do mesmo retorno não duplica a baixa.
            } else if (OCORRENCIAS_REJEICAO.contains(detalhe.ocorrencia())) {
                rejeitados++;
            }
            // qualquer outra ocorrência (protesto, negativação, abatimento, alteração de
            // vencimento etc.) fica fora de escopo — não conta em nenhum contador.
        }

        RetornoBanco retornoBanco = dataManager.create(RetornoBanco.class);
        retornoBanco.setCodEmpresa(codEmpresa);
        retornoBanco.setBanco(banco);
        retornoBanco.setDataProcessamento(LocalDate.now());
        retornoBanco.setNomeArquivo(nomeArquivo);
        retornoBanco.setQuantidadeConfirmados(confirmados);
        retornoBanco.setQuantidadeBaixados(baixados);
        retornoBanco.setQuantidadeRejeitados(rejeitados);
        retornoBanco.setQuantidadeNaoEncontrados(naoEncontrados);
        return dataManager.save(retornoBanco);
    }

    /**
     * Compara ignorando zeros à esquerda: o "seu número" volta do banco como foi registrado
     * ("351" pelo app do Sicredi, "000247" pela remessa do legado), e {@code TituloReceber.numero}
     * importado do legado tem 6 dígitos com zeros ("000351") — igualdade exata não achava
     * nenhum dos 31 títulos do retorno real da Rádio de set/2026. Mais de um título com o mesmo
     * número sem os zeros (ex.: "351" e "000351") é ambíguo: vale só a igualdade exata, senão
     * conta como não encontrado em vez de baixar o título errado.
     */
    private TituloReceber buscarTituloReceber(String numero, Integer codEmpresa) {
        String semZeros = semZerosAEsquerda(numero);
        if (semZeros.isEmpty()) {
            return null;
        }
        List<TituloReceber> candidatos = dataManager.load(TituloReceber.class)
                .query("select e from TituloReceber e where e.numero like :sufixo and e.codEmpresa = :codEmpresa")
                .parameter("sufixo", "%" + semZeros)
                .parameter("codEmpresa", codEmpresa)
                .list()
                .stream()
                .filter(t -> semZeros.equals(semZerosAEsquerda(t.getNumero())))
                .toList();
        if (candidatos.size() == 1) {
            return candidatos.get(0);
        }
        return candidatos.stream()
                .filter(t -> numero.trim().equals(t.getNumero()))
                .findFirst()
                .orElse(null);
    }

    private static String semZerosAEsquerda(String numero) {
        return numero == null ? "" : numero.trim().replaceFirst("^0+", "");
    }

    private void criarBaixa(TituloReceber tituloReceber, RetornoDetalheLido detalhe, HistoricoFinanceiro historicoBaixa) {
        Integer maxItem = dataManager.loadValue(
                        "select max(e.item) from ItemReceber e where e.tituloReceber = :tituloReceber", Integer.class)
                .parameter("tituloReceber", tituloReceber)
                .one();

        ItemReceber itemReceber = dataManager.create(ItemReceber.class);
        itemReceber.setTituloReceber(tituloReceber);
        itemReceber.setItem((maxItem == null ? 1 : maxItem) + 1);
        itemReceber.setData(detalhe.dataOcorrencia() != null ? detalhe.dataOcorrencia() : LocalDate.now());
        itemReceber.setHistoricoFinanceiro(historicoBaixa);
        itemReceber.setValor(detalhe.valorPago());
        itemReceber.setJuros(detalhe.juros());
        itemReceber.setDesconto(detalhe.desconto());
        itemReceber.setContabilizado(false);
        dataManager.save(itemReceber);
    }
}
