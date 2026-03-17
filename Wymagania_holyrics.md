Chciałbym teraz przeanalizować możliwość pobierania z Holyrics nr pieśni do aplikacji.

Założenia:
1. Nr pieśni i tytuły są takie same w aplikacji jak i w Holyrics
2. Apk jak i Holyrics działają w tej samej sieci wifi

Co jest do zrobienia:
Aplikacja powinna pobierać z Holyrics nr pieśni, które ma w sekcji lyrics wybrane do wyświetlania (sam nr).
Użytkownik po wciśnięciu przycisku Holyrics rozwija listę wszystkich pieśni jaki są w playlist holyrics. Po klkinięciu w jakąś pieśń APK ustawia pdf na odpowiedniej stronie tego nr pieśni.
Wykorzystaj ustawienia istniejące w aplikacji do tego by umieścić tam parametry potrzebne do połączenia się z Holyrics.

## Zaimplementowane rozwiązanie (finalne)

### UI: PopupWindow pod przyciskiem HOLYRICS
- Zamiast `BottomSheetDialogFragment` użyto `PopupWindow` zakotwiczonego pod przyciskiem `btnHolyrics`
- Popup wyrównany do prawej krawędzi przycisku, szerokość = 1/3 ekranu, max wysokość = 65% ekranu
- Pieśni wyświetlane **w pionie** z paskiem przewijania (`ScrollView`) — lista może być dowolnie długa
- Ponowne kliknięcie HOLYRICS gdy popup jest otwarty → zamknięcie popupu
- Kluczowe parametry `PopupWindow`: `isFocusable = true`, `isOutsideTouchable = true` (zamknięcie przez kliknięcie poza)

### Przepływ danych przy kliknięciu HOLYRICS
1. Popup otwiera się natychmiast (ewentualnie z danymi z poprzedniego fetcha)
2. Równolegle startuje `viewModel.fetchHolyricsPlaylist()` (żądanie HTTP na wątku IO)
3. Po odpowiedzi: `_holyricsPlaylist.postValue(numbers)`
4. Observer w `MainActivity` aktualizuje popup jeśli jest widoczny
5. Ponowne kliknięcie przycisku odświeża dane — fetch wywoływany za każdym razem

### Architektura LiveData dla popupu
- `holyricsPlaylist: LiveData<List<Int>>` obserwowany w `MainActivity`
- Popup tworzony i zarządzany bezpośrednio w `MainActivity` (nie Fragment — brak problemów z lifecycle)
- Przy otwarciu popupu: natychmiastowe wypełnienie z `viewModel.holyricsPlaylist.value` (cache), potem aktualizacja gdy przyjdzie nowy fetch

## Uwagi implementacyjne

### Błąd: duplikat ID w layoucie
- Przed dodaniem przycisku `btnHolyrics` do layoutu zawsze sprawdź najpierw, czy taki ID już istnieje w pliku `activity_main.xml`. Duplikat tego samego ID w jednym layoucie powoduje błąd kompilacji (`databinding: conflicts with another tag that has the same ID`).

### Błąd: BottomSheet pokazuje tylko nagłówek (przyciski niewidoczne)
- `BottomSheetDialogFragment` domyślnie otwiera się w stanie *collapsed* (zwinięty do peek height). Treść poniżej nagłówka jest ukryta — użytkownik widzi pusty sheet.
- Symptom mylący: logcat pokazywał `populateButtons: numbers=[5, 6, 7]` (dane były poprawne), ale przyciski wydawały się niewidoczne. Debugging danych to ślepy zaułek — problem był wyłącznie w zachowaniu BottomSheetBehavior.
- **Ostateczne rozwiązanie:** porzucono `BottomSheetDialogFragment` na rzecz `PopupWindow` — problem z collapsed state przestał istnieć.

### Błąd: LiveData timing w BottomSheetDialogFragment (rozwiązany przez zmianę architektury)
- Fragment może subskrybować LiveData po tym, gdy wartość już dotarła, albo dostać `emptyList()` z inicjalizacji zanim przyjdą dane.
- Pośrednie rozwiązanie: przekazywanie numerów przez `Bundle` (`putIntArray`) przed `show()`.
- **Ostateczne rozwiązanie:** przeniesienie całego zarządzania popupem do `MainActivity` — LiveData obserwowana tam, popup aktualizowany bezpośrednio. Żadnego Fragment lifecycle do zarządzania.

### Błąd: lista pieśni nie odświeżała się przy kolejnych kliknięciach
- `findFragmentByTag(TAG) != null` blokował tworzenie nowego fragmentu z odświeżonymi danymi.
- **Rozwiązanie:** `PopupWindow` — przy każdym kliknięciu fetch jest wywołany na nowo, observer aktualizuje widoczny popup gdy dane dotrą.

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