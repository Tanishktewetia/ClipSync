# build-windows.ps1 — Build the Windows ClipSync app
$ErrorActionPreference = "Stop"

$solutionDir = Join-Path $PSScriptRoot "..\windows"
$solution = Join-Path $solutionDir "ClipSync.sln"

if (-not (Test-Path $solution)) {
    Write-Error "Solution not found at $solution"
    exit 1
}

Write-Host "Building ClipSync Windows app..." -ForegroundColor Cyan
dotnet build $solution --configuration Debug

if ($LASTEXITCODE -ne 0) {
    Write-Error "Build failed with exit code $LASTEXITCODE"
    exit $LASTEXITCODE
}

Write-Host "Build succeeded." -ForegroundColor Green
