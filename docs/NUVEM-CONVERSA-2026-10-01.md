# Nuvem para o axctg3: primeira subida na Magalu (01/10/2026)

Continuação de `NUVEM-CONVERSA-2026-09-29.md`. Nesta sessão o axctg3 foi colocado no ar
numa VM da Magalu Cloud, **só para teste e aprendizado, não para produção**. O roteiro
abaixo segue a ordem em que foi feito, com os tropeços e as explicações de cada conceito,
e serve de receita para refazer o processo (outra VM, outro cliente).

---

## 0. O que ficou no ar

| Item | Valor |
|---|---|
| Provedor / região / zona | Magalu Cloud, `br-se1`, zona `br-se1-a` |
| VM | `axctg3teste`: Ubuntu 24.04 LTS (`noble`), x86_64, 2 vCPU / 4 GB / 20 GB |
| IP público | `201.23.79.163` |
| Usuário Linux | `ubuntu` (com `sudo`) |
| Chave SSH | `C:\Users\capan\.ssh\id_ed25519` (privada, com passphrase) + `.pub` cadastrada na Magalu como `capan-magalu` |
| Docker | repositório oficial do Docker, compose v5.5.1, sobe sozinho no boot (`systemctl is-enabled docker` → `enabled`) |
| Pasta da pilha | `~/axctg3` (= `/home/ubuntu/axctg3`): `docker-compose.yml`, `.env`, `axctg3-1.0.0.tar` |
| Volumes | `axctg3_pgdata` (banco) e `axctg3_arquivos` (FileStorage), criados à mão como *external* |
| Versão do app | `axctg3:1.0.0` (amd64) |
| Dados | **base de dev** restaurada (dump de 01/10 + `.jmix/work/filestorage`) |
| Acesso | **só por túnel SSH**: nenhuma porta aberta além da 22 |

---

## 1. Chave SSH (no Windows)

**Conceito:** a chave é um par de arquivos.
- **Privada** (`id_ed25519`): fica só no seu computador e nunca é enviada a lugar nenhum.
- **Pública** (`id_ed25519.pub`): vai para o provedor, que a coloca dentro da VM. Pode ser
  mostrada a qualquer um.

Na conexão, a VM confere se você tem a privada correspondente, sem nenhuma senha trafegar.

```powershell
ssh-keygen -t ed25519 -C "capan-magalu"     # Enter no caminho padrão; passphrase = senha da chave
Get-Content $HOME\.ssh\id_ed25519.pub       # a linha "ssh-ed25519 AAAA... capan-magalu"
```

A passphrase não aparece enquanto você digita. Isso é normal.

## 2. Criar a VM no console da Magalu

- **Região:** `br-se1` (Sudeste).
- **Zona de disponibilidade:** `br-se1-a` / `-b` / `-c` são **datacenters fisicamente
  separados** dentro da mesma região, cada um com energia, refrigeração e rede próprias.
  Servem para quem espalha cópias da aplicação: se um datacenter cai, o outro atende. Com
  uma VM só não há redundância, então tanto faz qual escolher; foi escolhida a `br-se1-a`.
  **Importante:** discos extras (block storage) só se ligam a VMs **da mesma zona**, por
  isso anote qual foi usada.
- **Nome:** só aceita letras minúsculas e números, por isso ficou `axctg3teste`.
- **Imagem:** Ubuntu 24.04 LTS. **Tipo:** 2 vCPU / 4 GB / 20 GB (~R$ 93/mês na tabela de 29/09).
- **Chave SSH:** "adicionar nova" e colar a linha inteira do `.pub`.
- **IP público:** atribuir. Sem ele, a VM só existe na rede interna da Magalu. Um IP
  começando com `10.`, `172.` ou `192.168.` é o **privado**; o público é o outro.
- **Custo:** "parar" a VM não apaga o disco, e o disco pode continuar sendo cobrado.

## 3. Primeiro acesso SSH

```powershell
ssh ubuntu@201.23.79.163
```

- Na **primeira** conexão, a VM se apresenta (`The authenticity of host ... can't be
  established ... Are you sure you want to continue connecting`). Responda `yes`, por
  extenso. O `ssh` grava a identidade dela em `.ssh\known_hosts` e, se um dia ela mudar
  sem motivo, avisa em letras grandes (pode ser alguém se passando pelo servidor).
- Em seguida ele pede a **passphrase** da chave.
- Sair: `exit` ou `Ctrl+D`.

