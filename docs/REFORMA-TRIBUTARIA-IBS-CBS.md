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

## Cronograma da transição (levantado em 2026-08-20 via busca externa)

Os percentuais/datas abaixo são os vigentes em agosto/2026 (fontes: Receita Federal,
CGIBS, imprensa especializada). A lei prevê recalibração anual por resolução do
Senado — reconferir antes de travar qualquer um desses valores como constante fixa no
código.

### Alíquotas por ano

| Ano | CBS | IBS estadual | IBS municipal | Observação |
|---|---|---|---|---|
| 2026 | 0,9% (teste, compensável) | 0,10% | **0,00%** (Rejeição 321 barra nota com IBS-Mun ≠ 0) | Sem efeito líquido se cumprida a obrigação acessória |
| 2027-2028 | ~8,8-9% (alíquota cheia/referência) | 0,05% | 0,05% | CBS já com arrecadação real; IBS ainda pequeno mas real |
| 2029-2032 | referência (~8,8%) | rampa +10%/ano | rampa +10%/ano | ICMS/ISS caem na mesma proporção (90% em 2029, 80% em 2030...) |
| 2033 | referência | referência (~17,7% total IBS) | — | ICMS e ISS extintos |

### Marcos de obrigatoriedade documental (NFe/NFCe e afins)

- **03/08/2026** ✅ já em vigor — 1º lote: obrigatório destacar IBS/CBS no XML pra
  regime regular (Lucro Real/Presumido). SEFAZ rejeita a nota se o campo vier vazio —
  é a mesma data que já está registrada no comentário de `NaturezaOperacao.codClassTrib`.
- **01/10/2026** — 2º lote: NFCom, DIR.
- **01/12/2026 e 01/01/2027** — 3º lote: demais casos.
- **01/01/2027** — obrigatório também pra **todo optante do Simples Nacional**,
  híbrido ou não (a diferença entre os dois não é "quem informa", é o que o destaque
  significa em termos de crédito — ver seção do Simples abaixo).
- **Layout visual do DANFE**: ainda não publicado (situação em ago/2026). O XML já
  carrega os campos desde 03/08, mas a representação impressa não teve o leiaute final
  divulgado — só o DANFSe (NFS-e) já foi atualizado, via NT 008/2026.

### Recolhimento (pagamento)

- 2026: sem recolhimento de fato (se cumprida a obrigação acessória) — apuração
  informativa, sem efeito tributário.
- Primeira apuração real: **janeiro/2027**, saldo a pagar até o último dia útil do mês
  seguinte — **primeiro vencimento em 26/02/2027**. Modelo: apuração mensal + guia
  (DAR), igual ao que já existe hoje — não é debitado automaticamente do caixa.
- **Split Payment**: não é o mecanismo geral de recolhimento a partir de 2027. Rollout
  gradual, calibrado por setor/meio de pagamento; na etapa inicial (2027) é
  **facultativo**, voltado a operações B2B. Convive com o modelo de guia, não o
  substitui — sem urgência em modelar liquidação automática via split payment no
  axctg3 por enquanto.

### Simples Nacional — janela de decisão iminente

- Entre **01 e 30/09/2026**, empresas do Simples decidem se recolhem IBS/CBS
  **dentro do DAS** (sem apuração própria, sem repasse de crédito — regime "normal")
  ou **fora do DAS** (regime regular/"híbrido", apuração própria com débito/crédito
  pleno, vale de jan a jun/2027).
- Os dois regimes **informam** IBS/CBS na nota a partir de 01/01/2027 — isso não muda
  entre eles. A diferença é o que o destaque significa: no híbrido, crédito pleno pro
  comprador; no regime normal (DAS), o comprador só aproveita um **crédito presumido**
  (mecânica própria, ainda não sedimentada — Resolução CGSN 190/2026), não o valor
  cheio do campo destacado.

### IPI

Sem mudança em 2026. Zera a partir de **01/01/2027** (junto com a extinção de
PIS/Cofins), exceto produtos que competem com a produção da Zona Franca de Manaus
(critério: IPI < 6,5% e já produzido na ZFM em 2024, ou projeto técnico-econômico
aprovado pela SUFRAMA entre jan/2022 e a publicação da lei) — esses mantêm IPI
integralmente. Nenhuma mudança de comportamento esperada em `aliqIpi`/`valorIpi`
antes da virada pra 2027.

### Imposto Seletivo (IS)

Entra em vigor **01/01/2027**, junto com a CBS plena — sem ano de teste separado em
2026 (diferente de IBS/CBS, que tiveram 2026 como ano de calibração).

**Mas ainda depende de uma lei que não saiu** (situação em 22/08/2026): a LC 214/2025
dedicou um livro inteiro ao IS, mas remeteu a lista de produtos/serviços tributados e
as alíquotas específicas pra uma **lei ordinária separada**, ainda não publicada.
Trava constitucional da **noventena**: essa lei precisa sair até **03/10/2026** pra
que a cobrança consiga mesmo começar em 01/01/2027 — se atrasar, a cobrança efetiva
empurra pra depois dessa data. É um prazo concreto a acompanhar.

Escopo: tributo de incidência **restrita** (não é amplo como IBS/CBS) —
popularmente "imposto do pecado", desestimula consumo/produção de bens nocivos à
saúde e ao meio ambiente: cigarro, bebida alcoólica, veículos/embarcações/aeronaves
de alto impacto ambiental, extração de recursos não renováveis, entre outros a
definir na lei ordinária.

