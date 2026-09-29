$ErrorActionPreference = 'Stop'
$scriptPath = Join-Path $PSScriptRoot 'refresh-mercadolivre.ps1'
$action = New-ScheduledTaskAction -Execute 'powershell.exe' -Argument ('-NoProfile -NonInteractive -WindowStyle Hidden -File "' + $scriptPath + '"')
$trigger = New-ScheduledTaskTrigger -Once -At (Get-Date).AddMinutes(1) -RepetitionInterval (New-TimeSpan -Minutes 5)
$settings = New-ScheduledTaskSettingsSet -StartWhenAvailable -MultipleInstances IgnoreNew -ExecutionTimeLimit (New-TimeSpan -Minutes 4) -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries
$principal = New-ScheduledTaskPrincipal -UserId ([Security.Principal.WindowsIdentity]::GetCurrent().Name) -LogonType Interactive -RunLevel Limited
Register-ScheduledTask -TaskName 'Milize-MercadoLivre-Refresh' -Action $action -Trigger $trigger -Settings $settings -Principal $principal -Description 'Renova OAuth do Mercado Livre e recarrega o bot quando necessario.' -Force | Out-Null
Write-Output 'Renovacao automatica instalada: verificacao a cada 5 minutos durante a sessao Windows.'
