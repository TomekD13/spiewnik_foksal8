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

## Duplikaty wpisów w libs.versions.toml

**Problem:** `Invalid TOML catalog definition — coroutines previously defined`.

**Przyczyna:** Przy dodawaniu nowych wpisów do sekcji `[versions]` lub `[libraries]` w `libs.versions.toml` łatwo przypadkowo dodać wpis który już istnieje.

**Rozwiązanie:** Przed dodaniem nowego wpisu zawsze sprawdź czy klucz o tej nazwie już nie istnieje w pliku. Każdy klucz w sekcji musi być unikalny.
