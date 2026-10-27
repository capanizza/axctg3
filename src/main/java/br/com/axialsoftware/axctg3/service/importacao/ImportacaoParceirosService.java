package br.com.axialsoftware.axctg3.service.importacao;

import br.com.axialsoftware.axctg3.entity.cadastros.Parceiro;
import br.com.axialsoftware.axctg3.entity.enums.TipoLote;
import br.com.axialsoftware.axctg3.entity.importacao.ImpLote;
import br.com.axialsoftware.axctg3.entity.importacao.ImpParceiro;
import br.com.axialsoftware.axctg3.entity.tabelas.Municipio;
import io.jmix.core.DataManager;
import io.jmix.core.FetchPlan;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Importa um {@link ImpLote} de parceiros do legado (PARCEIRO) para a empresa do lote —
 * comportamento em {@link ImportacaoCadastroBase}. Faz o papel de
 * {@code exportacao.ExportacaoParceiro} do projeto Axial, que gravava direto e pulava o que já
 * existia.
 * <p>
 * O município vem pelo código IBGE (0 = sem município); não achado, fica vazio com aviso. O
 * CNPJ/CPF fica como no legado, só dígitos. Sem apelido, usa o começo da razão social. CNPJ
 * repetido entre parceiros (fora o "000...0" dos órgãos públicos) vira aviso, não impede.
 * Parceiro sem CNPJ/CPF (na GB são favorecidos de contas a pagar: salário, FGTS, DAS...) fica de
 * fora, com aviso — o axctg3 exige o CNPJ/CPF.
 */
@Service
public class ImportacaoParceirosService {

    private final DataManager dataManager;

    public ImportacaoParceirosService(DataManager dataManager) {
        this.dataManager = dataManager;
    }

    /** Confere o lote e o compara com os parceiros da empresa. Só grava o relatório no lote. */
    @Transactional
    public ImportacaoPlanoContasService.Relatorio conferir(UUID loteId) {
        return new Importacao(dataManager).conferir(dataManager.load(ImpLote.class).id(loteId).one());
    }

    /** Confere e, sem erro, cria os novos e atualiza os alterados. Tudo ou nada. */
    @Transactional
    public ImportacaoPlanoContasService.Relatorio importar(UUID loteId) {
        return new Importacao(dataManager).importar(dataManager.load(ImpLote.class).id(loteId).one());
    }

    /** Uma execução: guarda os municípios usados entre {@code preparar} e {@code aplicar}. */
    private static class Importacao extends ImportacaoCadastroBase<ImpParceiro, Parceiro, Long> {

        private final Map<Integer, Municipio> municipios = new HashMap<>();

        Importacao(DataManager dataManager) {
            super(dataManager);
        }

        @Override
        protected TipoLote tipo() {
            return TipoLote.PARCEIROS;
        }

        @Override
        protected String nome() {
            return "Parceiro";
        }

        /** Parceiro sem CNPJ/CPF fica de fora (decisão do usuário 2026-10-27): ver {@link #preparar}. */
        @Override
        protected List<ImpParceiro> linhas(ImpLote lote) {
            return todasLinhas(lote).stream().filter(l -> texto(l.getCnpj()) != null).toList();
        }

        private List<ImpParceiro> todasLinhas(ImpLote lote) {
            return dataManager.load(ImpParceiro.class)
                    .query("select e from ImpParceiro e where e.lote = :lote order by e.codigo")
                    .parameter("lote", lote)
                    .list();
        }

