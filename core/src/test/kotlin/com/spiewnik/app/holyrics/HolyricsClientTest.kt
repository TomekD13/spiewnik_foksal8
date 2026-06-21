package com.spiewnik.app.holyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/** P2: network resilience via a fake transport — no real Holyrics needed. */
class HolyricsClientTest {

    private fun client(response: String) =
        HolyricsClient(object : HolyricsTransport { override fun post(url: String) = response })

    private val failing =
        HolyricsClient(object : HolyricsTransport { override fun post(url: String): String = throw IOException("host down") })

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
}
