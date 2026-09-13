[CmdletBinding()] param([string]$ApiUrl = $env:API_URL)
$ErrorActionPreference = 'Stop'
if (-not $ApiUrl) { throw 'API_URL is required.' }
$result = Invoke-RestMethod -Uri ([Uri]::new([Uri]$ApiUrl, '/health')) -TimeoutSec 10
if (-not $result.ok) { throw 'Backend health check failed.' }
$roblox = Get-Process -Name RobloxPlayerBeta -ErrorAction SilentlyContinue | Select-Object -First 1
[pscustomobject]@{ Backend = 'healthy'; Roblox = $(if ($roblox) { 'running' } else { 'stopped' }); RobloxPid = $roblox.Id } | Format-List
