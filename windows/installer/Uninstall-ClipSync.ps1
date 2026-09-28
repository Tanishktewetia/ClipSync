$ErrorActionPreference = 'SilentlyContinue'
$installDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$shell = New-Object -ComObject WScript.Shell
$answer = $shell.Popup('Remove ClipSync from this PC?', 0, 'Uninstall ClipSync', 4 + 32)
if ($answer -ne 6) { exit 0 }
Get-Process -Name ClipSync -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
Remove-ItemProperty -Path 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Run' -Name 'ClipSync' -ErrorAction SilentlyContinue
Remove-Item -LiteralPath 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Uninstall\ClipSync' -Recurse -Force -ErrorAction SilentlyContinue
Remove-Item -LiteralPath (Join-Path $env:APPDATA 'Microsoft\Windows\Start Menu\Programs\ClipSync.lnk') -Force -ErrorAction SilentlyContinue
$cleanup = Join-Path $env:TEMP ('clipsync-uninstall-' + [Guid]::NewGuid().ToString('N') + '.cmd')
$escaped = $installDir.Replace('%','%%')
Set-Content -LiteralPath $cleanup -Encoding ascii -Value ("@echo off`r`nping 127.0.0.1 -n 2 >nul`r`nrmdir /s /q `"" + $escaped + "`"`r`ndel /q `"%~f0`"`r`n")
Start-Process -FilePath $env:ComSpec -ArgumentList '/d','/c',('"' + $cleanup + '"') -WindowStyle Hidden
[void]$shell.Popup('ClipSync was removed.', 0, 'Uninstall ClipSync', 64)
