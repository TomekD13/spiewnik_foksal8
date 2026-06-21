package com.spiewnik.app

import com.spiewnik.app.data.Song
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for UiState navigation logic (canGoLeft/canGoRight, page numbers,
 * displayPages). Pure Kotlin — no Android context needed.
 */
class NavigationLogicTest {

    private fun song(number: Int, pages: List<Int>) = Song(number, "Pieśń $number", pages)

    /** SPREAD/PAGE state addressed by an absolute PDF page. */
    private fun spread(
        currentPdfPage: Int,
        total: Int,
        hasPrev: Boolean = false,
        hasNext: Boolean = false,
        mode: NavMode = NavMode.SPREAD,
    ) = UiState(
        song = song(1, listOf(currentPdfPage)),
        navMode = mode,
        hasPrevSong = hasPrev,
        hasNextSong = hasNext,
        currentPdfPage = currentPdfPage,
        totalPdfPages = total,
    )

    /** SONG-mode state addressed by an index into the song's own pages. */
    private fun songMode(
        pages: List<Int>,
        index: Int = 0,
        hasPrev: Boolean = false,
        hasNext: Boolean = false,
    ) = UiState(
        song = song(1, pages),
        navMode = NavMode.SONG,
        hasPrevSong = hasPrev,
        hasNextSong = hasNext,
        songPages = pages,
        songPageIndex = index,
    )

    // ── canGoLeft ──────────────────────────────────────────────────────────────
    @Test fun `canGoLeft false when no song`() = assertFalse(UiState().canGoLeft)
    @Test fun `canGoLeft false on first page (SPREAD)`() = assertFalse(spread(1, 700).canGoLeft)
    @Test fun `canGoLeft true past first page (SPREAD)`() = assertTrue(spread(3, 700).canGoLeft)
    @Test fun `canGoLeft in SONG follows hasPrevSong`() {
        assertFalse(songMode(listOf(10), hasPrev = false).canGoLeft)
        assertTrue(songMode(listOf(10), hasPrev = true).canGoLeft)
    }

    // ── canGoRight ─────────────────────────────────────────────────────────────
    @Test fun `canGoRight false when no song`() = assertFalse(UiState().canGoRight)
    @Test fun `canGoRight false on last page (SPREAD)`() = assertFalse(spread(700, 700).canGoRight)
    @Test fun `canGoRight true before last page (SPREAD)`() = assertTrue(spread(10, 700).canGoRight)
    @Test fun `canGoRight in SONG follows hasNextSong`() {
        assertFalse(songMode(listOf(10), hasNext = false).canGoRight)
        assertTrue(songMode(listOf(10), hasNext = true).canGoRight)
    }

    // ── Page numbers / displayPages ────────────────────────────────────────────
    @Test fun `spread shows current and next page`() {
        val s = spread(10, 700)
        assertEquals(10, s.leftPageNumber)
        assertEquals(11, s.rightPageNumber)
        assertEquals("str. 10–11", s.displayPages)
    }

    @Test fun `spread on last page has no right page`() {
        val s = spread(700, 700)
        assertEquals(700, s.leftPageNumber)
        assertNull(s.rightPageNumber)
        assertEquals("str. 700", s.displayPages)
    }

    @Test fun `song mode shows its own pages`() {
        val s = songMode(listOf(10, 11), index = 0)
        assertEquals(10, s.leftPageNumber)
        assertEquals(11, s.rightPageNumber)
        assertEquals("str. 10–11", s.displayPages)
    }

    @Test fun `song mode single page has no right page`() {
        val s = songMode(listOf(10), index = 0)
        assertEquals(10, s.leftPageNumber)
        assertNull(s.rightPageNumber)
    }

    @Test fun `displayPages empty when no song`() = assertEquals("", UiState().displayPages)
}
