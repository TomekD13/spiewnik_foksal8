# Dodatkowe uwagi — pułapki i wskazówki

## Fragment KTX — activityViewModels / viewModels

**Problem:** `Unresolved reference: activityViewModels` w klasach dziedziczących po `DialogFragment` / `Fragment`.

**Przyczyna:** Funkcje rozszerzające `activityViewModels()` i `viewModels()` nie pochodzą z `appcompat` ani `lifecycle-viewmodel-ktx` — wymagają osobnej zależności `fragment-ktx`.

**Rozwiązanie:** Zawsze gdy tworzysz nowy Fragment/DialogFragment, upewnij się że w `build.gradle.kts` jest:
```kotlin
implementation(libs.fragment.ktx)
```
i w `libs.versions.toml`:
```toml
fragment-ktx = { group = "androidx.fragment", name = "fragment-ktx", version = "1.8.1" }
```

---

## PdfRenderer — otwieranie pliku z assets

**Problem:** `PdfRenderer` nie może otworzyć PDF bezpośrednio z assets — pojawia się komunikat "Brak śpiewnika".

**Przyczyna:** `PdfRenderer` wymaga seekowalnego `ParcelFileDescriptor` zaczynającego się od bajtu 0. Pliki w APK mają niezerowy offset, więc `openFd()` + `dup()` zwraca deskryptor całego APK, nie samego PDF.

**Rozwiązanie:** Przed otwarciem `PdfRenderer` zawsze kopiuj PDF do `context.cacheDir`:
```kotlin
val cacheFile = File(context.cacheDir, "Spiewnik.pdf")
if (!cacheFile.exists()) {
    context.assets.open("Spiewnik.pdf").use { it.copyTo(cacheFile.outputStream()) }
}
val pfd = ParcelFileDescriptor.open(cacheFile, ParcelFileDescriptor.MODE_READ_ONLY)
val renderer = PdfRenderer(pfd)
```
Kopia tworzona jest tylko raz (sprawdzenie `exists()`). Plik cache jest automatycznie czyszczony przez system gdy brakuje miejsca.

---

## isActive wymaga jawnego importu w coroutine

**Problem:** `Unresolved reference: isActive` wewnątrz bloku `lifecycleScope.launch { }`.

**Przyczyna:** `isActive` to właściwość rozszerzająca `CoroutineScope` z pakietu `kotlinx.coroutines` — wymaga jawnego importu, kompilator jej nie znajdzie automatycznie.

**Rozwiązanie:** Dodaj import:
```kotlin
import kotlinx.coroutines.isActive
```

---

## Migotanie ekranu przy renderowaniu PDF

**Problem:** Ekran migota czarnym przy zmianie strony lub szybkiej nawigacji.

**Przyczyna 1 — emulator:** Emulator Androida potrzebuje dużo RAM (4–8 GB wolnych). Na maszynie z ograniczoną pamięcią renderowanie bitmap w 2× rozdzielczości powoduje zacinanie i flashowanie. Na fizycznym tablecie z 8 GB RAM problemu nie ma.

**Przyczyna 2 — równoległe joby renderowania:** Przy szybkiej nawigacji każde kliknięcie ▶ uruchamia nową coroutine renderującą. Jeśli poprzednia jeszcze nie skończyła, obie działają jednocześnie i nadpisują bitmapę w losowej kolejności — efekt: migotanie i wyświetlanie złej strony.

**Rozwiązanie:** Przed każdym nowym renderowaniem anuluj poprzedni job:
```kotlin
private var renderJob: Job? = null

// w renderPages():
renderJob?.cancel()
renderJob = lifecycleScope.launch {
    val bmp = withContext(Dispatchers.IO) { pdfCache.renderPage(...) }
    if (!isActive) return@launch  // sprawdź czy job nadal aktywny
    imageView.setImageBitmap(bmp)
}
```

---

## Splash screen — installSplashScreen() musi być przed super.onCreate()

**Problem:** Splash screen nie pojawia się lub aplikacja crashuje przy starcie.

**Przyczyna:** `installSplashScreen()` musi być wywołane **przed** `super.onCreate()`, inaczej system nie może poprawnie zainicjować splash screen window.

**Rozwiązanie:**
```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    installSplashScreen()   // ← PRZED super
    super.onCreate(savedInstanceState)
    ...
}
```
Wymaga zależności `implementation(libs.core.splashscreen)` oraz atrybutów w themes.xml:
```xml
<item name="android:windowSplashScreenBackground">#EEECEA</item>
<item name="android:windowSplashScreenAnimatedIcon">@drawable/logo</item>
```

---

## Ikona aplikacji — logo z białym tłem na ciemnym tle

**Problem:** Logo ma jasne tło — przy ikonie aplikacji na ciemnym launche rze widać biały kwadrat/prostokąt zamiast przezroczystego tła.

**Przyczyna:** PNG logo nie ma kanału alfa (przezroczystości) — tło jest kremowe (`#EEECEA`).

**Rozwiązanie zastosowane:** Ustawiono tło adaptive ikony na ten sam kolor kremowy (`#EEECEA`), przez co logo wygląda naturalnie. Ikona ma spójny wygląd.

