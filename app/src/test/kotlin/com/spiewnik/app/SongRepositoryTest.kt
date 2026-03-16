package com.spiewnik.app

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.spiewnik.app.data.Song
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for song data parsing and lookup logic.
 * JSON format matches piesni.json: nr_piesni, tytul, strony_pdf, interpolated.
 */
class SongRepositoryTest {

    private lateinit var songs: List<Song>

    private val sampleJson = """
        [
          {"nr_piesni": 480, "tytul": "Przyjdź Duchu Święty", "strony_pdf": [470, 471], "interpolated": false},
          {"nr_piesni": 1,   "tytul": "Bóg jest miłością",    "strony_pdf": [1, 2],     "interpolated": false},
          {"nr_piesni": 150, "tytul": "Jezu, ufam Tobie",     "strony_pdf": [143],      "interpolated": false},
          {"nr_piesni": 700, "tytul": "Zdrowaś Maryjo",       "strony_pdf": [691, 692], "interpolated": false}
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
        assertNull(songs.find { it.number == 999 })
    }

    @Test
    fun `single-page song has one page`() {
        val song = songs.find { it.number == 150 }!!
        assertEquals(1, song.pages.size)
        assertEquals(143, song.pages[0])
    }

    @Test
    fun `two-page song has two pages`() {
        assertEquals(2, songs.find { it.number == 480 }!!.pages.size)
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
        assertNull(if (idx > 0) songs[idx - 1] else null)
    }

    @Test
    fun `lastSong has no next`() {
        val idx = songs.indexOfFirst { it.number == 700 }
        assertNull(if (idx < songs.size - 1) songs[idx + 1] else null)
    }

    @Test
    fun `title search is case insensitive`() {
        val results = songs.filter { it.title.lowercase().contains("maryjo") }
        assertEquals(1, results.size)
        assertEquals(700, results[0].number)
    }

    @Test
    fun `interpolated field defaults to false`() {
        songs.forEach { assertFalse(it.interpolated) }
    }

    @Test
    fun `song with interpolated true is parsed correctly`() {
        val json = """[{"nr_piesni": 5, "tytul": "Test", "strony_pdf": [10], "interpolated": true}]"""
        val type = object : TypeToken<List<Song>>() {}.type
        val parsed: List<Song> = Gson().fromJson(json, type)
        assertTrue(parsed[0].interpolated)
    }
}
