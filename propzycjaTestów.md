

# 🎯 Najważniejsza zasada

👉 **Nie testujesz UI piksel po pikselu**  
👉 testujesz:

* logikę ✅
* integrację ✅
* krytyczne scenariusze ✅

***

# 🧠 Podział testów (dla Twojego projektu)

## 1. ✅ **Testy logiki (NAJWAŻNIEJSZE)**

To powinno być **80% wszystkich testów**

### Co testować:

#### 🔹 piesni.json

* poprawne wczytanie
* mapowanie:
  * numer → strona
  * tytuł → numer

#### 🔹 logika nawigacji

* przejście do:
  * następnej strony
  * poprzedniej
* granice (pierwsza / ostatnia)
* rozkładówka (left/right strony)

#### 🔹 PageConverter / mapowanie PDF

* numer pieśni → właściwe strony
* edge case’y:
  * brak pieśni
  * błędne dane

***

✅ to są testy:

* szybkie
* identyczne dla Android + Windows

👉 najlepiej wrzucić je do `core/`  
👉 odpalać raz → działa wszędzie

***

## 2. ✅ **Testy Holyrics (integracja)**

### Co testować:

#### 🔹 parsing odpowiedzi API

* `GetLyricsPlaylist`
* `GetCurrentPresentation`

#### 🔹 przypadki:

* ✅ poprawna odpowiedź
* ❌ brak połączenia
* ❌ timeout
* ❌ zły JSON

***

💡 ważne:
👉 NIE testuj prawdziwego Holyrics

👉 tylko:

* mock JSON
* stub HTTP

***

✅ przykład:

```
given(jsonResponse)
→ parse()
→ assert(songTitle == "Amazing Grace")
```

***

## 3. ✅ **Testy pracy offline / sieci**

Twój case to:
👉 LAN + IP

### testy:

* brak internetu
* złe IP
* host nie odpowiada
* zmiana IP

👉 sprawdzasz:

* czy appka nie crashuje ✅
* czy pokazuje błąd ✅

***

## 4. ✅ **Testy PDF (ważne, ale sprytne)**

Nie testuj renderingu jako obrazu ❌

### Testuj:

* czy:
  * strona X się wczytuje
  * cache działa
  * nie wywala błędu

### np.:

```
loadPage(123)
→ assert(not null)
```

***

✅ dodatkowo:

* cache LRU:
  * dodajesz 100 stron
  * sprawdzasz, czy stare znikają

***

## 5. ✅ **Testy UI (minimalne, ale sensowne)**

### Android:

* Espresso / Compose UI test

### Windows (Compose):

* Compose UI testing (podstawowe)

***

Testuj tylko:

✅ kliknięcie:

* "następna strona"
* "poprzednia"

✅ wpisanie numeru (jeśli masz numpad)
✅ wybór z listy

***

❌ NIE testuj:

* layoutu
* animacji
* styli

***

# 🧩 Co jest wspólne dla Android + Windows

👉 prawie wszystko poza UI

Dlatego:

```
core/
  ├── test/
```

zawiera:

* logika
* JSON
* Holyrics parsing

***

# 📁 Proponowana struktura testów

```
core/
  ├── src/
  ├── test/
       ├── SongRepositoryTest
       ├── PageConverterTest
       ├── NavigationTest
       ├── HolyricsParserTest
       ├── CacheTest

android/
  ├── androidTest/
       ├── basic navigation tests

windows/
  ├── test/
       ├── basic UI tests
```

***

# 🔥 Najważniejsze scenariusze (must-have)

Jeśli miałbym wybrać MINIMUM:

## ✅ 1. “otwieram pieśń”

```
input: numer
→ poprawna strona PDF
```

## ✅ 2. “next / prev”

```
page 10 → next → 11
page 1 → prev → stays 1
```

## ✅ 3. “Holyrics auto-follow”

```
mock API → zmiana pieśni
→ appka pokazuje nową stronę
```

## ✅ 4. “brak połączenia”

```
API down
→ appka działa dalej
→ pokazuje status
```

***

# ⚡ BONUS: coś, co naprawdę robi różnicę

## ✅ snapshot test JSON

Masz `piesni.json` → ważny plik

👉 dodaj test:

* czy nie zmieniła się struktura

***

# 🧠 Największa pułapka

👉 nie rób za dużo testów UI

To najczęstszy błąd:

* wolne
* kruche
* mało wartości

***

# ✅ TL;DR

👉 testuj głównie:

### ✅ core (najważniejsze)

* mapowanie pieśni
* nawigację
* parsing JSON

### ✅ integrację

* Holyrics (mock)

### ✅ odporność

* brak sieci
* błędy

### ✅ lekko UI

* tylko kluczowe kliknięcia

***

# 🚀 Jedna mocna rada na koniec

👉 jeśli dobrze wydzielisz `core/`,  
to:

* testy piszesz raz ✅
* działają na Android i Windows ✅

