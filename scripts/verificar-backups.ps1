# Confere os backups do axctg3 (nuvem e banco de dev) e abre uma janela de alerta quando
# algum esta com problema. Silencioso quando esta tudo bem.
#
# Problema = a ultima linha do backup.log e um ERRO, ou o ultimo OK tem mais de <Horas>
# horas (pega tambem a tarefa de backup que nem rodou: PC desligado, tarefa desativada).
#
# Quem chama:
#   - tarefa "axctg3 verificar backups": 20 min depois do logon e todo dia as 13:15;
#   - backup-nuvem.ps1 e backup-dev-db.ps1, na hora em que um backup falha (-SemEspera).
#
# Teste da janela (nao olha os logs):
#   powershell -NoProfile -ExecutionPolicy Bypass -File scripts\verificar-backups.ps1 -Teste
#
# Salvo em UTF-8 COM BOM de proposito: o PowerShell 5.1 le .ps1 sem BOM como ANSI, e a
# mensagem tem acentos. Se editar, mantenha o BOM.

param(
    [int]$Horas = 30,
    [switch]$Teste,
    # Chamado de dentro de um backup que acabou de falhar: a tarefa dele ainda consta como
    # "Running", entao nao adianta esperar ela terminar.
    [switch]$SemEspera
)

$backups = @(
    @{ Nome = "Backup da NUVEM"; Log = "C:\backups\axctg3-nuvem\backup.log"; Tarefa = "axctg3 backup nuvem" },
    @{ Nome = "Backup do banco de DEV"; Log = "C:\backups\axctg3\backup.log"; Tarefa = "axctg3 backup banco dev" }
)

function Esperar-Backups {
    # Logo depois do logon a tarefa de backup pode estar rodando (StartWhenAvailable):
    # espera ela terminar para não acusar um backup "atrasado" que está acontecendo agora.
    $limite = (Get-Date).AddMinutes(30)
    foreach ($b in $backups) {
        while ((Get-Date) -lt $limite) {
            $t = Get-ScheduledTask -TaskName $b.Tarefa -ErrorAction SilentlyContinue
            if ($t -eq $null -or $t.State -ne "Running") { break }
            Start-Sleep -Seconds 15
        }
    }
}

function Problema-Do-Backup($b) {
    if (-not (Test-Path $b.Log)) {
        return "$($b.Nome): o log $($b.Log) não existe."
    }
    $linhas = @(Get-Content $b.Log -Encoding UTF8 | Where-Object { $_ -match '^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2} ' })
    if ($linhas.Count -eq 0) {
        return "$($b.Nome): o log está vazio."
    }
    $ultima = $linhas[-1]
    $ultimoOk = $linhas | Where-Object { $_ -match '^\S+ \S+ OK ' } | Select-Object -Last 1
    $quandoOk = $null
    if ($ultimoOk) {
        $quandoOk = [datetime]::ParseExact($ultimoOk.Substring(0, 19), "yyyy-MM-dd HH:mm:ss", $null)
    }
    $desde = if ($quandoOk) { "Último backup bom: " + $quandoOk.ToString("dd/MM/yyyy HH:mm") + "." } else { "Nenhum backup bom no log." }

    if ($ultima -match '^\S+ \S+ ERRO') {
        $erro = $ultima.Substring(20)
        if ($erro.Length -gt 160) { $erro = $erro.Substring(0, 160) + "..." }
        $quando = [datetime]::ParseExact($ultima.Substring(0, 19), "yyyy-MM-dd HH:mm:ss", $null)
        return "$($b.Nome) FALHOU em " + $quando.ToString("dd/MM/yyyy HH:mm") + ".`n  $erro`n  $desde"
    }
    if (-not $quandoOk -or $quandoOk -lt (Get-Date).AddHours(-$Horas)) {
        return "$($b.Nome) ATRASADO: nenhum backup bom nas últimas $Horas horas.`n  $desde"
    }
    return $null
}

if ($Teste) {
    $problemas = @("TESTE: esta janela é só um teste do alerta. Nenhum backup foi conferido.")
}
else {
    if (-not $SemEspera) { Esperar-Backups }
    $problemas = @($backups | ForEach-Object { Problema-Do-Backup $_ } | Where-Object { $_ })
}
if ($problemas.Count -eq 0) { exit 0 }

$texto = ($problemas -join "`n`n") + "`n`n" + @"
O que verificar, nesta ordem:
1. "Connection timed out" ou "exit 255": o IP de casa mudou. Guia da nuvem, seção 4.
2. "Permission denied": a chave saiu do ssh-agent. Rode ssh-add no PowerShell.
3. Backup do dev: o Docker Desktop está aberto?
4. "Atrasado": o PC ficou desligado, ou a tarefa no Agendador de Tarefas está desativada.

Depois de corrigir, rode o backup à mão e confira a linha OK no backup.log.
"@

Add-Type -AssemblyName System.Windows.Forms
# Janela "dona" invisível e TopMost: a caixa de mensagem abre por cima de tudo, e não
# escondida atrás das outras janelas.
$dona = New-Object System.Windows.Forms.Form
$dona.TopMost = $true
$dona.ShowInTaskbar = $false
$dona.WindowState = "Minimized"
[System.Windows.Forms.MessageBox]::Show($dona, $texto, "axctg3 - problema no backup",
    [System.Windows.Forms.MessageBoxButtons]::OK, [System.Windows.Forms.MessageBoxIcon]::Warning) | Out-Null
$dona.Dispose()
exit 1