**Rozwiązanie docelowe (lepsza jakość):** Przygotować wersję logo z przezroczystym tłem (PNG z kanałem alfa) i użyć jej jako foreground adaptive ikony — wtedy launcher może swobodnie dobierać kształt ikony (kółko, zaokrąglony kwadrat itp.).

---

## setOnTouchListener wymaga performClick (lint + dostępność)

**Problem:** Android Lint ostrzega: `Custom view has setOnTouchListener called on it but does not override performClick`.

**Przyczyna:** Widoki z `setOnTouchListener` powinny wywoływać `performClick()` przy `ACTION_UP`, żeby obsługiwać zdarzenia dostępności (TalkBack, Switch Access).

**Rozwiązanie:** W lambdzie `setOnTouchListener` wywołaj `v.performClick()` przy zwolnieniu palca:
```kotlin
view.setOnTouchListener { v, event ->
    // … obsługa gestów …
    if (event.action == MotionEvent.ACTION_UP) v.performClick()
    true
}
```

---

## setText z konkatenacją — użyj zasobu z placeholderami

**Problem:** Lint ostrzega: `Do not concatenate text displayed with setText`.

**Przyczyna:** Sklejanie tekstu przez interpolację Kotlin (`"#${nr}  ${tytul}"`) utrudnia tłumaczenia i jest flagowane przez lint.

**Rozwiązanie:** Dodaj zasób z placeholderami w `strings.xml`:
```xml
<string name="song_info_format">#%1$d  %2$s</string>
```
i użyj `getString()` w kodzie:
```kotlin
binding.tvSongInfo.text = getString(R.string.song_info_format, song.number, song.title)
```

---

## Komentarze w kodzie muszą być po angielsku

**Problem:** Lint zgłasza `Typo: In word 'możliwy'` (lub inne polskie słowa) w plikach `.kt`.

**Przyczyna:** Android Lint sprawdza pisownię we wszystkich komentarzach używając angielskiego słownika — polskie słowa są flagowane jako literówki.

**Rozwiązanie:** Wszystkie komentarze w plikach Kotlin pisz po angielsku.

---

## Instalacja APK debug na emulatorze/urządzeniu — flaga -t

**Problem:** `adb install` kończy się błędem `INSTALL_FAILED_TEST_ONLY`.

**Przyczyna:** APK zbudowane w trybie debug mają w manifeście flagę `android:testOnly="true"`, którą Android blokuje przy normalnej instalacji.

**Rozwiązanie:** Dodaj flagę `-t` do komendy install:
```
adb -s localhost:5555 install -t spiewnik.apk
```

---

## Crash na Android < 13 — OnBackAnimationCallback

**Problem:** Aplikacja crashuje natychmiast po uruchomieniu na urządzeniu z Androidem 9–12. Błąd w logcat: `ClassNotFoundException: android.window.OnBackAnimationCallback`.

**Przyczyna:** `androidx.activity` w nowszych wersjach próbuje załadować klasę `OnBackAnimationCallback` dostępną dopiero od API 33 (Android 13). Przy `minSdk` ustawionym na 34 problem nie istnieje, ale po obniżeniu do 28 ujawnia się na starszych systemach.

**Rozwiązanie zastosowane:** Problem jest łagodny (informacyjny — widoczny jako `I` nie `E` w logcat) i aplikacja działa mimo tego. `androidx.activity` obsługuje to przez reflection z graceful fallback. Jeśli crash nadal występuje, zaktualizuj `androidx.activity` do najnowszej wersji w `libs.versions.toml`.

---

## Animacja chowania/pokazywania widoku (slide) — ValueAnimator na height

**Problem:** `view.animate().translationY(-height)` przesuwa widok poza ekran, ale nie zwalnia miejsca w layoucie — inne widoki nie wypełniają wolnego miejsca.

**Przyczyna:** `translationY` zmienia tylko pozycję rysowania, nie wpływa na rozmiar widoku w layoucie.

**Rozwiązanie:** Animuj właściwość `layoutParams.height` przez `ValueAnimator`, a po zakończeniu ustaw `visibility = GONE` (lub `VISIBLE` przy pokazywaniu):
```kotlin
ValueAnimator.ofInt(currentHeight, 0).apply {
    duration = 250
    addUpdateListener {
        view.layoutParams.height = it.animatedValue as Int
        view.requestLayout()
    }
    addListener(object : AnimatorListenerAdapter() {
        override fun onAnimationEnd(animation: Animator) {
            view.visibility = View.GONE
        }
    })
    start()
}
```
Zapamiętaj oryginalną wysokość przed pierwszą animacją (np. w `view.post { height = view.measuredHeight }`).

---

## Duplikaty wpisów w libs.versions.toml

**Problem:** `Invalid TOML catalog definition — coroutines previously defined`.

**Przyczyna:** Przy dodawaniu nowych wpisów do sekcji `[versions]` lub `[libraries]` w `libs.versions.toml` łatwo przypadkowo dodać wpis który już istnieje.

**Rozwiązanie:** Przed dodaniem nowego wpisu zawsze sprawdź czy klucz o tej nazwie już nie istnieje w pliku. Każdy klucz w sekcji musi być unikalny.
