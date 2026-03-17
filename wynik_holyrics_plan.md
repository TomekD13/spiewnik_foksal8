# Plan implementacji — integracja z Holyrics

## Źródło wymagań
`Wymagania_holyrics.md`

## Stan na dziś (przed implementacją)
- Aplikacja działa na branchu `feature/rebuild-v2`
- Istniejące ustawienia: `AppSettings.kt` (SharedPreferences), `SettingsFragment.kt` (DialogFragment), `fragment_settings.xml`
- Brak uprawnień sieciowych w manifeście
- Brak integracji z Holyrics

---

## Wymagania (po aktualizacji Wymagania_holyrics.md)

1. Numery pieśni identyczne w apce i Holyrics
2. Tablet i laptop na tej samej sieci WiFi
3. Aplikacja pobiera z Holyrics **tylko numery** pieśni z sekcji `Lyrics` (Holyrics przechowuje je jako sam numer: `"3"`, `"150"`)
4. Przycisk `HOLYRICS` → lista pieśni z playlisty → klik → otwiera pieśń w PDF
5. Parametry połączenia (IP, token) umieszczone w **istniejących ustawieniach** aplikacji

---

## API Holyrics

| Parametr | Wartość |
|----------|---------|
| Protokół | HTTP (nie HTTPS) |
| Port | 8091 |
| Metoda | POST |
| Endpoint | `http://[ip]:8091/api/GetLyricsPlaylist?token=[token]` |
| Token | Pobierany z Holyrics: `Narzędzia → API → token` |

### Odpowiedź API
```json
{
  "data": [
    {"id": "abc", "type": "song", "name": "3"},
    {"id": "def", "type": "song", "name": "150"},
    {"id": "ghi", "type": "song", "name": "480"}
  ]
}
```
Pole `name` zawiera **sam numer** pieśni jako string. Parsowanie: `name.toIntOrNull()`.

---

## Pliki do zmiany / stworzenia

### 1. `AndroidManifest.xml` — ZMIANA
Dodanie uprawnień i konfiguracji sieci:
```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```
Dodanie do `<application>`:
```xml
android:networkSecurityConfig="@xml/network_security_config"
```

### 2. `res/xml/network_security_config.xml` — NOWY
Zezwolenie na HTTP dla sieci lokalnej (adresy 192.168.x.x, 10.x.x.x, 172.16.x.x):
```xml
<network-security-config>
    <domain-config cleartextTrafficPermitted="true">
        <domain includeSubdomains="true">192.168.0.0</domain>
        <!-- lub wildcard dla całej sieci lokalnej -->
    </domain-config>
    <base-config cleartextTrafficPermitted="false" />
</network-security-config>
```
Ponieważ IP jest dynamiczne, użyjemy `<base-config cleartextTrafficPermitted="true" />` — wystarczy dla aplikacji używanej wyłącznie w sieci lokalnej.

### 3. `settings/AppSettings.kt` — ZMIANA
Dwa nowe pola:
```kotlin
var holyricsIp: String       // domyślnie ""
var holyricsToken: String    // domyślnie ""
```

### 4. `holyrics/HolyricsRepository.kt` — NOWY
```
package com.spiewnik.app.holyrics
```
- Metoda: `suspend fun fetchPlaylist(ip: String, token: String): Result<List<Int>>`
- Używa `HttpURLConnection` (bez dodatkowej biblioteki)
- Timeout połączenia: 3000 ms, timeout odczytu: 3000 ms
- Parsuje JSON → filtruje `type == "song"` → `name.toIntOrNull()` → lista Int
- Zwraca `Result.failure` przy błędzie sieci/timeout/złym JSON

### 5. `res/layout/fragment_settings.xml` — ZMIANA
Dodanie nowej sekcji **Holyrics** przed przyciskiem Zamknij:
```
── Divider ──
[Label] "Holyrics"
[Label] "Adres IP laptopa"
[EditText] etHolyricsIp  (inputType=text, hint="np. 192.168.1.100")
[Label] "Token API"
[EditText] etHolyricsToken  (inputType=textPassword)
[Button] btnSaveHolyrics  "Zapisz"
── Divider ──
```

### 6. `ui/settings/SettingsFragment.kt` — ZMIANA
- Wypełnienie pól IP i Token z `viewModel.settings`
- Obsługa `btnSaveHolyrics.setOnClickListener` → zapis do `settings.holyricsIp` / `settings.holyricsToken`

