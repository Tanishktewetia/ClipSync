# adb-wireless.ps1 — Connect to phone via wireless ADB
$ErrorActionPreference = "Stop"

# Try to find adb
$adbPaths = @(
    "$env:ANDROID_HOME\platform-tools\adb.exe",
    "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe",
    "adb.exe"
)

$adb = $null
foreach ($p in $adbPaths) {
    if (Test-Path $p -ErrorAction SilentlyContinue) {
        $adb = $p
        break
    }
    # Check if it's on PATH
    $found = Get-Command $p -ErrorAction SilentlyContinue
    if ($found) {
        $adb = $found.Source
        break
    }
}

if (-not $adb) {
    Write-Error "adb not found. Set ANDROID_HOME or add platform-tools to PATH."
    exit 1
}

Write-Host "Using adb: $adb" -ForegroundColor Gray
Write-Host ""
Write-Host "=== Wireless ADB Setup ===" -ForegroundColor Cyan
Write-Host ""
Write-Host "On your phone:" -ForegroundColor Yellow
Write-Host "  1. Settings → Developer options → Wireless debugging → ON"
Write-Host "  2. Tap 'Pair device with pairing code'"
Write-Host "  3. Note the IP:port and pairing code shown"
Write-Host ""

$pairAddr = Read-Host "Enter pairing address (IP:port)"
$pairCode = Read-Host "Enter pairing code"

Write-Host ""
Write-Host "Pairing..." -ForegroundColor Cyan
& $adb pair $pairAddr $pairCode

if ($LASTEXITCODE -ne 0) {
    Write-Error "Pairing failed."
    exit 1
}

Write-Host ""
Write-Host "Pairing succeeded!" -ForegroundColor Green
Write-Host ""
Write-Host "Now enter the connection address shown under 'Wireless debugging'" -ForegroundColor Yellow
Write-Host "(This is different from the pairing address!)" -ForegroundColor Yellow
$connectAddr = Read-Host "Enter connection address (IP:port)"

Write-Host "Connecting..." -ForegroundColor Cyan
& $adb connect $connectAddr

if ($LASTEXITCODE -ne 0) {
    Write-Error "Connection failed."
    exit 1
}

Write-Host ""
Write-Host "Connected! You can now use:" -ForegroundColor Green
Write-Host "  adb logcat -s ClipSync:*" -ForegroundColor Gray
