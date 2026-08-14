# Migração axctg-flow → axctg3

Checklist de acompanhamento do roteiro de migração. Levantado em 2026-08-13 comparando
diretórios de entidade/view entre `axctg-flow` (fonte, incompleta) e `axctg3` (destino).

Contexto completo do roteiro (histórico dos legados Delphi, ordem de prioridades,
decisão de adiar ECD/Sped Contábil) está registrado na memória do assistente —
pedir pra recontar se precisar reabrir essa discussão numa sessão nova.

**Ordem do roteiro:**
1. Terminar de portar o que já existia no axctg-flow para o axctg3 (este checklist).
2. Complementar no axctg3 o que o axctg-flow ainda não tinha desenvolvido.
3. Voltar aos legados Delphi (AxCtg/AxTit/AxFiscal/AxFat) para trazer o que nenhum dos
   dois projetos Jmix tratou ainda.

**Ressalva:** este levantamento é por *presença* de entidade/view (grep de diretórios),
não confirma paridade de regra de negócio — um item marcado como portado pode ainda ter
comportamento diferente do Delphi original. É um mapa de primeira camada, não uma
auditoria funcional.

---

## ✅ Já portado (fase 1 completa nesses módulos)

- [x] **Financeiro** — Banco, MovimentoBanco, TituloPagar/TituloReceber, DiversoPagar e
      todos os Item*/Dto correspondentes
- [x] **Contábil (núcleo)** — ContaContabil, Lancamento, LancamentoTmp,
      HistoricoContabil, SaldoConta + DTOs de relatório (Balancete, Razão)
- [x] **Cadastros (núcleo)** — CentroCusto, CondicaoPagamento, ConfigRel, Empresa,
      Parceiro
- [x] **Tabelas (núcleo)** — ClassificacaoFiscal, Municipio, TipoLogradouro
- [x] **Fiscal (parcial)** — GrupoProduto, NaturezaOperacao, NotaSaida/ItemNotaSaida,
      Produto/SaldoProduto

---

## ⏳ Pendente da fase 1 (existe no axctg-flow, falta portar)

### Contábil — patrimônio/depreciação e correção monetária
- [x] `Bem`, `Depreciacao` — CRUD portado em 2026-08-13: entidades (soft delete, índice
      único em vez de unique constraint), `BemEventListener`, changelog, list view +
      detail view com depreciações inline por composição. Coberto por `BemUiTest`
      (listener, listagem, detail com composição).
- [x] Fluxos de cálculo/lançamento — portado em 2026-08-13: `DepreciacaoService`
      (`calcularDepreciacoes`/`lancarDepreciacoes`/`listarDepreciacoes`) novo em
      `service/contabil/`, 3 métodos com `InputDialog` no `MenuBean`, dropdown
      "Depreciação" (Cálculo/Lançamentos/Listagem) na `BemListView`. Diferenças reais
      em relação ao axctg-flow (não é cópia 1:1): `codEmpresa`/`ano`/`mes`/`numero`/
      `dataLancamento` do `Lancamento` gerado NÃO são setados manualmente — o
      `LancamentoEventListener` já carimba isso a partir do período contábil corrente
      (só `dia` é setado, senão o listener assume dia 1); fetch plan explícito
      (`contaContabilDepr`, `bem.contaContabilDespDepr`, coleção `depreciacaos`) porque o
      default do `DataManager` não carrega isso. Coberto por `DepreciacaoServiceTest`
      (cálculo + idempotência de não duplicar depreciação/lançamento ao rodar 2x,
      listagem sem exceção).
- [x] `BemDto` — portado junto (idêntico ao axctg-flow, sem mudança de campos).
- [x] `VerificacaoDto` + `ContaContabilService.verificarContas()` — portado em
      2026-08-14: método público `verificarContas()` chama a privada
      `verificarContasContabeis()` (2 passadas: contas com != 12 `SaldoConta`, depois
      divergência saldo x movimento por conta analítica/mês, agregando
      `Lancamento.contaDevedora`/`contaCredora` por `dataLancamento` — nomes de campo já
      batiam com o axctg3, sem adaptação de query além disso). Ligado ao botão
      "Verificar" (`contaContabilsDataGrid.verificarAction`) que já existia órfão em
      `ContaContabilListView` — só faltava o handler. Recompilado
      `VerificacaoContas.jasper` com parâmetro `LOGO` (padrão do commit `ce4a2d0`, o
      template ainda usava caminho fixo pro `logo.bmp`). Coberto por
      `ContaContabilServiceTest` (não lança exceção; a lista de `VerificacaoDto` é
      privada ao serviço, não dá pra inspecionar por fora — mesma limitação já aceita em
      `DepreciacaoServiceTest.test_listarDepreciacoesNaoLancaExcecao`).
