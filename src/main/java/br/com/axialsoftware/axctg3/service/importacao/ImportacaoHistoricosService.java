package br.com.axialsoftware.axctg3.service.importacao;

import br.com.axialsoftware.axctg3.entity.contabil.HistoricoContabil;
import br.com.axialsoftware.axctg3.entity.enums.SituacaoLote;
import br.com.axialsoftware.axctg3.entity.enums.TipoLote;
import br.com.axialsoftware.axctg3.entity.importacao.ImpHistoricoContabil;
import br.com.axialsoftware.axctg3.entity.importacao.ImpLote;
import io.jmix.core.DataManager;
import io.jmix.core.SaveContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Importa um {@link ImpLote} de históricos contábeis do legado (HISTORICO) para a empresa do
 * lote. Substitui {@code exportacao.ExportacaoHistCont} do projeto Axial, que gravava direto
 * em HISTORICO_CONTABIL e pulava o que já existia.
 * <p>
 * Diferente do plano de contas e dos lançamentos, não exige a empresa vazia: o histórico não
 * tem ano, e reimportar é o jeito de trazer os históricos criados no legado depois da primeira
 * vez. Por código: o que não existe é criado, o que existe com outra descrição recebe a do
 * legado, o que está igual não é gravado. Nada é apagado — histórico que só existe no axctg3
 * (criado aqui, ou apagado no legado) fica como está e aparece nos avisos.
 * <p>
 * O histórico 0 (".") que as empresas importadas pelo caminho antigo têm no axctg3 não vem do
 * legado: foi criado à mão para o {@code LancamentoTmp}. Aparece nos avisos como "só no axctg3".
 * A importação de lançamentos nova não precisa dele — lançamento sem histórico no legado fica sem
 * histórico.
 */
@Service
public class ImportacaoHistoricosService {

    private static final int TAMANHO_DESCRICAO = 50;
    private static final int LIMITE_LINHAS_SECAO = 300;

    private final DataManager dataManager;

    public ImportacaoHistoricosService(DataManager dataManager) {
        this.dataManager = dataManager;
    }

    /** Confere o lote e o compara com os históricos da empresa. Só grava o relatório no lote. */
    @Transactional
    public ImportacaoPlanoContasService.Relatorio conferir(UUID loteId) {
        ImpLote lote = dataManager.load(ImpLote.class).id(loteId).one();
        ImportacaoPlanoContasService.Relatorio relatorio = new ImportacaoPlanoContasService.Relatorio();
        Comparacao comparacao = null;
        validarLote(lote, relatorio, false);
        if (relatorio.erros.isEmpty()) {
            comparacao = comparar(lote, ler(lote, relatorio), relatorio);
        }
        gravarRelatorio(lote, relatorio, comparacao, false);
        return relatorio;
    }

    /** Confere e, sem erro, cria os novos e atualiza os alterados. Tudo ou nada. */
    @Transactional
    public ImportacaoPlanoContasService.Relatorio importar(UUID loteId) {
        ImpLote lote = dataManager.load(ImpLote.class).id(loteId).one();
        ImportacaoPlanoContasService.Relatorio relatorio = new ImportacaoPlanoContasService.Relatorio();
        Comparacao comparacao = null;
        validarLote(lote, relatorio, true);
        if (relatorio.erros.isEmpty()) {
            comparacao = comparar(lote, ler(lote, relatorio), relatorio);
        }
        boolean importado = relatorio.erros.isEmpty();
        if (importado) {
            List<HistoricoContabil> gravar = new ArrayList<>(comparacao.novos);
            gravar.addAll(comparacao.alterados);
            if (!gravar.isEmpty()) {
                SaveContext ctx = new SaveContext().setDiscardSaved(true);
                gravar.forEach(ctx::saving);
                dataManager.save(ctx);
            }
            relatorio.importadas = gravar.size();
        }
        gravarRelatorio(lote, relatorio, comparacao, importado);
        return relatorio;
    }

    /** {@code paraImportar}: só a importação exige lote não importado. */
    private void validarLote(ImpLote lote, ImportacaoPlanoContasService.Relatorio relatorio, boolean paraImportar) {
        if (lote.getTipo() != TipoLote.HISTORICOS) {
            relatorio.erros.add("O lote não é de históricos contábeis.");
            return;
        }
        if (lote.getSituacao() == SituacaoLote.EXPORTANDO) {
            relatorio.erros.add("A exportação deste lote não terminou (situação EXPORTANDO). Exporte de novo.");
            return;
        }
        // grava na empresa do lote, não na selecionada (mesma regra do plano de contas)
        Long empresas = dataManager.loadValue(
                        "select count(e) from Empresa e where e.codigo = :codEmpresa", Long.class)
                .parameter("codEmpresa", lote.getCodEmpresa())
                .one();
        if (empresas == 0) {
            relatorio.erros.add("A empresa " + lote.getCodEmpresa() + " não está cadastrada no axctg3.");
            return;
        }
        if (paraImportar && lote.getSituacao() == SituacaoLote.IMPORTADO) {
            relatorio.erros.add("Este lote já foi importado. Exporte de novo para trazer o que mudou no legado.");
        }
    }

