# Radiowęzeł — bezstresowy dzwonek

Aplikacja Android (min. Android 8.0) na tablet w radiowęźle szkolnym. O zaplanowanych godzinach odtwarza lokalny utwór przez ustalony czas (start/koniec przerwy). Harmonogram: jeden szablon dnia, aktywny pon–pt; soboty, niedziele i święta są pomijane.

## Wymagania

- Android Studio Ladybug+ / JDK 17
- Tablet docelowy: Huawei MediaPad M5 Lite 10 (Android 8.0) lub nowszy

## Budowanie

```bat
gradlew.bat assembleRelease
```

APK: `app\build\outputs\apk\release\app-release.apk`

Repo OTA: https://github.com/mateusz-brzozowski/radiowezel-dzwonek  
Domyślny `version.json`: https://raw.githubusercontent.com/mateusz-brzozowski/radiowezel-dzwonek/master/updates/version.json

### Publikacja nowej wersji (OTA)

```powershell
.\scripts\publish-release.ps1 -VersionName 1.1.0 -VersionCode 2 -Changelog "Opis" -Force
```

### Device Owner (ADB)

1. Factory reset tabletu, **bez** logowania na konto Google.
2. Włącz debugowanie USB, podłącz do PC.
3. Uruchom:

```powershell
.\scripts\setup-device-owner.ps1
```

## Pierwszy setup na tablecie (ważne dla niezawodności)

1. Zainstaluj APK i otwórz aplikację.
2. **Wyłącz optymalizację baterii** (przycisk na Statusie / w Ustawieniach).
3. Na Huawei / EMUI:
   - Ustawienia → Bateria → uruchamianie aplikacji → **Radiowęzeł** → ręczne zarządzanie → włącz autostart, uruchamianie w tle, drugorzędne uruchamianie.
   - Dodaj aplikację do **chronionych aplikacji** (Protected Apps), jeśli dostępne.
4. Upewnij się, że **automatyczna data i czas** są włączone.
5. Podłącz tablet do stałego zasilania.
6. Zaimportuj utwory MP3 (zakładka Utwory) i dodaj godziny w Planie.
7. Użyj **Dzwoń teraz**, żeby sprawdzić wyjście audio radiowęzła.

### Opcjonalnie: Device Owner + kiosk (najwyższa odporność)

Po **factory reset** (bez konta Google na urządzeniu):

```bat
adb install app-debug.apk
adb shell dpm set-device-owner pl.szkola.radiowezel/.util.AdminReceiver
```

Potem w Ustawieniach aplikacji włącz **tryb kiosku**. Device Owner pozwala też na cichą instalację OTA.

## Aktualizacje OTA (GitHub Releases)

1. Utwórz publiczne repozytorium.
2. Wrzuć release z plikiem `app-release.apk`.
3. Opublikuj `version.json` (np. w `updates/` na branchu `main` albo jako asset releasu), np.:

```json
{
  "versionCode": 2,
  "versionName": "1.1.0",
  "apkUrl": "https://github.com/ORG/REPO/releases/download/v1.1.0/app-release.apk",
  "force": true,
  "changelog": "Opis zmian"
}
```

4. W aplikacji (Ustawienia) wklej URL do `version.json`, np.  
   `https://raw.githubusercontent.com/ORG/REPO/main/updates/version.json`
5. Tablet sprawdza aktualizacje przy starcie i raz dziennie; przycisk **Sprawdź aktualizację** wymusza od razu. Przy `"force": true` blokuje UI do instalacji.

## Architektura niezawodności

- `AlarmManager.setAlarmClock` — alarm o najwyższym priorytecie systemowym
- Watchdog co ~20 min — ponownie ustawia następny dzwonek
- `BootReceiver` — po restarcie tabletu odtwarza harmonogram
- Foreground Service + `USAGE_ALARM` podczas odtwarzania
- Pliki audio lokalne (dzwonek nie zależy od Wi‑Fi w momencie grania)

## Ekrany

| Zakładka | Funkcja |
|----------|---------|
| Status | Następny dzwonek, stan baterii/OTA, test |
| Plan | CRUD godzin dzwonków (szablon dnia) |
| Utwory | Import / usuwanie MP3 |
| Święta | Dni wolne (wbudowane PL + własne) |
| Ustawienia | OTA, głośność, Huawei, kiosk |
