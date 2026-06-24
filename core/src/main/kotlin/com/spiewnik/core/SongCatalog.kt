package com.spiewnik.core

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.spiewnik.app.data.Song

/**
 * Platform-neutral song catalog: parses piesni.json (as a String) and exposes
 * lookup/search. Loading the file is the platform's job (Android AssetManager,
 * desktop classpath/file) — this class only takes the already-read JSON.
 *
 * Shared by :androidApp and :desktopApp.
 */
class SongCatalog private constructor(val songs: List<Song>) {

    fun findByNumber(number: Int): Song? = songs.find { it.number == number }

    /** Pierwsza pieśń zawierająca daną stronę PDF (1-based). */
    fun findByPage(page: Int): Song? = songs.find { page in it.pages }

    fun searchByTitle(query: String): List<Song> {
        if (query.isBlank()) return emptyList()
        val lower = query.lowercase()
        return songs.filter { it.title.lowercase().contains(lower) }
    }

    fun previousSong(currentNumber: Int): Song? {
        val idx = songs.indexOfFirst { it.number == currentNumber }
        return if (idx > 0) songs[idx - 1] else null
    }

    fun nextSong(currentNumber: Int): Song? {
        val idx = songs.indexOfFirst { it.number == currentNumber }
        return if (idx in 0 until songs.size - 1) songs[idx + 1] else null
    }

    companion object {
        /** Parses piesni.json content and returns a catalog sorted by song number. */
        fun fromJson(json: String): SongCatalog {
            val type = object : TypeToken<List<Song>>() {}.type
            val songs: List<Song> = Gson().fromJson(json, type)
            return SongCatalog(songs.sortedBy { it.number })
        }
    }
}
