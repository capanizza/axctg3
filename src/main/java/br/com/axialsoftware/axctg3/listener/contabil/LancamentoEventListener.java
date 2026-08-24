package br.com.axialsoftware.axctg3.listener.contabil;

import br.com.axialsoftware.axctg3.entity.contabil.Lancamento;
import br.com.axialsoftware.axctg3.service.UtilGeralService;
import br.com.axialsoftware.axctg3.service.contabil.LancamentoService;
import io.jmix.core.DataManager;
import io.jmix.core.Id;
import io.jmix.core.event.EntityChangedEvent;
import io.jmix.core.event.EntitySavingEvent;
import io.jmix.data.Sequence;
import io.jmix.data.Sequences;
import io.jmix.flowui.Dialogs;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class LancamentoEventListener {

    private final UtilGeralService utilGeralService;
    private final LancamentoService lancamentoService;
    private final Sequences sequences;
    private final DataManager dataManager;
    private final Dialogs dialogs;

    public LancamentoEventListener(UtilGeralService utilGeralService, LancamentoService lancamentoService, Sequences sequences, DataManager dataManager, Dialogs dialogs) {
        this.utilGeralService = utilGeralService;
        this.lancamentoService = lancamentoService;
        this.sequences = sequences;
        this.dataManager = dataManager;
        this.dialogs = dialogs;
    }

    @EventListener
    public void onLancamentoSaving(final EntitySavingEvent<Lancamento> event) {
        if (event.isNewEntity()) {
            Lancamento lancamento = event.getEntity();
            Integer codEmpresa = utilGeralService.getCodEmpresa();
            int anoContabil = utilGeralService.getAnoContabil();
            int mesContabil = utilGeralService.getMesContabil();
            lancamento.setCodEmpresa(codEmpresa);
            lancamento.setAno(anoContabil);
            lancamento.setMes(mesContabil);
            LocalDate dataLancamento = lancamentoService.calcularDataLancamento(lancamento, anoContabil, mesContabil);
            lancamento.setDia(dataLancamento.getDayOfMonth());
            lancamento.setDataLancamento(dataLancamento);
            if (lancamento.getNumero() == null) {
                String st = "lancamento_seq_" + String.format("%4d%02d", anoContabil, mesContabil);
                long numero = sequences.createNextValue(Sequence.withName(st));
                lancamento.setNumero((int) numero);
            }
        }
    }

    @EventListener
    public void onLancamentoChangedBeforeCommit(final EntityChangedEvent<Lancamento> event) {
        if (event.getType() != EntityChangedEvent.Type.DELETED) {
            Id<Lancamento> lancamentoId = event.getEntityId();
            Lancamento lancamento = dataManager.load(lancamentoId).one();

            if (event.getType() == EntityChangedEvent.Type.CREATED) {
                lancamentoService.atualizarSaldos(lancamento);
            }

            if (event.getType() == EntityChangedEvent.Type.UPDATED) {
                lancamentoService.excluirSaldosAnteriores(event);

                lancamentoService.atualizarSaldos(lancamento);

                Integer anoContabil = utilGeralService.getAnoContabil();
                Integer mesContabil = utilGeralService.getMesContabil();
                LocalDate dtLanc = lancamentoService.calcularDataLancamento(lancamento, anoContabil, mesContabil);
                lancamento.setDia(dtLanc.getDayOfMonth());
                lancamento.setDataLancamento(dtLanc);
                dataManager.saveWithoutReload(lancamento);
            }
        } else {
            try {
                lancamentoService.excluirSaldosAnteriores(event);
            } catch (Exception e) {
                dialogs.createMessageDialog()
                        .withHeader("Erro")
                        .withText("Não alterou saldos")
                        .open();
            }
        }
    }
}