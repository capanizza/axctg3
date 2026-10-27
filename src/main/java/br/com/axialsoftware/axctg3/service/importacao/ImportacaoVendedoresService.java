package br.com.axialsoftware.axctg3.service.importacao;

import br.com.axialsoftware.axctg3.entity.cadastros.Vendedor;
import br.com.axialsoftware.axctg3.entity.enums.TipoLote;
import br.com.axialsoftware.axctg3.entity.importacao.ImpLote;
import br.com.axialsoftware.axctg3.entity.importacao.ImpVendedor;
import io.jmix.core.DataManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

/**
 * Importa um {@link ImpLote} de vendedores do legado (VENDEDOR) para a empresa do lote —
 * comportamento em {@link ImportacaoCadastroBase}. Faz o papel de
 * {@code exportacao.ExportacaoVendedor} do projeto Axial, que gravava direto e pulava o que já
 * existia. A comissão do legado tem 4 casas e a do axctg3 2: arredonda, com aviso se mudar. Os
 * demais campos do legado (apelido, endereço, telefones, supervisor...) não têm lugar no axctg3.
 */
@Service
public class ImportacaoVendedoresService {

    private final DataManager dataManager;

    public ImportacaoVendedoresService(DataManager dataManager) {
        this.dataManager = dataManager;
    }

    /** Confere o lote e o compara com os vendedores da empresa. Só grava o relatório no lote. */
    @Transactional
    public ImportacaoPlanoContasService.Relatorio conferir(UUID loteId) {
        return new Importacao(dataManager).conferir(dataManager.load(ImpLote.class).id(loteId).one());
    }

    /** Confere e, sem erro, cria os novos e atualiza os alterados. Tudo ou nada. */
    @Transactional
    public ImportacaoPlanoContasService.Relatorio importar(UUID loteId) {
        return new Importacao(dataManager).importar(dataManager.load(ImpLote.class).id(loteId).one());
    }

    private static class Importacao extends ImportacaoCadastroBase<ImpVendedor, Vendedor, Integer> {

        Importacao(DataManager dataManager) {
            super(dataManager);
        }

        @Override
        protected TipoLote tipo() {
            return TipoLote.VENDEDORES;
        }

        @Override
        protected String nome() {
            return "Vendedor";
        }

        @Override
        protected List<ImpVendedor> linhas(ImpLote lote) {
            return dataManager.load(ImpVendedor.class)
                    .query("select e from ImpVendedor e where e.lote = :lote order by e.codigo")
                    .parameter("lote", lote)
                    .list();
        }

        @Override
        protected List<Vendedor> gravados(ImpLote lote) {
            return dataManager.load(Vendedor.class)
                    .query("select e from Vendedor e where e.codEmpresa = :codEmpresa")
                    .parameter("codEmpresa", lote.getCodEmpresa())
                    .list();
        }

        @Override
        protected Integer codigoLinha(ImpVendedor linha) {
            return linha.getCodigo();
        }

        @Override
        protected Integer codigoGravado(Vendedor vendedor) {
            return vendedor.getCodigo();
        }

        @Override
        protected Vendedor criar(ImpLote lote, Integer codigo) {
            Vendedor vendedor = dataManager.create(Vendedor.class);
            vendedor.setCodigo(codigo);
            vendedor.setCodEmpresa(lote.getCodEmpresa());
            return vendedor;
        }

        @Override
        protected void aplicar(ImpLote lote, ImpVendedor l, Vendedor v, Campos campos, String titulo,
                               ImportacaoPlanoContasService.Relatorio relatorio) {
            String nome = texto(l.getNome());
            if (nome == null) {
                relatorio.erros.add(titulo + " sem nome.");
            }
            BigDecimal comissao = l.getComissao() == null ? null : l.getComissao().setScale(2, RoundingMode.HALF_UP);
            if (comissao != null && comissao.compareTo(l.getComissao()) != 0) {
                relatorio.avisos.add(titulo + ": comissão " + l.getComissao().stripTrailingZeros().toPlainString()
                        + "% arredondada para " + comissao + "%.");
            }
            campos.campo("nome", v::getNome, v::setNome, cortar(nome, 50, titulo, "nome", relatorio));
            campos.campo("CNPJ/CPF", v::getCnpj, v::setCnpj, cortar(texto(l.getCnpj()), 14, titulo, "CNPJ/CPF", relatorio));
            campos.campo("comissão", v::getComissao, v::setComissao, comissao);
        }

        @Override
        protected String descrever(Vendedor vendedor) {
            return vendedor.getNome();
        }
    }
}