**A janela é o PowerShell, mas o que manda é o prompt:**

| Prompt | Onde o comando roda |
|---|---|
| `PS C:\Users\capan>` | no Windows |
| `ubuntu@axctg3teste:~$` | na VM |

Enquanto o `ssh` está conectado, tudo o que é digitado ou colado vai para o bash da VM.
Colar: botão direito ou `Ctrl+V` no Windows Terminal. Cole **um bloco por vez** e espere o
prompt voltar.

Comandos para "conhecer a casa": `uname -a`, `lsb_release -a`, `free -h`, `df -h /`, `nproc`.

Erros típicos: `Connection timed out` costuma ser firewall ou grupo de segurança;
`Permission denied (publickey)` costuma ser usuário errado ou chave não associada.

**Regra do Linux:** comando que deu certo normalmente **não diz nada**; só erro fala.

## 4. Instalar o Docker (repositório oficial)

O Docker que vem no Ubuntu é antigo, por isso foi usado o repositório do próprio Docker.

```bash
sudo apt update && sudo apt upgrade -y        # tela roxa de "serviços a reiniciar": Enter
sudo apt install -y ca-certificates curl
sudo install -m 0755 -d /etc/apt/keyrings
sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
sudo chmod a+r /etc/apt/keyrings/docker.asc
```

### Tropeço: a linha longa do repositório derrubava o SSH
A receita oficial cadastra o repositório com uma linha só:
`echo "deb [arch=$(dpkg --print-architecture) ...] ... $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | sudo tee ...`.
**Colada no terminal, essa linha derrubava a sessão SSH** (voltava para o PowerShell). O
resultado foi `docker-ce has no installation candidate` no `apt install`, porque o arquivo
`/etc/apt/sources.list.d/docker.list` nunca chegou a ser criado.

**O que funcionou:** os mesmos passos quebrados em comandos curtos, sem `$( )` nem `|`:

```bash
lsb_release -cs        # "No LSB modules are available." é só aviso; o que vale é "noble"
echo "deb [arch=amd64 signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu noble stable" > /tmp/docker.list
sudo cp /tmp/docker.list /etc/apt/sources.list.d/docker.list
cat /etc/apt/sources.list.d/docker.list
sudo apt update        # deve aparecer download.docker.com na saída
```

Depois disso:

```bash
sudo apt install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
sudo usermod -aG docker ubuntu    # usar docker sem sudo
exit                              # o grupo novo só vale numa sessão nova: reconectar
docker run --rm hello-world       # "Hello from Docker!"
docker compose version            # v5.5.1: numeração nova do plugin, comandos iguais
systemctl is-enabled docker       # "enabled" = sobe sozinho no boot, sem login
```

## 5. Levar a imagem

