package br.com.axialsoftware.axctg3.service.fiscal;

import br.com.axialsoftware.axctg3.entity.fiscal.ItemPedidoVenda;
import br.com.axialsoftware.axctg3.entity.fiscal.PedidoVenda;
import br.com.axialsoftware.axctg3.entity.fiscal.PedidoVendaDto;
import br.com.axialsoftware.axctg3.service.RelatorioService;
import br.com.axialsoftware.axctg3.service.UtilGeralService;
import io.jmix.core.DataManager;
import io.jmix.core.FetchPlan;
import io.jmix.core.Messages;
import io.jmix.core.MetadataTools;
import net.sf.jasperreports.engine.JRDataSource;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

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
        String nomeRelatorio = "PedidoVenda.jasper";
        String nomeSaida = "PedidoVenda.pdf";

        var parametros = new HashMap<String, Object>();
        parametros.put("TITULO_RELATORIO", "Listagem dos pedidos de venda");
        parametros.put("NOME_EMPRESA", utilGeralService.getNomeEmpresa());
        parametros.put("LOGO", utilGeralService.getLogoEmpresa());

        List<PedidoVendaDto> pedidosDto = prepararPedidos();

        JRDataSource dataSource = new JRBeanCollectionDataSource(pedidosDto);

        relatorioService.emitirRelatorio(nomeRelatorio, dataSource, parametros, nomeSaida);
    }

    private List<PedidoVendaDto> prepararPedidos() {
        Integer codEmpresa = utilGeralService.getCodEmpresa();
        List<PedidoVenda> pedidos = dataManager.load(PedidoVenda.class)
                .query("select e from PedidoVenda e where e.codEmpresa = :codEmpresa order by e.numero")
                .parameter("codEmpresa", codEmpresa)
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
}
