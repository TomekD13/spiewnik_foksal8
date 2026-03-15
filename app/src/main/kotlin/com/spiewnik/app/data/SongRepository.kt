package com.spiewnik.app.data

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class SongRepository(private val context: Context) {

    companion object {
        private const val TAG = "SongRepository"
        private const val SONGS_FILE = "songs.json"
    }

    private var cachedSongs: List<Song>? = null

    /**
     * Loads and returns all songs sorted by number.
     * Throws on IO or JSON parse errors so callers can handle them.
     */
    fun loadSongs(): List<Song> {
        cachedSongs?.let { return it }
        val json = context.assets.open(SONGS_FILE)
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }
        val type = object : TypeToken<List<Song>>() {}.type
        val songs: List<Song> = Gson().fromJson(json, type)
        val sorted = songs.sortedBy { it.number }
        cachedSongs = sorted
        Log.i(TAG, "Loaded ${sorted.size} songs")
        return sorted
    }

    fun findByNumber(number: Int): Song? = loadSongs().find { it.number == number }

    fun searchByTitle(query: String): List<Song> {
        if (query.isBlank()) return emptyList()
        val lower = query.lowercase()
        return loadSongs().filter { it.title.lowercase().contains(lower) }
    }

    fun allSongs(): List<Song> = loadSongs()

    fun previousSong(currentNumber: Int): Song? {
        val songs = loadSongs()
        val idx = songs.indexOfFirst { it.number == currentNumber }
        return if (idx > 0) songs[idx - 1] else null
    }

    fun nextSong(currentNumber: Int): Song? {
        val songs = loadSongs()
        val idx = songs.indexOfFirst { it.number == currentNumber }
        return if (idx in 0 until songs.size - 1) songs[idx + 1] else null
    }
}
