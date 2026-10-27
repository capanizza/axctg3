package br.com.axialsoftware.axctg3.service.importacao;

import br.com.axialsoftware.axctg3.entity.cadastros.Mensagem;
import br.com.axialsoftware.axctg3.entity.enums.TipoLote;
import br.com.axialsoftware.axctg3.entity.importacao.ImpLote;
import br.com.axialsoftware.axctg3.entity.importacao.ImpMensagem;
import io.jmix.core.DataManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Importa um {@link ImpLote} de mensagens do legado (MENSAGEM — os textos padrão escolhidos na nota
 * e no pedido) para a empresa do lote — comportamento em {@link ImportacaoCadastroBase}. Não havia
 * rotina antiga no projeto Axial. Vem antes das notas de saída e dos pedidos, que se ligam à
 * mensagem pelo código.
 */
@Service
public class ImportacaoMensagensService {

    private final DataManager dataManager;

    public ImportacaoMensagensService(DataManager dataManager) {
        this.dataManager = dataManager;
    }

    /** Confere o lote e o compara com as mensagens da empresa. Só grava o relatório no lote. */
    @Transactional
    public ImportacaoPlanoContasService.Relatorio conferir(UUID loteId) {
        return new Importacao(dataManager).conferir(dataManager.load(ImpLote.class).id(loteId).one());
    }

    /** Confere e, sem erro, cria as novas e atualiza as alteradas. Tudo ou nada. */
    @Transactional
    public ImportacaoPlanoContasService.Relatorio importar(UUID loteId) {
        return new Importacao(dataManager).importar(dataManager.load(ImpLote.class).id(loteId).one());
    }

    private static class Importacao extends ImportacaoCadastroBase<ImpMensagem, Mensagem, Integer> {

        Importacao(DataManager dataManager) {
            super(dataManager);
        }

        @Override
        protected TipoLote tipo() {
            return TipoLote.MENSAGENS;
        }

        @Override
        protected String nome() {
            return "Mensagem";
        }

        @Override
        protected List<ImpMensagem> linhas(ImpLote lote) {
            return dataManager.load(ImpMensagem.class)
                    .query("select e from ImpMensagem e where e.lote = :lote order by e.codigo")
                    .parameter("lote", lote)
                    .list();
        }

        @Override
        protected List<Mensagem> gravados(ImpLote lote) {
            return dataManager.load(Mensagem.class)
                    .query("select e from Mensagem e where e.codEmpresa = :codEmpresa")
                    .parameter("codEmpresa", lote.getCodEmpresa())
                    .list();
        }

        @Override
        protected Integer codigoLinha(ImpMensagem linha) {
            return linha.getCodigo();
        }

        @Override
        protected Integer codigoGravado(Mensagem mensagem) {
            return mensagem.getCodigo();
        }

        @Override
        protected Mensagem criar(ImpLote lote, Integer codigo) {
            Mensagem mensagem = dataManager.create(Mensagem.class);
            mensagem.setCodigo(codigo);
            mensagem.setCodEmpresa(lote.getCodEmpresa());
            return mensagem;
        }

        @Override
        protected void aplicar(ImpLote lote, ImpMensagem l, Mensagem m, Campos campos, String titulo,
                               ImportacaoPlanoContasService.Relatorio relatorio) {
            String texto = texto(l.getTexto());
            if (texto == null) {
                relatorio.avisos.add(titulo + " sem texto no legado.");
            }
            campos.campo("texto", m::getTexto, m::setTexto, texto);
        }

        @Override
        protected String descrever(Mensagem mensagem) {
            String texto = mensagem.getTexto();
            return texto == null || texto.length() <= 40 ? texto : texto.substring(0, 40) + "...";
        }
    }
}
