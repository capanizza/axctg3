# Reforma Tributária — regra de precedência do cClassTrib (IBS/CBS)

Registrado em 2026-08-17, a partir de uma discussão conceitual sobre IBS/CBS (Imposto
Seletivo ficou de fora de propósito). Não é uma auditoria do que já existe no código —
é a decisão de design pra o que falta implementar. Se este arquivo e o código
divergirem, o código é que está desatualizado em relação à decisão, não o contrário.

## Contexto: onde o cClassTrib já vive hoje

- `ClassTrib` (`entity/tabelas/ClassTrib.java`) — tabela de referência global (sem
  `codEmpresa`), populada por import do JSON oficial
  (`classificacoes-tributarias-09-08-2026_09-57-33.json`, na raiz do projeto).
  `codigo` tem 6 dígitos e é **único** — conferido nos 161 registros do JSON (zero
  duplicata) e reforçado por `@UniqueConstraint` no banco. `cst` é derivado no import
  como os 3 primeiros dígitos do `codigo` — não é um dado independente, é o mesmo
  código lido numa granularidade mais grossa.
- `NaturezaOperacao.codClassTrib` (`entity/fiscal/NaturezaOperacao.java`) — Integer de
  6 dígitos, hoje nullable (pendente de migração do cadastro existente).
- `NfeItem.codClassTrib` (`entity/fiscal/NfeItem.java`) — String de 6 dígitos, **por
  item**, snapshot fiel do que veio no XML importado (nota de fornecedor). Reflete o
  leiaute oficial da NT 2025.002-RTC, onde o cClassTrib é declarado por item, não por
  natureza.
- `ItemNotaSaida` e `Produto` — **ainda não têm** campo de cClassTrib. É o que falta
  modelar, coberto abaixo.

Ou seja: no lado de **importação** (nota recebida de terceiro) o cClassTrib já é
granular por item, fielmente. No lado de **emissão** (nota própria), hoje só existe no
nível da natureza — e é exatamente essa granularidade insuficiente que motivou a
decisão abaixo.

## Distribuição dos 161 códigos por grupo de CST

Levantado a partir do JSON oficial, pra confirmar a semântica do CST:

| CST | Qtd | Com redução? | Exemplo |
|---|---|---|---|
| **000** | 5 | nenhum | **Tributação integral** (sem benefício algum) |
| 010 | 2 | não | Alíquotas uniformes (FGTS) |
| 011 | 5 | sim | Alíquotas uniformes reduzidas 60% |
| 200 | 54 | sim (todos) | Alíquota zero |
| 221 | 4 | não | Alíquota fixa proporcional |
| 222 | 1 | não | Redução de base de cálculo |
| 400 | 2 | não | Isenção |
| 410 | 38 | não | Imunidade e não incidência |
| 510/515 | 2 | 1 sim | Diferimento |
| 550 | 25 | não | Suspensão |
| 620 | 7 | não | Monofásico (combustíveis) |
| 800+ | 16 | não | Transferência de crédito, ajustes ZFM, etc. |

CST **000** é isoladamente o grupo "tributação plena, sem nenhum tratamento
diferenciado" — todos os outros 15 grupos representam algum benefício/regime especial
(redução, isenção, imunidade, diferimento, suspensão, monofásico...).

## Regra de precedência decidida

Quando a operação **não é devolução**:

1. Resolve `NaturezaOperacao.codClassTrib` → busca o `ClassTrib` correspondente → olha
   o `cst` (primeiros 3 dígitos).
2. **CST = 0** ("venda rasa" — natureza genérica de venda, sem benefício fixado nela)
   → prevalece o `codClassTrib` do **Produto** do item. É o caso em que a mesma
   natureza (mesmo CFOP) mistura itens com tratamento tributário diferente — ex.:
   mercadoria comum e item de cesta básica na mesma nota.
3. **CST ≠ 0** ("natureza especial" — ex.: exportação, substituição tributária,
   isenção específica etc.) → prevalece o `codClassTrib` da **NaturezaOperacao**,
   **ignora** o produto completamente. A natureza especial é fixa por definição da
   operação, não varia por item.

Quando a operação **é devolução**: tratamento à parte, fora dessa cascata — o
cClassTrib deve **espelhar o da operação original** (a venda ou compra que está sendo
revertida), não usar nem o valor da natureza de devolução nem o do produto isolado.
Ver "O que falta modelar/implementar" abaixo — hoje não existe modelagem pra isso.

## Por que `ItemNotaSaida.codClassTrib` guarda só o código (6 dígitos), não CST + código

O CST não é informação adicional a concatenar na frente do cClassTrib — ele já está
embutido como os 3 primeiros dígitos dos 6. Guardar CST + cClassTrib como 9 dígitos
duplicaria os mesmos 3 dígitos duas vezes, com risco de os dois ficarem
inconsistentes entre si sem ganhar nenhuma informação nova. Isso vale pro valor
resolvido gravado em `ItemNotaSaida.codClassTrib` (snapshot, `Integer` de 6 dígitos,
sem campo `cst` próprio) — ver "Status" abaixo pra como `NaturezaOperacao`/`Produto`
guardam o cClassTrib de cadastro (mudou de Integer solto pra referência real).

## Status

Implementado em 2026-08-17, em duas passadas:

