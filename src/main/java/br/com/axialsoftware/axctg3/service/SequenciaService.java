package br.com.axialsoftware.axctg3.service;

import io.jmix.data.Sequence;
import io.jmix.data.Sequences;
import org.springframework.stereotype.Service;

@Service
public class SequenciaService {

    private final Sequences sequences;

    public SequenciaService(Sequences sequences) {
        this.sequences = sequences;
    }

    /** Empurra {@code valor} pra sequence de banco de nome {@code codigo} — ver Javadoc de {@link br.com.axialsoftware.axctg3.entity.Sequencia}. */
    public void atualizaSequencia(String codigo, Integer valor) {
        sequences.setCurrentValue(Sequence.withName(codigo), valor);
    }
}
