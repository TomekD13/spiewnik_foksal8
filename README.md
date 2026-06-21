# Śpiewnik KADS Foksal 8

Aplikacja na Androida (tablet) do błyskawicznego otwierania nut ze śpiewnika w PDF
podczas gry. Organista wpisuje numer pieśni (lub szuka po tytule), a aplikacja
wyświetla właściwe strony — domyślnie jako rozkładówkę (2 strony obok siebie).

Działa w pełni **offline**.

> Ten plik jest jedynym, kanonicznym opisem działania aplikacji. Wcześniejsze pliki
> `wymagania*.md` / `wynik_*.md` zostały skonsolidowane tutaj. Pułapki i wskazówki
> implementacyjne są w [`dodatkowe_uwagi.md`](dodatkowe_uwagi.md).

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

### 2.1 PDF — `app/src/main/assets/Spiewnik.pdf`
~28 MB, 692 strony. **Marginesy są przycięte** (patrz [sekcja 8](#8-przycinanie-marginesów-pdf)),
żeby nuty były jak największe. Renderowany natywnym `PdfRenderer`.

### 2.2 Mapowanie pieśni — `app/src/main/assets/piesni.json`
UTF-8, ~700 rekordów, posortowane rosnąco po `nr_piesni`. Schemat (model [`Song.kt`](app/src/main/kotlin/com/spiewnik/app/data/Song.kt)):

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
- Konwersja na indeks `PdfRenderer` (0-based): `pdfIndex = jsonPage - 1` ([`PageConverter.kt`](app/src/main/kotlin/com/spiewnik/app/utils/PageConverter.kt)).

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

- Wpisanie numeru N + „Idź"/Enter, lub wybór z autocomplete po tytule.
- Autocomplete: podpowiedzi od 1 znaku, case-insensitive, obsługa polskich znaków.
- Po wybraniu: ustaw pieśń, załaduj `strony_pdf`, reset indeksu stron i pan.
- `strony_pdf` puste → Toast „Brak strony w śpiewniku"; nieznany numer → Toast „Nie znaleziono pieśni o numerze N".

---

## 5. Tryby nawigacji

Przełączane przyciskiem w input row lub w ustawieniach; zapamiętywane w SharedPreferences (`nav_mode`).

| Tryb | Etykieta | Krok ◀ ▶ |
|---|---|---|
| `SPREAD` | Rozkładówka | 2 strony (12–13 → 14–15) |
| `PAGE` | Strona | 1 strona (12–13 → 13–14) |
| `SONG` | Pieśń | skok do poprzedniej/następnej pieśni |

- Po ostatniej stronie pieśni (SPREAD/PAGE) → przejście do pierwszej strony następnej pieśni.
- Granice: na pierwszej pieśni ◀ wyszarzony; na ostatniej ▶ wyszarzony.
- **Portrait:** zawsze 1 strona (nigdy rozkładówka), skalowana na pełny obszar; w SPREAD ▶ przechodzi do następnej pieśni.

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

### 6.2 API Holyrics
| | |
|---|---|
| Protokół / port | HTTP / **8091** |
| Metoda | POST, body `{}` |
| Playlista | `…/api/GetLyricsPlaylist?token=<token>` → numer w polu **`title`** |
| Aktualna pieśń | `…/api/GetCurrentPresentation?token=<token>` → numer w polu **`data.name`** przy `data.type=="song"` (`data: null` gdy nic nie wyświetlane) |

Parser: [`HolyricsRepository.kt`](app/src/main/kotlin/com/spiewnik/app/holyrics/HolyricsRepository.kt)
(`HttpURLConnection`, timeouty 3 s, pomija pozycje bez numeru).

### 6.3 Konfiguracja
- **IP i token** wpisywane w ustawieniach aplikacji (⚙ → sekcja Holyrics), zapisywane w SharedPreferences.
- **Token** w Holyrics: `Narzędzia/Tools → API` (pole ID odbiornika API).
- **Uprawnienia API** w Holyrics: włącz `GetLyricsPlaylist` **oraz** `GetCurrentPresentation`.
- Aplikacja ma uprawnienie `INTERNET` i `network_security_config` zezwalający na cleartext HTTP (sieć lokalna).

### 6.4 Komunikaty
| Sytuacja | Toast |
|---|---|
| Puste IP/token | „Uzupełnij IP i token Holyrics w ustawieniach" |
| Timeout / brak połączenia (playlista) | „Holyrics niedostępny" |
| Pusta playlista | „Playlista Holyrics jest pusta" |
| Numer z Holyrics spoza `piesni.json` | pomijany bez komunikatu |

Błąd pobrania aktualnej pieśni jest tylko logowany (info drugorzędne, bez Toasta).

---

## 7. Ustawienia, pamięć stanu, błędy

**Ustawienia (⚙):** tryb nawigacji (Rozkładówka/Strona/Pieśń), reset ostatniej pozycji
(wraca do pieśni 1, nie rusza trybu), IP/token Holyrics, informacje o aplikacji.
Orientacją zarządza system — brak opcji w aplikacji.

**SharedPreferences** ([`AppSettings.kt`](app/src/main/kotlin/com/spiewnik/app/settings/AppSettings.kt)):
`last_song_number` (1), `last_page_index` (0), `last_pdf_page` (1), `nav_mode` (SPREAD),
`holyrics_ip` (""), `holyrics_token` (""). Przy starcie powrót do ostatniej pieśni.

**Błędy:** komunikaty przejściowe jako Toast; błędy krytyczne (brak/niepoprawny PDF lub JSON)
jako baner. Wszystkie wyjątki logowane do Logcat, bez crasha.

**Rendering / cache** ([`PdfPageCache.kt`](app/src/main/kotlin/com/spiewnik/app/pdf/PdfPageCache.kt)):
renderowane tylko widoczne strony, LRU cache bitmap (1/6 RAM), klucz `"pageIndex:WxH"`.
PDF kopiowany do `cacheDir` przed otwarciem (`PdfRenderer` wymaga FD od offsetu 0).

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
│   └── SettingsFragment.kt  // dialog ustawień (tryb, reset, Holyrics IP/token)
└── utils/
    └── PageConverter.kt     // konwersja stron JSON(1-based) ↔ PDF(0-based)
```

---

## 10. Build i instalacja

### Wymagania
- Android Studio / JDK 17 (JBR). PDF i `piesni.json` są już w `app/src/main/assets/`.

### Build lokalny
```bash
./gradlew assembleRelease   # podpisany, instalowalny APK -> app/build/outputs/apk/release/app-release.apk
./gradlew assembleDebug     # wersja debug
```
Release jest podpisywany **stałym kluczem** `app/sideload.jks` (klucz tylko do sideloadu,
hasło publiczne — nie Play Store), dzięki czemu aktualizacje instalują się „w miejsce".

### CI / pobranie gotowego APK
Push na `main` uruchamia GitHub Actions ([`.github/workflows/build-apk.yml`](.github/workflows/build-apk.yml)),
który buduje release APK i publikuje go jako GitHub Release. Pobierz `app-release.apk`
z najnowszego release.

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

`JUnit 4` + `Mockk` + `kotlinx-coroutines-test` (`src/test`):
- `PageConverter` — konwersja 1-based ↔ 0-based.
- `SongRepository` — parsowanie `piesni.json`, wyszukiwanie po numerze i tytule (case-insensitive, polskie znaki).
- Logika nawigacji — kroki i granice dla SPREAD/PAGE/SONG, zachowanie portrait.

```bash
./gradlew testDebugUnitTest
```
