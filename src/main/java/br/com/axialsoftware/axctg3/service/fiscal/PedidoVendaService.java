package br.com.axialsoftware.axctg3.service.fiscal;

import br.com.axialsoftware.axctg3.entity.cadastros.ConfigRel;
import br.com.axialsoftware.axctg3.entity.cadastros.Empresa;
import br.com.axialsoftware.axctg3.entity.fiscal.ItemNotaSaida;
import br.com.axialsoftware.axctg3.entity.fiscal.ItemPedidoVenda;
import br.com.axialsoftware.axctg3.entity.fiscal.NotaSaida;
import br.com.axialsoftware.axctg3.entity.fiscal.PedidoVenda;
import br.com.axialsoftware.axctg3.entity.fiscal.PedidoVendaDto;
import br.com.axialsoftware.axctg3.service.RelatorioService;
import br.com.axialsoftware.axctg3.service.UtilGeralService;
import io.jmix.core.DataManager;
import io.jmix.core.FetchPlan;
import io.jmix.core.Messages;
import io.jmix.core.MetadataTools;
import io.jmix.core.SaveContext;
import net.sf.jasperreports.engine.JRDataSource;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class PedidoVendaService {

    private final DataManager dataManager;
    private final UtilGeralService utilGeralService;
    private final RelatorioService relatorioService;
    private final MetadataTools metadataTools;
    private final Messages messages;

    public PedidoVendaService(DataManager dataManager, UtilGeralService utilGeralService,
                               RelatorioService relatorioService, MetadataTools metadataTools,
                               Messages messages) {
        this.dataManager = dataManager;
        this.utilGeralService = utilGeralService;
        this.relatorioService = relatorioService;
        this.metadataTools = metadataTools;
        this.messages = messages;
    }

    /**
     * Listagem dos pedidos de venda da empresa/período atual — uma linha por
     * {@link ItemPedidoVenda}, cabeçalho do {@link PedidoVenda} repetido em cada linha.
     * Pedido sem item nenhum ainda gera uma linha só com os campos do cabeçalho (campos
     * de item em branco), pra não sumir da listagem.
     */
    public void listarPedidos() {
        listarPedidos(null);
    }

    /**
     * Listagem de um pedido só (o selecionado na tela) ou, com {@code pedidoId}
     * {@code null}, de todos os pedidos do período delimitado na tela (ConfigRel,
     * botão "Delimitar" de PedidoVendaListView) — os mesmos que aparecem no grid. Antes a
     * tela ignorava a seleção e listava sempre todos os pedidos da empresa, com o pedido 1
     * abrindo o PDF (relato do usuário 2026-09-24).
     */
    public void listarPedidos(UUID pedidoId) {
        String nomeRelatorio = "PedidoVenda.jasper";
        String nomeSaida = "PedidoVenda.pdf";

        var parametros = new HashMap<String, Object>();
        parametros.put("TITULO_RELATORIO", "Listagem dos pedidos de venda");
        parametros.put("NOME_EMPRESA", utilGeralService.getNomeEmpresa());
        parametros.put("LOGO", utilGeralService.getLogoEmpresa());

        List<PedidoVendaDto> pedidosDto = montarLinhasListagem(pedidoId);

        JRDataSource dataSource = new JRBeanCollectionDataSource(pedidosDto);

        relatorioService.emitirRelatorio(nomeRelatorio, dataSource, parametros, nomeSaida);
    }

    /** Linhas (pedido × item) da listagem — público só pra o teste conferir o filtro. */
    public List<PedidoVendaDto> montarLinhasListagem(UUID pedidoId) {
        Integer codEmpresa = utilGeralService.getCodEmpresa();
        var consulta = dataManager.load(PedidoVenda.class)
                .query("select e from PedidoVenda e where e.codEmpresa = :codEmpresa"
                        + (pedidoId == null
                                ? " and e.dataEntrada between :dataEntradaInicial and :dataEntradaFinal"
                                : " and e.id = :pedidoId")
                        + " order by e.numero")
                .parameter("codEmpresa", codEmpresa);
        if (pedidoId != null) {
            consulta = consulta.parameter("pedidoId", pedidoId);
        } else {
            ConfigRel configRel = utilGeralService.prepararConfigRel();
            consulta = consulta
                    .parameter("dataEntradaInicial", Optional.ofNullable(configRel.getDataEntradaPedidoVendaInicial())
                            .orElse(LocalDate.now()))
                    .parameter("dataEntradaFinal", Optional.ofNullable(configRel.getDataEntradaPedidoVendaFinal())
                            .orElse(LocalDate.now()));
        }
        List<PedidoVenda> pedidos = consulta
                .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE)
                        .add("parceiro", FetchPlan.BASE)
                        .add("natureza", FetchPlan.BASE)
                        .add("classTrib", FetchPlan.BASE)
                        .add("condicaoPagamento", FetchPlan.BASE)
                        .add("banco", FetchPlan.BASE)
                        .add("vendedor", FetchPlan.BASE)
                        .add("mensagem", FetchPlan.BASE)
                        .add("itens", fpItens -> fpItens.addFetchPlan(FetchPlan.BASE)
                                .add("produto", FetchPlan.BASE)))
                .list();

        List<PedidoVendaDto> linhas = new ArrayList<>();
        for (PedidoVenda pedido : pedidos) {
            if (pedido.getItens() == null || pedido.getItens().isEmpty()) {
                linhas.add(montarLinha(pedido, null));
            } else {
                for (ItemPedidoVenda item : pedido.getItens()) {
                    linhas.add(montarLinha(pedido, item));
                }
            }
        }
        return linhas;
    }

    private PedidoVendaDto montarLinha(PedidoVenda pedido, ItemPedidoVenda item) {
        PedidoVendaDto dto = dataManager.create(PedidoVendaDto.class);
        dto.setNumero(pedido.getNumero());
        dto.setDataEntrada(pedido.getDataEntrada());
        dto.setCliente(instanceNameOu(pedido.getParceiro()));
        dto.setNaturezaOperacao(instanceNameOu(pedido.getNatureza()));
        dto.setClassificacaoTributaria(instanceNameOu(pedido.getClassTrib()));
        dto.setCondicaoPagamento(instanceNameOu(pedido.getCondicaoPagamento()));
        dto.setBanco(instanceNameOu(pedido.getBanco()));
        dto.setVendedor(instanceNameOu(pedido.getVendedor()));
        dto.setMensagem(pedido.getMensagem() == null ? null
                : pedido.getMensagem().getCodigo() + " " + pedido.getMensagem().getTexto());
        dto.setComplementoMensagem(pedido.getComplementoMensagem());
        dto.setModFrete(pedido.getModFrete() == null ? null : messages.getMessage(pedido.getModFrete()));
        dto.setFrete(pedido.getFrete());
        dto.setSeguro(pedido.getSeguro());
        dto.setDesconto(pedido.getDesconto());
        dto.setDespesas(pedido.getDespesas());
        dto.setPesoLiquido(pedido.getPesoLiquido());
        dto.setPesoBruto(pedido.getPesoBruto());
        if (item != null) {
            dto.setItem(item.getItem());
            dto.setProduto(instanceNameOu(item.getProduto()));
            dto.setCfop(item.getCfop());
            dto.setCst(item.getCst());
            dto.setCodClassTrib(item.getCodClassTrib());
            dto.setDescricaoComplementar(item.getDescricaoComplementar());
            dto.setQuantidade(item.getQuantidade());
            dto.setValorUnitario(item.getValorUnitario());
            dto.setSubTotal(item.getSubTotal());
        }
        return dto;
    }

    private String instanceNameOu(Object entidade) {
        return entidade == null ? null : metadataTools.getInstanceName(entidade);
    }

    public record ResultadoEmitirNotaSaida(boolean sucesso, String motivo, NotaSaida notaSaida) {
    }

    /**
     * Gera uma {@link NotaSaida} "de verdade" (numeração própria via
     * {@code nota_saida_seq_<codEmpresa>}, mesmo listener de qualquer nota criada pela
     * tela) a partir de um {@link PedidoVenda} já fechado. Cliente que não usa pedido de
     * venda lança a nota direto; este fluxo é só pro cliente que separa "fechar o
     * pedido" de "emitir a nota", possivelmente dias depois. De propósito NÃO emite a
     * NFe em seguida — isso continua um passo manual em {@code NotaSaidaListView}, pra
     * o operador poder gerar várias notas primeiro e emitir os NFes depois.
     * <p>
     * Espécie/série vêm de {@code Empresa.especieNfe}/{@code .serieNfe} (mesma regra de
     * {@code NotaSaidaDetailView.onInitEntity} pra uma nota nova pela tela);
     * {@code dataEmissao}/{@code dataSaida} são a data de hoje, não a data do pedido —
     * a nota nasce agora, o pedido pode ter sido fechado há dias. ICMS de cada item é
     * recalculado do zero por {@code ItemNotaSaidaEventListener} (mesmo caminho de um
     * item incluído na tela), então cfop/cst/codClassTrib do pedido só valem como
     * ponto de partida quando já vieram preenchidos lá.
     */
    @Transactional
    public ResultadoEmitirNotaSaida emitirNotaSaida(UUID pedidoVendaId) {
        PedidoVenda pedido = dataManager.load(PedidoVenda.class)
                .id(pedidoVendaId)
                .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE)
                        .add("parceiro", FetchPlan.BASE)
                        .add("natureza", FetchPlan.BASE)
                        .add("condicaoPagamento", FetchPlan.BASE)
                        .add("banco", FetchPlan.BASE)
                        .add("transportadora", FetchPlan.BASE)
                        .add("vendedor", FetchPlan.BASE)
                        .add("classTrib", FetchPlan.BASE)
                        .add("mensagem", FetchPlan.BASE)
                        .add("itens", fpItens -> fpItens.addFetchPlan(FetchPlan.BASE)
                                .add("produto", FetchPlan.BASE)))
                .one();

        if (pedido.getItens() == null || pedido.getItens().isEmpty()) {
            return new ResultadoEmitirNotaSaida(false, "Pedido sem itens — inclua ao menos um item antes de emitir a nota.", null);
        }

        Empresa empresa = utilGeralService.getEmpresa();
        if (empresa.getEspecieNfe() == null || empresa.getEspecieNfe().isBlank()
                || empresa.getSerieNfe() == null || empresa.getSerieNfe().isBlank()) {
            return new ResultadoEmitirNotaSaida(false,
                    "Espécie/série de NFe não configuradas em Empresa (aba \"Emissão NFe\").", null);
        }

        NotaSaida notaSaida = dataManager.create(NotaSaida.class);
        notaSaida.setCodEmpresa(pedido.getCodEmpresa());
        notaSaida.setDataEmissao(LocalDate.now());
        notaSaida.setDataSaida(LocalDate.now());
        notaSaida.setEspecie(empresa.getEspecieNfe());
        notaSaida.setSerie(empresa.getSerieNfe());
        notaSaida.setParceiro(pedido.getParceiro());
        notaSaida.setNatureza(pedido.getNatureza());
        notaSaida.setCondicaoPagamento(pedido.getCondicaoPagamento());
        notaSaida.setBanco(pedido.getBanco());
        notaSaida.setTransportadora(pedido.getTransportadora());
        notaSaida.setModFrete(pedido.getModFrete() != null ? pedido.getModFrete() : empresa.getModFretePadrao());
        notaSaida.setVendedor(pedido.getVendedor());
        notaSaida.setClassTrib(pedido.getClassTrib());
        notaSaida.setMensagem(pedido.getMensagem());
        notaSaida.setComplementoMensagem(pedido.getComplementoMensagem());
        notaSaida.setFrete(pedido.getFrete());
        notaSaida.setSeguro(pedido.getSeguro());
        notaSaida.setDespesas(pedido.getDespesas());
        notaSaida.setDesconto(pedido.getDesconto());
        notaSaida.setPesoLiquido(pedido.getPesoLiquido());
        notaSaida.setPesoBruto(pedido.getPesoBruto());

        List<ItemNotaSaida> itens = new ArrayList<>();
        for (ItemPedidoVenda itemPedido : pedido.getItens()) {
            ItemNotaSaida item = dataManager.create(ItemNotaSaida.class);
            item.setNotaSaida(notaSaida);
            item.setProduto(itemPedido.getProduto());
            item.setCfop(itemPedido.getCfop());
            item.setCst(itemPedido.getCst());
            item.setCodClassTrib(itemPedido.getCodClassTrib());
            item.setDescricaoComplementar(itemPedido.getDescricaoComplementar());
            item.setQuantidade(itemPedido.getQuantidade());
            item.setValorUnitario(itemPedido.getValorUnitario());
            itens.add(item);
        }
        notaSaida.setItens(itens);

        // @Composition não implica cascade automático de DataManager.save() sozinho — cada
        // filho entra explícito no mesmo SaveContext (mesmo padrão de NfeImportService.salvar).
        SaveContext saveContext = new SaveContext();
        saveContext.saving(notaSaida);
        for (ItemNotaSaida item : itens) {
            saveContext.saving(item);
        }
        dataManager.save(saveContext);

        // Reload: numero (NotaSaidaEventListener) e valor/valorMercadoria/baseIcms/valorIcms
        // (NotaSaidaService.atualizarValoresCalculados, disparado pelo EntityChangedEvent de
        // cada item) são calculados por listeners depois do saveContext.saving(notaSaida) —
        // a variável local usada ali é o argumento pré-save, não confiável pro retorno.
        NotaSaida notaSaidaGerada = dataManager.load(NotaSaida.class)
                .id(notaSaida.getId())
                .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE))
                .one();
        return new ResultadoEmitirNotaSaida(true, null, notaSaidaGerada);
    }
}
