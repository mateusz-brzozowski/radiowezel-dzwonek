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

## Checklist: tablet + strona (do dokończenia)

### Na tablecie
1. Zainstaluj **APK 1.2.2+** (OTA: Ustawienia → Sprawdź aktualizację, albo z GitHub Releases).
2. Ustawienia → **Pobierz paczkę alarmów** (musi być kompletna).
3. Ustawienia → ustaw **własny PIN** (4–6 cyfr) → Zapisz.
4. Ustawienia → włącz **Zdalne sterowanie (Firebase)**.
5. Skopiuj **Device ID** i **kod parowania** (pokazane w Ustawieniach).
6. (Opcja) Utwory + „Wczytaj plan lekcji szkoły”, test „Dzwoń teraz”.

### Na stronie https://radiowezel-dzwonek.web.app
1. Zaloguj się kontem szkoły (e-mail/hasło — nie są w publicznym README).
2. Wklej **Device ID** + **kod parowania** + PIN z tabletu.
3. „Odśwież stan” → zarządzaj Planem / Utworami / Świętami / Alarmami.

Panel: sesja tylko w karcie przeglądarki; 12 min bezczynności = wylogowanie.

## Pierwszy setup na tablecie (niezawodność)

1. Zainstaluj APK i otwórz aplikację.
2. **Wyłącz optymalizację baterii** (Status / Ustawienia).
3. Na Huawei: autostart + chronione aplikacje.
4. Upewnij się, że **automatyczna data i czas** są włączone.
5. Zaimportuj utwory MP3 (Utwory) → **Wczytaj plan lekcji szkoły**.
6. **Ustawienia → Pobierz / odśwież paczkę alarmów**.
7. Ustaw **PIN** i przetestuj Alarmy (ćwiczenie) + STOP.

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

Projekt produkcyjny: **radiowezel-dzwonek** · Hosting: https://radiowezel-dzwonek.web.app  

Na tablecie (APK z wgranym `firebase_config.json`): Device ID + **kod parowania** + PIN.  
Reguły: `firebase/database.rules.json`. Stan: `devices/{id}/state`, komendy: `…/command` (wymagają `remoteToken`).

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
