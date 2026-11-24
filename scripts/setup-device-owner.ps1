# Requires: USB debugging ON, tablet after factory reset (no Google account / no other device admin).
# Usage: .\scripts\setup-device-owner.ps1 [-ApkPath path\to\app.apk]

param(
    [string]$ApkPath = "$PSScriptRoot\..\app\build\outputs\apk\debug\app-debug.apk",
    [string]$Package = "pl.szkola.radiowezel",
    [string]$AdminReceiver = "pl.szkola.radiowezel/.util.AdminReceiver"
)

$ErrorActionPreference = "Stop"
$adb = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"
if (-not (Test-Path $adb)) {
    throw "Nie znaleziono adb: $adb"
}

Write-Host "Sprawdzam urządzenia..."
& $adb start-server | Out-Null
$devices = & $adb devices | Select-Object -Skip 1 | Where-Object { $_ -match "device$" }
if (-not $devices) {
    throw "Brak tabletu w ADB. Podłącz MediaPad, włącz debugowanie USB i potwierdź fingerprint."
}

Write-Host "Znaleziono:`n$devices"
if (-not (Test-Path $ApkPath)) {
    throw "Brak APK: $ApkPath — najpierw zbuduj: .\gradlew.bat assembleDebug"
}

Write-Host "Instaluję APK..."
& $adb install -r $ApkPath
if ($LASTEXITCODE -ne 0) { throw "Instalacja APK nie powiodła się" }

Write-Host "Ustawiam Device Owner..."
& $adb shell dpm set-device-owner $AdminReceiver
if ($LASTEXITCODE -ne 0) {
    Write-Host ""
    Write-Host "UWAGA: set-device-owner wymaga czystego tabletu (po factory reset, BEZ konta Google)." -ForegroundColor Yellow
    Write-Host "Jeśli urządzenie ma już konta użytkowników, zrób reset i powtórz ten skrypt." -ForegroundColor Yellow
    throw "Nie udało się ustawić Device Owner"
}

Write-Host "Device Owner ustawiony dla $Package" -ForegroundColor Green
& $adb shell dpm list-owners
Write-Host "Uruchamiam aplikację..."
& $adb shell am start -n "$Package/.MainActivity"
Write-Host "Gotowe. W Ustawieniach aplikacji włącz tryb kiosku (Lock Task)."
