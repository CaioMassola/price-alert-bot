$ErrorActionPreference = 'Stop'
$envFile = Join-Path $PSScriptRoot '..\.env'
$webhook = ''
foreach ($line in [System.IO.File]::ReadAllLines($envFile)) {
    if ($line -match '^DISCORD_WEBHOOK_URL=(.*)$') { $webhook = $Matches[1].Trim() }
}
if ($webhook -notmatch '^https://discord\.com/api/webhooks/[0-9]+/[A-Za-z0-9_-]+$') {
    Write-Output 'Webhook ausente ou formato inválido no .env.'
    exit 1
}
$payload = @{
    allowed_mentions = @{ parse = @() }
    embeds = @(@{
        title = 'Bem-vindo ao meu canal de ofertas! 💜'
        description = "Eu sou a Milizé! ✨`n`nEste é o nosso espaço para descobrir promoções, cupons e bons descontos em produtos de diversas categorias. 🛍️`n`nAtive as notificações para acompanhar as novidades e encontrar oportunidades de economizar! 🔔`n`nAntes de comprar, confira o preço final, o frete e as condições da oferta na loja."
        color = 3066993
    })
} | ConvertTo-Json -Depth 5
try {
    $reply = Invoke-RestMethod -Uri ($webhook + '?wait=true') -Method Post -ContentType 'application/json; charset=utf-8' -Body ([System.Text.Encoding]::UTF8.GetBytes($payload)) -TimeoutSec 25
    if ($reply.id) { Write-Output 'Discord confirmou o recebimento da mensagem de teste.' }
    else { Write-Output 'Resposta sem confirmação. Confira o canal antes de repetir.'; exit 2 }
} catch {
    $status = 0
    if ($_.Exception.Response) { $status = [int]$_.Exception.Response.StatusCode }
    Write-Output ("Envio não confirmado. HTTP: {0}. Confira o canal antes de repetir." -f $status)
    exit 2
}
