package br.com.axialsoftware.axctg3.service.financeiro;

import br.com.axialsoftware.axctg3.entity.cadastros.Empresa;
import br.com.axialsoftware.axctg3.entity.financeiro.Banco;
import br.com.axialsoftware.axctg3.entity.financeiro.RemessaBanco;
import br.com.axialsoftware.axctg3.entity.financeiro.TituloReceber;
import br.com.axialsoftware.axctg3.service.UtilGeralService;
import io.jmix.core.DataManager;
import io.jmix.core.SaveContext;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

/**
 * Orquestração bank-agnostic da geração de remessa bancária pra cobrança de
 * {@link TituloReceber}: resolve a implementação certa pelo {@link Banco#getCodGeral()}
 * (código Febraban), avança a numeração de remessa/nosso número do {@link Banco}, grava o
 * resultado nos títulos e audita em {@link RemessaBanco}. A codificação do arquivo em si
 * (campo a campo) fica em {@link BancoCobrancaHandler}.
 */
@Service
public class RemessaBancoService {

    private final DataManager dataManager;
    private final UtilGeralService utilGeralService;
    private final List<BancoCobrancaHandler> handlers;

    public RemessaBancoService(DataManager dataManager, UtilGeralService utilGeralService,
                                List<BancoCobrancaHandler> handlers) {
        this.dataManager = dataManager;
        this.utilGeralService = utilGeralService;
        this.handlers = handlers;
    }

    /**
     * Resolvido a cada chamada (não em cache montado no construtor) — os handlers são
     * poucos e resolver na hora deixa a troca por dublê em teste (ex.: {@code @MockitoBean})
     * funcionar mesmo com stub configurado só no {@code @BeforeEach}, depois que o
     * contexto Spring já subiu.
     */
    private BancoCobrancaHandler resolverHandler(Integer codGeral) {
        return handlers.stream()
                .filter(h -> codGeral != null && codGeral.equals(h.getCodGeralSuportado()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Banco código " + codGeral + " ainda não suportado para remessa/retorno"));
    }

    /**
     * Gera a remessa dos títulos informados (todos precisam ser do mesmo {@link Banco} e do
     * mesmo mês/ano de emissão — o arquivo é um só, com um único header/trailer, então não
     * dá pra ter dois meses na mesma pasta {@code aaaamm}), grava {@code numRemessa}/
     * {@code numBanco} nos títulos e {@code nossoNumAtual} no banco, audita em
     * {@link RemessaBanco} e grava o arquivo em disco na pasta cadastrada no banco:
     * {@code <pastaRemessa>/<codGeral>/<aaaamm>/<nome padrão do banco>} — {@code aaaamm} é o
     * mês/ano de <b>emissão dos títulos</b>, não o dia em que a remessa foi gerada (ver
     * {@link PastaCobrancaBanco}); já o nome do arquivo em si usa a data de geração, porque é
     * isso que a seção 6.1 do manual do banco define (ver
     * {@link BancoCobrancaHandler#nomeArquivoRemessa}).
     */
    public RemessaBanco gerarRemessa(List<TituloReceber> titulos) {
        if (titulos.isEmpty()) {
            throw new IllegalArgumentException("Nenhum título selecionado");
        }
        Banco banco = titulos.get(0).getBanco();
        LocalDate dataEmissao = titulos.get(0).getDataEmissao();
        for (TituloReceber tituloReceber : titulos) {
            if (!banco.getId().equals(tituloReceber.getBanco().getId())) {
                throw new IllegalArgumentException("Todos os títulos selecionados precisam ser do mesmo banco");
            }
            if (tituloReceber.getDataEmissao().getYear() != dataEmissao.getYear()
                    || tituloReceber.getDataEmissao().getMonthValue() != dataEmissao.getMonthValue()) {
                throw new IllegalArgumentException(
                        "Todos os títulos selecionados precisam ter a mesma data de emissão (mês/ano)");
            }
        }

        BancoCobrancaHandler handler = resolverHandler(banco.getCodGeral());
        LocalDate dataRemessa = LocalDate.now();
        Path pasta = PastaCobrancaBanco.resolver(banco, dataEmissao);

        Empresa empresa = utilGeralService.getEmpresa();
        int numeroRemessa = (banco.getNumRemessa() == null ? 0 : banco.getNumRemessa()) + 1;
        byte[] arquivo = handler.gerarRemessa(empresa, banco, titulos, numeroRemessa);

        BigDecimal valorTotal = BigDecimal.ZERO;
        SaveContext saveContext = new SaveContext();
        for (TituloReceber tituloReceber : titulos) {
            tituloReceber.setNumRemessa(numeroRemessa);
            saveContext.saving(tituloReceber);
            valorTotal = valorTotal.add(tituloReceber.getValor());
        }
        banco.setNumRemessa(numeroRemessa);
        saveContext.saving(banco);

        RemessaBanco remessaBanco = dataManager.create(RemessaBanco.class);
        remessaBanco.setCodEmpresa(utilGeralService.getCodEmpresa());
        remessaBanco.setBanco(banco);
        remessaBanco.setDataGeracao(dataRemessa);
        remessaBanco.setNumRemessa(numeroRemessa);
        remessaBanco.setQuantidadeTitulos(titulos.size());
        remessaBanco.setValorTotal(valorTotal);
        saveContext.saving(remessaBanco);

        dataManager.save(saveContext);

        String nomeArquivo = handler.nomeArquivoRemessa(banco, dataRemessa, numeroRemessa);
        try {
            Files.createDirectories(pasta);
            Files.write(pasta.resolve(nomeArquivo), arquivo);
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "Não foi possível gravar o arquivo em " + pasta + ": " + e.getMessage(), e);
        }

        return remessaBanco;
    }
}
