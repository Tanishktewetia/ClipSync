# Build the Windows website download without changing the normal development build.
# This creates an unsigned, self-contained win-x64 beta EXE; it does not sign, run or publish it online.
$ErrorActionPreference = 'Stop'
$root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$project = Join-Path $root 'windows\ClipSync.Windows\ClipSync.Windows.csproj'
$output = Join-Path $root 'dist\windows-download\publish'
$artifacts = Join-Path $root 'dist\windows-download\artifacts'
$version = ([xml](Get-Content -LiteralPath $project -Raw)).Project.PropertyGroup.Version | Select-Object -First 1
if ($version -ne '0.7.0') { throw "Unexpected Windows version $version; update the site version deliberately." }

dotnet publish $project -c Release -r win-x64 --self-contained true --artifacts-path $artifacts -o $output `
    -p:PublishSingleFile=true -p:IncludeNativeLibrariesForSelfExtract=true -p:EnableCompressionInSingleFile=true `
    -p:PublishTrimmed=false -p:DebugType=embedded -p:DebugSymbols=false
if ($LASTEXITCODE -ne 0) { throw "Windows publish failed: $LASTEXITCODE" }

$exe = Join-Path $output 'ClipSync.exe'
$info = [Diagnostics.FileVersionInfo]::GetVersionInfo($exe)
if (-not $info.ProductVersion.StartsWith($version)) { throw 'Published executable version does not match the project.' }
$unexpected = @(Get-ChildItem -LiteralPath $output -File | Where-Object { $_.Extension -in '.dll', '.json' })
if ($unexpected.Count -ne 0) { throw 'Publishing left loose runtime files. Do not distribute the EXE alone.' }
$filename = "ClipSync-$version-win-x64.exe"
$copy = Join-Path $root "dist\$filename"
Copy-Item -LiteralPath $exe -Destination $copy -Force
$record = [ordered]@{
    version = $version; runtime = 'win-x64'; selfContained = $true; singleFile = $true; signed = $false
    filename = $filename; bytes = (Get-Item -LiteralPath $copy).Length
    sha256 = (Get-FileHash -LiteralPath $copy -Algorithm SHA256).Hash.ToLowerInvariant()
}
$record | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $root 'dist\windows-download\download.json') -Encoding utf8
Write-Host "Website download built: $copy ($($record.bytes) bytes). Unsigned beta; nothing deployed."
