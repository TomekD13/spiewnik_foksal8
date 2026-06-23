# Śpiewnik KADS Foksal 8

Aplikacja na Androida (tablet - androidApp) oraz na Windows (desktopApp) do błyskawicznego otwierania nut ze śpiewnika w PDF
podczas gry. Organista wpisuje numer pieśni (lub szuka po tytule), a aplikacja
wyświetla właściwe strony. Domyślny tryb widoku zależy od orientacji ekranu
(w poziomie „Pieśń", w pionie „Strona") i można go zmienić jednym przyciskiem.

Działa w pełni **offline**.

> Ten plik opisuje działanie aplikacji (głównie wersji Android). Repo jest **monorepo**:
> aplikacja Android (`:androidApp`) i Windows (`:desktopApp`) ze wspólną logiką (`:core`).
> Strukturę, build i zasady rozbudowy opisuje [`docs/MONOREPO.md`](docs/MONOREPO.md).
> Pułapki implementacyjne: [`dodatkowe_uwagi.md`](dodatkowe_uwagi.md).

---

## 1. Platforma i technologie

| | |
|---|---|
| Urządzenie docelowe | TCL NXTPAPER 14 (MediaTek G99, 8 GB RAM, 14,3", Android 14) |
| Język | Kotlin |
| UI | Android Views + ViewBinding (nie Compose) |
| Architektura | MVVM + Repository |
| `minSdk` | **28** (Android 9 — obniżone z 34 dla zgodności z emulatorami/starszymi urządzeniami) |
| `targetSdk` / `compileSdk` | 34 |
| Persystencja | SharedPreferences |
| Orientacja | podąża za systemem (`fullSensor`), domyślnie landscape |

---

## 2. Źródła danych

### 2.1 PDF — `shared-assets/Spiewnik.pdf`
~28 MB, 692 strony. **Marginesy są przycięte** (patrz [sekcja 8](#8-przycinanie-marginesów-pdf)),
żeby nuty były jak największe. Android renderuje natywnym `PdfRenderer`, Windows — PDFBox.
Plik leży w `shared-assets/` (wspólny dla obu apek).

### 2.2 Mapowanie pieśni — `shared-assets/piesni.json`
UTF-8, ~700 rekordów, posortowane rosnąco po `nr_piesni`. Schemat (model [`Song.kt`](core/src/main/kotlin/com/spiewnik/app/data/Song.kt) w `:core`):

```json
{
  "nr_piesni": 123,
  "tytul": "Tytuł pieśni",
  "strony_pdf": [12, 13],
  "interpolated": false
}
```

- `strony_pdf` — strony PDF liczone **od 1**; pieśń ma 1 lub 2 strony.
- `interpolated` — flaga informacyjna (strony szacowane); wczytywana, nie zmienia zachowania.
- Konwersja na indeks `PdfRenderer` (0-based): `pdfIndex = jsonPage - 1` ([`PageConverter.kt`](core/src/main/kotlin/com/spiewnik/app/utils/PageConverter.kt) w `:core`).

---

## 3. Ekran główny

```
┌───────────────────────────────────────────────────────────────────────┐
│ [Nr 123 · Tytuł]        [Strony 12–13] [Wybierz] [Holyrics] [⚙]        │ ← górny pasek (info)
├───────────────────────────────────────────────────────────────────────┤
│ [etNumber][Idź]  [actvTitle szukaj po tytule…]      [Rozkładówka]      │ ← input row (chowany)
├───────────────────────────────────────────────────────────────────────┤
│  ◀  │            STRONA 12          │           STRONA 13          │ ▶ │ ← obszar PDF
└───────────────────────────────────────────────────────────────────────┘
```

**Górny pasek (info):** numer + tytuł pieśni, aktualne strony, przycisk
**„Wybierz"/„Schowaj pasek"**, przycisk **„Holyrics"**, ikona **⚙** (ustawienia).

**Input row** (domyślnie widoczny): pole numeru `etNumber` (klawiatura numeryczna,
maks. 4 znaki) + **„Idź"** (i Enter), pole autocomplete `actvTitle` (szukanie po tytule),
przycisk trybu nawigacji.

**Obszar PDF:** rozkładówka 2 stron (lub pojedyncza wyśrodkowana), duże przyciski ◀ ▶.

Stan startowy: przy pierwszym uruchomieniu otwiera pieśń nr 1.

### 3.1 Chowanie pasków
Dwa niezależne mechanizmy zwiększające obszar nut:
- **Przycisk „Wybierz"/„Schowaj pasek"** w górnym pasku — chowa/pokazuje **input row**
  (animacja slide 250 ms).
- **Tapnięcie w środek ekranu** (obszar PDF) — chowa/pokazuje **oba paski naraz**
  (górny + input row) dla pełnoekranowych nut. Strzałki ◀ ▶ to osobne widoki, więc
  tapnięcie ich nawiguje, a nie chowa paski.

### 3.2 Zoom i pan
- Zakres zoom **1×–5×** (pinch). Bitmapy renderowane w 2× rozdzielczości ekranu (ostrość przy zoomie).
- Zoom **nie** resetuje się przy zmianie pieśni; resetuje się do 1× po zamknięciu aplikacji (nie jest persystowany).
- Pan ograniczony do krawędzi strony; resetuje się (wyśrodkowanie) przy każdej zmianie pieśni.

---

## 4. Wybór pieśni i wyszukiwanie

- Wpisanie numeru N + „Idź"/Enter, lub wybór z pola tytułu.
- **Pole tytułu = spis treści:** dotknięcie otwiera wybierak — duża, przewijalna lista wszystkich
  pieśni (`nr. tytuł`) z polem szukania u góry; wpisywanie filtruje po numerze lub tytule
  (case-insensitive, polskie znaki). Dotknięcie pozycji otwiera pieśń.
- Po akcji „Idź"/wyborze pieśni klawiatura jest chowana.
- Po wybraniu: ustaw pieśń, załaduj `strony_pdf`, reset indeksu stron i pan.
- `strony_pdf` puste → Toast „Brak strony w śpiewniku"; nieznany numer → Toast „Nie znaleziono pieśni o numerze N".

---

## 5. Tryby nawigacji

Przełączane **przyciskiem trybu w input row** (wybór trybu nie jest już w ustawieniach).

| Tryb | Etykieta | Krok ◀ ▶ |
|---|---|---|
| `SPREAD` | Rozkładówka | 2 strony (12–13 → 14–15) |
| `PAGE` | Strona | 1 strona (12–13 → 13–14) |
| `SONG` | Pieśń | skok do poprzedniej/następnej pieśni |

- Po ostatniej stronie pieśni (SPREAD/PAGE) → przejście do pierwszej strony następnej pieśni.
- Granice: na pierwszej pieśni ◀ wyszarzony; na ostatniej ▶ wyszarzony.

### 5.1 Tryb domyślny zależny od orientacji
Reguła jest czysta i współdzielona z desktopem ([`NavMode.defaultFor`](core/src/main/kotlin/com/spiewnik/app/NavMode.kt) w `:core`):

- **pion (portrait) → `PAGE`** (pojedyncza strona — 2 strony obok siebie byłyby za małe),
- **poziom (landscape) → `SONG`**.

Obrót ekranu przywraca tryb domyślny dla nowej orientacji (przy wejściu w `SONG` otwiera
pieśń odpowiadającą bieżącej stronie). Przyciskiem trybu można ręcznie wybrać dowolny tryb
— wybór obowiązuje do następnego obrotu. Desktop (okno poziome) startuje w trybie `SONG`.

---

## 6. Integracja z Holyrics

Pozwala pobrać playlistę pieśni z Holyrics i podświetlić aktualnie wyświetlaną.
Tablet i Holyrics muszą być w tej samej sieci WiFi; numery pieśni muszą być zgodne.

### 6.1 Działanie
- Przycisk **„Holyrics"** w górnym pasku otwiera `PopupWindow` (zakotwiczony pod przyciskiem,
  szer. 1/3 ekranu, lista pieśni pionowo z przewijaniem). Ponowne kliknięcie zamyka popup.
- Przy otwarciu równolegle pobierane są: **playlista** i **aktualnie grana pieśń**.
- Każdy wiersz: `"<nr>. <tytuł>"`. Kliknięcie → `openSong(nr)` i zamknięcie popupu.
- **Aktualnie wyświetlana pieśń** (z Holyrics) jest podświetlona: prefiks **▶**, tło akcentowe,
  pogrubienie, oraz auto-scroll do tego wiersza.
- **Auto-follow** (opcja w ustawieniach): gdy włączona, aplikacja co ~2 s odpytuje
  `GetCurrentPresentation` i sama otwiera pieśń wyświetlaną na rzutniku (Holyrics zawsze wygrywa).
  Polling tylko na pierwszym planie; `data:null` (nic na rzutniku) nie zmienia pieśni.

### 6.2 API Holyrics
| | |
|---|---|
| Protokół / port | HTTP / **8091** |
| Metoda | POST, body `{}` |
| Playlista | `…/api/GetLyricsPlaylist?token=<token>` → numer w polu **`title`** |
| Aktualna pieśń | `…/api/GetCurrentPresentation?token=<token>` → numer w polu **`data.name`** przy `data.type=="song"` (`data: null` gdy nic nie wyświetlane) |

Parser: [`HolyricsRepository.kt`](androidApp/src/main/kotlin/com/spiewnik/app/holyrics/HolyricsRepository.kt)
(`HttpURLConnection`, timeouty 3 s, pomija pozycje bez numeru).

### 6.3 Konfiguracja
- **IP i token** wpisywane w ustawieniach aplikacji (⚙ → sekcja Holyrics), zapisywane w SharedPreferences.
- **Skan QR (najszybciej):** przycisk **„Skanuj kod QR"** w sekcji Holyrics uruchamia aparat
  (ZXing) i z kodu QR z Holyrics wypełnia pola **IP** i **token** — użytkownik sprawdza i klika
  „Zapisz". Z QR brane są tylko `ips[0]` i `token`; **port i `enabled` są ignorowane**
  (parsowanie: [`HolyricsQrParser`](core/src/main/kotlin/com/spiewnik/app/holyrics/HolyricsQrParser.kt) w `:core`).
  Format QR: `{"enabled":true,"ips":["192.168.x.x"],"port":N,"token":"..."}`. Wymaga uprawnienia
  `CAMERA`; ręczne wpisanie zawsze pozostaje dostępne (feature tylko na Androidzie).
- **Token** w Holyrics: `Narzędzia/Tools → API` (pole ID odbiornika API).
- **Uprawnienia API** w Holyrics: włącz `GetLyricsPlaylist` **oraz** `GetCurrentPresentation`
  (a dla wysyłania — sekcja 6.5 — także `SearchLyrics`, `AddLyricsToPlaylist`, `ShowLyrics`).
- Aplikacja ma uprawnienie `INTERNET` i `network_security_config` zezwalający na cleartext HTTP (sieć lokalna).

### 6.4 Komunikaty
| Sytuacja | Toast |
|---|---|
| Puste IP/token | „Uzupełnij IP i token Holyrics w ustawieniach" |
| Timeout / brak połączenia (playlista) | „Holyrics niedostępny" |
| Pusta playlista | „Playlista Holyrics jest pusta" |
| Numer z Holyrics spoza `piesni.json` | pomijany bez komunikatu |

Błąd pobrania aktualnej pieśni jest tylko logowany (info drugorzędne, bez Toasta).

### 6.5 Wysyłanie pieśni do Holyrics (sterowanie w drugą stronę)
Opcjonalne — włączane przełącznikiem **„Wysyłaj pieśni do Holyrics"** w ustawieniach
(jak auto-follow). Gdy włączone, w górnym pasku obok pieśni pojawia się przycisk:

1. **„Wyślij do Holyrics"** (niebieski) — numer otwartej pieśni jest zamieniany na `id`
   biblioteki Holyrics (`SearchLyrics`, **dokładne** dopasowanie tytułu = numer) i dodawany
   do playlisty (`AddLyricsToPlaylist`). Przycisk zmienia się w **„Wyświetl"** (pomarańczowy).
2. **„Wyświetl"** — rzuca pieśń na ekran Holyrics (`ShowLyrics`).

**Przycisk odzwierciedla zawartość playlisty Holyrics:** jeśli otwarta pieśń jest już
w playliście (dodana wcześniej albo przez operatora), przycisk od razu pokazuje **„Wyświetl"**
— bez ponownego dodawania. Stan playlisty (mapa `numer → id` z `GetLyricsPlaylist`) odświeżany
jest przy starcie/wznowieniu, przy włączeniu opcji, po otwarciu popupu Holyrics oraz po „Wyślij"
(optymistycznie, więc przycisk przeskakuje natychmiast). Przełączenie na pieśń spoza playlisty
pokazuje „Wyślij do Holyrics".

Logika i parser współdzielone w `:core` ([`HolyricsClient`](core/src/main/kotlin/com/spiewnik/app/holyrics/HolyricsClient.kt),
[`HolyricsParser`](core/src/main/kotlin/com/spiewnik/app/holyrics/HolyricsParser.kt) — `parseSongId`/`parsePlaylistData`).
Wymaga włączenia w Holyrics metod: `GetLyricsPlaylist`, `SearchLyrics`, `AddLyricsToPlaylist`, `ShowLyrics`.
Współgra z auto-follow (po „Wyświetl" Holyrics pokazuje pieśń, którą apka i tak już ma otwartą).

| Sytuacja | Toast |
|---|---|
| Brak pieśni w bibliotece Holyrics | „Nie znaleziono pieśni N w Holyrics" |
| Dodano do playlisty | „Dodano pieśń N do playlisty Holyrics" |
| Wyświetlono | „Wyświetlono w Holyrics" |

---

## 7. Ustawienia, pamięć stanu, błędy

**Ustawienia (⚙):** reset ostatniej pozycji (wraca do pieśni 1), IP/token Holyrics
(z opcją **skanowania kodu QR**), przełącznik auto-follow Holyrics, przełącznik
**wysyłania pieśni do Holyrics** (sekcja 6.5),
**Instrukcja obsługi** (dialog z opisem nawigacji i konfiguracji Holyrics), informacje o aplikacji.
Wyboru trybu nawigacji **nie ma** w ustawieniach — służy do tego przycisk trybu, a tryb domyślny
wynika z orientacji (sekcja 5.1). Orientacją zarządza system — brak opcji w aplikacji.

**SharedPreferences** ([`AppSettings.kt`](androidApp/src/main/kotlin/com/spiewnik/app/settings/AppSettings.kt)):
`last_song_number` (1), `last_page_index` (0), `last_pdf_page` (1),
`holyrics_ip` (""), `holyrics_token` (""), `holyrics_auto_follow` (false), `holyrics_send` (false). Tryb startowy wynika z orientacji ekranu
(nie jest odtwarzany z preferencji). Przy starcie powrót do ostatniej pieśni/strony.

**Błędy:** komunikaty przejściowe jako Toast; błędy krytyczne (brak/niepoprawny PDF lub JSON)
jako baner. Wszystkie wyjątki logowane do Logcat, bez crasha.

**Rendering / cache** ([`PdfPageCache.kt`](androidApp/src/main/kotlin/com/spiewnik/app/pdf/PdfPageCache.kt)):
renderowane tylko widoczne strony, LRU cache bitmap (1/6 RAM), klucz `"pageIndex:WxH"`.
Bitmapy w formacie **`ARGB_8888`** — wymagane przez `PdfRenderer.render()`; `RGB_565` dawało
czarny ekran na realnym urządzeniu (Android 14), mimo że przechodziło na emulatorze.
Rozdzielczość renderowania 2× (ostrość przy zoomie). PDF kopiowany do `cacheDir` przed
otwarciem (`PdfRenderer` wymaga FD od offsetu 0).

---

## 8. Przycinanie marginesów PDF

Skrypt [`crop_pdf.py`](crop_pdf.py) (PyMuPDF) ustawia każdej stronie nutowej własny
**CropBox** — bezstratnie (bez re-rasteryzacji), więc `PdfRenderer` renderuje już tylko
przyciętą stronę **bez zmian w kodzie aplikacji**.

Reguła (per strona, na podstawie `piesni.json`):
- Tylko 660 stron nutowych (suma `strony_pdf`); 32 strony nienutowe (tytułowe/teksty) nietknięte.
- **Góra:** do pierwszej treści (tytuł lub pięciolinia) + 8 pt bufora. Strona często zaczyna się
  kontynuacją poprzedniej pieśni, więc tniemy tylko biały margines — nigdy w treść.
- **Boki:** do realnego atramentu + 8 pt, podłoga szerokości 376 pt.
- **Dół:** pieśń 2-stronicowa → usuń tylko stopkę (biel zostaje → rozkładówka spójna);
  pieśń 1-stronicowa → ciasno do nut. Stopka wykrywana po białej przerwie nad nią
  (ogonki nut schodzące pod pięciolinię są bezpieczne).

**Regeneracja:** `python crop_pdf.py` — czyta zawsze z pristine `Spiewnik_original.pdf`
(idempotentne) i nadpisuje asset. Oryginał trzymany jest w `Spiewnik_original.pdf` (gitignore;
dodatkowo w historii git). Szczegóły i pułapki: [`dodatkowe_uwagi.md`](dodatkowe_uwagi.md).

---

## 9. Struktura projektu

```
com.spiewnik.app/
├── MainActivity.kt          // UI, gesty (zoom/pan, tap-toggle pasków), popup Holyrics
├── SongViewModel.kt         // stan, nawigacja, fetch Holyrics
├── data/
│   ├── Song.kt              // model: number, title, pages, interpolated
│   └── SongRepository.kt    // ładowanie/parsowanie piesni.json, wyszukiwanie
├── pdf/
│   └── PdfPageCache.kt      // PdfRenderer + LRU cache bitmap
├── holyrics/
│   └── HolyricsRepository.kt// HTTP do Holyrics (playlista + aktualna pieśń)
├── settings/
│   └── AppSettings.kt       // wrapper SharedPreferences
├── ui/settings/
│   └── SettingsFragment.kt  // dialog ustawień (reset, Holyrics IP/token, skan QR)
└── utils/
    └── PageConverter.kt     // konwersja stron JSON(1-based) ↔ PDF(0-based)
```

---

## 10. Build i instalacja

> Pełny opis struktury monorepo i build obu apek (Android + Windows): [`docs/MONOREPO.md`](docs/MONOREPO.md).

### Wymagania
- JDK 17 (JBR z Android Studio wystarcza do buildu/testów; **instalator Windows** wymaga
  pełnego JDK z `jpackage` — JBR go nie ma, ale CI/temurin tak). PDF i `piesni.json` są w `shared-assets/`.

### Build lokalny
```bash
./gradlew :androidApp:assembleRelease -PbuildNumber=<n>   # podpisany APK -> androidApp/build/outputs/apk/release/app-release.apk
./gradlew :androidApp:assembleDebug                       # wersja debug
./gradlew :desktopApp:run                                 # uruchom apkę Windows
./gradlew test                                            # testy wszystkich modułów
```
Release Androida jest podpisywany **stałym kluczem** `androidApp/sideload.jks` (klucz tylko do
sideloadu, hasło publiczne — nie Play Store), dzięki czemu aktualizacje instalują się „w miejsce".

**Wersjonowanie:** `versionCode`/`versionName` pochodzą z numeru buildu CI
(`-PbuildNumber=<n>` → `versionName 1.0.<n>`). Build lokalny bez tego parametru ma wersję
`1.0.0-dev`. Wersja jest widoczna w ustawieniach (Informacje o aplikacji).

### CI / pobranie gotowych aplikacji
Push na `main` uruchamia GitHub Actions:
- [`build-apk.yml`](.github/workflows/build-apk.yml) → **Android**: release APK jako GitHub Release (`app-release.apk`).
- [`build-desktop.yml`](.github/workflows/build-desktop.yml) → **Windows**: instalator `.msi` jako Release
  „Śpiewnik Windows #N" (klik → instaluje, z dołączonym JRE).

Wersja Windows ma parytet funkcjonalny z Androidem (poza zoomem); szczegóły uruchamiania
i budowania obu apek: [`docs/URUCHAMIANIE_I_TESTY.md`](docs/URUCHAMIANIE_I_TESTY.md).

### Instalacja na tablecie
1. Skopiuj `app-release.apk` na tablet i otwórz (instalacja przez kliknięcie).
2. Jeśli była zainstalowana wersja podpisana **innym** kluczem (starsze buildy) — najpierw ją
   odinstaluj (konflikt podpisów blokuje aktualizację). Od wersji ze stałym kluczem nie trzeba.
3. Po instalacji wpisz IP i token Holyrics w ustawieniach.

### Emulator (np. LDPlayer, ADB)
```bash
adb connect localhost:5555
adb -s localhost:5555 install -t app-debug.apk   # -t bo debug bywa testOnly
```

---

## 11. Testy

`JUnit 4` + `Mockk` + `kotlinx-coroutines-test`. Większość logiki jest w `:core`, więc
testy pisane są raz i obowiązują obie platformy:
- `PageConverter` — konwersja 1-based ↔ 0-based.
- `SongRepository` / `SongCatalog` — parsowanie `piesni.json`, wyszukiwanie po numerze i tytule (case-insensitive, polskie znaki).
- Logika nawigacji — kroki i granice dla SPREAD/PAGE/SONG.
- `NavMode.defaultFor` — tryb domyślny wg orientacji (poziom→Pieśń, pion→Strona).
- `HolyricsParser` / `HolyricsQrParser` — parsowanie odpowiedzi API oraz kodu QR (IP+token, port ignorowany, odporność na błędny JSON).
- `AutoFollow`, `LruCache` — decyzja auto-podążania, polityka eviction.

```bash
./gradlew test                      # wszystkie moduły (CI odpala to przy każdym pushu)
./gradlew :core:test                # sama logika wspólna
./gradlew :androidApp:testDebugUnitTest
```
