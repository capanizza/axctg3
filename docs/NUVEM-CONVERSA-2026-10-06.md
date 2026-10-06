# Nuvem para o axctg3: domínio próprio e HTTPS (06/10/2026)

Continuação de `NUVEM-CONVERSA-2026-10-01.md` (item **C** dos próximos passos de lá). Nesta
sessão a VM de teste da Magalu ganhou nome e HTTPS: o axctg3 passou a abrir em
**https://axctg3.axialsoftware.com.br**, sem túnel. Continua sendo **teste, não produção**.

---

## 0. O que mudou

| Item | Antes (01/10) | Agora |
|---|---|---|
| Endereço | `http://localhost:8085/axctg3` via túnel SSH | `https://axctg3.axialsoftware.com.br` (a raiz leva a `/axctg3/`) |
| Portas abertas na Magalu | 22 | 22, **80**, **443** |
| Porta 8085 | publicada em todas as interfaces da VM (bloqueada só pelo grupo de segurança) | publicada só em `127.0.0.1` da VM; o túnel continua funcionando |
| Containers | `axctg3-postgres`, `axctg3-app` | + `axctg3-caddy` |
| Compose na VM | cópia de `deploy/servidor-teste/` | `deploy/nuvem/docker-compose.yml` + `deploy/nuvem/Caddyfile` (o anterior ficou em `~/axctg3/docker-compose.yml.antes-caddy`) |
| Certificado | — | Let's Encrypt, emitido pelo Caddy; o primeiro vale até 04/01/2027 e é renovado sozinho |

---

## 1. DNS no registro.br

O domínio `axialsoftware.com.br` foi registrado no registro.br. Logo depois de publicado, ele
fica nos servidores `a.auto.dns.br` / `b.auto.dns.br`, com a **zona vazia**: o nome existe,
mas não aponta para lugar nenhum.

Caminho no painel (registro.br → Entrar → clicar no domínio):

1. Aba **DNS**: há duas opções. **Alterar servidores DNS** serve para entregar o DNS a outro
   provedor (Cloudflare, Magalu...). **Configurar endereçamento** usa o DNS grátis do próprio
   registro.br, e foi a escolhida.
2. Dentro dela, **não** usar o modo básico (campos de "site" e "e-mail"): ativar o
   **Modo avançado**.
3. Ao ativar, o domínio entra **em transição**: o registro.br troca os servidores para
   `*.sec.dns.br` (aqui: `d.sec.dns.br` / `f.sec.dns.br`). Enquanto isso, **Configurar zona
   DNS** responde "Domínio em transição. Por favor, aguarde alguns minutos e tente
   novamente". Levou cerca de uma hora. Recarregar a página (F5) de tempos em tempos; ficar
   parado nela não adianta.
4. Em **Configurar zona DNS** → **Nova entrada**: tipo `A`, nome `axctg3` (só o prefixo, o
   painel completa o domínio), dados `201.23.79.163` → **Salvar alterações** (sem esse
   clique a entrada fica só na tela).

O nome passou a resolver em poucos minutos. Para conferir de qualquer lugar, sem `dig`:
`https://dns.google/resolve?name=axctg3.axialsoftware.com.br&type=A` no navegador.

Por que um **subdomínio** (`axctg3.`) e não o domínio puro: `axialsoftware.com.br` e `www`
ficam livres para um site institucional, e dá para criar outros nomes depois (`teste.`,
`cliente1.`...) — cada um é só mais uma entrada `A`.

---

## 2. Portas 80 e 443 na Magalu

No **grupo de segurança** da VM (o mesmo que já tinha a regra da 22), duas regras novas:
**Entrada**, **TCP**, porta **80** e porta **443**, **IPv4**, origem **0.0.0.0/0** (qualquer
endereço da internet, o certo para um site público). A 8085 continua fechada.

- **443** é o HTTPS de fato.
- **80** é usada pelo Let's Encrypt para confirmar que o nome é nosso na emissão do
  certificado; depois o Caddy a usa para redirecionar `http://` para `https://`.

