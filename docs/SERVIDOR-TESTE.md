# Servidor de teste — axctg3 em Docker no Windows 10 Home

Roteiro para rodar o axctg3 num computador separado (o "servidor"), em containers, e
praticar o ciclo de atualização: gerar versão nova → levar ao servidor → subir → voltar
versão se der errado.

**Duas máquinas:**

| Onde | O que roda | Arquivos usados |
|---|---|---|
| Máquina de desenvolvimento | gera a imagem | `Dockerfile`, `scripts\gerar-imagem.ps1` |
| Servidor (Windows 10 Home) | Postgres + axctg3 | `deploy\servidor-teste\docker-compose.yml`, `.env` |

No servidor não vai código-fonte, JDK, Gradle nem IntelliJ — só o Docker Desktop, a
imagem (um `.tar`) e dois arquivos de texto.

**O que já foi testado** (2026-09-23, na máquina de desenvolvimento, com nomes/porta
trocados para não colidir com o dev): o `gerar-imagem.ps1` gera a imagem; a pilha do
compose sobe com o profile `prod`, Vaadin em modo produção e horário de Brasília; o
Liquibase cria o banco do zero no Postgres; a restauração de um dump de dev (seção 3) e
a cópia dos arquivos funcionaram, e o app subiu de novo em cima deles.
**Não testado:** as seções 1 (Windows do servidor) e 6 (backup agendado), e o uso das
telas no navegador — nenhuma tela foi aberta.

---

## 1. Preparar o Windows do servidor (uma vez)

1. **Versão do Windows:** `Win+R` → `winver`. Tem que ser a versão 22H2 (build 19045).
   Se for mais antiga, atualize pelo Windows Update primeiro.
2. **WSL2:** PowerShell **como administrador**:
   ```powershell
   wsl --install --no-distribution
   ```
   Reinicie o computador.
3. **Docker Desktop:** baixe em https://www.docker.com/products/docker-desktop/ e instale
   com a opção *Use WSL 2* marcada. Uso pessoal ou em empresa pequena (menos de 250
   funcionários **e** menos de US$ 10 milhões de faturamento anual) é gratuito.
   Confira na página de requisitos se o Docker ainda suporta o Windows 10 na versão que
   você baixar.
4. **Docker Desktop → Settings → General:** marque *Start Docker Desktop when you sign in
   to your computer*.
5. **Limitar a memória do WSL** para ele não disputar RAM com o Windows. Crie o arquivo
   `C:\Users\<seu-usuario>\.wslconfig` com:
   ```ini
   [wsl2]
   memory=4GB
   ```
   Depois, em PowerShell: `wsl --shutdown`, e abra o Docker Desktop de novo.
6. **Login automático** (sem ele, depois de uma queda de energia o Docker não sobe até
   alguém digitar a senha): `Win+R` → `netplwiz` → desmarque *Os usuários devem digitar
   um nome de usuário e uma senha*. Se essa caixa não aparecer: Configurações → Contas →
   Opções de entrada → desligue *Exigir entrada do Windows Hello*, e abra o `netplwiz`
   de novo.
7. **Não dormir:** Configurações → Sistema → Energia e suspensão → *Suspender:
   Nunca*.
8. **Liberar a porta no firewall**, em PowerShell como administrador:
   ```powershell
   New-NetFirewallRule -DisplayName "axctg3" -Direction Inbound -Protocol TCP -LocalPort 8085 -Action Allow
   ```
9. **Anote o IP do servidor:** `ipconfig`, linha *Endereço IPv4*. Vale configurar IP
   fixo (ou reserva de DHCP no roteador), senão o endereço pode mudar.

Teste: abra um PowerShell comum e rode `docker version`. Se der "não reconhecido", use o
caminho completo `& "C:\Program Files\Docker\Docker\resources\bin\docker.exe" version`
(e o mesmo prefixo em todos os comandos abaixo).

---

## 2. Primeira instalação

### Na máquina de desenvolvimento

```powershell
cd C:\projetos\jmix\axctg3
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\gerar-imagem.ps1 -Versao 1.0.0
```

