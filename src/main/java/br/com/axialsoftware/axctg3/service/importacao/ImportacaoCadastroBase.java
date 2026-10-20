package br.com.axialsoftware.axctg3.service.importacao;

import br.com.axialsoftware.axctg3.entity.contabil.ContaContabil;
import br.com.axialsoftware.axctg3.entity.enums.SituacaoLote;
import br.com.axialsoftware.axctg3.entity.enums.TipoLote;
import br.com.axialsoftware.axctg3.entity.importacao.ImpLote;
import io.jmix.core.DataManager;
import io.jmix.core.SaveContext;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Esqueleto comum da importação dos cadastros do legado (bancos, centros de custo, históricos
 * financeiros, parceiros), no mesmo comportamento dos históricos contábeis: na empresa do lote,
 * por código, o que não existe é criado, o que existe com algum campo diferente recebe os
 * valores do legado (cada diferença vira aviso), o que está igual não é gravado. Nada é
 * apagado — o que só existe no axctg3 fica como está e aparece nos avisos.
 * <p>
 * Os cadastros não têm ano: reimportar é o jeito de trazer o que foi criado ou alterado no
 * legado depois da primeira vez.
 *
 * <p>
 * Uma instância por execução (o serviço cria uma a cada chamada): {@link #preparar} pode guardar
 * em campos o que {@link #aplicar} consulta. O serviço abre a transação.
 *
 * @param <I> linha crua do lote ({@code Imp...})
 * @param <E> entidade de destino
 * @param <K> código que identifica o registro dentro da empresa
 */
public abstract class ImportacaoCadastroBase<I, E, K extends Comparable<K>> {

    private static final int LIMITE_LINHAS_SECAO = 300;

    protected final DataManager dataManager;

    protected ImportacaoCadastroBase(DataManager dataManager) {
        this.dataManager = dataManager;
    }

    protected abstract TipoLote tipo();

    /** Nome do registro nas mensagens, com maiúscula: "Banco", "Parceiro". */
    protected abstract String nome();

    /** Linhas do lote, na ordem do código. */
    protected abstract List<I> linhas(ImpLote lote);

    /** Registros da empresa já gravados no axctg3, com o que {@link #aplicar} compara. */
    protected abstract List<E> gravados(ImpLote lote);

    protected abstract K codigoLinha(I linha);

    protected abstract K codigoGravado(E entidade);

    /** Entidade nova, já com código e empresa. */
    protected abstract E criar(ImpLote lote, K codigo);

    /**
     * Prepara o que a conversão de todas as linhas precisa (contas, municípios...). Pode
     * registrar erro de pré-requisito em {@code relatorio}.
     */
    protected void preparar(ImpLote lote, ImportacaoPlanoContasService.Relatorio relatorio) {
    }

    /**
     * Leva os valores da linha para a entidade com {@link Campos#campo}, que anota cada
     * diferença. Problema que impede a importação vai para {@code relatorio.erros}; o que só
     * precisa ser visto, para {@code relatorio.avisos}. {@code titulo} é "Banco 1" etc.
     */
    protected abstract void aplicar(ImpLote lote, I linha, E entidade, Campos campos, String titulo,
                                    ImportacaoPlanoContasService.Relatorio relatorio);

    /** Texto que identifica o registro nos avisos de "só no axctg3". */
    protected abstract String descrever(E entidade);

    /** Confere o lote e o compara com o que a empresa já tem. Só grava o relatório no lote. */
    public ImportacaoPlanoContasService.Relatorio conferir(ImpLote lote) {
        return executar(lote, false);
    }

    /** Confere e, sem erro, cria os novos e atualiza os alterados. Tudo ou nada. */
    public ImportacaoPlanoContasService.Relatorio importar(ImpLote lote) {
        return executar(lote, true);
    }

    private ImportacaoPlanoContasService.Relatorio executar(ImpLote lote, boolean paraImportar) {
        ImportacaoPlanoContasService.Relatorio relatorio = new ImportacaoPlanoContasService.Relatorio();
        Comparacao<E> comparacao = null;
        validarLote(lote, relatorio, paraImportar);
        if (relatorio.erros.isEmpty()) {
            preparar(lote, relatorio);
        }
        if (relatorio.erros.isEmpty()) {
            comparacao = comparar(lote, relatorio);
        }
        boolean importado = paraImportar && relatorio.erros.isEmpty();
        if (importado) {
            List<E> gravar = new ArrayList<>(comparacao.novos);
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
        if (lote.getTipo() != tipo()) {
            relatorio.erros.add("O lote não é do tipo " + tipo() + ".");
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

    private Comparacao<E> comparar(ImpLote lote, ImportacaoPlanoContasService.Relatorio relatorio) {
        Comparacao<E> comparacao = new Comparacao<>();
        List<I> linhas = linhas(lote);
        if (linhas.isEmpty()) {
            relatorio.erros.add("O lote não tem nenhum registro.");
            return comparacao;
        }
        Map<K, E> gravados = new LinkedHashMap<>();
        gravados(lote).forEach(e -> gravados.put(codigoGravado(e), e));

        Map<K, I> doLote = new LinkedHashMap<>();
        for (I linha : linhas) {
            K codigo = codigoLinha(linha);
            if (codigo == null) {
                relatorio.erros.add(nome() + " sem código no lote.");
                continue;
            }
            if (doLote.containsKey(codigo)) {
                relatorio.erros.add(nome() + " " + codigo + " repetido no lote.");
                continue;
            }
            doLote.put(codigo, linha);
        }

        List<String> alteracoes = new ArrayList<>();
        for (Map.Entry<K, I> e : doLote.entrySet()) {
            String titulo = nome() + " " + e.getKey();
            E entidade = gravados.get(e.getKey());
            boolean novo = entidade == null;
            if (novo) {
                entidade = criar(lote, e.getKey());
            }
            Campos campos = new Campos(novo);
            aplicar(lote, e.getValue(), entidade, campos, titulo, relatorio);
            if (novo) {
                comparacao.novos.add(entidade);
            } else if (campos.mudancas.isEmpty()) {
                comparacao.iguais++;
            } else {
                alteracoes.add(titulo + ": " + String.join("; ", campos.mudancas) + ".");
                comparacao.alterados.add(entidade);
            }
        }
        relatorio.avisos.addAll(alteracoes);
        gravados.keySet().stream()
                .filter(codigo -> !doLote.containsKey(codigo))
                .sorted()
                .forEach(codigo -> relatorio.avisos.add(nome() + " " + codigo + " (\""
                        + descrever(gravados.get(codigo)) + "\") existe no axctg3 e não veio no lote: fica como está."));
        return comparacao;
    }

    private void gravarRelatorio(ImpLote lote, ImportacaoPlanoContasService.Relatorio relatorio,
                                 Comparacao<E> comparacao, boolean importado) {
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

    /**
     * Contas do plano do ano mais recente da empresa do lote, por código: o cadastro aponta para
     * uma conta de um ano só. Sem plano nenhum, registra erro ({@code motivo} completa a frase).
     */
    protected Map<String, ContaContabil> contasDoPlanoMaisRecente(ImpLote lote, String motivo,
                                                                  ImportacaoPlanoContasService.Relatorio relatorio) {
        Map<String, ContaContabil> contas = new HashMap<>();
        Integer ano = dataManager.loadValue(
                        "select max(e.ano) from ContaContabil e where e.codEmpresa = :codEmpresa", Integer.class)
                .parameter("codEmpresa", lote.getCodEmpresa())
                .optional()
                .orElse(null);
        if (ano == null) {
            relatorio.erros.add("A empresa " + lote.getCodEmpresa()
                    + " não tem plano de contas. Importe antes o plano de contas: " + motivo + ".");
            return contas;
        }
        dataManager.load(ContaContabil.class)
                .query("select e from ContaContabil e where e.codEmpresa = :codEmpresa and e.ano = :ano")
                .parameter("codEmpresa", lote.getCodEmpresa())
                .parameter("ano", ano)
                .list()
                .forEach(c -> contas.put(c.getCodigo(), c));
        return contas;
    }

    /** Código e ano, porque o mesmo código existe em cada ano do plano. */
    protected static String descreverConta(ContaContabil conta) {
        return conta == null ? null : conta.getCodigo() + " (" + conta.getAno() + ")";
    }

    /** Texto do legado sem espaços nas pontas; vazio vira null, como o axctg3 grava campo em branco. */
    protected static String texto(String valor) {
        if (valor == null) {
            return null;
        }
        String t = valor.trim();
        return t.isEmpty() ? null : t;
    }

    /**
     * Corta {@code valor} no tamanho da coluna de destino, avisando. {@code campo} é o nome do
     * campo na mensagem.
     */
    protected static String cortar(String valor, int tamanho, String titulo, String campo,
                                   ImportacaoPlanoContasService.Relatorio relatorio) {
        if (valor == null || valor.length() <= tamanho) {
            return valor;
        }
        relatorio.avisos.add(titulo + ": " + campo + " cortado em " + tamanho + " caracteres (" + valor + ").");
        return valor.substring(0, tamanho).trim();
    }

    /** Anota as diferenças campo a campo enquanto leva os valores do legado para a entidade. */
    protected static class Campos {
        private final boolean novo;
        final List<String> mudancas = new ArrayList<>();

        Campos(boolean novo) {
            this.novo = novo;
        }

        public <T> void campo(String nome, Supplier<T> atual, Consumer<T> definir, T valor) {
            T antes = atual.get();
            if (iguais(antes, valor)) {
                return;
            }
            if (!novo) {
                mudancas.add(nome + " " + mostrar(antes) + " passa a " + mostrar(valor));
            }
            definir.accept(valor);
        }

        private static boolean iguais(Object a, Object b) {
            if (a instanceof java.math.BigDecimal x && b instanceof java.math.BigDecimal y) {
                return x.compareTo(y) == 0;
            }
            return Objects.equals(a, b);
        }

        private static String mostrar(Object valor) {
            return valor == null ? "(vazio)" : "\"" + valor + "\"";
        }
    }

    private static class Comparacao<E> {
        final List<E> novos = new ArrayList<>();
        final List<E> alterados = new ArrayList<>();
        int iguais;
    }
}
