# Nuvem para o axctg3: conversa de 29/09/2026

Registro da conversa sobre levar o axctg3 para a nuvem: escolha de provedor, backup,
SSH, preços da Magalu Cloud e um glossário dos termos que aparecem no simulador e no
dia a dia de um servidor Linux com Docker. As perguntas estão na ordem em que foram
feitas, e as respostas estão resumidas.

Preços e condições de provedores **mudam**: confira antes de contratar.

---

## 1. Onde paramos antes (28/09)

- **Oracle Cloud Always Free** foi sugerida para **teste**, porque é gratuita. **Magalu Cloud**
  (ou Hostinger) ficou como opção para **produção**.
- O **Postgres roda em container na mesma VM** do app, e não como banco gerenciado.
- Esse estudo puxou o PR #46: remessa, boleto e SPED ECD saem pelo navegador
  (`Downloader`), sem pasta no servidor.

## 2. Backup

### Na Oracle Always Free o backup é automático?
Não. Com o Postgres num container da VM, para a Oracle ele é só um programa, e o backup
é **responsabilidade nossa**.
- A Oracle faz **backup do disco da VM** (o Always Free inclui 5), mas é uma cópia do disco
  inteiro com o banco rodando. Serve como rede de segurança, não como backup do banco.
- **Postgres gerenciado** (OCI Database with PostgreSQL) é pago. O banco gratuito deles
  (Autonomous) é Oracle, não Postgres.
- **Recomendação:** `pg_dump -Fc` diário via `cron`, conferido com `pg_restore -l`,
  guardando os N mais recentes e **copiando para fora da VM**.

### E na Magalu Cloud?
Depende de onde o banco fica:
- **Postgres em container na VM:** não é automático, igual à Oracle. Os snapshots da VM são
  manuais (ou agendados via CLI `mgc`) e copiam o disco, não o banco de forma consistente.
- **DBaaS PostgreSQL:** **sim**. Snapshot diário automático com retenção de 1 a 30 dias,
  snapshot manual quando quiser, proteção contra exclusão acidental e opção de cluster com
  failover. O perfil `prod` já lê a conexão de variáveis de ambiente, então trocar para o
  DBaaS é só apontar o endereço.
- Mesmo com DBaaS, manter um `pg_dump` periódico **fora da Magalu**, porque os snapshots
  ficam no mesmo provedor.

### Dá para fazer o backup da minha máquina, acessando o banco remoto?
Sim, nos dois casos, **sem abrir a porta do Postgres para a internet**: entra-se pelo SSH.

**Banco em container na VM:**
```powershell
# (a) pg_dump roda lá e o arquivo desce pelo SSH (atenção: no PowerShell 5 o ">" corrompe
#     binário. Use cmd /c "..." ou PowerShell 7)
ssh usuario@ip-da-vm "docker exec axctg3-postgres pg_dump -Fc -U postgres axctg3" > backup.dump

# (b) túnel SSH e pg_dump local (o container publica a porta só em 127.0.0.1 da VM)
ssh -N -L 15432:localhost:5432 usuario@ip-da-vm
pg_dump -Fc -h localhost -p 15432 -U postgres axctg3 -f backup.dump
```
**DBaaS:** conexão direta, se ele aceitar acesso externo restrito ao seu IP, ou túnel pela
VM (`ssh -L 15432:endereco-privado-do-banco:5432 usuario@ip-da-vm`).

**O banco não é tudo:** o volume `axctg3_arquivos` guarda o `FileStorage` (logos,
**certificados A1**, cópias das remessas). O backup precisa levar os dois.

## 3. SSH

### O mstsc é um tipo de SSH?
Não.

| | mstsc (Área de Trabalho Remota / RDP) | SSH |
|---|---|---|
| O que você vê | A tela do Windows do servidor | Um terminal de texto |
| Onde é usado | Servidores Windows | Servidores Linux (quase toda VM na nuvem) |
| Autenticação típica | Usuário e senha | Par de chaves |
| Automação | Pouca | Muita (scripts rodam comandos e trazem arquivos) |

As VMs planejadas são Linux, sem área gráfica, então o acesso é por SSH. Uma VM Windows
com mstsc é possível, mas é mais cara (licença, memória), não existe no Always Free e o
Docker roda pior nela.

