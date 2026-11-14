param([switch]$CheckOnly)
$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)
function Invoke-Git {
    & git @args
    if ($LASTEXITCODE -ne 0) { throw 'Git command failed. Resolve the reported problem, then retry.' }
}
Invoke-Git status --short --branch
if ($CheckOnly) { exit 0 }
if (@(& git status --porcelain).Count -gt 0) {
    throw 'Commit your changes in VS Code Source Control first, then run Noble: sync GitHub again.'
}
Invoke-Git pull --ff-only
Invoke-Git push
Write-Host 'Noble is synced with GitHub.'
