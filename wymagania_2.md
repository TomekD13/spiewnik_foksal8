Jesteś doświadczonym Android Developerem (Kotlin) oraz architektem aplikacji tabletowych.
Twoim zadaniem jest zaprojektowanie i wygenerowanie kompletnej aplikacji Android zgodnie z poniższą specyfikacją.
Nie upraszczaj założeń.
Stosuj czystą architekturę, separację logiki od UI oraz dobre praktyki Android (MVVM).

---

## 0. PLATFORMA DOCELOWA

Tablet: TCL NXTPAPER 14 (9491GT)
Procesor: MediaTek G99
RAM: 8 GB
Ekran: 14,3"
System: Android 14
Minimalna wersja SDK: API 28 (Android 9) — obniżone z 34 dla zgodności z LDPlayer i innymi urządzeniami
Target SDK: API 34


---

## 1. CEL APLIKACJI

Aplikacja służy organiście do błyskawicznego otwierania nut w PDF podczas gry.

Główna funkcja:
- użytkownik wpisuje numer pieśni
- aplikacja natychmiast wyświetla właściwe strony PDF
- preferowany widok: rozkładówka (2 strony obok siebie)

Aplikacja:
- działa wyłącznie offline
- zoptymalizowana pod duży tablet (14,3")
- domyślnie landscape, orientacja podąża za ustawieniami systemu


---

## 2. TECHNOLOGIE

Platforma: Android
Język: Kotlin
Min SDK: API 28

UI:
- Jetpack Compose (preferowane)
- klasyczne Views dopuszczalne tylko jeśli uzasadnione technicznie

Architektura:
- MVVM
- Repository pattern

Persystencja ustawień: SharedPreferences

Punkt startowy: przebudowa istniejącego repo (nie nowy projekt)


---

## 3. ŹRÓDŁA DANYCH

### 3.1 PDF

Plik: `Spiewnik.pdf`
Lokalizacja: `app/src/main/assets/Spiewnik.pdf`
Rozmiar: ~25 MB, ~700 stron

### 3.2 JSON (mapowanie pieśni)

Plik: `piesni.json`
Lokalizacja: `app/src/main/assets/piesni.json`
Kodowanie: UTF-8
Liczba rekordów: ~700 pieśni
Sortowanie: posortowany rosnąco po `nr_piesni` — aplikacja nie sortuje samodzielnie

Schemat rekordu:
```json
{
  "nr_piesni": 123,
  "tytul": "Tytuł pieśni",
  "strony_pdf": [12, 13],
  "interpolated": false
}
```

Zasady:
- `nr_piesni` – numer pieśni (unikalny, całkowity)
- `tytul` – tytuł pieśni (do wyszukiwania i wyświetlania)
- `strony_pdf` – lista stron PDF liczona od 1, zawsze ciągła (np. [12, 13, 14])
- `interpolated` – flaga informacyjna (bool): czy strony były interpolowane/szacowane. Aplikacja wczytuje pole ale nie zmienia zachowania na jego podstawie.

Przypadki brzegowe:
- `strony_pdf` puste (`[]`) → Toast: „Brak strony w śpiewniku"
- numer pieśni nie istnieje w JSON → Toast: „Nie znaleziono pieśni o numerze N"


---

## 4. NUMERACJA STRON

- W JSON: strona 1 = pierwsza strona PDF
- PdfRenderer używa numeracji od 0
- Konwersja: `pdfIndex = jsonPage - 1`
- Aplikacja musi zawsze wykonywać tę konwersję


---

## 5. EKRAN GŁÓWNY

### 5.1 Układ ogólny

- Orientacja: podąża za ustawieniami systemowymi, domyślnie landscape
- Tryb: pełnoekranowy (immersive, bez paska systemu)
- Ekran nigdy nie wygasza się podczas działania aplikacji (`FLAG_KEEP_SCREEN_ON`)
- Pasek sterowania: domyślnie widoczny, może być schowany przez użytkownika (patrz sekcja 5.4)
- Obsługa tylko przyciskami ekranowymi (bez obsługi klawiatury BT ani przycisków głośności)

### 5.2 Układ elementów

```
┌──────────────────────────────────────────────────────────────────┐
│  [Nr: 123]  [Tytuł pieśni]    [Strony: 12–13]  [Wybierz]  [⚙️]  │  ← pasek informacji (góra)
├──────────────────────────────────────────────────────────────────┤
│ [etNumber][Idź] [actvTitle___________] [Rozkładówka] [Schowaj]  │  ← pasek sterowania (chowany)
├──────────────────────────────────────────────────────────────────┤
│  ◀   │         STRONA 12        │        STRONA 13        │ ▶   │  ← obszar PDF
└──────────────────────────────────────────────────────────────────┘
```

**Pasek informacji (góra) — zawsze widoczny:**
- numer aktualnej pieśni
- tytuł aktualnej pieśni
- aktualnie wyświetlane strony (np. `12–13`)
- przycisk przełączający **„Wybierz" / „Schowaj pasek"** (patrz sekcja 5.4)
- przycisk **„Holyrics"** — bez akcji, do przyszłego użycia
- ikona ⚙️ do ustawień

**Pasek sterowania (domyślnie widoczny, może być schowany):**
- pole numeryczne `etNumber` (tylko cyfry, klawiatura numeryczna, maks. 4 znaki)
- przycisk „Idź" + obsługa klawisza Enter
- pole autocomplete `actvTitle` do wyszukiwania po tytule (osobne, obok numeru)
- przycisk trybu nawigacji (Rozkładówka / Strona / Pieśń)

**Obszar PDF:**
- Dwie strony obok siebie (rozkładówka) – zmaksymalizowany rozmiar, obie strony widoczne w całości
- Jeśli jedna strona – wyśrodkowana pojedyncza strona
- Obsługa zoom/pan (patrz sekcja 5.3)
- Przyciski ◀ ▶ duże, czytelne (obsługa w rękawiczkach)

**Stan startowy:**
- Przy pierwszym uruchomieniu (brak `last_song_number`) → automatycznie otwiera pieśń nr 1

### 5.4 Chowanie paska sterowania

- W górnym pasku znajduje się jeden przycisk przełączający:
  - Gdy pasek sterowania jest **widoczny** → przycisk pokazuje **„Schowaj pasek"** → kliknięcie chowa pasek (animacja slide up, 250ms)
  - Gdy pasek sterowania jest **schowany** → przycisk pokazuje **„Wybierz"** → kliknięcie pokazuje pasek (animacja slide down, 250ms)
- Stan startowy: pasek sterowania widoczny, przycisk pokazuje „Schowaj pasek"
- W górnym pasku znajduje się też przycisk **„Holyrics"** (osobny, bez akcji — do przyszłego użycia)
- Cel: zwiększenie obszaru wyświetlania nut po wybraniu pieśni

### 5.5 Zoom i pan

- Zakres zoom: 1x – 5x (max 5-krotne powiększenie)
- Rendering przy zoom: bitmapy w rozdzielczości 2x rozdzielczości ekranu (kompromis jakość/pamięć)
- Zoom NIE jest resetowany przy zmianie pieśni
- Zoom jest resetowany do 1x przy zamknięciu aplikacji (nie jest persystowany)
- Pan (przesuwanie po powiększeniu): przy każdej zmianie pieśni widok wraca automatycznie do wyśrodkowania
- Pan ograniczony do obszaru strony (nie można przesunąć poza krawędź strony)
- Zoom i pan działają w obu orientacjach (landscape i portrait)
- Przeznaczenie: głównie redukcja marginesów PDF


---

## 6. LOGIKA WYBORU PIEŚNI

Po wpisaniu numeru N lub wybraniu z autocomplete:

**Jeśli pieśń istnieje:**
1. Wczytaj rekord z JSON
2. Ustaw jako aktualną pieśń
3. Załaduj strony z `pages`
4. Resetuj indeks stron do 0 (pierwsza strona/rozkładówka pieśni)
5. Wyśrodkuj widok (resetuj pan)

**Wyświetlanie:**
- `pages.size == 0` → Toast: „Brak strony w śpiewniku"
- `pages.size == 1` → jedna strona, **skalowana na cały dostępny obszar PDF** (pełna szerokość i wysokość)
- `pages.size == 2` → rozkładówka
- `pages.size > 2` → pokaż pierwsze 2 strony, pozostałe dostępne przez nawigację ◀▶

**Jeśli pieśń nie istnieje:**
- Toast: „Nie znaleziono pieśni o numerze N"


---

## 7. TRYBY NAWIGACJI

Aplikacja obsługuje 3 tryby nawigacji, przełączane przyciskiem w UI lub w ustawieniach.
Aktywny tryb zapamiętywany w SharedPreferences.

### 7.1 Spread
- krok = 2 strony
- np. `12–13` → `14–15`
- po ostatniej stronie pieśni → przechodzi do pierwszej strony następnej pieśni

### 7.2 Page
- krok = 1 strona
- np. `12–13` → `13–14`
- po ostatniej stronie pieśni → przechodzi do pierwszej strony następnej pieśni

### 7.3 Song
- skok do poprzedniej / następnej pieśni według `number` z JSON
- przechodzi na pierwszą stronę/rozkładówkę docelowej pieśni

### Zachowanie na granicach (dotyczy wszystkich trybów)
- Na **pierwszej pieśni** (lub pierwszej stronie pierwszej pieśni): przycisk ◀ jest wyszarzony i nieaktywny
- Na **ostatniej pieśni** (lub ostatniej stronie ostatniej pieśni): przycisk ▶ jest wyszarzony i nieaktywny

### Orientacja portrait
- Zawsze wyświetlana 1 strona (nigdy rozkładówka)
- Pojedyncza strona skalowana na **cały dostępny obszar PDF** (pełna szerokość ekranu)
- Tryb SPREAD w portrait: ▶ przechodzi do następnej pieśni (nie do kolejnej strony)
- Tryb PAGE w portrait: ▶ przechodzi stronę po stronie, po ostatniej → następna pieśń


---

## 8. MENU USTAWIEŃ

Dostępne z ikony ⚙️.

### 8.1 Tryb nawigacji
- Spread (domyślny)
- Page
- Song

Zmiana zapamiętywana w SharedPreferences.

### 8.2 Inne
- **Reset ostatniej pozycji** – resetuje `last_song_number` i `last_page_index`. Przy następnym starcie otwiera się pieśń nr 1. NIE resetuje trybu nawigacji.
- **Informacje o aplikacji** – nazwa, wersja

> **Uwaga:** Brak opcji wyboru orientacji w ustawieniach aplikacji.
> Orientacja jest w całości zarządzana przez ustawienia systemowe tabletu.


---

## 9. WYSZUKIWANIE PO TYTULE

- Pole `AutoCompleteTextView` / `Autocomplete` w Compose
- Umiejscowienie: osobne pole w pasku sterowania, obok pola numerycznego
- Podpowiedzi od 1 znaku (dynamiczne filtrowanie po `title`)
- Wyszukiwanie case-insensitive, obsługa polskich znaków
- Wybór wyniku → otwiera pieśń (jak wpisanie numeru)


---

## 10. PAMIĘĆ STANU

Aplikacja zapamiętuje w SharedPreferences:

| Klucz | Typ | Domyślnie |
|---|---|---|
| `last_song_number` | Int | 1 |
| `last_page_index` | Int | 0 |
| `nav_mode` | String (SPREAD/PAGE/SONG) | SPREAD |

Przy ponownym uruchomieniu:
- Automatyczny powrót do ostatniej pieśni i indeksu strony
- Zoom jest zawsze resetowany do 1x (nie jest persystowany)


---

## 11. PDF RENDERING I WYDAJNOŚĆ

### Silnik
- `PdfRenderer` (Android SDK, natywny, dostępny od API 21)

### Zasady renderowania
- Renderuj tylko aktualnie widoczne strony (1 lub 2)
- Bitmapy w rozdzielczości **2x** fizycznej rozdzielczości ekranu (dla jakości przy zoom)
- Tryb renderowania: `RENDER_MODE_FOR_DISPLAY`

### Cache
- LRU cache bitmap
- Rozmiar cache: 1/6 dostępnego RAM (~1,3 GB przy 8 GB RAM)
- Cache sąsiednich stron (prefetch: 1 strona przed i 1 za aktualnym widokiem)
- Klucz cache: `"pageIndex:widthxheight"` (inwalidacja przy zmianie rozmiaru)

### Zwalnianie zasobów
- Zamknięcie `PdfRenderer` przy przejściu aplikacji w tło
- Ponowne otwarcie przy wznowieniu


---

## 12. OBSŁUGA BŁĘDÓW

### Komunikaty dla użytkownika
Wszystkie komunikaty wyświetlane jako **Toast** (znikają automatycznie po ~2 sekundach).

| Sytuacja | Komunikat |
|---|---|
| Nieznany numer pieśni | „Nie znaleziono pieśni o numerze N" |
| Pieśń bez stron | „Brak strony w śpiewniku" |
| Numer strony poza zakresem PDF | „Błąd: strona N niedostępna" |

### Błędy krytyczne (ekran błędu)
- Brak `Spiewnik.pdf` → pełnoekranowy ekran błędu: „Brak pliku śpiewnika"
- Brak `songs.json` → pełnoekranowy ekran błędu: „Brak listy pieśni"
- Nieprawidłowy format JSON → pełnoekranowy ekran błędu + log do Logcat

### Zasady ogólne
- Brak crasha w żadnym przypadku
- Wszystkie wyjątki logowane do Logcat


---

## 13. STRUKTURA PROJEKTU

```
com.spiewnik.app/
├── data/
│   ├── model/
│   │   └── Song.kt               // data class: number, title, pages
│   └── repository/
│       └── SongRepository.kt     // ładowanie i parsowanie songs.json
│
├── pdf/
│   ├── PdfRendererManager.kt     // zarządzanie PdfRenderer, otwieranie/zamykanie
│   └── PdfPageCache.kt           // LRU cache bitmap
│
├── ui/
│   ├── main/
│   │   ├── MainActivity.kt
│   │   └── MainViewModel.kt
│   ├── components/               // komponenty Compose
│   └── settings/
│       └── SettingsScreen.kt
│
├── settings/
│   └── AppSettings.kt            // SharedPreferences wrapper
│
└── utils/
    └── PageConverter.kt          // konwersja stron: JSON 1-based → PDF 0-based
```


---

## 14. TESTY

### Framework
- JUnit 4
- Mockk (mockowanie)
- kotlinx-coroutines-test

### 14.1 Testy jednostkowe (src/test)

**PageConverter**
- `jsonPage 1 → pdfIndex 0`
- `jsonPage 700 → pdfIndex 699`

**SongRepository**
- poprawne parsowanie songs.json (number, title, pages)
- wyszukiwanie po numerze: istniejący, nieistniejący
- wyszukiwanie po tytule: dopasowanie, brak, case-insensitive, polskie znaki
- obsługa pustego `pages` (zwraca Song z pustą listą, nie crash)

**NavigationLogic**
- tryb SPREAD: next o 2 strony, prev o 2 strony
- tryb PAGE: next o 1 stronę, prev o 1 stronę
- tryb SONG: przejście do następnej/poprzedniej pieśni
- SPREAD: ostatnia strona pieśni → next przechodzi do następnej pieśni
- PAGE: ostatnia strona pieśni → next przechodzi do następnej pieśni
- granica: pierwsza strona pierwszej pieśni → prev = disabled
- granica: ostatnia strona ostatniej pieśni → next = disabled
- pieśń 3-stronicowa, tryb SPREAD: `[10,11]` → next → `[12]` solo
- portrait + SPREAD: ▶ przechodzi do następnej pieśni (nie strony)

### 14.2 Testy instrumentowane (src/androidTest) — nice-to-have
- Wpisanie numeru → wyświetlenie poprawnych stron
- Wybór z autocomplete → otwarcie pieśni
- Zmiana trybu nawigacji → persystencja po restarcie
- Reset pozycji → pieśń nr 1 po restarcie


---

## 15. LOGO I IKONA APLIKACJI

### Logo
Plik: `Logo.png` (skopiowany z assets do `res/drawable/logo.png`)
Zawartość: okrągła pieczęć z kluczem wiolinowym i nutami, napis „ŚPIEWNIK" (góra) i „KADS FOKSAL 8" (dół)
Tło logo: kremowe (~`#EEECEA`)

### Ikona aplikacji
- Adaptive icon (foreground: logo, background: `#EEECEA`)
- Koło z logo zajmuje ~60% powierzchni ikony — proporcja właściwa, nie wymaga powiększenia

### Splash screen
- Mechanizm: natywny Android 12+ Splash Screen API (dostępny od API 31, minSdk=28 — działa na urządzeniach z API 31+, na starszych degraduje się łagodnie)
- Tło: `#EEECEA` (spójne z tłem logo)
- Ikona: `logo.png` wyśrodkowane
- Czas wyświetlania: naturalny — trwa dopóki aplikacja nie zakończy ładowania (kopiowanie PDF do cacheDir + otwarcie PdfRenderer)
- `installSplashScreen()` wywoływane w `MainActivity.onCreate()` przed `super.onCreate()`


---

## 16. SZATA GRAFICZNA (DARK MODE – INSPIROWANA VS CODE)

### Ogólny styl
- Dark mode jako jedyny tryb
- Wysoki kontrast
- Minimalistyczny, techniczny
- Brak zbędnych animacji

### Kolory

| Element | Kolor |
|---|---|
| Tło główne | `#1E1E1E` |
| Panel sterowania | `#252526` |
| Akcent | `#007ACC` |
| Tekst główny | `#D4D4D4` |
| Tekst drugorzędny | `#9CDCFE` |
| Błędy | `#F44747` |
| Przycisk nieaktywny (disabled) | `#555555` |

### UI
- Duże przyciski ◀ ▶ (min. 64dp wysokości, obsługa w rękawiczkach)
- Wyraźne stany focus i pressed
- Pola tekstowe z wyraźną ramką w kolorze akcentu przy aktywności


---

## 17. WYJŚCIE OCZEKIWANE OD LLM

LLM powinien wygenerować:

1. Kompletną aplikację Android (Kotlin) gotową do zbudowania w Android Studio
2. Przykładowy `songs.json` z 5 rekordami do testów
3. Instrukcję:
   - gdzie umieścić `Spiewnik.pdf` i `songs.json`
   - jak uruchomić projekt w Android Studio
   - jak wgrać na tablet
4. Opis architektury
5. Uzasadnienie kluczowych decyzji technicznych
