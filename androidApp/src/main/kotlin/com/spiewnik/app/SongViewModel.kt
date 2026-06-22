package com.spiewnik.app

import android.app.Application
import android.content.res.Configuration
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.spiewnik.app.data.Song
import com.spiewnik.app.data.SongRepository
import com.spiewnik.app.holyrics.HolyricsRepository
import com.spiewnik.app.pdf.PdfPageCache
import com.spiewnik.app.settings.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

// NavMode and UiState moved to :core (com.spiewnik.app) — shared with the desktop app.

sealed class LoadState {
    object Loading : LoadState()
    object Ready : LoadState()
    data class Error(val message: String) : LoadState()
}

class SongViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "SongViewModel"
        private const val AUTO_FOLLOW_INTERVAL_MS = 2000L
    }

    val repository = SongRepository(application)
    val pdfCache = PdfPageCache(application)
    val settings = AppSettings(application)
    val holyricsRepository = HolyricsRepository()

    private var totalPdfPages = 0

    private val _state = MutableLiveData(UiState())
    val state: LiveData<UiState> = _state

    private val _holyricsPlaylist = MutableLiveData<List<Int>>(emptyList())
    val holyricsPlaylist: LiveData<List<Int>> = _holyricsPlaylist

    // Song currently shown on screen in Holyrics (null = nothing/non-song presented)
    private val _currentHolyricsSong = MutableLiveData<Int?>(null)
    val currentHolyricsSong: LiveData<Int?> = _currentHolyricsSong

    private val _allSongs = MutableLiveData<List<Song>>(emptyList())
    val allSongs: LiveData<List<Song>> = _allSongs

    private val _loadState = MutableLiveData<LoadState>(LoadState.Loading)
    val loadState: LiveData<LoadState> = _loadState

    private val _toastEvent = MutableLiveData<String?>()
    val toastEvent: LiveData<String?> = _toastEvent

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch(Dispatchers.IO) {
            val songs = try {
                repository.loadSongs()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load piesni.json", e)
                _loadState.postValue(LoadState.Error("Brak listy pieśni (piesni.json)"))
                return@launch
            }
            _allSongs.postValue(songs)

            if (!pdfCache.open()) {
                _loadState.postValue(LoadState.Error("Brak pliku śpiewnika (Spiewnik.pdf)"))
                return@launch
            }

            totalPdfPages = pdfCache.pageCount
            _loadState.postValue(LoadState.Ready)

            // Tryb startowy zależy od orientacji ekranu: poziom → Pieśń, pion → Strona.
            // Użytkownik może go zmienić przyciskiem trybu; obrót przywraca domyślny tryb.
            val initialMode = defaultModeForCurrentOrientation()

            when (initialMode) {
                NavMode.SONG -> openSong(settings.lastSongNumber, initialMode, settings.lastPageIndex)
                else -> {
                    val page = settings.lastPdfPage.coerceIn(1, totalPdfPages)
                    val song = songs.find { page in it.pages }
                    val hasPrev = song?.let { repository.previousSong(it.number) != null } ?: false
                    val hasNext = song?.let { repository.nextSong(it.number) != null } ?: false
                    _state.postValue(UiState(
                        song = song,
                        navMode = initialMode,
                        currentPdfPage = page,
                        totalPdfPages = totalPdfPages,
                        hasPrevSong = hasPrev,
                        hasNextSong = hasNext,
                        songPages = song?.pages?.filter { it in 1..totalPdfPages } ?: emptyList(),
                    ))
                }
            }
        }
    }

    fun openSong(number: Int, navMode: NavMode? = null, pageIndex: Int = 0) {
        viewModelScope.launch(Dispatchers.IO) {
            val currentNavMode = navMode ?: currentState().navMode
            val song = repository.findByNumber(number)

            if (song == null) {
                _toastEvent.postValue("Nie znaleziono pieśni o numerze $number")
                return@launch
            }

            if (song.pages.isEmpty()) {
                _toastEvent.postValue("Brak strony w śpiewniku")
                return@launch
            }

            val validPages = song.pages.filter { it >= 1 && it <= totalPdfPages }
            if (validPages.isEmpty()) {
                _toastEvent.postValue("Brak strony w śpiewniku")
                return@launch
            }

            val clampedIndex = pageIndex.coerceIn(0, validPages.size - 1)
            val firstPage = validPages[clampedIndex]

            settings.lastSongNumber = number
            settings.lastPageIndex = clampedIndex
            if (currentNavMode != NavMode.SONG) settings.lastPdfPage = firstPage

            val hasPrev = repository.previousSong(number) != null
            val hasNext = repository.nextSong(number) != null

            _state.postValue(UiState(
                song = song,
                navMode = currentNavMode,
                hasPrevSong = hasPrev,
                hasNextSong = hasNext,
                songPages = validPages,
                songPageIndex = clampedIndex,
                currentPdfPage = firstPage,
                totalPdfPages = totalPdfPages,
            ))
        }
    }

    fun navigateLeft() {
        val s = _state.value ?: return
        when (s.navMode) {
            NavMode.SPREAD, NavMode.PAGE -> {
                val newPage = (s.currentPdfPage - 1).coerceAtLeast(1)
                if (newPage != s.currentPdfPage) {
                    settings.lastPdfPage = newPage
                    _state.value = s.copy(currentPdfPage = newPage)
                }
            }
            NavMode.SONG -> s.song?.let { goToPrevSong(it.number, s.navMode) }
        }
    }

    fun navigateRight() {
        val s = _state.value ?: return
        when (s.navMode) {
            NavMode.SPREAD, NavMode.PAGE -> {
                val newPage = (s.currentPdfPage + 1).coerceAtMost(s.totalPdfPages)
                if (newPage != s.currentPdfPage) {
                    settings.lastPdfPage = newPage
                    _state.value = s.copy(currentPdfPage = newPage)
                }
            }
            NavMode.SONG -> s.song?.let { goToNextSong(it.number, s.navMode) }
        }
    }

    fun setNavMode(mode: NavMode) {
        settings.navMode = mode.name
        _state.value = _state.value?.copy(navMode = mode, totalPdfPages = totalPdfPages)
    }

    fun cycleNavMode() {
        val current = _state.value?.navMode ?: NavMode.SPREAD
        setNavMode(current.next())
    }

    private fun defaultModeForCurrentOrientation(): NavMode {
        val isLandscape = getApplication<Application>().resources.configuration.orientation ==
            Configuration.ORIENTATION_LANDSCAPE
        return NavMode.defaultFor(isLandscape)
    }

    /**
     * Wywoływane po obrocie ekranu — ustawia domyślny tryb dla nowej orientacji.
     * Ręczny wybór trybu obowiązuje tylko do następnego obrotu.
     * Wchodząc w tryb Pieśń otwieramy pieśń zawierającą aktualnie oglądaną stronę,
     * żeby przejście Strona → Pieśń nie zgubiło kontekstu.
     */
    fun applyOrientationDefault(isLandscape: Boolean) {
        val s = _state.value ?: return
        val mode = NavMode.defaultFor(isLandscape)
        if (s.navMode == mode) return
        if (mode == NavMode.SONG) {
            // Otwórz pieśń odpowiadającą bieżącej stronie (gdy w trybie Strona przewinięto
            // na inną pieśń), z fallbackiem do aktualnie wczytanej. openSong sam używa postValue.
            viewModelScope.launch(Dispatchers.IO) {
                val number = repository.findByPage(s.currentPdfPage)?.number ?: s.song?.number
                if (number != null) {
                    openSong(number, NavMode.SONG, 0)
                } else {
                    _state.postValue(s.copy(navMode = NavMode.SONG, totalPdfPages = totalPdfPages))
                }
            }
        } else {
            // PAGE: zachowujemy bieżącą stronę. Wywoływane z wątku głównego (onConfigurationChanged).
            setNavMode(mode)
        }
    }

    fun resetPosition() {
        settings.resetPosition()
        openSong(1, _state.value?.navMode, 0)
        _toastEvent.postValue("Pozycja zresetowana (pieśń 1)")
    }

    fun fetchHolyricsPlaylist() {
        viewModelScope.launch(Dispatchers.IO) {
            val ip = settings.holyricsIp
            val token = settings.holyricsToken
            if (ip.isBlank() || token.isBlank()) {
                _toastEvent.postValue("Uzupełnij IP i token Holyrics w ustawieniach")
                return@launch
            }
            holyricsRepository.fetchPlaylist(ip, token)
                .onSuccess { numbers ->
                    Log.i(TAG, "Holyrics fetchPlaylist success: $numbers")
                    if (numbers.isEmpty()) {
                        _toastEvent.postValue("Playlista Holyrics jest pusta")
                    } else {
                        _holyricsPlaylist.postValue(numbers)
                    }
                }
                .onFailure { e ->
                    Log.e(TAG, "Holyrics fetchPlaylist failure", e)
                    _toastEvent.postValue("Holyrics niedostępny")
                }
        }
    }

    // ── Auto-follow Holyrics ───────────────────────────────────────────────────
    private var autoFollowJob: Job? = null

    /**
     * Starts polling Holyrics' current presentation and auto-opens the song shown on
     * the projector. No-op if the setting is off or IP/token are missing. Holyrics
     * always wins: whatever is on screen there becomes the open song here.
     */
    fun startHolyricsAutoFollow() {
        if (!settings.holyricsAutoFollow) return
        if (settings.holyricsIp.isBlank() || settings.holyricsToken.isBlank()) return
        if (autoFollowJob?.isActive == true) return
        autoFollowJob = viewModelScope.launch(Dispatchers.IO) {
            var lastSeen: Int? = null   // last number reported by Holyrics — act once per change
            while (isActive) {
                val ip = settings.holyricsIp
                val token = settings.holyricsToken
                holyricsRepository.fetchCurrentSong(ip, token)
                    .onSuccess { number ->
                        _currentHolyricsSong.postValue(number)
                        // React only when Holyrics switches to a new song that isn't already open.
                        // Acting once per change avoids toast spam for numbers absent from piesni.json.
                        if (number != null && number != lastSeen && number != _state.value?.song?.number) {
                            openSong(number)
                        }
                        lastSeen = number
                    }
                delay(AUTO_FOLLOW_INTERVAL_MS)
            }
        }
    }

    fun stopHolyricsAutoFollow() {
        autoFollowJob?.cancel()
        autoFollowJob = null
    }

    /**
     * Fetches the song currently shown on screen in Holyrics, to highlight it in the popup.
     * Secondary info — failures are logged but not surfaced as a toast.
     */
    fun fetchHolyricsCurrentSong() {
        viewModelScope.launch(Dispatchers.IO) {
            val ip = settings.holyricsIp
            val token = settings.holyricsToken
            if (ip.isBlank() || token.isBlank()) return@launch
            holyricsRepository.fetchCurrentSong(ip, token)
                .onSuccess { number ->
                    Log.i(TAG, "Holyrics current song: $number")
                    _currentHolyricsSong.postValue(number)
                }
                .onFailure { e ->
                    Log.e(TAG, "Holyrics fetchCurrentSong failure", e)
                }
        }
    }

    fun showToast(message: String) {
        _toastEvent.postValue(message)
    }

    fun clearToast() {
        _toastEvent.value = null
    }

    private fun goToPrevSong(currentNumber: Int, navMode: NavMode) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.previousSong(currentNumber)?.let { openSong(it.number, navMode, 0) }
        }
    }

    private fun goToNextSong(currentNumber: Int, navMode: NavMode) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.nextSong(currentNumber)?.let { openSong(it.number, navMode, 0) }
        }
    }

    private fun currentState() = _state.value ?: UiState()

    override fun onCleared() {
        super.onCleared()
        pdfCache.close()
    }
}
