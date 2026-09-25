# run-windows.ps1 — Build and run the Windows ClipSync app
$ErrorActionPreference = "Stop"

$projectDir = Join-Path $PSScriptRoot "..\windows\ClipSync.Windows"
$project = Join-Path $projectDir "ClipSync.Windows.csproj"

if (-not (Test-Path $project)) {
    Write-Error "Project not found at $project"
    exit 1
}

Write-Host "Building and running ClipSync Windows app..." -ForegroundColor Cyan
dotnet run --project $project --configuration Debug