Leva uns 2 minutos e gera `C:\axctg3-imagens\axctg3-1.0.0.tar` (cerca de 240 MB). Cada
entrega precisa de uma versão nova: o script recusa uma versão que já existe.

Leve para o servidor, numa pasta `C:\axctg3` (por pendrive ou pasta compartilhada):
- `C:\axctg3-imagens\axctg3-1.0.0.tar`
- `deploy\servidor-teste\docker-compose.yml`
- `deploy\servidor-teste\.env.example`

### No servidor (PowerShell comum, na pasta `C:\axctg3`)

```powershell
cd C:\axctg3
docker load -i axctg3-1.0.0.tar            # carrega a imagem no Docker do servidor
copy .env.example .env
notepad .env                               # troque DB_SENHA; AXCTG3_VERSAO=1.0.0
docker volume create axctg3_pgdata         # dados do Postgres
docker volume create axctg3_arquivos       # FileStorage: logo, certificado A1, ...
docker compose up -d
docker compose logs -f app                 # espere "Started Axctg3Application"; Ctrl+C sai
```

A senha em `DB_SENHA` só vale na **primeira** subida, quando o Postgres cria o banco.
Mudar o `.env` depois não muda a senha do banco — nesse caso, rode
`docker exec axctg3-postgres psql -U postgres -c "ALTER USER postgres PASSWORD 'nova'"`
e só então altere o `.env` e rode `docker compose up -d`.

Acesse de qualquer computador da rede: `http://<ip-do-servidor>:8085/axctg3`.
Com o banco vazio, o Liquibase cria tudo e o login é `admin` / `admin` — troque a senha.

---

## 3. Levar a base de dev para o servidor (opcional)

Isso substitui o banco inteiro do servidor. Vão junto os usuários e senhas de dev, as
empresas e o **certificado A1**. Hoje todas as empresas de dev estão em homologação
(`ambiente_nfe = 2`). Num servidor de teste, **nunca** mude uma empresa para produção:
com o certificado real ali, a nota seria emitida de verdade.

**Na máquina de desenvolvimento:** gere um dump novo e junte os arquivos:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\backup-dev-db.ps1
```

Leve para `C:\axctg3` no servidor:
- o dump mais novo de `C:\backups\axctg3`, renomeado para `base.dump`;
- a pasta `C:\projetos\jmix\axctg3\.jmix\work\filestorage` inteira. Os campos de arquivo
  do banco (logo, certificado) apontam para esses arquivos.

**No servidor:**

```powershell
cd C:\axctg3
docker compose stop app
docker cp base.dump axctg3-postgres:/tmp/base.dump
docker exec axctg3-postgres dropdb -U postgres axctg3
docker exec axctg3-postgres createdb -U postgres axctg3
docker exec axctg3-postgres pg_restore -U postgres -d axctg3 --no-owner /tmp/base.dump
docker exec axctg3-postgres rm /tmp/base.dump
docker cp filestorage axctg3-app:/opt/axctg3/work/
docker compose start app
docker compose logs -f app
```

O `docker cp` para o `axctg3-app` funciona com o container parado. Os arquivos caem no
volume `axctg3_arquivos` e sobrevivem às atualizações.

---

## 4. Atualizar para uma versão nova (o ciclo)

**Na máquina de desenvolvimento:** gere com o próximo número, por exemplo:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\gerar-imagem.ps1 -Versao 1.0.1
```

Leve o `axctg3-1.0.1.tar` para `C:\axctg3` no servidor.

**No servidor:**

```powershell
cd C:\axctg3
powershell -NoProfile -ExecutionPolicy Bypass -File backup-dev-db.ps1   # backup ANTES (ver seção 6)
docker load -i axctg3-1.0.1.tar
notepad .env                               # AXCTG3_VERSAO=1.0.1
docker compose up -d                       # recria só o app; o Postgres continua de pé
docker compose logs -f app
```

Na subida, o Liquibase aplica sozinho os changelogs novos no banco. É por isso que o
backup vem **antes**.

## 5. Voltar para a versão anterior

```powershell
notepad .env                               # AXCTG3_VERSAO=1.0.0
docker compose up -d
```

