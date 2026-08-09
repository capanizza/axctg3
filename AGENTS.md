# AGENTS.md

This file provides guidance to coding agents when working with code in this repository.

Axctg3 is a Brazilian multi-company accounting application (contabilidade) built on
Jmix 3 / Spring Boot 4 / Vaadin 25, Java 21, Gradle, PostgreSQL + Liquibase.
Pinned in `build.gradle`: `io.jmix` plugin and `bomVersion` `3.0.1`; Vaadin `25.1.3`
(see `package.json`). Hilla/Copilot are excluded from `configurations.implementation`.

`CLAUDE.md` and `.junie/guidelines.md` hold the same guidance for other tools. These
three files are kept identical apart from this header — mirror any change to all three.

## Commands

```bash
./gradlew compileJava                 # fast syntax/API check — BLIND to *-view.xml defects
./gradlew clean test                  # Gate 2: boots the Spring/Jmix context + Liquibase, then EXITS
./gradlew test --tests "br.com.axialsoftware.axctg3.user.UserUiTest"                 # one class
./gradlew test --tests "br.com.axialsoftware.axctg3.user.UserUiTest.test_createUser" # one method
./gradlew bootRun                     # http://localhost:8085/axctg3 — admin/admin
```

- Dev DB: PostgreSQL `jdbc:postgresql://localhost/axctg3` (postgres/root). Liquibase
  runs on every startup from `br/com/axialsoftware/axctg3/liquibase/changelog.xml`.
- Tests use a file-backed HSQLDB at `.jmix/hsqldb/axctg3_test` (`@ActiveProfiles("test")`).
- NEVER use `bootRun` as a verification gate — it does not exit and will hang the turn.
  Gate 2 is `clean test`. See below for the cases where starting the app IS the point.

### Starting the app on purpose (render walk, or migrating the dev Postgres)

Two things genuinely require a running app: a Gate-3 render walk, and applying a
changelog to the dev Postgres — `clean test` only ever migrates HSQLDB, so a changeset
scoped `dbms="postgresql"` is never exercised by the test suite.

**There is no actuator on the classpath**, so do not poll `/actuator/health` — it 404s.
Wait on the startup line that `Axctg3Application` logs from its `ApplicationStartedEvent`
listener:

```bash
cmd.exe /c "gradlew.bat --no-daemon bootRun" > /tmp/bootrun.log 2>&1 &
# aguarde:  "Started Axctg3Application in N seconds" / "Application started at http://..."
# ou pare cedo em:  "APPLICATION FAILED TO START" / "BUILD FAILED"
```

Shut it down by matching the main class — matching on `axctg3` alone also hits the
IntelliJ process that has the project open:

```bash
powershell.exe -NoProfile -Command "Get-CimInstance Win32_Process -Filter \"Name='java.exe'\" | Where-Object { \$_.CommandLine -match 'Axctg3Application' } | ForEach-Object { Stop-Process -Id \$_.ProcessId -Force }"
```

Then confirm port 8085 is free before reporting the app stopped.

To inspect the dev Postgres directly, `psql` is at
`C:\Program Files\Postgresql\16\bin\psql.exe`. Quoting it through `cmd.exe /c` fails —
write a small `.bat` under `%TEMP%` and run that instead. A booted app proves only that
Liquibase did not throw; query `pg_indexes` / `information_schema.columns` to confirm the
schema actually changed.

### Running the build from a WSL shell

There is **no JDK inside WSL** — Java lives only on the Windows side
(`C:\Program Files\Eclipse Adoptium\jdk-21.0.9.10-hotspot`, with `JAVA_HOME` set in the
Windows environment). A bare `./gradlew` from WSL dies with
`ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH`.

The project sits at `C:\projetos\jmix\axctg3`, so drive the Windows wrapper instead:

```bash
cmd.exe /c "gradlew.bat --no-daemon clean test"
```

**Do not pipe the result through `tail`/`tr`** — the pipeline's exit status is the last
command's, so a failed build reports exit 0 and reads as green. Redirect, then check:

