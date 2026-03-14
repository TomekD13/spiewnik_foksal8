Zbuduj aplikację na Androida dla tabletu w Kotlin.
Aplikacja ma działać w orientacji poziomej (landscape) i być zoptymalizowana pod tablet.

CEL:
Aplikacja służy organiście do szybkiego otwierania nut ze śpiewnika w PDF.
Użytkownik wpisuje numer pieśni, a aplikacja wyświetla odpowiadające jej strony PDF, najlepiej jako rozkładówkę (2 strony obok siebie). Czasme pieś, może mieć jedną stronę, czasem 2. Będzie to zdefiniowane w pliku JSON.

ŹRÓDŁA DANYCH:
- Plik PDF: Spiewnik.pdf
  - lokalizacja: app/src/main/assets/spiewnik.pdf 
  - nazwa pliku: Spiewnik.pdf
- Plik mapowania JSON: 
  - lokalizacja: app/src/main/assets/songs.json
  - kodowanie UTF-8
  - schemat danych: każdy rekord opisuje jedną pieśń:
    {
      "number": (int) numer pieśni,
      "title": (string) tytuł,
      "pages": (array<int>) lista stron w PDF powiązanych z pieśnią
    }

ZAŁOŻENIA DOT. NUMERACJI STRON:
- Strony w JSON są liczone od 1 = pierwsza strona ma numer 1.
- Biblioteka PDF może używać innej numeracji — aplikacja ma wykonać poprawną konwersję.

WYMAGANIA FUNKCJONALNE (MVP):
1) Ekran główny (landscape):
   - pole do wpisania numeru pieśni (tylko cyfry, klawiatura numeryczna)
   - przycisk „Idź” + obsługa Enter
   - obszar podglądu PDF: dwie strony obok siebie (lewa i prawa) Jeśli pieśń ma tylko jedną stronę, pokaż tylko jedną stronę. 
   - przyciski nawigacji: strzałka w lewo i strzałka w prawo

2) Logika wyboru pieśni:
   - po wpisaniu numeru N:
     - jeśli rekord istnieje w JSON:
       - ustaw widok na strony podane w pages
       - jeśli pages ma 2 elementy: pokaż je jako lewa/prawa
       - jeśli pages ma 1 element: pokaż tylko lewą, prawa pusta/placeholder
       - jeśli pages ma więcej: pokaż pierwsze 2 jako rozkładówkę, a kolejne dostępne przez nawigację (patrz punkt 3) albo przewijanie
     - jeśli rekord nie istnieje:
       - pokaż komunikat: „Nie znaleziono pieśni o numerze N”

3) Nawigacja strzałkami:
   - tryb nawigacji: 
     - "spread" = poruszanie rozkładówką (np. 12–13 -> 14–15), krok = 2 strony
     - "page"  = poruszanie o 1 stronę (12–13 -> 13–14), krok = 1 strona
     - "prev song", "next song" poruszanie pieśniami

4) Pasek informacji (na górze):
   - aktualny numer pieśni i tytuł (jeśli wybrana)
   - aktualne strony (np. „12–13”)

WYMAGANIA UX / TABLET:
- Aplikacja ma działać offline.
- Tryb pełnoekranowy: tak.
- Zapobiegaj wygaszaniu ekranu podczas użycia: tak
- Skalowanie: tak powinno być możliwe
- Dostępność: czytelne duże przyciski, obsługa w rękawiczkach opcjonalnie: tak.

PDF RENDERING / WYDAJNOŚĆ:
- Silnik PDF: może być PdfRenderer chyba, że masz lepszy pomysł
- Renderuj tylko aktualnie widoczne strony.
- Cache: zaproponuj najlepsze rozwiązanie

OBSŁUGA BŁĘDÓW:
- brak PDF lub brak JSON: pokaż ekran błędu z informacją co brakuje
- niepoprawny JSON: pokaż czytelny komunikat + log
- numer strony poza zakresem PDF: komunikat + bez crasha

STRUKTURA PROJEKTU:
- oddziel logikę mapowania (repozytorium) od UI
- dodaj przykładowe dane JSON w assets oraz minimalny zestaw testów

DODATKI
- zapamiętaj ostatnio otwartą pieśń i strony
- wyszukiwanie po tytule: tak (dynamicznie podpowiadaj)

WYJŚCIE LLM:
- wygeneruj kompletny kod aplikacji + instrukcję uruchomienia
- podaj format songs.json i przykładowy plik
- opisz gdzie umieścić PDF/JSON w projekcie