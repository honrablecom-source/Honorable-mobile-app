#Requires -RunAsAdministrator
[CmdletBinding()] param([string]$InstallRoot = "$env:ProgramData\RobloxCloudAgent", [string]$RunAsUser = "$env:USERDOMAIN\$env:USERNAME")
$ErrorActionPreference = 'Stop'
$node = (Get-Command node.exe -ErrorAction Stop).Source
$action = New-ScheduledTaskAction -Execute $node -Argument 'dist/main.js' -WorkingDirectory $InstallRoot
$trigger = New-ScheduledTaskTrigger -AtLogOn -User $RunAsUser
$settings = New-ScheduledTaskSettingsSet -RestartCount 5 -RestartInterval (New-TimeSpan -Minutes 1) -ExecutionTimeLimit ([TimeSpan]::Zero) -StartWhenAvailable -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries
$principal = New-ScheduledTaskPrincipal -UserId $RunAsUser -LogonType Interactive -RunLevel Limited
Register-ScheduledTask -TaskName 'RobloxCloudAgent' -Action $action -Trigger $trigger -Settings $settings -Principal $principal -Force | Out-Null
Write-Host 'Startup task created. It runs only in the interactive user session so Roblox and RDP share the same desktop.'
