package com.spiewnik.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Data-integrity test for the real shared-assets/piesni.json (not a brittle snapshot).
 * Catches structural regressions: duplicate numbers, missing/out-of-range pages.
 */
class PiesniJsonIntegrityTest {

    private val catalog = SongCatalog.fromJson(
        File("../shared-assets/piesni.json").readText(Charsets.UTF_8)
    )

    @Test fun `catalog is non-trivially populated`() {
        assertTrue("oczekiwano >600 pieśni, jest ${catalog.songs.size}", catalog.songs.size > 600)
    }

    @Test fun `song numbers are unique`() {
        val numbers = catalog.songs.map { it.number }
        assertEquals("numery pieśni muszą być unikalne", numbers.size, numbers.toSet().size)
    }

    @Test fun `each song has 1 or 2 pages within the pdf range`() {
        catalog.songs.forEach { s ->
            assertTrue("pieśń ${s.number} ma ${s.pages.size} stron", s.pages.size in 1..2)
            s.pages.forEach { p ->
                assertTrue("pieśń ${s.number}: strona $p poza zakresem 1..692", p in 1..692)
            }
        }
    }
}