```bash
cmd.exe /c "gradlew.bat --no-daemon clean test" > /tmp/gradle.log 2>&1; echo "EXIT=$?"
```

Either way, confirm `BUILD SUCCESSFUL` in the output before calling a gate passed.

## Architecture

### Company + period is application state carried on `User`

There is no Jmix multi-tenancy addon here. `User` carries `codEmpresa`, `anoContabil`,
`mesContabil` (and `anoFiscal`/`mesFiscal`), and **every query filters on those columns
by hand**. Entities store a plain `Integer codEmpresa` — not a FK to `Empresa`.

`UtilGeralService` is the single accessor for this context (`getCodEmpresa()`,
`getAnoContabil()`, `getMesContabil()`, `getMascContabil()`, `getNomeEmpresa()`,
`getLogoEmpresa()`, `prepararDatas()`). Go through it — do not read
`CurrentAuthentication` for these values in new code.

`SelecionarEmpresaListView` switches company/period. It writes the **persisted** `User`
row *and* mutates the in-memory principal (so the header updates), then calls
`UI.getCurrent().getPage().reload()`. Follow that pattern if you add another switcher.

### Reports: view action → MenuBean → service → JasperReports

`MenuBean` (`@Component("MenuBean")`) is the entry point for every report. Today every
call site is a **list-view action**, not a menu item — `ContaContabilListView`
(`listarContasContabeis`, `listarBalancete`) and `LancamentoListView`
(`listarLancamentos`, `listarRazao`). `jmix.ui.composite-menu=true` is set, so `menu.xml`
*could* invoke bean methods, but no item currently does; don't assume a report is
reachable from the menu without grepping `menu.xml`.

The pipeline:

1. `UtilGeralService.prepararConfigRel()` loads-or-creates the caller's `ConfigRel` —
   one row per user holding last-used report filters (dates, account ranges, grau).
2. An `InputDialog` (`io.jmix.flowui.Dialogs`) prefilled from `ConfigRel`.
3. On OK: write the values back to `ConfigRel`, save, then call the domain service.
4. The service builds a `List<XxxDto>` and hands it to
   `RelatorioService.emitirRelatorio(...)` as a `JRBeanCollectionDataSource`.

`RelatorioService` resolves templates from `<user.dir>/relatorios/*.jasper` — **on disk,
not on the classpath**, with the file name passed in full (`"Balancete.jasper"`). A report
added to `relatorios/` needs no build step, but the process working directory must be the
project root. The filled report goes to the browser through the Jmix `Downloader` as
`DownloadFormat.PDF` — nothing is written to disk.

`emitirRelatorio` swallows every exception into `log.info(e.getMessage())`, so a missing
template or a bean/field mismatch produces **no error in the UI** — just no download.
When a report "does nothing", read the app log before touching the code.

The `*Dto` classes in `entity/contabil` (`BalanceteDto`, `RazaoDto`, `LancamentoDto`,
`ContaContabilDto`) are non-persistent `@JmixEntity` beans with `@JmixId
@JmixGeneratedValue UUID id`. They serve double duty as Jasper beans and UI-bindable
model.

### Accounting posting and balance rollup

`LancamentoEventListener` is where the accounting rules live, not the view:

- `EntitySavingEvent` (new entry): stamps `codEmpresa`/`ano`/`mes`/`dia`/`dataLancamento`
  from the session context, and takes `numero` from a **per-period Jmix `Sequence`**
  named `lancamento_seq_<yyyy><MM>` (`Sequences.createNextValue`).
- `EntityChangedEvent`: `CREATED` → `LancamentoService.atualizarSaldos()`;
  `UPDATED` → `excluirSaldosAnteriores()` first (reverse the old amounts), then re-apply;
  `DELETED` → reverse only.

`atualizarSaldos` walks the chart of accounts **upward** via `ContaContabil.codContaSup`
and `grau`, updating a `SaldoConta` row per month on every ancestor account. Any change
to posting logic must keep debit/credit reversal symmetric or balances silently drift —
this is the highest-risk area in the codebase.

