package com.spiewnik.app

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.spiewnik.app.data.Song
import com.spiewnik.app.data.SongRepository
import com.spiewnik.app.pdf.PdfPageCache
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

/**
 * Immutable UI state.
 * [allPageNumbers] are 1-based page numbers as defined in songs.json.
 * [spreadStart] is an index into [allPageNumbers] pointing at the current left page.
 */
data class UiState(
    val song: Song? = null,
    val allPageNumbers: List<Int> = emptyList(),
    val spreadStart: Int = 0,
    val navMode: NavMode = NavMode.SPREAD,
    val error: String? = null,
    val pdfMissing: Boolean = false,
    val jsonMissing: Boolean = false
) {
    /** 1-based page number shown on the left, null when no song is open. */
    val leftPageNumber: Int? get() = allPageNumbers.getOrNull(spreadStart)

    /** 1-based page number shown on the right, null for single-page songs. */
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

    val canGoLeft: Boolean get() = spreadStart > 0 || song != null
    val canGoRight: Boolean get() = spreadStart + 1 < allPageNumbers.size || song != null
}

class SongViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "SongViewModel"
        private const val PREF_FILE = "spiewnik_prefs"
        private const val PREF_LAST_SONG = "last_song_number"
    }

    val repository = SongRepository(application)
    val pdfCache = PdfPageCache(application)

    private val prefs = application.getSharedPreferences(PREF_FILE, 0)

    private val _state = MutableLiveData(UiState())
    val state: LiveData<UiState> = _state

    private val _allSongs = MutableLiveData<List<Song>>(emptyList())
    val allSongs: LiveData<List<Song>> = _allSongs

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch(Dispatchers.IO) {
            // Load songs.json
            val songs = try {
                repository.loadSongs()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load songs.json", e)
                _state.postValue(UiState(jsonMissing = true, error = "Nie można wczytać songs.json: ${e.message}"))
                return@launch
            }
            _allSongs.postValue(songs)

            // Open PDF
            if (!pdfCache.open()) {
                _state.postValue(UiState(pdfMissing = true, error = "Nie można otworzyć Spiewnik.pdf – upewnij się, że plik jest w assets/"))
                return@launch
            }

            // Restore last opened song
            val lastSong = prefs.getInt(PREF_LAST_SONG, -1)
            if (lastSong > 0) openSong(lastSong)
        }
    }

    fun openSong(number: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val song = repository.findByNumber(number)
            if (song == null) {
                _state.postValue(currentState().copy(error = "Nie znaleziono pieśni o numerze $number"))
                return@launch
            }

            val pdfPages = pdfCache.pageCount
            val validPages = song.pages.filter { it >= 1 && it <= pdfPages }
            if (validPages.isEmpty()) {
                _state.postValue(currentState().copy(
                    error = "Strony pieśni $number są poza zakresem PDF (liczba stron: $pdfPages)"
                ))
                return@launch
            }

            prefs.edit().putInt(PREF_LAST_SONG, number).apply()
            _state.postValue(UiState(
                song = song,
                allPageNumbers = validPages,
                spreadStart = 0,
                navMode = currentState().navMode
            ))
        }
    }

    fun navigateLeft() {
        val s = _state.value ?: return
        when (s.navMode) {
            NavMode.SPREAD -> {
                if (s.spreadStart >= 2) {
                    _state.value = s.copy(spreadStart = s.spreadStart - 2)
                } else if (s.spreadStart == 1) {
                    _state.value = s.copy(spreadStart = 0)
                } else {
                    s.song?.let { goToPrevSong(it.number) }
                }
            }
            NavMode.PAGE -> {
                if (s.spreadStart > 0) {
                    _state.value = s.copy(spreadStart = s.spreadStart - 1)
                } else {
                    s.song?.let { goToPrevSong(it.number) }
                }
            }
            NavMode.SONG -> s.song?.let { goToPrevSong(it.number) }
        }
    }

    fun navigateRight() {
        val s = _state.value ?: return
        val lastIdx = s.allPageNumbers.size - 1
        when (s.navMode) {
            NavMode.SPREAD -> {
                if (s.spreadStart + 2 <= lastIdx) {
                    _state.value = s.copy(spreadStart = s.spreadStart + 2)
                } else if (s.spreadStart < lastIdx) {
                    _state.value = s.copy(spreadStart = lastIdx)
                } else {
                    s.song?.let { goToNextSong(it.number) }
                }
            }
            NavMode.PAGE -> {
                if (s.spreadStart < lastIdx) {
                    _state.value = s.copy(spreadStart = s.spreadStart + 1)
                } else {
                    s.song?.let { goToNextSong(it.number) }
                }
            }
            NavMode.SONG -> s.song?.let { goToNextSong(it.number) }
        }
    }

    fun cycleNavMode() {
        _state.value = _state.value?.let { it.copy(navMode = it.navMode.next()) }
    }

    fun clearError() {
        _state.value = _state.value?.copy(error = null)
    }

    private fun goToPrevSong(currentNumber: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.previousSong(currentNumber)?.let { openSong(it.number) }
        }
    }

    private fun goToNextSong(currentNumber: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.nextSong(currentNumber)?.let { openSong(it.number) }
        }
    }

    private fun currentState() = _state.value ?: UiState()

    override fun onCleared() {
        super.onCleared()
        pdfCache.close()
    }
}