### Tenho SSH no Windows 11?
Sim: o **OpenSSH 9.5** vem com o Windows, em `C:\Windows\System32\OpenSSH\`.

| Programa | Para quê |
|---|---|
| `ssh` | Terminal no servidor, ou rodar um comando lá (`ssh usuario@ip "comando"`) |
| `scp` | Copiar arquivos entre as máquinas |
| `sftp` | Transferência interativa |
| `ssh-keygen` | Gerar o par de chaves |
| `ssh-agent` / `ssh-add` | Guardar a chave destravada na memória |

**Par de chaves:** a **privada** (`id_ed25519`) fica só na sua máquina, e a **pública**
(`id_ed25519.pub`) vai para o servidor. Gerar com `ssh-keygen -t ed25519`, e a pública se
cola no painel do provedor ao criar a VM. (Em 29/09 ainda não havia chave: `~\.ssh` não
existia.)

### Com o SSH: linha de comando, executar scripts, baixar e enviar arquivos?
Sim para os três.
```powershell
ssh ubuntu@203.0.113.10                                   # entra (sai com exit)
ssh ubuntu@203.0.113.10 "docker compose -f ~/axctg3/docker-compose.yml ps"   # um comando
scp C:\axctg3\axctg3-1.2.tar ubuntu@203.0.113.10:~/axctg3/                    # enviar
scp ubuntu@203.0.113.10:~/backups/axctg3.dump C:\backups\axctg3-nuvem\        # baixar
```
O usuário inicial depende da imagem (`ubuntu` no Ubuntu, `opc` no Oracle Linux). Comandos
de administração vão com `sudo`.

## 4. Oracle Always Free

- **Prevenir a retirada de VM ociosa:** upgrade da conta para **Pay As You Go** (PAYG). Nada
  é cobrado dentro dos limites do Always Free, e a Oracle deixa de recuperar instâncias
  ociosas (critério: CPU, rede e memória abaixo de ~20% por 7 dias). Contas PAYG também
  costumam conseguir VM ARM com mais facilidade.
- **Cuidados:** criar um **Budget com alerta** (R$ 1 / US$ 1) e conferir a etiqueta
  "Always Free-eligible". Não usar script que gasta CPU de propósito.
- **Na criação da conta:** a **Home Region não muda depois** (Brazil East, São Paulo, ou
  Brazil Southeast, Vinhedo); use cartão de **crédito** com nome e endereço iguais aos do
  cadastro; o trial de US$ 300 dura 30 dias; ative o MFA.
- **Bloqueio encontrado:** a Oracle exigiu **e-mail corporativo**. Ponto de atenção: o e-mail
  do cadastro é a chave-mestra da conta. Se ficar no domínio do cliente, o cliente controla
  a conta na prática. Para um servidor próprio, o ideal é um e-mail num **domínio próprio**
  (ex.: `axialsoftware.com.br`, com Zoho Mail gratuito, Google Workspace ou Microsoft 365).
- **Imagem arm64:** a VM Ampere A1 é ARM. Feito no mesmo dia (commit `b7eee09`):
  `scripts\gerar-imagem.ps1 -Versao X -Plataforma arm64` gera `axctg3-X-arm64.tar` por
  emulação, sem passar pelo Docker local. A tag dentro do tar continua `axctg3:X`.
  Validado subindo a imagem emulada contra um Postgres temporário.

## 5. Magalu Cloud

**O que muda em relação à Oracle:** empresa brasileira (cobrança em reais, nota fiscal,
CPF/CNPJ); as VMs são **x86**, então usam a imagem amd64 padrão, sem `-Plataforma`; não há
recuperação de VM ociosa; tem DBaaS PostgreSQL. Por outro lado, **não há camada gratuita
permanente**.

### Preços (29/09/2026, região br-se1, R$/mês, Linux)
Fonte: a API pública que alimenta a calculadora deles,
`https://api.magalu.cloud/sku/v0/skus?region=br-se1`.

**VMs, linha Balanced Value (BV).** A linha Dedicated Performance custa de 3 a 4 vezes mais.

| vCPU | RAM | Disco | R$/mês |
|---|---|---|---|
| 2 | 2 GB | 20 GB | 69,99 |
| 2 | 4 GB | 20 GB | 92,99 |
| 2 | 4 GB | 40 GB | 102,99 |
| 2 | 8 GB | 40 GB | 139,99 |
| 4 | 8 GB | 40 GB | 169,99 |
| 4 | 16 GB | 40 GB | 249,99 |

