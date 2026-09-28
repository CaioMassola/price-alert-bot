$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$runtime = Join-Path $projectRoot '.runtime'
$downloads = Join-Path $runtime 'downloads'
New-Item -ItemType Directory -Path $downloads -Force | Out-Null
$ProgressPreference = 'SilentlyContinue'

# Portable packages only: no global PATH, services or registry changes.
$javaDirectory = Join-Path $runtime 'java'
if (-not (Test-Path (Join-Path $javaDirectory 'bin\java.exe'))) {
    Write-Output 'Baixando Java 21 do Eclipse Adoptium...'
    $assets = Invoke-RestMethod 'https://api.adoptium.net/v3/assets/latest/21/hotspot?architecture=x64&image_type=jdk&os=windows&vendor=eclipse'
    $package = @($assets)[0].binary.package
    $archive = Join-Path $downloads 'java21.zip'
    Invoke-WebRequest -UseBasicParsing -Uri $package.link -OutFile $archive
    if ((Get-FileHash $archive -Algorithm SHA256).Hash -ne $package.checksum) { throw 'Checksum Java invalido.' }
    $unpacked = Join-Path $downloads 'jdk-unpacked'
    Expand-Archive -LiteralPath $archive -DestinationPath $unpacked -Force
    $jdk = Get-ChildItem -LiteralPath $unpacked -Directory | Where-Object { Test-Path (Join-Path $_.FullName 'bin\java.exe') } | Select-Object -First 1
    if (-not $jdk) { throw 'JDK nao encontrado no arquivo.' }
    Copy-Item -LiteralPath $jdk.FullName -Destination $javaDirectory -Recurse
}

$postgresDirectory = Join-Path $runtime 'postgres'
if (-not (Test-Path (Join-Path $postgresDirectory 'bin\pg_ctl.exe'))) {
    Write-Output 'Baixando PostgreSQL 17 portatil do pacote Zonky/Maven Central...'
    $base = 'https://repo.maven.apache.org/maven2/io/zonky/test/postgres/embedded-postgres-binaries-windows-amd64'
    [xml]$metadata = (Invoke-WebRequest -UseBasicParsing -Uri ($base + '/maven-metadata.xml')).Content
    $version = $metadata.metadata.versioning.versions.version | Where-Object { $_ -match '^17\.\d+\.0$' } | Sort-Object { [version]$_ } -Descending | Select-Object -First 1
    if (-not $version) { throw 'Pacote PostgreSQL 17 indisponivel.' }
    $artifact = 'embedded-postgres-binaries-windows-amd64-' + $version + '.jar'
    $archive = Join-Path $downloads 'postgres.jar'
    $url = $base + '/' + $version + '/' + $artifact
    Invoke-WebRequest -UseBasicParsing -Uri $url -OutFile $archive
    $expected = ((Invoke-WebRequest -UseBasicParsing -Uri ($url + '.sha256')).Content).Trim().Split(' ')[0]
    if ((Get-FileHash $archive -Algorithm SHA256).Hash -ne $expected) { throw 'Checksum PostgreSQL invalido.' }
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $zip = [System.IO.Compression.ZipFile]::OpenRead($archive)
    try {
        $entry = $zip.Entries | Where-Object { $_.Name -eq 'postgres-windows-x86_64.txz' } | Select-Object -First 1
        if (-not $entry) { throw 'Arquivo de binarios PostgreSQL nao encontrado.' }
        $tarFile = Join-Path $downloads 'postgres.txz'
        [System.IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $tarFile, $true)
    } finally { $zip.Dispose() }
    $entries = & tar.exe -tf $tarFile
    if ($LASTEXITCODE -ne 0 -or ($entries | Where-Object { $_ -match '(^[/\\]|(^|[/\\])\.\.([/\\]|$)|^[A-Za-z]:)' })) { throw 'Caminhos invalidos no arquivo PostgreSQL.' }
    New-Item -ItemType Directory -Path $postgresDirectory -Force | Out-Null
    & tar.exe -xf $tarFile -C $postgresDirectory
    if ($LASTEXITCODE -ne 0) { throw 'Falha ao extrair PostgreSQL.' }
}
& (Join-Path $javaDirectory 'bin\java.exe') -version
& (Join-Path $postgresDirectory 'bin\postgres.exe') --version
Write-Output 'Componentes locais prontos. Execute .\scripts\start-local.ps1.'