    /** Código → descrição já normalizada. Código vazio, repetido ou sem descrição é erro. */
    private Map<Integer, String> ler(ImpLote lote, ImportacaoPlanoContasService.Relatorio relatorio) {
        List<ImpHistoricoContabil> linhas = dataManager.load(ImpHistoricoContabil.class)
                .query("select e from ImpHistoricoContabil e where e.lote = :lote order by e.codigo")
                .parameter("lote", lote)
                .list();
        Map<Integer, String> historicos = new TreeMap<>();
        if (linhas.isEmpty()) {
            relatorio.erros.add("O lote não tem nenhum histórico.");
            return historicos;
        }
        for (ImpHistoricoContabil linha : linhas) {
            Integer codigo = linha.getCodigo();
            String descricao = linha.getDescricao() == null ? "" : linha.getDescricao().trim();
            if (codigo == null) {
                relatorio.erros.add("Histórico sem código (descrição \"" + descricao + "\").");
                continue;
            }
            if (historicos.containsKey(codigo)) {
                relatorio.erros.add("Histórico " + codigo + " repetido no lote.");
                continue;
            }
            if (descricao.isEmpty()) {
                relatorio.erros.add("Histórico " + codigo + " sem descrição.");
                continue;
            }
            if (descricao.length() > TAMANHO_DESCRICAO) {
                relatorio.avisos.add("Histórico " + codigo + ": descrição cortada em " + TAMANHO_DESCRICAO
                        + " caracteres (" + descricao + ").");
                descricao = descricao.substring(0, TAMANHO_DESCRICAO).trim();
            }
            historicos.put(codigo, descricao);
        }
        return historicos;
    }

    /** Monta o que criar e o que alterar; o que só existe no axctg3 vira aviso. */
    private Comparacao comparar(ImpLote lote, Map<Integer, String> doLote,
                                ImportacaoPlanoContasService.Relatorio relatorio) {
        Comparacao comparacao = new Comparacao();
        if (!relatorio.erros.isEmpty()) {
            return comparacao;
        }
        Map<Integer, HistoricoContabil> gravados = new HashMap<>();
        dataManager.load(HistoricoContabil.class)
                .query("select e from HistoricoContabil e where e.codEmpresa = :codEmpresa")
                .parameter("codEmpresa", lote.getCodEmpresa())
                .list()
                .forEach(h -> gravados.put(h.getCodigo(), h));

        List<String> alteracoes = new ArrayList<>();
        for (Map.Entry<Integer, String> e : doLote.entrySet()) {
            HistoricoContabil h = gravados.get(e.getKey());
            if (h == null) {
                h = dataManager.create(HistoricoContabil.class);
                h.setCodigo(e.getKey());
                h.setCodEmpresa(lote.getCodEmpresa());
                h.setDescricao(e.getValue());
                comparacao.novos.add(h);
            } else if (Objects.equals(h.getDescricao(), e.getValue())) {
                comparacao.iguais++;
            } else {
                alteracoes.add("Histórico " + e.getKey() + ": \"" + h.getDescricao() + "\" passa a \""
                        + e.getValue() + "\".");
                h.setDescricao(e.getValue());
                comparacao.alterados.add(h);
            }
        }
        relatorio.avisos.addAll(alteracoes);
        gravados.keySet().stream()
                .filter(codigo -> !doLote.containsKey(codigo))
                .sorted()
                .forEach(codigo -> relatorio.avisos.add("Histórico " + codigo + " (\""
                        + gravados.get(codigo).getDescricao() + "\") existe no axctg3 e não veio no lote: fica como está."));
        return comparacao;
    }

    private void gravarRelatorio(ImpLote lote, ImportacaoPlanoContasService.Relatorio relatorio,
                                 Comparacao comparacao, boolean importado) {
        StringBuilder sb = new StringBuilder();
        sb.append(importado ? "IMPORTADO em " : "Conferido em ").append(OffsetDateTime.now().withNano(0)).append('\n');
        if (comparacao != null && relatorio.erros.isEmpty()) {
            sb.append(comparacao.novos.size()).append(importado ? " criados, " : " a criar, ")
                    .append(comparacao.alterados.size()).append(importado ? " alterados, " : " a alterar, ")
                    .append(comparacao.iguais).append(" iguais.\n");
        }
        secao(sb, "ERROS (impedem a importação)", relatorio.erros);
        secao(sb, "AVISOS", relatorio.avisos);
        lote.setMensagem(sb.toString());
        if (importado) {
            lote.setSituacao(SituacaoLote.IMPORTADO);
            lote.setDataImportacao(OffsetDateTime.now());
        }
        dataManager.saveWithoutReload(lote);
    }

    private void secao(StringBuilder sb, String titulo, List<String> linhas) {
        if (linhas.isEmpty()) {
            return;
        }
        sb.append('\n').append(titulo).append(" (").append(linhas.size()).append(")\n");
        linhas.stream().limit(LIMITE_LINHAS_SECAO).forEach(linha -> sb.append("  - ").append(linha).append('\n'));
        if (linhas.size() > LIMITE_LINHAS_SECAO) {
            sb.append("  ... e mais ").append(linhas.size() - LIMITE_LINHAS_SECAO).append('\n');
        }
    }

    private static class Comparacao {
        final List<HistoricoContabil> novos = new ArrayList<>();
        final List<HistoricoContabil> alterados = new ArrayList<>();
        int iguais;
    }
}
