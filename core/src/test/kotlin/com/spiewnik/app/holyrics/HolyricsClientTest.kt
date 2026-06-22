package com.spiewnik.app.holyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/** P2: network resilience via a fake transport — no real Holyrics needed. */
class HolyricsClientTest {

    private fun client(response: String) =
        HolyricsClient(object : HolyricsTransport { override fun post(url: String, body: String) = response })

    private val failing =
        HolyricsClient(object : HolyricsTransport { override fun post(url: String, body: String): String = throw IOException("host down") })

    @Test fun `playlist parsed on success`() {
        val result = client("""{"status":"ok","data":[{"title":"5"},{"title":"7"}]}""").fetchPlaylist("ip", "t")
        assertEquals(listOf(5, 7), result.getOrThrow())
    }

    @Test fun `playlist is failure when transport throws`() {
        assertTrue(failing.fetchPlaylist("ip", "t").isFailure)
    }

    @Test fun `current song parsed on success`() {
        val result = client("""{"status":"ok","data":{"type":"song","name":"264"}}""").fetchCurrentSong("ip", "t")
        assertEquals(264, result.getOrThrow())
    }

    @Test fun `current song null when nothing presented`() {
        val result = client("""{"status":"ok","data":null}""").fetchCurrentSong("ip", "t")
        assertNull(result.getOrThrow())
    }

    @Test fun `current song is failure when transport throws`() {
        assertTrue(failing.fetchCurrentSong("ip", "t").isFailure)
    }

    @Test fun `findSongId returns id of exact title match`() {
        val resp = """{"status":"ok","data":[{"id":"5","title":"1"},{"id":"9","title":"10"}]}"""
        assertEquals("5", client(resp).findSongId("ip", "t", 1).getOrThrow())
    }

    @Test fun `findSongId is null when no exact match`() {
        val resp = """{"status":"ok","data":[{"id":"9","title":"10"},{"id":"7","title":"100"}]}"""
        assertNull(client(resp).findSongId("ip", "t", 1).getOrThrow())
    }

    @Test fun `addToPlaylist succeeds on ok and empty body`() {
        assertTrue(client("""{"status":"ok"}""").addToPlaylist("ip", "t", "5").isSuccess)
        assertTrue(client("").addToPlaylist("ip", "t", "5").isSuccess)
    }

    @Test fun `showSong fails on error status`() {
        assertTrue(client("""{"status":"error"}""").showSong("ip", "t", "5").isFailure)
    }

    @Test fun `send actions fail when transport throws`() {
        assertTrue(failing.findSongId("ip", "t", 1).isFailure)
        assertTrue(failing.addToPlaylist("ip", "t", "5").isFailure)
        assertTrue(failing.showSong("ip", "t", "5").isFailure)
    }
}
