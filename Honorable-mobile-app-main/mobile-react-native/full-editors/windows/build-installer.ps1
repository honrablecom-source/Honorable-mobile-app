param(
    [Parameter(Mandatory)][string]$Stage,
    [Parameter(Mandatory)][string]$Output
)

$ErrorActionPreference = 'Stop'
$stagePath = (Resolve-Path -LiteralPath $Stage).Path
$outputPath = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($Output)
if (Test-Path -LiteralPath $outputPath) { throw 'Choose a new installer output path.' }
foreach ($file in 'NobleStudio.exe','WebView2Loader.dll','resources/runtime/node.exe','resources/app/studio-service.mjs') {
    if (!(Test-Path -LiteralPath (Join-Path $stagePath $file) -PathType Leaf)) { throw "The native Noble package is missing $file." }
}

$compiler = Join-Path $env:WINDIR 'Microsoft.NET/Framework64/v4.0.30319/csc.exe'
$scratch = Join-Path ([IO.Path]::GetTempPath()) ('noble-installer-' + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $scratch | Out-Null
try {
    $payload = Join-Path $scratch 'NoblePayload.zip'
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    [IO.Compression.ZipFile]::CreateFromDirectory($stagePath, $payload)
    $identity = Join-Path $scratch 'PackageIdentity.cs'
    $hash = (Get-FileHash -LiteralPath $payload).Hash.Substring(0, 16).ToLowerInvariant()
    Set-Content -LiteralPath $identity -Value ('static class PackageIdentity {{ public const string Folder = "app-{0}"; }}' -f $hash)
    & $compiler /nologo /target:winexe /platform:x64 "/out:$outputPath" "/win32icon:$(Join-Path $PSScriptRoot 'noble.ico')" "/resource:$payload,NoblePayload" /reference:System.Windows.Forms.dll /reference:System.IO.Compression.dll /reference:System.IO.Compression.FileSystem.dll /reference:Microsoft.CSharp.dll (Join-Path $PSScriptRoot 'Installer.cs') $identity
    if ($LASTEXITCODE -ne 0) { throw 'Could not compile the installer.' }
}
finally {
    $verifiedScratch = (Resolve-Path -LiteralPath $scratch).Path
    if ([IO.Path]::GetFileName($verifiedScratch) -match '^noble-installer-[0-9a-f]{32}$' -and
        [IO.Path]::GetDirectoryName($verifiedScratch) -eq [IO.Path]::GetTempPath().TrimEnd('\')) {
        Remove-Item -LiteralPath $verifiedScratch -Recurse -Force
    }
}
