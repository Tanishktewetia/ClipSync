# serve-apk.ps1 — Serve the latest APK over HTTP for phone sideloading
$ErrorActionPreference = "Stop"

$distDir = Join-Path $PSScriptRoot "..\dist"
$apks = Get-ChildItem $distDir -Filter "ClipSync-debug-*.apk" -ErrorAction SilentlyContinue | Sort-Object LastWriteTime -Descending

if (-not $apks -or $apks.Count -eq 0) {
    Write-Error "No APK found in $distDir. Run build-android.ps1 first."
    exit 1
}

$apk = $apks[0]
$port = 8080

# Get local IP addresses
$ips = Get-NetIPAddress -AddressFamily IPv4 | Where-Object {
    $_.InterfaceAlias -notmatch "Loopback" -and $_.IPAddress -ne "127.0.0.1"
} | Select-Object -ExpandProperty IPAddress

Write-Host ""
Write-Host "Serving: $($apk.Name)" -ForegroundColor Cyan
Write-Host "File size: $([math]::Round($apk.Length / 1MB, 2)) MB" -ForegroundColor Gray
Write-Host ""
Write-Host "Open one of these URLs on your phone:" -ForegroundColor Yellow
foreach ($ip in $ips) {
    Write-Host "  http://${ip}:${port}/$($apk.Name)" -ForegroundColor Green
}
Write-Host ""
Write-Host "Press Ctrl+C to stop." -ForegroundColor Gray
Write-Host ""

# Simple HTTP server
$listener = New-Object System.Net.HttpListener
$listener.Prefixes.Add("http://+:${port}/")

try {
    $listener.Start()
    Write-Host "Listening on port $port..." -ForegroundColor Gray

    while ($true) {
        $context = $listener.GetContext()
        $request = $context.Request
        $response = $context.Response

        Write-Host "$(Get-Date -Format 'HH:mm:ss') Request from $($request.RemoteEndPoint): $($request.Url.LocalPath)" -ForegroundColor Gray

        if ($request.Url.LocalPath -eq "/$($apk.Name)" -or $request.Url.LocalPath -eq "/") {
            $bytes = [System.IO.File]::ReadAllBytes($apk.FullName)
            $response.ContentType = "application/vnd.android.package-archive"
            $response.ContentLength64 = $bytes.Length
            $response.Headers.Add("Content-Disposition", "attachment; filename=$($apk.Name)")
            $response.OutputStream.Write($bytes, 0, $bytes.Length)
            $response.StatusCode = 200
            Write-Host "  → Sent $($apk.Name) ($([math]::Round($bytes.Length / 1MB, 2)) MB)" -ForegroundColor Green
        } else {
            $msg = [System.Text.Encoding]::UTF8.GetBytes("ClipSync APK Server. Download: /$($apk.Name)")
            $response.ContentType = "text/plain"
            $response.ContentLength64 = $msg.Length
            $response.OutputStream.Write($msg, 0, $msg.Length)
            $response.StatusCode = 200
        }

        $response.OutputStream.Close()
    }
} finally {
    $listener.Stop()
    $listener.Close()
    Write-Host "Server stopped." -ForegroundColor Yellow
}
