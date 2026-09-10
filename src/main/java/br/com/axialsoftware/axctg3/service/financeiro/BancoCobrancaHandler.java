package br.com.axialsoftware.axctg3.service.financeiro;

import br.com.axialsoftware.axctg3.entity.cadastros.Empresa;
import br.com.axialsoftware.axctg3.entity.financeiro.Banco;
import br.com.axialsoftware.axctg3.entity.financeiro.TituloReceber;

import java.time.LocalDate;
import java.util.List;

/**
 * Geração de remessa e leitura de retorno bancário (CNAB) — uma implementação por banco,
 * porque o "padrão" CNAB varia campo a campo de banco pra banco (e entre CNAB240/CNAB400).
 * {@code RemessaBancoService}/{@code RetornoBancoService} resolvem a implementação certa
 * pelo {@link Banco#getCodGeral()} (código Febraban) e cuidam de tudo que é comum entre
 * bancos (persistência, regra de negócio de baixa etc.) — a implementação só sabe
 * codificar/decodificar o arquivo do seu banco.
 */
public interface BancoCobrancaHandler {

    /** Código Febraban do banco atendido por esta implementação (ex.: 748 = Sicredi). */
    Integer getCodGeralSuportado();

    /**
     * Monta o arquivo de remessa (header + 1 detalhe por título + trailer) pros títulos
     * informados. Efeito colateral: preenche {@link TituloReceber#setNumBanco} (Nosso
     * Número, calculado por esta implementação) e avança
     * {@link Banco#setNossoNumAtual} — quem chama salva as entidades depois de gerar.
     */
    byte[] gerarRemessa(Empresa empresa, Banco banco, List<TituloReceber> titulos, int numeroRemessa);

    /** Interpreta um arquivo de retorno, devolvendo um registro lido por linha de detalhe. */
    List<RetornoDetalheLido> lerRetorno(byte[] arquivo);

    /**
     * Monta os 44 dígitos do código de barras Febraban pro boleto do título — estrutura
     * geral (banco+moeda+DV+fator vencimento+valor) é padrão Febraban, mas o "campo livre"
     * (25 dígitos) é definido por cada banco. Requer {@link TituloReceber#getNumBanco()} já
     * preenchido (nosso número, gerado em {@link #gerarRemessa}).
     */
    String montarCodigoBarras(Banco banco, TituloReceber tituloReceber);

    /** Dígito verificador Febraban do código do banco, ex.: {@code "X"} pro 748 (Sicredi). */
    String getDigitoVerificadorBanco();

    /**
     * Formata o Nosso Número pro padrão impresso do banco — recebe {@link Banco} porque
     * alguns bancos (Sicredi incluso) só persistem o sequencial em
     * {@link TituloReceber#getNumBanco()}; o resto (ano/byte/DV) é recomposto a partir do
     * cadastro do banco e da data atual, não fica gravado.
     */
    String formatarNossoNumero(Banco banco, TituloReceber tituloReceber);

    /** Formata agência/posto/código do cedente pro cabeçalho "Agência / Código Beneficiário" do boleto. */
    String formatarAgenciaCodigoBeneficiario(Banco banco);

    /**
     * Nome do arquivo de remessa no padrão do banco — cada banco tem sua própria convenção
     * de nomenclatura, assim como o leiaute do CNAB (ex.: Sicredi, seção 6.1 do manual:
     * {@code CCCCCMDD.XXX}).
     */
    String nomeArquivoRemessa(Banco banco, LocalDate dataRemessa, int numeroRemessa);
}
