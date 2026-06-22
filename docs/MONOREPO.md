# Monorepo — jak to jest zbudowane i jak rozbudowywać

Jedno repozytorium, dwie aplikacje (Android + Windows) i wspólna logika. Ten plik to
„instrukcja produkcyjna": gdzie co leży, jak budować, i jak dokładać funkcje, żeby nie
duplikować kodu i nie psuć drugiej platformy.

## Moduły

```
spiewnik/
├── core/         ← wspólna LOGIKA (kotlin-jvm, ZERO android.*)
├── androidApp/   ← aplikacja Android (Views, PdfRenderer, ViewModel)
├── desktopApp/   ← aplikacja Windows (Compose Desktop, PDFBox)
├── shared-assets/← piesni.json + Spiewnik.pdf (jedno źródło prawdy)
└── tools/        ← crop_pdf.py i inne skrypty
```

### `:core` — wspólna logika
- Zwykły moduł **Kotlin/JVM** (`org.jetbrains.kotlin.jvm`). Android i desktop oba chodzą na
  JVM, więc oba używają tego samego `.jar`. **Nie potrzeba Kotlin Multiplatform.**
- **Zasada żelazna:** w `:core` NIE wolno używać `android.*` (`Context`, `AssetManager`,
  `android.util.Log` itd.). Tylko czysty Kotlin + biblioteki działające na zwykłym JVM (Gson…).
- Co tu jest dziś:
  - `Song`, `SongCatalog` (parsowanie `piesni.json` + wyszukiwanie/nawigacja), `PageConverter`,
  - `NavMode` + `UiState` (reguły nawigacji: rozkładówka/strona/pieśń, granice, numery stron),
  - Holyrics: `HolyricsParser` (parsowanie odpowiedzi), `HolyricsClient` + `HolyricsTransport`
    (sieć z abstrakcją transportu), `AutoFollow` (decyzja auto-podążania),
  - `LruCache` (polityka eviction cache stron).
- Ładowanie plików (assety) i sieć (HTTP) NIE należą do core — core dostaje gotowy
  `String`/`ByteArray` albo transport przez interfejs; *skąd* je wziąć decyduje platforma.

### `:androidApp`
- Aplikacja Android (`com.android.application`), zależy od `:core`.
- Warstwa platformowa: `MainActivity` (Views), `SongViewModel` (LiveData), `PdfPageCache`
  (`PdfRenderer`), `AppSettings` (SharedPreferences), `SettingsFragment`.
- Assety: `Spiewnik.pdf` + `piesni.json` brane z `../shared-assets` (dodatkowy `assets.srcDir`),
  plus własne (`Logo.png`, `token_holyrics.md`).

### `:desktopApp`
- Aplikacja Windows w **Compose Desktop** (`org.jetbrains.compose`), zależy od `:core`.
- Render PDF: **Apache PDFBox** (`PDFRenderer.renderImageWithDPI`, respektuje CropBox) →
  `BufferedImage.toComposeImageBitmap()`, cache przez `LruCache`.
- Funkcje (parytet z Androidem, poza zoomem): nawigacja na `UiState`/`NavMode`, numpad
  ekranowy, spis treści (filtr odporny na polskie znaki + ekranowa klawiatura QWERTY z Shift),
  pełny ekran (tap chowa paski), Holyrics (playlista + podświetlanie + auto-follow),
  ustawienia (`DesktopSettings` → `~/.spiewnik/settings.json`), zapamiętywanie ostatniej pieśni,
  instrukcja obsługi.
- Sieć Holyrics: `HttpUrlTransport` (implementacja `HolyricsTransport` z core).
- Assety: `../shared-assets` jako `resources.srcDir`; ikona okna `logo.png`.
- Pakowanie: `nativeDistributions` z ikoną `icons/spiewnik.ico`, stałym `upgradeUuid`
  (aktualizacje w miejsce) i `modules("jdk.unsupported")` (Gson używa `sun.misc.Unsafe`).

## Jak budować i uruchamiać

```bash
# Android
./gradlew :androidApp:assembleDebug            # APK debug
./gradlew :androidApp:assembleRelease -PbuildNumber=<n>   # podpisany release (wersja 1.0.<n>)

# Windows (desktop)
./gradlew :desktopApp:run                       # uruchom apkę
./gradlew :desktopApp:createDistributable       # katalog z apką + JRE (bez instalatora)
./gradlew :desktopApp:packageMsi                # instalator .msi (wymaga WiX na Windows)

# Wspólne
./gradlew :core:test                            # testy logiki
./gradlew test                                  # testy wszystkich modułów
```

Build lokalny używa JDK 17 (JBR z Android Studio). Ustaw `JAVA_HOME` na `…/jbr` jeśli trzeba.

## Linia produkcyjna (CI)
- `.github/workflows/build-apk.yml` — runner Ubuntu: buduje podpisany **release APK**
  (`:androidApp:assembleRelease`, wersja z `github.run_number`) i publikuje jako GitHub Release.
- `.github/workflows/build-desktop.yml` — runner Windows: instaluje **WiX**, buduje
  `createDistributable` (folder z `.exe` jako fallback) **oraz instalator `.msi`**
  (`packageMsi`), publikuje `.msi` jako Release (tag `windows-<n>`) i wrzuca artefakty.

## Jak dokładać funkcje (reguła decyzyjna)
Zadaj sobie pytanie: **czy to logika, czy UI/platforma?**

- **Logika** (reguły nawigacji, parsowanie, dopasowania, klient Holyrics) → do `:core`,
  raz, używana przez obie apki. Pamiętaj: bez `android.*`. Jak potrzebujesz czegoś
  platformowego (np. zegar, sieć), przekaż to przez parametr/interfejs, a implementację
  dostarcz w app.
- **UI / platforma** (ekrany, render PDF, ustawienia systemowe, klawiatura) → do `:androidApp`
  albo `:desktopApp` osobno. UI NIE jest dziś współdzielone (Android = Views, Windows = Compose).

Przy zmianie w `:core` zawsze odpal `./gradlew test` — testy jednostkowe pilnują, że nie
zmieniłeś zachowania (siatka bezpieczeństwa zamiast ręcznego klikania na obu platformach).

## Czego świadomie NIE zrobiliśmy (i kiedy to zmienić)
- **Brak Kotlin Multiplatform.** Wystarcza kotlin-jvm. KMP wprowadź dopiero, gdy dojdzie
  iOS lub wersja web — wtedy `:core` → `commonMain` i Gson/`HttpURLConnection` zamieniasz na
  `kotlinx.serialization` + `Ktor`.
- **Brak wspólnego UI.** Wspólne ekrany w Compose (Android+Desktop) to osobny, większy krok
  (Compose Multiplatform UI w `commonMain`).
- **Parsowanie i sieć Holyrics już są w `:core`** (`HolyricsParser`, `HolyricsClient`).
  `androidApp/HolyricsRepository` deleguje parsowanie do core, ale wciąż ma własny HTTP
  (`HttpURLConnection`) — można go w przyszłości przepiąć na `HolyricsClient` + transport
  (jak desktop), żeby ujednolicić. To dotknie Androida, więc warto przy okazji innego zadania.
- **`SongRepository` (android)** deleguje już do core `SongCatalog` (tylko ładowanie assetu jest
  platformowe).
