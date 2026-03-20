package com.spiewnik.app

import android.app.Application
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
import kotlinx.coroutines.launch

enum class NavMode {
    SPREAD, PAGE, SONG;

    fun next(): NavMode = when (this) {
        SPREAD -> PAGE
        PAGE   -> SONG
        SONG   -> SPREAD
    }

    fun label(): String = when (this) {
        SPREAD -> "Rozkładówka"
        PAGE   -> "Strona"
        SONG   -> "Pieśń"
    }
}

sealed class LoadState {
    object Loading : LoadState()
    object Ready : LoadState()
    data class Error(val message: String) : LoadState()
}

/**
 * Immutable UI state.
 *
 * SONG mode:   shows song pages from strony_pdf (1 or 2). Arrows jump prev/next song.
 * SPREAD mode: shows N and N+1 from all PDF pages. Arrows move by 1 page.
 * PAGE mode:   shows single page N from all PDF pages. Arrows move by 1 page.
 *
 * [songPages] / [songPageIndex] — used in SONG mode only.
 * [currentPdfPage] / [totalPdfPages] — used in SPREAD and PAGE modes.
 */
data class UiState(
    val song: Song? = null,
    val navMode: NavMode = NavMode.SPREAD,
    val hasPrevSong: Boolean = false,
    val hasNextSong: Boolean = false,
    // SONG mode
    val songPages: List<Int> = emptyList(),
    val songPageIndex: Int = 0,
    // SPREAD / PAGE mode
    val currentPdfPage: Int = 1,
    val totalPdfPages: Int = 0,
) {
    val leftPageNumber: Int? get() = when (navMode) {
        NavMode.SONG -> songPages.getOrNull(songPageIndex)
        else         -> currentPdfPage.takeIf { totalPdfPages > 0 && it in 1..totalPdfPages }
    }

    val rightPageNumber: Int? get() = when (navMode) {
        NavMode.SPREAD -> (currentPdfPage + 1).takeIf { it <= totalPdfPages }
        NavMode.SONG   -> songPages.getOrNull(songPageIndex + 1)
        NavMode.PAGE   -> null
    }

    val leftPdfIndex: Int?  get() = leftPageNumber?.minus(1)
    val rightPdfIndex: Int? get() = rightPageNumber?.minus(1)

    val displayPages: String get() {
        val l = leftPageNumber ?: return ""
        val r = rightPageNumber
        return if (r != null) "str. $l–$r" else "str. $l"
    }

    val canGoLeft: Boolean get() = when (navMode) {
        NavMode.SONG -> hasPrevSong
        else         -> totalPdfPages > 0 && currentPdfPage > 1
    }

    val canGoRight: Boolean get() = when (navMode) {
        NavMode.SONG -> hasNextSong
        else         -> totalPdfPages > 0 && currentPdfPage < totalPdfPages
    }
}

class SongViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "SongViewModel"
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

            val savedNavMode = try {
                NavMode.valueOf(settings.navMode)
            } catch (e: Exception) {
                NavMode.SPREAD
            }

            when (savedNavMode) {
                NavMode.SONG -> openSong(settings.lastSongNumber, savedNavMode, settings.lastPageIndex)
                else -> {
                    val page = settings.lastPdfPage.coerceIn(1, totalPdfPages)
                    val song = songs.find { page in it.pages }
                    val hasPrev = song?.let { repository.previousSong(it.number) != null } ?: false
                    val hasNext = song?.let { repository.nextSong(it.number) != null } ?: false
                    _state.postValue(UiState(
                        song = song,
                        navMode = savedNavMode,
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
