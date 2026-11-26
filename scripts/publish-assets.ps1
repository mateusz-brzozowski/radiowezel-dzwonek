# Uploads alarm MP3 pack as GitHub Release assets-v1 (or -Tag override).
param(
    [string]$Tag = "assets-v1",
    [string]$Repo = "mateusz-brzozowski/radiowezel-dzwonek"
)

$ErrorActionPreference = "Stop"
$root = Resolve-Path (Join-Path $PSScriptRoot "..")
Set-Location $root

$env:Path = [System.Environment]::GetEnvironmentVariable("Path","Machine") + ";" + [System.Environment]::GetEnvironmentVariable("Path","User")

$files = @()
$files += Get-ChildItem "alarms\tones\*.mp3"
$files += Get-ChildItem "alarms\voices\*.mp3"
$files += Get-Item "updates\assets.json"

if ($files.Count -lt 14) { throw "Expected alarm MP3 files under alarms/" }

$ErrorActionPreference = "Continue"
gh release view $Tag --repo $Repo 2>$null | Out-Null
$releaseExists = ($LASTEXITCODE -eq 0)
$ErrorActionPreference = "Stop"

if ($releaseExists) {
    Write-Host "Release $Tag already exists — uploading/clobbering assets..."
    foreach ($f in $files) {
        gh release upload $Tag $f.FullName --repo $Repo --clobber
        if ($LASTEXITCODE -ne 0) { throw "upload failed: $($f.Name)" }
    }
} else {
    Write-Host "Creating release $Tag..."
    $paths = @($files | ForEach-Object { $_.FullName })
    gh release create $Tag @paths --repo $Repo --title $Tag --notes "Paczka dźwięków alarmowych (tony + komunikaty PL Edge TTS). bundleVersion=1" --latest=false
    if ($LASTEXITCODE -ne 0) { throw "gh release create failed" }
}

Write-Host "Gotowe. Manifest: https://raw.githubusercontent.com/$Repo/master/updates/assets.json" -ForegroundColor Green
