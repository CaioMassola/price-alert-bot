$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
& 'C:\Program Files\nodejs\node.exe' (Join-Path $PSScriptRoot 'refresh-mercadolivre.mjs') *> (Join-Path $projectRoot '.runtime\ml-refresh.log')
exit $LASTEXITCODE
