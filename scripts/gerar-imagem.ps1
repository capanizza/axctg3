# Gera a imagem Docker do axctg3 para o servidor de teste e salva num .tar para levar ate la.
#   1. gradlew -Pvaadin.productionMode=true clean bootJar   (jar com o front-end de producao)
#   2. docker build -t axctg3:<Versao>                        (Dockerfile na raiz do projeto)
#   3. docker save  -> <Destino>\axctg3-<Versao>.tar
#
# Uso (na raiz do projeto):
#   powershell -NoProfile -ExecutionPolicy Bypass -File scripts\gerar-imagem.ps1 -Versao 1.0.0
#
# -Plataforma arm64 gera a imagem para servidor ARM (ex.: VM Ampere A1 do Oracle Always Free)
# -> <Destino>\axctg3-<Versao>-arm64.tar. O jar e o mesmo; so a imagem base (Java, fontes)
# muda de arquitetura. O Docker Desktop monta via emulacao (QEMU), entao o apt-get do
# Dockerfile fica mais lento. Essa imagem vai direto para o .tar, sem entrar no Docker
# local -- nao roda nesta maquina e nao colide com a amd64 de mesma versao.
# A tag dentro do .tar continua axctg3:<Versao>, entao o compose do servidor nao muda.
#
# No servidor: docker load -i axctg3-<Versao>.tar, depois AXCTG3_VERSAO=<Versao> no .env e
# docker compose up -d. Roteiro completo em docs\SERVIDOR-TESTE.md.
#
# Arquivo so em ASCII de proposito: o PowerShell 5.1 le .ps1 sem BOM como ANSI.

param(
    [Parameter(Mandatory = $true)][string]$Versao,
    [string]$Destino = "C:\axctg3-imagens",
    [ValidateSet("amd64", "arm64")][string]$Plataforma = "amd64"
)

$ErrorActionPreference = "Stop"
$docker = "C:\Program Files\Docker\Docker\resources\bin\docker.exe"
$raiz   = Split-Path -Parent $PSScriptRoot
$imagem = "axctg3:$Versao"
$sufixo = if ($Plataforma -eq "amd64") { "" } else { "-$Plataforma" }
$tar    = Join-Path $Destino "axctg3-$Versao$sufixo.tar"

function Passo([string]$msg) { Write-Host ""; Write-Host "==> $msg" -ForegroundColor Cyan }

function Executar([string]$exe) {
    & $exe @args
    if ($LASTEXITCODE -ne 0) { throw "$exe $($args -join ' ') falhou (exit $LASTEXITCODE)" }
}

Push-Location $raiz
try {
    # "images -q" em vez de "image inspect": nao escreve no stderr quando a imagem nao
    # existe (stderr redirecionado + ErrorActionPreference=Stop derruba o PowerShell 5.1).
    if ($Plataforma -eq "amd64") {
        if (& $docker images -q $imagem) {
            throw "A imagem $imagem ja existe. Use outra versao (cada entrega = uma versao nova)."
        }
    }
    elseif (Test-Path $tar) {
        throw "$tar ja existe. Use outra versao (cada entrega = uma versao nova)."
    }

    Passo "Compilando o jar de producao"
    Executar ".\gradlew.bat" --no-daemon "-Pvaadin.productionMode=true" "-Pversao=$Versao" clean bootJar

    $jars = @(Get-ChildItem "build\libs\*.jar" | Where-Object { $_.Name -notlike "*-plain.jar" })
    if ($jars.Count -ne 1) { throw "Esperava 1 jar em build\libs, achei $($jars.Count)" }
    $jar = "build/libs/" + $jars[0].Name

    New-Item -ItemType Directory -Force -Path $Destino | Out-Null
    if ($Plataforma -eq "amd64") {
        Passo "Montando a imagem $imagem"
        Executar $docker build --build-arg "JAR_FILE=$jar" -t $imagem .

        Passo "Salvando em $tar"
        Executar $docker save -o $tar $imagem
    }
    else {
        Passo "Montando a imagem $imagem (linux/$Plataforma) direto em $tar"
        Executar $docker buildx build --platform "linux/$Plataforma" --build-arg "JAR_FILE=$jar" `
            -t $imagem --output "type=docker,dest=$tar" .
    }

    Passo ("Pronto: {0} ({1:N0} MB)" -f $tar, ((Get-Item $tar).Length / 1MB))
}
finally {
    Pop-Location
}
