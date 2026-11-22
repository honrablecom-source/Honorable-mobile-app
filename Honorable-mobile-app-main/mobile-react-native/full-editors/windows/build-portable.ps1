param(
    [Parameter(Mandatory)][string]$RuntimeDirectory,
    [Parameter(Mandatory)][string]$Stage,
    [Parameter(Mandatory)][string]$SearchModules,
    [string]$DesktopVideoBundle,
    [string]$DesktopImageBundle,
    [string]$NativeArchiveDirectory
)

$ErrorActionPreference = 'Stop'
if (Test-Path -LiteralPath $Stage) { throw 'Choose a new staging directory.' }

$nodeName = 'node-v24.22.3-win-x64'
$nodeArchive = Join-Path $RuntimeDirectory "$nodeName.zip"
$webViewPackage = Join-Path $RuntimeDirectory 'microsoft.web.webview2.1.0.3595.46.nupkg'
$checks = @{
    $nodeArchive = 'e612af99f626ab415ea225af6bce5c971feb448cb5cae250bb1c3fa50c5f7d9f'
    $webViewPackage = 'f448c20859199ecd846a7d693710d143a8cd019ad1dcdc4cd6332d842d871054'
}
foreach ($file in $checks.Keys) {
    if ((Get-FileHash -LiteralPath $file).Hash -ine $checks[$file]) { throw "Runtime checksum mismatch for $file." }
}
Add-Type -AssemblyName System.IO.Compression.FileSystem
$null = New-Item -ItemType Directory -Path $Stage
$Stage = (Resolve-Path -LiteralPath $Stage).Path
$runtime = Join-Path $Stage 'resources/runtime'
$null = New-Item -ItemType Directory -Path $runtime
function Copy-RuntimeEntry($archive, $entry, $destination) {
    $item = $archive.GetEntry($entry)
    if (!$item) { throw "The runtime archive is missing $entry." }
    [IO.Compression.ZipFileExtensions]::ExtractToFile($item, $destination)
}
$node = [IO.Compression.ZipFile]::OpenRead($nodeArchive)
try {
    foreach ($name in 'node.exe','LICENSE') {
        Copy-RuntimeEntry $node "$nodeName/$name" (Join-Path $runtime $name)
    }
}
finally { $node.Dispose() }
$webView = [IO.Compression.ZipFile]::OpenRead($webViewPackage)
try {
    foreach ($name in 'Microsoft.Web.WebView2.Core.dll','Microsoft.Web.WebView2.WinForms.dll') {
        Copy-RuntimeEntry $webView "lib/net462/$name" (Join-Path $Stage $name)
    }
    Copy-RuntimeEntry $webView 'runtimes/win-x64/native/WebView2Loader.dll' (Join-Path $Stage 'WebView2Loader.dll')
    Copy-RuntimeEntry $webView 'LICENSE.txt' (Join-Path $Stage 'WebView2-LICENSE.txt')
}
finally { $webView.Dispose() }

$compiler = Join-Path $env:WINDIR 'Microsoft.NET/Framework64/v4.0.30319/csc.exe'
$references = @('System.Windows.Forms.dll','System.Drawing.dll','System.Web.dll','System.Web.Extensions.dll',
    (Join-Path $Stage 'Microsoft.Web.WebView2.Core.dll'),(Join-Path $Stage 'Microsoft.Web.WebView2.WinForms.dll'))
$referenceOptions = $references | ForEach-Object { "/reference:$_" }
& $compiler /nologo /target:winexe /platform:x64 "/out:$(Join-Path $Stage 'NobleStudio.exe')" @referenceOptions `
    "/win32icon:$(Join-Path $PSScriptRoot 'noble.ico')" "/win32manifest:$(Join-Path $PSScriptRoot 'NobleStudio.manifest')" `
    (Join-Path $PSScriptRoot 'NobleStudio.cs') (Join-Path $PSScriptRoot 'NativeTransfers.cs')
if ($LASTEXITCODE) { throw 'Could not compile Noble Studio.' }

$appDirectory = Join-Path $Stage 'resources/app'
$studio = Join-Path $appDirectory 'studio'
$appAssets = Join-Path $studio 'android/app/src/main/assets'
New-Item -ItemType Directory -Path (Join-Path $studio 'full-editors'), $appAssets | Out-Null
$editors = Split-Path $PSScriptRoot
$mobile = Split-Path $editors
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'studio-service.mjs'), (Join-Path $PSScriptRoot 'native-bridge.js'), (Join-Path $PSScriptRoot 'native-studios.json') -Destination $appDirectory
if ($NativeArchiveDirectory) {
    & $compiler /nologo /target:exe /platform:x64 "/out:$(Join-Path (Resolve-Path $appDirectory) 'NobleEditorHost.exe')" (Join-Path $PSScriptRoot 'EditorHost.cs')
    if ($LASTEXITCODE) { throw 'Could not build the Noble native editor host.' }
    $native = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'native-studios.json') -Raw | ConvertFrom-Json
    foreach ($kind in @('image', 'video')) {
        $release = $native.$kind
        $zip = Join-Path $NativeArchiveDirectory $release.archive
        if ((Get-FileHash -LiteralPath $zip).Hash -ine $release.sha256) {
            throw "$($release.name) does not match the pinned publisher archive."
        }
        $destination = Join-Path $Stage "resources/native/$kind"
        Expand-Archive -LiteralPath $zip -DestinationPath $destination
        $bin = if ($release.directory) { Join-Path $destination $release.directory } else { $destination }
        foreach ($name in @($release.executable, $release.cli, 'LICENSE-MIT', 'LICENSE-APACHE', 'NOTICE')) {
            if (!(Test-Path -LiteralPath (Join-Path $bin $name) -PathType Leaf)) {
                throw "The $($release.name) archive is missing $name."
            }
        }
    }
}
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
foreach ($name in @('home.html', 'home.css', 'library.js', 'video-samples.js', 'storyboard.js', 'cinematic-panel.js', 'cinematic-media.js', 'video-mcp.js', 'video-controls.js', 'video-changes.js', 'video-export.js', 'workspace.js', 'workspace.css', 'photo-workspace.js', 'photo-workspace.css', 'photo-agent.js', 'photo-settings.js', 'noble.png')) {
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot $name) -Destination $desktop
}
$modelAssets = Join-Path (Split-Path $mobile) 'android-app/app/src/main/assets/tinyclip'
Copy-Item -LiteralPath (Join-Path $modelAssets 'model_int8.onnx'), (Join-Path $modelAssets 'tokenizer.json') -Destination $desktop
node (Join-Path $PSScriptRoot 'build-search.mjs') $SearchModules (Join-Path $desktop 'semantic-bundle.js')
if ($LASTEXITCODE -ne 0) { throw 'Could not build the offline search worker.' }
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'README.md') -Destination $Stage
Write-Output (Join-Path $Stage 'NobleStudio.exe')