A imagem antiga continua no servidor, então voltar leva segundos. **Mas o banco não
volta junto:** o Liquibase não desfaz changelogs. Se a versão nova alterou o banco (coluna
nova, tabela nova), a versão antiga pode estranhar. Nesse caso, restaure também o backup
feito antes de atualizar, com os comandos da seção 3 e o dump de `C:\backups\axctg3`.
Esse é o motivo de a seção 4 começar pelo backup.

---

## 6. Backup diário

O `scripts\backup-dev-db.ps1` do projeto funciona no servidor sem mudança: ele fala com o
container `axctg3-postgres`, que tem o mesmo nome aqui. Copie o script para `C:\axctg3` e
agende, em PowerShell como administrador:

```powershell
schtasks /Create /TN "axctg3 backup" /SC DAILY /ST 12:30 /TR "powershell -NoProfile -ExecutionPolicy Bypass -File C:\axctg3\backup-dev-db.ps1"
```

Os dumps ficam em `C:\backups\axctg3` (mantém os 14 mais novos) e o log fica em
`backup.log`, na mesma pasta. O script cobre só o banco. Os arquivos do FileStorage
mudam pouco; copie-os de vez em quando com
`docker cp axctg3-app:/opt/axctg3/work/filestorage C:\backups\axctg3\`.

---

## 7. Comandos do dia a dia (na pasta `C:\axctg3`)

| Comando | Para quê |
|---|---|
| `docker compose ps` | estado dos dois containers |
| `docker compose logs -f app` | log do axctg3 ao vivo (Ctrl+C sai; o app continua) |
| `docker compose logs --tail 200 app` | últimas 200 linhas |
| `docker compose restart app` | reinicia só o axctg3 |
| `docker compose stop` / `docker compose start` | para / volta tudo, sem apagar nada |
| `docker compose down` | remove os containers; os volumes (dados) ficam |
| `docker images axctg3` | versões carregadas no servidor |
| `docker image rm axctg3:1.0.0` | apaga uma versão antiga (guarde as 2–3 mais novas) |
| `docker system df` | quanto espaço o Docker está usando |
| `docker exec -it axctg3-postgres psql -U postgres -d axctg3` | console SQL (`\q` sai) |

Os volumes são `external`, então nem `docker compose down -v` apaga os dados. Para
apagar de verdade, é preciso rodar `docker volume rm` de propósito.

---

## 8. Detalhes da imagem e limitações conhecidas

- **Imagem:** `eclipse-temurin:21-jre` (Ubuntu) mais o jar. O jar é gerado com
  `-Pvaadin.productionMode=true`, e o front-end já vai compilado dentro dele.
- **Fuso e locale:** `TZ=America/Sao_Paulo` e `-Duser.language=pt -Duser.country=BR`,
  definidos no `Dockerfile`. Sem isso, a imagem roda em UTC e em inglês.
- **Memória:** `-Xmx1536m` no `JAVA_OPTS` do `Dockerfile`, pensado para um WSL com 4 GB.
- **Profile `prod`** (`src/main/resources/application-prod.properties`): as credenciais
  do banco vêm do ambiente e a tela de login não vem pré-preenchida. Todo o resto é o
  `application.properties` normal.
- **O Postgres não fica exposto na rede**, só o app o enxerga. Para acessar pelo DBeaver,
  descomente o `ports` no `docker-compose.yml`.
- **Relatórios com fonte "Courier New":** o Linux da imagem não tem essa fonte. Seis
  `.jrxml` antigos a usam (`CotacaoRequisicaoAnt`, `InventarioAlmoxarifado`,
  `MovimentoAlmoxarifado2`, `PedidoCompra2`, `PedidoCompraImbramil`,
  `PedidoCompraImbramilAnt`), mas nenhum é chamado pelo código hoje. Se algum passar a
  ser usado, troque a fonte para `DejaVu Sans Mono`, senão o relatório falha no servidor
  sem aviso na tela.
- **O jar leva os jars `vaadin-dev*`** porque o `build.gradle` declara `vaadin-dev` como
  `implementation`. Em modo produção eles ficam inativos. Só ocupam espaço.
- **O container roda como root.** Para um servidor de teste na rede interna, tudo bem.
