# Radiowęzeł — bezstresowy dzwonek

Aplikacja Android (min. Android 8.0) na tablet w radiowęźle szkolnym. O zaplanowanych godzinach odtwarza lokalny utwór przez ustalony czas (start/koniec przerwy). Harmonogram: jeden szablon dnia, aktywny pon–pt; soboty, niedziele i święta są pomijane.

Od **1.2.0**: lokalne alarmy szkolne (tony + komunikaty PL), paczka dźwięków OTA (`assets-v1`) oraz opcjonalne zdalne sterowanie przez Firebase + panel WWW.

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
Manifest assetów: https://raw.githubusercontent.com/mateusz-brzozowski/radiowezel-dzwonek/master/updates/assets.json

### Publikacja nowej wersji (OTA)

```powershell
.\scripts\publish-release.ps1 -VersionName 1.2.0 -VersionCode 5 -Changelog "Opis"
```

`force: true` **nie** używamy przy tym wdrożeniu (unika pętli instalacji na Device Owner).

### Publikacja paczki alarmów

```powershell
.\scripts\generate-alarm-voices.ps1   # tony + Edge TTS
.\scripts\publish-assets.ps1          # release assets-v1
```

## Pierwszy setup na tablecie

1. Zainstaluj APK i otwórz aplikację.
2. **Wyłącz optymalizację baterii** (Status / Ustawienia).
3. Na Huawei: autostart + chronione aplikacje.
4. Upewnij się, że **automatyczna data i czas** są włączone.
5. Zaimportuj utwory MP3 (Utwory) → **Wczytaj plan lekcji szkoły**.
6. **Ustawienia → Pobierz / odśwież paczkę alarmów** (postęp „Paczka alarmów n/14”).
7. Zakładka **Alarmy**: ustaw tryb Ćwiczenie, PIN (domyślnie `1234`), test na niskiej głośności, STOP.

### Test regresji alarm vs dzwonek

1. Uruchom **Test systemu** z zakładki Alarmy.
2. W trakcie alarmu „Dzwoń teraz” / zaplanowany dzwonek **nie** gra.
3. STOP → harmonogram wraca.

## Alarmy — skrót

| Element | Opis |
|---------|------|
| Ćwiczenie / Prawdziwy | Wybór komunikatu (np. pożar: drill vs real) |
| PIN | 4–6 cyfr w DataStore (Ustawienia) |
| STOP | Zawsze widoczny; też w notyfikacji |
| AssetSync | Pobiera MP3 z GitHub Releases, weryfikuje SHA-256 → `filesDir/alarms/` |

Generowanie lokalne: `scripts/generate_alarm_tones.py`, `scripts/generate_alarm_voices.py` (Edge TTS: Zofia/Marek).

## Zdalne sterowanie (Firebase)

1. Utwórz projekt Firebase: **Authentication** (e-mail/hasło) + **Realtime Database**.
2. Wgraj reguły z `firebase/database.rules.json`.
3. Utwórz konta: dyrektor (panel) + konto urządzenia (tablet).
4. Wypełnij `app/src/main/assets/firebase_config.json` (apiKey, appId, databaseURL, deviceEmail/Password).
5. W aplikacji: Ustawienia → skopiuj **Device ID** → włącz **Zdalne sterowanie**.
6. Panel: `web/` — skopiuj `.env.example` → `.env`, `npm install && npm run build`, hostuj (`firebase deploy --only hosting` z katalogu `firebase/` albo dowolny HTTPS).

Ścieżki RTDB: `devices/{deviceId}/presence|command|ack`, audyt w `audit/`.

## Device Owner (ADB)

```powershell
.\scripts\setup-device-owner.ps1
```

## Ekrany

| Zakładka | Funkcja |
|----------|---------|
| Status | Następny dzwonek, stan baterii/OTA, test |
| Plan | CRUD godzin dzwonków |
| Alarmy | Typy zagrożeń, ćwiczenie/prawdziwy, PIN, STOP |
| Utwory | Import / usuwanie MP3 |
| Święta | Dni wolne (PL + własne) |
| Ustawienia | OTA, paczka alarmów, PIN, Firebase, Huawei, kiosk |