Conferência de fora, antes de existir o Caddy: porta que responde **"recusada" na hora**
está liberada no firewall (só não tem ninguém escutando); porta que fica **pendurada até o
tempo estourar** está bloqueada. Foi assim que se viu que o `ufw` do Ubuntu (desligado por
padrão nessa imagem) não atrapalhava.

---

## 3. Caddy

**Conceito:** o Caddy é um *proxy reverso*. Ele fica na frente do app, recebe as conexões
HTTPS na 443, cuida do certificado e repassa cada requisição ao axctg3 pela rede interna do
compose (`app:8085`). O app em si continua falando HTTP simples, sem saber de certificado.

Arquivos no repositório, em `deploy/nuvem/` (o `deploy/servidor-teste/` do Windows 10 ficou
como estava):

- **`Caddyfile`**: o nome do site, `redir / /axctg3/` (o app vive no context-path
  `/axctg3`) e `reverse_proxy app:8085`. O WebSocket do Vaadin passa sem configuração extra.
- **`docker-compose.yml`**: o de antes, mais:
  - serviço `caddy` (imagem `caddy:2`), portas 80/443, volumes `caddy_data` (certificados) e
    `caddy_config`. Perder o `caddy_data` não é grave — o Caddy pede outro certificado —,
    mas o Let's Encrypt limita emissões por semana, então não apagar à toa;
  - app com `ports: "127.0.0.1:8085:8085"`. Detalhe: o Docker abre portas por fora do `ufw`,
    então prender no `127.0.0.1` é o que garante a 8085 fechada, e não o firewall do Ubuntu;
  - app com `SERVER_FORWARDHEADERSSTRATEGY: native` (= `server.forward-headers-strategy`; em
    variável de ambiente o Spring tira os hífens). Sem isso, o app acha que foi acessado por
    `http://` e monta redirects (como o do login) errados.

Subida (do Windows, cada comando pede a passphrase da chave):

```powershell
ssh ubuntu@201.23.79.163 "cp ~/axctg3/docker-compose.yml ~/axctg3/docker-compose.yml.antes-caddy"
scp C:\projetos\jmix\axctg3\deploy\nuvem\docker-compose.yml C:\projetos\jmix\axctg3\deploy\nuvem\Caddyfile ubuntu@201.23.79.163:~/axctg3/
ssh ubuntu@201.23.79.163
```

Na VM:

```bash
cd ~/axctg3
docker compose up -d           # baixa o Caddy e recria o app (reinicia, boot normal)
docker compose logs -f caddy   # esperar "certificate obtained successfully"; Ctrl+C sai
```

Logo após o `up`, a página pode dar **502** por um minuto: é o app ainda subindo.

**Cadeado:** o Chrome/Edge não mostra mais cadeado desde 2023 — no lugar há um ícone de
"ajustes". Clicando nele, "A conexão é segura" é a confirmação.

Conferência de fora (`curl -I`):

| Teste | Resultado |
|---|---|
| `http://axctg3.axialsoftware.com.br/` | 308 → `https://` |
| `https://…/` | 302 → `/axctg3/` |
| `https://…/axctg3/` | 302 → `https://axctg3.axialsoftware.com.br/axctg3/login` (host e esquema certos = forward headers funcionando) |
| Cookie `JSESSIONID` | `Secure; HttpOnly` |
| Certificado | `CN=axctg3.axialsoftware.com.br`, emissor Let's Encrypt |
| Porta 8085 | sem resposta (fechada) |

---

## 4. Atenção: login exposto

Até 01/10 só o túnel chegava ao app. Agora a **tela de login está na internet**, e robôs
testam senhas o tempo todo. A base na VM é a **cópia do dev**: vieram junto os usuários de
teste dos papéis operador/gerente e suas senhas, e os certificados A1 (empresas em
homologação). Usuário de teste com senha fraca deve ser trocado ou bloqueado. Antes de virar
produção, entra aqui uma base própria, sem os dados de dev.

---

## 5. Backup da VM (item A)

`scripts\backup-nuvem.ps1`, irmão do `backup-dev-db.ps1`, roda **no Windows** e conversa
com a VM por SSH:

1. Na VM, numa pasta temporária em `/tmp`: `pg_dump -Fc` do banco, **conferido com
   `pg_restore -l`** ali mesmo; o volume `axctg3_arquivos` empacotado (`tar czf`) por um
   container descartável da imagem `postgres:16` (que já está na VM); `sha256sum` dos dois.
