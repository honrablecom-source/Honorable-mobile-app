#Requires -RunAsAdministrator
[CmdletBinding()] param(
  [Parameter(Mandatory)][string]$PackagePath,
  [string]$InstallRoot = "$env:ProgramData\RobloxCloudAgent"
)
$ErrorActionPreference = 'Stop'
& "$PSScriptRoot\setup-windows.ps1" -InstallRoot $InstallRoot
$resolvedPackage = (Resolve-Path $PackagePath).Path
if (-not (Test-Path "$resolvedPackage\dist\main.js")) { throw 'PackagePath must contain the compiled windows-agent dist directory.' }
Copy-Item -Path "$resolvedPackage\*" -Destination $InstallRoot -Recurse -Force
$acl = Get-Acl $InstallRoot
$acl.SetAccessRuleProtection($true, $false)
$admin = New-Object System.Security.AccessControl.FileSystemAccessRule('BUILTIN\Administrators','FullControl','ContainerInherit,ObjectInherit','None','Allow')
$system = New-Object System.Security.AccessControl.FileSystemAccessRule('NT AUTHORITY\SYSTEM','FullControl','ContainerInherit,ObjectInherit','None','Allow')
$user = New-Object System.Security.AccessControl.FileSystemAccessRule("$env:USERDOMAIN\$env:USERNAME",'ReadAndExecute','ContainerInherit,ObjectInherit','None','Allow')
$acl.AddAccessRule($admin); $acl.AddAccessRule($system); $acl.AddAccessRule($user); Set-Acl $InstallRoot $acl
Write-Host 'Agent files installed. Create .env using the documented protected environment configuration, then create the startup task.'
