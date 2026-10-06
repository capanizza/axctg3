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

## 5. Próximos passos

- **A. Backup via SSH** (pendente desde 01/10): script `.ps1` no Windows que roda
  `pg_dump -Fc` no container da VM, copia o volume `axctg3_arquivos` e traz os dois para
  `C:\backups\axctg3-nuvem`, guardando os N mais recentes. Depois, treinar o restore.
- **B. Ciclo de atualização:** gerar a 1.0.1, `scp`, `docker load`, trocar `AXCTG3_VERSAO`
  no `.env`, `docker compose up -d`, e treinar a volta para a 1.0.0.
- Opcional: travar a regra da porta 22 na Magalu para o IP de casa/escritório, agora que o
  acesso do dia a dia não depende mais do túnel.
