package br.com.axialsoftware.axctg3.service.importacao;

import br.com.axialsoftware.axctg3.entity.enums.TipoLote;
import io.jmix.core.DataManager;
import io.jmix.data.Sequences;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;

/**
 * Importa um {@link br.com.axialsoftware.axctg3.entity.importacao.ImpLote} de diversos a pagar do
 * legado (DIVERSOS, com todos os movimentos de MOVDIVERSOS) — ver {@link ImportacaoContasPagarBase}.
 */
@Service
public class ImportacaoDiversosPagarService extends ImportacaoContasPagarBase {

    public ImportacaoDiversosPagarService(DataManager dataManager, DataSource dataSource, Sequences sequences) {
        super(dataManager, dataSource, sequences, new Config(TipoLote.DIVERSOS_PAGAR, "diversos a pagar", "Diverso",
                "DIVERSO_PAGAR", "ITEM_DIVERSO_PAGAR", "DIVERSO_PAGAR_ID", "IMP_DIVERSO_PAGAR",
                "IMP_ITEM_DIVERSO_PAGAR", "diverso_pagar_seq_"));
    }
}
