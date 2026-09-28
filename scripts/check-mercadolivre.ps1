$ErrorActionPreference='Stop'
$envFile=Join-Path (Split-Path $PSScriptRoot -Parent) '.env'
$accessToken=''
foreach($line in [System.IO.File]::ReadAllLines($envFile)) {
    if($line -match '^MERCADO_LIVRE_ACCESS_TOKEN=(.*)$') { $accessToken=$Matches[1].Trim() }
}
if(-not $accessToken) {
    Write-Output 'MERCADO_LIVRE_ACCESS_TOKEN nao configurado. Consulte docs/ACESSO-LOJAS.md.'
    exit 1
}
$headers=@{Authorization=('Bearer '+$accessToken)}
try {
    $identity=Invoke-RestMethod -Uri 'https://api.mercadolibre.com/users/me' -Headers $headers -TimeoutSec 20
    if(-not $identity.id) { throw 'Resposta sem identidade' }
    Write-Output 'Autenticacao: OK. Dados pessoais nao exibidos.'
} catch {
    $status=0
    if($_.Exception.Response) { $status=[int]$_.Exception.Response.StatusCode }
    Write-Output ("Autenticacao nao confirmada. HTTP: {0}. Token nao exibido." -f $status)
    exit 2
}
Write-Output 'Aguardando 15 segundos antes de verificar a busca de produtos...'
Start-Sleep -Seconds 15
try {
    $result=Invoke-RestMethod -Uri 'https://api.mercadolibre.com/sites/MLB/search?q=teclado&limit=1' -Headers $headers -TimeoutSec 20
    if(-not ($result.PSObject.Properties.Name -contains 'results')) { throw 'Estrutura inesperada' }
    Write-Output ('Busca autorizada. Produtos nesta amostra: '+@($result.results).Count)
} catch {
    $status=0
    if($_.Exception.Response) { $status=[int]$_.Exception.Response.StatusCode }
    Write-Output ("Token autenticado, mas busca nao confirmada. HTTP: {0}." -f $status)
    if($status -eq 403) {
        Write-Output 'Acesso negado ao recurso. O status 403 sozinho nao identifica a causa e nao comprova falta de escopos.'
        Write-Output 'Consulte docs/ACESSO-LOJAS.md antes de alterar permissoes ou refazer o login.'
    }
    exit 3
}