Isto é só o cronograma — a modelagem de dados do IS continua fora do escopo deste
documento (ver "Fora de escopo desta decisão" no final).

### PIS/Cofins — pergunta em aberto sobre o leiaute pós-extinção (2026-08-22)

PIS e Cofins são extintos em 31/12/2026 (CBS entra em vigor plena em 01/01/2027).
**Não encontrei confirmação oficial** de como o leiaute da NFe vai tratar o grupo de
tags PIS/COFINS a partir daí — busquei na NT 2025.002-RTC e em guias técnicos
derivados, nenhum resolve esse ponto específico. Duas hipóteses:

1. **Grupo continua existindo, com CST/valores zerados** (possivelmente um CST novo
   indicando "tributo extinto") — é o padrão histórico do leiaute da NFe quando um
   tributo não incide (mantém o grupo, zera o conteúdo, em vez de omitir a tag). É a
   hipótese mais provável por precedente, mas não confirmada.
2. **Grupo passa a ser omitido** do XML a partir de 2027.

Provavelmente só sai uma Nota Técnica específica sobre isso mais perto da virada
(dez/2026-jan/2027) — o layout do DANFE também ainda não saiu, então não seria
surpresa esse detalhe demorar igual. **Reconferir antes de codificar qualquer
comportamento definitivo.**

Na prática, `NfeItem` já está pronto pros dois cenários sem mudança estrutural: os
campos `cstPis`/`basePis`/`aliqPis`/`valorPis`/`cstCofins`/`baseCofins`/`aliqCofins`/
`valorCofins` são nullable, sem `@NotNull` — se o grupo continuar vindo zerado, eles
seguem sendo populados (com zero); se passar a ser omitido, simplesmente deixam de vir
preenchidos nas importações novas. Não têm como ser removidos da entidade de qualquer
forma, porque notas históricas (pré-2027, com PIS/Cofins reais) continuam precisando
deles.

`AliquotaIbsCbs` ganhou os campos `cstPisCofins`/`aliqPis`/`aliqCofins` (todos
nullable, sem default) só como referência preparatória — mesmo padrão "informativo,
não lido automaticamente" que já vale pra `aliqIbsUf`/`aliqIbsMun`/`aliqCbs` na
mesma entidade. `cstPisCofins` é um CST hipotético (não existe na tabela oficial
hoje); fica null pros anos anteriores a 2027, já que PIS/Cofins ainda vigentes têm
alíquotas reais por regime que não cabem num "valor único de referência por ano".
Nenhuma lógica de emissão lê esses campos ainda — só a modelagem. **Mesma ressalva
acima: reconferir contra a NT oficial antes de usar em emissão real.**

### Substituição Tributária (ICMS-ST)

Atrelada ao próprio ICMS — **não existe uma "ST" genérica no sistema novo** (a
não-cumulatividade plena em cada elo dispensa a lógica de concentrar a cobrança num
ponto só). Cronograma: plena até 2028, cai na mesma rampa de 10%/ano do ICMS entre
2029-2032, extinta em 2033. Durante 2029-2032 uma mesma nota carrega ICMS-ST
(minguando) e IBS/CBS (crescendo) ao mesmo tempo — os campos de ST em
`NfeItem`/`ItemNotaSaida` (`baseIcmsSt`, `aliqIcmsSt`, `valorIcmsSt`,
`percMvaIcmsSt`, `baseSt`, `valorSt`) **não podem ser aposentados antes de 2033**.

**Exceção**: combustíveis mantêm um mecanismo parecido em espírito — tributação
monofásica (retenção concentrada em refinarias/formuladores/importadores), já
identificado como fora de escopo do `NfeItem` atual (`gIBSCBSMono`).

## Base de cálculo do IBS/CBS

Não é o valor "raso" do produto — é o **valor da operação** (total cobrado do
adquirente):

- **Entra**: valor do bem/serviço, frete, seguro, juros/encargos cobrados do cliente,
  outras despesas acessórias (`vOutro` no XML / `NfeItem.valorOutro` /
  `ItemNotaSaida.despesas` — embalagens especiais, montagem/instalação vendida junto,
  taxas de manuseio, comissões repassadas, encargos financeiros já embutidos no
  preço; em importação: taxa SISCOMEX, AFRMM, diferença de peso/classificação
  fiscal, multas aduaneiras).
- **Reduz a base**: desconto **incondicional** (concedido de cara, sem depender de
  evento futuro).
- **Não reduz a base**: desconto **condicional** (depende de evento futuro, ex.:
  pagamento antecipado) — tratado como despesa/receita financeira, tributado
  normalmente. Mesma distinção que já vale hoje pra ICMS/PIS/Cofins — **conferir se
  `ItemNotaSaida.valorDesconto`/`porcDesconto` já diferenciam os dois casos**, porque
  só o incondicional pode abater a base; se for um campo genérico sem essa distinção,
  é um gap a fechar antes de montar `baseIbsCbs`.
- **Não entra**: o próprio IBS, a própria CBS (são "por fora" — não compõem a própria
  base, diferente do ICMS que é "por dentro"), IPI, e — durante a transição — os
  valores destacados de ICMS/PIS/Cofins.

## Fora de escopo desta decisão

- Imposto Seletivo (IS) — tratado à parte, não entrou nesta discussão.
- Variação de cClassTrib por **destinatário** dentro de uma mesma natureza "rasa" (ex.:
  mesmo produto, tratamento diferente pra entidade imune vs. consumidor comum) — a
  regra atual resolve a variação por produto, não por quem compra. Não avaliado ainda
  se é necessário cobrir esse eixo.
