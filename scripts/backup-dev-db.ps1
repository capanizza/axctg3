# Backup do banco de dev (container axctg3-postgres) com pg_dump -Fc.
# Gera <Destino>\axctg3-<yyyyMMdd-HHmmss>.dump, confere o arquivo com pg_restore -l
# e mantem so os <Manter> mais recentes. Log em <Destino>\backup.log.
#
# Uso manual:   powershell -NoProfile -ExecutionPolicy Bypass -File scripts\backup-dev-db.ps1
# Restaurar:    ver "Dev DB" no CLAUDE.md (pg_restore --no-owner num banco vazio).
#
# Arquivo so em ASCII de proposito: o PowerShell 5.1 le .ps1 sem BOM como ANSI.

param(
    [string]$Destino = "C:\backups\axctg3",
    [int]$Manter = 14
)

$docker    = "C:\Program Files\Docker\Docker\resources\bin\docker.exe"
$container = "axctg3-postgres"
$arquivo   = "axctg3-{0}.dump" -f (Get-Date -Format "yyyyMMdd-HHmmss")
$tmp       = "/tmp/$arquivo"
$final     = Join-Path $Destino $arquivo

New-Item -ItemType Directory -Force -Path $Destino | Out-Null
$log = Join-Path $Destino "backup.log"

function Log([string]$msg) {
    $linha = "{0} {1}" -f (Get-Date -Format "yyyy-MM-dd HH:mm:ss"), $msg
    # Tee-Object no PowerShell 5.1 grava UTF-16; Add-Content deixa o log legivel.
    Add-Content -Path $log -Value $linha -Encoding UTF8
    Write-Output $linha
}

function Docker {
    & $docker @args
    if ($LASTEXITCODE -ne 0) { throw "docker $($args -join ' ') falhou (exit $LASTEXITCODE)" }
}

try {
    Docker exec $container pg_dump -U postgres -Fc -f $tmp axctg3
    # Le o indice do dump inteiro: pega arquivo truncado/corrompido antes de confiar nele.
    Docker exec $container pg_restore -l $tmp | Out-Null
    Docker cp "${container}:$tmp" $final 2>&1 | Out-Null
    Docker exec $container rm -f $tmp

    $tamanho = (Get-Item $final).Length
    if ($tamanho -eq 0) { throw "dump vazio: $final" }

    $antigos = Get-ChildItem $Destino -Filter "axctg3-*.dump" |
        Sort-Object Name -Descending | Select-Object -Skip $Manter
    $antigos | Remove-Item

    Log ("OK {0} ({1:N1} MB), {2} antigo(s) removido(s)" -f $arquivo, ($tamanho / 1MB), @($antigos).Count)
    exit 0
}
catch {
    Log "ERRO: $_"
    exit 1
}
