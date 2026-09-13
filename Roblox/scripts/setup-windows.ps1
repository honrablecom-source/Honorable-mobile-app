#Requires -Version 5.1
[CmdletBinding()] param([string]$InstallRoot = "$env:ProgramData\RobloxCloudAgent")
$ErrorActionPreference = 'Stop'
if (-not [Environment]::Is64BitOperatingSystem) { throw 'A 64-bit Windows installation is required.' }
if ((Get-CimInstance Win32_OperatingSystem).ProductType -ne 1) { Write-Warning 'Windows Server is not the recommended Roblox host; verify Roblox compatibility before continuing.' }
$node = Get-Command node.exe -ErrorAction SilentlyContinue
if (-not $node) { throw 'Node.js 22 LTS must be installed from the official Node.js distribution.' }
$version = (& $node.Source --version).TrimStart('v').Split('.')[0]
if ([int]$version -lt 22) { throw 'Node.js 22 or newer is required.' }
New-Item -ItemType Directory -Force -Path $InstallRoot, "$InstallRoot\data" | Out-Null
Write-Host "Prerequisites verified. Install root: $InstallRoot"