`ContaContabil` owns `List<SaldoConta>` as `@Composition` + `@OnDelete(CASCADE)`;
`SaldoContaService.criarSaldos()` seeds the 12 monthly rows for a new account.

`LancamentoTmp` is a staging table for entries imported from the legacy system — same
shape as `Lancamento`, unique index on `NUMERO, ANO, MES, COD_EMPRESA`. It has no view:
`LancamentoService.importarLancamentos()` reads it and posts real `Lancamento` rows, fired
from `lancamentoesDataGrid.importarAction` in `LancamentoListView`. An earlier
`BackgroundTask` + progress-dialog version of that flow is still in the file as a large
commented-out block — read it before rewriting the import, and delete it if you replace it.

### Enum convention (SPED/ECD numeric codes)

Enums implement `EnumClass<Integer>` with a static `fromId`. The entity field is declared
as a raw `Integer` column and converted in the getter/setter:

```java
@Column(name = "COD_NAT", nullable = false) private Integer codNat;
public CodNat getCodNat() { return codNat == null ? null : CodNat.fromId(codNat); }
public void setCodNat(CodNat v) { this.codNat = v == null ? null : v.getId(); }
```

The integer IDs are fiscal codes mandated by SPED/ECD — never renumber them.

### Layout

Java under `src/main/java/br/com/axialsoftware/axctg3/`: `entity/`, `entity/enums/`,
`view/`, `service/`, `bean/`, `listener/`, `security/`. Domain modules split each of
those: `tabelas`, `cadastros`, `contabil`, `financeiro`, `fiscal`, `almoxarifado`,
`compras`.

View XML mirrors the Java package under
`src/main/resources/br/com/axialsoftware/axctg3/view/<modulo>/<entidade>/`.

A new view is only reachable once it is added to `menu.xml`, whose four groups are
`application` (working set: select company, chart of accounts, entries), `contabil`,
`administracao` and `tabelas`. Items reference the **view id** (`Lancamento.list`), never
the class, and their `title` is a `msg://<view.package>/<viewId>.title` key that must exist
in both bundles — a typo there renders the raw `msg://…` string in the menu.

i18n is **one pair of bundles** for the whole app —
`src/main/resources/br/com/axialsoftware/axctg3/messages_{en,pt_BR}.properties`, not
per-package bundles. Both files must stay key-for-key identical. Keys are
`<entity.package>/<Entity>.<attr>` and `<view.package>/<viewId>.<element>`.

Themes `axctg3-aura` (active — wired via `@StyleSheet` on `Axctg3Application`) and
`axctg3-lumo` live in `src/main/resources/META-INF/resources/themes/`.
Never edit `src/main/frontend/generated/` — regenerated every build.

### Test harness — two flavors, both `@ActiveProfiles("test")`

The suite is **two sample classes only** (`user/UserTest`, `user/UserUiTest`); a green
`clean test` therefore proves the context boots, not that any feature works.

- **Data/service test** — `@SpringBootTest` + `@ExtendWith(AuthenticatedAsAdmin.class)`
  (`test_support/AuthenticatedAsAdmin` wraps each test in `SystemAuthenticator.begin("admin")`).
  Needed for anything touching `DataManager`; without it, saves fail unauthenticated.
- **UI test** — `@UiTest` +
  `@SpringBootTest(classes = {Axctg3Application.class, FlowuiTestAssistConfiguration.class})`.
  Drive views with `ViewNavigators` + `UiTestUtils.getCurrentView()` and grab components by
  their descriptor `id` via `UiTestUtils.getComponent(view, "usernameField")` — so a test is
  the cheapest way to catch the XML defects `compileJava` misses.

Beware the shared file-backed HSQLDB at `.jmix/hsqldb/axctg3_test`: state survives between
runs, so tests clean up after themselves in `@AfterEach`.

