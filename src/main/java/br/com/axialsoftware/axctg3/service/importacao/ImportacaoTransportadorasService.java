package br.com.axialsoftware.axctg3.service.importacao;

import br.com.axialsoftware.axctg3.entity.cadastros.Transportadora;
import br.com.axialsoftware.axctg3.entity.enums.TipoLote;
import br.com.axialsoftware.axctg3.entity.importacao.ImpLote;
import br.com.axialsoftware.axctg3.entity.importacao.ImpTransportadora;
import io.jmix.core.DataManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Importa um {@link ImpLote} de transportadoras do legado (TRANSPO) para a empresa do lote —
 * comportamento em {@link ImportacaoCadastroBase}. Faz o papel de
 * {@code exportacao.ExportacaoTransportadora} do projeto Axial, que gravava direto e pulava o que
 * já existia. A cidade continua texto, como no legado e na entidade; inscrição, telefones e
 * tipo de redespacho do legado não têm lugar no axctg3.
 */
@Service
public class ImportacaoTransportadorasService {

    private final DataManager dataManager;

    public ImportacaoTransportadorasService(DataManager dataManager) {
        this.dataManager = dataManager;
    }

    /** Confere o lote e o compara com as transportadoras da empresa. Só grava o relatório no lote. */
    @Transactional
    public ImportacaoPlanoContasService.Relatorio conferir(UUID loteId) {
        return new Importacao(dataManager).conferir(dataManager.load(ImpLote.class).id(loteId).one());
    }

    /** Confere e, sem erro, cria as novas e atualiza as alteradas. Tudo ou nada. */
    @Transactional
    public ImportacaoPlanoContasService.Relatorio importar(UUID loteId) {
        return new Importacao(dataManager).importar(dataManager.load(ImpLote.class).id(loteId).one());
    }

    private static class Importacao extends ImportacaoCadastroBase<ImpTransportadora, Transportadora, Integer> {

        Importacao(DataManager dataManager) {
            super(dataManager);
        }

        @Override
        protected TipoLote tipo() {
            return TipoLote.TRANSPORTADORAS;
        }

        @Override
        protected String nome() {
            return "Transportadora";
        }

        @Override
        protected List<ImpTransportadora> linhas(ImpLote lote) {
            return dataManager.load(ImpTransportadora.class)
                    .query("select e from ImpTransportadora e where e.lote = :lote order by e.codigo")
                    .parameter("lote", lote)
                    .list();
        }

        @Override
        protected List<Transportadora> gravados(ImpLote lote) {
            return dataManager.load(Transportadora.class)
                    .query("select e from Transportadora e where e.codEmpresa = :codEmpresa")
                    .parameter("codEmpresa", lote.getCodEmpresa())
                    .list();
        }

        @Override
        protected Integer codigoLinha(ImpTransportadora linha) {
            return linha.getCodigo();
        }

        @Override
        protected Integer codigoGravado(Transportadora transportadora) {
            return transportadora.getCodigo();
        }

        @Override
        protected Transportadora criar(ImpLote lote, Integer codigo) {
            Transportadora transportadora = dataManager.create(Transportadora.class);
            transportadora.setCodigo(codigo);
            transportadora.setCodEmpresa(lote.getCodEmpresa());
            return transportadora;
        }

        @Override
        protected void aplicar(ImpLote lote, ImpTransportadora l, Transportadora t, Campos campos, String titulo,
                               ImportacaoPlanoContasService.Relatorio relatorio) {
            String nome = texto(l.getNome());
            if (nome == null) {
                relatorio.erros.add(titulo + " sem nome.");
            }
            campos.campo("nome", t::getNome, t::setNome, cortar(nome, 50, titulo, "nome", relatorio));
            campos.campo("CNPJ", t::getCnpj, t::setCnpj, cortar(texto(l.getCnpj()), 14, titulo, "CNPJ", relatorio));
            campos.campo("endereço", t::getEndereco, t::setEndereco,
                    cortar(texto(l.getEndereco()), 50, titulo, "endereço", relatorio));
            campos.campo("bairro", t::getBairro, t::setBairro, cortar(texto(l.getBairro()), 20, titulo, "bairro", relatorio));
            campos.campo("cidade", t::getCidade, t::setCidade, cortar(texto(l.getCidade()), 20, titulo, "cidade", relatorio));
            campos.campo("UF", t::getEstado, t::setEstado, cortar(texto(l.getEstado()), 2, titulo, "UF", relatorio));
            campos.campo("CEP", t::getCep, t::setCep, cortar(texto(l.getCep()), 8, titulo, "CEP", relatorio));
        }

        @Override
        protected String descrever(Transportadora transportadora) {
            return transportadora.getNome();
        }
    }
}