1. Changelog `.../17-085140-...-produto-itemnotasaida-codclasstrib-adicionar.xml`:
   `Produto.codClassTrib` e `ItemNotaSaida.codClassTrib` como `Integer` solto (decisão
   original deste doc), com a regra de precedência em
   `ItemNotaSaidaEventListener.resolverCodClassTrib`.
2. Changelog `.../17-091725-...-natureza_operacao-produto-classtrib-fk-vincular.xml`:
   **revisão** da decisão de "Integer solto" pra `NaturezaOperacao`/`Produto` — viraram
   `@ManyToOne` real pra `ClassTrib` (campo renomeado `codClassTrib` → `classTrib` nos
   dois), depois de conferir que `ContaContabil.contaReferencial` já é o precedente do
   projeto pra esse exato cenário (tabela de referência grande, importada em bulk,
   escolhida na tela) — a alegação de "mesmo estilo Integer solto do resto do projeto"
   no texto original abaixo não se sustentou. `ItemNotaSaida.codClassTrib` **não**
   mudou: continua `Integer` (snapshot congelado), mesmo motivo de
   `NfeItem.codClassTrib` ser String e não FK — um reimport futuro do `ClassTrib` não
   pode alterar retroativamente o que um item já gravado enxerga.

Detalhe de implementação não coberto no texto original: natureza sem `classTrib` ainda
(cadastro não migrado) é tratada como CST 000 na precedência — mesmo caminho do
produto decidir.

Continuam pendentes: a referência à operação original em devolução, e um plano de
backfill formal para qualquer ambiente que já tenha linhas em `PRODUTO` sem
`classTrib` (o dev Postgres deste projeto tinha 0 linhas na tabela e 412 em
`NATUREZA_OPERACAO` — nenhuma com o código antigo preenchido — no momento da
migração, então não houve dado a migrar).

## O que falta modelar/implementar

- ~~**`Produto.codClassTrib`**~~ — feito, ver Status acima.
- ~~**Resolução congelada no item, não lida ao vivo**~~ — feito, ver Status acima.
- **Referência à operação original em devolução** — hoje **não existe** nenhum campo
  de "nota de origem" em `NotaSaida`/`NotaEntrada`. Precisa decidir e modelar:
  - Devolução de venda (cliente devolve mercadoria) → referenciar
    `NotaSaida`/`ItemNotaSaida` original emitida pela empresa.
  - Devolução de compra (empresa devolve ao fornecedor) → referenciar
    `Nfe`/`NfeItem` original importado do fornecedor.
  - São dois fluxos com direção e fonte de dados diferentes; confirmar se os dois
    entram no escopo agora ou só um.
- **Migração do cadastro de produtos existente** — tornar `codClassTrib` obrigatório
  no `Produto` empurra a mesma pendência de migração que `NaturezaOperacao` já tem
  documentada (NFe não autorizada sem o código desde 03/08/2026), só que numa base
  tipicamente bem maior. Vale um plano de backfill antes de travar a obrigatoriedade
  em produção.

## Onde ficam as alíquotas básicas de IBS/CBS/IS

Pergunta separada da precedência de cClassTrib, decidida em 2026-08-17: **não** em
`Empresa`. CBS é alíquota única nacional; IBS (UF+Município) também é fixo
nacionalmente durante a fase de teste (2026) e, no regime pleno, é imposto de
**destino** (a alíquota é a de quem compra, não a de quem vende) — em nenhum dos dois
casos é "da empresa emissora". IS nem tem uma alíquota básica: é por produto/NCM, como
o IPI, e a lei que vai definir os valores ainda não existe.

Cogitou-se modelar regime tributário (Simples Nacional trata IBS/CBS diferente) pra
justificar variação por empresa, mas decidiu-se **não** entrar nesse escopo agora —
o projeto não modela regime tributário em lugar nenhum hoje.

Implementado: `AliquotaIbsCbs` (`entity/tabelas/AliquotaIbsCbs.java`) — tabela global
(sem `codEmpresa`, como `ClassTrib`), uma linha por ano civil (`ano` + `aliqIbsUf` +
`aliqIbsMun` + `aliqCbs`), casando com o cronograma de transição da EC 132/2023 (2026
teste → 2033 regime pleno). Seed inicial: 2026 com IBS 0,05%+0,05% / CBS 0,90% (changelog
`liquibase/changelog/2026/08/17-094409-c1f40fd1-aliquotaibscbs-criar.xml`). CRUD simples
em `Tabelas > Alíquotas IBS/CBS por ano`.

**Só informativo por enquanto** — não é lida automaticamente por `NaturezaOperacao`
nem `Produto`; continuam com `aliqIbsUf`/`aliqIbsMun`/`aliqCbs` preenchidos à mão por
natureza. Auto-preencher a partir desta tabela ao criar uma nova `NaturezaOperacao` é
uma decisão em aberto, não implementada.

## Fora de escopo desta decisão

- Imposto Seletivo (IS) — tratado à parte, não entrou nesta discussão.
- Variação de cClassTrib por **destinatário** dentro de uma mesma natureza "rasa" (ex.:
  mesmo produto, tratamento diferente pra entidade imune vs. consumidor comum) — a
  regra atual resolve a variação por produto, não por quem compra. Não avaliado ainda
  se é necessário cobrir esse eixo.
