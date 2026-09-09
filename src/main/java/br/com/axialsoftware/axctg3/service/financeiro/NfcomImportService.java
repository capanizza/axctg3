package br.com.axialsoftware.axctg3.service.financeiro;

import br.com.axialsoftware.axctg3.entity.cadastros.Parceiro;
import br.com.axialsoftware.axctg3.entity.financeiro.Banco;
import br.com.axialsoftware.axctg3.entity.financeiro.TituloReceber;
import br.com.axialsoftware.axctg3.entity.tabelas.Municipio;
import br.com.axialsoftware.axctg3.service.UtilGeralService;
import io.jmix.core.DataManager;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Import de XML de NFCom (modelo 62) pra gerar {@link TituloReceber} — o axctg3 não emite NFCom
 * (é emitida por app especializado do cliente), só gerencia a cobrança da duplicata gerada por
 * ela. Sem entidade {@code Nfcom} própria: o XML é lido e descartado, só o {@link TituloReceber}
 * fica gravado (mesma decisão de não guardar o XML da NFe original nesta parte do sistema).
 *
 * <p>Regras replicadas do sistema intermediário Axial
 * ({@code ReceberGridController.importarXmls/lerXMLCliente}, fora deste repo — usuário indicou
 * a rotina em 2026-09-07):
 * <ul>
 *   <li>Dedup por {@code numero}+{@code codEmpresa} — um XML cujo número já existe é
 *   {@link Resultado#DUPLICADA}, não sobrescreve;
 *   <li>{@code Parceiro} casado por CNPJ/CPF; se não existir, cria com
 *   {@code codigo = max(codigo)+1} da empresa, {@code cliente=true};
 *   <li>Cancelamento (evento 110111) exclui o título pelo número derivado da chave — decisão do
 *   usuário (diferente do Axial, que só marca {@code OBS='Cancelada'} sem excluir);
 *   <li>Banco: NFCom não tem grupo de cobrança/duplicata (sempre 1 nota = 1 título) nem indica
 *   banco — usa o único {@link Banco} cadastrado pra empresa (mesmo valor fixo que o Axial grava,
 *   só que resolvido dinamicamente em vez de hardcoded).
 * </ul>
 *
 * <p>Desvio proposital do Axial: se o cancelamento chegar pra um título que já tem baixa
 * registrada, NÃO exclui — reporta {@link Resultado#IGNORADA} pra revisão manual, já que apagar
 * um título pago perderia o histórico de recebimento sem aviso.
 */
@Service
public class NfcomImportService {

    private final DataManager dataManager;
    private final NfcomXmlParser parser;
    private final UtilGeralService utilGeralService;

    public NfcomImportService(DataManager dataManager, NfcomXmlParser parser, UtilGeralService utilGeralService) {
        this.dataManager = dataManager;
        this.parser = parser;
        this.utilGeralService = utilGeralService;
    }

    public enum Resultado {
        CRIADA, CANCELADA, DUPLICADA, NAO_ENCONTRADA, IGNORADA, ERRO
    }

    public record ImportResult(Resultado resultado, String mensagem) {
    }

    public ImportResult importar(String nomeArquivo, byte[] conteudoXml) {
        try {
            NfcomXmlLido lido = parser.parse(conteudoXml);
            return switch (lido) {
                case NfcomXmlLido.Cancelamento cancelamento -> processarCancelamento(nomeArquivo, cancelamento);
                case NfcomXmlLido.Nota nota -> processarNota(nomeArquivo, nota);
            };
        } catch (Exception e) {
            return new ImportResult(Resultado.ERRO, nomeArquivo + ": " + e.getMessage());
        }
    }

    private ImportResult processarCancelamento(String nomeArquivo, NfcomXmlLido.Cancelamento cancelamento) {
        if (cancelamento.numero() == null) {
            return new ImportResult(Resultado.ERRO, nomeArquivo + ": chave de acesso não encontrada no evento");
        }
        Integer codEmpresa = utilGeralService.getCodEmpresa();
        TituloReceber tituloReceber = buscarTitulo(cancelamento.numero(), codEmpresa);
        if (tituloReceber == null) {
            return new ImportResult(Resultado.NAO_ENCONTRADA,
                    nomeArquivo + ": título " + cancelamento.numero() + " não encontrado pra cancelar");
        }
        if (Boolean.FALSE.equals(tituloReceber.getAberto())) {
            return new ImportResult(Resultado.IGNORADA,
                    nomeArquivo + ": título " + cancelamento.numero()
                            + " já tem baixa registrada — cancelamento não aplicado, revisar manualmente");
        }
        dataManager.remove(tituloReceber);
        return new ImportResult(Resultado.CANCELADA, nomeArquivo + ": título " + cancelamento.numero() + " cancelado");
    }

    private ImportResult processarNota(String nomeArquivo, NfcomXmlLido.Nota nota) {
        if (nota.numero() == null) {
            return new ImportResult(Resultado.ERRO, nomeArquivo + ": número da nota não encontrado no XML");
        }
        Integer codEmpresa = utilGeralService.getCodEmpresa();
        if (buscarTitulo(nota.numero(), codEmpresa) != null) {
            return new ImportResult(Resultado.DUPLICADA, nomeArquivo + ": título " + nota.numero() + " já importado");
        }

        Parceiro parceiro = resolverParceiro(nota.destinatario(), codEmpresa);
        Banco banco = resolverBancoUnico(codEmpresa);

        TituloReceber tituloReceber = dataManager.create(TituloReceber.class);
        tituloReceber.setNumero(nota.numero());
        tituloReceber.setDataEmissao(nota.dataEmissao());
        tituloReceber.setDataVencimento(nota.dataVencimento());
        tituloReceber.setParceiro(parceiro);
        tituloReceber.setBanco(banco);
        tituloReceber.setValor(nota.valor());
        dataManager.save(tituloReceber);

        return new ImportResult(Resultado.CRIADA, null);
    }

    private TituloReceber buscarTitulo(String numero, Integer codEmpresa) {
        return dataManager.load(TituloReceber.class)
                .query("select e from TituloReceber e where e.numero = :numero and e.codEmpresa = :codEmpresa")
                .parameter("numero", numero)
                .parameter("codEmpresa", codEmpresa)
                .optional()
                .orElse(null);
    }

    private Parceiro resolverParceiro(NfcomXmlLido.Nota.Destinatario destinatario, Integer codEmpresa) {
        return dataManager.load(Parceiro.class)
                .query("select e from Parceiro e where e.cnpj = :cnpj and e.codEmpresa = :codEmpresa")
                .parameter("cnpj", destinatario.cnpjCpf())
                .parameter("codEmpresa", codEmpresa)
                .optional()
                .orElseGet(() -> criarParceiro(destinatario, codEmpresa));
    }

    private Parceiro criarParceiro(NfcomXmlLido.Nota.Destinatario destinatario, Integer codEmpresa) {
        Long maxCodigo = dataManager.loadValue(
                        "select max(e.codigo) from Parceiro e where e.codEmpresa = :codEmpresa", Long.class)
                .parameter("codEmpresa", codEmpresa)
                .one();

        Parceiro parceiro = dataManager.create(Parceiro.class);
        parceiro.setCodigo((maxCodigo == null ? 0 : maxCodigo) + 1);
        parceiro.setCodEmpresa(codEmpresa);
        parceiro.setCnpj(destinatario.cnpjCpf());
        parceiro.setNome(limitado(destinatario.nome(), 80));
        parceiro.setApelido(limitado(destinatario.nome(), 20));
        parceiro.setInscricao(destinatario.inscricaoEstadual());
        parceiro.setLogradouro(limitado(destinatario.logradouro(), 50));
        parceiro.setNumero(destinatario.numero());
        parceiro.setBairro(limitado(destinatario.bairro(), 20));
        parceiro.setMunicipio(resolverMunicipio(destinatario.codMunicipio()));
        parceiro.setCep(destinatario.cep());
        parceiro.setEstado(destinatario.uf());
        parceiro.setTelefone(limitado(destinatario.telefone(), 15));
        parceiro.setCliente(true);
        return dataManager.save(parceiro);
    }

    private Municipio resolverMunicipio(Integer codMunicipio) {
        if (codMunicipio == null) {
            return null;
        }
        return dataManager.load(Municipio.class)
                .query("select e from Municipio e where e.codigo = :codigo")
                .parameter("codigo", codMunicipio)
                .optional()
                .orElse(null);
    }

    private Banco resolverBancoUnico(Integer codEmpresa) {
        List<Banco> bancos = dataManager.load(Banco.class)
                .query("select e from Banco e where e.codEmpresa = :codEmpresa")
                .parameter("codEmpresa", codEmpresa)
                .list();
        if (bancos.size() != 1) {
            throw new IllegalStateException(
                    "Import de NFCom exige exatamente 1 banco cadastrado pra empresa — encontrado(s) " + bancos.size());
        }
        return bancos.get(0);
    }

    private static String limitado(String valor, int tamanho) {
        if (valor == null) {
            return null;
        }
        return valor.length() > tamanho ? valor.substring(0, tamanho) : valor;
    }
}