2. `scp` traz tudo para `C:\backups\axctg3-nuvem\<yyyyMMdd-HHmmss>\` (`axctg3.dump`,
   `arquivos.tgz`, `sha256.txt`), e o script confere cada arquivo contra o sha256 da VM.
3. Apaga a pasta temporária da VM (o disco dela é de 20 GB) e mantém as **14** pastas mais
   recentes. Log em `C:\backups\axctg3-nuvem\backup.log`. Backup que falha no meio tem a
   pasta local removida, para não confundir num restore.

Detalhe do PowerShell 5.1: o dump binário nunca passa por ele (o `>` roda no bash da VM, e o
`scp` copia o arquivo). Redirecionar a saída de um `ssh` para arquivo no PowerShell 5.1
corrompe binário (ele converte para texto UTF-16).

### ssh-agent: a passphrase uma vez só

O script chama `ssh`/`scp` três vezes, e o backup agendado não tem quem digite passphrase.
O **ssh-agent** do Windows guarda a chave destravada (sobrevive a reinício; a proteção
passa a ser a senha do usuário do Windows). Ele vem **desativado** de fábrica:

```powershell
# PowerShell como ADMINISTRADOR, uma vez:
Set-Service ssh-agent -StartupType Automatic
Start-Service ssh-agent
# PowerShell normal:
ssh-add                                     # pede a passphrase uma vez
ssh ubuntu@201.23.79.163 "echo ok"          # tem que responder ok sem pedir nada
```

O script usa `ssh -o BatchMode=yes`: se a chave não estiver no agente, falha na hora com
"Permission denied" (fica no `backup.log`) em vez de travar esperando a passphrase.

### Agendamento

Tarefa do Agendador do Windows **"axctg3 backup nuvem"**: diária às **12:45** (a do dev é às
12:30), `StartWhenAvailable` (se o PC estava desligado, roda quando ligar), limite de 30 min,
usuário `capan`, só com sessão aberta. Testada disparando à mão: `LastTaskResult = 0`.

### Restore testado

Backup que nunca foi restaurado é só esperança. O dump de 06/10 (12,9 MB) foi restaurado num
banco descartável no Postgres de dev (`createdb axctg3_restore_teste` + `pg_restore
--no-owner`, depois `dropdb`): 64 tabelas, 6.867 lançamentos, 3.080 contas, 4 empresas, sem
erro. O `arquivos.tgz` tinha os 8 arquivos esperados (certificados `.pfx`, logos `.bmp`, uma
remessa).

Para restaurar **na VM** (substitui o banco inteiro dela), mesma receita da seção 7 de
`NUVEM-CONVERSA-2026-10-01.md`, trocando a origem:

```powershell
scp C:\backups\axctg3-nuvem\<pasta>\axctg3.dump C:\backups\axctg3-nuvem\<pasta>\arquivos.tgz ubuntu@201.23.79.163:~/axctg3/
```

```bash
cd ~/axctg3
docker compose stop app
docker cp axctg3.dump axctg3-postgres:/tmp/base.dump
docker exec axctg3-postgres dropdb -U postgres axctg3
docker exec axctg3-postgres createdb -U postgres axctg3
docker exec axctg3-postgres pg_restore -U postgres -d axctg3 --no-owner /tmp/base.dump
docker exec axctg3-postgres rm /tmp/base.dump
docker run --rm -v axctg3_arquivos:/dados -v ~/axctg3:/origem postgres:16 sh -c "rm -rf /dados/* && tar xzf /origem/arquivos.tgz -C /dados"
docker compose start app
docker compose logs -f app             # "Started Axctg3Application"; Ctrl+C sai
rm axctg3.dump arquivos.tgz
```

O `rm -rf /dados/*` esvazia o volume antes de desempacotar: sem ele, um arquivo criado
depois do backup continuaria lá, apontado por nenhum registro do banco restaurado.

**Restore na VM treinado em 06/10**, com prova de que o tempo voltou: backup
`20261006-160302` → cadastrado o Centro de Custo "TESTE RESTORE" no site → restore com a
receita acima → o "TESTE RESTORE" sumiu (conferido no banco), 6.867 lançamentos e os 8
arquivos do volume no lugar, app de pé em 23 s. Site fora do ar (502) do `stop` ao
`Started`. Se algo falhar no meio, o backup continua no Windows: é só repetir.

Tropeço do treino: a linha longa do `scp` foi quebrada ao colar, e o pedaço final
(`ubuntu@...:~/axctg3/`) rodou sozinho como comando ("não é reconhecido como nome de
cmdlet"). A cópia em si tinha funcionado: olhe se o `scp` mostrou os arquivos com 100%
antes de repetir.

---

## 6. Versão na tela

Antes do ciclo de atualização, o app ganhou a **versão no rodapé do menu lateral**
("Versão 1.0.1 · 06/10/2026 15:38" = versão + hora do build). É o que prova, olhando a tela,
qual imagem o servidor está rodando.

- Uma fonte só: `scripts\gerar-imagem.ps1 -Versao X` passa `-Pversao=X` ao Gradle, e o
  `build.gradle` usa como `version`. A tag da imagem e o número na tela não divergem.
- Sem o script (bootRun, IntelliJ) aparece "Versão dev · …".
- O plugin do Jmix já gerava o `META-INF/build-info.properties` (tarefa `bootBuildInfo`); a
  `MainView` só lê o bean `BuildProperties`.
- A 1.0.0 é anterior a isso: com ela, o rodapé fica **vazio**.

---

## 7. Ciclo de atualização (item B)

Treinado em 06/10: 1.0.0 → **1.0.1** → volta para 1.0.0 → 1.0.1 de novo. A 1.0.1 trouxe o
Jmix 3.0.3 e a versão na tela, **sem changelog do Liquibase**.

**No Windows:**

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\gerar-imagem.ps1 -Versao 1.0.1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\backup-nuvem.ps1   # SEMPRE antes
scp C:\axctg3-imagens\axctg3-1.0.1.tar ubuntu@201.23.79.163:~/axctg3/
ssh ubuntu@201.23.79.163
```

**Na VM:**

```bash
cd ~/axctg3
docker load -i axctg3-1.0.1.tar        # "Loaded image: axctg3:1.0.1"
docker images axctg3                   # a antiga continua lá: é o que permite voltar
sed -i 's/^AXCTG3_VERSAO=.*/AXCTG3_VERSAO=1.0.1/' .env
grep AXCTG3_VERSAO .env
docker compose up -d                   # recria só o app; Postgres e Caddy não são tocados
docker compose logs -f app             # "Started Axctg3Application"; Ctrl+C sai
```

Durante o boot o site dá **502** por um ou dois minutos: é a janela de indisponibilidade da
atualização. Conferência: o rodapé do menu mostra a versão nova.

**Rollback** = os mesmos comandos com a versão antiga no `sed` (a imagem já está na VM, não
precisa mandar nada). Só é simples assim quando a versão nova **não trouxe changelog**: o
Liquibase altera o banco ao subir, e a versão antiga pode não funcionar com o banco
alterado. Nesse caso, voltar = versão antiga **+ restaurar o backup** feito antes da
atualização (receita na seção 5). Antes de gerar uma versão, conferir:
`git log <commit da versão anterior>..HEAD -- src/main/resources/br/com/axialsoftware/axctg3/liquibase`.

Cada versão gerada fica registrada com uma **tag no git** (`v1.0.1`), para saber
exatamente qual código está em cada imagem. A 1.0.0 é anterior a essa regra e ficou sem tag.

Depois de confirmar a versão nova, o `.tar` na VM pode ser apagado (`rm axctg3-1.0.1.tar`):
a imagem já está carregada no Docker, e o disco da VM é de 20 GB. As imagens antigas também
se apagam (`docker image rm axctg3:1.0.0`), mas guardar a anterior à atual é o que permite
o rollback.

---

## 8. Próximos passos

- Opcional: travar a regra da porta 22 na Magalu para o IP de casa/escritório, agora que o
  acesso do dia a dia não depende mais do túnel.
- Antes de virar produção: base própria, sem os usuários, senhas e certificados do dev
  (seção 4).
