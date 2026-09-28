$ErrorActionPreference = 'Stop'

function Show-Message([string]$text, [string]$title = 'ClipSync Setup', [int]$icon = 64) {
    if ($env:CLIPSYNC_INSTALL_TEST_ROOT) { Write-Host $text; return }
    $shell = New-Object -ComObject WScript.Shell
    [void]$shell.Popup($text, 0, $title, $icon)
}

try {
    if (-not [Environment]::Is64BitOperatingSystem) { throw 'ClipSync requires 64-bit Windows.' }
    $testMode = -not [string]::IsNullOrWhiteSpace($env:CLIPSYNC_INSTALL_TEST_ROOT)
    $installDir = if ($testMode) { [IO.Path]::GetFullPath($env:CLIPSYNC_INSTALL_TEST_ROOT) } else { Join-Path $env:LOCALAPPDATA 'Programs\ClipSync' }
    $payload = Join-Path $PSScriptRoot 'payload.zip'
    if (-not (Test-Path -LiteralPath $payload)) { throw 'The installer payload is missing.' }

    if (-not $testMode) {
        $dotnet = Get-Command dotnet.exe -ErrorAction SilentlyContinue
        if (-not $dotnet) {
            $candidate = Join-Path $env:ProgramFiles 'dotnet\dotnet.exe'
            if (Test-Path -LiteralPath $candidate) { $dotnet = Get-Item -LiteralPath $candidate }
        }
        $hasDesktopRuntime = $false
        if ($dotnet) {
            $runtimeList = & $dotnet.Source --list-runtimes 2>$null
            $hasDesktopRuntime = [bool]($runtimeList | Select-String '^Microsoft\.WindowsDesktop\.App 10\.')
        }
        if (-not $hasDesktopRuntime) {
            Show-Message 'ClipSync Setup will install the official Microsoft .NET 10 Desktop Runtime, then continue.'
            $runtimeInstaller = Join-Path $env:TEMP 'windowsdesktop-runtime-10-win-x64.exe'
            Invoke-WebRequest -UseBasicParsing -Uri 'https://aka.ms/dotnet/10.0/windowsdesktop-runtime-win-x64.exe' -OutFile $runtimeInstaller
            $signature = Get-AuthenticodeSignature -LiteralPath $runtimeInstaller
            if ($signature.Status -ne 'Valid' -or $signature.SignerCertificate.Subject -notmatch 'Microsoft') { throw 'The downloaded Microsoft .NET runtime did not pass signature verification.' }
            $runtimeProcess = Start-Process -FilePath $runtimeInstaller -ArgumentList '/install','/quiet','/norestart' -Wait -PassThru
            Remove-Item -LiteralPath $runtimeInstaller -Force -ErrorAction SilentlyContinue
            if ($runtimeProcess.ExitCode -notin 0, 3010) { throw ".NET Desktop Runtime setup failed with exit code $($runtimeProcess.ExitCode)." }
        }
    }

    if (-not $testMode) { Get-Process -Name ClipSync -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue }
    $parent = Split-Path -Parent $installDir
    New-Item -ItemType Directory -Force -Path $parent | Out-Null
    $incoming = Join-Path $parent ('ClipSync.installing.' + [Guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory -Force -Path $incoming | Out-Null
    Expand-Archive -LiteralPath $payload -DestinationPath $incoming -Force
    if (-not (Test-Path -LiteralPath (Join-Path $incoming 'ClipSync.exe'))) { throw 'The ClipSync application payload is invalid.' }
    if (Test-Path -LiteralPath $installDir) { Remove-Item -LiteralPath $installDir -Recurse -Force }
    Move-Item -LiteralPath $incoming -Destination $installDir
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'Uninstall-ClipSync.ps1') -Destination (Join-Path $installDir 'Uninstall-ClipSync.ps1') -Force
    $exe = Join-Path $installDir 'ClipSync.exe'

    if (-not $testMode) {
        $shell = New-Object -ComObject WScript.Shell
        $programs = Join-Path $env:APPDATA 'Microsoft\Windows\Start Menu\Programs'
        $shortcut = $shell.CreateShortcut((Join-Path $programs 'ClipSync.lnk'))
        $shortcut.TargetPath = $exe
        $shortcut.WorkingDirectory = $installDir
        $shortcut.Description = 'Open ClipSync'
        $shortcut.Save()

        $runKey = 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Run'
        New-Item -Path $runKey -Force | Out-Null
        Set-ItemProperty -Path $runKey -Name 'ClipSync' -Value ('"' + $exe + '" --startup')

        $uninstallKey = 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Uninstall\ClipSync'
        New-Item -Path $uninstallKey -Force | Out-Null
        $uninstallCommand = 'powershell.exe -NoProfile -ExecutionPolicy Bypass -File "' + (Join-Path $installDir 'Uninstall-ClipSync.ps1') + '"'
        New-ItemProperty -Path $uninstallKey -Name DisplayName -Value 'ClipSync' -PropertyType String -Force | Out-Null
        New-ItemProperty -Path $uninstallKey -Name DisplayVersion -Value '__VERSION__' -PropertyType String -Force | Out-Null
        New-ItemProperty -Path $uninstallKey -Name Publisher -Value 'ClipSync' -PropertyType String -Force | Out-Null
        New-ItemProperty -Path $uninstallKey -Name InstallLocation -Value $installDir -PropertyType String -Force | Out-Null
        New-ItemProperty -Path $uninstallKey -Name DisplayIcon -Value $exe -PropertyType String -Force | Out-Null
        New-ItemProperty -Path $uninstallKey -Name UninstallString -Value $uninstallCommand -PropertyType String -Force | Out-Null
        New-ItemProperty -Path $uninstallKey -Name NoModify -Value 1 -PropertyType DWord -Force | Out-Null
        New-ItemProperty -Path $uninstallKey -Name NoRepair -Value 1 -PropertyType DWord -Force | Out-Null
        Start-Process -FilePath $exe -ArgumentList '--show'
        Show-Message 'ClipSync is installed and running in the system tray. It will start automatically when you sign in.'
    } else {
        Set-Content -LiteralPath (Join-Path $installDir '.installer-smoke-test') -Value 'ok' -Encoding ascii
    }
    exit 0
}
catch {
    Show-Message ("Installation failed: " + $_.Exception.Message) 'ClipSync Setup' 16
    exit 1
}
