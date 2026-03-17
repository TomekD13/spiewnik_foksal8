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
 * [allPageNumbers] are 1-based page numbers from piesni.json.
 * [spreadStart] is the index into [allPageNumbers] for the current left/single page.
 * [hasPrevSong] / [hasNextSong] drive the disabled state of navigation arrows.
 */
data class UiState(
    val song: Song? = null,
    val allPageNumbers: List<Int> = emptyList(),
    val spreadStart: Int = 0,
    val navMode: NavMode = NavMode.SPREAD,
    val hasPrevSong: Boolean = false,
    val hasNextSong: Boolean = false
) {
    /** 1-based page number shown on the left (or single page in portrait). */
    val leftPageNumber: Int? get() = allPageNumbers.getOrNull(spreadStart)

    /** 1-based page number shown on the right; null in portrait or single-page songs. */
    val rightPageNumber: Int? get() =
        if (allPageNumbers.size > spreadStart + 1) allPageNumbers[spreadStart + 1] else null

    /** 0-based index for PdfRenderer */
    val leftPdfIndex: Int? get() = leftPageNumber?.minus(1)

    /** 0-based index for PdfRenderer */
    val rightPdfIndex: Int? get() = rightPageNumber?.minus(1)

    val displayPages: String get() {
        val l = leftPageNumber ?: return ""
        val r = rightPageNumber
        return if (r != null) "str. $l–$r" else "str. $l"
    }

    val canGoLeft: Boolean get() {
        if (song == null) return false
        return when (navMode) {
            NavMode.SONG -> hasPrevSong
            else         -> spreadStart > 0 || hasPrevSong
        }
    }

    val canGoRight: Boolean get() {
        if (song == null) return false
        val lastIdx = allPageNumbers.size - 1
        return when (navMode) {
            NavMode.SONG -> hasNextSong
            else         -> spreadStart < lastIdx || hasNextSong
        }
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

    private val _state = MutableLiveData(UiState())
    val state: LiveData<UiState> = _state

    private val _holyricsPlaylist = MutableLiveData<List<Int>>(emptyList())
    val holyricsPlaylist: LiveData<List<Int>> = _holyricsPlaylist

    private val _allSongs = MutableLiveData<List<Song>>(emptyList())
    val allSongs: LiveData<List<Song>> = _allSongs

    private val _loadState = MutableLiveData<LoadState>(LoadState.Loading)
    val loadState: LiveData<LoadState> = _loadState

    /** Single-fire toast message. Observer must call clearToast() after consuming. */
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

            _loadState.postValue(LoadState.Ready)

            val savedNavMode = try {
                NavMode.valueOf(settings.navMode)
            } catch (e: Exception) {
                NavMode.SPREAD
            }

            openSong(settings.lastSongNumber, savedNavMode, settings.lastPageIndex)
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

            val pdfPageCount = pdfCache.pageCount
            val validPages = song.pages.filter { it >= 1 && it <= pdfPageCount }
            if (validPages.isEmpty()) {
                _toastEvent.postValue("Brak strony w śpiewniku")
                return@launch
            }

            val clampedIndex = pageIndex.coerceIn(0, validPages.size - 1)

            settings.lastSongNumber = number
            settings.lastPageIndex = clampedIndex

            val hasPrev = repository.previousSong(number) != null
            val hasNext = repository.nextSong(number) != null

            _state.postValue(
                UiState(
                    song = song,
                    allPageNumbers = validPages,
                    spreadStart = clampedIndex,
                    navMode = currentNavMode,
                    hasPrevSong = hasPrev,
                    hasNextSong = hasNext
                )
            )
        }
    }

    fun navigateLeft() {
        val s = _state.value ?: return
        val isPortrait = getApplication<Application>()
            .resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT

        when (s.navMode) {
            NavMode.SPREAD -> {
                if (isPortrait) {
                    s.song?.let { goToPrevSong(it.number, s.navMode) }
                } else if (s.spreadStart >= 2) {
                    val newIdx = s.spreadStart - 2
                    settings.lastPageIndex = newIdx
                    _state.value = s.copy(spreadStart = newIdx)
                } else if (s.spreadStart == 1) {
                    settings.lastPageIndex = 0
                    _state.value = s.copy(spreadStart = 0)
                } else {
                    s.song?.let { goToPrevSong(it.number, s.navMode) }
                }
            }
            NavMode.PAGE -> {
                if (s.spreadStart > 0) {
                    val newIdx = s.spreadStart - 1
                    settings.lastPageIndex = newIdx
                    _state.value = s.copy(spreadStart = newIdx)
                } else {
                    s.song?.let { goToPrevSong(it.number, s.navMode) }
                }
            }
            NavMode.SONG -> s.song?.let { goToPrevSong(it.number, s.navMode) }
        }
    }

    fun navigateRight() {
        val s = _state.value ?: return
        val lastIdx = s.allPageNumbers.size - 1
        val isPortrait = getApplication<Application>()
            .resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT

        when (s.navMode) {
            NavMode.SPREAD -> {
                if (isPortrait) {
                    s.song?.let { goToNextSong(it.number, s.navMode) }
                } else if (s.spreadStart + 2 <= lastIdx) {
                    val newIdx = s.spreadStart + 2
                    settings.lastPageIndex = newIdx
                    _state.value = s.copy(spreadStart = newIdx)
                } else if (s.spreadStart < lastIdx) {
                    settings.lastPageIndex = lastIdx
                    _state.value = s.copy(spreadStart = lastIdx)
                } else {
                    s.song?.let { goToNextSong(it.number, s.navMode) }
                }
            }
            NavMode.PAGE -> {
                if (s.spreadStart < lastIdx) {
                    val newIdx = s.spreadStart + 1
                    settings.lastPageIndex = newIdx
                    _state.value = s.copy(spreadStart = newIdx)
                } else {
                    s.song?.let { goToNextSong(it.number, s.navMode) }
                }
            }
            NavMode.SONG -> s.song?.let { goToNextSong(it.number, s.navMode) }
        }
    }

    fun setNavMode(mode: NavMode) {
        settings.navMode = mode.name
        _state.value = _state.value?.copy(navMode = mode)
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
