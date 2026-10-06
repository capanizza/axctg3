# Backup da VM da nuvem (deploy/nuvem) via SSH: banco (pg_dump -Fc) + volume de arquivos
# (FileStorage: logos, certificados A1, remessas). Gera <Destino>\<yyyyMMdd-HHmmss>\ com
# axctg3.dump, arquivos.tgz e sha256.txt; mantem so os <Manter> mais recentes.
# Log em <Destino>\backup.log.
#
# Na VM o dump e conferido com pg_restore -l antes de sair; no Windows, cada arquivo e
# conferido contra o sha256 calculado na VM (pega copia truncada/corrompida no caminho).
#
# Usa ssh com BatchMode: a chave precisa estar carregada no ssh-agent do Windows (senao
# falha na hora com "Permission denied", em vez de ficar esperando a passphrase).
# Ver docs/NUVEM-CONVERSA-2026-10-06.md.
#
# Uso manual:   powershell -NoProfile -ExecutionPolicy Bypass -File scripts\backup-nuvem.ps1
#
# Arquivo so em ASCII de proposito: o PowerShell 5.1 le .ps1 sem BOM como ANSI.

param(
    [string]$Servidor = "ubuntu@201.23.79.163",
    [string]$Destino = "C:\backups\axctg3-nuvem",
    [int]$Manter = 14
)

$carimbo = Get-Date -Format "yyyyMMdd-HHmmss"
$remoto  = "/tmp/axctg3-backup-$carimbo"
$local   = Join-Path $Destino $carimbo
$opcoes  = @("-o", "BatchMode=yes", "-o", "ConnectTimeout=20")

New-Item -ItemType Directory -Force -Path $Destino | Out-Null
$log = Join-Path $Destino "backup.log"

function Log([string]$msg) {
    $linha = "{0} {1}" -f (Get-Date -Format "yyyy-MM-dd HH:mm:ss"), $msg
    # Tee-Object no PowerShell 5.1 grava UTF-16; Add-Content deixa o log legivel.
    Add-Content -Path $log -Value $linha -Encoding UTF8
    Write-Output $linha
}

function Ssh([string]$comando) {
    & ssh.exe @opcoes $Servidor $comando
    if ($LASTEXITCODE -ne 0) { throw "ssh falhou (exit $LASTEXITCODE): $comando" }
}

try {
    # Tudo do lado da VM numa linha so, sem aspas nem variaveis do bash: o PowerShell 5.1
    # estraga aspas internas ao passar argumentos para programas nativos. Os redirecionamentos
    # (> e <) rodam no bash da VM, entao o dump binario nunca passa pelo PowerShell.
    Ssh (@(
        "set -e",
        "mkdir -p $remoto",
        "docker exec axctg3-postgres pg_dump -U postgres -Fc axctg3 > $remoto/axctg3.dump",
        "docker exec -i axctg3-postgres pg_restore -l < $remoto/axctg3.dump > /dev/null",
        # Volume lido por um container descartavel (imagem do postgres, ja presente na VM).
        "docker run --rm -v axctg3_arquivos:/dados:ro -v ${remoto}:/saida postgres:16 tar czf /saida/arquivos.tgz -C /dados .",
        "cd $remoto",
        "sha256sum axctg3.dump arquivos.tgz > sha256.txt"
    ) -join "; ")

    New-Item -ItemType Directory -Force -Path $local | Out-Null
    & scp.exe @opcoes -q "${Servidor}:$remoto/*" $local
    if ($LASTEXITCODE -ne 0) { throw "scp falhou (exit $LASTEXITCODE)" }

    # sha256.txt: "<hash>  <arquivo>" por linha.
    $resumo = @()
    foreach ($linha in Get-Content (Join-Path $local "sha256.txt")) {
        $hash, $nome = $linha -split "\s+", 2
        $nome = $nome.TrimStart("*")
        $caminho = Join-Path $local $nome
        if (-not (Test-Path $caminho)) { throw "faltou $nome" }
        $calculado = (Get-FileHash -Algorithm SHA256 $caminho).Hash
        if ($calculado -ne $hash.ToUpper()) { throw "sha256 diferente em $nome" }
        $resumo += "{0} {1:N1} MB" -f $nome, ((Get-Item $caminho).Length / 1MB)
    }
    if ($resumo.Count -ne 2) { throw "sha256.txt com $($resumo.Count) arquivo(s), esperava 2" }

    $antigos = Get-ChildItem $Destino -Directory |
        Where-Object { $_.Name -match '^\d{8}-\d{6}$' } |
        Sort-Object Name -Descending | Select-Object -Skip $Manter
    $antigos | Remove-Item -Recurse -Force

    Log ("OK {0}: {1}; {2} antigo(s) removido(s)" -f $carimbo, ($resumo -join ", "), @($antigos).Count)
    $codigo = 0
}
catch {
    Log "ERRO: $_"
    # Backup pela metade nao conta: some com a pasta local para nao confundir num restore.
    if (Test-Path $local) { Remove-Item -Recurse -Force $local }
    $codigo = 1
}
finally {
    # A copia na VM so serve de passagem; nao deixa acumular no disco de 20 GB.
    & ssh.exe @opcoes $Servidor "rm -rf $remoto" 2>&1 | Out-Null
}
exit $codigo
