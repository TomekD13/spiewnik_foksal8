# Uruchamianie i testowanie

Praktyczny runbook. Komendy odpalaj z katalogu głównego repo.

## Wymagania
- **JDK 17** wystarcza do buildu, uruchomienia i testów. JBR z Android Studio jest OK
  (`JAVA_HOME` na `…/AndroidStudio/jbr`).
- **Instalator Windows (.msi/.exe)** wymaga **pełnego JDK 17 z `jpackage`** — JBR go nie ma.
  Lokalnie do testów wystarczy `:desktopApp:run`; instalator i tak buduje CI (temurin).
- W PowerShell ustaw zmienną przed komendami: `$env:JAVA_HOME="E:\AndroidStudio\jbr"`.

---

## Aplikacja Android

```bash
# Uruchom na podłączonym urządzeniu / emulatorze (instaluje i odpala)
./gradlew :androidApp:installDebug

# Sam APK debug  ->  androidApp/build/outputs/apk/debug/app-debug.apk
./gradlew :androidApp:assembleDebug

# Podpisany APK release (instalowalny przez kliknięcie)  ->  .../release/app-release.apk
./gradlew :androidApp:assembleRelease -PbuildNumber=1
```

Emulator po ADB (np. LDPlayer):
```bash
adb connect localhost:5555
adb -s localhost:5555 install -t androidApp/build/outputs/apk/debug/app-debug.apk   # -t bo debug bywa testOnly
```

Gotowy APK z GitHuba: zakładka **Releases** (buduje workflow `build-apk.yml`).

---

## Aplikacja Windows (Compose Desktop)

```bash
# Uruchom apkę (NIE wymaga jpackage)
./gradlew :desktopApp:run

# Katalog z apką + dołączony JRE (app-image, bez instalatora)
./gradlew :desktopApp:createDistributable
#   ->  desktopApp/build/compose/binaries/main/app/Spiewnik/

# Instalator .msi (wymaga pełnego JDK z jpackage + WiX — lokalnie JBR nie ma jpackage)
./gradlew :desktopApp:packageMsi
```

**Gotowy instalator z GitHuba** (buduje `build-desktop.yml` na każdym pushu na `main`):
- **Releases → „Śpiewnik Windows #N" → plik `.msi`** — klik, instaluje (dołączony JRE, bez Javy).
  Aktualizacje wchodzą w miejsce (stałe `upgradeUuid`).
- Albo **Actions → Build Windows app → Artifacts**: `spiewnik-windows-installer` (.msi)
  lub `spiewnik-windows-app` (folder z `.exe`).

---

## Testy

```bash
# Wszystkie testy wszystkich modułów (core + android + desktop)
./gradlew test

# Per moduł
./gradlew :core:test                      # logika wspólna (mapowanie, nawigacja, parsing)
./gradlew :androidApp:testDebugUnitTest   # testy jednostkowe Androida
./gradlew :desktopApp:test                # smoke test: wczytanie PDF/JSON + render PDFBox
```

Raporty HTML po uruchomieniu:
- `core/build/reports/tests/test/index.html`
- `androidApp/build/reports/tests/testDebugUnitTest/index.html`
- `desktopApp/build/reports/tests/test/index.html`

> **Uwaga:** od teraz `./gradlew test` jest też uruchamiany w CI przy każdym pushu —
> czerwony test blokuje build. (Wcześniej CI robił tylko `assembleRelease` i testy nie
> były odpalane.)

---

## Szybka diagnostyka
- `e: …: Unresolved reference` → błąd kompilacji, sprawdź importy/wersje.
- `jpackage.exe is missing` → używasz JBR; do instalatora potrzebny pełny JDK (albo zbuduj na CI).
- Android: `INSTALL_FAILED_TEST_ONLY` → dodaj `-t` do `adb install` (APK debug).
- Android: „App not installed" przy aktualizacji → inny podpis; odinstaluj starą wersję.
