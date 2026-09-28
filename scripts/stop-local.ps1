$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$runtime = Join-Path $projectRoot '.runtime'
$stateFile = Join-Path $runtime 'local-state.json'
$jar = Join-Path $projectRoot 'target\price-alert-bot-0.1.0.jar'
if (Test-Path $stateFile) {
    $state = Get-Content -LiteralPath $stateFile -Raw | ConvertFrom-Json
    $running = Get-CimInstance Win32_Process -Filter ("ProcessId = " + [int]$state.processId) -ErrorAction SilentlyContinue
    if ($running) {
        if ($running.CommandLine -notlike ('*' + $jar + '*')) { throw 'PID pertence a outro processo; parada cancelada.' }
        Stop-Process -Id ([int]$state.processId)
    }
    Remove-Item -LiteralPath $stateFile
}
$pgCtl = Join-Path $runtime 'postgres\bin\pg_ctl.exe'
$data = Join-Path $runtime 'postgres-data'
if ((Test-Path $pgCtl) -and (Test-Path (Join-Path $data 'PG_VERSION'))) {
    & $pgCtl -D $data status *> $null
    if ($LASTEXITCODE -eq 0) {
        & $pgCtl -D $data -m fast -w stop
        if ($LASTEXITCODE -ne 0) { throw 'Confira o estado do PostgreSQL antes de encerrar o computador.' }
    }
}
Write-Output 'Bot parado. O banco e o historico foram preservados.'
