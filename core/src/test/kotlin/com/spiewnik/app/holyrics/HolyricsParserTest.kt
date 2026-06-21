package com.spiewnik.app.holyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HolyricsParserTest {

    // ── GetLyricsPlaylist ───────────────────────────────────────────────────────
    @Test fun `playlist parses song numbers from title field`() {
        val json = """{"status":"ok","data":[
            {"id":"a","title":"1"},{"id":"b","title":"264"},
            {"id":"c","title":"22"},{"id":"d","title":"635"},{"id":"e","title":"128"}]}"""
        assertEquals(listOf(1, 264, 22, 635, 128), HolyricsParser.parsePlaylist(json))
    }

    @Test fun `playlist skips items with non-numeric title`() {
        val json = """{"status":"ok","data":[{"title":"5"},{"title":"Amazing Grace"},{"title":"7"}]}"""
        assertEquals(listOf(5, 7), HolyricsParser.parsePlaylist(json))
    }

    @Test fun `playlist empty when status not ok`() {
        assertTrue(HolyricsParser.parsePlaylist("""{"status":"error","error":"invalid token"}""").isEmpty())
    }

    @Test fun `playlist empty when data missing or not array`() {
        assertTrue(HolyricsParser.parsePlaylist("""{"status":"ok"}""").isEmpty())
        assertTrue(HolyricsParser.parsePlaylist("""{"status":"ok","data":{}}""").isEmpty())
    }

    @Test fun `playlist empty on malformed json`() {
        assertTrue(HolyricsParser.parsePlaylist("not json at all").isEmpty())
        assertTrue(HolyricsParser.parsePlaylist("").isEmpty())
    }

    // ── GetCurrentPresentation ──────────────────────────────────────────────────
    @Test fun `current song reads data name when type is song`() {
        val json = """{"status":"ok","data":{"id":"z","type":"song","name":"264","slide_number":1}}"""
        assertEquals(264, HolyricsParser.parseCurrentSong(json))
    }

    @Test fun `current song null when nothing presented (data null)`() {
        assertNull(HolyricsParser.parseCurrentSong("""{"status":"ok","data":null}"""))
    }

    @Test fun `current song null when type is not song`() {
        assertNull(HolyricsParser.parseCurrentSong("""{"status":"ok","data":{"type":"image","name":"5"}}"""))
    }

    @Test fun `current song null when name is not numeric`() {
        assertNull(HolyricsParser.parseCurrentSong("""{"status":"ok","data":{"type":"song","name":"Intro"}}"""))
    }

    @Test fun `current song null on error status or malformed json`() {
        assertNull(HolyricsParser.parseCurrentSong("""{"status":"error"}"""))
        assertNull(HolyricsParser.parseCurrentSong("garbage"))
    }
}
