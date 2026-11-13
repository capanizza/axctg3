# Túnel SSH para o Postgres da nuvem

O Postgres da VM de produção (Magalu, `201.23.79.163`) só escuta em `127.0.0.1:5432`
**dentro da VM**: não fica exposto à internet. Para o exportador do Axial gravar os lotes
`IMP_*`, abra um túnel SSH que liga a porta **5434** do seu PC a essa porta da VM.

## Abrir

1. Abra um **PowerShell** ou Prompt de Comando no Windows (não precisa ser administrador).
2. Rode:

   ```
   ssh -N -o ServerAliveInterval=30 -L 5434:localhost:5432 ubuntu@201.23.79.163
   ```

3. **Deixe a janela aberta.** O cursor fica parado e não aparece nada: isso é o normal,
   quer dizer que o túnel está ativo. Pode minimizar.
4. Rode o exportador do Axial; ele grava em `localhost:5434`.
5. Quando terminar, aperte **Ctrl+C** na janela do túnel para fechá-lo.

## O que cada parte faz

| Parte | Função |
|---|---|
| `-N` | não abre um terminal na VM, só cria o túnel |
| `-o ServerAliveInterval=30` | manda um sinal a cada 30 s para a rede não derrubar o túnel enquanto a consulta lenta do Firebird roda |
| `-L 5434:localhost:5432` | a porta 5434 do PC passa a levar à porta 5432 dentro da VM |
| `ubuntu@201.23.79.163` | usuário e IP da VM |

## Problemas comuns

- **`Connection timed out`**: quase sempre o IP de casa (dinâmico) mudou. Veja o IP novo em
  https://api.ipify.org. No console da Magalu, no grupo de segurança, **crie** uma regra
  nova da porta 22 com o IP novo (`/32`) e **exclua** a antiga, porque lá não dá para
  editar uma regra. O backup diário da nuvem (`scripts/backup-nuvem.ps1`) também
  depende disso.
- **Pede a senha da chave (passphrase)**: o ssh-agent do Windows não está com a chave.
  Rode `ssh-add` uma vez e digite a senha.
- **O túnel caiu no meio da exportação**: abra de novo (passo 2) e rode o exportador de
  novo. Ele substitui o lote que ainda não foi importado.
- **`bind ... Address already in use`**: já existe outro túnel aberto na 5434. Use o que
  está aberto ou feche a janela dele.

## Ver se a VM está de pé

```
ssh ubuntu@201.23.79.163 "docker ps"
```

O comando deve listar `axctg3-caddy`, `axctg3-app` e `axctg3-postgres (healthy)`.
