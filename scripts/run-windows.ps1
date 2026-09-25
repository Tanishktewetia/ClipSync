# run-windows.ps1 — Build and run the current Windows app, not an old Debug binary.
param([ValidateSet("Debug", "Release")][string]$Configuration = "Release")
$ErrorActionPreference = "Stop"

$projectDir = Join-Path $PSScriptRoot "..\windows\ClipSync.Windows"
$project = Join-Path $projectDir "ClipSync.Windows.csproj"
if (-not (Test-Path -LiteralPath $project)) { throw "Project not found at $project" }

# Never kill a user's running app or overwrite a locked executable.
if (Get-Process -Name "ClipSync" -ErrorAction SilentlyContinue) {
    throw "ClipSync is already running. Choose Exit ClipSync in its tray menu, then run this script again to launch the current $Configuration build."
}
Write-Host "Building and running ClipSync Windows ($Configuration)..." -ForegroundColor Cyan
dotnet run --project $project --configuration $Configuration
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