- [x] `ResumoCorrecaoDto` + `DepreciacaoService.resumoCorrecaoMonetaria()` — portado em
      2026-08-14, só a variante 2 do legado (bean datasource via `emitirRelatorio()`),
      não a variante 1 (JDBC direto pro `axialdb` via `emitirRelatorio2`, propositalmente
      não seguida). Diferença real em relação ao legado: a query de elegibilidade
      (`taxaDepr > 0 and valorCompra > valorBaixa`) foi ajustada pra
      `(valorBaixa is null or valorCompra > valorBaixa)` — `Bem.valorBaixa` não tem
      default no `BemEventListener`, fica `null` até o bem ser baixado, então a
      comparação direta do legado excluiria todo bem ainda ativo. `ResumoCorrecao2.jrxml`
      recompilado com os `field name` renomeados pra bater com os getters do DTO
      (`codConta`/`nmConta`/`valorAtual`, antes `codigo`/`nome`/`depr_acum` — só faziam
      sentido pro `queryString` SQL direto que o bean datasource ignora) e com título/logo
      adicionados (o template não tinha `pageHeader` nenhum, os parâmetros
      `TITULO_RELATORIO`/`NOME_EMPRESA`/`PERIODO_RELATORIO` existiam mas não eram usados
      em lugar nenhum). Ligado ao dropdown "Depreciação" de `BemListView` (novo item
      "Resumo correção monetária"). Coberto por
      `DepreciacaoServiceTest.test_resumoCorrecaoMonetariaNaoLancaExcecao` (mesma
      limitação de não inspecionar a lista privada).

### Fiscal — nota de entrada
- [ ] `NotaEntrada` — **entidade já existe no axctg3, falta a view** (dá pra salvar via
      código, ninguém abre pela UI — gap silencioso)
- [ ] `ItemNotaEntrada` — nem a entidade existe ainda
- [ ] `TipoCompra` — tabela de apoio pequena, ausente

### Almoxarifado — módulo inteiro ausente
- [ ] `EntradaAlmoxarifado`
- [ ] `RequisicaoAlmoxarifado`
- [ ] `TipoProdutoAlmoxarifado`
- [ ] `MovimentoAlmoxarifadoDto`
- [ ] Views: `entradaalmoxarifado`, `requisicaoalmoxarifado`, `saldoprodutoalmoxarifado`,
      `tipoprodutoalmoxarifado`

### Compras — módulo inteiro ausente (maior bloco pendente)
- [ ] `CotacaoCompra`, `CotacaoServico`, `ItemCotacaoCompra`
- [ ] `PedidoCompra`, `PedidoCompraNota`, `PedidoServico`, `ItemPedidoCompra`
- [ ] `RequisicaoCompra`, `RequisicaoServico`, `ItemRequisicaoCompra`
- [ ] `TipoServico`
- [ ] Views auxiliares: `escolharequisicaocompra`, `escolharequisicaoservico`,
      `requisicaocompraalmoxarifado`

### Cadastros — a checar
- [ ] `ProdutoDto` — não portado; confirmar se ainda é usado em algum relatório do
      axctg-flow antes de portar, ou se ficou obsoleto

---

## 🔜 Já feito na fase 2 (novo, não vem do axctg-flow — adiantado fora de ordem)

- [x] `Vendedor`
- [x] `ClassTrib`
- [x] Módulo **NFe** completo — `Nfe`/`NfeItem`/`NfeDuplicata`/`NfePagamento`/`NfeVolume`
      + núcleo IBS/CBS/Imposto Seletivo (reforma tributária) + import de XML

---

## Explicitamente adiado (fase 3, não mexer sem sinal do usuário)

- **ECD/Sped Contábil** — adiado de propósito até o motor de lançamentos
  (`LancamentoEventListener`, rollup de `SaldoConta`) estar mais maduro. Não puxar essa
  frente mesmo sob pressão externa sem confirmar com o usuário que já chegou a hora.