**Na máquina de desenvolvimento** (gera o jar de produção e a imagem amd64, ~2 min, 237 MB):

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\gerar-imagem.ps1 -Versao 1.0.0
```

**Na VM:**

```bash
mkdir ~/axctg3
docker volume create axctg3_pgdata
docker volume create axctg3_arquivos
```

O `~` é a pasta pessoal (`/home/ubuntu`). Os volumes são *external* de propósito, para um
`docker compose down -v` não conseguir apagá-los.

**`scp`** é o "copy" que viaja por dentro do SSH, com a mesma chave, passphrase e porta.
Formato: `scp <origem> <destino>`, com o lado remoto escrito `usuario@ip:caminho`. É
rodado **no Windows**; vale manter uma segunda janela do PowerShell para isso, deixando a
primeira conectada na VM.

```powershell
scp C:\axctg3-imagens\axctg3-1.0.0.tar ubuntu@201.23.79.163:~/axctg3/
scp C:\projetos\jmix\axctg3\deploy\servidor-teste\docker-compose.yml ubuntu@201.23.79.163:~/axctg3/
```

## 6. Subir a pilha

```bash
cd ~/axctg3
docker load -i axctg3-1.0.0.tar      # "Loaded image: axctg3:1.0.0"
nano .env
```

Conteúdo do `.env`:

```
AXCTG3_VERSAO=1.0.0
DB_SENHA=<senha forte, guardada à parte>
```

**`nano`:** `Ctrl+O` e Enter grava; `Ctrl+X` sai (no rodapé aparecem como `^O` e `^X`).
A `DB_SENHA` só vale na **primeira** subida, com o volume vazio: ela fica gravada no banco.

```bash
docker compose up -d
docker compose logs -f app     # esperar "Started Axctg3Application"; Ctrl+C sai do log, o app continua
```

Na primeira subida o Liquibase criou o banco inteiro do zero.

### Acesso pelo túnel SSH (sem abrir porta)
Num banco novo, o Jmix cria o usuário `admin` com senha `admin`. Abrir a 8085 para a
internet seria convidar robôs que varrem IPs o tempo todo. Por isso o acesso é feito por
**túnel**, rodado **no Windows**:

```powershell
ssh -L 8085:localhost:8085 ubuntu@201.23.79.163
```

`-L 8085:localhost:8085` quer dizer: "o que chegar na porta 8085 **do meu Windows**, leve
pelo SSH até a porta 8085 **da VM**". Para o mundo, a VM continua só com a 22 aberta.
Com essa janela aberta, o navegador acessa **http://localhost:8085/axctg3**. O navegador
acha que fala com o próprio Windows; o túnel leva a conversa até a VM. Fechou a janela,
acabou o túnel. O profile `prod` mostra o login em branco; ao entrar, a senha do `admin`
foi trocada na hora.

## 7. Levar a base de dev para a nuvem

É a seção 3 do `SERVIDOR-TESTE.md`, adaptada para SSH. **Substitui o banco inteiro da
nuvem.** Vão junto os usuários e senhas de dev (a troca de senha do passo 6 foi desfeita) e
os certificados A1. As empresas com NFe (Agropec 7, Imbramil 1) estavam em
**homologação** (`ambiente_nfe = 2`) e assim devem ficar: nesse servidor, nunca mude uma
empresa para produção.

**No Windows:**

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\backup-dev-db.ps1
scp C:\backups\axctg3\axctg3-20261001-130142.dump ubuntu@201.23.79.163:~/axctg3/base.dump
scp -r C:\projetos\jmix\axctg3\.jmix\work\filestorage ubuntu@201.23.79.163:~/axctg3/
```

No primeiro `scp`, o arquivo já chega com o nome `base.dump`. O `-r` do segundo copia a
pasta inteira (recursivo).

**Na VM, em `~/axctg3`:**

```bash
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

O app subiu com a base de dev. **Pendente:** trocar de novo a senha do admin na nuvem (e
dos usuários de teste, se for o caso).

---

## 8. Domínio: como ter um nome em vez do IP

O HTTPS (necessário para acessar sem túnel, senão a senha trafega aberta) pede um **nome**.

- **Para testar já, de graça: `sslip.io`.** O nome `201-23-79-163.sslip.io` responde com o
  IP `201.23.79.163`, porque o DNS deles lê o IP de dentro do próprio nome, sem nenhum
  cadastro. O **Let's Encrypt emite certificado** para esse nome. Limitações: o nome é feio,
  depende de terceiros e quebra se o IP mudar.
- **Para valer: domínio `.com.br` no registro.br**, com CPF ou CNPJ, cerca de R$ 40 por ano.
  O registro.br oferece DNS grátis: basta criar um registro **A**, por exemplo
  `teste` → `201.23.79.163`. Com um domínio só dá para criar quantos subdomínios quiser
  (`teste.`, `app.`, `cliente1.`...).

**Situação:** o pedido de registro **já foi feito no registro.br**; aguardando a aprovação.
Quando sair, a troca do `sslip.io` para o domínio próprio é uma linha na configuração do
Caddy.

---

## 9. Próximos passos

Nenhum foi feito ainda.

- **C. HTTPS:** container do **Caddy** no compose como proxy na frente do app (ele obtém e
  renova o certificado do Let's Encrypt sozinho), liberar **80/443** no grupo de segurança
  da Magalu e acessar `https://<nome>/axctg3`. Usar o domínio novo se já estiver ativo;
  senão, o `sslip.io`. A 8085 continua fechada para fora.
- **A. Backup via SSH:** script `.ps1` no Windows que roda `pg_dump -Fc` no container da VM,
  copia o volume `axctg3_arquivos` e traz os dois para `C:\backups\axctg3-nuvem`, guardando
  os N mais recentes. Depois, **treinar o restore**: backup que nunca foi restaurado é só
  esperança.
- **B. Ciclo de atualização:** gerar a 1.0.1, `scp`, `docker load`, trocar `AXCTG3_VERSAO`
  no `.env`, `docker compose up -d`, e treinar a volta para a 1.0.0.
- Senhas: trocar a do admin na nuvem (desfeita pelo restore).
