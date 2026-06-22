package com.spiewnik.app.data

import android.content.Context
import android.util.Log
import com.spiewnik.core.SongCatalog

/**
 * Android-side loader: reads piesni.json from assets and delegates all parsing/search
 * to the shared [SongCatalog] in :core (same logic as the desktop app).
 */
class SongRepository(private val context: Context) {

    companion object {
        private const val TAG = "SongRepository"
        private const val SONGS_FILE = "piesni.json"
    }

    private var cachedCatalog: SongCatalog? = null

    private fun catalog(): SongCatalog {
        cachedCatalog?.let { return it }
        val json = context.assets.open(SONGS_FILE)
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }
        val catalog = SongCatalog.fromJson(json)
        cachedCatalog = catalog
        Log.i(TAG, "Loaded ${catalog.songs.size} songs")
        return catalog
    }

    fun loadSongs(): List<Song> = catalog().songs

    fun findByNumber(number: Int): Song? = catalog().findByNumber(number)

    /** Pierwsza pieśń zawierająca daną stronę PDF (1-based). */
    fun findByPage(page: Int): Song? = catalog().songs.find { page in it.pages }

    fun searchByTitle(query: String): List<Song> = catalog().searchByTitle(query)

    fun allSongs(): List<Song> = catalog().songs

    fun previousSong(currentNumber: Int): Song? = catalog().previousSong(currentNumber)

    fun nextSong(currentNumber: Int): Song? = catalog().nextSong(currentNumber)
}
