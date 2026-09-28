param([int]$Port = 8080)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$runtime = Join-Path $projectRoot '.runtime'
$java = Join-Path $runtime 'java\bin\java.exe'
$pgBin = Join-Path $runtime 'postgres\bin'
$data = Join-Path $runtime 'postgres-data'
$jar = Join-Path $projectRoot 'target\price-alert-bot-0.1.0.jar'
$stateFile = Join-Path $runtime 'local-state.json'

function Assert-FreePort([int]$Number) {
    $listener = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Loopback, $Number)
    try { $listener.Start() }
    catch { throw "Porta $Number ocupada. Nenhum processo existente foi encerrado." }
    finally { $listener.Stop() }
}

if (Test-Path $stateFile) {
    $state = Get-Content -LiteralPath $stateFile -Raw | ConvertFrom-Json
    $running = Get-CimInstance Win32_Process -Filter ("ProcessId = " + [int]$state.processId) -ErrorAction SilentlyContinue
    if ($running -and $running.CommandLine -like ('*' + $jar + '*')) {
        Write-Output ("Bot ja esta rodando em http://localhost:" + $state.port)
        exit 0
    }
}
if (-not (Test-Path $java) -or -not (Test-Path (Join-Path $pgBin 'pg_ctl.exe'))) {
    throw 'Execute primeiro .\scripts\setup-local.ps1.'
}
if (-not (Test-Path $jar)) { throw 'Gere o executavel com .\mvnw.cmd package.' }
Assert-FreePort $Port

$settings = @{}
foreach ($line in [System.IO.File]::ReadAllLines((Join-Path $projectRoot '.env'))) {
    if ($line -match '^([A-Z_]+)=(.*)$') { $settings[$Matches[1]] = $Matches[2].Trim() }
}
if ($settings.DATABASE_URL -notmatch '^jdbc:postgresql://(?:localhost|127\.0\.0\.1):([0-9]+)/([A-Za-z_][A-Za-z0-9_]*)$') {
    throw 'DATABASE_URL deve apontar para PostgreSQL local com porta e nome do banco.'
}
$pgPort = [int]$Matches[1]
if ($settings.DATABASE_USERNAME -notmatch '^[A-Za-z_][A-Za-z0-9_]*$' -or -not $settings.DATABASE_PASSWORD) {
    throw 'Configure usuario e senha do banco no .env.'
}
New-Item -ItemType Directory -Path $runtime -Force | Out-Null

if (-not (Test-Path (Join-Path $data 'PG_VERSION'))) {
    Assert-FreePort $pgPort
    $passwordFile = Join-Path $runtime 'init-password.tmp'
    try {
        [System.IO.File]::WriteAllText($passwordFile, $settings.DATABASE_PASSWORD, [System.Text.UTF8Encoding]::new($false))
        & (Join-Path $pgBin 'initdb.exe') -D $data -U $settings.DATABASE_USERNAME --pwfile=$passwordFile --encoding=UTF8 --locale=C --auth-host=scram-sha-256 --auth-local=scram-sha-256 *> (Join-Path $runtime 'initdb.log')
        if ($LASTEXITCODE -ne 0) { throw 'Falha ao criar banco. Consulte .runtime\initdb.log.' }
        $config = "`nlisten_addresses = '127.0.0.1'`nport = $pgPort`nmax_connections = 40`npassword_encryption = 'scram-sha-256'`n"
        [System.IO.File]::AppendAllText((Join-Path $data 'postgresql.conf'),$config,[System.Text.UTF8Encoding]::new($false))
    } finally {
        if (Test-Path -LiteralPath $passwordFile) { Remove-Item -LiteralPath $passwordFile }
    }
}
& (Join-Path $pgBin 'pg_ctl.exe') -D $data status *> $null
if ($LASTEXITCODE -ne 0) {
    Assert-FreePort $pgPort
    $pg = Start-Process -FilePath (Join-Path $pgBin 'pg_ctl.exe') -ArgumentList @('-D',('"' + $data + '"'),'-l',('"' + (Join-Path $runtime 'postgres.log') + '"'),'-w','-t','30','start') -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $runtime 'postgres-start.log') -RedirectStandardError (Join-Path $runtime 'postgres-start-error.log')
    if (-not $pg.WaitForExit(35000)) { throw 'PostgreSQL nao confirmou a inicializacao em 35 segundos.' }
    & (Join-Path $pgBin 'pg_ctl.exe') -D $data status *> $null
    if ($LASTEXITCODE -ne 0) { throw 'Falha ao iniciar PostgreSQL. Consulte .runtime\postgres.log.' }
}

# Reuse the production JDBC driver, without adding tools or passwords to PATH.
Add-Type -AssemblyName System.IO.Compression.FileSystem
$driver = Join-Path $runtime 'postgresql-jdbc.jar'
$zip = [System.IO.Compression.ZipFile]::OpenRead($jar)
try {
    $entry = $zip.Entries | Where-Object { $_.FullName -match '^BOOT-INF/lib/postgresql-[^/]+\.jar$' } | Select-Object -First 1
    if (-not $entry) { throw 'Driver JDBC nao encontrado no executavel.' }
    [System.IO.Compression.ZipFileExtensions]::ExtractToFile($entry,$driver,$true)
} finally { $zip.Dispose() }
Push-Location $projectRoot
try {
    & $java --class-path $driver (Join-Path $PSScriptRoot 'PrepareDatabase.java')
    if ($LASTEXITCODE -ne 0) { throw 'Banco nao ficou pronto. Confira as credenciais locais.' }
    $app = Start-Process -FilePath $java -ArgumentList @('-jar',('"' + $jar + '"'),"--server.port=$Port",'--server.address=127.0.0.1','--debug=false') -WorkingDirectory $projectRoot -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $runtime 'bot.log') -RedirectStandardError (Join-Path $runtime 'bot-error.log')
    @{ processId=$app.Id; port=$Port; databasePort=$pgPort; startedAt=[DateTime]::UtcNow.ToString('o') } | ConvertTo-Json | Set-Content -LiteralPath $stateFile -Encoding UTF8
} finally { Pop-Location }
for ($attempt=0; $attempt -lt 30; $attempt++) {
    Start-Sleep -Milliseconds 500
    if ($app.HasExited) { throw 'Bot encerrou durante a inicializacao. Consulte .runtime\bot.log.' }
    try {
        $health = Invoke-RestMethod -Uri "http://127.0.0.1:$Port/actuator/health" -TimeoutSec 2
        if ($health.status -eq 'UP') {
            Write-Output "Milize iniciada: http://localhost:$Port"
            Write-Output 'Status: /api/status | Produtos: /api/products | Ofertas: /api/deals'
            Write-Output 'Logs: .runtime\bot.log | Parar: .\scripts\stop-local.ps1'
            exit 0
        }
    } catch { }
}
throw 'Inicializacao ainda nao confirmada. Consulte .runtime\bot.log antes de repetir.'
