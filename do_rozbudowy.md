# Do rozbudowy

Lista pomysłów i usprawnień do wdrożenia w przyszłości — w formie „co chcemy osiągnąć" + „co trzeba zrobić".

> Zrealizowane (tryby wg orientacji, usunięcie wyboru trybu z ustawień, skan QR Holyrics)
> zostały opisane w `README.md` i usunięte z tej listy.

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

### Co trzeba zrobić (priorytet — pewny zysk)
1. **`Bitmap.Config.RGB_565` zamiast `ARGB_8888`** w `PdfPageCache.renderPage()`
   (`PdfPageCache.kt:~95`). Połowa pamięci na stronę (~30 MB → ~15 MB). Nuty są
   czarno-białe, a bitmapa i tak jest zamalowywana na biało (`eraseColor(WHITE)`),
   więc kanał alfa jest zbędny — praktycznie bez utraty jakości. Automatycznie 2× więcej
   stron mieści się w istniejącym cache.
2. **Mniej agresywny prefetch:** ograniczyć z 2 sąsiednich do **1 (następna strona)**
   w `prefetchNeighbours()` (`MainActivity.kt:575-593`).

### Do decyzji (tradeoff)
- **Współczynnik renderowania 2× → 1,5×** (`PdfPageCache.kt:91`). Daje duży zysk
  (~15 MB → ~9 MB/strona), ALE na nutach cienkie linie mogą stracić ostrość — zwłaszcza
  na matowym ekranie NXTPAPER. **Decyzja do podjęcia:** zostawić 2×, dać 1,5×, czy zrobić
  z tego parametr/ustawienie (1,5×/2,0×). Konsekwencja: niższy = mniej RAM, wyższy =
  ostrzejsze cienkie linie.
- **`android:largeHeap="true"`** — **już włączone** w `AndroidManifest.xml`. To tylko
  fallback (maskuje problemy, działa różnie na urządzeniach), więc i tak warto wprowadzić
  RGB_565 + prefetch=1 jako właściwe rozwiązanie. Rozważyć, czy `largeHeap` jest potrzebne
  po tych zmianach.

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
