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
- [ ] `VerificacaoDto` — **não é do módulo de bens**: pertence a
      `ContaContabilService.verificarContasContabeis()` (correção de item registrado por
      engano nesta lista). Conferir se esse método já foi portado antes de tratá-lo aqui.
- [ ] `ResumoCorrecaoDto` — usado por `DepreciacaoService.resumoCorrecaoMonetaria[2]`;
      no axctg-flow só a variante `resumoCorrecaoMonetaria()` (via `emitirRelatorio2`,
      o caminho JDBC legado pro `axialdb`) estava de fato ligada no `MenuBean` — a
      variante 2, com bean datasource, existia no serviço mas ficava comentada/morta.
      Ao portar, preferir a variante 2 (`emitirRelatorio()`), não `emitirRelatorio2`.

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
