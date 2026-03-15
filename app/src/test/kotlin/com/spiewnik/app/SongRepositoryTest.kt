package com.spiewnik.app

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.spiewnik.app.data.Song
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for song data parsing and lookup logic.
 * These tests work directly on the data model without needing Android context.
 */
class SongRepositoryTest {

    private lateinit var songs: List<Song>

    private val sampleJson = """
        [
          {"number": 480, "title": "Przyjdź Duchu Święty", "pages": [470, 471]},
          {"number": 1,   "title": "Bóg jest miłością",    "pages": [1, 2]},
          {"number": 150, "title": "Jezu, ufam Tobie",     "pages": [143]},
          {"number": 700, "title": "Zdrowaś Maryjo",       "pages": [691, 692]}
        ]
    """.trimIndent()

    @Before
    fun setUp() {
        val type = object : TypeToken<List<Song>>() {}.type
        songs = (Gson().fromJson<List<Song>>(sampleJson, type)).sortedBy { it.number }
    }

    @Test
    fun `songs are sorted by number after loading`() {
        assertEquals(listOf(1, 150, 480, 700), songs.map { it.number })
    }

    @Test
    fun `find by number returns correct song`() {
        val song = songs.find { it.number == 480 }
        assertNotNull(song)
        assertEquals("Przyjdź Duchu Święty", song!!.title)
        assertEquals(listOf(470, 471), song.pages)
    }

    @Test
    fun `find by number returns null for missing song`() {
        val song = songs.find { it.number == 999 }
        assertNull(song)
    }

    @Test
    fun `single-page song has one page`() {
        val song = songs.find { it.number == 150 }!!
        assertEquals(1, song.pages.size)
        assertEquals(143, song.pages[0])
    }

    @Test
    fun `two-page song has two pages`() {
        val song = songs.find { it.number == 480 }!!
        assertEquals(2, song.pages.size)
    }

    @Test
    fun `previousSong returns correct song`() {
        val idx = songs.indexOfFirst { it.number == 480 }
        val prev = if (idx > 0) songs[idx - 1] else null
        assertEquals(150, prev?.number)
    }

    @Test
    fun `nextSong returns correct song`() {
        val idx = songs.indexOfFirst { it.number == 480 }
        val next = if (idx < songs.size - 1) songs[idx + 1] else null
        assertEquals(700, next?.number)
    }

    @Test
    fun `firstSong has no previous`() {
        val idx = songs.indexOfFirst { it.number == 1 }
        val prev = if (idx > 0) songs[idx - 1] else null
        assertNull(prev)
    }

    @Test
    fun `lastSong has no next`() {
        val idx = songs.indexOfFirst { it.number == 700 }
        val next = if (idx < songs.size - 1) songs[idx + 1] else null
        assertNull(next)
    }

    @Test
    fun `title search is case insensitive`() {
        val results = songs.filter { it.title.lowercase().contains("maryjo") }
        assertEquals(1, results.size)
        assertEquals(700, results[0].number)
    }
}
