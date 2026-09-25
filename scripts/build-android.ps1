# build-android.ps1 — Build Android debug APK and copy to dist/
$ErrorActionPreference = "Stop"

$androidDir = Join-Path $PSScriptRoot "..\android"
$gradlew = Join-Path $androidDir "gradlew.bat"

if (-not (Test-Path $gradlew)) {
    Write-Error "gradlew.bat not found at $gradlew. Run Android Studio setup first."
    exit 1
}

Write-Host "Building ClipSync Android debug APK..." -ForegroundColor Cyan
Push-Location $androidDir
try {
    & .\gradlew.bat assembleDebug
    if ($LASTEXITCODE -ne 0) {
        Write-Error "Android build failed with exit code $LASTEXITCODE"
        exit $LASTEXITCODE
    }
} finally {
    Pop-Location
}

# Copy APK to dist/
$distDir = Join-Path $PSScriptRoot "..\dist"
if (-not (Test-Path $distDir)) {
    New-Item -ItemType Directory -Path $distDir | Out-Null
}

$apkSource = Join-Path $androidDir "app\build\outputs\apk\debug\app-debug.apk"
if (Test-Path $apkSource) {
    # Read version from build.gradle.kts
    $version = "0.1.0"
    $buildGradle = Join-Path $androidDir "app\build.gradle.kts"
    if (Test-Path $buildGradle) {
        $content = Get-Content $buildGradle -Raw
        if ($content -match 'versionName\s*=\s*"([^"]+)"') {
            $version = $Matches[1]
        }
    }

    $destName = "ClipSync-debug-$version.apk"
    $dest = Join-Path $distDir $destName
    Copy-Item $apkSource $dest -Force
    Write-Host "APK copied to: $dest" -ForegroundColor Green
    Write-Host "File size: $([math]::Round((Get-Item $dest).Length / 1MB, 2)) MB" -ForegroundColor Gray
} else {
    Write-Warning "APK not found at expected path: $apkSource"
    Write-Host "Check android/app/build/outputs/apk/debug/ manually." -ForegroundColor Yellow
}