### 7. `SongViewModel.kt` — ZMIANA
Nowe elementy:
```kotlin
val holyricsRepository = HolyricsRepository()

private val _holyricsPlaylist = MutableLiveData<List<Int>>(emptyList())
val holyricsPlaylist: LiveData<List<Int>> = _holyricsPlaylist

fun fetchHolyricsPlaylist() {
    viewModelScope.launch(Dispatchers.IO) {
        val ip = settings.holyricsIp
        val token = settings.holyricsToken
        if (ip.isBlank() || token.isBlank()) {
            _toastEvent.postValue("Uzupełnij IP i token Holyrics w ustawieniach")
            return@launch
        }
        val result = holyricsRepository.fetchPlaylist(ip, token)
        result.onSuccess { numbers ->
            if (numbers.isEmpty()) {
                _toastEvent.postValue("Playlista Holyrics jest pusta")
            } else {
                _holyricsPlaylist.postValue(numbers)
            }
        }.onFailure {
            _toastEvent.postValue("Holyrics niedostępny")
        }
    }
}
```

### 8. `activity_main.xml` — ZMIANA
Dodanie przycisku `HOLYRICS` w wierszu wejściowym (obok btnNavMode):
```xml
<Button
    android:id="@+id/btnHolyrics"
    android:layout_width="wrap_content"
    android:layout_height="match_parent"
    android:layout_marginStart="8dp"
    android:text="@string/btn_holyrics"
    android:textSize="14sp"
    app:backgroundTint="@color/holyrics_button" />
```
Nowy kolor `holyrics_button` = `#6A0DAD` (fiolet, kojarzący się z kościołem/liturgią).

### 9. `ui/HolyricsBottomSheet.kt` — NOWY
```
package com.spiewnik.app.ui
```
- Rozszerza `BottomSheetDialogFragment`
- Obserwuje `viewModel.holyricsPlaylist`
- Dla każdego numeru: szuka tytułu w `viewModel.repository` → tworzy `Button`
- Format przycisku: `"3. Jezu drogi, dzięki Twe"`
- Klik → `viewModel.openSong(number)` → `dismiss()`
- Ciemny styl zgodny z VS Code palette

### 10. `res/layout/fragment_holyrics.xml` — NOWY
```xml
<BottomSheetBehavior>
  <LinearLayout vertical>
    <TextView "Playlista Holyrics" />
    <HorizontalScrollView>          ← przewijanie poziome jeśli dużo pieśni
      <LinearLayout horizontal>
        <!-- przyciski generowane dynamicznie -->
      </LinearLayout>
    </HorizontalScrollView>
    <Button btnClose />
  </LinearLayout>
</BottomSheetBehavior>
```

### 11. `MainActivity.kt` — ZMIANA
```kotlin
binding.btnHolyrics.setOnClickListener {
    viewModel.fetchHolyricsPlaylist()
}
viewModel.holyricsPlaylist.observe(this) { playlist ->
    if (playlist.isNotEmpty()) {
        HolyricsBottomSheet.show(supportFragmentManager)
    }
}
```

### 12. `res/values/strings.xml` — ZMIANA
Nowe stringi:
```xml
<string name="btn_holyrics">Holyrics</string>
<string name="holyrics_title">Playlista Holyrics</string>
<string name="holyrics_ip_label">Adres IP laptopa</string>
<string name="holyrics_token_label">Token API</string>
<string name="holyrics_ip_hint">np. 192.168.1.100</string>
<string name="holyrics_save">Zapisz</string>
```

---

## Stany błędów

| Sytuacja | Komunikat Toast |
|----------|----------------|
| Puste IP lub token | "Uzupełnij IP i token Holyrics w ustawieniach" |
| Timeout / brak połączenia | "Holyrics niedostępny" |
| Pusta playlista | "Playlista Holyrics jest pusta" |
| Numer z Holyrics nie istnieje w piesni.json | pomijamy (bez komunikatu) |

---

## Kolejność implementacji

1. `network_security_config.xml` + `AndroidManifest.xml`
2. `AppSettings.kt` — nowe pola
3. `HolyricsRepository.kt` — logika HTTP
4. `SongViewModel.kt` — `fetchHolyricsPlaylist()`
5. `fragment_settings.xml` + `SettingsFragment.kt` — pola IP/token
6. `activity_main.xml` — przycisk HOLYRICS + kolor
7. `fragment_holyrics.xml` + `HolyricsBottomSheet.kt`
8. `MainActivity.kt` — obserwacja + pokazanie BottomSheet
9. `strings.xml` — nowe stringi

---

## Co NIE jest w zakresie

- Synchronizacja na żywo (auto-odświeżanie playlisty co X sekund) — poza zakresem
- Sterowanie Holyrics ze śpiewnika (np. zmiana slajdu) — poza zakresem
- Obsługa innych typów niż `type == "song"` w playliście — pomijane

---

*Wygenerowano: 2026-03-17*
