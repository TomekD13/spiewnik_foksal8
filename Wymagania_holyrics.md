Chciałbym teraz przeanalizować możliwość pobierania z Holyrics nr pieśni do aplikacji.

Założenia:
1. Nr pieśni i tytuły są takie same w aplikacji jak i w Holyrics
2. Apk jak i Holyrics działają w tej samej sieci wifi

Co jest do zrobienia:
Aplikacja powinna pobierać z Holyrics nr pieśni, które ma w sekcji lyrics wybrane do wyświetlania (sam nr).
Użytkownik po wciśnięciu przycisku Holyrics rozwija listę wszystkich pieśni jaki są w playlist holyrics. Po klkinięciu w jakąś pieśń APK ustawia pdf na odpowiedniej stronie tego nr pieśni.
Wykorzystaj ustawienia istniejące w aplikacji do tego by umieścić tam parametry potrzebne do połączenia się z Holyrics.

## Uwagi implementacyjne

- Przed dodaniem przycisku `btnHolyrics` do layoutu zawsze sprawdź najpierw, czy taki ID już istnieje w pliku `activity_main.xml`. Duplikat tego samego ID w jednym layoucie powoduje błąd kompilacji (`databinding: conflicts with another tag that has the same ID`).

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