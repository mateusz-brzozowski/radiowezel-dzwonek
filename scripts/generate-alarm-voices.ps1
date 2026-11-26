# Generates alarm tones + Edge TTS voices, then writes SHA-256 into updates/assets.json
$ErrorActionPreference = "Stop"
$root = Resolve-Path (Join-Path $PSScriptRoot "..")
Set-Location $root

$env:Path = [System.Environment]::GetEnvironmentVariable("Path","Machine") + ";" + [System.Environment]::GetEnvironmentVariable("Path","User")

Write-Host "Generating tones..."
python scripts\generate_alarm_tones.py
Write-Host "Generating voices (Edge TTS)..."
python scripts\generate_alarm_voices.py
Write-Host "Building assets.json..."
python scripts\build_assets_manifest.py
Write-Host "Done."