**DBaaS PostgreSQL (BV)**, com 10 GB de disco incluídos:

| Tipo | vCPU | RAM | R$/mês |
|---|---|---|---|
| Instância única | 1 | 4 GB | 94,22 |
| Instância única | 2 | 4 GB | 100,00 |
| Instância única | 4 | 8 GB | 188,49 |
| Cluster (alta disponibilidade) | 4 | 16 GB | 249,15 |

**Outros:** block storage de R$ 0,58 a 0,89 por GB/mês (conforme o IOPS); object storage
R$ 0,10 por GB/mês + R$ 0,10 por GB baixado; **tráfego de saída pelo IP público: R$ 0**. O
preço do IP público fixo e dos snapshots **não aparece** na API; confira no simulador.

### Montagens

| Cenário | Composição | R$/mês |
|---|---|---|
| Teste | VM 2 vCPU / 4 GB / 20 GB, com app e Postgres em container | ~93 |
| Produção A | VM 2 vCPU / 8 GB / 40 GB, tudo na VM, backup por script | ~140 |
| Produção B | VM 2 vCPU / 4 GB / 20 GB (app) + DBaaS 2 vCPU / 4 GB | ~193 + disco extra |

A diferença entre A e B (R$ 50 a 60) é o preço do backup automático e do banco fora da VM.
Nos dois casos, manter o `pg_dump` baixado para fora do provedor.

## 6. Glossário do simulador

