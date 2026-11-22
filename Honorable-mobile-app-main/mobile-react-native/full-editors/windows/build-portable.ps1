param(
    [Parameter(Mandatory)][string]$ElectronDirectory,
    [Parameter(Mandatory)][string]$Stage,
    [Parameter(Mandatory)][string]$SearchModules,
    [string]$DesktopVideoBundle,
    [string]$DesktopImageBundle
)

$ErrorActionPreference = 'Stop'
$electronVersion = '45.4.1'
$zipName = "electron-v$electronVersion-win32-x64.zip"
$archive = Join-Path $ElectronDirectory $zipName
$manifest = Get-Content -LiteralPath (Join-Path $ElectronDirectory 'SHASUMS256.txt')
$pattern = '^([0-9a-f]{64})\s+\*?' + [regex]::Escape($zipName) + '$'
$entry = $manifest | Where-Object { $_ -match $pattern }
if (@($entry).Count -ne 1 -or $entry -notmatch $pattern -or (Get-FileHash -LiteralPath $archive).Hash -ine $Matches[1]) {
    throw 'The Electron archive does not match the upstream checksum.'
}
if (Test-Path -LiteralPath $Stage) { throw 'Choose a new staging directory.' }
Expand-Archive -LiteralPath $archive -DestinationPath $Stage
Rename-Item -LiteralPath (Join-Path $Stage 'electron.exe') -NewName 'NobleStudio.exe'

$appDirectory = Join-Path $Stage 'resources/app'
$studio = Join-Path $appDirectory 'studio'
$appAssets = Join-Path $studio 'android/app/src/main/assets'
New-Item -ItemType Directory -Path (Join-Path $studio 'full-editors'), $appAssets | Out-Null
$editors = Split-Path $PSScriptRoot
$mobile = Split-Path $editors
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'main.cjs'), (Join-Path $PSScriptRoot 'navigation.cjs'), (Join-Path $PSScriptRoot 'package.json') -Destination $appDirectory
Copy-Item -LiteralPath (Join-Path $editors 'preview.mjs'), (Join-Path $editors 'editing-agent') -Destination (Join-Path $studio 'full-editors') -Recurse
$bundledEditors = Join-Path $mobile 'android/app/src/main/assets/creative-editors'
$stagedEditors = Join-Path $appAssets 'creative-editors'
New-Item -ItemType Directory -Path $stagedEditors | Out-Null
$overrides = @{ image = $DesktopImageBundle; video = $DesktopVideoBundle }
foreach ($kind in @('image', 'video')) {
    $candidate = $overrides[$kind]
    if ($candidate) {
        $provenance = Get-Content -LiteralPath (Join-Path $candidate 'provenance.json') -Raw | ConvertFrom-Json
        if ($provenance.kind -ne $kind -or $provenance.variant -ne 'windows-theme') {
            throw "Expected a Windows-themed $kind bundle."
        }
    } else {
        $candidate = Join-Path $bundledEditors $kind
    }
    Copy-Item -LiteralPath $candidate -Destination (Join-Path $stagedEditors $kind) -Recurse
}
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'noble.ico') -Destination (Join-Path $Stage 'Noble.ico')
$desktop = Join-Path $studio 'full-editors/windows'
New-Item -ItemType Directory -Path $desktop | Out-Null
foreach ($name in @('home.html', 'home.css', 'library.js', 'video-samples.js', 'storyboard.js', 'cinematic-panel.js', 'cinematic-media.js', 'video-mcp.js', 'video-controls.js', 'workspace.js', 'workspace.css', 'photo-workspace.js', 'photo-workspace.css', 'photo-agent.js', 'photo-settings.js', 'noble.png')) {
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot $name) -Destination $desktop
}
$modelAssets = Join-Path (Split-Path $mobile) 'android-app/app/src/main/assets/tinyclip'
Copy-Item -LiteralPath (Join-Path $modelAssets 'model_int8.onnx'), (Join-Path $modelAssets 'tokenizer.json') -Destination $desktop
node (Join-Path $PSScriptRoot 'build-search.mjs') $SearchModules (Join-Path $desktop 'semantic-bundle.js')
if ($LASTEXITCODE -ne 0) { throw 'Could not build the offline search worker.' }
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'README.md') -Destination $Stage
Write-Output (Join-Path $Stage 'NobleStudio.exe')
