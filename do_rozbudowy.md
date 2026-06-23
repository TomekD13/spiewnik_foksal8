# Do rozbudowy

Lista pomysłów i usprawnień do wdrożenia w przyszłości — w formie „co chcemy osiągnąć" + „co trzeba zrobić".

> Zrealizowane (tryby wg orientacji, usunięcie wyboru trybu z ustawień, skan QR Holyrics,
> numer wersji w aplikacji Windows, zmniejszenie numpada w aplikacji Windows ~20%)
> zostały opisane w dokumentacji i usunięte z tej listy.

## 1. Optymalizacja pamięci pod tablet TCL NXTPAPER 14 (2400×1600, 14,3", 3:2)

### Co chcemy osiągnąć
Płynne działanie bez `OutOfMemory` i zacięć na docelowym tablecie. Przy 2400×1600 jedna
strona renderowana 2× to ~30 MB (`ARGB_8888`); 2 strony widoczne + 2 prefetchowane to
łatwo ~120 MB chwilowo — to realne ryzyko na heapie ~192–256 MB.

### Stan obecny (sprostowanie)
Cache **już jest** ograniczany rozmiarem pamięci, nie liczbą stron:
`LruCache(maxMemory/6 [KB])` z `sizeOf = byteCount/1024`
(`androidApp/.../pdf/PdfPageCache.kt:30-33`). Uwaga „brak limitu MB" z wcześniejszej
polemiki jest więc nietrafna — limit istnieje. Poprawiamy to, co faktycznie pomaga:
zmniejszamy koszt pojedynczej strony i agresję prefetchu.

### Co trzeba zrobić
1. ❌ **`RGB_565` — ODRZUCONE.** Próbowane, ale `PdfRenderer.render()` wymaga `ARGB_8888`;
   RGB_565 dawało **czarny ekran** na realnym tablecie (Android 14), choć przechodziło na
   emulatorze. Cofnięte do `ARGB_8888`. Oszczędności pamięci szukamy poniżej, NIE w formacie.
2. **Mniej agresywny prefetch (do rozważenia):** ograniczyć z 2 sąsiednich do
   **1 (następna strona)** w `prefetchNeighbours()` (`MainActivity.kt`).
3. **Render w ARGB_8888 + cache w RGB_565 (do rozważenia):** renderować w wymaganym
   ARGB_8888, a do cache trzymać kopię `bitmap.copy(RGB_565, false)` — pół pamięci w cache
   bez psucia renderu. Uwaga: kopia + chwilowa bitmapa ARGB w trakcie renderowania.

### Do decyzji (tradeoff)
- **Współczynnik renderowania 2× → 1,5×** (`PdfPageCache.kt:91`). Daje duży zysk
  (~15 MB → ~9 MB/strona), ALE na nutach cienkie linie mogą stracić ostrość — zwłaszcza
  na matowym ekranie NXTPAPER. **Decyzja do podjęcia:** zostawić 2×, dać 1,5×, czy zrobić
  z tego parametr/ustawienie (1,5×/2,0×). Konsekwencja: niższy = mniej RAM, wyższy =
  ostrzejsze cienkie linie.
- **`android:largeHeap="true"`** — **już włączone** w `AndroidManifest.xml`. To fallback
  (maskuje problemy, działa różnie na urządzeniach). Właściwe oszczędności: skala 1,5×,
  prefetch=1 lub cache w RGB_565 (pkt wyżej). Tablet ma 8 GB RAM, więc na razie ARGB_8888
  + largeHeap są w pełni wystarczające — pamięć to temat „na później", nie pilny.

### Opcjonalne (większy nakład, ostrożnie)
- **Recykling bitmap (`inBitmap`/reuse):** trudne razem z `LruCache`, który wciąż trzyma
  referencje — łatwo o użycie zwolnionej bitmapy. Tylko jeśli pomiary pokażą problem z GC.
- **Lazy loading:** częściowo już jest (render lewej, potem prawej; prawa tylko gdy
  istnieje). Ewentualnie nie renderować prawej, dopóki realnie widoczna.

### Weryfikacja
Po zmianach zbudować APK i zmierzyć zużycie pamięci na realnym tablecie przez ADB
(np. `adb shell dumpsys meminfo <pkg>`, ewentualnie log z `Debug.getNativeHeapAllocatedSize()`).

### Szacunki pamięci (na 1 stronę / 4 strony)
| Konfiguracja | na stronę | 4 strony |
|---|---|---|
| ARGB_8888, 2× (obecnie) | ~30 MB | ~120 MB 💀 |
| RGB_565, 2× | ~15 MB | ~60 MB ✅ |
| RGB_565, 1,5× | ~9 MB | ~36 MB ✅✅ |


