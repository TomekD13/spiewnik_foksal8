Chciałbym teraz przeanalizować możliwość pobierania z Holyrics nr pieśni do aplikacji.

Założenia:
1. Nr pieśni i tytuły są takie same w aplikacji jak i w Holyrics
2. Apk jak i Holyrics działają w tej samej sieci wifi

Co jest do zrobienia:
Aplikacja powinna pobierać z Holyrics nr pieśni, które ma w sekcji lyrics wybrane do wyświetlania (sam nr).
Użytkownik po wciśnięciu przycisku Holyrics rozwija listę wszystkich pieśni jaki są w playlist holyrics. Po klkinięciu w jakąś pieśń APK ustawia pdf na odpowiedniej stronie tego nr pieśni.
Wykorzystaj ustawienia istniejące w aplikacji do tego by umieścić tam parametry potrzebne do połączenia się z Holyrics.

## Uwagi implementacyjne

### Błąd: duplikat ID w layoucie
- Przed dodaniem przycisku `btnHolyrics` do layoutu zawsze sprawdź najpierw, czy taki ID już istnieje w pliku `activity_main.xml`. Duplikat tego samego ID w jednym layoucie powoduje błąd kompilacji (`databinding: conflicts with another tag that has the same ID`).

### Błąd: BottomSheet pokazuje tylko nagłówek (przyciski niewidoczne)
- `BottomSheetDialogFragment` domyślnie otwiera się w stanie *collapsed* (zwinięty do peek height). Treść poniżej nagłówka jest ukryta — użytkownik widzi pusty sheet.
- **Rozwiązanie:** w `onViewCreated` wymusić `STATE_EXPANDED`:
  ```kotlin
  (dialog as? BottomSheetDialog)?.behavior?.apply {
      state = BottomSheetBehavior.STATE_EXPANDED
      skipCollapsed = true
  }
  ```
- `skipCollapsed = true` zapobiega zatrzymaniu się sheetu w stanie pośrednim przy przeciąganiu w dół — od razu się zamyka.
- Symptom mylący: logcat pokazywał `populateButtons: numbers=[5, 6, 7]` (dane były poprawne), ale przyciski wydawały się niewidoczne. Debugging danych to ślepy zaułek — problem był wyłącznie w zachowaniu BottomSheetBehavior.

### Błąd: LiveData timing w BottomSheetDialogFragment
- Nie obserwuj `holyricsPlaylist` LiveData wewnątrz BottomSheet — fragment może subskrybować się po tym, gdy LiveData już dostarczyła wartość, ale może też dostać wartość `emptyList()` z inicjalizacji, zanim przyjdą prawdziwe dane.
- **Rozwiązanie:** przekazuj numery przez `Bundle` jako argument fragmentu (`putIntArray`) przed wywołaniem `show()`. Odczyt w `onViewCreated` z `arguments?.getIntArray(ARG_NUMBERS)`.
- Obserwatorem `holyricsPlaylist` jest `MainActivity` — ona wywołuje `HolyricsBottomSheet.show(fragmentManager, playlist)` dopiero gdy lista jest niepusta.

## Konfiguracja Holyrics (zweryfikowana)

### Gdzie znaleźć token API
Token nie jest generowany automatycznie — należy go pobrać z sekcji **Tools → Several → Receivers → API/Script**, pole **ID**.

### Uprawnienia API
W ustawieniach Holyrics, sekcja Local, należy włączyć co najmniej:
- `GetLyricsPlaylist` — pobieranie playlisty pieśni

### Rzeczywista struktura odpowiedzi API
Endpoint: `POST http://[ip]:8091/api/GetLyricsPlaylist?token=[token]`

```json
{
  "status": "ok",
  "data": [
    {
      "id": "1651907678984",
      "title": "5",
      "artist": "",
      "author": ""
    }
  ]
}
```

Kluczowe uwagi:
- Pole z numerem pieśni to **`title`** (nie `name` jak w dokumentacji społecznościowej)
- **Brak pola `type`** — nie filtrujemy po typie
- Odpowiedź zawsze HTTP 200 — błędy sygnalizowane są przez `"status": "error"` w JSON
- Przy błędnym tokenie: `{"status": "error", "error": "invalid token"}`
- Bez tokenu: `{}` z błędem HTTP