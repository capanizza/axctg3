package br.com.axialsoftware.axctg3.service.importacao;

import br.com.axialsoftware.axctg3.entity.enums.TipoLote;
import io.jmix.core.DataManager;
import io.jmix.data.Sequences;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;

/**
 * Importa um {@link br.com.axialsoftware.axctg3.entity.importacao.ImpLote} de títulos a pagar do
 * legado (PAGAR, com todos os movimentos de MOVPAGAR) — ver {@link ImportacaoContasPagarBase}.
 */
@Service
public class ImportacaoTitulosPagarService extends ImportacaoContasPagarBase {

    public ImportacaoTitulosPagarService(DataManager dataManager, DataSource dataSource, Sequences sequences) {
        super(dataManager, dataSource, sequences, new Config(TipoLote.TITULOS_PAGAR, "títulos a pagar", "Título",
                "TITULO_PAGAR", "ITEM_PAGAR", "TITULO_PAGAR_ID", "IMP_TITULO_PAGAR", "IMP_ITEM_PAGAR",
                "titulo_pagar_seq_"));
    }
}
