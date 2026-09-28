# Build a compact Windows installer without deploying it.
$ErrorActionPreference = 'Stop'
$root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$project = Join-Path $root 'windows\ClipSync.Windows\ClipSync.Windows.csproj'
$publish = Join-Path $root 'dist\windows-download\publish'
$artifacts = Join-Path $root 'dist\windows-download\artifacts'
$staging = Join-Path $root 'dist\windows-download\installer-staging'
$installerSource = Join-Path $root 'windows\installer'
$version = ([xml](Get-Content -LiteralPath $project -Raw)).Project.PropertyGroup.Version | Select-Object -First 1
if ([string]::IsNullOrWhiteSpace($version)) { throw 'Windows project version is missing.' }

foreach ($targetDirectory in @($publish, $artifacts, $staging)) {
    $resolved = [IO.Path]::GetFullPath($targetDirectory)
    $distRoot = [IO.Path]::GetFullPath((Join-Path $root 'dist')) + [IO.Path]::DirectorySeparatorChar
    if (-not $resolved.StartsWith($distRoot, [StringComparison]::OrdinalIgnoreCase)) { throw "Refusing to clean path outside dist: $resolved" }
    if (Test-Path -LiteralPath $resolved) { Remove-Item -LiteralPath $resolved -Recurse -Force }
    New-Item -ItemType Directory -Path $resolved | Out-Null
}

$publishArgs = @('publish', $project, '-c', 'Release', '--self-contained', 'false', '--artifacts-path', $artifacts, '-o', $publish, '-p:PublishSingleFile=false', '-p:UseAppHost=true', '-p:DebugType=None', '-p:DebugSymbols=false')
& 'C:\Program Files\dotnet\dotnet.exe' @publishArgs
if ($LASTEXITCODE -ne 0) { throw "Windows publish failed: $LASTEXITCODE" }

$exe = Join-Path $publish 'ClipSync.exe'
$info = [Diagnostics.FileVersionInfo]::GetVersionInfo($exe)
if (-not $info.ProductVersion.StartsWith($version)) { throw 'Published executable version does not match the project.' }
$payloadBytes = (Get-ChildItem -LiteralPath $publish -File | Measure-Object Length -Sum).Sum
if ($payloadBytes -gt 2MB) { throw "Framework-dependent payload unexpectedly exceeds 2 MB ($payloadBytes bytes)." }

$payload = Join-Path $staging 'payload.zip'
Compress-Archive -Path (Join-Path $publish '*') -DestinationPath $payload -CompressionLevel Optimal
$installScript = (Get-Content -LiteralPath (Join-Path $installerSource 'Install-ClipSync.ps1') -Raw).Replace('__VERSION__', $version)
Set-Content -LiteralPath (Join-Path $staging 'Install-ClipSync.ps1') -Value $installScript -Encoding utf8
Copy-Item -LiteralPath (Join-Path $installerSource 'Uninstall-ClipSync.ps1') -Destination (Join-Path $staging 'Uninstall-ClipSync.ps1')
Set-Content -LiteralPath (Join-Path $staging 'install.cmd') -Encoding ascii -Value "@echo off`r`npowershell.exe -NoProfile -ExecutionPolicy Bypass -File `"%~dp0Install-ClipSync.ps1`"`r`nexit /b %ERRORLEVEL%`r`n"

$filename = "ClipSync-Setup-$version-win-x64.exe"
$target = Join-Path $root "dist\$filename"
$sed = Join-Path $staging 'ClipSync-Setup.sed'
$source = $staging.TrimEnd('\') + '\'
$sedText = @"
[Version]
Class=IEXPRESS
SEDVersion=3
[Options]
PackagePurpose=InstallApp
ShowInstallProgramWindow=1
HideExtractAnimation=0
UseLongFileName=1
InsideCompressed=0
CAB_FixedSize=0
CAB_ResvCodeSigning=0
RebootMode=N
InstallPrompt=%InstallPrompt%
DisplayLicense=%DisplayLicense%
FinishMessage=%FinishMessage%
TargetName=%TargetName%
FriendlyName=%FriendlyName%
AppLaunched=%AppLaunched%
PostInstallCmd=%PostInstallCmd%
AdminQuietInstCmd=%AdminQuietInstCmd%
UserQuietInstCmd=%UserQuietInstCmd%
SourceFiles=SourceFiles
[Strings]
InstallPrompt=
DisplayLicense=
FinishMessage=
TargetName=$target
FriendlyName=ClipSync Setup
AppLaunched=install.cmd
PostInstallCmd=<None>
AdminQuietInstCmd=install.cmd
UserQuietInstCmd=install.cmd
FILE0=payload.zip
FILE1=Install-ClipSync.ps1
FILE2=Uninstall-ClipSync.ps1
FILE3=install.cmd
[SourceFiles]
SourceFiles0=$source
[SourceFiles0]
%FILE0%=
%FILE1%=
%FILE2%=
%FILE3%=
"@
Set-Content -LiteralPath $sed -Value $sedText -Encoding ascii
if (Test-Path -LiteralPath $target) { Remove-Item -LiteralPath $target -Force }
& "$env:SystemRoot\System32\iexpress.exe" /N /Q $sed
$lastSize = -1; $stableReads = 0
for ($attempt = 0; $attempt -lt 150 -and $stableReads -lt 5; $attempt++) {
    Start-Sleep -Milliseconds 100
    if (Test-Path -LiteralPath $target) {
        $size = (Get-Item -LiteralPath $target).Length
        if ($size -gt 0 -and $size -eq $lastSize) { $stableReads++ } else { $stableReads = 0; $lastSize = $size }
    }
}
if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $target) -or $stableReads -lt 5) { throw "IExpress installer build failed: $LASTEXITCODE" }

$setup = [IO.File]::ReadAllBytes($target)
if ($setup.Length -lt 2 -or $setup[0] -ne 0x4d -or $setup[1] -ne 0x5a) { throw 'Generated setup is not a Windows executable.' }
$record = [ordered]@{
    version = $version; runtime = 'win-x64'; installer = $true; selfContained = $false; runtimeBootstrap = $true; signed = $false
    filename = $filename; bytes = (Get-Item -LiteralPath $target).Length; installedPayloadBytes = $payloadBytes
    sha256 = (Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash.ToLowerInvariant()
}
$record | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $root 'dist\windows-download\download.json') -Encoding utf8
Write-Host "Windows installer built: $target ($([math]::Round($record.bytes / 1MB, 2)) MB; installed app payload $([math]::Round($payloadBytes / 1MB, 2)) MB). Nothing deployed."
