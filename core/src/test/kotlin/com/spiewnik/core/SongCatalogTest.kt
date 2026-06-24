package com.spiewnik.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SongCatalogTest {

    private val catalog = SongCatalog.fromJson(
        """[
            {"nr_piesni":3,"tytul":"Trzecia","strony_pdf":[12,13]},
            {"nr_piesni":1,"tytul":"Pierwsza","strony_pdf":[10]},
            {"nr_piesni":2,"tytul":"Druga pieśń","strony_pdf":[11]}
        ]"""
    )

    @Test fun `parses and sorts by number`() {
        assertEquals(listOf(1, 2, 3), catalog.songs.map { it.number })
    }

    @Test fun `findByNumber returns the song or null`() {
        assertEquals("Pierwsza", catalog.findByNumber(1)?.title)
        assertNull(catalog.findByNumber(999))
    }

    @Test fun `searchByTitle is case-insensitive, partial, handles polish chars`() {
        assertEquals(listOf(2), catalog.searchByTitle("druga").map { it.number })
        assertEquals(listOf(2), catalog.searchByTitle("PIEŚŃ").map { it.number })
        assertTrue(catalog.searchByTitle("").isEmpty())
    }

    @Test fun `previous and next respect order and boundaries`() {
        assertEquals(1, catalog.previousSong(2)?.number)
        assertEquals(3, catalog.nextSong(2)?.number)
        assertNull(catalog.previousSong(1))
        assertNull(catalog.nextSong(3))
    }

    @Test fun `findByPage returns the song containing the page (both pages of a spread)`() {
        assertEquals(1, catalog.findByPage(10)?.number)
        assertEquals(3, catalog.findByPage(12)?.number)
        assertEquals(3, catalog.findByPage(13)?.number) // druga strona pieśni 3
        assertNull(catalog.findByPage(99))              // strona spoza jakiejkolwiek pieśni
    }
}
