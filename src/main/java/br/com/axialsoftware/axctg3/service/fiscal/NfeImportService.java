package br.com.axialsoftware.axctg3.service.fiscal;

import br.com.axialsoftware.axctg3.entity.fiscal.Nfe;
import br.com.axialsoftware.axctg3.entity.fiscal.NfeDuplicata;
import br.com.axialsoftware.axctg3.entity.fiscal.NfeItem;
import br.com.axialsoftware.axctg3.entity.fiscal.NfePagamento;
import br.com.axialsoftware.axctg3.entity.fiscal.NfeVolume;

import io.jmix.core.DataManager;
import io.jmix.core.SaveContext;
import org.springframework.stereotype.Service;

/**
 * Import de {@link Nfe} a partir do XML autorizado (modelo 55), com ou sem o wrapper
 * {@code nfeProc} de protocolo. Só popula {@code Nfe}/{@code NfeItem}/{@code NfeVolume}/
 * {@code NfeDuplicata}/{@code NfePagamento} — sem side-effects em {@code Parceiro}/
 * {@code NotaSaida}/contas a receber (decisão do usuário, 2026-08-11; ver
 * {@link NfeXmlParser} pro porquê disso divergir do sistema legado AxFiscal que inspirou
 * esta rotina).
 * <p>
 * Dedup por {@code chave} (índice único em {@code Nfe}): um XML cuja chave já foi
 * importada é reportado como {@link Resultado#DUPLICADA} e não grava nada — a mesma
 * política do import de lançamentos contábeis (pula o que já existe, não sobrescreve).
 */
@Service
public class NfeImportService {

    private final DataManager dataManager;
    private final NfeXmlParser parser;

    public NfeImportService(DataManager dataManager, NfeXmlParser parser) {
        this.dataManager = dataManager;
        this.parser = parser;
    }

    public enum Resultado {
        CRIADA, DUPLICADA, ERRO
    }

    public record ImportResult(Resultado resultado, String mensagem) {
    }

    public ImportResult importar(String nomeArquivo, byte[] conteudoXml) {
        Nfe nfe;
        try {
            nfe = parser.parse(conteudoXml);
        } catch (Exception e) {
            return new ImportResult(Resultado.ERRO, nomeArquivo + ": " + e.getMessage());
        }
        if (nfe.getChave() == null || nfe.getChave().isBlank()) {
            return new ImportResult(Resultado.ERRO,
                    nomeArquivo + ": chave de acesso não encontrada no XML");
        }

        boolean jaImportada = dataManager.load(Nfe.class)
                .query("select e from Nfe e where e.chave = :chave")
                .parameter("chave", nfe.getChave())
                .optional()
                .isPresent();
        if (jaImportada) {
            return new ImportResult(Resultado.DUPLICADA,
                    nomeArquivo + ": NFe " + nfe.getChave() + " já importada");
        }

        // @Composition não implica cascade automático de DataManager.save(nfe) sozinho — os
        // @OneToMany aqui não declaram CascadeType (só o @OnDelete cuida do cascade na exclusão).
        // Cada filho entra explícito no mesmo SaveContext, igual ao que o DataContext da UI faz
        // por trás dos panos ao salvar uma detail view com composição.
        SaveContext saveContext = new SaveContext();
        saveContext.saving(nfe);
        for (NfeItem item : nfe.getItens()) {
            saveContext.saving(item);
        }
        for (NfeDuplicata duplicata : nfe.getDuplicatas()) {
            saveContext.saving(duplicata);
        }
        for (NfePagamento pagamento : nfe.getPagamentos()) {
            saveContext.saving(pagamento);
        }
        for (NfeVolume volume : nfe.getVolumes()) {
            saveContext.saving(volume);
        }
        dataManager.save(saveContext);

        return new ImportResult(Resultado.CRIADA, null);
    }
}
