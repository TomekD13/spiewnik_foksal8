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

## Duplikaty wpisów w libs.versions.toml

**Problem:** `Invalid TOML catalog definition — coroutines previously defined`.

**Przyczyna:** Przy dodawaniu nowych wpisów do sekcji `[versions]` lub `[libraries]` w `libs.versions.toml` łatwo przypadkowo dodać wpis który już istnieje.

**Rozwiązanie:** Przed dodaniem nowego wpisu zawsze sprawdź czy klucz o tej nazwie już nie istnieje w pliku. Każdy klucz w sekcji musi być unikalny.
