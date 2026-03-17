# Wynik implementacji — wymagania_2.md

## Co zostało zmienione

### Konfiguracja projektu

| Plik | Zmiana |
|------|--------|
| `gradle/libs.versions.toml` | Dodano: `mockk 1.13.10`, `kotlinx-coroutines-test` |
| `app/build.gradle.kts` | `minSdk` 26 → **34**, włączono `buildConfig = true`, dodano zależności testowe |
| `app/src/main/AndroidManifest.xml` | Orientacja `sensorLandscape` → **`fullSensor`** (system decyduje), dodano `android:largeHeap="true"` |

### Model danych

| Plik | Zmiana |
|------|--------|
| `data/Song.kt` | Dodano `@SerializedName` dla nowych nazw pól JSON (`nr_piesni`, `tytul`, `strony_pdf`) oraz pole `interpolated: Boolean` |
| `data/SongRepository.kt` | Nazwa pliku `songs.json` → **`piesni.json`** |

### Nowe pliki

| Plik | Opis |
|------|------|
| `settings/AppSettings.kt` | Wrapper SharedPreferences — przechowuje `lastSongNumber`, `lastPageIndex`, `navMode` |
| `utils/PageConverter.kt` | Konwersja między stronami JSON (1-based) a indeksami PdfRenderer (0-based) |
| `ui/settings/SettingsFragment.kt` | Dialog ustawień: wybór trybu nawigacji (radio), reset pozycji, wersja aplikacji |
| `res/layout/fragment_settings.xml` | Layout dialoga ustawień |
| `res/color/nav_button_tint.xml` | ColorStateList dla tła przycisków nawigacyjnych (aktywny / wyszarzony) |
| `res/color/nav_button_text_tint.xml` | ColorStateList dla tekstu przycisków nawigacyjnych |

### Zaktualizowane pliki

| Plik | Zmiana |
|------|--------|
| `pdf/PdfPageCache.kt` | Renderowanie w **2× rozdzielczości ekranu** (mnożnik `* 2f`) — sharp przy zoom do 2× |
| `SongViewModel.kt` | Pełna przebudowa: `AppSettings` zamiast raw prefs, `LoadState` sealed class, `toastEvent LiveData`, `hasPrevSong`/`hasNextSong` w `UiState`, portrait+SPREAD nawiguje do następnej pieśni, `setNavMode()`, `resetPosition()` |
| `MainActivity.kt` | Toast dla błędów przejściowych, baner tylko dla błędów krytycznych, zoom/pan (pinch 1×–5×, reset pan przy zmianie pieśni), przycisk ⚙ → SettingsFragment, wyszarzanie przycisków ◀▶ |
| `res/layout/activity_main.xml` | Dodano `btnSettings`, `pdfContainer` (LinearLayout z id dla zoom/pan), `app:backgroundTint` z selectorami |
| `res/values/colors.xml` | Paleta **VS Code Dark+** (`#1E1E1E`, `#252526`, `#0E639C`, …) |
| `res/values/strings.xml` | Nowe stringi: ustawienia, błędy, przycisk ⚙ |

### Testy

| Plik | Opis |
|------|------|
| `SongRepositoryTest.kt` | Zaktualizowany format JSON (`nr_piesni`, `tytul`, `strony_pdf`), dodano testy pola `interpolated` |
| `utils/PageConverterTest.kt` | Testy konwersji 1-based ↔ 0-based, round-trip dla 700 stron |
| `NavigationLogicTest.kt` | Testy `canGoLeft`/`canGoRight` dla wszystkich trybów i stanów brzegowych, testy obliczania numerów stron i `displayPages` |

## Kluczowe decyzje architektoniczne

- **Brak drugiego Activity/Fragment dla głównego ekranu** — cała logika w `SongViewModel` + `MainActivity`
- **`LoadState`** oddziela błędy krytyczne (brak PDF/JSON) od błędów przejściowych (brak pieśni → Toast)
- **Zoom/pan** działa przez `scaleX`/`scaleY`/`translationX`/`translationY` na `pdfContainer` — nie wymaga custom View
- **Orientacja** sterowana wyłącznie systemem (`fullSensor`), aplikacja reaguje przez `configChanges` bez restartu Activity
- **Portrait + SPREAD** = zawsze przeskakuje do następnej/poprzedniej pieśni (jednolita logika w ViewModel przez `isPortrait` z `Configuration`)
- **Zoom nie resetuje się** przy zmianie pieśni; **pan resetuje się** przy każdej zmianie pieśni

## Poprawki po testach

| Plik | Zmiana |
|------|--------|
| `pdf/PdfPageCache.kt` | `open()` kopiuje PDF do `cacheDir` przed otwarciem — `PdfRenderer` wymaga seekowalnego FD od offsetu 0, a assets w APK mają niezerowy offset |
| `MainActivity.kt` | `ivRight.visibility = GONE` zamiast `INVISIBLE` — gdy pokazywana jest 1 strona, prawa połowa nie zajmuje miejsca i lewa strona wypełnia cały obszar PDF |
| `activity_main.xml` | Domyślna widoczność `ivRight` zmieniona na `gone` |
| `gradle/libs.versions.toml` | Usunięto zduplikowany wpis `coroutines`; dodano `fragment-ktx 1.8.1` |
| `app/build.gradle.kts` | Dodano `implementation(libs.fragment.ktx)` — wymagane przez `activityViewModels()` w `SettingsFragment` |

## Pliki nie zmieniane

- `data/SongRepository.kt` — logika wyszukiwania pozostała bez zmian (tylko nazwa pliku JSON)
- `res/values/themes.xml` — bez zmian (kolory zaktualizowane w colors.xml)
- `res/drawable/input_background.xml` — bez zmian
