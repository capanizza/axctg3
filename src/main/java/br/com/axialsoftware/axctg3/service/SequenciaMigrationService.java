package br.com.axialsoftware.axctg3.service;

import io.jmix.core.DataManager;
import io.jmix.core.entity.KeyValueEntity;
import io.jmix.data.Sequence;
import io.jmix.data.Sequences;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Migração pontual e idempotente pro esquema de sequences por-empresa introduzido em
 * 2026-08-24 (nome da sequence passou a incluir {@code codEmpresa} — ver
 * {@code LancamentoEventListener} e os demais listeners de numeração). Antes dessa
 * mudança todas as empresas competiam pela mesma sequence global (só uma por
 * ano/mês/tipo, não por empresa), então uma empresa podia "roubar" a numeração de outra
 * — foi exatamente esse cenário que expôs o bug: um lançamento novo salvo hoje pra uma
 * empresa cai em cima de um {@code numero} que ela mesma já usou, batendo no índice
 * único.
 * <p>
 * Esta classe lê o {@code max(numero)} já gravado por empresa (e, no caso do
 * {@link br.com.axialsoftware.axctg3.entity.contabil.Lancamento}, por ano/mês também) e
 * semeia a sequence nova (nome com {@code codEmpresa}) nesse valor via
 * {@code Sequences.setCurrentValue} — assim a próxima gravação continua de onde a
 * empresa já estava, em vez de reiniciar do zero e colidir com dado já existente.
 * Cada método é seguro de rodar de novo: sempre semeia pro max atual e
 * {@code setCurrentValue} não tem efeito colateral fora da própria sequence.
 * <p>
 * Não migra {@code ItemNotaSaida} — o valor de {@code item} é provisório, sobrescrito
 * por {@code NotaSaidaEventListener} logo depois do save da nota inteira (renumeração
 * 1, 2, 3...), então não tem uma identidade que precise ser preservada.
 */
@Service
public class SequenciaMigrationService {

    private static final Logger log = LoggerFactory.getLogger(SequenciaMigrationService.class);

    private final DataManager dataManager;
    private final Sequences sequences;

    public SequenciaMigrationService(DataManager dataManager, Sequences sequences) {
        this.dataManager = dataManager;
        this.sequences = sequences;
    }

    /** Roda a migração de todos os numeradores conhecidos, nessa ordem. */
    public void migrarTudo() {
        migrarLancamento();
        migrarTituloPagar();
        migrarDiversoPagar();
        migrarMovimentoBanco();
        migrarNotaSaida();
    }

    /** {@code lancamento_seq_<codEmpresa>_<anoMes>} — a única sequence por-período além de por-empresa. */
    public void migrarLancamento() {
        List<KeyValueEntity> grupos = dataManager.loadValues(
                        "select e.codEmpresa, e.ano, e.mes, max(e.numero) from Lancamento e " +
                                "group by e.codEmpresa, e.ano, e.mes")
                .properties("codEmpresa", "ano", "mes", "maxNumero")
                .list();
        for (KeyValueEntity grupo : grupos) {
            int codEmpresa = intValue(grupo.getValue("codEmpresa"));
            int ano = intValue(grupo.getValue("ano"));
            int mes = intValue(grupo.getValue("mes"));
            int maxNumero = intValue(grupo.getValue("maxNumero"));
            String nome = "lancamento_seq_" + codEmpresa + "_" + String.format("%4d%02d", ano, mes);
            sequences.setCurrentValue(Sequence.withName(nome), maxNumero);
            log.info("migrarLancamento: {} -> {}", nome, maxNumero);
        }
    }

    public void migrarTituloPagar() {
        migrarPorEmpresa("TituloPagar", "numero", "titulo_pagar_seq_");
    }

    public void migrarDiversoPagar() {
        migrarPorEmpresa("DiversoPagar", "numero", "diverso_pagar_seq_");
    }

    public void migrarMovimentoBanco() {
        migrarPorEmpresa("MovimentoBanco", "lancamento", "movimento_banco_seq_");
    }

    public void migrarNotaSaida() {
        migrarPorEmpresa("NotaSaida", "numero", "nota_saida_seq_");
    }

    private void migrarPorEmpresa(String entityName, String campoNumero, String prefixoSequence) {
        List<KeyValueEntity> grupos = dataManager.loadValues(
                        "select e.codEmpresa, max(e." + campoNumero + ") from " + entityName + " e " +
                                "group by e.codEmpresa")
                .properties("codEmpresa", "maxNumero")
                .list();
        for (KeyValueEntity grupo : grupos) {
            int codEmpresa = intValue(grupo.getValue("codEmpresa"));
            int maxNumero = intValue(grupo.getValue("maxNumero"));
            String nome = prefixoSequence + codEmpresa;
            sequences.setCurrentValue(Sequence.withName(nome), maxNumero);
            log.info("migrar{}: {} -> {}", entityName, nome, maxNumero);
        }
    }

    /** max(...) sobre coluna Integer pode voltar Long/BigInteger dependendo do provider JPA. */
    private int intValue(Object value) {
        return ((Number) value).intValue();
    }
}