`UtilGeralService` reads company/period straight off the principal, and changelog `020`
leaves those columns **nullable and unseeded** — under `AuthenticatedAsAdmin` they are
`null`. Any test that exercises a listener or service touching `getCodEmpresa()` /
`getAnoContabil()` must set them on the `admin` `User` row first. The accessors return
`null` quietly; the failure lands downstream and looks unrelated — `getMascContabil()`
throwing on `.one()` with no `Empresa`, or a `ConstraintViolationException` on the
`@NotNull COD_EMPRESA` of the row being saved.

## Project conventions (obrigatórias)

### Entidades

- **Soft delete em todas as entidades.** Campos `deletedBy` (`@DeletedBy`) e
  `deletedDate` (`@DeletedDate`) mais o quarteto de auditoria
  `createdBy`/`createdDate`/`lastModifiedBy`/`lastModifiedDate`.
- Por causa do soft delete, chave única **nunca** por `@UniqueConstraint` — sempre
  `@Index(name = "IDX_<TABELA>_UNQ", columnList = "...", unique = true)`.
- Booleanos recebem valor padrão `false` na declaração do campo
  (`private Boolean analitica = false;`).
- Campos monetários: `BigDecimal`, `precision = 19, scale = 2`, com
  `@NumberFormat(pattern = "###,###,##0.00", decimalSeparator = ",", groupingSeparator = ".")`.

### Layout de formulário em detail views

**Não usar `formLayout`** (o componente que o Jmix Studio gera por padrão). Ele monta um
grid de colunas *iguais* via `responsiveSteps` — cada campo ocupa uma célula inteira,
então um `integerField` de código sai do mesmo tamanho que um `textField` de nome longo
("campos gigantes"). Dá pra um campo ocupar mais de uma coluna (`colspan`), mas é
discreto (colunas inteiras), não fração de largura — não dá pra reproduzir uma linha tipo
código 10% / nome 45% / cfop 15% / venda 10%.

O padrão do projeto é montar o layout na mão: um `vbox` externo com várias `hbox`
internas (uma por linha), cada campo com `width` em `%` explícito e `themeNames="small"`.
Mais XML, mas dá controle fino de proporção e bate com a densidade esperada de uma tela
de sistema contábil. Ver `view/financeiro/banco/banco-detail-view.xml` ou
`view/fiscal/naturezaoperacao/natureza-operacao-detail-view.xml` como referência antes de
escrever uma nova detail view — **todas** as detail views do projeto seguem esse padrão,
não misturar com `formLayout` numa tela nova.

### Changelogs Liquibase

Padrão Jmix: `changelog/<ano>/<mês 2 dígitos>/dd-hhMMss<cccccccc>-<descrição>.xml`.
`cccccccc` é o código do desenvolvedor — neste projeto, `c1f40fd1`. Na descrição, o
nome da entidade e a ação: `empresa-criar`, `lancamento_tmp-criar`.

O master `liquibase/changelog.xml` usa `<includeAll path=".../liquibase/changelog"/>`:
todo arquivo colocado ali roda automaticamente, **em ordem alfabética de caminho** — é o
nome datado que garante a ordem, não uma lista de `<include>`. Não há changelog a editar
ao criar um changeset; e um XML mal formado nessa pasta quebra a subida inteira.

O master também declara `offsetDateTime.type` para `postgresql` **e** para `hsqldb`. Sem a
linha do hsqldb, `${offsetDateTime.type}` não é substituído e os testes falham com
`Unknown JDBC escape sequence: {`. Ao usar uma nova propriedade de tipo, declare-a para os
dois bancos.

### Cópia dos projetos legados

`axctg-flow` e `axctg3-salvo` ficam "ao lado" da pasta `axctg3`. Ao copiar:

- **Entidades:** manter a subpasta de origem. **Listeners** → `listener/`,
  **services** → `service/`, e assim por diante. Criar a pasta de destino se não existir.
- **Views:** na origem não há subpastas — copiar para a subpasta correspondente à da
  entidade.
- O legado não tinha soft delete: acrescentar `deletedBy`/`deletedDate` no destino e
  converter chaves únicas para índice `unique = true`.

## Verification discipline

A task is not done when it compiles. Three gates, in order; never claim a gate passed
without showing the evidence.

