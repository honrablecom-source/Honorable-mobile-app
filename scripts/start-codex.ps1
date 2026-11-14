$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)
$codexCommand = Get-Command codex -ErrorAction SilentlyContinue
if ($codexCommand) {
    & $codexCommand.Source
} else {
    $installation = Get-AppxPackage OpenAI.Codex | Select-Object -First 1
    if (-not $installation) { throw 'Install Codex desktop or the Codex CLI, then reopen VS Code.' }
    $codexPath = Join-Path $installation.InstallLocation 'app/resources/codex.exe'
    if (-not (Test-Path -LiteralPath $codexPath)) { throw 'Codex CLI was not found in the desktop installation.' }
    & $codexPath
}
