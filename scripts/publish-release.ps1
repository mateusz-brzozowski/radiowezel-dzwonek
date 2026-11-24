# Builds release APK, updates updates/version.json, creates GitHub Release.
# Usage: .\scripts\publish-release.ps1 -VersionName 1.1.0 -VersionCode 2 [-Force] [-Changelog "..."]

param(
    [Parameter(Mandatory = $true)][string]$VersionName,
    [Parameter(Mandatory = $true)][int]$VersionCode,
    [string]$Changelog = "Aktualizacja radiowęzła",
    [switch]$Force,
    [string]$Repo = "mateusz-brzozowski/radiowezel-dzwonek"
)

$ErrorActionPreference = "Stop"
$root = Resolve-Path (Join-Path $PSScriptRoot "..")
Set-Location $root

$env:Path = [System.Environment]::GetEnvironmentVariable("Path","Machine") + ";" + [System.Environment]::GetEnvironmentVariable("Path","User")

Write-Host "Ustawiam versionCode=$VersionCode versionName=$VersionName w app/build.gradle.kts"
$gradle = Get-Content "app\build.gradle.kts" -Raw
$gradle = $gradle -replace 'versionCode = \d+', "versionCode = $VersionCode"
$gradle = $gradle -replace 'versionName = "[^"]+"', "versionName = `"$VersionName`""
Set-Content "app\build.gradle.kts" $gradle -NoNewline

Write-Host "Buduję release APK..."
.\gradlew.bat assembleRelease --no-daemon
if ($LASTEXITCODE -ne 0) { throw "Build failed" }

$apkSrc = "app\build\outputs\apk\release\app-release.apk"
if (-not (Test-Path $apkSrc)) { throw "Brak APK: $apkSrc" }

$tag = "v$VersionName"
$forceJson = if ($Force) { "true" } else { "false" }
$apkUrl = "https://github.com/$Repo/releases/download/$tag/app-release.apk"

$versionJson = @"
{
  "versionCode": $VersionCode,
  "versionName": "$VersionName",
  "apkUrl": "$apkUrl",
  "force": $forceJson,
  "changelog": "$Changelog"
}
"@
Set-Content "updates\version.json" $versionJson -Encoding utf8

Write-Host "Commit + push version.json i kodu..."
git add -A
git status
git commit -m "release: $tag ($VersionCode)"
git push origin HEAD

Write-Host "Tworzę GitHub Release $tag..."
gh release create $tag $apkSrc --repo $Repo --title $tag --notes $Changelog --latest
if ($LASTEXITCODE -ne 0) { throw "gh release create failed" }

Write-Host "Gotowe. OTA URL: https://raw.githubusercontent.com/$Repo/main/updates/version.json" -ForegroundColor Green