| Gate | Primary (MCP, if connected) | Fallback (always available) |
|---|---|---|
| 1. API & static | Context7 (`/jmix-framework/jmix-context7`) for every Jmix/Vaadin symbol before typing it, plus the IDE inspection (`get_file_problems`) on every file written — the only static catch for unresolved `msg://`, invalid property paths, missing data containers in `*-view.xml` | `./gradlew compileJava` |
| 2. Context loads | *(no MCP substitute)* | `./gradlew clean test` |
| 3. Render | Playwright render-walk of every new view/button/field — no error overlay, no server exception, no raw `msg://` caption | none; state plainly `render not browser-verified` |

`compileJava` is blind to XML descriptors: a field bound to the wrong reference, a broken
`itemsQuery`, or an action opening a nonexistent view id (`NoSuchViewException`) all
compile clean. A green `clean test` boots the context but does **not** open your views,
exercise your roles, or fire your listeners — if nothing enters the code path, add a
caller or report it unverified.

In the completion report, give a per-file static verdict and, per new view/button/field,
how it was verified. When Gate 1 fell back to `compileJava`, name those files as
inspected-by-compile-only so the next session re-checks them.

### Verify a symbol before you type it

Inventing plausible Jmix/Vaadin API names is the top failure mode. Before typing any
symbol not already used in this project's `src/`: check Context7, else an IDE symbol
search, else grep this repo for a working call site and copy it.

Reference implementations already in the tree, worth reading before writing a new one:

| Artifact | Read |
|---|---|
| Entity (soft delete, audit, composition, enum column) | `entity/contabil/ContaContabil.java` |
| Non-persistent DTO | `entity/contabil/RazaoDto.java` |
| Enum | `entity/enums/CodNat.java` |
| Entity lifecycle logic | `listener/contabil/LancamentoEventListener.java` |
| Service + report emission | `service/contabil/ContaContabilService.java` |
| List view with custom renderer, side panel, dialogs | `view/cadastros/empresa/SelecionarEmpresaListView.java` |
| Menu-invoked bean + InputDialog | `bean/MenuBean.java` |
| Resource role | `security/FullAccessRole.java` |
| UI integration test | `src/test/java/.../user/UserUiTest.java` |
| Changelog | `liquibase/changelog/2026/08/02-231248-c1f40fd1-empresa-criar.xml` |

Service- or listener-level defaulting does NOT relieve an entity from defaulting its
required fields on initial persist — defaults must hold through `DataManager.create()` +
`DataManager.save()`, because tests bypass the view layer entirely.

### When tests go red, you broke them

Do not label a newly red `clean test` "pre-existing." Usual causes:

- **`NoSuchViewException`** after adding views → the view registry is poisoned and
  navigation to *every* view breaks. Check: each view `.java` has a `package` line
  matching its directory; no duplicate `@ViewController(id=…)`; every `@ViewDescriptor`
  path resolves; no empty/malformed `*-view.xml` (an empty descriptor throws
  `SAXParseException: Premature end of file`).
- **`MetaClass not found for class X`** → missing `@JmixEntity`, or the package sits
  outside the scan root `br.com.axialsoftware.axctg3`.
- **`ConstraintViolationException` on save** → a `@NotNull` field has no value on the
  `DataManager` path.

### File-write trap

Always pass absolute paths to file-writing tools. After a batch of writes, `ls` the
target and confirm each file is non-empty — a 0-byte write survives both `compileJava`
and `clean test` (an empty role class silently drops all its policies; an empty
`*-view.xml` poisons the registry). If a file is missing or empty, rewrite it; do not
`rm -rf` to "clean up".

## Known rough edges

- `RelatorioService.emitirRelatorio2()` opens a hardcoded JDBC connection to a different
  database (`axialdb`) with inline credentials — legacy path, prefer `emitirRelatorio()`
  with a bean datasource.
- `application.properties` ships dev DB credentials and `ui.login.defaultUsername`/
  `defaultPassword`; both must go before any production deploy.