| Termo | O que é | Para o axctg3 |
|---|---|---|
| **GPU** | Placa de vídeo / processador paralelo (IA, 3D, vídeo) | Não precisa. Deixe zero |
| **IOPS** | Leituras e gravações por segundo que o disco aguenta (agilidade, não tamanho). Faixas NVMe 1K a 20K | 1K no teste; 5K é folga barata se o Postgres ficar na VM |
| **Snapshot** | "Foto" do disco inteiro num instante | Bom antes de mudanças arriscadas. **Não substitui o `pg_dump`** (é cópia de disco, fica dentro do provedor). No DBaaS o snapshot é consistente |
| **VPC** | Rede privada virtual da conta, isolada dos outros clientes | Padrão com uma sub-rede resolve. Importa se usar DBaaS |
| **IP flutuante** | IP público reservado na conta, transferível entre VMs | Um só, na VM do app. Permite trocar a VM sem mudar o endereço. IP reservado sem uso costuma ser cobrado |
| **NAT Gateway** | Saída para a internet de VMs **sem** IP público | Não precisa |
| **Load Balancer** | Distribui acessos entre várias VMs | Não precisa (uma VM). Se um dia houver várias, o Vaadin exige *sticky session* |
| **Load Balancer interno** | Mesmo balanceador, só dentro da rede privada | Não precisa |
| **Kubernetes** | Orquestrador de containers em várias máquinas | Não precisa. Compose numa VM resolve, e a mesma imagem roda no K8s se um dia crescer |
| **Object Storage** | Depósito de arquivos acessado por API (compatível com S3), cobrado pelo uso | Backups (bucket **privado**). No futuro, o `FileStorage` do Jmix (add-on S3) |
| **DNS** | Tradução de nomes em IPs (registros A, CNAME, MX, TXT; TTL) | Domínio no registro.br (DNS gratuito), registro A → IP da VM. **Necessário para HTTPS** (Let's Encrypt) |
| **CDN** | Servidores pelo mundo com cópias do conteúdo estático | Não precisa (usuários no Brasil, conteúdo dinâmico) |
| **Security Group** | Firewall da nuvem, **fora** da VM, com regras nomeadas aplicáveis a várias VMs, entrada bloqueada por padrão e *stateful* | É a proteção que vale: o **Docker passa por cima do `ufw`** de dentro da VM |
| **Private Network** | Rede interna com IPs privados (sub-rede da VPC) | Caminho entre o app e o DBaaS (os dois na mesma VPC) |
| **Public IP** | Endereço da VM na internet | Um IPv4, na VM do app. O banco nunca tem IP público |
| **Elastic IP** | Nome da AWS para IP flutuante (OpenStack: Floating IP; Google: Static External IP; Oracle: Reserved Public IP) | Idem |
| **IPv6** | Endereços novos (os IPv4 esgotaram) | IPv4 obrigatório; IPv6 opcional. Pegadinha: o SSH pode sair por IPv6 e ser barrado pela regra "22 só para o meu IP" (`ssh -4` força IPv4) |
| **Block Storage** | O HD virtual da VM: o disco de boot do plano e os volumes adicionais | Teste: só o boot. Produção com Postgres na VM: volume adicional só para os dados (sobrevive à VM, muda de VM, cresce) |

### Regras de firewall para o axctg3

| Porta | Para quê | Liberar para |
|---|---|---|
| 22 (SSH) | Administração, deploy, backup | Só o seu IP, se fixo; senão todos, com acesso só por chave |
| 80 (HTTP) | Let's Encrypt e redirecionamento para HTTPS | Todos |
| 443 (HTTPS) | Usuários | Todos |
| 8085 (app) | Só antes do HTTPS | Todos, **temporariamente** |
| 5432 (Postgres) | — | **Ninguém, nunca** |

Saída toda liberada (SEFAZ, atualizações, imagens).

## 7. Docker e o servidor Linux

### O "axctg3" no Docker Desktop é um container?
Não: é um **projeto do Compose**, um agrupamento. Na máquina de desenvolvimento ele contém
**um container só**, o `axctg3-postgres`, que tem só o banco. No servidor, o mesmo projeto
tem **dois containers**: `axctg3-postgres` e `axctg3-app`. A regra é um processo principal
por container; o Compose agrupa os containers e os liga numa rede interna. No
desenvolvimento, o app continua rodando pelo IntelliJ. **Não rode o compose do servidor na
máquina de dev**: os nomes colidem de propósito.

### Java + jar + banco direto na VM: ninguém faz mais isso?
Ainda se faz e funciona (Java e Postgres via `apt`, jar como serviço `systemd`). O Docker
virou o mais comum pelas vantagens: pacote com tudo junto, "igual ao testado", montagem de
servidor novo em poucos passos, *rollback* trocando uma linha. Para o axctg3 fica o Docker:
já está pronto, e o Dockerfile resolve fontes, fuso e locale.

### Antes do Spring Boot: WAR e Tomcat
O `.war` ia para dentro de um Tomcat instalado e configurado à mão (vários sistemas por
Tomcat). O Spring Boot **inverteu**: o Tomcat vai **dentro** do jar (Tomcat embutido), e
`java -jar` basta.
```
WAR + Tomcat instalado → JAR com Tomcat embutido → JAR dentro de uma imagem Docker
```

### Onde está o Java do container?
Na **imagem**. A base `eclipse-temurin:21-jre` já traz um Linux mínimo com o Java 21
(`/opt/java/openjdk`). A imagem é uma **boneca russa**:
```
Imagem Docker
 └─ Linux mínimo + Java 21 + fontes + fuso
     └─ axctg3.jar
         └─ Spring Boot + Jmix + Tomcat embutido + nosso código
```
O container **não é uma VM**: o "Linux" da imagem são só os arquivos, e quem executa é o
kernel do servidor, compartilhado. Cada camada é guardada separadamente, então uma
atualização troca só a camada do jar.

### Volume
Um disco externo do container. A imagem é somente leitura, e o que o container grava
some quando ele é recriado, o que acontece em toda atualização. Os volumes sobrevivem:

| Volume | Ligado em | Guarda |
|---|---|---|
| `axctg3_axctg3_pgdata` (dev) | Postgres | Banco de desenvolvimento |
| `axctg3_pgdata` (servidor) | Postgres | Banco do servidor |
| `axctg3_arquivos` (servidor) | App | `FileStorage`: logos, certificados, remessas |

`external: true` impede o Compose de criar ou apagar o volume (protege contra
`docker compose down -v`). No Linux eles ficam em `/var/lib/docker/volumes/`; no Docker
Desktop, dentro da VM do WSL2. **O backup é dos volumes**: containers e imagens se recriam.

### Docker Compose
A receita de um **ambiente** (vários containers, rede, volumes, ordem, reinício), em
`docker-compose.yml`. Substitui uma série de `docker network create` e `docker run` longos.

| Comando | Faz |
|---|---|
| `docker compose up -d` | Sobe, ou aplica mudanças (recria só o que mudou) |
| `docker compose ps` | Estado |
| `docker compose logs -f app` | Log ao vivo |
| `docker compose stop` / `start` | Para ou retoma |
| `docker compose down` | Remove os containers (os volumes ficam) |

### Dockerfile
A receita de **uma imagem**. O nosso: `FROM eclipse-temurin:21-jre` (base), `RUN apt-get
install fonts-dejavu-core` (roda na montagem), `ENV` (fuso, locale, memória, workdir do
Jmix), `ARG`/`COPY` (o jar), `EXPOSE 8085` (só documenta; quem abre porta é o `ports:` do
compose) e `ENTRYPOINT` (roda a cada início do container).
```
código ──bootJar──→ jar ──docker build (Dockerfile)──→ imagem (.tar) ──compose + .env──→ containers
        (na máquina de desenvolvimento: gerar-imagem.ps1)              (no servidor)
```

### .env
Variáveis `NOME=valor` ao lado do compose, que substituem os `${NOME}`. O nosso tem
`AXCTG3_VERSAO` e `DB_SENHA`. O compose vai para o git; o `.env` **não** (no git só fica o
`.env.example`). Atualizar a versão = trocar uma linha e rodar `docker compose up -d`.
**Pegadinha:** a `DB_SENHA` só vale na primeira subida com o volume vazio; depois disso,
trocar a senha exige um comando no Postgres (ver `docs/SERVIDOR-TESTE.md`). Na VM:
`chmod 600 .env`.

## 8. Linux: comandos que voltaram

- **`chmod`**: permissões em octal, r=4, w=2, x=1, em três grupos: **u**ser (dono),
  **g**roup, **o**thers. `600` = `-rw-------`, só o dono lê e grava (`.env`, chave privada SSH,
  que o `ssh` recusa se estiver aberta; `~/.ssh` em `700`). `755` ou `chmod +x` para scripts.
- **`chown`** (*change owner*): muda dono e grupo (`chown usuario:grupo arq`, `-R` recursivo),
  quase sempre com `sudo`. Uso comum: devolver ao seu usuário o que foi criado com `sudo`.
  **Não mexer** no dono dos arquivos dos volumes Docker (o Postgres usa UID 999).
- **`sudo`** (*superuser do*): roda **um** comando como root, com a **sua** senha. Substitui o
  hábito antigo do `su`. Não é gerenciador de pacotes.
- **`apt`**: o gerenciador de pacotes "esperto" do Debian/Ubuntu (baixa e resolve
  dependências) por cima do `dpkg`, como `yum`/`dnf` por cima do `rpm`. O Conectiva (Curitiba,
  baseado no Red Hat) foi quem adaptou o apt para RPM (apt-rpm); depois se juntou à Mandrake
  e virou Mandriva (hoje seguem Mageia e OpenMandriva).
  `apt update` só atualiza o catálogo; quem atualiza o sistema é o `apt upgrade`. Na VM, o
  apt serve só para atualizações de segurança e para instalar o Docker.
- **`tar`**: o `.tar` do `gerar-imagem.ps1` é um tar de verdade (camadas da imagem dentro).
- **`systemd`**: o processo 1 (PID 1) e gerenciador de serviços, que substituiu o `init` do
  System V (`/etc/rc.d`, *runlevels*, `S20`/`K80`). `systemctl status|restart|enable docker`,
  `journalctl -u docker`. Cadeia no boot:
  ```
  systemd → serviço docker (enable) → axctg3-postgres + axctg3-app (restart: unless-stopped)
  ```

## 9. Pendências

- **Conta na nuvem:** Oracle travada por exigir e-mail corporativo; a Magalu Cloud passou a
  ser a candidata. Decidir: Magalu só para produção ou também para teste? Banco em container
  ou DBaaS?
- **Domínio e e-mail próprios** (resolve e-mail corporativo, endereço do sistema e HTTPS).
- **Script de backup "nuvem":** adaptar o `backup-dev-db.ps1` para dump + volume de arquivos
  via SSH, conferido com `pg_restore -l`, guardando os 14 mais recentes, agendado no Windows.
- **Primeiro acesso SSH**, com geração de chave, ao criar a VM.
- **HTTPS** com proxy reverso (Caddy) e Let's Encrypt, depois do domínio.
