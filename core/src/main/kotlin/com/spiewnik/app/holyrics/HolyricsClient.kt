package com.spiewnik.app.holyrics

/** Sends a POST (with a JSON [body]) to the Holyrics API and returns the response body. Throws on error. */
interface HolyricsTransport {
    fun post(url: String, body: String): String
}

/**
 * Platform-neutral Holyrics client: builds the API URLs, calls the injected
 * [transport], and parses the result with [HolyricsParser]. Network failures
 * (timeout, no host, bad HTTP) surface as [Result.failure] — never throws.
 *
 * Shared logic; the transport (HTTP) is provided per platform.
 */
class HolyricsClient(private val transport: HolyricsTransport) {

    // ── Reads ───────────────────────────────────────────────────────────────────

    fun fetchPlaylist(ip: String, token: String): Result<List<Int>> = runCatching {
        val body = transport.post(api(ip, "GetLyricsPlaylist", token), "{}")
        HolyricsParser.parsePlaylist(body)
    }

    /** GetLyricsPlaylist with both the ordered numbers and the number→id map. */
    fun fetchPlaylistData(ip: String, token: String): Result<HolyricsParser.Playlist> = runCatching {
        val body = transport.post(api(ip, "GetLyricsPlaylist", token), "{}")
        HolyricsParser.parsePlaylistData(body)
    }

    fun fetchCurrentSong(ip: String, token: String): Result<Int?> = runCatching {
        val body = transport.post(api(ip, "GetCurrentPresentation", token), "{}")
        HolyricsParser.parseCurrentSong(body)
    }

    // ── Sending a song to Holyrics ────────────────────────────────────────────────

    /**
     * Finds the Holyrics library id of the song whose title equals [number] (exact match).
     * Returns null when no song with that exact title exists.
     */
    fun findSongId(ip: String, token: String, number: Int): Result<String?> = runCatching {
        val body = transport.post(
            api(ip, "SearchLyrics", token),
            """{"text":"$number","title":true,"artist":false,"note":false}"""
        )
        HolyricsParser.parseSongId(body, number)
    }

    /** Adds the song with the given library [id] to the current Holyrics lyrics playlist. */
    fun addToPlaylist(ip: String, token: String, id: String): Result<Unit> = runCatching {
        val body = transport.post(api(ip, "AddLyricsToPlaylist", token), """{"id":"$id"}""")
        if (!HolyricsParser.isOk(body)) error("Holyrics: $body")
    }

    /** Projects (shows) the song with the given library [id] on the Holyrics output. */
    fun showSong(ip: String, token: String, id: String): Result<Unit> = runCatching {
        val body = transport.post(api(ip, "ShowLyrics", token), """{"id":"$id"}""")
        if (!HolyricsParser.isOk(body)) error("Holyrics: $body")
    }

    private fun api(ip: String, method: String, token: String) =
        "http://$ip:$PORT/api/$method?token=$token"

    companion object {
        const val PORT = 8091
    }
}
