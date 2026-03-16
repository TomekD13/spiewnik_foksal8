package com.spiewnik.app

import com.spiewnik.app.data.Song
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for UiState navigation logic (canGoLeft, canGoRight, page indices).
 * Pure Kotlin — no Android context needed.
 */
class NavigationLogicTest {

    private fun song(number: Int, pages: List<Int>) = Song(number, "Pieśń $number", pages)

    private fun state(
        song: Song? = null,
        pages: List<Int> = emptyList(),
        spreadStart: Int = 0,
        navMode: NavMode = NavMode.SPREAD,
        hasPrev: Boolean = false,
        hasNext: Boolean = false
    ) = UiState(song, pages, spreadStart, navMode, hasPrev, hasNext)

    // ── canGoLeft ─────────────────────────────────────────────────────────────

    @Test
    fun `canGoLeft is false when no song`() {
        assertFalse(state().canGoLeft)
    }

    @Test
    fun `canGoLeft is false at spreadStart 0 and no prev song in SPREAD mode`() {
        val s = state(song(1, listOf(1, 2)), listOf(1, 2), spreadStart = 0, navMode = NavMode.SPREAD, hasPrev = false)
        assertFalse(s.canGoLeft)
    }

    @Test
    fun `canGoLeft is true at spreadStart 0 when prev song exists in SPREAD mode`() {
        val s = state(song(2, listOf(3, 4)), listOf(3, 4), spreadStart = 0, navMode = NavMode.SPREAD, hasPrev = true)
        assertTrue(s.canGoLeft)
    }

    @Test
    fun `canGoLeft is true at spreadStart 2 in SPREAD mode`() {
        val s = state(song(1, listOf(1, 2, 3)), listOf(1, 2, 3), spreadStart = 2, navMode = NavMode.SPREAD, hasPrev = false)
        assertTrue(s.canGoLeft)
    }

    @Test
    fun `canGoLeft is false at SONG mode with no prev song`() {
        val s = state(song(1, listOf(1, 2)), listOf(1, 2), navMode = NavMode.SONG, hasPrev = false)
        assertFalse(s.canGoLeft)
    }

    @Test
    fun `canGoLeft is true at SONG mode with prev song`() {
        val s = state(song(5, listOf(10)), listOf(10), navMode = NavMode.SONG, hasPrev = true)
        assertTrue(s.canGoLeft)
    }

    // ── canGoRight ────────────────────────────────────────────────────────────

    @Test
    fun `canGoRight is false when no song`() {
        assertFalse(state().canGoRight)
    }

    @Test
    fun `canGoRight is false at last page and no next song in SPREAD mode`() {
        val s = state(song(1, listOf(1, 2)), listOf(1, 2), spreadStart = 1, navMode = NavMode.SPREAD, hasNext = false)
        assertFalse(s.canGoRight)
    }

    @Test
    fun `canGoRight is true at last page when next song exists in SPREAD mode`() {
        val s = state(song(1, listOf(1, 2)), listOf(1, 2), spreadStart = 1, navMode = NavMode.SPREAD, hasNext = true)
        assertTrue(s.canGoRight)
    }

    @Test
    fun `canGoRight is true when more pages remain in SPREAD mode`() {
        val s = state(song(1, listOf(1, 2, 3)), listOf(1, 2, 3), spreadStart = 0, navMode = NavMode.SPREAD, hasNext = false)
        assertTrue(s.canGoRight)
    }

    @Test
    fun `canGoRight is false at SONG mode with no next song`() {
        val s = state(song(700, listOf(690)), listOf(690), navMode = NavMode.SONG, hasNext = false)
        assertFalse(s.canGoRight)
    }

    // ── Page number computations ──────────────────────────────────────────────

    @Test
    fun `leftPageNumber returns first page at spreadStart 0`() {
        val s = state(song(1, listOf(10, 11)), listOf(10, 11), spreadStart = 0)
        assertEquals(10, s.leftPageNumber)
    }

    @Test
    fun `rightPageNumber returns second page when available`() {
        val s = state(song(1, listOf(10, 11)), listOf(10, 11), spreadStart = 0)
        assertEquals(11, s.rightPageNumber)
    }

    @Test
    fun `rightPageNumber is null for single-page song`() {
        val s = state(song(1, listOf(10)), listOf(10), spreadStart = 0)
        assertNull(s.rightPageNumber)
    }

    @Test
    fun `leftPdfIndex is leftPageNumber minus 1`() {
        val s = state(song(1, listOf(143)), listOf(143), spreadStart = 0)
        assertEquals(142, s.leftPdfIndex)
    }

    @Test
    fun `displayPages shows range for two pages`() {
        val s = state(song(1, listOf(10, 11)), listOf(10, 11), spreadStart = 0)
        assertEquals("str. 10–11", s.displayPages)
    }

    @Test
    fun `displayPages shows single page for one-page song`() {
        val s = state(song(1, listOf(143)), listOf(143), spreadStart = 0)
        assertEquals("str. 143", s.displayPages)
    }

    @Test
    fun `displayPages is empty when no song`() {
        assertEquals("", state().displayPages)
    }
}