        @Override
        protected List<Parceiro> gravados(ImpLote lote) {
            return dataManager.load(Parceiro.class)
                    .query("select e from Parceiro e where e.codEmpresa = :codEmpresa")
                    .parameter("codEmpresa", lote.getCodEmpresa())
                    .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE).add("municipio", FetchPlan.BASE))
                    .list();
        }

        @Override
        protected Long codigoLinha(ImpParceiro linha) {
            return linha.getCodigo();
        }

        @Override
        protected Long codigoGravado(Parceiro parceiro) {
            return parceiro.getCodigo();
        }

        @Override
        protected Parceiro criar(ImpLote lote, Long codigo) {
            Parceiro parceiro = dataManager.create(Parceiro.class);
            parceiro.setCodigo(codigo);
            parceiro.setCodEmpresa(lote.getCodEmpresa());
            return parceiro;
        }

        @Override
        protected void preparar(ImpLote lote, ImportacaoPlanoContasService.Relatorio relatorio) {
            List<String> semCnpj = todasLinhas(lote).stream()
                    .filter(l -> texto(l.getCnpj()) == null)
                    .map(l -> l.getCodigo() + " " + texto(l.getRazao()))
                    .toList();
            if (!semCnpj.isEmpty()) {
                relatorio.avisos.add(semCnpj.size() + " parceiro(s) sem CNPJ/CPF no legado não entram (o axctg3 exige o "
                        + "CNPJ/CPF); nota, pedido ou título que use um deles não poderá ser importado: "
                        + String.join("; ", semCnpj) + ".");
            }
            List<ImpParceiro> linhas = linhas(lote);
            List<Integer> codigos = linhas.stream()
                    .map(ImpParceiro::getMunicipio)
                    .filter(m -> m != null && m != 0)
                    .distinct()
                    .toList();
            if (!codigos.isEmpty()) {
                dataManager.load(Municipio.class)
                        .query("select e from Municipio e where e.codigo in :codigos")
                        .parameter("codigos", codigos)
                        .list()
                        .forEach(m -> municipios.put(m.getCodigo(), m));
            }
            Map<String, List<Long>> porCnpj = new TreeMap<>(linhas.stream()
                    .filter(l -> l.getCodigo() != null && texto(l.getCnpj()) != null
                            && !texto(l.getCnpj()).matches("0+"))
                    .collect(Collectors.groupingBy(l -> texto(l.getCnpj()),
                            Collectors.mapping(ImpParceiro::getCodigo, Collectors.toList()))));
            porCnpj.forEach((cnpj, parceiros) -> {
                if (parceiros.size() > 1) {
                    relatorio.avisos.add("CNPJ/CPF " + cnpj + " repetido nos parceiros " + parceiros + ".");
                }
            });
        }

        @Override
        protected void aplicar(ImpLote lote, ImpParceiro l, Parceiro p, Campos campos, String titulo,
                               ImportacaoPlanoContasService.Relatorio relatorio) {
            String razao = texto(l.getRazao());
            if (razao == null) {
                relatorio.erros.add(titulo + " sem razão social.");
            }
            String cnpj = texto(l.getCnpj());
            String apelido = texto(l.getApelido());
            if (apelido == null && razao != null) {
                apelido = razao.length() > 20 ? razao.substring(0, 20).trim() : razao;
                relatorio.avisos.add(titulo + ": sem apelido no legado; usado \"" + apelido + "\".");
            }
            campos.campo("razão social", p::getNome, p::setNome, cortar(razao, 80, titulo, "razão social", relatorio));
            campos.campo("apelido", p::getApelido, p::setApelido, cortar(apelido, 20, titulo, "apelido", relatorio));
            campos.campo("CNPJ/CPF", p::getCnpj, p::setCnpj, cortar(cnpj, 18, titulo, "CNPJ/CPF", relatorio));
            campos.campo("inscrição", p::getInscricao, p::setInscricao,
                    cortar(texto(l.getInscricao()), 25, titulo, "inscrição", relatorio));
            campos.campo("logradouro", p::getLogradouro, p::setLogradouro,
                    cortar(texto(l.getLogradouro()), 50, titulo, "logradouro", relatorio));
            campos.campo("número", p::getNumero, p::setNumero,
                    l.getNumero() == null || l.getNumero() == 0 ? null : String.valueOf(l.getNumero()));
            campos.campo("complemento", p::getComplemento, p::setComplemento,
                    cortar(texto(l.getComplemento()), 20, titulo, "complemento", relatorio));
            campos.campo("bairro", p::getBairro, p::setBairro, cortar(texto(l.getBairro()), 20, titulo, "bairro", relatorio));
            campos.campo("CEP", p::getCep, p::setCep, cortar(texto(l.getCep()), 8, titulo, "CEP", relatorio));
            campos.campo("UF", p::getEstado, p::setEstado, cortar(texto(l.getEstado()), 2, titulo, "UF", relatorio));
            campos.campo("telefone", p::getTelefone, p::setTelefone,
                    cortar(texto(l.getTelefone()), 15, titulo, "telefone", relatorio));
            campos.campo("contato", p::getContato, p::setContato, cortar(texto(l.getContato()), 40, titulo, "contato", relatorio));
            campos.campo("observação", p::getObservacao, p::setObservacao, texto(l.getObservacao()));
            campos.campo("cliente", p::getCliente, p::setCliente, "S".equalsIgnoreCase(texto(l.getCliente())));
            campos.campo("fornecedor", p::getFornecedor, p::setFornecedor, "S".equalsIgnoreCase(texto(l.getFornecedor())));

            Integer codMunicipio = l.getMunicipio() == null || l.getMunicipio() == 0 ? null : l.getMunicipio();
            Municipio municipio = codMunicipio == null ? null : municipios.get(codMunicipio);
            if (codMunicipio != null && municipio == null) {
                relatorio.avisos.add(titulo + ": município " + codMunicipio + " não existe na tabela de municípios; fica vazio.");
            }
            campos.campo("município", () -> p.getMunicipio() == null ? null : p.getMunicipio().getCodigo(),
                    v -> p.setMunicipio(municipio), municipio == null ? null : municipio.getCodigo());
        }

        @Override
        protected String descrever(Parceiro parceiro) {
            return parceiro.getNome();
        }
    }
}
